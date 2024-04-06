package org.springframework.ai.vectorstore;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.DefaultResourceLoader;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.typesense.api.Client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

import org.springframework.ai.vectorstore.TypesenseVectorStore.TypesenseVectorStoreConfig;
import org.typesense.api.Configuration;
import org.typesense.resources.Node;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Pablo Sanchidrian Herrera
 */
@Testcontainers
public class TypesenseVectorStoreIT {

	private static Path tempDirectory;

	static {
		try {
			tempDirectory = Files.createTempDirectory("typesense-test");
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Container
	private static GenericContainer<?> typesenseContainer = new GenericContainer<>("typesense/typesense:26.0")
		.withExposedPorts(8108)
		.withCommand("--data-dir", "/data", "--api-key=xyz", "--enable-cors")
		.withFileSystemBind(tempDirectory.toString(), "/data", BindMode.READ_WRITE);

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

	private void resetCollection(VectorStore vectorStore) {
		((TypesenseVectorStore) vectorStore).dropCollection();
		((TypesenseVectorStore) vectorStore).createCollection();
	}

	@Test
	void documentUpdate() {
		contextRunner.run(context -> {

			VectorStore vectorStore = context.getBean(VectorStore.class);

			resetCollection(vectorStore);

			Document document = new Document(UUID.randomUUID().toString(), "Spring AI rocks!!",
					Collections.singletonMap("meta1", "meta1"));

			vectorStore.add(List.of(document));

			Map<String, Object> info = ((TypesenseVectorStore) vectorStore).getCollectionInfo();
			assertThat(info.get("num_documents")).isEqualTo(1L);

			List<Document> results = vectorStore.similaritySearch(SearchRequest.query("Spring").withTopK(5));

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(document.getId());
			assertThat(resultDoc.getContent()).isEqualTo("Spring AI rocks!!");
			assertThat(resultDoc.getMetadata()).containsKey("meta1");
			assertThat(resultDoc.getMetadata()).containsKey("distance");

			Document sameIdDocument = new Document(document.getId(),
					"The World is Big and Salvation Lurks Around the Corner",
					Collections.singletonMap("meta2", "meta2"));

			vectorStore.add(List.of(sameIdDocument));

			info = ((TypesenseVectorStore) vectorStore).getCollectionInfo();
			assertThat(info.get("num_documents")).isEqualTo(1L);

			results = vectorStore.similaritySearch(SearchRequest.query("FooBar").withTopK(5));

			assertThat(results).hasSize(1);
			resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(document.getId());
			assertThat(resultDoc.getContent()).isEqualTo("The World is Big and Salvation Lurks Around the Corner");
			assertThat(resultDoc.getMetadata()).containsKey("meta2");
			assertThat(resultDoc.getMetadata()).containsKey("distance");

			vectorStore.delete(List.of(document.getId()));

			info = ((TypesenseVectorStore) vectorStore).getCollectionInfo();
			assertThat(info.get("num_documents")).isEqualTo(0L);

		});
	}

	@Test
	void addAndSearch() {

		contextRunner.run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);

			resetCollection(vectorStore);

			vectorStore.add(documents);

			Map<String, Object> info = ((TypesenseVectorStore) vectorStore).getCollectionInfo();

			assertThat(info.get("num_documents")).isEqualTo(3L);

			List<Document> results = vectorStore.similaritySearch(SearchRequest.query("Spring"));

			assertThat(results).hasSize(3);
		});
	}

	@Test
	void searchWithFilters() {

		contextRunner.run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);

			resetCollection(vectorStore);

			var bgDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
					Map.of("country", "BG", "year", 2020));
			var nlDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
					Map.of("country", "NL"));
			var bgDocument2 = new Document("The World is Big and Salvation Lurks Around the Corner",
					Map.of("country", "BG", "year", 2023));

			vectorStore.add(List.of(bgDocument, nlDocument, bgDocument2));

			List<Document> results = vectorStore.similaritySearch(SearchRequest.query("The World").withTopK(5));
			assertThat(results).hasSize(3);

			results = vectorStore.similaritySearch(SearchRequest.query("The World")
				.withTopK(5)
				.withSimilarityThresholdAll()
				.withFilterExpression("country == 'NL'"));
			assertThat(results).hasSize(1);
			assertThat(results.get(0).getId()).isEqualTo(nlDocument.getId());

			results = vectorStore.similaritySearch(SearchRequest.query("The World")
				.withTopK(5)
				.withSimilarityThresholdAll()
				.withFilterExpression("country in ['BG']"));

			assertThat(results).hasSize(2);
			assertThat(results.get(0).getId()).isIn(bgDocument.getId(), bgDocument2.getId());
			assertThat(results.get(1).getId()).isIn(bgDocument.getId(), bgDocument2.getId());

			results = vectorStore.similaritySearch(SearchRequest.query("The World")
				.withTopK(5)
				.withSimilarityThresholdAll()
				.withFilterExpression("country == 'BG' && year == 2020"));

			assertThat(results).hasSize(1);
			assertThat(results.get(0).getId()).isEqualTo(bgDocument.getId());

			results = vectorStore.similaritySearch(SearchRequest.query("The World")
				.withTopK(5)
				.withSimilarityThresholdAll()
				.withFilterExpression("NOT(country == 'BG' && year == 2020)"));

			assertThat(results).hasSize(2);
			assertThat(results.get(0).getId()).isIn(nlDocument.getId(), bgDocument2.getId());
			assertThat(results.get(1).getId()).isIn(nlDocument.getId(), bgDocument2.getId());

		});
	}

	@Test
	void searchWithThreshold() {

		contextRunner.run(context -> {

			VectorStore vectorStore = context.getBean(VectorStore.class);

			resetCollection(vectorStore);

			vectorStore.add(documents);

			List<Document> fullResult = vectorStore
				.similaritySearch(SearchRequest.query("Spring").withTopK(5).withSimilarityThresholdAll());

			List<Float> distances = fullResult.stream().map(doc -> (Float) doc.getMetadata().get("distance")).toList();

			assertThat(distances).hasSize(3);

			float threshold = (distances.get(0) + distances.get(1)) / 2;

			List<Document> results = vectorStore
				.similaritySearch(SearchRequest.query("Spring").withTopK(5).withSimilarityThreshold(1 - threshold));

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(documents.get(0).getId());
			assertThat(resultDoc.getContent()).contains(
					"Spring AI provides abstractions that serve as the foundation for developing AI applications.");
			assertThat(resultDoc.getMetadata()).containsKeys("meta1", "distance");

		});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration(exclude = { DataSourceAutoConfiguration.class })
	public static class TestApplication {

		@Bean
		public VectorStore vectorStore(Client client, EmbeddingModel embeddingClient) {

			TypesenseVectorStoreConfig config = TypesenseVectorStoreConfig.builder()
				.withCollectionName("test_vector_store")
				.withEmbeddingDimension(embeddingClient.dimensions())
				.build();

			return new TypesenseVectorStore(client, embeddingClient, config);
		}

		@Bean
		public Client typesenseClient() {
			List<Node> nodes = new ArrayList<>();
			nodes
				.add(new Node("http", typesenseContainer.getHost(), typesenseContainer.getMappedPort(8108).toString()));

			Configuration configuration = new Configuration(nodes, Duration.ofSeconds(5), "xyz");
			return new Client(configuration);
		}

		@Bean
		public EmbeddingModel embeddingClient() {
			return new TransformersEmbeddingModel();
		}

	}

	@AfterAll
	static void deleteContainer() {
		if (typesenseContainer != null) {
			typesenseContainer.stop();
		}

		if (tempDirectory != null) {
			tempDirectory.toFile().delete();
		}
	}

}
