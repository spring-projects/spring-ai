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

package org.springframework.ai.vectorstore.couchbase;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.couchbase.client.core.error.IndexNotFoundException;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.manager.query.CreatePrimaryQueryIndexOptions;
import com.couchbase.client.java.query.QueryOptions;
import com.couchbase.client.java.query.QueryScanConsistency;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.couchbase.CouchbaseContainer;
import org.testcontainers.couchbase.CouchbaseService;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.test.vectorstore.AbstractVectorStoreUpsertTests;
import org.springframework.ai.test.vectorstore.FixedDimensionEmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.awaitility.Awaitility.await;

/**
 * Upsert verification for {@link CouchbaseSearchVectorStore}, running the shared
 * {@link AbstractVectorStoreUpsertTests} suite.
 * <p>
 * The tests use a {@link FixedDimensionEmbeddingModel} instead of a real
 * {@link EmbeddingModel}: upsert never embeds, and read-back only needs a query vector.
 * Only the Couchbase container is required.
 *
 * @author Soby Chacko
 * @since 2.1.0
 */
@Testcontainers
class CouchbaseSearchVectorStoreUpsertIT extends AbstractVectorStoreUpsertTests {

	private static final int EMBEDDING_DIMENSIONS = 4;

	private static final String BUCKET = CouchbaseContainerMetadata.BUCKET_NAME;

	private static final String SCOPE = "upsertScope";

	private static final String COLLECTION = "upsertCollection";

	private static final String INDEX_NAME = "upsert-index";

	private static final String CONFIGURED_COLLECTION = "configuredCollection";

	private static final String CONFIGURED_INDEX_NAME = "upsert-configured-index";

	// Indexes the tests create on purpose cover this collection, so building them does
	// not slow down the shared index
	private static final String SIDE_COLLECTION = "sideCollection";

	private static final String MODEL_SIZED_INDEX_NAME = "upsert-model-sized-index";

	private static final String RACE_INDEX_NAME = "upsert-race-index";

	private static final QueryOptions REQUEST_PLUS = QueryOptions.queryOptions()
		.scanConsistency(QueryScanConsistency.REQUEST_PLUS);

	@Container
	static final CouchbaseContainer couchbaseContainer = new CouchbaseContainer(
			CouchbaseContainerMetadata.COUCHBASE_IMAGE_ENTERPRISE)
		.withCredentials(CouchbaseContainerMetadata.USERNAME, CouchbaseContainerMetadata.PASSWORD)
		.withEnabledServices(CouchbaseService.KV, CouchbaseService.QUERY, CouchbaseService.INDEX,
				CouchbaseService.SEARCH)
		// At the default 256 MB, the search service sometimes applies a write or delete
		// 10 to 30 s late, longer than the tests wait
		.withServiceQuota(CouchbaseService.SEARCH, 1024)
		.withBucket(CouchbaseContainerMetadata.bucketDefinition)
		.withStartupAttempts(4)
		.withStartupTimeout(Duration.ofSeconds(90));

	// For the stores the tests build themselves; the context's store has its own
	private static Cluster cluster;

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestApplication.class);

	@BeforeAll
	static void createCollections() {
		cluster = connectCluster();
		// Created up front, since the search service learns about a new collection a
		// little after it is created and refuses an index on it until then
		var collections = cluster.bucket(BUCKET).collections();
		collections.createScope(SCOPE);
		for (String name : List.of(COLLECTION, CONFIGURED_COLLECTION, SIDE_COLLECTION)) {
			collections.createCollection(SCOPE, name);
		}
	}

	@AfterAll
	static void disconnect() {
		cluster.close();
	}

	@BeforeEach
	void dropTestIndexes() {
		// The indexes a test creates on purpose start out missing
		for (String name : List.of(MODEL_SIZED_INDEX_NAME, RACE_INDEX_NAME)) {
			try {
				cluster.bucket(BUCKET).scope(SCOPE).searchIndexes().dropIndex(name);
			}
			catch (IndexNotFoundException ex) {
				// Already gone
			}
		}
	}

	@Override
	protected void executeTest(Consumer<VectorStore> testFunction) {
		this.contextRunner.run(context -> {
			// The store has created the scope, collection and index; start each test
			// from an empty collection
			emptyCollection();
			testFunction.accept(context.getBean(VectorStore.class));
		});
	}

	@Override
	protected int embeddingDimensions() {
		return EMBEDDING_DIMENSIONS;
	}

	@Override
	protected VectorStore createStoreOverExistingSchema(VectorStore schemaOwner, EmbeddingModel embeddingModel) {
		return storeBuilder(embeddingModel).build();
	}

	@Override
	protected VectorStore createStoreWithConfiguredDimensions(VectorStore schemaOwner, EmbeddingModel embeddingModel,
			int dimensions) {
		CouchbaseSearchVectorStore store = CouchbaseSearchVectorStore.builder(cluster, embeddingModel)
			.bucketName(BUCKET)
			.scopeName(SCOPE)
			.collectionName(CONFIGURED_COLLECTION)
			.vectorIndexName(CONFIGURED_INDEX_NAME)
			.dimensions(dimensions)
			.initializeSchema(true)
			.build();
		store.afterPropertiesSet();
		return store;
	}

	@Test
	void initializingOverAnExistingIndexWithoutEmbeddingModel() {
		executeTest(vectorStore -> {
			CallCountingEmbeddingModel embeddingModel = new CallCountingEmbeddingModel(EMBEDDING_DIMENSIONS + 3);

			// The shared store has already created the index
			CouchbaseSearchVectorStore store = storeBuilder(embeddingModel).initializeSchema(true).build();
			assertThatNoException().isThrownBy(store::afterPropertiesSet);

			store.upsert(List.of(embeddedDocument(UUID.randomUUID().toString(), "existing", Map.of("tag", "e"))));
			assertWrongDimensionRejected(store);
			assertThat(embeddingModel.calls()).isZero();
		});
	}

	@Test
	void startupFailsWhenTheConfiguredSizeDoesNotMatchTheIndex() {
		executeTest(vectorStore -> {
			CallCountingEmbeddingModel embeddingModel = new CallCountingEmbeddingModel(EMBEDDING_DIMENSIONS);

			// The shared store has already created the index with the suite's size
			CouchbaseSearchVectorStore store = storeBuilder(embeddingModel).dimensions(EMBEDDING_DIMENSIONS + 1)
				.initializeSchema(true)
				.build();

			assertThatIllegalStateException().isThrownBy(store::afterPropertiesSet)
				.withMessageContaining("(" + (EMBEDDING_DIMENSIONS + 1) + ")")
				.withMessageContaining(INDEX_NAME + " (" + EMBEDDING_DIMENSIONS + ")");
			assertThat(embeddingModel.calls()).isZero();
		});
	}

	@Test
	void upsertIsRefusedWithoutAnIndex() {
		executeTest(vectorStore -> {
			CallCountingEmbeddingModel embeddingModel = new CallCountingEmbeddingModel(EMBEDDING_DIMENSIONS);
			CouchbaseSearchVectorStore store = storeBuilder(embeddingModel).vectorIndexName("no-such-index").build();

			assertThatIllegalStateException()
				.isThrownBy(() -> store
					.upsert(List.of(embeddedDocument(UUID.randomUUID().toString(), "nowhere", Map.of("tag", "n")))))
				.withMessageContaining("no-such-index");
			assertThat(documentCount()).isZero();
			assertThat(embeddingModel.calls()).isZero();
		});
	}

	@Test
	void aNewIndexTakesTheModelSizeWhenNoneIsConfigured() {
		executeTest(vectorStore -> {
			CallCountingEmbeddingModel embeddingModel = new CallCountingEmbeddingModel(EMBEDDING_DIMENSIONS + 2);
			CouchbaseSearchVectorStore store = storeBuilder(embeddingModel).collectionName(SIDE_COLLECTION)
				.vectorIndexName(MODEL_SIZED_INDEX_NAME)
				.initializeSchema(true)
				.build();
			store.afterPropertiesSet();

			assertThat(store.createObservationContextBuilder("query").build().getDimensions())
				.isEqualTo(EMBEDDING_DIMENSIONS + 2);
			assertThat(embeddingModel.calls()).isPositive();
		});
	}

	@Test
	void startupOverAnIndexWithoutAVectorFieldForTheCollectionSaysSo() {
		executeTest(vectorStore -> {
			// The index exists, but only maps the main collection
			CallCountingEmbeddingModel embeddingModel = new CallCountingEmbeddingModel(EMBEDDING_DIMENSIONS);
			CouchbaseSearchVectorStore store = storeBuilder(embeddingModel).collectionName(SIDE_COLLECTION)
				.vectorIndexName(INDEX_NAME)
				.initializeSchema(true)
				.build();

			assertThatIllegalStateException().isThrownBy(store::afterPropertiesSet)
				.withMessageContaining("has no vector field")
				.withMessageContaining(SIDE_COLLECTION);
			assertThat(embeddingModel.calls()).isZero();
		});
	}

	@Test
	void storesStartingTogetherAgreeOnTheSizeOfTheIndex() {
		executeTest(vectorStore -> {
			// Both find the index missing and create it with different sizes; whichever
			// loses must read back the winner's size instead of failing
			CouchbaseSearchVectorStore first = storeBuilder(new FixedDimensionEmbeddingModel(EMBEDDING_DIMENSIONS))
				.collectionName(SIDE_COLLECTION)
				.vectorIndexName(RACE_INDEX_NAME)
				.initializeSchema(true)
				.build();
			CouchbaseSearchVectorStore second = storeBuilder(new FixedDimensionEmbeddingModel(EMBEDDING_DIMENSIONS + 1))
				.collectionName(SIDE_COLLECTION)
				.vectorIndexName(RACE_INDEX_NAME)
				.initializeSchema(true)
				.build();
			CountDownLatch start = new CountDownLatch(1);
			CompletableFuture<Void> a = CompletableFuture.runAsync(() -> startWhenReleased(first, start));
			CompletableFuture<Void> b = CompletableFuture.runAsync(() -> startWhenReleased(second, start));
			start.countDown();
			assertThatNoException().isThrownBy(() -> CompletableFuture.allOf(a, b).get(60, TimeUnit.SECONDS));

			Integer firstSize = first.createObservationContextBuilder("query").build().getDimensions();
			Integer secondSize = second.createObservationContextBuilder("query").build().getDimensions();
			assertThat(firstSize).isIn(EMBEDDING_DIMENSIONS, EMBEDDING_DIMENSIONS + 1).isEqualTo(secondSize);
		});
	}

	@Test
	void upsertReplacesTheWholeDocument() {
		executeTest(vectorStore -> {
			String id = UUID.randomUUID().toString();
			vectorStore.upsert(List.of(embeddedDocument(id, "first", Map.of("tag", "v1", "removed", "old"))));
			vectorStore.upsert(List.of(embeddedDocument(id, "second", Map.of("tag", "v2"))));

			CouchbaseSearchVectorStore.CouchbaseDocument stored = cluster.bucket(BUCKET)
				.scope(SCOPE)
				.collection(COLLECTION)
				.get(id)
				.contentAs(CouchbaseSearchVectorStore.CouchbaseDocument.class);
			assertThat(stored.content()).isEqualTo("second");
			assertThat(stored.metadata()).containsOnlyKeys("tag");
			assertThat(stored.embedding()).hasSize(EMBEDDING_DIMENSIONS);
		});
	}

	@Test
	void theLaterEntryForAnIdInABatchIsWhatIsStored() {
		executeTest(vectorStore -> {
			String id = UUID.randomUUID().toString();
			vectorStore.upsert(List.of(embeddedDocument(id, "earlier", Map.of("a", "1")),
					embeddedDocument(id, "later", Map.of("b", "2"))));

			await().atMost(10, TimeUnit.SECONDS)
				.pollInterval(Duration.ofMillis(250))
				.untilAsserted(() -> assertThat(readAll(vectorStore)).singleElement().satisfies(document -> {
					assertThat(document.getText()).isEqualTo("later");
					assertThat(document.getMetadata()).containsEntry("b", "2").doesNotContainKey("a");
				}));
		});
	}

	private static void startWhenReleased(CouchbaseSearchVectorStore store, CountDownLatch start) {
		try {
			start.await();
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(ex);
		}
		store.afterPropertiesSet();
	}

	private static CouchbaseSearchVectorStore.Builder storeBuilder(EmbeddingModel embeddingModel) {
		return CouchbaseSearchVectorStore.builder(cluster, embeddingModel)
			.bucketName(BUCKET)
			.scopeName(SCOPE)
			.collectionName(COLLECTION)
			.vectorIndexName(INDEX_NAME);
	}

	private static List<Document> readAll(VectorStore vectorStore) {
		return vectorStore
			.similaritySearch(SearchRequest.builder().query("read back").topK(10).similarityThresholdAll().build());
	}

	private static void emptyCollection() {
		var collection = cluster.bucket(BUCKET).scope(SCOPE).collection(COLLECTION);
		collection.queryIndexes()
			.createPrimaryIndex(CreatePrimaryQueryIndexOptions.createPrimaryQueryIndexOptions().ignoreIfExists(true));
		// REQUEST_PLUS, so documents written just before are deleted too
		cluster.bucket(BUCKET).scope(SCOPE).query("DELETE FROM `" + COLLECTION + "`", REQUEST_PLUS);
		await().atMost(30, TimeUnit.SECONDS)
			.pollInterval(Duration.ofMillis(250))
			.until(() -> cluster.bucket(BUCKET).scope(SCOPE).searchIndexes().getIndexedDocumentsCount(INDEX_NAME) == 0);
	}

	private static long documentCount() {
		return cluster.bucket(BUCKET)
			.scope(SCOPE)
			.query("SELECT RAW COUNT(*) FROM `" + COLLECTION + "`", REQUEST_PLUS)
			.rowsAs(Long.class)
			.get(0);
	}

	private static Cluster connectCluster() {
		return Cluster.connect(couchbaseContainer.getConnectionString(), couchbaseContainer.getUsername(),
				couchbaseContainer.getPassword());
	}

	@SpringBootConfiguration
	public static class TestApplication {

		@Bean
		public CouchbaseSearchVectorStore vectorStore(EmbeddingModel embeddingModel) {
			// dot_product, the default, also ranks by vector length, so only l2_norm
			// ranks every one of the suite's vectors nearest to itself
			return CouchbaseSearchVectorStore.builder(connectCluster(), embeddingModel)
				.bucketName(BUCKET)
				.scopeName(SCOPE)
				.collectionName(COLLECTION)
				.vectorIndexName(INDEX_NAME)
				.similarityFunction(CouchbaseSimilarityFunction.l2_norm)
				.initializeSchema(true)
				.build();
		}

		@Bean
		public EmbeddingModel embeddingModel() {
			return new FixedDimensionEmbeddingModel(EMBEDDING_DIMENSIONS);
		}

	}

}
