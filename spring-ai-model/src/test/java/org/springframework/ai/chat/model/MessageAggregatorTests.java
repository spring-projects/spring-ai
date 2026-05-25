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

package org.springframework.ai.chat.model;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link MessageAggregator} streaming tool call merging.
 */
class MessageAggregatorTests {

	private final MessageAggregator aggregator = new MessageAggregator();

	@Test
	void should_merge_tool_call_chunks_into_single_complete_tool_call() {
		// Streaming chunks: first has id+name, rest have empty id and append arguments
		List<ChatResponse> chunks = List.of(
				chatResponseWithToolCall(new ToolCall("call_123", "function", "queryCourse", "")),
				chatResponseWithToolCall(new ToolCall("", "", "", "{\"query\":\"edu\"")),
				chatResponseWithToolCall(new ToolCall("", "", "", ",\"page\":4")),
				chatResponseWithToolCall(new ToolCall("", "", "", "}")));

		ChatResponse aggregated = aggregateChunks(chunks);

		List<ToolCall> toolCalls = aggregated.getResult().getOutput().getToolCalls();
		assertThat(toolCalls).hasSize(1);
		ToolCall merged = toolCalls.get(0);
		assertThat(merged.id()).isEqualTo("call_123");
		assertThat(merged.name()).isEqualTo("queryCourse");
		assertThat(merged.arguments()).isEqualTo("{\"query\":\"edu\",\"page\":4}");
	}

	@Test
	void should_pass_through_complete_tool_call_unchanged() {
		List<ChatResponse> chunks = List
			.of(chatResponseWithToolCall(new ToolCall("call_abc", "function", "myTool", "{\"key\":\"value\"}")));

		ChatResponse aggregated = aggregateChunks(chunks);

		List<ToolCall> toolCalls = aggregated.getResult().getOutput().getToolCalls();
		assertThat(toolCalls).hasSize(1);
		assertThat(toolCalls.get(0).id()).isEqualTo("call_abc");
		assertThat(toolCalls.get(0).arguments()).isEqualTo("{\"key\":\"value\"}");
	}

	@Test
	void should_merge_multiple_parallel_tool_calls() {
		// Two tool calls streamed in sequence, each with their own chunks
		List<ChatResponse> chunks = List.of(
				chatResponseWithToolCall(new ToolCall("call_1", "function", "toolA", "{\"a\":")),
				chatResponseWithToolCall(new ToolCall("", "", "", "1}")),
				chatResponseWithToolCall(new ToolCall("call_2", "function", "toolB", "{\"b\":")),
				chatResponseWithToolCall(new ToolCall("", "", "", "2}")));

		ChatResponse aggregated = aggregateChunks(chunks);

		List<ToolCall> toolCalls = aggregated.getResult().getOutput().getToolCalls();
		assertThat(toolCalls).hasSize(2);

		assertThat(toolCalls.get(0).id()).isEqualTo("call_1");
		assertThat(toolCalls.get(0).name()).isEqualTo("toolA");
		assertThat(toolCalls.get(0).arguments()).isEqualTo("{\"a\":1}");

		assertThat(toolCalls.get(1).id()).isEqualTo("call_2");
		assertThat(toolCalls.get(1).name()).isEqualTo("toolB");
		assertThat(toolCalls.get(1).arguments()).isEqualTo("{\"b\":2}");
	}

	@Test
	void should_handle_chunk_with_null_arguments() {
		List<ChatResponse> chunks = List.of(
				chatResponseWithToolCall(new ToolCall("call_x", "function", "myTool", null)),
				chatResponseWithToolCall(new ToolCall("", "", "", "{\"k\":\"v\"}")));

		ChatResponse aggregated = aggregateChunks(chunks);

		List<ToolCall> toolCalls = aggregated.getResult().getOutput().getToolCalls();
		assertThat(toolCalls).hasSize(1);
		assertThat(toolCalls.get(0).arguments()).isEqualTo("{\"k\":\"v\"}");
	}

	private ChatResponse chatResponseWithToolCall(ToolCall toolCall) {
		AssistantMessage message = AssistantMessage.builder().toolCalls(List.of(toolCall)).build();
		return new ChatResponse(List.of(new Generation(message)));
	}

	private ChatResponse aggregateChunks(List<ChatResponse> chunks) {
		AtomicReference<ChatResponse> result = new AtomicReference<>();
		this.aggregator.aggregate(Flux.fromIterable(chunks), result::set).blockLast();
		return result.get();
	}

}
