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

package org.springframework.ai.vectorstore.typesense;

import java.net.ConnectException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.typesense.TypesenseContainer;
import org.typesense.api.Client;
import org.typesense.api.Collection;
import org.typesense.api.Configuration;
import org.typesense.api.exceptions.ObjectNotFound;
import org.typesense.resources.Node;

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
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

/**
 * Upsert verification for {@link TypesenseVectorStore}, running the shared
 * {@link AbstractVectorStoreUpsertTests} suite.
 * <p>
 * Unlike {@link TypesenseVectorStoreIT}, this test uses a small fixed-size local
 * {@link EmbeddingModel}: upsert never embeds, and read-back only needs a query vector.
 * Only the Typesense container is required.
 *
 * @author Soby Chacko
 * @since 2.1.0
 */
@Testcontainers
class TypesenseVectorStoreUpsertIT extends AbstractVectorStoreUpsertTests {

	private static final int EMBEDDING_DIMENSIONS = 4;

	private static final String COLLECTION_NAME = "upsert_vector_store";

	private static final String CONFIGURED_DIMENSIONS_COLLECTION_NAME = "upsert_configured_dimensions";

	@Container
	private static TypesenseContainer typesense = new TypesenseContainer(TypesenseImage.DEFAULT_IMAGE);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestApplication.class);

	@BeforeEach
	void dropCollections() {
		// Each test starts from a collection the shared store creates
		Client client = client();
		for (String name : List.of(COLLECTION_NAME, CONFIGURED_DIMENSIONS_COLLECTION_NAME)) {
			try {
				client.collections(name).delete();
			}
			catch (ObjectNotFound ex) {
				// Nothing to drop
			}
			catch (Exception ex) {
				throw new IllegalStateException(ex);
			}
		}
	}

	@Override
	protected void executeTest(Consumer<VectorStore> testFunction) {
		this.contextRunner.run(context -> testFunction.accept(context.getBean(VectorStore.class)));
	}

	@Override
	protected int embeddingDimensions() {
		return EMBEDDING_DIMENSIONS;
	}

	@Override
	protected VectorStore createStoreOverExistingSchema(VectorStore schemaOwner, EmbeddingModel embeddingModel) {
		Client client = schemaOwner.<Client>getNativeClient().orElseThrow();
		return TypesenseVectorStore.builder(client, embeddingModel).collectionName(COLLECTION_NAME).build();
	}

	@Override
	protected VectorStore createStoreWithConfiguredDimensions(VectorStore schemaOwner, EmbeddingModel embeddingModel,
			int dimensions) {
		Client client = schemaOwner.<Client>getNativeClient().orElseThrow();
		TypesenseVectorStore store = TypesenseVectorStore.builder(client, embeddingModel)
			.collectionName(CONFIGURED_DIMENSIONS_COLLECTION_NAME)
			.embeddingDimension(dimensions)
			.initializeSchema(true)
			.build();
		store.afterPropertiesSet();
		return store;
	}

	@Test
	void initializingOverAnExistingCollectionWithoutEmbeddingModel() {
		executeTest(vectorStore -> {
			Client client = vectorStore.<Client>getNativeClient().orElseThrow();
			CallCountingEmbeddingModel embeddingModel = new CallCountingEmbeddingModel(EMBEDDING_DIMENSIONS + 3);

			// The shared store has already created the collection
			TypesenseVectorStore store = TypesenseVectorStore.builder(client, embeddingModel)
				.collectionName(COLLECTION_NAME)
				.initializeSchema(true)
				.build();
			assertThatNoException().isThrownBy(store::afterPropertiesSet);

			store.upsert(List.of(embeddedDocument(UUID.randomUUID().toString(), "existing", Map.of("tag", "e"))));
			assertWrongDimensionRejected(store);
			assertThat(embeddingModel.calls()).isZero();
		});
	}

	@Test
	void failedSchemaReadLeavesTheSizeUnknownAndIsReadAgainLater() {
		executeTest(vectorStore -> {
			AtomicBoolean failSchemaReads = new AtomicBoolean();
			Client client = clientWithFailingSchemaReads(failSchemaReads);
			CallCountingEmbeddingModel embeddingModel = new CallCountingEmbeddingModel(EMBEDDING_DIMENSIONS + 3);
			TypesenseVectorStore store = TypesenseVectorStore.builder(client, embeddingModel)
				.collectionName(COLLECTION_NAME)
				.build();
			EmbeddedDocument wrongSize = new EmbeddedDocument(
					new Document(UUID.randomUUID().toString(), "wrong size", Map.of()), new float[] { 1f, 0f, 0f });
			SearchRequest request = SearchRequest.builder().topK(3).similarityThresholdAll().build();

			// The size cannot be read, so the store leaves the check to Typesense, which
			// rejects a vector of the wrong size both when writing and when searching
			failSchemaReads.set(true);
			store.upsert(List.of(embeddedDocument(UUID.randomUUID().toString(), "during outage", Map.of("tag", "o"))));
			assertThatIllegalStateException().isThrownBy(() -> store.upsert(List.of(wrongSize)))
				.withMessageContaining("must have " + EMBEDDING_DIMENSIONS + " dimensions");
			assertThatIllegalStateException()
				.isThrownBy(() -> store.similaritySearch(new float[] { 1f, 0f, 0f }, request))
				.withMessageContaining("must have " + EMBEDDING_DIMENSIONS + " dimensions");

			// Once the schema can be read again its size is read and applies
			failSchemaReads.set(false);
			assertWrongDimensionRejected(store);
			assertThat(embeddingModel.calls()).isZero();
		});
	}

	@Test
	void aRejectedEntryInABatchIsReportedAndTheOthersAreWritten() {
		executeTest(vectorStore -> {
			// The size is unknown, so the whole-batch check is skipped and Typesense
			// checks each document on its own
			Client client = clientWithFailingSchemaReads(new AtomicBoolean(true));
			TypesenseVectorStore store = TypesenseVectorStore
				.builder(client, new CallCountingEmbeddingModel(EMBEDDING_DIMENSIONS))
				.collectionName(COLLECTION_NAME)
				.build();
			String goodId = UUID.randomUUID().toString();
			EmbeddedDocument good = embeddedDocument(goodId, "good", Map.of("tag", "g"));
			EmbeddedDocument wrongSize = new EmbeddedDocument(
					new Document(UUID.randomUUID().toString(), "wrong size", Map.of()), new float[] { 1f, 0f, 0f });

			assertThatIllegalStateException().isThrownBy(() -> store.upsert(List.of(good, wrongSize)))
				.withMessageContaining("index 1")
				.withMessageContaining("must have " + EMBEDDING_DIMENSIONS + " dimensions");

			// A Typesense batch is not atomic: the valid entry was written
			List<Document> results = vectorStore.similaritySearch(good.embedding(),
					SearchRequest.builder().topK(10).similarityThresholdAll().build());
			assertThat(results).extracting(Document::getId).containsExactly(goodId);
		});
	}

	@Test
	void anEntryTheClientCannotSerializeFailsTheUpsertAndTheOthersAreWritten() {
		executeTest(vectorStore -> {
			// The client serializes with a plain Jackson 2 mapper, which cannot write an
			// Instant, and leaves the entry out of the request
			String unserializableId = UUID.randomUUID().toString();
			EmbeddedDocument unserializable = embeddedDocument(unserializableId, "unserializable",
					Map.of("createdAt", Instant.now()));
			String goodId = UUID.randomUUID().toString();
			EmbeddedDocument good = embeddedDocument(goodId, "good", Map.of("tag", "g"));

			assertThatIllegalStateException().isThrownBy(() -> vectorStore.upsert(List.of(unserializable, good)))
				.withMessageContaining("returned 1 import results for 2 documents");

			// A Typesense batch is not atomic: the entry that was sent was written
			List<Document> results = vectorStore.similaritySearch(good.embedding(),
					SearchRequest.builder().topK(10).similarityThresholdAll().build());
			assertThat(results).extracting(Document::getId).contains(goodId).doesNotContain(unserializableId);
		});
	}

	private static Client clientWithFailingSchemaReads(AtomicBoolean failSchemaReads) {
		return new Client(configuration()) {

			@Override
			public Collection collections(String name) {
				Collection collection = spy(super.collections(name));
				try {
					doAnswer(invocation -> {
						if (failSchemaReads.get()) {
							throw new ConnectException("Simulated schema read failure");
						}
						return invocation.callRealMethod();
					}).when(collection).retrieve();
				}
				catch (Exception ex) {
					throw new IllegalStateException(ex);
				}
				return collection;
			}

		};
	}

	private static Configuration configuration() {
		return new Configuration(
				List.of(new Node("http", typesense.getHost(), typesense.getMappedPort(8108).toString())),
				Duration.ofSeconds(5), typesense.getApiKey());
	}

	private static Client client() {
		return new Client(configuration());
	}

	@SpringBootConfiguration
	public static class TestApplication {

		@Bean
		public VectorStore vectorStore(Client client, EmbeddingModel embeddingModel) {
			return TypesenseVectorStore.builder(client, embeddingModel)
				.collectionName(COLLECTION_NAME)
				.initializeSchema(true)
				.build();
		}

		@Bean
		public Client typesenseClient() {
			return client();
		}

		@Bean
		public EmbeddingModel embeddingModel() {
			return new FixedDimensionEmbeddingModel(EMBEDDING_DIMENSIONS);
		}

	}

}
