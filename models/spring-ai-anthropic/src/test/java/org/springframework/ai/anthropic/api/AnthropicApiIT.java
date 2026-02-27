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

package org.springframework.ai.anthropic.api;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import org.springframework.ai.anthropic.api.AnthropicApi.AnthropicMessage;
import org.springframework.ai.anthropic.api.AnthropicApi.ChatCompletionRequest;
import org.springframework.ai.anthropic.api.AnthropicApi.ChatCompletionResponse;
import org.springframework.ai.anthropic.api.AnthropicApi.ContentBlock;
import org.springframework.ai.anthropic.api.AnthropicApi.EventType;
import org.springframework.ai.anthropic.api.AnthropicApi.Role;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author Christian Tzolov
 * @author Jihoon Kim
 * @author Alexandros Pappas
 */
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
public class AnthropicApiIT {

	private static final Logger logger = LoggerFactory.getLogger(AnthropicApiIT.class);

	AnthropicApi anthropicApi = AnthropicApi.builder().apiKey(System.getenv("ANTHROPIC_API_KEY")).build();

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
			.chatCompletionEntity(ChatCompletionRequest.builder()
				.model(AnthropicApi.ChatModel.CLAUDE_HAIKU_4_5.getValue())
				.messages(List.of(chatCompletionMessage))
				.maxTokens(100)
				.temperature(0.8)
				.stream(false)
				.build());

		logger.info("Non-Streaming Response: {}", response.getBody());
		assertThat(response).isNotNull();
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().content()).isNotEmpty();
		assertThat(response.getBody().content().get(0).text()).isNotBlank();
		assertThat(response.getBody().stopReason()).isEqualTo("end_turn");
	}

	@Test
	void chatCompletionWithThinking() {
		AnthropicMessage chatCompletionMessage = new AnthropicMessage(
				List.of(new ContentBlock("Are there an infinite number of prime numbers such that n mod 4 == 3?")),
				Role.USER);

		ChatCompletionRequest request = ChatCompletionRequest.builder()
			.model(AnthropicApi.ChatModel.CLAUDE_SONNET_4_5.getValue())
			.messages(List.of(chatCompletionMessage))
			.maxTokens(8192)
			.temperature(1.0) // temperature should be set to 1 when thinking is enabled
			.thinking(new ChatCompletionRequest.ThinkingConfig(AnthropicApi.ThinkingType.ENABLED, 2048))
			.build();

		ResponseEntity<ChatCompletionResponse> response = this.anthropicApi.chatCompletionEntity(request);

		assertThat(response).isNotNull();
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().content()).isNotEmpty();

		boolean foundThinkingBlock = false;
		boolean foundTextBlock = false;

		List<ContentBlock> content = response.getBody().content();
		for (ContentBlock block : content) {
			if (block.type() == ContentBlock.Type.THINKING) {
				assertThat(block.thinking()).isNotBlank();
				assertThat(block.signature()).isNotBlank();
				foundThinkingBlock = true;
			}
			// Note: Redacted thinking might occur if budget is exceeded or other reasons.
			if (block.type() == ContentBlock.Type.REDACTED_THINKING) {
				assertThat(block.data()).isNotBlank();
			}
			if (block.type() == ContentBlock.Type.TEXT) {
				assertThat(block.text()).isNotBlank();
				foundTextBlock = true;
			}
		}

		assertThat(foundThinkingBlock).isTrue();
		assertThat(foundTextBlock).isTrue();
		assertThat(response.getBody().stopReason()).isEqualTo("end_turn");
	}

	@Test
	void chatCompletionStream() {

		AnthropicMessage chatCompletionMessage = new AnthropicMessage(List.of(new ContentBlock("Tell me a Joke?")),
				Role.USER);

		Flux<ChatCompletionResponse> response = this.anthropicApi.chatCompletionStream(ChatCompletionRequest.builder()
			.model(AnthropicApi.ChatModel.CLAUDE_HAIKU_4_5.getValue())
			.messages(List.of(chatCompletionMessage))
			.maxTokens(100)
			.temperature(0.8)
			.stream(true)
			.build());

		assertThat(response).isNotNull();

		List<ChatCompletionResponse> results = response.collectList().block();
		assertThat(results).isNotNull().isNotEmpty();

		results.forEach(chunk -> logger.info("Streaming Chunk: {}", chunk));

		// Verify the stream contains actual text content deltas
		String aggregatedText = results.stream()
			.filter(r -> !CollectionUtils.isEmpty(r.content()))
			.flatMap(r -> r.content().stream())
			.filter(cb -> cb.type() == ContentBlock.Type.TEXT_DELTA)
			.map(ContentBlock::text)
			.collect(Collectors.joining());
		assertThat(aggregatedText).isNotBlank();

		// Verify the final state
		ChatCompletionResponse lastMeaningfulResponse = results.stream()
			.filter(r -> StringUtils.hasText(r.stopReason()))
			.reduce((first, second) -> second)
			.orElse(results.get(results.size() - 1)); // Fallback to very last if no stop

		// StopReason found earlier
		assertThat(lastMeaningfulResponse.stopReason()).isEqualTo("end_turn");
		assertThat(lastMeaningfulResponse.usage()).isNotNull();
		assertThat(lastMeaningfulResponse.usage().outputTokens()).isPositive();
	}

	@Test
	void chatCompletionStreamWithThinking() {
		AnthropicMessage chatCompletionMessage = new AnthropicMessage(
				List.of(new ContentBlock("Are there an infinite number of prime numbers such that n mod 4 == 3?")),
				Role.USER);

		ChatCompletionRequest request = ChatCompletionRequest.builder()
			.model(AnthropicApi.ChatModel.CLAUDE_SONNET_4_5.getValue())
			.messages(List.of(chatCompletionMessage))
			.maxTokens(2048)
			.temperature(1.0)
			.stream(true)
			.thinking(new ChatCompletionRequest.ThinkingConfig(AnthropicApi.ThinkingType.ENABLED, 1024))
			.build();

		Flux<ChatCompletionResponse> responseFlux = this.anthropicApi.chatCompletionStream(request);

		assertThat(responseFlux).isNotNull();

		List<ChatCompletionResponse> results = responseFlux.collectList().block();
		assertThat(results).isNotNull().isNotEmpty();

		results.forEach(chunk -> logger.info("Streaming Thinking Chunk: {}", chunk));

		// Verify MESSAGE_START event exists
		assertThat(results.stream().anyMatch(r -> EventType.MESSAGE_START.name().equals(r.type()))).isTrue();
		assertThat(results.get(0).id()).isNotBlank();
		assertThat(results.get(0).role()).isEqualTo(Role.ASSISTANT);

		// Verify presence of THINKING_DELTA content
		boolean foundThinkingDelta = results.stream()
			.filter(r -> !CollectionUtils.isEmpty(r.content()))
			.flatMap(r -> r.content().stream())
			.anyMatch(cb -> cb.type() == ContentBlock.Type.THINKING_DELTA && StringUtils.hasText(cb.thinking()));
		assertThat(foundThinkingDelta).as("Should find THINKING_DELTA content").isTrue();

		// Verify presence of SIGNATURE_DELTA content
		boolean foundSignatureDelta = results.stream()
			.filter(r -> !CollectionUtils.isEmpty(r.content()))
			.flatMap(r -> r.content().stream())
			.anyMatch(cb -> cb.type() == ContentBlock.Type.SIGNATURE_DELTA && StringUtils.hasText(cb.signature()));
		assertThat(foundSignatureDelta).as("Should find SIGNATURE_DELTA content").isTrue();

		// Verify presence of TEXT_DELTA content (the actual answer)
		boolean foundTextDelta = results.stream()
			.filter(r -> !CollectionUtils.isEmpty(r.content()))
			.flatMap(r -> r.content().stream())
			.anyMatch(cb -> cb.type() == ContentBlock.Type.TEXT_DELTA && StringUtils.hasText(cb.text()));
		assertThat(foundTextDelta).as("Should find TEXT_DELTA content").isTrue();

		// Combine text deltas to check final answer structure
		String aggregatedText = results.stream()
			.filter(r -> !CollectionUtils.isEmpty(r.content()))
			.flatMap(r -> r.content().stream())
			.filter(cb -> cb.type() == ContentBlock.Type.TEXT_DELTA)
			.map(ContentBlock::text)
			.collect(Collectors.joining());
		assertThat(aggregatedText).as("Aggregated text response should not be blank").isNotBlank();
		logger.info("Aggregated Text from Stream: {}", aggregatedText);

		// Verify the final state (stop reason and usage)
		ChatCompletionResponse finalStateEvent = results.stream()
			.filter(r -> StringUtils.hasText(r.stopReason()))
			.reduce((first, second) -> second)
			.orElse(null);

		assertThat(finalStateEvent).as("Should find an event with stopReason").isNotNull();
		assertThat(finalStateEvent.stopReason()).isEqualTo("end_turn");
		assertThat(finalStateEvent.usage()).isNotNull();
		assertThat(finalStateEvent.usage().outputTokens()).isPositive();
		assertThat(finalStateEvent.usage().inputTokens()).isPositive();

		// Verify presence of key event types
		assertThat(results.stream().anyMatch(r -> EventType.CONTENT_BLOCK_START.name().equals(r.type())))
			.as("Should find CONTENT_BLOCK_START event")
			.isTrue();
		assertThat(results.stream().anyMatch(r -> EventType.CONTENT_BLOCK_STOP.name().equals(r.type())))
			.as("Should find CONTENT_BLOCK_STOP event")
			.isTrue();
		assertThat(results.stream()
			.anyMatch(r -> EventType.MESSAGE_STOP.name().equals(r.type()) || StringUtils.hasText(r.stopReason())))
			.as("Should find MESSAGE_STOP or MESSAGE_DELTA with stopReason")
			.isTrue();
	}

	@Test
	void chatCompletionStreamWithToolCall() {
		List<AnthropicMessage> messageConversation = new ArrayList<>();

		AnthropicMessage chatCompletionMessage = new AnthropicMessage(
				List.of(new ContentBlock("What's the weather like in San Francisco? Show the temperature in Celsius.")),
				Role.USER);

		messageConversation.add(chatCompletionMessage);

		ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest.builder()
			.model(AnthropicApi.ChatModel.CLAUDE_HAIKU_4_5)
			.messages(messageConversation)
			.maxTokens(1500)
			.stream(true)
			.temperature(0.8)
			.tools(this.tools)
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
		AnthropicApi api = AnthropicApi.builder().apiKey("FAKE_KEY_FOR_ERROR_RESPONSE").build();

		Flux<ChatCompletionResponse> response = api.chatCompletionStream(ChatCompletionRequest.builder()
			.model(AnthropicApi.ChatModel.CLAUDE_HAIKU_4_5.getValue())
			.messages(List.of(chatCompletionMessage))
			.maxTokens(100)
			.temperature(0.8)
			.stream(true)
			.build());

		assertThat(response).isNotNull();

		assertThatThrownBy(() -> response.collectList().block()).isInstanceOf(RuntimeException.class)
			.hasMessageStartingWith("Response exception, Status: [")
			.hasMessageContaining(
					"{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"invalid x-api-key\"}");
	}

}
