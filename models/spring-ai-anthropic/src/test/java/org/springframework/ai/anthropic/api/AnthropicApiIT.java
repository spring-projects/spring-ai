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

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.model.ModelOptionsUtils;
import reactor.core.publisher.Flux;

import org.springframework.ai.anthropic.api.AnthropicApi.AnthropicMessage;
import org.springframework.ai.anthropic.api.AnthropicApi.ChatCompletionRequest;
import org.springframework.ai.anthropic.api.AnthropicApi.ChatCompletionResponse;
import org.springframework.ai.anthropic.api.AnthropicApi.ContentBlock;
import org.springframework.ai.anthropic.api.AnthropicApi.Role;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author Christian Tzolov
 * @author Jihoon Kim
 */
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
public class AnthropicApiIT {

	AnthropicApi anthropicApi = new AnthropicApi(System.getenv("ANTHROPIC_API_KEY"));

	List<AnthropicApi.Tool> tools = List.of(new AnthropicApi.Tool("getCurrentWeather",
			"Get the weather in location. Return temperature in 30°F or 30°C format.", ModelOptionsUtils.jsonToMap("""
					{
						"type": "object",
						"properties": {
							"location": {
								"type": "string",
								"description": "The city and state e.g. San Francisco, CA"
							},
							"unit": {
								"type": "string",
								"enum": ["C", "F"]
							}
						},
						"required": ["location", "unit"]
					}
					""")));

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
	void chatCompletionStreamWithToolCall() {
		List<AnthropicMessage> messageConversation = new ArrayList<>();

		AnthropicMessage chatCompletionMessage = new AnthropicMessage(
				List.of(new ContentBlock("What's the weather like in San Francisco? Show the temperature in Celsius.")),
				Role.USER);

		messageConversation.add(chatCompletionMessage);

		ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest.builder()
			.withModel(AnthropicApi.ChatModel.CLAUDE_3_OPUS)
			.withMessages(messageConversation)
			.withMaxTokens(1500)
			.withStream(true)
			.withTemperature(0.8)
			.withTools(tools)
			.build();

		List<ChatCompletionResponse> responses = this.anthropicApi.chatCompletionStream(chatCompletionRequest)
			.collectList()
			.block();

		// Check that tool uses response returned only once
		List<ChatCompletionResponse> toolCompletionResponses = responses.stream()
			.filter(r -> r.stopReason() != null && r.stopReason().equals(ContentBlock.Type.TOOL_USE.value))
			.toList();
		assertThat(toolCompletionResponses).size().isEqualTo(1);
		List<ContentBlock> toolContentBlocks = toolCompletionResponses.get(0).content();
		assertThat(toolContentBlocks).size().isEqualTo(1);
		ContentBlock toolContentBlock = toolContentBlocks.get(0);
		assertThat(toolContentBlock.type()).isEqualTo(ContentBlock.Type.TOOL_USE);
		assertThat(toolContentBlock.name()).isEqualTo("getCurrentWeather");

		// Check that message stop response also returned
		List<ChatCompletionResponse> messageStopEvents = responses.stream()
			.filter(r -> r.type().equals(AnthropicApi.EventType.MESSAGE_STOP.name()))
			.toList();
		assertThat(messageStopEvents).size().isEqualTo(1);
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
