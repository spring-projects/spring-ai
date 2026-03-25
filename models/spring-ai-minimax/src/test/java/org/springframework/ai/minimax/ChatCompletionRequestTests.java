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

package org.springframework.ai.minimax;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.minimax.api.MiniMaxApi;
import org.springframework.ai.minimax.api.MiniMaxApi.ChatCompletionMessage.ReasoningDetail;
import org.springframework.ai.minimax.api.MockWeatherService;
import org.springframework.ai.tool.function.FunctionToolCallback;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Geng Rong
 * @author Ilayaperumal Gopinathan
 */
public class ChatCompletionRequestTests {

	@Test
	public void createRequestWithChatOptions() {

		var client = new MiniMaxChatModel(new MiniMaxApi("TEST"),
				MiniMaxChatOptions.builder().model("DEFAULT_MODEL").temperature(66.6).reasoningSplit(false).build());

		var request = client.createRequest(new Prompt("Test message content",
				MiniMaxChatOptions.builder().model("DEFAULT_MODEL").temperature(66.6).reasoningSplit(true).build()),
				false);

		assertThat(request.messages()).hasSize(1);
		assertThat(request.stream()).isFalse();

		assertThat(request.model()).isEqualTo("DEFAULT_MODEL");
		assertThat(request.temperature()).isEqualTo(66.6);
		assertThat(request.reasoningSplit()).isTrue();

		request = client.createRequest(new Prompt("Test message content",
				MiniMaxChatOptions.builder().model("PROMPT_MODEL").temperature(99.9).reasoningSplit(false).build()),
				true);

		assertThat(request.messages()).hasSize(1);
		assertThat(request.stream()).isTrue();

		assertThat(request.model()).isEqualTo("PROMPT_MODEL");
		assertThat(request.temperature()).isEqualTo(99.9);
		assertThat(request.reasoningSplit()).isFalse();
	}

	@Test
	public void promptOptionsTools() {

		final String TOOL_FUNCTION_NAME = "CurrentWeather";

		var client = new MiniMaxChatModel(new MiniMaxApi("TEST"),
				MiniMaxChatOptions.builder().model("DEFAULT_MODEL").build());

		var request = client.createRequest(new Prompt("Test message content",
				MiniMaxChatOptions.builder()
					.model("PROMPT_MODEL")
					.toolCallbacks(List.of(FunctionToolCallback.builder(TOOL_FUNCTION_NAME, new MockWeatherService())
						.description("Get the weather in location")
						.inputType(MockWeatherService.Request.class)
						.build()))
					.build()),
				false);

		assertThat(request.messages()).hasSize(1);
		assertThat(request.stream()).isFalse();
		assertThat(request.model()).isEqualTo("PROMPT_MODEL");

		assertThat(request.tools()).hasSize(1);
		assertThat(request.tools().get(0).getFunction().getName()).isEqualTo(TOOL_FUNCTION_NAME);
	}

	@Test
	public void defaultOptionsTools() {

		final String TOOL_FUNCTION_NAME = "CurrentWeather";

		var client = new MiniMaxChatModel(new MiniMaxApi("TEST"),
				MiniMaxChatOptions.builder()
					.model("DEFAULT_MODEL")
					.toolCallbacks(List.of(FunctionToolCallback.builder(TOOL_FUNCTION_NAME, new MockWeatherService())
						.description("Get the weather in location")
						.inputType(MockWeatherService.Request.class)
						.build()))
					.build());

		var prompt = client.buildRequestPrompt(new Prompt("Test message content"));

		var request = client.createRequest(prompt, false);

		assertThat(request.messages()).hasSize(1);
		assertThat(request.stream()).isFalse();
		assertThat(request.model()).isEqualTo("DEFAULT_MODEL");
	}

	@Test
	public void assistantMetadataReasoningDetailsRoundTrip() {
		var client = new MiniMaxChatModel(new MiniMaxApi("TEST"),
				MiniMaxChatOptions.builder().model("DEFAULT_MODEL").build());

		AssistantMessage assistantMessage = AssistantMessage.builder()
			.content("Previous assistant response")
			.properties(Map.of("reasoningDetails",
					List.of(new ReasoningDetail("reasoning.text", "reasoning-1", "MiniMax-response-v1", 0,
							"thinking text"))))
			.build();

		var request = client.createRequest(
				new Prompt(List.of(assistantMessage), MiniMaxChatOptions.builder().model("DEFAULT_MODEL").build()),
				false);

		assertThat(request.messages()).hasSize(1);
		assertThat(request.messages().get(0).reasoningDetails()).containsExactly(
				new ReasoningDetail("reasoning.text", "reasoning-1", "MiniMax-response-v1", 0, "thinking text"));
	}

}
