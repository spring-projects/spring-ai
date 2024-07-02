package org.springframework.ai.wenxin.embedding;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.wenxin.WenxinEmbeddingModel;
import org.springframework.ai.wenxin.api.WenxinApi;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author lvchzh
 * @since 1.0.0
 */
@EnabledIfEnvironmentVariable(named = "WENXIN_ACCESS_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WENXIN_SECRET_KEY", matches = ".+")
public class WenxinEmbeddingIT {

	WenxinEmbeddingModel wenxinEmbeddingModel = new WenxinEmbeddingModel(
			new WenxinApi(System.getenv("WENXIN_ACCESS_KEY"), System.getenv("WENXIN_SECRET_KEY")));

	@Test
	void defaultEmbedding() {
		assertThat(wenxinEmbeddingModel).isNotNull();
		EmbeddingResponse embeddingResponse = wenxinEmbeddingModel.embedForResponse(List.of("Hello World"));
		System.out.println(embeddingResponse);
		assertThat(embeddingResponse.getResults()).hasSize(1);
		assertThat(embeddingResponse.getResults().get(0)).isNotNull();
		assertThat(embeddingResponse.getResults().get(0).getOutput()).hasSize(384);
		assertThat(embeddingResponse.getMetadata()).containsEntry("total-tokens", 2);
		assertThat(embeddingResponse.getMetadata()).containsEntry("prompt-tokens", 2);
		assertThat(wenxinEmbeddingModel.dimensions()).isEqualTo(384);

	}

}
