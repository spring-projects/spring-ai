package org.springframework.ai.autoconfigure.vectorstore.typesense;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.ai.ResourceUtils;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.FileSystemUtils;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Pablo Sanchidrian Herrera
 */
@Testcontainers
public class TypesenseVectorStoreAutoConfigurationIT {

	private static GenericContainer<?> typesenseContainer;

	private static final File TEMP_FOLDER = new File("target/test-" + UUID.randomUUID().toString());

	List<Document> documents = List.of(
			new Document(ResourceUtils.getText("classpath:/test/data/spring.ai.txt"), Map.of("spring", "great")),
			new Document(ResourceUtils.getText("classpath:/test/data/time.shelter.txt")), new Document(
					ResourceUtils.getText("classpath:/test/data/great.depression.txt"), Map.of("depression", "bad")));

	@BeforeAll
	public static void beforeAll() {
		FileSystemUtils.deleteRecursively(TEMP_FOLDER);
		TEMP_FOLDER.mkdirs();

		typesenseContainer = new GenericContainer<>("typesense/typesense:26.0").withExposedPorts(8108)
			.withCommand("--data-dir", "/data", "--api-key=xyz", "--enable-cors")
			.withFileSystemBind(TEMP_FOLDER.getAbsolutePath(), "/data", BindMode.READ_WRITE)
			.withStartupTimeout(Duration.ofSeconds(100));

		typesenseContainer.start();
	}

	@AfterAll
	public static void afterAll() {
		typesenseContainer.stop();
		FileSystemUtils.deleteRecursively(TEMP_FOLDER);
	}

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(TypesenseVectorStoreAutoConfiguration.class))
		.withUserConfiguration(Config.class);

	@Test
	public void addAndSearch() {
		contextRunner
			.withPropertyValues("spring.ai.vectorstore.typesense.embeddingDimension=384",
					"spring.ai.vectorstore.typesense.collectionName=myTestCollection",
					"spring.ai.vectorstore.typesense.client.apiKey=xyz",
					"spring.ai.vectorstore.typesense.client.protocol=http",
					"spring.ai.vectorstore.typesense.client.host=" + typesenseContainer.getHost(),
					"spring.ai.vectorstore.typesense.client.port=" + typesenseContainer.getMappedPort(8108).toString())
			.run(context -> {
				VectorStore vectorStore = context.getBean(VectorStore.class);
				vectorStore.add(documents);

				List<Document> results = vectorStore.similaritySearch(SearchRequest.query("Spring").withTopK(1));

				assertThat(results).hasSize(1);
				Document resultDoc = results.get(0);
				assertThat(resultDoc.getId()).isEqualTo(documents.get(0).getId());
				assertThat(resultDoc.getContent()).contains(
						"Spring AI provides abstractions that serve as the foundation for developing AI applications.");
				assertThat(resultDoc.getMetadata()).hasSize(2);
				assertThat(resultDoc.getMetadata()).containsKeys("spring", "distance");

				vectorStore.delete(documents.stream().map(doc -> doc.getId()).toList());

				results = vectorStore.similaritySearch(SearchRequest.query("Spring").withTopK(1));
				assertThat(results).hasSize(0);
			});
	}

	@Configuration(proxyBeanMethods = false)
	static class Config {

		@Bean
		public EmbeddingModel embeddingClient() {
			return new TransformersEmbeddingModel();
		}

	}

}
