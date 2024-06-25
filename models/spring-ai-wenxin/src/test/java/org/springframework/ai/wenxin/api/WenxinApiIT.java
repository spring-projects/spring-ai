package org.springframework.ai.wenxin.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author lvchzh
 * @since 1.0.0
 */
@EnabledIfEnvironmentVariable(named = "WENXIN_ACCESS_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WENXIN_SECRET_KEY", matches = ".+")
public class WenxinApiIT {

	WenxinApi wenxinApi = new WenxinApi(System.getenv("WENXIN_ACCESS_KEY"), System.getenv("WENXIN_SECRET_KEY"));

	@Test
	void chatCompletionEntity() {
		WenxinApi.ChatCompletionMessage chatCompletionMessage = new WenxinApi.ChatCompletionMessage("Tell me a joke",
				WenxinApi.Role.USER);
		ResponseEntity<WenxinApi.ChatCompletion> response = wenxinApi.chatCompletionEntity(
				new WenxinApi.ChatCompletionRequest(List.of(chatCompletionMessage), "completions", 0.8f, false));
		System.out.println(response.getBody().result());
		assertThat(response).isNotNull();
		assertThat(response.getBody()).isNotNull();
	}

	@Test
	void chatCompletionStream() {
		WenxinApi.ChatCompletionMessage chatCompletionMessage = new WenxinApi.ChatCompletionMessage("Tell me a joke",
				WenxinApi.Role.USER);
		Flux<WenxinApi.ChatCompletionChunk> response = wenxinApi.chatCompletionStream(
				new WenxinApi.ChatCompletionRequest(List.of(chatCompletionMessage), "completions", 0.8f, true));

		assertThat(response).isNotNull();
		assertThat(response.collectList().block()).isNotNull();
	}

	@Test
	void embeddings() {
		ResponseEntity<WenxinApi.EmbeddingList<WenxinApi.Embedding>> response = wenxinApi
			.embeddings(new WenxinApi.EmbeddingRequest<>(List.of("Hello world")));

		assertThat(response).isNotNull();
		assertThat(response.getBody().data()).hasSize(1);
		assertThat(response.getBody().data().get(0).embedding()).hasSize(384);
	}

}
