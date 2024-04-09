package org.springframework.ai.vectorstore;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.transformers.TransformersEmbeddingClient;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.ai.vectorstore.TypesenseVectorStore.TypesenseVectorStoreConfig;
import org.typesense.api.Configuration;
import org.typesense.resources.Node;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
public class TypesenseVectorStoreIT {

	private static Path tempDirectory;

    static {
        try {
            tempDirectory = Files.createTempDirectory("typesense-test");
        } catch (IOException e) {
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
	void addAndSearch() {

		contextRunner.run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);

			resetCollection(vectorStore);

			vectorStore.add(documents);

			List<Document> results = vectorStore.similaritySearch(SearchRequest.query("Spring"));

			assertThat(results).hasSize(1);
		});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration(exclude = { DataSourceAutoConfiguration.class })
	public static class TestApplication {

		@Bean
		public VectorStore vectorStore(Client client, EmbeddingClient embeddingClient) {

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
		public EmbeddingClient embeddingClient() {
			return new TransformersEmbeddingClient();
		}

	}

	@AfterAll
    static void deleteContainer() {
		if(typesenseContainer != null) {
			typesenseContainer.stop();
		}

		if(tempDirectory != null) {
			tempDirectory.toFile().delete();
		}
	}

}
