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

package org.springframework.ai.vectorstore.neo4j;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.neo4j.driver.TransactionCallback;
import org.neo4j.driver.TransactionContext;
import org.neo4j.driver.exceptions.ClientException;
import org.testcontainers.containers.Neo4jContainer;
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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Upsert verification for {@link Neo4jVectorStore}, running the shared
 * {@link AbstractVectorStoreUpsertTests} suite.
 * <p>
 * The tests use a {@link FixedDimensionEmbeddingModel} instead of a real
 * {@link EmbeddingModel}: upsert never embeds, and read-back only needs a query vector.
 * Only the Neo4j container is required.
 *
 * @author Soby Chacko
 * @since 2.1.0
 */
@Testcontainers
class Neo4jVectorStoreUpsertIT extends AbstractVectorStoreUpsertTests {

	private static final int EMBEDDING_DIMENSIONS = 4;

	private static final String INDEX_NAME = "upsert-index";

	private static final String CONFIGURED_LABEL = "ConfiguredDocument";

	private static final String CONFIGURED_INDEX_NAME = "upsert-configured-index";

	private static final String ANY_SIZE_LABEL = "AnySizeDocument";

	private static final String ANY_SIZE_INDEX_NAME = "upsert-any-size-index";

	@Container
	static Neo4jContainer<?> neo4jContainer = new Neo4jContainer<>(Neo4jImage.DEFAULT_IMAGE).withRandomPassword();

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestApplication.class);

	@BeforeEach
	void cleanDatabase() {
		// Each test starts from an index the shared store creates
		try (Driver driver = driver()) {
			for (String name : List.of(INDEX_NAME, CONFIGURED_INDEX_NAME, ANY_SIZE_INDEX_NAME)) {
				driver.executableQuery("DROP INDEX `%s` IF EXISTS".formatted(name)).execute();
			}
			driver.executableQuery("MATCH (n) DETACH DELETE n").execute();
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
		Driver driver = schemaOwner.<Driver>getNativeClient().orElseThrow();
		return Neo4jVectorStore.builder(driver, embeddingModel).indexName(INDEX_NAME).build();
	}

	@Override
	protected VectorStore createStoreWithConfiguredDimensions(VectorStore schemaOwner, EmbeddingModel embeddingModel,
			int dimensions) {
		Driver driver = schemaOwner.<Driver>getNativeClient().orElseThrow();
		Neo4jVectorStore store = Neo4jVectorStore.builder(driver, embeddingModel)
			.label(CONFIGURED_LABEL)
			.constraintName(CONFIGURED_LABEL + "_unique_idx")
			.indexName(CONFIGURED_INDEX_NAME)
			.embeddingDimension(dimensions)
			.initializeSchema(true)
			.build();
		store.afterPropertiesSet();
		return store;
	}

	@Test
	void initializingOverAnExistingIndexWithoutEmbeddingModel() {
		executeTest(vectorStore -> {
			Driver driver = vectorStore.<Driver>getNativeClient().orElseThrow();
			CallCountingEmbeddingModel embeddingModel = new CallCountingEmbeddingModel(EMBEDDING_DIMENSIONS + 3);

			// The shared store has already created the index
			Neo4jVectorStore store = Neo4jVectorStore.builder(driver, embeddingModel)
				.indexName(INDEX_NAME)
				.initializeSchema(true)
				.build();
			assertThatNoException().isThrownBy(store::afterPropertiesSet);

			store.upsert(List.of(embeddedDocument(UUID.randomUUID().toString(), "existing", Map.of("tag", "e"))));
			assertWrongDimensionRejected(store);
			assertThat(embeddingModel.calls()).isZero();
		});
	}

	@Test
	void upsertIsRefusedWithoutAnIndex() {
		executeTest(vectorStore -> {
			Driver driver = vectorStore.<Driver>getNativeClient().orElseThrow();
			CallCountingEmbeddingModel embeddingModel = new CallCountingEmbeddingModel(EMBEDDING_DIMENSIONS);
			Neo4jVectorStore store = Neo4jVectorStore.builder(driver, embeddingModel)
				.indexName("no-such-index")
				.build();

			assertThatIllegalStateException()
				.isThrownBy(() -> store
					.upsert(List.of(embeddedDocument(UUID.randomUUID().toString(), "nowhere", Map.of("tag", "n")))))
				.withMessageContaining("no-such-index");
			assertThat(readAll(vectorStore, 10)).isEmpty();
			assertThat(embeddingModel.calls()).isZero();
		});
	}

	@Test
	void failedIndexReadRefusesUpsertAndIsReadAgainLater() {
		executeTest(vectorStore -> {
			Driver realDriver = vectorStore.<Driver>getNativeClient().orElseThrow();
			AtomicBoolean failIndexReads = new AtomicBoolean(true);
			CallCountingEmbeddingModel embeddingModel = new CallCountingEmbeddingModel(EMBEDDING_DIMENSIONS + 3);
			Neo4jVectorStore store = Neo4jVectorStore
				.builder(driverWithFailingIndexReads(realDriver, failIndexReads), embeddingModel)
				.indexName(INDEX_NAME)
				.build();
			String id = UUID.randomUUID().toString();

			// Neo4j would store a vector of any size, so nothing is written while the
			// size
			// is unknown
			assertThatIllegalStateException()
				.isThrownBy(() -> store.upsert(List.of(embeddedDocument(id, "during outage", Map.of("tag", "o")))));
			assertThat(readAll(vectorStore, 10)).isEmpty();

			// A wrong-size query is still rejected, by Neo4j
			SearchRequest request = SearchRequest.builder().topK(3).similarityThresholdAll().build();
			assertThatExceptionOfType(ClientException.class)
				.isThrownBy(() -> store.similaritySearch(new float[] { 1f, 0f, 0f }, request))
				.withMessageContaining("indexed vectors have " + EMBEDDING_DIMENSIONS);

			// Once the index can be read again its size is read and applies
			failIndexReads.set(false);
			store.upsert(List.of(embeddedDocument(id, "after outage", Map.of("tag", "a"))));
			assertThat(readAll(vectorStore, 10)).extracting(Document::getId).containsExactly(id);
			assertWrongDimensionRejected(store);
			assertThat(embeddingModel.calls()).isZero();
		});
	}

	@Test
	void upsertKeepsWhatTheStoreDoesNotOwn() {
		executeTest(vectorStore -> {
			Driver driver = vectorStore.<Driver>getNativeClient().orElseThrow();
			String id = UUID.randomUUID().toString();
			vectorStore.upsert(List.of(embeddedDocument(id, "first", Map.of("tag", "v1", "removed", "old"))));
			driver.executableQuery("""
					MATCH (n:Document {id: $id})
					SET n.foreign = 'keep', n:Extra
					CREATE (n)-[:LINKED_TO]->(:Other)""").withParameters(Map.of("id", id)).execute();

			vectorStore.upsert(List.of(embeddedDocument(id, "second", Map.of("tag", "v2"))));

			var record = driver.executableQuery("""
					MATCH (n:Document {id: $id})
					RETURN n.text AS text, n.foreign AS foreign, labels(n) AS labels,
						[k IN keys(n) WHERE k STARTS WITH 'metadata.'] AS metadataKeys,
						COUNT { (n)-[:LINKED_TO]->() } AS links""")
				.withParameters(Map.of("id", id))
				.execute()
				.records()
				.get(0);
			assertThat(record.get("text").asString()).isEqualTo("second");
			assertThat(record.get("foreign").asString()).isEqualTo("keep");
			assertThat(record.get("labels").asList()).containsExactlyInAnyOrder("Document", "Extra");
			assertThat(record.get("metadataKeys").asList()).containsExactly("metadata.tag");
			assertThat(record.get("links").asInt()).isEqualTo(1);
		});
	}

	@Test
	void theLaterEntryForAnIdInABatchIsWhatIsStored() {
		executeTest(vectorStore -> {
			String id = UUID.randomUUID().toString();

			vectorStore.upsert(List.of(embeddedDocument(id, "earlier", Map.of("a", "1")),
					embeddedDocument(id, "later", Map.of("b", "2"))));

			assertThat(readAll(vectorStore, 10)).singleElement().satisfies(document -> {
				assertThat(document.getText()).isEqualTo("later");
				assertThat(document.getMetadata()).containsEntry("b", "2").doesNotContainKey("a");
			});
		});
	}

	@Test
	void aConcurrentReplacementWaitsAndItsKeysAreRemovedToo() {
		executeTest(vectorStore -> {
			Driver driver = vectorStore.<Driver>getNativeClient().orElseThrow();
			String id = UUID.randomUUID().toString();
			vectorStore.upsert(List.of(embeddedDocument(id, "seed", Map.of("a", "0"))));

			// Writer A pauses between reading the metadata keys and writing, until writer
			// B has finished or 3 seconds have passed
			CountDownLatch aHasReadTheKeys = new CountDownLatch(1);
			CountDownLatch bIsDone = new CountDownLatch(1);
			Neo4jVectorStore writerA = Neo4jVectorStore.builder(driverWithStatementHook(driver, query -> {
				if (query.contains("setNodeVectorProperty")) {
					aHasReadTheKeys.countDown();
					awaitQuietly(bIsDone, 3);
				}
			}), new FixedDimensionEmbeddingModel(EMBEDDING_DIMENSIONS)).indexName(INDEX_NAME).build();

			ExecutorService executor = Executors.newFixedThreadPool(2);
			try {
				Future<?> a = executor
					.submit(() -> writerA.upsert(List.of(embeddedDocument(id, "a", Map.of("a", "1")))));
				assertThat(awaitQuietly(aHasReadTheKeys, 30)).isTrue();
				Future<?> b = executor.submit(() -> {
					vectorStore.upsert(List.of(embeddedDocument(id, "b", Map.of("b", "1"))));
					bIsDone.countDown();
				});
				a.get(30, TimeUnit.SECONDS);
				b.get(30, TimeUnit.SECONDS);
			}
			catch (Exception ex) {
				throw new IllegalStateException(ex);
			}
			finally {
				executor.shutdownNow();
			}

			// B waits for A's lock and then removes A's key; without the lock A would
			// keep B's key
			var keys = driver.executableQuery("""
					MATCH (n:Document {id: $id})
					RETURN [k IN keys(n) WHERE k STARTS WITH 'metadata.'] AS keys""")
				.withParameters(Map.of("id", id))
				.execute()
				.records()
				.get(0)
				.get("keys")
				.asList();
			assertThat(keys).containsExactly("metadata.b");
		});
	}

	@Test
	void startupFailsWhenAnIndexUnderAnotherNameCoversTheSameProperty() {
		executeTest(vectorStore -> {
			Driver driver = vectorStore.<Driver>getNativeClient().orElseThrow();
			driver
				.executableQuery("""
						CREATE VECTOR INDEX `%s` FOR (n:%s) ON (n.embedding)
						OPTIONS {indexConfig: {`vector.dimensions`: %d, `vector.similarity_function`: 'cosine'}}"""
					.formatted(ANY_SIZE_INDEX_NAME, ANY_SIZE_LABEL, EMBEDDING_DIMENSIONS))
				.execute();
			Neo4jVectorStore store = Neo4jVectorStore
				.builder(driver, new FixedDimensionEmbeddingModel(EMBEDDING_DIMENSIONS))
				.label(ANY_SIZE_LABEL)
				.constraintName(ANY_SIZE_LABEL + "_unique_idx")
				.indexName("upsert-other-name")
				.initializeSchema(true)
				.build();

			assertThatIllegalStateException().isThrownBy(store::afterPropertiesSet)
				.withMessageContaining("upsert-other-name");
		});
	}

	@Test
	void anIndexWithoutDimensionsTakesVectorsOfAnySize() {
		executeTest(vectorStore -> {
			Driver driver = vectorStore.<Driver>getNativeClient().orElseThrow();
			driver
				.executableQuery("""
						CREATE VECTOR INDEX `%s` FOR (n:%s) ON (n.embedding)
						OPTIONS {indexConfig: {`vector.similarity_function`: 'cosine'}}"""
					.formatted(ANY_SIZE_INDEX_NAME, ANY_SIZE_LABEL))
				.execute();
			driver.executableQuery("CALL db.awaitIndexes()").execute();
			CallCountingEmbeddingModel embeddingModel = new CallCountingEmbeddingModel(EMBEDDING_DIMENSIONS);
			Neo4jVectorStore store = Neo4jVectorStore.builder(driver, embeddingModel)
				.label(ANY_SIZE_LABEL)
				.indexName(ANY_SIZE_INDEX_NAME)
				.build();
			String smallId = UUID.randomUUID().toString();
			String largeId = UUID.randomUUID().toString();

			store.upsert(List.of(new EmbeddedDocument(new Document(smallId, "small", Map.of()), new float[] { 1f, 0f }),
					new EmbeddedDocument(new Document(largeId, "large", Map.of()),
							new float[] { 1f, 0f, 0f, 0f, 0f })));

			SearchRequest request = SearchRequest.builder().topK(5).similarityThresholdAll().build();
			assertThat(store.similaritySearch(new float[] { 1f, 0f }, request)).extracting(Document::getId)
				.containsExactly(smallId);
			assertThat(store.similaritySearch(new float[] { 1f, 0f, 0f, 0f, 0f }, request)).extracting(Document::getId)
				.containsExactly(largeId);
			assertThat(embeddingModel.calls()).isZero();
		});
	}

	private static List<Document> readAll(VectorStore vectorStore, int topK) {
		return vectorStore
			.similaritySearch(SearchRequest.builder().query("read back").topK(topK).similarityThresholdAll().build());
	}

	private static Driver driverWithFailingIndexReads(Driver driver, AtomicBoolean failIndexReads) {
		return driverWithStatementHook(driver, query -> {
			if (failIndexReads.get() && query.startsWith("SHOW VECTOR INDEXES")) {
				// Not a transient error, so the driver does not retry it for 30 seconds
				throw new ClientException("Simulated index read failure");
			}
		});
	}

	/**
	 * A driver that calls the hook with every statement run in a managed transaction,
	 * before the statement runs.
	 */
	private static Driver driverWithStatementHook(Driver driver, Consumer<String> hook) {
		return proxy(Driver.class, driver, (method, args) -> {
			Object result = method.invoke(driver, args);
			return (result instanceof Session session) ? sessionWithStatementHook(session, hook) : result;
		});
	}

	private static Session sessionWithStatementHook(Session session, Consumer<String> hook) {
		return proxy(Session.class, session, (method, args) -> {
			if (args != null && args.length > 0 && args[0] instanceof TransactionCallback<?> callback) {
				args[0] = (TransactionCallback<?>) tx -> callback
					.execute(proxy(TransactionContext.class, tx, (txMethod, txArgs) -> {
						if (txMethod.getName().equals("run") && txArgs[0] instanceof String query) {
							hook.accept(query);
						}
						return txMethod.invoke(tx, txArgs);
					}));
			}
			return method.invoke(session, args);
		});
	}

	private static boolean awaitQuietly(CountDownLatch latch, int seconds) {
		try {
			return latch.await(seconds, TimeUnit.SECONDS);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	@SuppressWarnings("unchecked")
	private static <T> T proxy(Class<T> type, T target, ProxyHandler handler) {
		return (T) Proxy.newProxyInstance(Neo4jVectorStoreUpsertIT.class.getClassLoader(), new Class<?>[] { type },
				(proxy, method, args) -> {
					try {
						return handler.invoke(method, args);
					}
					catch (InvocationTargetException ex) {
						throw ex.getCause();
					}
				});
	}

	private static Driver driver() {
		return GraphDatabase.driver(neo4jContainer.getBoltUrl(),
				AuthTokens.basic("neo4j", neo4jContainer.getAdminPassword()));
	}

	@FunctionalInterface
	private interface ProxyHandler {

		Object invoke(Method method, Object[] args) throws Throwable;

	}

	@SpringBootConfiguration
	public static class TestApplication {

		@Bean
		public VectorStore vectorStore(Driver driver, EmbeddingModel embeddingModel) {
			return Neo4jVectorStore.builder(driver, embeddingModel)
				.indexName(INDEX_NAME)
				.initializeSchema(true)
				.build();
		}

		@Bean
		public Driver driver() {
			return Neo4jVectorStoreUpsertIT.driver();
		}

		@Bean
		public EmbeddingModel embeddingModel() {
			return new FixedDimensionEmbeddingModel(EMBEDDING_DIMENSIONS);
		}

	}

}
