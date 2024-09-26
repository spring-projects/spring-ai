/*
 * Copyright 2023-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.anthropic.api;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import reactor.core.publisher.Flux;

import org.springframework.ai.anthropic.api.AnthropicApi.AnthropicMessage;
import org.springframework.ai.anthropic.api.AnthropicApi.ChatCompletionRequest;
import org.springframework.ai.anthropic.api.AnthropicApi.ChatCompletionResponse;
import org.springframework.ai.anthropic.api.AnthropicApi.ContentBlock;
import org.springframework.ai.anthropic.api.AnthropicApi.Role;
import org.springframework.http.ResponseEntity;

import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author Christian Tzolov
 * @author Jihoon Kim
 */
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
public class AnthropicApiIT {

	AnthropicApi anthropicApi = new AnthropicApi(System.getenv("ANTHROPIC_API_KEY"));

	@Test
	void chatWithPromptCache() {
		String userMessageText = "It could be either a contraction of the full title Quenta Silmarillion (\"Tale of the Silmarils\") or also a plain Genitive which "
				+ "(as in Ancient Greek) signifies reference. This genitive is translated in English with \"about\" or \"of\" "
				+ "constructions; the titles of the chapters in The Silmarillion are examples of this genitive in poetic English "
				+ "(Of the Sindar, Of Men, Of the Darkening of Valinor etc), where \"of\" means \"about\" or \"concerning\". "
				+ "In the same way, Silmarillion can be taken to mean \"Of/About the Silmarils\"";

		AnthropicMessage chatCompletionMessage = new AnthropicMessage(
				List.of(new ContentBlock(userMessageText.repeat(20), AnthropicCacheType.EPHEMERAL.cacheControl())),
				Role.USER);

		ChatCompletionRequest chatCompletionRequest = new ChatCompletionRequest(
				AnthropicApi.ChatModel.CLAUDE_3_HAIKU.getValue(), List.of(chatCompletionMessage), null, 100, 0.8,
				false);
		AnthropicApi.Usage createdCacheToken = anthropicApi.chatCompletionEntity(chatCompletionRequest)
			.getBody()
			.usage();

		assertThat(createdCacheToken.cacheCreationInputTokens()).isGreaterThan(0);
		assertThat(createdCacheToken.cacheReadInputTokens()).isEqualTo(0);

		AnthropicApi.Usage readCacheToken = anthropicApi.chatCompletionEntity(chatCompletionRequest).getBody().usage();

		assertThat(readCacheToken.cacheCreationInputTokens()).isEqualTo(0);
		assertThat(readCacheToken.cacheReadInputTokens()).isGreaterThan(0);
	}

	@Test
	void chatCompletionEntity() {

		AnthropicMessage chatCompletionMessage = new AnthropicMessage(List.of(new ContentBlock("Tell me a Joke?")),
				Role.USER);
		ResponseEntity<ChatCompletionResponse> response = this.anthropicApi
			.chatCompletionEntity(new ChatCompletionRequest(AnthropicApi.ChatModel.CLAUDE_3_OPUS.getValue(),
					List.of(chatCompletionMessage), null, 100, 0.8, false));

		System.out.println(response);
		assertThat(response).isNotNull();
		assertThat(response.getBody()).isNotNull();
	}

	@Test
	void chatCompletionStream() {

		AnthropicMessage chatCompletionMessage = new AnthropicMessage(List.of(new ContentBlock("Tell me a Joke?")),
				Role.USER);

		Flux<ChatCompletionResponse> response = this.anthropicApi.chatCompletionStream(new ChatCompletionRequest(
				AnthropicApi.ChatModel.CLAUDE_3_OPUS.getValue(), List.of(chatCompletionMessage), null, 100, 0.8, true));

		assertThat(response).isNotNull();

		List<ChatCompletionResponse> bla = response.collectList().block();
		assertThat(bla).isNotNull();

		bla.stream().forEach(r -> System.out.println(r));
	}

	@Test
	void chatCompletionStreamError() {
		AnthropicMessage chatCompletionMessage = new AnthropicMessage(List.of(new ContentBlock("Tell me a Joke?")),
				Role.USER);
		AnthropicApi api = new AnthropicApi("FAKE_KEY_FOR_ERROR_RESPONSE");

		Flux<ChatCompletionResponse> response = api.chatCompletionStream(new ChatCompletionRequest(
				AnthropicApi.ChatModel.CLAUDE_3_OPUS.getValue(), List.of(chatCompletionMessage), null, 100, 0.8, true));

		assertThat(response).isNotNull();

		assertThatThrownBy(() -> response.collectList().block()).isInstanceOf(RuntimeException.class)
			.hasMessageStartingWith("Response exception, Status: [")
			.hasMessageContaining(
					"{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"invalid x-api-key\"}}");
	}

}
