package org.springframework.ai.dashscope.embedding;

import org.junit.jupiter.api.Test;
import org.springframework.ai.dashscope.DashscopeEmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author nottyjay
 */
@SpringBootTest
public class EmbeddingIT {

	@Autowired
	private DashscopeEmbeddingModel dashscopeEmbeddingClient;

	@Test
	void defaultEmbedding() {
		assertThat(dashscopeEmbeddingClient).isNotNull();

		EmbeddingResponse embeddingResponse = dashscopeEmbeddingClient.embedForResponse(List.of("hello world"));
		System.out.println(embeddingResponse);
		assertThat(embeddingResponse.getResults()).hasSize(1);
		assertThat(embeddingResponse.getResults().get(0)).isNotNull();
		assertThat(embeddingResponse.getResults().get(0).getOutput()).hasSize(1536);
		assertThat(embeddingResponse.getMetadata()).containsEntry("model", "text-embedding-v1");
		assertThat(embeddingResponse.getMetadata()).containsEntry("total-tokens", 2);
	}

}
