package org.springframework.ai.vertex.embedding;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vertex.api.VertexAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "PALM_API_KEY", matches = ".*")
class VertexAiEmbeddingClientIT {

	@Autowired
	private VertexAiEmbeddingClient embeddingClient;

	@Test
	void simpleEmbedding() {
		assertThat(embeddingClient).isNotNull();
		EmbeddingResponse embeddingResponse = embeddingClient.embedForResponse(List.of("Hello World"));
		assertThat(embeddingResponse.getData()).hasSize(1);
		assertThat(embeddingResponse.getData().get(0).getEmbedding()).isNotEmpty();
		assertThat(embeddingClient.dimensions()).isEqualTo(768);
	}

	@SpringBootConfiguration
	public static class TestConfiguration {

		@Bean
		public VertexAiApi vertexAiApi() {
			return new VertexAiApi(System.getenv("PALM_API_KEY"));
		}

		@Bean
		public VertexAiEmbeddingClient vertexAiEmbedding(VertexAiApi vertexAiApi) {
			return new VertexAiEmbeddingClient(vertexAiApi);
		}

	}

}
