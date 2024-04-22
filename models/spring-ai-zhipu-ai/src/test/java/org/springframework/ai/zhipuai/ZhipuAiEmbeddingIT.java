package org.springframework.ai.zhipuai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ZHIPU_AI_API_KEY", matches = ".+")
class ZhipuAiEmbeddingIT {

	@Autowired
	private ZhipuAiEmbeddingClient zhipuAiEmbeddingClient;

	@Test
	void defaultEmbedding() {
		assertThat(zhipuAiEmbeddingClient).isNotNull();
		var embeddingResponse = zhipuAiEmbeddingClient.embedForResponse(List.of("Hello World"));
		assertThat(embeddingResponse.getResults()).hasSize(1);
		assertThat(embeddingResponse.getResults().get(0)).isNotNull();
		assertThat(embeddingResponse.getResults().get(0).getOutput()).hasSize(1024);
		assertThat(embeddingResponse.getMetadata()).containsEntry("model", "embedding-2");
		assertThat(embeddingResponse.getMetadata()).containsEntry("total-tokens", 3);
		assertThat(embeddingResponse.getMetadata()).containsEntry("prompt-tokens", 3);
		assertThat(embeddingResponse.getMetadata()).containsEntry("completion_tokens", 0);
		assertThat(zhipuAiEmbeddingClient.dimensions()).isEqualTo(1024);
	}

	@Test
	void embeddingTest() {
		assertThat(zhipuAiEmbeddingClient).isNotNull();
		var embeddingResponse = zhipuAiEmbeddingClient.call(new EmbeddingRequest(List.of("Hello World", "World is big"),
				ZhipuAiEmbeddingOptions.builder().withModel("embedding-2").build()));
		assertThat(embeddingResponse.getResults()).hasSize(1);
		assertThat(embeddingResponse.getResults().get(0)).isNotNull();
		assertThat(embeddingResponse.getResults().get(0).getOutput()).hasSize(1024);
		assertThat(embeddingResponse.getMetadata()).containsEntry("model", "embedding-2");
		assertThat(embeddingResponse.getMetadata()).containsEntry("total-tokens", 7);
		assertThat(embeddingResponse.getMetadata()).containsEntry("prompt-tokens", 7);
		assertThat(embeddingResponse.getMetadata()).containsEntry("completion_tokens", 0);
		assertThat(zhipuAiEmbeddingClient.dimensions()).isEqualTo(1024);
	}

}
