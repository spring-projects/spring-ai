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

package org.springframework.ai.vectorstore.qdrant;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.VectorParams;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.qdrant.QdrantContainer;

import org.springframework.ai.content.Media;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentMetadata;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.test.vectorstore.BaseVectorStoreTests;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.MimeType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Anush Shetty
 * @author Josh Long
 * @author Eddú Meléndez
 * @author Thomas Vitale
 * @author Soby Chacko
 * @author Jonghoon Park
 * @author Kim San
 * @since 0.8.1
 */
@Testcontainers
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class QdrantVectorStoreIT extends BaseVectorStoreTests {

	private static final String COLLECTION_NAME = "test_collection";

	private static final int EMBEDDING_DIMENSION = 1536;

	@Container
	static QdrantContainer qdrantContainer = new QdrantContainer(QdrantImage.DEFAULT_IMAGE);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestApplication.class);

	List<Document> documents = List.of(
			new Document("Spring AI rocks!! Spring AI rocks!! Spring AI rocks!! Spring AI rocks!! Spring AI rocks!!",
					Collections.singletonMap("meta1", "meta1")),
			new Document("Hello World Hello World Hello World Hello World Hello World Hello World Hello World",
					Collections.singletonMap("meta1", List.of("meta-list"))),
			new Document(
					"Great Depression Great Depression Great Depression Great Depression Great Depression Great Depression",
					Collections.singletonMap("meta2", List.of("meta-list"))));

	@BeforeAll
	static void setup() throws InterruptedException, ExecutionException {

		String host = qdrantContainer.getHost();
		int port = qdrantContainer.getGrpcPort();
		QdrantClient client = new QdrantClient(QdrantGrpcClient.newBuilder(host, port, false).build());

		client
			.createCollectionAsync(COLLECTION_NAME,
					VectorParams.newBuilder().setDistance(Distance.Cosine).setSize(EMBEDDING_DIMENSION).build())
			.get();

		client.close();
	}

	@Override
	protected void executeTest(Consumer<VectorStore> testFunction) {
		this.contextRunner.run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);
			testFunction.accept(vectorStore);
		});
	}

	@Test
	public void addAndSearch() {
		this.contextRunner.run(context -> {

			VectorStore vectorStore = context.getBean(VectorStore.class);

			vectorStore.add(this.documents);

			List<Document> results = vectorStore
				.similaritySearch(SearchRequest.builder().query("Great").topK(1).build());

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(this.documents.get(2).getId());
			assertThat(resultDoc.getText()).isEqualTo(
					"Great Depression Great Depression Great Depression Great Depression Great Depression Great Depression");
			assertThat(resultDoc.getMetadata()).containsKeys("meta2", DocumentMetadata.DISTANCE.value());

			// Remove all documents from the store
			vectorStore.delete(this.documents.stream().map(doc -> doc.getId()).toList());

			List<Document> results2 = vectorStore
				.similaritySearch(SearchRequest.builder().query("Great").topK(1).build());
			assertThat(results2).hasSize(0);
		});
	}

	@Test
	public void addAndSearchWithFilters() {

		this.contextRunner.run(context -> {

			VectorStore vectorStore = context.getBean(VectorStore.class);

			var bgDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
					Map.of("country", "Bulgaria", "number", 3));
			var nlDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
					Map.of("country", "Netherlands", "number", 90));

			vectorStore.add(List.of(bgDocument, nlDocument));

			var request = SearchRequest.builder().query("The World").topK(5).build();

			List<Document> results = vectorStore.similaritySearch(request);
			assertThat(results).hasSize(2);

			results = vectorStore.similaritySearch(SearchRequest.from(request)
				.similarityThresholdAll()
				.filterExpression("country == 'Bulgaria'")
				.build());
			assertThat(results).hasSize(1);
			assertThat(results.get(0).getId()).isEqualTo(bgDocument.getId());

			results = vectorStore.similaritySearch(SearchRequest.from(request)
				.similarityThresholdAll()
				.filterExpression("country == 'Netherlands'")
				.build());
			assertThat(results).hasSize(1);
			assertThat(results.get(0).getId()).isEqualTo(nlDocument.getId());

			results = vectorStore.similaritySearch(SearchRequest.from(request)
				.similarityThresholdAll()
				.filterExpression("NOT(country == 'Netherlands')")
				.build());
			assertThat(results).hasSize(1);
			assertThat(results.get(0).getId()).isEqualTo(bgDocument.getId());

			results = vectorStore.similaritySearch(SearchRequest.from(request)
				.similarityThresholdAll()
				.filterExpression("number in [3, 5, 12]")
				.build());
			assertThat(results).hasSize(1);
			assertThat(results.get(0).getId()).isEqualTo(bgDocument.getId());

			results = vectorStore.similaritySearch(SearchRequest.from(request)
				.similarityThresholdAll()
				.filterExpression("number nin [3, 5, 12]")
				.build());
			assertThat(results).hasSize(1);
			assertThat(results.get(0).getId()).isEqualTo(nlDocument.getId());

			// Remove all documents from the store
			vectorStore.delete(List.of(bgDocument, nlDocument).stream().map(doc -> doc.getId()).toList());
		});
	}

	@Test
	public void documentUpdateTest() {

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
			assertThat(resultDoc.getMetadata()).containsKey(DocumentMetadata.DISTANCE.value());

			vectorStore.delete(List.of(document.getId()));
		});
	}

	@Test
	public void searchThresholdTest() {

		this.contextRunner.run(context -> {

			VectorStore vectorStore = context.getBean(VectorStore.class);

			vectorStore.add(this.documents);

			var request = SearchRequest.builder().query("Great").topK(5).build();
			List<Document> fullResult = vectorStore
				.similaritySearch(SearchRequest.from(request).similarityThresholdAll().build());

			List<Double> scores = fullResult.stream().map(Document::getScore).toList();

			assertThat(scores).hasSize(3);

			double similarityThreshold = (scores.get(0) + scores.get(1)) / 2;

			List<Document> results = vectorStore
				.similaritySearch(SearchRequest.from(request).similarityThreshold(similarityThreshold).build());

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(this.documents.get(2).getId());
			assertThat(resultDoc.getText()).isEqualTo(
					"Great Depression Great Depression Great Depression Great Depression Great Depression Great Depression");
			assertThat(resultDoc.getMetadata()).containsKey("meta2");
			assertThat(resultDoc.getMetadata()).containsKey(DocumentMetadata.DISTANCE.value());
			assertThat(resultDoc.getScore()).isGreaterThanOrEqualTo(similarityThreshold);

			// Remove all documents from the store
			vectorStore.delete(this.documents.stream().map(doc -> doc.getId()).toList());
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
				.containsExactlyInAnyOrder(1L, 1L);

			vectorStore.delete(List.of(doc1.getId(), doc3.getId()));
		});
	}

	@Test
	void getNativeClientTest() {
		this.contextRunner.run(context -> {
			QdrantVectorStore vectorStore = context.getBean(QdrantVectorStore.class);
			Optional<QdrantClient> nativeClient = vectorStore.getNativeClient();
			assertThat(nativeClient).isPresent();
		});
	}

	@Test
	void shouldConvertLongToString() {
		this.contextRunner.run(context -> {
			QdrantVectorStore vectorStore = context.getBean(QdrantVectorStore.class);
			var refId = System.currentTimeMillis();
			var doc = new Document("Long type ref_id", Map.of("ref_id", refId));
			vectorStore.add(List.of(doc));

			List<Document> results = vectorStore
				.similaritySearch(SearchRequest.builder().query("Long type ref_id").topK(1).build());
			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			var resultRefId = resultDoc.getMetadata().get("ref_id");
			assertThat(resultRefId).isInstanceOf(String.class);
			assertThat(Double.valueOf((String) resultRefId)).isEqualTo(refId);

			// Remove all documents from the store
			vectorStore.delete(List.of(resultDoc.getId()));
		});
	}

	@Test
	void testNonTextDocuments() {
		this.contextRunner.run(context -> {
			QdrantVectorStore vectorStore = context.getBean(QdrantVectorStore.class);
			Media media = new Media(MimeType.valueOf("image/png"), new ByteArrayResource(new byte[] { 0x00 }));

			Document imgDoc = Document.builder().media(media).metadata(Map.of("fileName", "pixel.png")).build();

			Exception exception = assertThrows(IllegalArgumentException.class, () -> vectorStore.add(List.of(imgDoc)));
			assertEquals("Only text documents are supported for now. One of the documents contains non-text content.",
					exception.getMessage());
		});
	}

	@Test
	void metadataTypePreservationInSearchResults() {
		this.contextRunner.run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);

			// Create document with different metadata types
			var doc = new Document("Spring Framework is a powerful Java framework",
					Map.of(
						"title", "Spring Guide",
						"version", 3.0,       // Double type
						"published", true));   // Boolean type

			// Add document to vector store
			vectorStore.add(List.of(doc));

			// Search and retrieve document
			List<Document> results = vectorStore
				.similaritySearch(SearchRequest.builder().query("Spring Framework").topK(1).build());

			assertThat(results).hasSize(1);
			Map<String, Object> metadata = results.get(0).getMetadata();

			// Verify metadata types are preserved correctly (no silent conversions)
			// String should stay String
			assertThat(metadata.get("title")).isInstanceOf(String.class).isEqualTo("Spring Guide");
			// Double should stay Double (not converted to String like "3.0")
			assertThat(metadata.get("version")).isInstanceOf(Double.class).isEqualTo(3.0);
			// Boolean should stay Boolean (not converted to String like "true")
			assertThat(metadata.get("published")).isInstanceOf(Boolean.class).isEqualTo(true);

			// Clean up
			vectorStore.delete(List.of(doc.getId()));
		});
	}

	@Test
	void metadataPreservationInFilteredResults() {
		this.contextRunner.run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);

			// Create documents with boolean metadata for filtering
			var doc1 = new Document("Spring Framework documentation",
					Map.of(
						"published", true,
						"source", "spring-docs"));

			var doc2 = new Document("Kubernetes documentation",
					Map.of(
						"published", false,
						"source", "k8s-docs"));

			// Add documents to vector store
			vectorStore.add(List.of(doc1, doc2));

			// Search with filter expression on boolean metadata
			List<Document> filteredResults = vectorStore.similaritySearch(
				SearchRequest.builder()
					.query("documentation")
					.topK(5)
					.similarityThresholdAll()
					.filterExpression("published == true")
					.build());

			// Verify filter works and metadata is preserved
			assertThat(filteredResults).hasSize(1);
			Document filteredDoc = filteredResults.get(0);

			// Verify all metadata fields are present and correct in filtered results
			Map<String, Object> metadata = filteredDoc.getMetadata();
			assertThat(metadata)
				.containsEntry("published", true)
				.containsEntry("source", "spring-docs");

			// Clean up
			vectorStore.delete(List.of(doc1.getId(), doc2.getId()));
		});
	}

	@SpringBootConfiguration
	public static class TestApplication {

		@Bean
		public QdrantClient qdrantClient() {
			String host = qdrantContainer.getHost();
			int port = qdrantContainer.getGrpcPort();
			QdrantClient qdrantClient = new QdrantClient(QdrantGrpcClient.newBuilder(host, port, false).build());
			return qdrantClient;
		}

		@Bean
		public VectorStore qdrantVectorStore(EmbeddingModel embeddingModel, QdrantClient qdrantClient) {
			return QdrantVectorStore.builder(qdrantClient, embeddingModel)
				.collectionName(COLLECTION_NAME)
				.initializeSchema(true)
				.build();
		}

		@Bean
		public EmbeddingModel embeddingModel() {
			return new OpenAiEmbeddingModel(OpenAiApi.builder().apiKey(System.getenv("OPENAI_API_KEY")).build());
		}

	}

}
