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

package org.springframework.ai.vectorstore.redis;

import com.redis.testcontainers.RedisStackContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentMetadata;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.test.vectorstore.BaseVectorStoreTests;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.redis.RedisVectorStore.MetadataField;
import org.springframework.ai.vectorstore.redis.RedisVectorStore.TextScorer;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.DefaultResourceLoader;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import redis.clients.jedis.JedisPooled;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author Julien Ruaux
 * @author Eddú Meléndez
 * @author Thomas Vitale
 * @author Soby Chacko
 * @author Brian Sam-Bodden
 */
@Testcontainers
class RedisVectorStoreIT extends BaseVectorStoreTests {

	@Container
	static RedisStackContainer redisContainer = new RedisStackContainer(
			RedisStackContainer.DEFAULT_IMAGE_NAME.withTag(RedisStackContainer.DEFAULT_TAG));

	// Use host and port explicitly since getRedisURI() might not be consistent
	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(RedisAutoConfiguration.class))
		.withUserConfiguration(TestApplication.class)
		.withPropertyValues("spring.data.redis.host=" + redisContainer.getHost(),
				"spring.data.redis.port=" + redisContainer.getFirstMappedPort());

	List<Document> documents = List.of(
			new Document("1", getText("classpath:/test/data/spring.ai.txt"), Map.of("meta1", "meta1")),
			new Document("2", getText("classpath:/test/data/time.shelter.txt"), Map.of()),
			new Document("3", getText("classpath:/test/data/great.depression.txt"), Map.of("meta2", "meta2")));

	public static String getText(String uri) {
		var resource = new DefaultResourceLoader().getResource(uri);
		try {
			return resource.getContentAsString(StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@BeforeEach
	void cleanDatabase() {
		this.contextRunner.run(context -> context.getBean(RedisVectorStore.class).getJedis().flushAll());
	}

	@Override
	protected void executeTest(Consumer<VectorStore> testFunction) {
		this.contextRunner.run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);
			testFunction.accept(vectorStore);
		});
	}

	@Test
	void ensureIndexGetsCreated() {
		this.contextRunner.run(context -> assertThat(context.getBean(RedisVectorStore.class).getJedis().ftList())
			.contains(RedisVectorStore.DEFAULT_INDEX_NAME));
	}

	@Test
	void addAndSearch() {

		this.contextRunner.run(context -> {

			VectorStore vectorStore = context.getBean(VectorStore.class);

			vectorStore.add(this.documents);

			List<Document> results = vectorStore
				.similaritySearch(SearchRequest.builder().query("Spring").topK(1).build());

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(this.documents.get(0).getId());
			assertThat(resultDoc.getText()).contains(
					"Spring AI provides abstractions that serve as the foundation for developing AI applications.");
			assertThat(resultDoc.getMetadata()).hasSize(3);
			assertThat(resultDoc.getMetadata()).containsKeys("meta1", RedisVectorStore.DISTANCE_FIELD_NAME,
					DocumentMetadata.DISTANCE.value());

			// Remove all documents from the store
			vectorStore.delete(this.documents.stream().map(doc -> doc.getId()).toList());

			results = vectorStore.similaritySearch(SearchRequest.builder().query("Spring").topK(1).build());
			assertThat(results).isEmpty();
		});
	}

	@Test
	void searchWithFilters() throws InterruptedException {

		this.contextRunner.run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);

			var bgDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
					Map.of("country", "BG", "year", 2020));
			var nlDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
					Map.of("country", "NL"));
			var bgDocument2 = new Document("The World is Big and Salvation Lurks Around the Corner",
					Map.of("country", "BG", "year", 2023));

			vectorStore.add(List.of(bgDocument, nlDocument, bgDocument2));

			List<Document> results = vectorStore
				.similaritySearch(SearchRequest.builder().query("The World").topK(5).build());
			assertThat(results).hasSize(3);

			results = vectorStore.similaritySearch(SearchRequest.builder()
				.query("The World")
				.topK(5)
				.similarityThresholdAll()
				.filterExpression("country == 'NL'")
				.build());
			assertThat(results).hasSize(1);
			assertThat(results.get(0).getId()).isEqualTo(nlDocument.getId());

			results = vectorStore.similaritySearch(SearchRequest.builder()
				.query("The World")
				.topK(5)
				.similarityThresholdAll()
				.filterExpression("country == 'BG'")
				.build());

			assertThat(results).hasSize(2);
			assertThat(results.get(0).getId()).isIn(bgDocument.getId(), bgDocument2.getId());
			assertThat(results.get(1).getId()).isIn(bgDocument.getId(), bgDocument2.getId());

			results = vectorStore.similaritySearch(SearchRequest.builder()
				.query("The World")
				.topK(5)
				.similarityThresholdAll()
				.filterExpression("country == 'BG' && year == 2020")
				.build());

			assertThat(results).hasSize(1);
			assertThat(results.get(0).getId()).isEqualTo(bgDocument.getId());

			results = vectorStore.similaritySearch(SearchRequest.builder()
				.query("The World")
				.topK(5)
				.similarityThresholdAll()
				.filterExpression("NOT(country == 'BG' && year == 2020)")
				.build());

			assertThat(results).hasSize(2);
			assertThat(results.get(0).getId()).isIn(nlDocument.getId(), bgDocument2.getId());
			assertThat(results.get(1).getId()).isIn(nlDocument.getId(), bgDocument2.getId());

		});
	}

	@Test
	void documentUpdate() {

		this.contextRunner.run(context -> {

			VectorStore vectorStore = context.getBean(VectorStore.class);

			Document document = new Document(UUID.randomUUID().toString(), "Spring AI rocks!!",
					Collections.singletonMap("meta1", "meta1"));

			vectorStore.add(List.of(document));

			List<Document> results = vectorStore
				.similaritySearch(SearchRequest.builder().query("Spring").topK(5).build());

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(document.getId());
			assertThat(resultDoc.getText()).isEqualTo("Spring AI rocks!!");
			assertThat(resultDoc.getMetadata()).containsKey("meta1");
			assertThat(resultDoc.getMetadata()).containsKey(RedisVectorStore.DISTANCE_FIELD_NAME);
			assertThat(resultDoc.getMetadata()).containsKey(DocumentMetadata.DISTANCE.value());

			Document sameIdDocument = new Document(document.getId(),
					"The World is Big and Salvation Lurks Around the Corner",
					Collections.singletonMap("meta2", "meta2"));

			vectorStore.add(List.of(sameIdDocument));

			results = vectorStore.similaritySearch(SearchRequest.builder().query("FooBar").topK(5).build());

			assertThat(results).hasSize(1);
			resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(document.getId());
			assertThat(resultDoc.getText()).isEqualTo("The World is Big and Salvation Lurks Around the Corner");
			assertThat(resultDoc.getMetadata()).containsKey("meta2");
			assertThat(resultDoc.getMetadata()).containsKey(RedisVectorStore.DISTANCE_FIELD_NAME);
			assertThat(resultDoc.getMetadata()).containsKey(DocumentMetadata.DISTANCE.value());

			vectorStore.delete(List.of(document.getId()));

		});
	}

	@Test
	void searchWithThreshold() {

		this.contextRunner.run(context -> {

			VectorStore vectorStore = context.getBean(VectorStore.class);

			vectorStore.add(this.documents);

			List<Document> fullResult = vectorStore
				.similaritySearch(SearchRequest.builder().query("Spring").topK(5).similarityThresholdAll().build());

			List<Double> scores = fullResult.stream().map(Document::getScore).toList();

			assertThat(scores).hasSize(3);

			double similarityThreshold = (scores.get(0) + scores.get(1)) / 2;

			List<Document> results = vectorStore.similaritySearch(
					SearchRequest.builder().query("Spring").topK(5).similarityThreshold(similarityThreshold).build());

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(this.documents.get(0).getId());
			assertThat(resultDoc.getText()).contains(
					"Spring AI provides abstractions that serve as the foundation for developing AI applications.");
			assertThat(resultDoc.getMetadata()).containsKeys("meta1", RedisVectorStore.DISTANCE_FIELD_NAME,
					DocumentMetadata.DISTANCE.value());
			assertThat(resultDoc.getScore()).isGreaterThanOrEqualTo(similarityThreshold);
		});
	}

	@Test
	void deleteWithComplexFilterExpression() {
		this.contextRunner.run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);

			var doc1 = new Document("Content 1", Map.of("type", "A", "priority", 1));
			var doc2 = new Document("Content 2", Map.of("type", "A", "priority", 2));
			var doc3 = new Document("Content 3", Map.of("type", "B", "priority", 1));

			vectorStore.add(List.of(doc1, doc2, doc3));

			// Complex filter expression: (type == 'A' AND priority > 1)
			Filter.Expression priorityFilter = new Filter.Expression(Filter.ExpressionType.GT,
					new Filter.Key("priority"), new Filter.Value(1));
			Filter.Expression typeFilter = new Filter.Expression(Filter.ExpressionType.EQ, new Filter.Key("type"),
					new Filter.Value("A"));
			Filter.Expression complexFilter = new Filter.Expression(Filter.ExpressionType.AND, typeFilter,
					priorityFilter);

			vectorStore.delete(complexFilter);

			var results = vectorStore
				.similaritySearch(SearchRequest.builder().query("Content").topK(5).similarityThresholdAll().build());

			assertThat(results).hasSize(2);
			assertThat(results.stream().map(doc -> doc.getMetadata().get("type")).collect(Collectors.toList()))
				.containsExactlyInAnyOrder("A", "B");
			assertThat(results.stream()
				.map(doc -> Integer.parseInt(doc.getMetadata().get("priority").toString()))
				.collect(Collectors.toList())).containsExactlyInAnyOrder(1, 1);
		});
	}

	@Test
	void getNativeClientTest() {
		this.contextRunner.run(context -> {
			RedisVectorStore vectorStore = context.getBean(RedisVectorStore.class);
			Optional<JedisPooled> nativeClient = vectorStore.getNativeClient();
			assertThat(nativeClient).isPresent();
		});
	}

	@Test
	void rangeQueryTest() {
		this.contextRunner.run(context -> {
			RedisVectorStore vectorStore = context.getBean(RedisVectorStore.class);

			// Add documents with distinct content to ensure different vector embeddings
			Document doc1 = new Document("1", "Spring AI provides powerful abstractions", Map.of("category", "AI"));
			Document doc2 = new Document("2", "Redis is an in-memory database", Map.of("category", "DB"));
			Document doc3 = new Document("3", "Vector search enables semantic similarity", Map.of("category", "AI"));
			Document doc4 = new Document("4", "Machine learning models power modern applications",
					Map.of("category", "AI"));
			Document doc5 = new Document("5", "Database indexing improves query performance", Map.of("category", "DB"));

			vectorStore.add(List.of(doc1, doc2, doc3, doc4, doc5));

			// First perform standard search to understand the score distribution
			List<Document> allDocs = vectorStore
				.similaritySearch(SearchRequest.builder().query("AI and machine learning").topK(5).build());

			assertThat(allDocs).hasSize(5);

			// Get highest and lowest scores
			double highestScore = allDocs.stream().mapToDouble(Document::getScore).max().orElse(0.0);
			double lowestScore = allDocs.stream().mapToDouble(Document::getScore).min().orElse(0.0);

			// Calculate a radius that should include some but not all documents
			// (typically between the highest and lowest scores)
			double midRadius = (highestScore - lowestScore) * 0.6 + lowestScore;

			// Perform range query with the calculated radius
			List<Document> rangeResults = vectorStore.searchByRange("AI and machine learning", midRadius);

			// Range results should be a subset of all results (more than 1 but fewer than
			// 5)
			assertThat(rangeResults.size()).isGreaterThan(0);
			assertThat(rangeResults.size()).isLessThan(5);

			// All returned documents should have scores >= radius
			for (Document doc : rangeResults) {
				assertThat(doc.getScore()).isGreaterThanOrEqualTo(midRadius);
			}
		});
	}

	@Test
	void textSearchTest() {
		this.contextRunner.run(context -> {
			RedisVectorStore vectorStore = context.getBean(RedisVectorStore.class);

			// Add documents with distinct text content
			Document doc1 = new Document("1", "Spring AI provides powerful abstractions for machine learning",
					Map.of("category", "AI", "description", "Framework for AI integration"));
			Document doc2 = new Document("2", "Redis is an in-memory database for high performance",
					Map.of("category", "DB", "description", "In-memory database system"));
			Document doc3 = new Document("3", "Vector search enables semantic similarity in AI applications",
					Map.of("category", "AI", "description", "Semantic search technology"));
			Document doc4 = new Document("4", "Machine learning models power modern AI applications",
					Map.of("category", "AI", "description", "ML model integration"));
			Document doc5 = new Document("5", "Database indexing improves query performance in Redis",
					Map.of("category", "DB", "description", "Database performance optimization"));

			vectorStore.add(List.of(doc1, doc2, doc3, doc4, doc5));

			// Perform text search on content field
			List<Document> results1 = vectorStore.searchByText("machine learning", "content");

			// Should find docs that mention "machine learning"
			assertThat(results1).hasSize(2);
			assertThat(results1.stream().map(Document::getId).collect(Collectors.toList()))
				.containsExactlyInAnyOrder("1", "4");

			// Perform text search with filter expression
			List<Document> results2 = vectorStore.searchByText("database", "content", 10, "category == 'DB'");

			// Should find only DB-related docs that mention "database"
			assertThat(results2).hasSize(2);
			assertThat(results2.stream().map(Document::getId).collect(Collectors.toList()))
				.containsExactlyInAnyOrder("2", "5");

			// Test with limit
			List<Document> results3 = vectorStore.searchByText("AI", "content", 2);

			// Should limit to 2 results
			assertThat(results3).hasSize(2);

			// Search in metadata text field
			List<Document> results4 = vectorStore.searchByText("framework integration", "description");

			// Should find docs matching the description
			assertThat(results4).hasSize(1);
			assertThat(results4.get(0).getId()).isEqualTo("1");

			// Test invalid field (should throw exception)
			assertThatThrownBy(() -> vectorStore.searchByText("test", "nonexistent"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("is not a TEXT field");
		});
	}

	@Test
	void textSearchConfigurationTest() {
		// Create a context with custom text search configuration
		var customContextRunner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(RedisAutoConfiguration.class))
			.withUserConfiguration(CustomTextSearchApplication.class)
			.withPropertyValues("spring.data.redis.host=" + redisContainer.getHost(),
					"spring.data.redis.port=" + redisContainer.getFirstMappedPort());

		customContextRunner.run(context -> {
			RedisVectorStore vectorStore = context.getBean(RedisVectorStore.class);

			// Add test documents
			Document doc1 = new Document("1", "Spring AI is a framework for AI integration",
					Map.of("description", "AI framework by Spring"));
			Document doc2 = new Document("2", "Redis is a fast in-memory database",
					Map.of("description", "In-memory database"));

			vectorStore.add(List.of(doc1, doc2));

			// With stopwords configured ("is", "a", "for" should be removed)
			List<Document> results = vectorStore.searchByText("is a framework for", "content");

			// Should still find document about framework without the stopwords
			assertThat(results).hasSize(1);
			assertThat(results.get(0).getId()).isEqualTo("1");
		});
	}

	@Test
	void countQueryTest() {
		this.contextRunner.run(context -> {
			RedisVectorStore vectorStore = context.getBean(RedisVectorStore.class);

			// Add documents with distinct content and metadata
			Document doc1 = new Document("1", "Spring AI provides powerful abstractions",
					Map.of("category", "AI", "year", 2023));
			Document doc2 = new Document("2", "Redis is an in-memory database", Map.of("category", "DB", "year", 2022));
			Document doc3 = new Document("3", "Vector search enables semantic similarity",
					Map.of("category", "AI", "year", 2023));
			Document doc4 = new Document("4", "Machine learning models power modern applications",
					Map.of("category", "AI", "year", 2021));
			Document doc5 = new Document("5", "Database indexing improves query performance",
					Map.of("category", "DB", "year", 2023));

			vectorStore.add(List.of(doc1, doc2, doc3, doc4, doc5));

			// 1. Test total count (no filter)
			long totalCount = vectorStore.count();
			assertThat(totalCount).isEqualTo(5);

			// 2. Test count with string filter expression
			long aiCategoryCount = vectorStore.count("@category:{AI}");
			assertThat(aiCategoryCount).isEqualTo(3);

			// 3. Test count with Filter.Expression
			Filter.Expression yearFilter = new Filter.Expression(Filter.ExpressionType.EQ, new Filter.Key("year"),
					new Filter.Value(2023));
			long year2023Count = vectorStore.count(yearFilter);
			assertThat(year2023Count).isEqualTo(3);

			// 4. Test count with complex Filter.Expression (AND condition)
			Filter.Expression categoryFilter = new Filter.Expression(Filter.ExpressionType.EQ,
					new Filter.Key("category"), new Filter.Value("AI"));
			Filter.Expression complexFilter = new Filter.Expression(Filter.ExpressionType.AND, categoryFilter,
					yearFilter);
			long aiAnd2023Count = vectorStore.count(complexFilter);
			assertThat(aiAnd2023Count).isEqualTo(2);

			// 5. Test count with complex string expression
			long dbOr2021Count = vectorStore.count("(@category:{DB} | @year:[2021 2021])");
			assertThat(dbOr2021Count).isEqualTo(3); // 2 DB + 1 from 2021

			// 6. Test count after deleting documents
			vectorStore.delete(List.of("1", "2"));

			long countAfterDelete = vectorStore.count();
			assertThat(countAfterDelete).isEqualTo(3);

			// 7. Test count with a filter that matches no documents
			Filter.Expression noMatchFilter = new Filter.Expression(Filter.ExpressionType.EQ, new Filter.Key("year"),
					new Filter.Value(2024));
			long noMatchCount = vectorStore.count(noMatchFilter);
			assertThat(noMatchCount).isEqualTo(0);
		});
	}

	@EnableAutoConfiguration(exclude = { DataSourceAutoConfiguration.class })
	public static class TestApplication {

		@Bean
		public RedisVectorStore vectorStore(EmbeddingModel embeddingModel) {
			// Create JedisPooled directly with container properties for more reliable
			// connection
			return RedisVectorStore
				.builder(new JedisPooled(redisContainer.getHost(), redisContainer.getFirstMappedPort()), embeddingModel)
				.metadataFields(MetadataField.tag("meta1"), MetadataField.tag("meta2"), MetadataField.tag("country"),
						MetadataField.numeric("year"), MetadataField.numeric("priority"), MetadataField.tag("type"),
						MetadataField.text("description"), MetadataField.tag("category"))
				.initializeSchema(true)
				.build();
		}

		@Bean
		public EmbeddingModel embeddingModel() {
			return new TransformersEmbeddingModel();
		}

	}

	@SpringBootConfiguration
	@EnableAutoConfiguration(exclude = { DataSourceAutoConfiguration.class })
	static class CustomTextSearchApplication {

		@Bean
		public RedisVectorStore vectorStore(EmbeddingModel embeddingModel) {
			// Create a store with custom text search configuration
			Set<String> stopwords = new HashSet<>(Arrays.asList("is", "a", "for", "the", "in"));

			return RedisVectorStore
				.builder(new JedisPooled(redisContainer.getHost(), redisContainer.getFirstMappedPort()), embeddingModel)
				.metadataFields(MetadataField.text("description"))
				.textScorer(TextScorer.TFIDF)
				.stopwords(stopwords)
				.inOrder(true)
				.initializeSchema(true)
				.build();
		}

		@Bean
		public EmbeddingModel embeddingModel() {
			return new TransformersEmbeddingModel();
		}

	}

}
