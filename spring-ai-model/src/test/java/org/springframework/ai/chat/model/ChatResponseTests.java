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

package org.springframework.ai.chat.model;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.ai.chat.messages.AssistantMessage.ToolCall;

/**
 * Unit tests for {@link ChatResponse}.
 *
 * @author Thomas Vitale
 * @author Heonwoo Kim
 */
class ChatResponseTests {

	@Test
	void whenToolCallsArePresentThenReturnTrue() {
		ChatResponse chatResponse = ChatResponse.builder()
			.generations(List.of(new Generation(
					new AssistantMessage("", Map.of(), List.of(new ToolCall("toolA", "function", "toolA", "{}"))))))
			.build();
		assertThat(chatResponse.hasToolCalls()).isTrue();
	}

	@Test
	void whenNoToolCallsArePresentThenReturnFalse() {
		ChatResponse chatResponse = ChatResponse.builder()
			.generations(List.of(new Generation(new AssistantMessage("Result"))))
			.build();
		assertThat(chatResponse.hasToolCalls()).isFalse();
	}

	@Test
	void whenFinishReasonIsNullThenThrow() {
		var chatResponse = ChatResponse.builder()
			.generations(List.of(new Generation(new AssistantMessage("Result"),
					ChatGenerationMetadata.builder().finishReason("completed").build())))
			.build();
		assertThatThrownBy(() -> chatResponse.hasFinishReasons(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("finishReasons cannot be null");
	}

	@Test
	void whenFinishReasonIsPresent() {
		ChatResponse chatResponse = ChatResponse.builder()
			.generations(List.of(new Generation(new AssistantMessage("Result"),
					ChatGenerationMetadata.builder().finishReason("completed").build())))
			.build();
		assertThat(chatResponse.hasFinishReasons(Set.of("completed"))).isTrue();
	}

	@Test
	void whenFinishReasonIsNotPresent() {
		ChatResponse chatResponse = ChatResponse.builder()
			.generations(List.of(new Generation(new AssistantMessage("Result"),
					ChatGenerationMetadata.builder().finishReason("failed").build())))
			.build();
		assertThat(chatResponse.hasFinishReasons(Set.of("completed"))).isFalse();
	}

	@Test
	void messageAggregatorShouldCorrectlyAggregateToolCallsFromStream() {

		MessageAggregator aggregator = new MessageAggregator();

		ChatResponse chunk1 = new ChatResponse(
				List.of(new Generation(new AssistantMessage("Thinking about the weather... "))));

		ToolCall weatherToolCall = new ToolCall("tool-id-123", "function", "getCurrentWeather",
				"{\"location\": \"Seoul\"}");

		Map<String, Object> metadataWithToolCall = Map.of("toolCalls", List.of(weatherToolCall));
		ChatResponseMetadata responseMetadataForChunk2 = ChatResponseMetadata.builder()
			.metadata(metadataWithToolCall)
			.build();

		ChatResponse chunk2 = new ChatResponse(List.of(new Generation(new AssistantMessage(""))),
				responseMetadataForChunk2);

		Flux<ChatResponse> streamingResponse = Flux.just(chunk1, chunk2);

		AtomicReference<ChatResponse> aggregatedResponseRef = new AtomicReference<>();

		aggregator.aggregate(streamingResponse, aggregatedResponseRef::set).blockLast();

		ChatResponse finalResponse = aggregatedResponseRef.get();
		assertThat(finalResponse).isNotNull();

		AssistantMessage finalAssistantMessage = finalResponse.getResult().getOutput();

		assertThat(finalAssistantMessage).isNotNull();
		assertThat(finalAssistantMessage.getText()).isEqualTo("Thinking about the weather... ");
		assertThat(finalAssistantMessage.hasToolCalls()).isTrue();
		assertThat(finalAssistantMessage.getToolCalls()).hasSize(1);

		ToolCall resultToolCall = finalAssistantMessage.getToolCalls().get(0);
		assertThat(resultToolCall.id()).isEqualTo("tool-id-123");
		assertThat(resultToolCall.name()).isEqualTo("getCurrentWeather");
		assertThat(resultToolCall.arguments()).isEqualTo("{\"location\": \"Seoul\"}");
	}

	@Test
	void whenEmptyGenerationsListThenReturnFalse() {
		ChatResponse chatResponse = ChatResponse.builder().generations(List.of()).build();
		assertThat(chatResponse.hasToolCalls()).isFalse();
	}

	@Test
	void whenMultipleGenerationsWithToolCallsThenReturnTrue() {
		ChatResponse chatResponse = ChatResponse.builder()
			.generations(List.of(new Generation(new AssistantMessage("First response")),
					new Generation(new AssistantMessage("", Map.of(),
							List.of(new ToolCall("toolB", "function", "toolB", "{}"))))))
			.build();
		assertThat(chatResponse.hasToolCalls()).isTrue();
	}

}
