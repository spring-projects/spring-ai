package org.springframework.ai.testcontainers.service.connection.typesense;

import org.junit.jupiter.api.Test;
import org.springframework.ai.ResourceUtils;
import org.springframework.ai.autoconfigure.vectorstore.typesense.TypesenseVectorStoreAutoConfiguration;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig
@TestPropertySource(properties = { "spring.ai.vectorstore.typesense.embeddingDimension=384",
		"spring.ai.vectorstore.typesense.collectionName=myTestCollection" })
@Testcontainers
class TypesenseContainerConnectionDetailsFactoryTest {

	@Container
	@ServiceConnection
	private static final GenericContainer<?> typesense = new GenericContainer<>("typesense/typesense:26.0")
		.withExposedPorts(8108)
		.withCommand("--data-dir", "/tmp", "--enable-cors")
		.withEnv("TYPESENSE_API_KEY", "secret")
		.withStartupTimeout(Duration.ofSeconds(100));

	List<Document> documents = List.of(
			new Document(ResourceUtils.getText("classpath:/test/data/spring.ai.txt"), Map.of("spring", "great")),
			new Document(ResourceUtils.getText("classpath:/test/data/time.shelter.txt")), new Document(
					ResourceUtils.getText("classpath:/test/data/great.depression.txt"), Map.of("depression", "bad")));

	@Autowired
	private VectorStore vectorStore;

	@Test
	public void addAndSearch() {

		this.vectorStore.add(documents);

		List<Document> results = this.vectorStore.similaritySearch(SearchRequest.query("Spring").withTopK(1));

		assertThat(results).hasSize(1);
		Document resultDoc = results.get(0);
		assertThat(resultDoc.getId()).isEqualTo(documents.get(0).getId());
		assertThat(resultDoc.getContent())
			.contains("Spring AI provides abstractions that serve as the foundation for developing AI applications.");
		assertThat(resultDoc.getMetadata()).hasSize(2);
		assertThat(resultDoc.getMetadata()).containsKeys("spring", "distance");

		this.vectorStore.delete(documents.stream().map(doc -> doc.getId()).toList());

		results = this.vectorStore.similaritySearch(SearchRequest.query("Spring").withTopK(1));
		assertThat(results).hasSize(0);
	}

	@Configuration(proxyBeanMethods = false)
	@ImportAutoConfiguration(TypesenseVectorStoreAutoConfiguration.class)
	static class Config {

		@Bean
		public EmbeddingModel embeddingModel() {
			return new TransformersEmbeddingModel();
		}

	}

}