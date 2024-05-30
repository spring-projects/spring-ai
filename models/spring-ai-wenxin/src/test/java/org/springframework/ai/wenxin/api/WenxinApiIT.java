package org.springframework.ai.wenxin.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author lvchzh
 * @date 2024年05月22日 下午6:50
 * @description:
 */
@EnabledIfEnvironmentVariable(named = "ACCESS_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "SECRET_KEY", matches = ".+")
public class WenxinApiIT {

	WenxinApi wenxinApi = new WenxinApi(System.getenv("ACCESS_KEY"), System.getenv("SECRET_KEY"));

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

}
