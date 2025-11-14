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
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.messages.AssistantMessage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link MessageAggregator} with streaming tool calls that lack IDs in
 * subsequent chunks. This pattern is common in OpenAI-compatible APIs.
 *
 * @author Taewoong Kim
 */
class MessageAggregatorTests {

	private final MessageAggregator messageAggregator = new MessageAggregator();

	/**
	 * Test merging of tool calls when subsequent chunks have no ID. First chunk contains
	 * the tool name and ID, subsequent chunks contain only arguments.
	 */
	@Test
	void shouldMergeToolCallsWithoutIds() {
		// Chunk 1: ID and name present
		ChatResponse chunk1 = new ChatResponse(List.of(new Generation(AssistantMessage.builder()
			.toolCalls(List.of(new AssistantMessage.ToolCall("chatcmpl-tool-123", "function", "getCurrentWeather", "")))
			.build())));

		// Chunk 2-5: No ID, only arguments (common streaming pattern)
		ChatResponse chunk2 = new ChatResponse(List.of(new Generation(AssistantMessage.builder()
			.toolCalls(List.of(new AssistantMessage.ToolCall("", "function", "", "{\"location\": \"")))
			.build())));

		ChatResponse chunk3 = new ChatResponse(List.of(new Generation(AssistantMessage.builder()
			.toolCalls(List.of(new AssistantMessage.ToolCall("", "function", "", "Se")))
			.build())));

		ChatResponse chunk4 = new ChatResponse(List.of(new Generation(AssistantMessage.builder()
			.toolCalls(List.of(new AssistantMessage.ToolCall("", "function", "", "oul")))
			.build())));

		ChatResponse chunk5 = new ChatResponse(List.of(new Generation(AssistantMessage.builder()
			.toolCalls(List.of(new AssistantMessage.ToolCall("", "function", "", "\"}")))
			.build())));

		Flux<ChatResponse> flux = Flux.just(chunk1, chunk2, chunk3, chunk4, chunk5);

		// When: Aggregate the streaming responses
		AtomicReference<ChatResponse> finalResponse = new AtomicReference<>();
		this.messageAggregator.aggregate(flux, finalResponse::set).blockLast();

		// Then: Verify the tool call was properly merged
		assertThat(finalResponse.get()).isNotNull();
		List<AssistantMessage.ToolCall> toolCalls = finalResponse.get().getResult().getOutput().getToolCalls();

		assertThat(toolCalls).hasSize(1);
		AssistantMessage.ToolCall mergedToolCall = toolCalls.get(0);

		assertThat(mergedToolCall.id()).isEqualTo("chatcmpl-tool-123");
		assertThat(mergedToolCall.name()).isEqualTo("getCurrentWeather");
		assertThat(mergedToolCall.arguments()).isEqualTo("{\"location\": \"Seoul\"}");
	}

	/**
	 * Test multiple tool calls being streamed simultaneously. Each tool call has its own
	 * ID in the first chunk, and subsequent chunks have no ID but are merged with the
	 * last tool call.
	 */
	@Test
	void shouldMergeMultipleToolCallsWithMixedIds() {
		// Given: Multiple tool calls being streamed
		// Chunk 1: First tool call starts with ID
		ChatResponse chunk1 = new ChatResponse(List.of(new Generation(AssistantMessage.builder()
			.toolCalls(List.of(new AssistantMessage.ToolCall("tool-1", "function", "getWeather", "")))
			.build())));

		// Chunk 2: Argument for first tool call (no ID)
		ChatResponse chunk2 = new ChatResponse(List.of(new Generation(AssistantMessage.builder()
			.toolCalls(List.of(new AssistantMessage.ToolCall("", "function", "", "{\"city\":\"Tokyo\"}")))
			.build())));

		// Chunk 3: Second tool call starts with ID
		ChatResponse chunk3 = new ChatResponse(List.of(new Generation(AssistantMessage.builder()
			.toolCalls(List.of(new AssistantMessage.ToolCall("tool-2", "function", "getTime", "")))
			.build())));

		// Chunk 4: Argument for second tool call (no ID)
		ChatResponse chunk4 = new ChatResponse(List.of(new Generation(AssistantMessage.builder()
			.toolCalls(List.of(new AssistantMessage.ToolCall("", "function", "", "{\"timezone\":\"JST\"}")))
			.build())));

		Flux<ChatResponse> flux = Flux.just(chunk1, chunk2, chunk3, chunk4);

		// When: Aggregate the streaming responses
		AtomicReference<ChatResponse> finalResponse = new AtomicReference<>();
		this.messageAggregator.aggregate(flux, finalResponse::set).blockLast();

		// Then: Verify both tool calls were properly merged
		assertThat(finalResponse.get()).isNotNull();
		List<AssistantMessage.ToolCall> toolCalls = finalResponse.get().getResult().getOutput().getToolCalls();

		assertThat(toolCalls).hasSize(2);

		AssistantMessage.ToolCall firstToolCall = toolCalls.get(0);
		assertThat(firstToolCall.id()).isEqualTo("tool-1");
		assertThat(firstToolCall.name()).isEqualTo("getWeather");
		assertThat(firstToolCall.arguments()).isEqualTo("{\"city\":\"Tokyo\"}");

		AssistantMessage.ToolCall secondToolCall = toolCalls.get(1);
		assertThat(secondToolCall.id()).isEqualTo("tool-2");
		assertThat(secondToolCall.name()).isEqualTo("getTime");
		assertThat(secondToolCall.arguments()).isEqualTo("{\"timezone\":\"JST\"}");
	}

	/**
	 * Test that tool calls with IDs are still matched correctly by ID, even when they
	 * arrive in different chunks.
	 */
	@Test
	void shouldMergeToolCallsById() {
		// Given: Chunks with same ID arriving separately
		ChatResponse chunk1 = new ChatResponse(List.of(new Generation(AssistantMessage.builder()
			.toolCalls(List.of(new AssistantMessage.ToolCall("tool-1", "function", "getWeather", "{\"ci")))
			.build())));

		ChatResponse chunk2 = new ChatResponse(List.of(new Generation(AssistantMessage.builder()
			.toolCalls(List.of(new AssistantMessage.ToolCall("tool-1", "function", "", "ty\":\"Paris\"}")))
			.build())));

		Flux<ChatResponse> flux = Flux.just(chunk1, chunk2);

		// When: Aggregate the streaming responses
		AtomicReference<ChatResponse> finalResponse = new AtomicReference<>();
		this.messageAggregator.aggregate(flux, finalResponse::set).blockLast();

		// Then: Verify the tool call was merged by ID
		assertThat(finalResponse.get()).isNotNull();
		List<AssistantMessage.ToolCall> toolCalls = finalResponse.get().getResult().getOutput().getToolCalls();

		assertThat(toolCalls).hasSize(1);
		AssistantMessage.ToolCall mergedToolCall = toolCalls.get(0);
		assertThat(mergedToolCall.id()).isEqualTo("tool-1");
		assertThat(mergedToolCall.name()).isEqualTo("getWeather");
		assertThat(mergedToolCall.arguments()).isEqualTo("{\"city\":\"Paris\"}");
	}

}
