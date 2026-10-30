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

package org.springframework.ai.vectorstore.mongodb.atlas;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBAtlasLocalContainer;

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
import org.springframework.core.convert.converter.Converter;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.util.MimeType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * Upsert verification for {@link MongoDBAtlasVectorStore}, running the shared
 * {@link AbstractVectorStoreUpsertTests} suite.
 * <p>
 * Unlike {@link MongoDBAtlasVectorStoreIT}, this test needs no {@code OPENAI_API_KEY}:
 * upsert never embeds, and read-back only needs a query vector, so a small fixed-size
 * local {@link EmbeddingModel} is used. Only the Atlas container is required.
 *
 * @author Soby Chacko
 * @since 2.1.0
 */
@Testcontainers
class MongoDBAtlasVectorStoreUpsertIT extends AbstractVectorStoreUpsertTests {

	private static final int EMBEDDING_DIMENSIONS = 4;

	private static final String DATABASE = "springaiupsert";

	@Container
	private static MongoDBAtlasLocalContainer container = new MongoDBAtlasLocalContainer(MongoDbImage.DEFAULT_IMAGE);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestApplication.class);

	@BeforeEach
	void clearCollection() {
		this.contextRunner.run(context -> context.getBean(MongoTemplate.class)
			.getCollection(MongoDBAtlasVectorStore.DEFAULT_VECTOR_COLLECTION_NAME)
			.deleteMany(new org.bson.Document()));
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
		MongoTemplate mongoTemplate = schemaOwner.<MongoTemplate>getNativeClient().orElseThrow();
		return MongoDBAtlasVectorStore.builder(mongoTemplate, embeddingModel).build();
	}

	@Test
	void initializingOverAnExistingIndexWithoutEmbeddingModel() {
		executeTest(vectorStore -> {
			MongoTemplate mongoTemplate = vectorStore.<MongoTemplate>getNativeClient().orElseThrow();
			CallCountingEmbeddingModel embeddingModel = new CallCountingEmbeddingModel(EMBEDDING_DIMENSIONS + 3);

			// The shared store has already created the default index
			MongoDBAtlasVectorStore store = MongoDBAtlasVectorStore.builder(mongoTemplate, embeddingModel)
				.initializeSchema(true)
				.build();
			assertThatNoException().isThrownBy(store::afterPropertiesSet);

			store.upsert(List.of(embeddedDocument(UUID.randomUUID().toString(), "existing", Map.of("tag", "e"))));
			assertWrongDimensionRejected(store);
			assertThat(embeddingModel.calls()).isZero();
		});
	}

	@Test
	void upsertIsRefusedWhenThereIsNoIndex() {
		executeTest(vectorStore -> {
			MongoTemplate mongoTemplate = vectorStore.<MongoTemplate>getNativeClient().orElseThrow();
			CallCountingEmbeddingModel embeddingModel = new CallCountingEmbeddingModel(EMBEDDING_DIMENSIONS);
			MongoDBAtlasVectorStore store = MongoDBAtlasVectorStore.builder(mongoTemplate, embeddingModel)
				.collectionName("collection_without_index")
				.build();

			assertThatIllegalStateException()
				.isThrownBy(() -> store.upsert(List.of(embeddedDocument(UUID.randomUUID().toString(), "x", Map.of()))))
				.withMessageContaining("Could not determine the vector size");
			assertThat(mongoTemplate.getCollection("collection_without_index").countDocuments()).isZero();
			assertThat(embeddingModel.calls()).isZero();
		});
	}

	@Test
	void upsertAndSearchUseTheConfiguredPath() {
		upsertAndSearchAtPath("custom_path_collection", "custom_embedding");
	}

	@Test
	void upsertAndSearchUseANestedPath() {
		upsertAndSearchAtPath("nested_path_collection", "vectors.embedding");
	}

	private void upsertAndSearchAtPath(String collectionName, String pathName) {
		executeTest(vectorStore -> {
			MongoTemplate mongoTemplate = vectorStore.<MongoTemplate>getNativeClient().orElseThrow();
			MongoDBAtlasVectorStore store = MongoDBAtlasVectorStore
				.builder(mongoTemplate, new FixedDimensionEmbeddingModel(EMBEDDING_DIMENSIONS))
				.collectionName(collectionName)
				.vectorIndexName("custom_path_index")
				.pathName(pathName)
				.initializeSchema(true)
				.build();
			assertThatNoException().isThrownBy(store::afterPropertiesSet);
			awaitQueryable(mongoTemplate, collectionName, "custom_path_index");

			String id = UUID.randomUUID().toString();
			store.upsert(List.of(new EmbeddedDocument(new Document(id, "first", Map.of("tag", "a")),
					new float[] { 1f, 0f, 0f, 0f })));
			// Replacing must keep the vector at the indexed path
			store.upsert(List.of(new EmbeddedDocument(new Document(id, "second", Map.of("tag", "b")),
					new float[] { 0f, 1f, 0f, 0f })));

			org.bson.Document stored = mongoTemplate.getCollection(collectionName)
				.find(new org.bson.Document("_id", id))
				.first();
			assertThat(stored).doesNotContainKey("embedding");
			if (pathName.contains(".")) {
				// Not a literal key with a dot in it, which the index cannot see
				assertThat(stored).doesNotContainKey(pathName);
			}
			// The vector sits at the path, nested if the path is dotted
			Object holder = stored;
			for (String name : pathName.split("\\.")) {
				assertThat(holder).isInstanceOf(org.bson.Document.class);
				holder = ((org.bson.Document) holder).get(name);
			}
			assertThat(holder).isNotNull();
			SearchRequest request = SearchRequest.builder().topK(3).similarityThresholdAll().build();
			await().atMost(10, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(250)).untilAsserted(() -> {
				List<Document> results = store.similaritySearch(new float[] { 0f, 1f, 0f, 0f }, request);
				assertThat(results).hasSize(1);
				assertThat(results.get(0).getId()).isEqualTo(id);
				assertThat(results.get(0).getText()).isEqualTo("second");
				assertThat(results.get(0).getScore()).isGreaterThan(0.99);
			});
		});
	}

	@Test
	void failedIndexReadLeavesTheSizeUnknownAndIsReadAgainLater() {
		executeTest(vectorStore -> {
			AtomicBoolean failIndexReads = new AtomicBoolean();
			MongoTemplate delegate = vectorStore.<MongoTemplate>getNativeClient().orElseThrow();
			// Atlas only rejects a query vector of the wrong size once the index is built
			awaitQueryable(delegate, MongoDBAtlasVectorStore.DEFAULT_VECTOR_COLLECTION_NAME, "vector_index");
			MongoTemplate mongoTemplate = new MongoTemplate(delegate.getMongoDatabaseFactory(),
					delegate.getConverter()) {

				@Override
				public MongoCollection<org.bson.Document> getCollection(String collectionName) {
					if (failIndexReads.get()) {
						throw new DataAccessResourceFailureException("Simulated index read failure");
					}
					return super.getCollection(collectionName);
				}

			};
			CallCountingEmbeddingModel embeddingModel = new CallCountingEmbeddingModel(EMBEDDING_DIMENSIONS + 3);
			MongoDBAtlasVectorStore store = MongoDBAtlasVectorStore.builder(mongoTemplate, embeddingModel).build();
			EmbeddedDocument valid = embeddedDocument(UUID.randomUUID().toString(), "after outage", Map.of("tag", "o"));

			// Atlas would store a wrong-sized vector and never find it, so nothing is
			// written while the size is unknown
			failIndexReads.set(true);
			assertThatIllegalStateException().isThrownBy(() -> store.upsert(List.of(valid)))
				.withMessageContaining("Could not determine the vector size");
			assertThat(delegate.getCollection(MongoDBAtlasVectorStore.DEFAULT_VECTOR_COLLECTION_NAME).countDocuments())
				.isZero();

			// A query vector of the wrong size is left to Atlas, which rejects it
			SearchRequest request = SearchRequest.builder().topK(3).similarityThresholdAll().build();
			assertThatThrownBy(() -> store.similaritySearch(new float[] { 1f, 0f, 0f }, request))
				.hasStackTraceContaining("indexed with " + EMBEDDING_DIMENSIONS + " dimensions");

			// Once the index can be read again its size is read and applies
			failIndexReads.set(false);
			store.upsert(List.of(valid));
			assertWrongDimensionRejected(store);
			assertThat(embeddingModel.calls()).isZero();
		});
	}

	private static void awaitQueryable(MongoTemplate mongoTemplate, String collectionName, String indexName) {
		await().atMost(30, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(250)).untilAsserted(() -> {
			org.bson.Document index = mongoTemplate.getCollection(collectionName)
				.aggregate(
						List.of(new org.bson.Document("$listSearchIndexes", new org.bson.Document("name", indexName))))
				.first();
			assertThat(index).isNotNull();
			assertThat(index.getBoolean("queryable")).isTrue();
		});
	}

	@SpringBootConfiguration
	public static class TestApplication {

		@Bean
		public VectorStore vectorStore(MongoTemplate mongoTemplate, EmbeddingModel embeddingModel) {
			return MongoDBAtlasVectorStore.builder(mongoTemplate, embeddingModel)
				.metadataFieldsToFilter(List.of("tag"))
				.initializeSchema(true)
				.build();
		}

		@Bean
		public EmbeddingModel embeddingModel() {
			return new FixedDimensionEmbeddingModel(EMBEDDING_DIMENSIONS);
		}

		@Bean
		public MongoClient mongoClient() {
			String baseUri = container.getConnectionString();
			return MongoClients.create(baseUri.replace("/?", "/" + DATABASE + "?"));
		}

		@Bean
		public MongoTemplate mongoTemplate(MongoClient mongoClient) {
			return new MongoTemplate(mongoClient, DATABASE);
		}

		@Bean
		public MongoCustomConversions mongoCustomConversions() {
			return new MongoCustomConversions(Arrays.asList(new Converter<MimeType, String>() {

				@Override
				public String convert(MimeType source) {
					return source.toString();
				}
			}, new Converter<String, MimeType>() {

				@Override
				public MimeType convert(String source) {
					return MimeType.valueOf(source);
				}
			}));
		}

	}

}
