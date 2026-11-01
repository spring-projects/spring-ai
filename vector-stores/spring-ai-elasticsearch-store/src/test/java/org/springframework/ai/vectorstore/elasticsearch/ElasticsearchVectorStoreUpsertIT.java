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

package org.springframework.ai.vectorstore.elasticsearch;

import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.cat.indices.IndicesRecord;
import co.elastic.clients.json.jackson.Jackson3JsonpMapper;
import co.elastic.clients.transport.rest5_client.Rest5ClientTransport;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import org.apache.hc.core5.http.HttpHost;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.test.vectorstore.AbstractVectorStoreUpsertTests;
import org.springframework.ai.test.vectorstore.FixedDimensionEmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Upsert verification for {@link ElasticsearchVectorStore}, running the shared
 * {@link AbstractVectorStoreUpsertTests} suite.
 * <p>
 * Unlike {@link ElasticsearchVectorStoreIT}, this needs no {@code OPENAI_API_KEY}:
 * {@code doUpsert} never embeds, and read-back only needs a query vector, so a
 * {@link FixedDimensionEmbeddingModel} is wired instead. Only Testcontainers
 * Elasticsearch is required. Elasticsearch writes a batch as a single bulk request, so
 * {@code pairingAcrossBatches} exercises positional pairing across all entries rather
 * than across write-batch boundaries.
 *
 * @author Soby Chacko
 * @since 2.1.0
 */
@Testcontainers
public class ElasticsearchVectorStoreUpsertIT extends AbstractVectorStoreUpsertTests {

	private static final int EMBEDDING_DIMENSIONS = 4;

	@Container
	private static final ElasticsearchContainer elasticsearchContainer = new ElasticsearchContainer(
			ElasticsearchImage.DEFAULT_IMAGE)
		.withEnv("xpack.security.enabled", "false");

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestApplication.class);

	@BeforeEach
	void cleanDatabase() {
		this.contextRunner.run(context -> {
			ElasticsearchClient elasticsearchClient = context.getBean(ElasticsearchClient.class);
			List<String> indices = elasticsearchClient.cat()
				.indices()
				.indices()
				.stream()
				.map(IndicesRecord::index)
				.toList();
			if (!indices.isEmpty()) {
				elasticsearchClient.indices().delete(del -> del.index(indices));
			}
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
		// Default options leave the configured dimension at its default of 1536, so the
		// store has to read the real size from the index mapping.
		return ElasticsearchVectorStore.builder(restClient(schemaOwner), embeddingModel).build();
	}

	@Override
	protected VectorStore createStoreWithConfiguredDimensions(VectorStore schemaOwner, EmbeddingModel embeddingModel,
			int dimensions) {
		ElasticsearchVectorStoreOptions options = new ElasticsearchVectorStoreOptions();
		options.setIndexName("upsert-configured-dimensions");
		options.setDimensions(dimensions);
		ElasticsearchVectorStore store = ElasticsearchVectorStore.builder(restClient(schemaOwner), embeddingModel)
			.options(options)
			.initializeSchema(true)
			.build();
		store.afterPropertiesSet();
		return store;
	}

	@Test
	void configuredDimensionsApplyWhenTheMappingCannotBeRead() {
		executeTest(vectorStore -> {
			CallCountingEmbeddingModel embeddingModel = new CallCountingEmbeddingModel(EMBEDDING_DIMENSIONS - 1);
			ElasticsearchVectorStoreOptions options = new ElasticsearchVectorStoreOptions();
			options.setIndexName("upsert-missing-index");
			options.setDimensions(EMBEDDING_DIMENSIONS);
			// The index does not exist, so there is no mapping to read.
			ElasticsearchVectorStore store = ElasticsearchVectorStore.builder(restClient(vectorStore), embeddingModel)
				.options(options)
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
			ElasticsearchVectorStoreOptions options = new ElasticsearchVectorStoreOptions();
			options.setIndexName("upsert-late-index");
			ElasticsearchVectorStore store = ElasticsearchVectorStore.builder(restClient(vectorStore), embeddingModel)
				.options(options)
				.build();
			float[] tooShort = new float[EMBEDDING_DIMENSIONS - 1];
			tooShort[0] = 1f;
			SearchRequest request = SearchRequest.builder().query("ignored").topK(1).similarityThresholdAll().build();

			// There is no mapping yet, so the size is unknown. The search fails in
			// Elasticsearch, and the model must not have stood in for the index.
			assertThatThrownBy(() -> store.similaritySearch(tooShort, request))
				.isNotInstanceOf(IllegalArgumentException.class);

			ElasticsearchVectorStoreOptions created = new ElasticsearchVectorStoreOptions();
			created.setIndexName("upsert-late-index");
			created.setDimensions(EMBEDDING_DIMENSIONS);
			ElasticsearchVectorStore.builder(restClient(vectorStore), new FixedDimensionEmbeddingModel(1))
				.options(created)
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
			ElasticsearchVectorStore store = ElasticsearchVectorStore.builder(restClient(vectorStore), embeddingModel)
				.initializeSchema(true)
				.build();
			store.afterPropertiesSet();

			store.upsert(List.of(embeddedDocument(UUID.randomUUID().toString(), "existing", Map.of("tag", "e"))));
			assertWrongDimensionRejected(store);
			assertThat(embeddingModel.calls()).isZero();
		});
	}

	// Reuse the schema owner's connection rather than opening one the test would have to
	// close.
	private static Rest5Client restClient(VectorStore schemaOwner) {
		ElasticsearchClient client = schemaOwner.<ElasticsearchClient>getNativeClient().orElseThrow();
		return ((Rest5ClientTransport) client._transport()).restClient();
	}

	@SpringBootConfiguration
	public static class TestApplication {

		@Bean
		public ElasticsearchVectorStore vectorStore(EmbeddingModel embeddingModel, Rest5Client restClient) {
			ElasticsearchVectorStoreOptions options = new ElasticsearchVectorStoreOptions();
			options.setDimensions(EMBEDDING_DIMENSIONS);
			return ElasticsearchVectorStore.builder(restClient, embeddingModel)
				.initializeSchema(true)
				.options(options)
				.build();
		}

		@Bean
		public EmbeddingModel embeddingModel() {
			return new FixedDimensionEmbeddingModel(EMBEDDING_DIMENSIONS);
		}

		@Bean
		Rest5Client restClient() throws URISyntaxException {
			return Rest5Client.builder(HttpHost.create(elasticsearchContainer.getHttpHostAddress())).build();
		}

		@Bean
		ElasticsearchClient elasticsearchClient(Rest5Client restClient) {
			JsonMapper jsonMapper = JsonMapper.builder()
				.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
				.enable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
				.build();
			return new ElasticsearchClient(new Rest5ClientTransport(restClient, new Jackson3JsonpMapper(jsonMapper)));
		}

	}

}
