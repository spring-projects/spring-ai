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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import org.springframework.dao.DataAccessException;
import org.springframework.data.mongodb.BulkOperationException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.util.MimeType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * Upsert verification for {@link MongoDBAtlasVectorStore} with bulk writes enabled,
 * running the shared {@link AbstractVectorStoreUpsertTests} suite with a batch size that
 * makes writes cross batch boundaries. The parameterized tests run the same fixture
 * through one save per document and through bulk writes, so that the two paths are held
 * to the same outcome.
 *
 * @author Ayush Kulshreshtha
 * @since 2.1.0
 */
@Testcontainers
class MongoDBAtlasVectorStoreBulkUpsertIT extends AbstractVectorStoreUpsertTests {

	private static final int EMBEDDING_DIMENSIONS = 4;

	private static final String DATABASE = "springaibulkupsert";

	private static final int BATCH_SIZE = 2;

	private static final int PER_DOCUMENT = 0;

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
		return MongoDBAtlasVectorStore.builder(mongoTemplate, embeddingModel).bulkUpsertBatchSize(BATCH_SIZE).build();
	}

	@ParameterizedTest
	@ValueSource(ints = { PER_DOCUMENT, BATCH_SIZE })
	void theLastEntryWinsWhenAnIdRepeatsWithinAndAcrossBatches(int batchSize) {
		executeTest(vectorStore -> {
			MongoTemplate mongoTemplate = vectorStore.<MongoTemplate>getNativeClient().orElseThrow();
			VectorStore store = storeOnDefaultCollection(mongoTemplate, batchSize);

			// With a batch size of 2 "same-batch" repeats within the first batch, and
			// "across-batches" repeats in the second
			store.upsert(List.of(entry("same-batch", "first", Map.of("old", "kept-out")),
					entry("same-batch", "second", Map.of("tag", "b")),
					entry("across-batches", "first", Map.of("old", "kept-out")), entry("other", "other", Map.of()),
					entry("across-batches", "second", Map.of("tag", "b"))));

			assertThat(storedIds(mongoTemplate)).containsExactlyInAnyOrder("same-batch", "across-batches", "other");
			for (String id : List.of("same-batch", "across-batches")) {
				org.bson.Document stored = stored(mongoTemplate, id);
				assertThat(stored.getString("content")).isEqualTo("second");
				assertThat(stored.get("metadata", org.bson.Document.class)).containsEntry("tag", "b")
					.doesNotContainKey("old");
			}
		});
	}

	@ParameterizedTest
	@ValueSource(ints = { PER_DOCUMENT, BATCH_SIZE })
	void aWrongSizeBeyondTheFirstBatchWritesNothing(int batchSize) {
		executeTest(vectorStore -> {
			MongoTemplate mongoTemplate = vectorStore.<MongoTemplate>getNativeClient().orElseThrow();
			VectorStore store = storeOnDefaultCollection(mongoTemplate, batchSize);

			assertThatIllegalArgumentException()
				.isThrownBy(() -> store
					.upsert(List.of(entry("a", "a", Map.of()), entry("b", "b", Map.of()), entry("c", "c", Map.of()),
							new EmbeddedDocument(new Document("d", "d", Map.of()), new float[3]))))
				.withMessageContaining("Embedding at index 3");

			assertThat(storedIds(mongoTemplate)).isEmpty();
		});
	}

	@ParameterizedTest
	@ValueSource(ints = { PER_DOCUMENT, BATCH_SIZE })
	void aFailedWriteKeepsWhatCameBeforeItAndSkipsWhatComesAfter(int batchSize) {
		executeTest(vectorStore -> {
			MongoTemplate mongoTemplate = vectorStore.<MongoTemplate>getNativeClient().orElseThrow();
			VectorStore store = storeOnDefaultCollection(mongoTemplate, batchSize);
			MongoCollection<org.bson.Document> collection = mongoTemplate
				.getCollection(MongoDBAtlasVectorStore.DEFAULT_VECTOR_COLLECTION_NAME);
			collection.createIndex(Indexes.ascending("metadata.unique"), new IndexOptions().unique(true).sparse(true));
			try {
				// With a batch size of 2 "d" fails in the middle of the second batch
				// after "a" and "b" in the first batch and "c" in its own were written
				assertThatThrownBy(() -> store.upsert(List.of(entry("a", "a", Map.of("unique", 1)),
						entry("b", "b", Map.of("unique", 2)), entry("c", "c", Map.of("unique", 3)),
						entry("d", "d", Map.of("unique", 1)), entry("e", "e", Map.of("unique", 5)))))
					.isInstanceOf(DataAccessException.class)
					.satisfies(failure -> {
						if (batchSize != PER_DOCUMENT) {
							assertThat(failure).isInstanceOf(BulkOperationException.class);
						}
					});

				assertThat(storedIds(mongoTemplate)).containsExactlyInAnyOrder("a", "b", "c");
			}
			finally {
				collection.dropIndex(Indexes.ascending("metadata.unique"));
			}
		});
	}

	@Test
	void bulkUpsertAndSearchUseACustomCollectionAndANestedPath() {
		executeTest(vectorStore -> {
			MongoTemplate mongoTemplate = vectorStore.<MongoTemplate>getNativeClient().orElseThrow();
			String collectionName = "bulk_nested_path_collection";
			MongoDBAtlasVectorStore store = MongoDBAtlasVectorStore
				.builder(mongoTemplate, new FixedDimensionEmbeddingModel(EMBEDDING_DIMENSIONS))
				.collectionName(collectionName)
				.vectorIndexName("bulk_nested_path_index")
				.pathName("vectors.embedding")
				.bulkUpsertBatchSize(BATCH_SIZE)
				.initializeSchema(true)
				.build();
			assertThatNoException().isThrownBy(store::afterPropertiesSet);
			awaitQueryable(mongoTemplate, collectionName, "bulk_nested_path_index");

			store.upsert(List.of(entry("nested", "first", Map.of())));
			// Replacing must keep the vector at the indexed path
			store.upsert(List
				.of(new EmbeddedDocument(new Document("nested", "second", Map.of()), new float[] { 0f, 1f, 0f, 0f })));

			org.bson.Document stored = mongoTemplate.getCollection(collectionName)
				.find(new org.bson.Document("_id", "nested"))
				.first();
			assertThat(stored).doesNotContainKeys("embedding", "vectors.embedding");
			assertThat(stored.get("vectors", org.bson.Document.class)).containsKey("embedding");
			SearchRequest request = SearchRequest.builder().topK(3).similarityThresholdAll().build();
			await().atMost(10, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(250)).untilAsserted(() -> {
				List<Document> results = store.similaritySearch(new float[] { 0f, 1f, 0f, 0f }, request);
				assertThat(results).hasSize(1);
				assertThat(results.get(0).getText()).isEqualTo("second");
			});
		});
	}

	private static VectorStore storeOnDefaultCollection(MongoTemplate mongoTemplate, int batchSize) {
		MongoDBAtlasVectorStore.Builder builder = MongoDBAtlasVectorStore.builder(mongoTemplate,
				new FixedDimensionEmbeddingModel(EMBEDDING_DIMENSIONS));
		if (batchSize != PER_DOCUMENT) {
			builder.bulkUpsertBatchSize(batchSize);
		}
		return builder.build();
	}

	private static EmbeddedDocument entry(String id, String content, Map<String, Object> metadata) {
		return new EmbeddedDocument(new Document(id, content, metadata), new float[] { 1f, 0f, 0f, 0f });
	}

	private static List<String> storedIds(MongoTemplate mongoTemplate) {
		return mongoTemplate.getCollection(MongoDBAtlasVectorStore.DEFAULT_VECTOR_COLLECTION_NAME)
			.find()
			.map(document -> document.getString("_id"))
			.into(new ArrayList<>());
	}

	private static org.bson.Document stored(MongoTemplate mongoTemplate, String id) {
		return mongoTemplate.getCollection(MongoDBAtlasVectorStore.DEFAULT_VECTOR_COLLECTION_NAME)
			.find(new org.bson.Document("_id", id))
			.first();
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
				.bulkUpsertBatchSize(BATCH_SIZE)
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
