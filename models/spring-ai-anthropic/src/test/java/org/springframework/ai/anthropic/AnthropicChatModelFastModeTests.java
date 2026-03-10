/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.anthropic;

import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.anthropic.api.AnthropicApi.ChatCompletionRequest;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.core.retry.RetryTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AnthropicChatModel} with fast mode support.
 *
 * @author Spring AI
 * @since 2.0.0
 */
@ExtendWith(MockitoExtension.class)
class AnthropicChatModelFastModeTests {

	@Mock
	private AnthropicApi anthropicApi;

	private AnthropicChatModel createChatModel(AnthropicChatOptions defaultOptions) {
		RetryTemplate retryTemplate = RetryUtils.SHORT_RETRY_TEMPLATE;
		ObservationRegistry observationRegistry = ObservationRegistry.NOOP;
		ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();
		return new AnthropicChatModel(this.anthropicApi, defaultOptions, toolCallingManager, retryTemplate,
				observationRegistry);
	}

	@Test
	void shouldSetSpeedInRequestBody() {
		AnthropicChatOptions defaultOptions = AnthropicChatOptions.builder()
			.model(AnthropicApi.ChatModel.CLAUDE_SONNET_4_5)
			.maxTokens(1024)
			.build();

		AnthropicChatModel chatModel = createChatModel(defaultOptions);

		AnthropicChatOptions requestOptions = AnthropicChatOptions.builder().speed("fast").build();

		Prompt prompt = new Prompt("Hello", requestOptions);

		ChatCompletionRequest request = chatModel.createRequest(prompt, false);

		assertThat(request.speed()).isEqualTo("fast");
	}

	@Test
	void shouldAddFastModeBetaHeaderWhenSpeedIsFast() {
		AnthropicChatOptions defaultOptions = AnthropicChatOptions.builder()
			.model(AnthropicApi.ChatModel.CLAUDE_SONNET_4_5)
			.maxTokens(1024)
			.build();

		AnthropicChatModel chatModel = createChatModel(defaultOptions);

		AnthropicChatOptions requestOptions = AnthropicChatOptions.builder().speed("fast").build();

		Prompt prompt = new Prompt("Hello", requestOptions);

		chatModel.createRequest(prompt, false);

		assertThat(requestOptions.getHttpHeaders()).containsKey("anthropic-beta");
		String betaHeader = requestOptions.getHttpHeaders().get("anthropic-beta");
		assertThat(betaHeader).contains(AnthropicApi.BETA_FAST_MODE);
	}

	@Test
	void shouldNotAddFastModeBetaHeaderWhenNoSpeed() {
		AnthropicChatOptions defaultOptions = AnthropicChatOptions.builder()
			.model(AnthropicApi.ChatModel.CLAUDE_SONNET_4_5)
			.maxTokens(1024)
			.build();

		AnthropicChatModel chatModel = createChatModel(defaultOptions);

		AnthropicChatOptions requestOptions = AnthropicChatOptions.builder().build();

		Prompt prompt = new Prompt("Hello", requestOptions);

		chatModel.createRequest(prompt, false);

		String betaHeader = requestOptions.getHttpHeaders().get("anthropic-beta");
		assertThat(betaHeader).isNull();
	}

	@Test
	void shouldNotAddFastModeBetaHeaderWhenSpeedIsNotFast() {
		AnthropicChatOptions defaultOptions = AnthropicChatOptions.builder()
			.model(AnthropicApi.ChatModel.CLAUDE_SONNET_4_5)
			.maxTokens(1024)
			.build();

		AnthropicChatModel chatModel = createChatModel(defaultOptions);

		AnthropicChatOptions requestOptions = AnthropicChatOptions.builder().speed("standard").build();

		Prompt prompt = new Prompt("Hello", requestOptions);

		chatModel.createRequest(prompt, false);

		String betaHeader = requestOptions.getHttpHeaders().get("anthropic-beta");
		assertThat(betaHeader).isNull();
	}

	@Test
	void shouldAppendFastModeBetaHeaderToExistingBetaHeaders() {
		AnthropicChatOptions defaultOptions = AnthropicChatOptions.builder()
			.model(AnthropicApi.ChatModel.CLAUDE_SONNET_4_5)
			.maxTokens(1024)
			.build();

		AnthropicChatModel chatModel = createChatModel(defaultOptions);

		java.util.Map<String, String> existingHeaders = new java.util.HashMap<>();
		existingHeaders.put("anthropic-beta", "some-other-beta");

		AnthropicChatOptions requestOptions = AnthropicChatOptions.builder()
			.speed("fast")
			.httpHeaders(existingHeaders)
			.build();

		Prompt prompt = new Prompt("Hello", requestOptions);

		chatModel.createRequest(prompt, false);

		String betaHeader = requestOptions.getHttpHeaders().get("anthropic-beta");
		assertThat(betaHeader).contains("some-other-beta").contains(AnthropicApi.BETA_FAST_MODE);
	}

}
