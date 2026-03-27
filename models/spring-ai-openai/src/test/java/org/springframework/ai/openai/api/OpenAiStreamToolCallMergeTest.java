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

package org.springframework.ai.openai.api;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionChunk;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionChunk.ChunkChoice;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionFinishReason;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage.ChatCompletionFunction;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage.Role;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage.ToolCall;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for streaming tool call merging with OpenAI-compatible APIs (Qwen, DeepSeek via
 * vLLM/OpenRouter) that send the same tool call ID across multiple chunks.
 *
 * Reproduces the bug described in
 * <a href="https://github.com/spring-projects/spring-ai/issues/4790">#4790</a>.
 *
 * @author ChoMinGi
 */
class OpenAiStreamToolCallMergeTest {

	private final OpenAiStreamFunctionCallingHelper helper = new OpenAiStreamFunctionCallingHelper();

	/**
	 * Simulates the full OpenAiApi streaming pipeline (windowing + merging) with
	 * Qwen-style chunks where the same tool call ID is repeated across multiple chunks.
	 * Before the fix, this would produce multiple ToolCall objects with empty names,
	 * causing "toolName cannot be null or empty" in DefaultToolCallingManager.
	 */
	@Test
	void streamingPipeline_qwenSameIdToolCallChunks_shouldProduceSingleMergedToolCall() {
		// Qwen streaming pattern from issue #4790:
		// Chunk 1: id + name + empty args
		// Chunk 2: same id + empty name + partial args
		// Chunk 3-N: empty id + empty name + more args
		// Finish: finish_reason=tool_calls

		var chunk1 = createToolCallChunk("call_f7e76b4b", "init_work_status", "");
		var chunk2 = createToolCallChunk("call_f7e76b4b", "", "{\"firstStep");
		var chunk3 = createToolCallChunk("", "", "\": \"start\"}");
		var finishChunk = createFinishChunk(ChatCompletionFinishReason.TOOL_CALLS);

		Flux<ChatCompletionChunk> rawChunks = Flux.just(chunk1, chunk2, chunk3, finishChunk);

		List<ChatCompletionChunk> result = applyStreamingPipeline(rawChunks).collectList().block();

		// Find the chunk that contains tool calls
		ChatCompletionChunk toolChunk = result.stream()
			.filter(c -> !c.choices().isEmpty() && c.choices().get(0).delta() != null
					&& c.choices().get(0).delta().toolCalls() != null
					&& !c.choices().get(0).delta().toolCalls().isEmpty())
			.findFirst()
			.orElseThrow(() -> new AssertionError("No tool call chunk found"));

		List<ToolCall> toolCalls = toolChunk.choices().get(0).delta().toolCalls();
		assertThat(toolCalls).hasSize(1);
		assertThat(toolCalls.get(0).function().name()).isEqualTo("init_work_status");
		assertThat(toolCalls.get(0).function().arguments()).isEqualTo("{\"firstStep\": \"start\"}");
	}

	/**
	 * Simulates the Qwen streaming pattern where finish_reason is "stop" instead of
	 * "tool_calls". The windowing still closes when the stream completes, and reduce()
	 * merges all chunks correctly.
	 */
	@Test
	void streamingPipeline_qwenStopFinishReason_shouldStillMergeToolCalls() {
		var chunk1 = createToolCallChunk("call_abc123", "get_weather", "");
		var chunk2 = createToolCallChunk("call_abc123", "", "{\"location\":");
		var chunk3 = createToolCallChunk("", "", " \"Seoul\"}");
		var finishChunk = createFinishChunk(ChatCompletionFinishReason.STOP);

		Flux<ChatCompletionChunk> rawChunks = Flux.just(chunk1, chunk2, chunk3, finishChunk);

		List<ChatCompletionChunk> result = applyStreamingPipeline(rawChunks).collectList().block();

		// All chunks end up in one window (since finish_reason != TOOL_CALLS)
		// but reduce still merges them when stream completes
		boolean hasValidToolCall = result.stream().anyMatch(c -> {
			if (c.choices().isEmpty() || c.choices().get(0).delta() == null
					|| c.choices().get(0).delta().toolCalls() == null) {
				return false;
			}
			var toolCalls = c.choices().get(0).delta().toolCalls();
			return toolCalls.size() == 1 && "get_weather".equals(toolCalls.get(0).function().name())
					&& toolCalls.get(0).function().arguments().contains("Seoul");
		});

		assertThat(hasValidToolCall).isTrue();
	}

	/**
	 * Simulates the DeepSeek streaming pattern reported by wmaozhi where arguments are
	 * null in the first chunk and the same ID is used across chunks.
	 */
	@Test
	void streamingPipeline_deepSeekNullArguments_shouldMergeCorrectly() {
		var func1 = new ChatCompletionFunction("todoList", null);
		var toolCall1 = new ToolCall(0, "868f7f42", "function", func1);
		var delta1 = new ChatCompletionMessage(null, Role.ASSISTANT, null, null, List.of(toolCall1), null, null, null,
				null);
		var chunk1 = new ChatCompletionChunk("id", List.of(new ChunkChoice(null, 0, delta1, null)), 1L, "deepseek",
				null, null, null, null);

		var func2 = new ChatCompletionFunction(null, "{\"page");
		var toolCall2 = new ToolCall(0, "868f7f42", "function", func2);
		var delta2 = new ChatCompletionMessage(null, null, null, null, List.of(toolCall2), null, null, null, null);
		var chunk2 = new ChatCompletionChunk("id", List.of(new ChunkChoice(null, 0, delta2, null)), 1L, "deepseek",
				null, null, null, null);

		var func3 = new ChatCompletionFunction(null, "No\":1}");
		var toolCall3 = new ToolCall(0, "868f7f42", "function", func3);
		var delta3 = new ChatCompletionMessage(null, null, null, null, List.of(toolCall3), null, null, null, null);
		var chunk3 = new ChatCompletionChunk("id", List.of(new ChunkChoice(null, 0, delta3, null)), 1L, "deepseek",
				null, null, null, null);

		var finishChunk = createFinishChunk(ChatCompletionFinishReason.TOOL_CALLS);

		Flux<ChatCompletionChunk> rawChunks = Flux.just(chunk1, chunk2, chunk3, finishChunk);

		List<ChatCompletionChunk> result = applyStreamingPipeline(rawChunks).collectList().block();

		ChatCompletionChunk toolChunk = result.stream()
			.filter(c -> !c.choices().isEmpty() && c.choices().get(0).delta() != null
					&& c.choices().get(0).delta().toolCalls() != null
					&& !c.choices().get(0).delta().toolCalls().isEmpty())
			.findFirst()
			.orElseThrow(() -> new AssertionError("No tool call chunk found"));

		List<ToolCall> toolCalls = toolChunk.choices().get(0).delta().toolCalls();
		assertThat(toolCalls).hasSize(1);
		assertThat(toolCalls.get(0).function().name()).isEqualTo("todoList");
		assertThat(toolCalls.get(0).function().arguments()).isEqualTo("{\"pageNo\":1}");
	}

	/**
	 * Verifies that standard OpenAI streaming pattern (empty ID in continuation chunks)
	 * still works correctly after the fix.
	 */
	@Test
	void streamingPipeline_standardOpenAiPattern_shouldStillWork() {
		// Standard OpenAI: first chunk has ID+name, continuations have empty ID
		var chunk1 = createToolCallChunk("call_standard", "get_weather", "");
		var chunk2 = createToolCallChunk("", "", "{\"location\":");
		var chunk3 = createToolCallChunk("", "", " \"Tokyo\"}");
		var finishChunk = createFinishChunk(ChatCompletionFinishReason.TOOL_CALLS);

		Flux<ChatCompletionChunk> rawChunks = Flux.just(chunk1, chunk2, chunk3, finishChunk);

		List<ChatCompletionChunk> result = applyStreamingPipeline(rawChunks).collectList().block();

		ChatCompletionChunk toolChunk = result.stream()
			.filter(c -> !c.choices().isEmpty() && c.choices().get(0).delta() != null
					&& c.choices().get(0).delta().toolCalls() != null
					&& !c.choices().get(0).delta().toolCalls().isEmpty())
			.findFirst()
			.orElseThrow(() -> new AssertionError("No tool call chunk found"));

		List<ToolCall> toolCalls = toolChunk.choices().get(0).delta().toolCalls();
		assertThat(toolCalls).hasSize(1);
		assertThat(toolCalls.get(0).function().name()).isEqualTo("get_weather");
		assertThat(toolCalls.get(0).function().arguments()).isEqualTo("{\"location\": \"Tokyo\"}");
	}

	/**
	 * Verifies that parallel tool calls with different IDs are kept separate and not
	 * incorrectly merged.
	 */
	@Test
	void streamingPipeline_parallelToolCallsWithDifferentIds_shouldRemainSeparate() {
		var chunkA1 = createToolCallChunk("call_A", "get_weather", "{\"location\": \"Seoul\"}");
		var chunkB1 = createToolCallChunk("call_B", "get_time", "{\"timezone\": \"KST\"}");
		var finishChunk = createFinishChunk(ChatCompletionFinishReason.TOOL_CALLS);

		Flux<ChatCompletionChunk> rawChunks = Flux.just(chunkA1, chunkB1, finishChunk);

		List<ChatCompletionChunk> result = applyStreamingPipeline(rawChunks).collectList().block();

		ChatCompletionChunk toolChunk = result.stream()
			.filter(c -> !c.choices().isEmpty() && c.choices().get(0).delta() != null
					&& c.choices().get(0).delta().toolCalls() != null
					&& !c.choices().get(0).delta().toolCalls().isEmpty())
			.findFirst()
			.orElseThrow(() -> new AssertionError("No tool call chunk found"));

		List<ToolCall> toolCalls = toolChunk.choices().get(0).delta().toolCalls();
		assertThat(toolCalls).hasSize(2);
		assertThat(toolCalls.get(0).function().name()).isEqualTo("get_weather");
		assertThat(toolCalls.get(1).function().name()).isEqualTo("get_time");
	}

	/**
	 * Verifies the transition from merging same-ID chunks to starting a new tool call:
	 * A(id=X) → A(id=X, continuation) → B(id=Y, new tool call).
	 */
	@Test
	void streamingPipeline_sameIdThenDifferentId_shouldMergeThenSeparate() {
		var chunkA1 = createToolCallChunk("call_A", "get_weather", "");
		var chunkA2 = createToolCallChunk("call_A", "", "{\"location\": \"Seoul\"}");
		var chunkB1 = createToolCallChunk("call_B", "get_time", "{\"timezone\": \"KST\"}");
		var finishChunk = createFinishChunk(ChatCompletionFinishReason.TOOL_CALLS);

		Flux<ChatCompletionChunk> rawChunks = Flux.just(chunkA1, chunkA2, chunkB1, finishChunk);

		List<ChatCompletionChunk> result = applyStreamingPipeline(rawChunks).collectList().block();

		ChatCompletionChunk toolChunk = result.stream()
			.filter(c -> !c.choices().isEmpty() && c.choices().get(0).delta() != null
					&& c.choices().get(0).delta().toolCalls() != null
					&& !c.choices().get(0).delta().toolCalls().isEmpty())
			.findFirst()
			.orElseThrow(() -> new AssertionError("No tool call chunk found"));

		List<ToolCall> toolCalls = toolChunk.choices().get(0).delta().toolCalls();
		assertThat(toolCalls).hasSize(2);
		assertThat(toolCalls.get(0).function().name()).isEqualTo("get_weather");
		assertThat(toolCalls.get(0).function().arguments()).isEqualTo("{\"location\": \"Seoul\"}");
		assertThat(toolCalls.get(1).function().name()).isEqualTo("get_time");
	}

	/**
	 * Verifies that null arguments in the first chunk are handled correctly during merge.
	 */
	@Test
	void streamingPipeline_nullArgumentsInFirstChunk_shouldMergeCorrectly() {
		var func1 = new ChatCompletionFunction("search", null);
		var toolCall1 = new ToolCall(0, "call_null_args", "function", func1);
		var delta1 = new ChatCompletionMessage(null, Role.ASSISTANT, null, null, List.of(toolCall1), null, null, null,
				null);
		var chunk1 = new ChatCompletionChunk("chatcmpl-1", List.of(new ChunkChoice(null, 0, delta1, null)), 1L, "qwen",
				null, null, null, null);

		var chunk2 = createToolCallChunk("call_null_args", "", "{\"query\": \"spring ai\"}");
		var finishChunk = createFinishChunk(ChatCompletionFinishReason.TOOL_CALLS);

		Flux<ChatCompletionChunk> rawChunks = Flux.just(chunk1, chunk2, finishChunk);

		List<ChatCompletionChunk> result = applyStreamingPipeline(rawChunks).collectList().block();

		ChatCompletionChunk toolChunk = result.stream()
			.filter(c -> !c.choices().isEmpty() && c.choices().get(0).delta() != null
					&& c.choices().get(0).delta().toolCalls() != null
					&& !c.choices().get(0).delta().toolCalls().isEmpty())
			.findFirst()
			.orElseThrow(() -> new AssertionError("No tool call chunk found"));

		List<ToolCall> toolCalls = toolChunk.choices().get(0).delta().toolCalls();
		assertThat(toolCalls).hasSize(1);
		assertThat(toolCalls.get(0).function().name()).isEqualTo("search");
		assertThat(toolCalls.get(0).function().arguments()).isEqualTo("{\"query\": \"spring ai\"}");
	}

	/**
	 * Applies the same streaming pipeline as OpenAiApi.chatCompletionStream(): windowing
	 * by tool call boundaries and reducing each window via merge.
	 */
	private Flux<ChatCompletionChunk> applyStreamingPipeline(Flux<ChatCompletionChunk> rawChunks) {
		AtomicBoolean isInsideTool = new AtomicBoolean(false);

		return rawChunks.map(chunk -> {
			if (this.helper.isStreamingToolFunctionCall(chunk)) {
				isInsideTool.set(true);
			}
			return chunk;
		}).windowUntil(chunk -> {
			if (isInsideTool.get() && this.helper.isStreamingToolFunctionCallFinish(chunk)) {
				isInsideTool.set(false);
				return true;
			}
			return !isInsideTool.get();
		}).concatMapIterable(window -> {
			Mono<ChatCompletionChunk> monoChunk = window.reduce(
					new ChatCompletionChunk(null, null, null, null, null, null, null, null),
					(previous, current) -> this.helper.merge(previous, current));
			return List.of(monoChunk);
		}).flatMap(mono -> mono);
	}

	private ChatCompletionChunk createToolCallChunk(String toolCallId, String functionName, String arguments) {
		var function = new ChatCompletionFunction(functionName, arguments);
		var toolCall = new ToolCall(0, toolCallId, "function", function);
		var delta = new ChatCompletionMessage(null, Role.ASSISTANT, null, null, List.of(toolCall), null, null, null,
				null);
		var choice = new ChunkChoice(null, 0, delta, null);
		return new ChatCompletionChunk("chatcmpl-1", List.of(choice), 1L, "qwen", null, null, null, null);
	}

	private ChatCompletionChunk createFinishChunk(ChatCompletionFinishReason finishReason) {
		var delta = new ChatCompletionMessage(null, Role.ASSISTANT, null, null, null, null, null, null, null);
		var choice = new ChunkChoice(finishReason, 0, delta, null);
		return new ChatCompletionChunk("chatcmpl-1", List.of(choice), 1L, "qwen", null, null, null, null);
	}

}
