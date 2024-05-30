package org.springframework.ai.yi.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.yi.api.YiAiApi.ChatCompletionChunk;
import org.springframework.ai.yi.api.YiAiApi.ChatCompletionMessage;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "YI_AI_API_KEY", matches = ".+")
public class YiAiApiIT {

	YiAiApi yiAiApi = new YiAiApi(System.getenv("YI_AI_API_KEY"));

	@Test
	void chatCompletionEntity() {
		ChatCompletionMessage chatCompletionMessage = new ChatCompletionMessage("Hello world",
				ChatCompletionMessage.Role.USER);
		ResponseEntity<YiAiApi.ChatCompletion> response = yiAiApi.chatCompletionEntity(
				new YiAiApi.ChatCompletionRequest(List.of(chatCompletionMessage), "yi-large", 0.3f, false));

		assertThat(response).isNotNull();
		assertThat(response.getBody()).isNotNull();
	}

	@Test
	void chatCompletionStream() {
		ChatCompletionMessage chatCompletionMessage = new ChatCompletionMessage("Hello world",
				ChatCompletionMessage.Role.USER);
		Flux<ChatCompletionChunk> response = yiAiApi.chatCompletionStream(
				new YiAiApi.ChatCompletionRequest(List.of(chatCompletionMessage), "yi-large", 0.3f, true));

		assertThat(response).isNotNull();
		assertThat(response.collectList().block()).isNotNull();
	}

}
