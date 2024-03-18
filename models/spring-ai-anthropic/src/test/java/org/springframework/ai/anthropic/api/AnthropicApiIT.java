/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
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

import org.springframework.ai.anthropic.api.AnthropicApi.ChatCompletion;
import org.springframework.ai.anthropic.api.AnthropicApi.RequestMessage;
import org.springframework.ai.anthropic.api.AnthropicApi.ChatCompletionRequest;
import org.springframework.ai.anthropic.api.AnthropicApi.MediaContent;
import org.springframework.ai.anthropic.api.AnthropicApi.Role;
import org.springframework.ai.anthropic.api.AnthropicApi.StreamResponse;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 */
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
public class AnthropicApiIT {

	AnthropicApi anthropicApi = new AnthropicApi(System.getenv("ANTHROPIC_API_KEY"));

	@Test
	void chatCompletionEntity() {

		RequestMessage chatCompletionMessage = new RequestMessage(List.of(new MediaContent("Tell me a Joke?")),
				Role.USER);
		ResponseEntity<ChatCompletion> response = anthropicApi
			.chatCompletionEntity(new ChatCompletionRequest(AnthropicApi.ChatModel.CLAUDE_3_OPUS.getValue(),
					List.of(chatCompletionMessage), null, 100, 0.8f, false));

		System.out.println(response);
		assertThat(response).isNotNull();
		assertThat(response.getBody()).isNotNull();
	}

	@Test
	void chatCompletionStream() {

		RequestMessage chatCompletionMessage = new RequestMessage(List.of(new MediaContent("Tell me a Joke?")),
				Role.USER);

		Flux<StreamResponse> response = anthropicApi
			.chatCompletionStream(new ChatCompletionRequest(AnthropicApi.ChatModel.CLAUDE_3_OPUS.getValue(),
					List.of(chatCompletionMessage), null, 100, 0.8f, true));

		assertThat(response).isNotNull();

		List<StreamResponse> bla = response.collectList().block();
		assertThat(bla).isNotNull();

		bla.stream().forEach(r -> System.out.println(r));
	}

}
