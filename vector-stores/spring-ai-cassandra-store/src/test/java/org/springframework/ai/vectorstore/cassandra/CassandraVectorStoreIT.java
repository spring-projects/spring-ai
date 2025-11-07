/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.vectorstore.cassandra;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.api.core.servererrors.InvalidQueryException;
import com.datastax.oss.driver.api.core.servererrors.SyntaxError;
import com.datastax.oss.driver.api.core.type.DataTypes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.testcontainers.cassandra.CassandraContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentMetadata;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.test.vectorstore.BaseVectorStoreTests;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.cassandra.CassandraVectorStore.SchemaColumn;
import org.springframework.ai.vectorstore.cassandra.CassandraVectorStore.SchemaColumnTags;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Use `mvn failsafe:integration-test -Dit.test=CassandraVectorStoreIT`
 *
 * @author Mick Semb Wever
 * @author Thomas Vitale
 * @author Soby Chacko
 * @since 1.0.0
 */
@Testcontainers
class CassandraVectorStoreIT extends BaseVectorStoreTests {

	@Container
	static CassandraContainer cassandraContainer = new CassandraContainer(CassandraImage.DEFAULT_IMAGE);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestApplication.class);

	private static List<Document> documents() {
		return List.of(new Document("1", getText("classpath:/test/data/spring.ai.txt"), Map.of("meta1", "meta1")),
				new Document("2", getText("classpath:/test/data/time.shelter.txt"), Map.of()),
				new Document("3", getText("classpath:/test/data/great.depression.txt"),
						Map.of("meta2", "meta2", "something_extra", "blue")));
	}

	private static String getText(String uri) {
		var resource = new DefaultResourceLoader().getResource(uri);
		try {
			return resource.getContentAsString(StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static CassandraVectorStore.Builder storeBuilder(CqlSession cqlSession, EmbeddingModel embeddingModel) {
		return CassandraVectorStore.builder(embeddingModel)
			.session(cqlSession)
			.keyspace("test_" + CassandraVectorStore.DEFAULT_KEYSPACE_NAME);
	}

	private static CassandraVectorStore createTestStore(ApplicationContext context, SchemaColumn... metadataFields) {
		CassandraVectorStore.Builder builder = storeBuilder(context.getBean(CqlSession.class),
				context.getBean(EmbeddingModel.class))
			.addMetadataColumns(metadataFields);

		return createTestStore(context, builder);
	}

	private static CassandraVectorStore createTestStore(ApplicationContext context,
			CassandraVectorStore.Builder builder) {
		CassandraVectorStore.dropKeyspace(builder);
		CassandraVectorStore store = builder.build();
		return store;
	}

	@Override
	protected void executeTest(Consumer<VectorStore> testFunction) {
		this.contextRunner.run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);
			testFunction.accept(vectorStore);
		});
	}

	@Override
	protected Document createDocument(String country, Integer year) {
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("country", country);
		if (year != null) {
			metadata.put("year", year.shortValue());
		}
		return new Document("The World is Big and Salvation Lurks Around the Corner", metadata);
	}

	@Test
	void ensureBeanGetsCreated() {
		this.contextRunner.run(context -> {
			try (CassandraVectorStore store = context.getBean(CassandraVectorStore.class)) {
				Assertions.assertNotNull(store);
				store.checkSchemaValid();
			}
		});
	}

	@Test
	void addAndSearch() {
		this.contextRunner.run(context -> {
			try (CassandraVectorStore store = createTestStore(context, new SchemaColumn("meta1", DataTypes.TEXT),
					new SchemaColumn("meta2", DataTypes.TEXT))) {

				List<Document> documents = documents();
				store.add(documents);

				List<Document> results = store
					.similaritySearch(SearchRequest.builder().query("Spring").topK(1).build());

				assertThat(results).hasSize(1);
				Document resultDoc = results.get(0);
				assertThat(resultDoc.getId()).isEqualTo(documents().get(0).getId());

				assertThat(resultDoc.getText()).contains(
						"Spring AI provides abstractions that serve as the foundation for developing AI applications.");

				assertThat(resultDoc.getMetadata()).hasSize(2);
				assertThat(resultDoc.getMetadata()).containsKeys("meta1", DocumentMetadata.DISTANCE.value());

				// Remove all documents from the store
				store.delete(documents().stream().map(doc -> doc.getId()).toList());

				results = store.similaritySearch(SearchRequest.builder().query("Spring").topK(1).build());
				assertThat(results).isEmpty();
			}
		});
	}

	@Test
	void searchWithPartitionFilter() throws InterruptedException {
		this.contextRunner.run(context -> {

			try (CassandraVectorStore store = createTestStore(context,
					new SchemaColumn("year", DataTypes.SMALLINT, SchemaColumnTags.INDEXED))) {

				var bgDocument = new Document("BG", "The World is Big and Salvation Lurks Around the Corner",
						Map.of("year", (short) 2020));
				var nlDocument = new Document("NL", "The World is Big and Salvation Lurks Around the Corner",
						java.util.Collections.emptyMap());
				var bgDocument2 = new Document("BG2", "The World is Big and Salvation Lurks Around the Corner",
						Map.of("year", (short) 2023));

				store.add(List.of(bgDocument, nlDocument, bgDocument2));

				List<Document> results = store
					.similaritySearch(SearchRequest.builder().query("The World").topK(5).build());
				assertThat(results).hasSize(3);

				results = store.similaritySearch(SearchRequest.builder()
					.query("The World")
					.topK(5)
					.similarityThresholdAll()
					.filterExpression(java.lang.String.format("%s == 'NL'", CassandraVectorStore.DEFAULT_ID_NAME))
					.build());

				assertThat(results).hasSize(1);
				assertThat(results.get(0).getId()).isEqualTo(nlDocument.getId());

				results = store.similaritySearch(SearchRequest.builder()
					.query("The World")
					.topK(5)
					.similarityThresholdAll()
					.filterExpression(java.lang.String.format("%s == 'BG2'", CassandraVectorStore.DEFAULT_ID_NAME))
					.build());

				assertThat(results).hasSize(1);
				assertThat(results.get(0).getId()).isEqualTo(bgDocument2.getId());

				results = store.similaritySearch(SearchRequest.builder()
					.query("The World")
					.topK(5)
					.similarityThresholdAll()
					.filterExpression(
							java.lang.String.format("%s == 'BG' && year == 2020", CassandraVectorStore.DEFAULT_ID_NAME))
					.build());

				assertThat(results).hasSize(1);
				assertThat(results.get(0).getId()).isEqualTo(bgDocument.getId());

				// cassandra server will throw an error
				Assertions.assertThrows(SyntaxError.class,
						() -> store.similaritySearch(SearchRequest.builder()
							.query("The World")
							.topK(5)
							.similarityThresholdAll()
							.filterExpression(java.lang.String.format("NOT(%s == 'BG' && year == 2020)",
									CassandraVectorStore.DEFAULT_ID_NAME))
							.build()));
			}
		});
	}

	@Test
	void unsearchableFilters() {
		this.contextRunner.run(context -> {
			try (CassandraVectorStore store = context.getBean(CassandraVectorStore.class)) {

				var bgDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
						Map.of("country", "BG", "year", (short) 2020));
				var nlDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
						Map.of("country", "NL"));
				var bgDocument2 = new Document("The World is Big and Salvation Lurks Around the Corner",
						Map.of("country", "BG", "year", (short) 2023));

				store.add(List.of(bgDocument, nlDocument, bgDocument2));

				List<Document> results = store
					.similaritySearch(SearchRequest.builder().query("The World").topK(5).build());
				assertThat(results).hasSize(3);

				Assertions.assertThrows(InvalidQueryException.class,
						() -> store.similaritySearch(SearchRequest.builder()
							.query("The World")
							.topK(5)
							.similarityThresholdAll()
							.filterExpression("country == 'NL'")
							.build()));
			}
		});
	}

	@Test
	void searchWithFilters() throws InterruptedException {
		this.contextRunner.run(context -> {

			try (CassandraVectorStore store = createTestStore(context,
					new SchemaColumn("country", DataTypes.TEXT, SchemaColumnTags.INDEXED),
					new SchemaColumn("year", DataTypes.SMALLINT, SchemaColumnTags.INDEXED))) {

				var bgDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
						Map.of("country", "BG", "year", (short) 2020));
				var nlDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
						Map.of("country", "NL"));
				var bgDocument2 = new Document("The World is Big and Salvation Lurks Around the Corner",
						Map.of("country", "BG", "year", (short) 2023));

				store.add(List.of(bgDocument, nlDocument, bgDocument2));

				List<Document> results = store
					.similaritySearch(SearchRequest.builder().query("The World").topK(5).build());
				assertThat(results).hasSize(3);

				results = store.similaritySearch(SearchRequest.builder()
					.query("The World")
					.topK(5)
					.similarityThresholdAll()
					.filterExpression("country == 'NL'")
					.build());
				assertThat(results).hasSize(1);
				assertThat(results.get(0).getId()).isEqualTo(nlDocument.getId());

				results = store.similaritySearch(SearchRequest.builder()
					.query("The World")
					.topK(5)
					.similarityThresholdAll()
					.filterExpression("country == 'BG'")
					.build());

				assertThat(results).hasSize(2);
				assertThat(results.get(0).getId()).isIn(bgDocument.getId(), bgDocument2.getId());
				assertThat(results.get(1).getId()).isIn(bgDocument.getId(), bgDocument2.getId());

				results = store.similaritySearch(SearchRequest.builder()
					.query("The World")
					.topK(5)
					.similarityThresholdAll()
					.filterExpression("country == 'BG' && year == 2020")
					.build());

				assertThat(results).hasSize(1);
				assertThat(results.get(0).getId()).isEqualTo(bgDocument.getId());

				// cassandra server will throw an error
				Assertions.assertThrows(SyntaxError.class,
						() -> store.similaritySearch(SearchRequest.builder()
							.query("The World")
							.topK(5)
							.similarityThresholdAll()
							.filterExpression("country == 'BG' || year == 2020")
							.build()));

				// cassandra server will throw an error
				Assertions.assertThrows(SyntaxError.class,
						() -> store.similaritySearch(SearchRequest.builder()
							.query("The World")
							.topK(5)
							.similarityThresholdAll()
							.filterExpression("NOT(country == 'BG' && year == 2020)")
							.build()));
			}
		});
	}

	@Test
	void documentUpdate() {
		this.contextRunner.run(context -> {
			try (CassandraVectorStore store = context.getBean(CassandraVectorStore.class)) {

				Document document = new Document(UUID.randomUUID().toString(), "Spring AI rocks!!",
						Collections.singletonMap("meta1", "meta1"));

				store.add(List.of(document));

				List<Document> results = store
					.similaritySearch(SearchRequest.builder().query("Spring").topK(5).build());

				assertThat(results).hasSize(1);
				Document resultDoc = results.get(0);
				assertThat(resultDoc.getId()).isEqualTo(document.getId());
				assertThat(resultDoc.getText()).isEqualTo("Spring AI rocks!!");
				assertThat(resultDoc.getMetadata()).containsKey("meta1");

				Document sameIdDocument = new Document(document.getId(),
						"The World is Big and Salvation Lurks Around the Corner",
						Collections.singletonMap("meta2", "meta2"));

				store.add(List.of(sameIdDocument));

				results = store.similaritySearch(SearchRequest.builder().query("FooBar").topK(5).build());

				assertThat(results).hasSize(1);
				resultDoc = results.get(0);
				assertThat(resultDoc.getId()).isEqualTo(document.getId());
				assertThat(resultDoc.getText()).isEqualTo("The World is Big and Salvation Lurks Around the Corner");
				assertThat(resultDoc.getMetadata()).containsKeys("meta2", DocumentMetadata.DISTANCE.value());

				store.delete(List.of(document.getId()));
			}
		});
	}

	@Test
	void searchWithThreshold() {
		this.contextRunner.run(context -> {
			try (CassandraVectorStore store = context.getBean(CassandraVectorStore.class)) {
				store.add(documents());

				List<Document> fullResult = store
					.similaritySearch(SearchRequest.builder().query("Spring").topK(5).similarityThresholdAll().build());

				List<Double> scores = fullResult.stream().map(Document::getScore).toList();

				assertThat(scores).hasSize(3);

				double similarityThreshold = (scores.get(0) + scores.get(1)) / 2;

				List<Document> results = store.similaritySearch(SearchRequest.builder()
					.query("Spring")
					.topK(5)
					.similarityThreshold(similarityThreshold)
					.build());

				assertThat(results).hasSize(1);
				Document resultDoc = results.get(0);
				assertThat(resultDoc.getId()).isEqualTo(documents().get(0).getId());

				assertThat(resultDoc.getText()).contains(
						"Spring AI provides abstractions that serve as the foundation for developing AI applications.");

				assertThat(resultDoc.getMetadata()).containsKeys("meta1", DocumentMetadata.DISTANCE.value());
				assertThat(resultDoc.getScore()).isGreaterThanOrEqualTo(similarityThreshold);
			}
		});
	}

	@Test
	protected void deleteByFilter() {
		this.contextRunner.run(context -> {
			try (CassandraVectorStore store = createTestStore(context,
					new SchemaColumn("country", DataTypes.TEXT, SchemaColumnTags.INDEXED),
					new SchemaColumn("year", DataTypes.SMALLINT, SchemaColumnTags.INDEXED))) {

				var bgDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
						Map.of("country", "BG", "year", (short) 2020));
				var nlDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
						Map.of("country", "NL"));
				var bgDocument2 = new Document("The World is Big and Salvation Lurks Around the Corner",
						Map.of("country", "BG", "year", (short) 2023));

				store.add(List.of(bgDocument, nlDocument, bgDocument2));

				// Verify initial state
				List<Document> results = store
					.similaritySearch(SearchRequest.builder().query("The World").topK(5).build());
				assertThat(results).hasSize(3);

				// Delete documents with country = BG
				Filter.Expression filterExpression = new Filter.Expression(Filter.ExpressionType.EQ,
						new Filter.Key("country"), new Filter.Value("BG"));

				store.delete(filterExpression);

				results = store.similaritySearch(
						SearchRequest.builder().query("The World").topK(5).similarityThresholdAll().build());

				assertThat(results).hasSize(1);
				assertThat(results.get(0).getMetadata()).containsEntry("country", "NL");
			}
		});
	}

	@Test
	protected void deleteWithStringFilterExpression() {
		this.contextRunner.run(context -> {
			try (CassandraVectorStore store = createTestStore(context,
					new SchemaColumn("country", DataTypes.TEXT, SchemaColumnTags.INDEXED),
					new SchemaColumn("year", DataTypes.SMALLINT, SchemaColumnTags.INDEXED))) {

				var bgDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
						Map.of("country", "BG", "year", (short) 2020));
				var nlDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
						Map.of("country", "NL"));
				var bgDocument2 = new Document("The World is Big and Salvation Lurks Around the Corner",
						Map.of("country", "BG", "year", (short) 2023));

				store.add(List.of(bgDocument, nlDocument, bgDocument2));

				// Verify initial state
				List<Document> results = store
					.similaritySearch(SearchRequest.builder().query("The World").topK(5).build());
				assertThat(results).hasSize(3);

				store.delete("country == 'BG'");

				results = store.similaritySearch(
						SearchRequest.builder().query("The World").topK(5).similarityThresholdAll().build());

				assertThat(results).hasSize(1);
				assertThat(results.get(0).getMetadata()).containsEntry("country", "NL");
			}
		});
	}

	@Test
	void deleteWithComplexFilterExpression() {
		this.contextRunner.run(context -> {
			try (CassandraVectorStore store = createTestStore(context,
					new SchemaColumn("type", DataTypes.TEXT, SchemaColumnTags.INDEXED),
					new SchemaColumn("priority", DataTypes.SMALLINT, SchemaColumnTags.INDEXED))) {

				var doc1 = new Document("Content 1", Map.of("type", "A", "priority", (short) 1));
				var doc2 = new Document("Content 2", Map.of("type", "A", "priority", (short) 2));
				var doc3 = new Document("Content 3", Map.of("type", "B", "priority", (short) 1));

				store.add(List.of(doc1, doc2, doc3));

				// Complex filter expression: (type == 'A' AND priority > 1)
				Filter.Expression priorityFilter = new Filter.Expression(Filter.ExpressionType.GT,
						new Filter.Key("priority"), new Filter.Value((short) 1));
				Filter.Expression typeFilter = new Filter.Expression(Filter.ExpressionType.EQ, new Filter.Key("type"),
						new Filter.Value("A"));
				Filter.Expression complexFilter = new Filter.Expression(Filter.ExpressionType.AND, typeFilter,
						priorityFilter);

				store.delete(complexFilter);

				var results = store.similaritySearch(
						SearchRequest.builder().query("Content").topK(5).similarityThresholdAll().build());

				assertThat(results).hasSize(2);
				assertThat(results.stream().map(doc -> doc.getMetadata().get("type")).collect(Collectors.toList()))
					.containsExactlyInAnyOrder("A", "B");
				assertThat(results.stream()
					.map(doc -> ((Short) doc.getMetadata().get("priority")).intValue())
					.collect(Collectors.toList())).containsExactlyInAnyOrder(1, 1);
			}
		});
	}

	@Test
	void getNativeClientTest() {
		this.contextRunner.run(context -> {
			CassandraVectorStore vectorStore = context.getBean(CassandraVectorStore.class);
			Optional<CqlSession> nativeClient = vectorStore.getNativeClient();
			assertThat(nativeClient).isPresent();
		});
	}

	@Test
	void searchWithCollectionFilter() {
		this.contextRunner.run(context -> {
			try (CassandraVectorStore store = createTestStore(context,
					new SchemaColumn("currencies", DataTypes.listOf(DataTypes.TEXT), SchemaColumnTags.INDEXED))) {

				// Create test documents with different currency lists
				var btcDocument = new Document("BTC_doc", "Bitcoin document", Map.of("currencies", List.of("BTC")));
				var ethDocument = new Document("ETH_doc", "Ethereum document", Map.of("currencies", List.of("ETH")));
				var multiCurrencyDocument = new Document("MULTI_doc", "Multi-currency document",
						Map.of("currencies", List.of("BTC", "ETH", "SOL")));

				store.add(List.of(btcDocument, ethDocument, multiCurrencyDocument));

				// Verify initial state
				List<Document> results = store
					.similaritySearch(SearchRequest.builder().query("document").topK(5).build());
				assertThat(results).hasSize(3);

				try {
					// Test filtering with IN operator on a collection field
					Filter.Expression filterExpression = new Filter.Expression(Filter.ExpressionType.IN,
							new Filter.Key("currencies"), new Filter.Value(List.of("BTC")));

					// Search using programmatic filter
					store.similaritySearch(SearchRequest.builder()
						.query("document")
						.topK(5)
						.similarityThresholdAll()
						.filterExpression(filterExpression)
						.build());

					// If we get here without an exception, it means Cassandra
					// unexpectedly accepted the query,
					// which is surprising since Cassandra doesn't support the IN operator
					// on collection columns.
					// This would indicate a potential change in Cassandra's behavior.
					Assertions.fail("Expected InvalidQueryException from Cassandra");
				}
				catch (InvalidQueryException e) {
					// This is the expected outcome: Cassandra rejects the query with a
					// specific error
					// indicating that collection columns cannot be used with IN
					// operators, which is
					// a documented limitation of Cassandra's query language. Support for
					// collection
					// filtering via CONTAINS would be needed for this type of query to
					// work.
					assertThat(e.getMessage()).contains("Collection column 'currencies'");
					assertThat(e.getMessage()).contains("cannot be restricted by a 'IN' relation");
				}
			}
		});
	}

	@Test
	void throwsExceptionOnInvalidIndexNameWithSchemaValidation() {
		this.contextRunner.run(context -> {
			// Create valid schema first, then close
			try (CassandraVectorStore validStore = createTestStore(context, new SchemaColumn("meta1", DataTypes.TEXT),
					new SchemaColumn("meta2", DataTypes.TEXT))) {
				// Nothing to do here. This should not fail as the Schema now exists
			}

			// Now try with invalid index name but don't reinitialize schema
			CassandraVectorStore.Builder invalidBuilder = storeBuilder(context.getBean(CqlSession.class),
					context.getBean(EmbeddingModel.class))
				.addMetadataColumns(new SchemaColumn("meta1", DataTypes.TEXT),
						new SchemaColumn("meta2", DataTypes.TEXT))
				.indexName("non_existent_index_name")
				.initializeSchema(false);

			IllegalStateException exception = Assertions.assertThrows(IllegalStateException.class,
					invalidBuilder::build);

			assertThat(exception.getMessage()).contains("non_existent_index_name");
			assertThat(exception.getMessage()).contains("does not exist");
		});
	}

	@SpringBootConfiguration
	public static class TestApplication {

		@Bean
		public CassandraVectorStore store(CqlSession cqlSession, EmbeddingModel embeddingModel) {

			CassandraVectorStore.Builder builder = storeBuilder(cqlSession, embeddingModel).addMetadataColumns(
					new CassandraVectorStore.SchemaColumn("meta1", DataTypes.TEXT),
					new CassandraVectorStore.SchemaColumn("meta2", DataTypes.TEXT),
					new CassandraVectorStore.SchemaColumn("country", DataTypes.TEXT),
					new CassandraVectorStore.SchemaColumn("year", DataTypes.SMALLINT));

			CassandraVectorStore.dropKeyspace(builder);
			return builder.build();
		}

		@Bean
		public EmbeddingModel embeddingModel() {
			return new TransformersEmbeddingModel();
		}

		@Bean
		public CqlSession cqlSession() {
			return new CqlSessionBuilder()
				// comment next two lines out to connect to a local C* cluster
				.addContactPoint(cassandraContainer.getContactPoint())
				.withLocalDatacenter(cassandraContainer.getLocalDatacenter())
				.build();
		}

	}

}
