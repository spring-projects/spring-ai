/*
 * Copyright 2023-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.vectorstore.opensearch;

import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.apache.hc.core5.http.HttpHost;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.opensearch.testcontainers.OpenSearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.test.vectorstore.AbstractVectorStoreUpsertTests;
import org.springframework.ai.test.vectorstore.FixedDimensionEmbeddingModel;
import org.springframework.ai.vectorstore.EmbeddedDocument;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Upsert verification for {@link OpenSearchVectorStore}, running the shared
 * {@link AbstractVectorStoreUpsertTests} suite.
 * <p>
 * Unlike {@link OpenSearchVectorStoreIT}, this needs no {@code OPENAI_API_KEY}:
 * {@code doUpsert} never embeds, and read-back only needs a query vector, so a
 * {@link FixedDimensionEmbeddingModel} is wired instead. Only Testcontainers OpenSearch
 * is required. OpenSearch writes a batch as a single bulk request, so
 * {@code pairingAcrossBatches} exercises positional pairing across all entries rather
 * than across write-batch boundaries.
 *
 * @author Soby Chacko
 * @since 2.1.0
 */
@Testcontainers
public class OpenSearchVectorStoreUpsertIT extends AbstractVectorStoreUpsertTests {

	private static final int EMBEDDING_DIMENSIONS = 4;

	private static final String CONFIGURED_DIMENSIONS_INDEX = "upsert-configured-dimensions";

	private static final String MISSING_INDEX = "upsert-missing-index";

	private static final String LATE_INDEX = "upsert-late-index";

	private static final String CUSTOM_MAPPING_INDEX = "upsert-custom-mapping";

	@Container
	private static final OpenSearchContainer<?> opensearchContainer = new OpenSearchContainer<>(
			OpenSearchImage.DEFAULT_IMAGE);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestApplication.class);

	@BeforeEach
	void cleanDatabase() {
		this.contextRunner.run(context -> {
			OpenSearchClient client = context.getBean(OpenSearchClient.class);
			client.indices()
				.delete(del -> del
					.index(OpenSearchVectorStore.DEFAULT_INDEX_NAME, CONFIGURED_DIMENSIONS_INDEX, MISSING_INDEX,
							LATE_INDEX, CUSTOM_MAPPING_INDEX)
					.ignoreUnavailable(true));
		});
	}

	@Override
	protected void executeTest(Consumer<VectorStore> testFunction) {
		this.contextRunner.run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);
			testFunction.accept(vectorStore);
		});
	}

	@Override
	protected int embeddingDimensions() {
		return EMBEDDING_DIMENSIONS;
	}

	@Override
	protected VectorStore createStoreOverExistingSchema(VectorStore schemaOwner, EmbeddingModel embeddingModel) {
		// No configured dimension, so the store has to read the real size from the index
		// mapping.
		return OpenSearchVectorStore.builder(client(schemaOwner), embeddingModel).build();
	}

	@Override
	protected VectorStore createStoreWithConfiguredDimensions(VectorStore schemaOwner, EmbeddingModel embeddingModel,
			int dimensions) {
		// The approximate mapping is the one built from the configured dimension, rather
		// than from the embedding model.
		OpenSearchVectorStore store = OpenSearchVectorStore.builder(client(schemaOwner), embeddingModel)
			.index(CONFIGURED_DIMENSIONS_INDEX)
			.useApproximateKnn(true)
			.dimensions(dimensions)
			.initializeSchema(true)
			.build();
		store.afterPropertiesSet();
		return store;
	}

	// Reuse the schema owner's connection rather than opening one the test would have to
	// close.
	private static OpenSearchClient client(VectorStore schemaOwner) {
		return schemaOwner.<OpenSearchClient>getNativeClient().orElseThrow();
	}

	@Test
	void upsertRequiresManagedDocumentIds() {
		executeTest(vectorStore -> {
			OpenSearchVectorStore store = OpenSearchVectorStore
				.builder(client(vectorStore), new FixedDimensionEmbeddingModel(EMBEDDING_DIMENSIONS))
				.manageDocumentIds(false)
				.build();

			EmbeddedDocument entry = new EmbeddedDocument(new Document("1", "content", Map.of()),
					new float[] { 1f, 0f, 0f, 0f });

			assertThatThrownBy(() -> store.upsert(List.of(entry))).isInstanceOf(UnsupportedOperationException.class)
				.hasMessageContaining("manageDocumentIds");
		});
	}

	@Test
	void configuredDimensionsApplyWhenTheMappingCannotBeRead() {
		executeTest(vectorStore -> {
			CallCountingEmbeddingModel embeddingModel = new CallCountingEmbeddingModel(EMBEDDING_DIMENSIONS - 1);
			// The index does not exist, so there is no mapping to read.
			OpenSearchVectorStore store = OpenSearchVectorStore.builder(client(vectorStore), embeddingModel)
				.index(MISSING_INDEX)
				.dimensions(EMBEDDING_DIMENSIONS)
				.build();

			// Checked against the configured size, not against the model's narrower one
			assertWrongDimensionRejected(store);
			assertThat(embeddingModel.calls()).isZero();
		});
	}

	@Test
	void unreadableMappingLeavesTheSizeUnknownAndIsReadAgainLater() {
		executeTest(vectorStore -> {
			CallCountingEmbeddingModel embeddingModel = new CallCountingEmbeddingModel(EMBEDDING_DIMENSIONS + 3);
			OpenSearchVectorStore store = OpenSearchVectorStore.builder(client(vectorStore), embeddingModel)
				.index(LATE_INDEX)
				.build();
			float[] tooShort = new float[EMBEDDING_DIMENSIONS - 1];
			tooShort[0] = 1f;
			SearchRequest request = SearchRequest.builder().query("ignored").topK(1).similarityThresholdAll().build();

			// There is no mapping yet, so the size is unknown. The search fails in
			// OpenSearch, and the model must not have stood in for the index.
			assertThatThrownBy(() -> store.similaritySearch(tooShort, request))
				.isNotInstanceOf(IllegalArgumentException.class);

			OpenSearchVectorStore.builder(client(vectorStore), new FixedDimensionEmbeddingModel(1))
				.index(LATE_INDEX)
				.dimensions(EMBEDDING_DIMENSIONS)
				.initializeSchema(true)
				.build()
				.afterPropertiesSet();

			// Now that the index exists its real size is read and applies
			assertThatThrownBy(() -> store.similaritySearch(tooShort, request))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("dimension " + (EMBEDDING_DIMENSIONS - 1))
				.hasMessageContaining("dimension " + EMBEDDING_DIMENSIONS);
			assertThat(embeddingModel.calls()).isZero();
		});
	}

	@Test
	void initializingOverAnExistingIndexWithoutEmbeddingModel() {
		executeTest(vectorStore -> {
			CallCountingEmbeddingModel embeddingModel = new CallCountingEmbeddingModel(EMBEDDING_DIMENSIONS + 3);
			// The shared store has already created the default index.
			OpenSearchVectorStore store = OpenSearchVectorStore.builder(client(vectorStore), embeddingModel)
				.initializeSchema(true)
				.build();
			store.afterPropertiesSet();

			store.upsert(List.of(embeddedDocument(UUID.randomUUID().toString(), "existing", Map.of("tag", "e"))));
			assertWrongDimensionRejected(store);
			assertThat(embeddingModel.calls()).isZero();
		});
	}

	@Test
	void customMappingWithFixedDimensionIsReadFromTheIndex() {
		executeTest(vectorStore -> {
			CallCountingEmbeddingModel embeddingModel = new CallCountingEmbeddingModel(EMBEDDING_DIMENSIONS + 3);
			// The mapping ignores the size it is formatted with, so the index is created
			// with the literal size rather than the model's.
			String mapping = """
					{"properties":{"embedding":{"type":"knn_vector","dimension":%d}}}
					""".formatted(EMBEDDING_DIMENSIONS);
			OpenSearchVectorStore store = OpenSearchVectorStore.builder(client(vectorStore), embeddingModel)
				.index(CUSTOM_MAPPING_INDEX)
				.mappingJson(mapping)
				.initializeSchema(true)
				.build();
			store.afterPropertiesSet();
			int callsAfterCreation = embeddingModel.calls();

			String id = UUID.randomUUID().toString();
			store.upsert(List.of(embeddedDocument(id, "custom", Map.of("tag", "c"))));
			float[] query = new float[EMBEDDING_DIMENSIONS];
			query[0] = 1f;
			SearchRequest request = SearchRequest.builder().query("ignored").topK(1).similarityThresholdAll().build();
			assertThat(store.similaritySearch(query, request)).isNotNull();

			float[] modelWidth = new float[EMBEDDING_DIMENSIONS + 3];
			modelWidth[0] = 1f;
			EmbeddedDocument tooWide = new EmbeddedDocument(new Document("2", "wide", Map.of()), modelWidth);
			assertThatThrownBy(() -> store.upsert(List.of(tooWide))).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("expects dimension " + EMBEDDING_DIMENSIONS);
			assertThatThrownBy(() -> store.similaritySearch(modelWidth, request))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("expects dimension " + EMBEDDING_DIMENSIONS);
			assertThat(embeddingModel.calls()).isEqualTo(callsAfterCreation);
		});
	}

	@SpringBootConfiguration
	public static class TestApplication {

		@Bean
		public OpenSearchClient openSearchClient() {
			try {
				return new OpenSearchClient(ApacheHttpClient5TransportBuilder
					.builder(HttpHost.create(opensearchContainer.getHttpHostAddress()))
					.build());
			}
			catch (URISyntaxException e) {
				throw new RuntimeException(e);
			}
		}

		@Bean
		public OpenSearchVectorStore vectorStore(OpenSearchClient openSearchClient, EmbeddingModel embeddingModel) {
			return OpenSearchVectorStore.builder(openSearchClient, embeddingModel).initializeSchema(true).build();
		}

		@Bean
		public EmbeddingModel embeddingModel() {
			return new FixedDimensionEmbeddingModel(EMBEDDING_DIMENSIONS);
		}

	}

}
