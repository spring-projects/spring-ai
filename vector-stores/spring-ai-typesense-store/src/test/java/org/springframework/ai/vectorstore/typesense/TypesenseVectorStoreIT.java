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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.typesense.TypesenseContainer;
import org.typesense.api.Client;
import org.typesense.api.Configuration;
import org.typesense.resources.Node;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentMetadata;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.test.vectorstore.BaseVectorStoreTests;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Pablo Sanchidrian Herrera
 * @author Eddú Meléndez
 * @author Soby Chacko
 * @author Thomas Vitale
 */
@Testcontainers
public class TypesenseVectorStoreIT extends BaseVectorStoreTests {

	@Container
	private static TypesenseContainer typesense = new TypesenseContainer(TypesenseImage.DEFAULT_IMAGE);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestApplication.class);

	List<Document> documents = List.of(
			new Document(getText("classpath:/test/data/spring.ai.txt"), Map.of("meta1", "meta1")),
			new Document(getText("classpath:/test/data/time.shelter.txt")),
			new Document(getText("classpath:/test/data/great.depression.txt"), Map.of("meta2", "meta2")));

	public static String getText(String uri) {
		var resource = new DefaultResourceLoader().getResource(uri);
		try {
			return resource.getContentAsString(StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	protected void executeTest(Consumer<VectorStore> testFunction) {
		this.contextRunner.run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);
			testFunction.accept(vectorStore);
		});
	}

	@Test
	void documentUpdate() {
		this.contextRunner.run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);
			Document document = new Document(UUID.randomUUID().toString(), "Spring AI rocks!!",
					Collections.singletonMap("meta1", "meta1"));
			vectorStore.add(List.of(document));

			Map<String, Object> info = ((TypesenseVectorStore) vectorStore).getCollectionInfo();
			assertThat(info.get("num_documents")).isEqualTo(1L);

			List<Document> results = vectorStore
				.similaritySearch(SearchRequest.builder().query("Spring").topK(5).build());

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(document.getId());
			assertThat(resultDoc.getText()).isEqualTo("Spring AI rocks!!");
			assertThat(resultDoc.getMetadata()).containsKey("meta1");
			assertThat(resultDoc.getMetadata()).containsKey(DocumentMetadata.DISTANCE.value());

			Document sameIdDocument = new Document(document.getId(),
					"The World is Big and Salvation Lurks Around the Corner",
					Collections.singletonMap("meta2", "meta2"));

			vectorStore.add(List.of(sameIdDocument));

			info = ((TypesenseVectorStore) vectorStore).getCollectionInfo();
			assertThat(info.get("num_documents")).isEqualTo(1L);

			results = vectorStore.similaritySearch(SearchRequest.builder().query("FooBar").topK(5).build());

			assertThat(results).hasSize(1);
			resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(document.getId());
			assertThat(resultDoc.getText()).isEqualTo("The World is Big and Salvation Lurks Around the Corner");
			assertThat(resultDoc.getMetadata()).containsKey("meta2");
			assertThat(resultDoc.getMetadata()).containsKey(DocumentMetadata.DISTANCE.value());

			vectorStore.delete(List.of(document.getId()));

			info = ((TypesenseVectorStore) vectorStore).getCollectionInfo();
			assertThat(info.get("num_documents")).isEqualTo(0L);

			((TypesenseVectorStore) vectorStore).dropCollection();

		});
	}

	@Test
	void addAndSearch() {
		this.contextRunner.run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);

			vectorStore.add(this.documents);

			Map<String, Object> info = ((TypesenseVectorStore) vectorStore).getCollectionInfo();

			assertThat(info.get("num_documents")).isEqualTo(3L);

			List<Document> results = vectorStore.similaritySearch(SearchRequest.builder().query("Spring").build());

			assertThat(results).hasSize(3);

			((TypesenseVectorStore) vectorStore).dropCollection();
		});
	}

	@Test
	void searchWithFilters() {
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
				.filterExpression("country in ['BG']")
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

			((TypesenseVectorStore) vectorStore).dropCollection();

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
			assertThat(resultDoc.getMetadata()).containsKeys("meta1", DocumentMetadata.DISTANCE.value());
			assertThat(resultDoc.getScore()).isGreaterThanOrEqualTo(similarityThreshold);

			((TypesenseVectorStore) vectorStore).dropCollection();

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
			assertThat(results.stream().map(doc -> doc.getMetadata().get("priority")).collect(Collectors.toList()))
				.containsExactlyInAnyOrder(1, 1);

			((TypesenseVectorStore) vectorStore).dropCollection();
		});
	}

	@Test
	void filterValueCannotInjectAdditionalClauses() {
		// Verifies against a real Typesense server that a filter value cannot break out
		// of its literal and inject an additional `||` clause. Typesense's filter_by
		// tokenizer only honours backticks as string-literal delimiters, so before the
		// values were backtick-delimited the injected clause was tokenized as a separate
		// condition and bypassed the tenant constraint.
		this.contextRunner.run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);

			var attackerDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
					Map.of("tenant", "attacker", "country", "BG"));
			var victimDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
					Map.of("tenant", "victim", "country", "US"));

			vectorStore.add(List.of(attackerDocument, victimDocument));

			// Baseline: the tenant constraint alone returns only the attacker's document.
			List<Document> results = vectorStore.similaritySearch(SearchRequest.builder()
				.query("The World")
				.topK(5)
				.similarityThresholdAll()
				.filterExpression("tenant == 'attacker' && country == 'BG'")
				.build());
			assertThat(results).hasSize(1);
			assertThat(results.get(0).getId()).isEqualTo(attackerDocument.getId());

			// Control: a non-matching country returns nothing.
			results = vectorStore.similaritySearch(SearchRequest.builder()
				.query("The World")
				.topK(5)
				.similarityThresholdAll()
				.filterExpression("tenant == 'attacker' && country == 'FR'")
				.build());
			assertThat(results).isEmpty();

			// Attack: the injected clause must be matched as literal text rather than
			// evaluated, so the result is the same as the non-matching control above.
			results = vectorStore.similaritySearch(SearchRequest.builder()
				.query("The World")
				.topK(5)
				.similarityThresholdAll()
				.filterExpression("tenant == 'attacker' && country == 'x\" || metadata.tenant: \"victim'")
				.build());
			assertThat(results).isEmpty();

			((TypesenseVectorStore) vectorStore).dropCollection();
		});
	}

	@Test
	void deleteWithFilterValueCannotInjectAdditionalClauses() {
		// The delete path runs the same converter output through filter_by, so verify the
		// injected clause cannot widen a delete beyond the tenant constraint either.
		this.contextRunner.run(context -> {
			TypesenseVectorStore vectorStore = context.getBean(TypesenseVectorStore.class);

			var attackerDocument = new Document("Content one", Map.of("tenant", "attacker", "country", "BG"));
			var victimDocument = new Document("Content two", Map.of("tenant", "victim", "country", "US"));

			vectorStore.add(List.of(attackerDocument, victimDocument));

			Map<String, Object> info = vectorStore.getCollectionInfo();
			assertThat(info.get("num_documents")).isEqualTo(2L);

			Filter.Expression injected = new Filter.Expression(Filter.ExpressionType.AND,
					new Filter.Expression(Filter.ExpressionType.EQ, new Filter.Key("tenant"),
							new Filter.Value("attacker")),
					new Filter.Expression(Filter.ExpressionType.EQ, new Filter.Key("country"),
							new Filter.Value("x\" || metadata.tenant: \"victim")));

			vectorStore.delete(injected);

			// Nothing matches the literal value, so both documents survive.
			info = vectorStore.getCollectionInfo();
			assertThat(info.get("num_documents")).isEqualTo(2L);

			vectorStore.dropCollection();
		});
	}

	@Test
	void searchWithFilterValueContainingFilterMetacharacters() {
		// Verifies that backtick delimiting does not regress legitimate values, including
		// ones containing characters that filter_by would otherwise treat as syntax. Such
		// values were matched incorrectly before they were delimited as literals.
		this.contextRunner.run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);

			var commaDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
					Map.of("label", "alpha,beta"));
			var parenthesisDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
					Map.of("label", "gamma(delta)"));
			var operatorDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
					Map.of("label", "epsilon && zeta"));

			vectorStore.add(List.of(commaDocument, parenthesisDocument, operatorDocument));

			assertLabelFilterMatchesOnly(vectorStore, "alpha,beta", commaDocument);
			assertLabelFilterMatchesOnly(vectorStore, "gamma(delta)", parenthesisDocument);
			assertLabelFilterMatchesOnly(vectorStore, "epsilon && zeta", operatorDocument);

			((TypesenseVectorStore) vectorStore).dropCollection();
		});
	}

	private void assertLabelFilterMatchesOnly(VectorStore vectorStore, String label, Document expected) {
		Filter.Expression filter = new Filter.Expression(Filter.ExpressionType.EQ, new Filter.Key("label"),
				new Filter.Value(label));

		List<Document> results = vectorStore.similaritySearch(SearchRequest.builder()
			.query("The World")
			.topK(5)
			.similarityThresholdAll()
			.filterExpression(filter)
			.build());

		assertThat(results).hasSize(1);
		assertThat(results.get(0).getId()).isEqualTo(expected.getId());
	}

	@Test
	void searchWithDateFilter() {
		// The Typesense client stores a Date as epoch millis, so the converter must emit
		// the same representation. A quoted ISO-8601 string never matched the stored
		// numeric value.
		this.contextRunner.run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);

			var earlyDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
					Map.of("activationDate", new Date(1000)));
			var lateDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
					Map.of("activationDate", new Date(3000)));

			vectorStore.add(List.of(earlyDocument, lateDocument));

			List<Document> results = vectorStore.similaritySearch(SearchRequest.builder()
				.query("The World")
				.topK(5)
				.similarityThresholdAll()
				.filterExpression(new Filter.Expression(Filter.ExpressionType.GT, new Filter.Key("activationDate"),
						new Filter.Value(new Date(2000))))
				.build());

			assertThat(results).hasSize(1);
			assertThat(results.get(0).getId()).isEqualTo(lateDocument.getId());

			// The same filter expressed as an ISO-8601 string is normalized to a Date.
			results = vectorStore.similaritySearch(SearchRequest.builder()
				.query("The World")
				.topK(5)
				.similarityThresholdAll()
				.filterExpression("activationDate > '1970-01-01T00:00:02Z'")
				.build());

			assertThat(results).hasSize(1);
			assertThat(results.get(0).getId()).isEqualTo(lateDocument.getId());

			((TypesenseVectorStore) vectorStore).dropCollection();
		});
	}

	@Test
	void deleteByIdListDeletesOnlySpecifiedDocuments() {
		// Verifies that delete-by-id-list correctly removes only the specified documents
		// using the id: [id1,id2] bare-value filter_by format that Typesense requires
		// for its special id field. If the filter matched nothing, the count would stay
		// at 3 rather than dropping to 1.
		this.contextRunner.run(context -> {
			TypesenseVectorStore vectorStore = context.getBean(TypesenseVectorStore.class);

			// Drop and recreate to guarantee a clean collection regardless of what
			// previous tests (e.g. BaseVectorStoreTests) may have left behind.
			vectorStore.dropCollection();
			vectorStore.createCollection();

			var doc1 = new Document(UUID.randomUUID().toString(), "Content one", Map.of("keep", "true"));
			var doc2 = new Document(UUID.randomUUID().toString(), "Content two", Map.of("keep", "false"));
			var doc3 = new Document(UUID.randomUUID().toString(), "Content three", Map.of("keep", "false"));

			vectorStore.add(List.of(doc1, doc2, doc3));

			Map<String, Object> info = vectorStore.getCollectionInfo();
			assertThat(info.get("num_documents")).isEqualTo(3L);

			vectorStore.delete(List.of(doc2.getId(), doc3.getId()));

			info = vectorStore.getCollectionInfo();
			assertThat(info.get("num_documents")).isEqualTo(1L);

			List<Document> remaining = vectorStore
				.similaritySearch(SearchRequest.builder().query("Content").topK(5).similarityThresholdAll().build());
			assertThat(remaining).hasSize(1);
			assertThat(remaining.get(0).getId()).isEqualTo(doc1.getId());

			vectorStore.dropCollection();
		});
	}

	@Test
	void getNativeClientTest() {
		this.contextRunner.run(context -> {
			TypesenseVectorStore vectorStore = context.getBean(TypesenseVectorStore.class);
			Optional<TypesenseVectorStore> nativeClient = vectorStore.getNativeClient();
			assertThat(nativeClient).isPresent();
		});
	}

	@SpringBootConfiguration
	public static class TestApplication {

		@Bean
		public VectorStore vectorStore(Client client, EmbeddingModel embeddingModel) {

			return TypesenseVectorStore.builder(client, embeddingModel)
				.collectionName("test_vector_store")
				.embeddingDimension(embeddingModel.dimensions())
				.initializeSchema(true)
				.build();
		}

		@Bean
		public Client typesenseClient() {
			List<Node> nodes = new ArrayList<>();
			nodes.add(new Node("http", typesense.getHost(), typesense.getMappedPort(8108).toString()));

			Configuration configuration = new Configuration(nodes, Duration.ofSeconds(5), typesense.getApiKey());
			return new Client(configuration);
		}

		@Bean
		public EmbeddingModel embeddingModel() {
			return new TransformersEmbeddingModel();
		}

	}

}
