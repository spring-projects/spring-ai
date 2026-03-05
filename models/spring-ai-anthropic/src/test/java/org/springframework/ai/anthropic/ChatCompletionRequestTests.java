/*
 * Copyright 2023-2025 the original author or authors.
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

import org.junit.jupiter.api.Test;

import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 * @author Alexandros Pappas
 * @author Thomas Vitale
 */
public class ChatCompletionRequestTests {

	@Test
	public void createRequestWithChatOptions() {

		var client = AnthropicChatModel.builder()
			.anthropicApi(AnthropicApi.builder().apiKey("TEST").build())
			.defaultOptions(
					AnthropicChatOptions.builder().model("DEFAULT_MODEL").maxTokens(500).temperature(66.6).build())
			.build();

		var prompt = client.buildRequestPrompt(new Prompt("Test message content"));

		var request = client.createRequest(prompt, false);

		assertThat(request.messages()).hasSize(1);
		assertThat(request.stream()).isFalse();

		assertThat(request.model()).isEqualTo("DEFAULT_MODEL");
		assertThat(request.temperature()).isEqualTo(66.6);

		prompt = client.buildRequestPrompt(new Prompt("Test message content",
				AnthropicChatOptions.builder().model("PROMPT_MODEL").temperature(99.9).build()));

		request = client.createRequest(prompt, true);

		assertThat(request.messages()).hasSize(1);
		assertThat(request.stream()).isTrue();

		assertThat(request.model()).isEqualTo("PROMPT_MODEL");
		assertThat(request.temperature()).isEqualTo(99.9);
	}

	@Test
	public void createRequestWithToolChoice() {

		var client = AnthropicChatModel.builder()
			.anthropicApi(AnthropicApi.builder().apiKey("TEST").build())
			.defaultOptions(AnthropicChatOptions.builder().model("DEFAULT_MODEL").maxTokens(500).build())
			.build();

		// Test with ToolChoiceAuto
		var autoToolChoice = new AnthropicApi.ToolChoiceAuto();
		var prompt = client.buildRequestPrompt(
				new Prompt("Test message content", AnthropicChatOptions.builder().toolChoice(autoToolChoice).build()));

		var request = client.createRequest(prompt, false);

		assertThat(request.toolChoice()).isNotNull();
		assertThat(request.toolChoice()).isInstanceOf(AnthropicApi.ToolChoiceAuto.class);
		assertThat(request.toolChoice().type()).isEqualTo("auto");

		// Test with ToolChoiceAny
		var anyToolChoice = new AnthropicApi.ToolChoiceAny();
		prompt = client.buildRequestPrompt(
				new Prompt("Test message content", AnthropicChatOptions.builder().toolChoice(anyToolChoice).build()));

		request = client.createRequest(prompt, false);

		assertThat(request.toolChoice()).isNotNull();
		assertThat(request.toolChoice()).isInstanceOf(AnthropicApi.ToolChoiceAny.class);
		assertThat(request.toolChoice().type()).isEqualTo("any");

		// Test with ToolChoiceTool
		var specificToolChoice = new AnthropicApi.ToolChoiceTool("get_weather");
		prompt = client.buildRequestPrompt(new Prompt("Test message content",
				AnthropicChatOptions.builder().toolChoice(specificToolChoice).build()));

		request = client.createRequest(prompt, false);

		assertThat(request.toolChoice()).isNotNull();
		assertThat(request.toolChoice()).isInstanceOf(AnthropicApi.ToolChoiceTool.class);
		assertThat(request.toolChoice().type()).isEqualTo("tool");
		assertThat(((AnthropicApi.ToolChoiceTool) request.toolChoice()).name()).isEqualTo("get_weather");

		// Test with ToolChoiceNone
		var noneToolChoice = new AnthropicApi.ToolChoiceNone();
		prompt = client.buildRequestPrompt(
				new Prompt("Test message content", AnthropicChatOptions.builder().toolChoice(noneToolChoice).build()));

		request = client.createRequest(prompt, false);

		assertThat(request.toolChoice()).isNotNull();
		assertThat(request.toolChoice()).isInstanceOf(AnthropicApi.ToolChoiceNone.class);
		assertThat(request.toolChoice().type()).isEqualTo("none");

		// Test with disableParallelToolUse
		var autoWithDisabledParallel = new AnthropicApi.ToolChoiceAuto(true);
		prompt = client.buildRequestPrompt(new Prompt("Test message content",
				AnthropicChatOptions.builder().toolChoice(autoWithDisabledParallel).build()));

		request = client.createRequest(prompt, false);

		assertThat(request.toolChoice()).isNotNull();
		assertThat(request.toolChoice()).isInstanceOf(AnthropicApi.ToolChoiceAuto.class);
		assertThat(((AnthropicApi.ToolChoiceAuto) request.toolChoice()).disableParallelToolUse()).isTrue();
	}

}
