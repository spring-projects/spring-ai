package org.springframework.ai.testcontainers.service.connection.elasticsearch;

import org.apache.http.HttpHost;
import org.awaitility.Awaitility;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStore;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStoreOptions;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;

@Testcontainers
class ElasticsearchContainerConnectionDetailsFactoryIT {

	@Container
	@ServiceConnection
	private static final ElasticsearchContainer elasticsearch = new ElasticsearchContainer(
			ElasticsearchImage.DEFAULT_IMAGE)
		.withStartupTimeout(Duration.ofSeconds(30))
		.withEnv("ES_JAVA_OPTS", "-Xms256m -Xmx256m")
		.withEnv("xpack.security.enabled", "false");

	private final List<Document> documents = List.of(
			new Document("1", getText("classpath:/test/data/spring.ai.txt"), Map.of("meta1", "meta1")),
			new Document("2", getText("classpath:/test/data/time.shelter.txt"), Map.of()),
			new Document("3", getText("classpath:/test/data/great.depression.txt"), Map.of("meta2", "meta2")));

	@Test
	public void addAndSearchTest() {
		getContextRunner().run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);
			vectorStore.add(this.documents);

			Awaitility.await()
				.until(() -> vectorStore.similaritySearch(
						SearchRequest.builder().query("Great Depression").topK(1).similarityThreshold(0).build()),
						hasSize(1));

			List<Document> results = vectorStore.similaritySearch(
					SearchRequest.builder().query("Great Depression").topK(1).similarityThreshold(0).build());

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(this.documents.get(2).getId());
			assertThat(resultDoc.getText()).contains("The Great Depression (1929–1939) was an economic " + "shock");
			assertThat(resultDoc.getMetadata()).hasSize(2);
			assertThat(resultDoc.getMetadata()).containsKey("meta2");
			assertThat(resultDoc.getMetadata()).containsKey("distance");

			// Remove all documents from the store
			vectorStore.delete(this.documents.stream().map(Document::getId).toList());

			Awaitility.await()
				.until(() -> vectorStore.similaritySearch(
						SearchRequest.builder().query("Great Depression").topK(1).similarityThreshold(0).build()),
						hasSize(0));

		});
	}

	private ApplicationContextRunner getContextRunner() {
		return new ApplicationContextRunner().withUserConfiguration(Config.class);
	}

	private String getText(String uri) {
		var resource = new DefaultResourceLoader().getResource(uri);
		try {
			return resource.getContentAsString(StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Configuration(proxyBeanMethods = false)
	static class Config {

		@Bean
		public EmbeddingModel embeddingModel() {
			return new TransformersEmbeddingModel();
		}

		@Bean
		RestClient restClient() {
			return RestClient.builder(new HttpHost(elasticsearch.getHost(), elasticsearch.getMappedPort(9200), "http"))
				.build();
		}

		@Bean
		public ElasticsearchVectorStore vectorStoreDefault(EmbeddingModel embeddingModel, RestClient restClient) {
			ElasticsearchVectorStoreOptions options = new ElasticsearchVectorStoreOptions();
			options.setDimensions(384);
			return ElasticsearchVectorStore.builder(restClient, embeddingModel)
				.initializeSchema(true)
				.options(options)
				.build();
		}

	}

}
