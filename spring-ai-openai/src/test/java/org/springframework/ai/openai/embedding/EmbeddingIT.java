package org.springframework.ai.openai.embedding;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class EmbeddingIT {

	@Autowired
	private OpenAiEmbeddingClient embeddingClient;

	@Test
	void simpleEmbedding() {
		assertThat(embeddingClient).isNotNull();

		EmbeddingResponse embeddingResponse = embeddingClient.embedForResponse(List.of("Hello World"));
		System.out.println(embeddingResponse);
		assertThat(embeddingResponse.getData()).hasSize(1);
		assertThat(embeddingResponse.getData().get(0).getEmbedding()).isNotEmpty();
		assertThat(embeddingResponse.getMetadata()).containsEntry("model", "text-embedding-ada-002-v2");
		assertThat(embeddingResponse.getMetadata()).containsEntry("completion-tokens", 0L);
		assertThat(embeddingResponse.getMetadata()).containsEntry("total-tokens", 2L);
		assertThat(embeddingResponse.getMetadata()).containsEntry("prompt-tokens", 2L);

		assertThat(embeddingClient.dimensions()).isEqualTo(1536);
	}

}
