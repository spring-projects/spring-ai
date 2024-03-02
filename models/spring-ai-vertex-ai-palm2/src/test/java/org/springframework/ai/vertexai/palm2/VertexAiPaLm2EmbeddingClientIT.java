package org.springframework.ai.vertexai.palm2;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vertexai.palm2.api.VertexAiPaLm2Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "PALM_API_KEY", matches = ".*")
class VertexAiPaLm2EmbeddingClientIT {

	@Autowired
	private VertexAiPaLm2EmbeddingClient embeddingClient;

	@Test
	void simpleEmbedding() {
		assertThat(embeddingClient).isNotNull();
		EmbeddingResponse embeddingResponse = embeddingClient.embedForResponse(List.of("Hello World"));
		assertThat(embeddingResponse.getResults()).hasSize(1);
		assertThat(embeddingResponse.getResults().get(0).getOutput()).isNotEmpty();
		assertThat(embeddingClient.dimensions()).isEqualTo(768);
	}

	@Test
	void batchEmbedding() {
		assertThat(embeddingClient).isNotNull();
		EmbeddingResponse embeddingResponse = embeddingClient
			.embedForResponse(List.of("Hello World", "World is big and salvation is near"));
		assertThat(embeddingResponse.getResults()).hasSize(2);
		assertThat(embeddingResponse.getResults().get(0).getOutput()).isNotEmpty();
		assertThat(embeddingResponse.getResults().get(0).getIndex()).isEqualTo(0);
		assertThat(embeddingResponse.getResults().get(1).getOutput()).isNotEmpty();
		assertThat(embeddingResponse.getResults().get(1).getIndex()).isEqualTo(1);

		assertThat(embeddingClient.dimensions()).isEqualTo(768);
	}

	@SpringBootConfiguration
	public static class TestConfiguration {

		@Bean
		public VertexAiPaLm2Api vertexAiApi() {
			return new VertexAiPaLm2Api(System.getenv("PALM_API_KEY"));
		}

		@Bean
		public VertexAiPaLm2EmbeddingClient vertexAiEmbedding(VertexAiPaLm2Api vertexAiApi) {
			return new VertexAiPaLm2EmbeddingClient(vertexAiApi);
		}

	}

}
