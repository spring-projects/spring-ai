package org.springframework.ai.wenxin.embedding;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.wenxin.WenxinEmbeddingModel;
import org.springframework.ai.wenxin.api.WenxinApi;

import java.util.List;

/**
 * @author lvchzh
 * @date 2024年05月30日 下午4:13
 * @description:
 */
@EnabledIfEnvironmentVariable(named = "ACCESS_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "SECRET_KEY", matches = ".+")
public class EmbeddingIT {

	WenxinEmbeddingModel wenxinEmbeddingModel = new WenxinEmbeddingModel(
			new WenxinApi(System.getenv("ACCESS_KEY"), System.getenv("SECRET_KEY")));

	@Test
	void defaultEmbedding() {
		EmbeddingResponse embeddingResponse = wenxinEmbeddingModel.embedForResponse(List.of("Hello World"));
		System.out.println(embeddingResponse);

	}

}
