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
 * Unit tests for {@link MessageAggregator}, with a focus on streaming tool-call delta
 * aggregation behaviour introduced for partial {@link ToolCall} frames.
 *
 * @author Spring AI
 */
class MessageAggregatorTests {

	private static ChatResponse responseWithToolCalls(List<ToolCall> toolCalls) {
		return new ChatResponse(
				List.of(new Generation(AssistantMessage.builder().content("").toolCalls(toolCalls).build())));
	}

	private static ChatResponse aggregate(Flux<ChatResponse> source) {
		AtomicReference<ChatResponse> result = new AtomicReference<>();
		new MessageAggregator().aggregate(source, result::set).blockLast();
		return result.get();
	}

	@Test
	void aggregatesLegacyCompleteToolCallUnchanged() {
		// Provider that doesn't emit deltas — single non-partial frame.
		ChatResponse aggregated = aggregate(Flux.just(responseWithToolCalls(
				List.of(new ToolCall("call_1", "function", "writeCode", "{\"src\":\"print(1)\"}")))));

		List<ToolCall> calls = aggregated.getResult().getOutput().getToolCalls();
		assertThat(calls).hasSize(1);
		assertThat(calls.get(0).id()).isEqualTo("call_1");
		assertThat(calls.get(0).name()).isEqualTo("writeCode");
		assertThat(calls.get(0).arguments()).isEqualTo("{\"src\":\"print(1)\"}");
		assertThat(calls.get(0).partial()).isFalse();
	}

	@Test
	void aggregatesPartialFragmentsThenAuthoritativeFinalFrame() {
		// Realistic streaming flow: multiple partials carrying argument deltas, then
		// a single non-partial frame with the complete arguments string.
		ChatResponse aggregated = aggregate(Flux.just(
				responseWithToolCalls(List.of(new ToolCall("call_1", "function", "writeCode", "", true))),
				responseWithToolCalls(List.of(new ToolCall("call_1", "function", "writeCode", "{\"src\":", true))),
				responseWithToolCalls(List.of(new ToolCall("call_1", "function", "writeCode", "\"print(1)\"", true))),
				responseWithToolCalls(List.of(new ToolCall("call_1", "function", "writeCode", "}", true))),
				responseWithToolCalls(
						List.of(new ToolCall("call_1", "function", "writeCode", "{\"src\":\"print(1)\"}", false)))));

		List<ToolCall> calls = aggregated.getResult().getOutput().getToolCalls();
		assertThat(calls).hasSize(1);
		// The non-partial final frame is authoritative.
		assertThat(calls.get(0).arguments()).isEqualTo("{\"src\":\"print(1)\"}");
		assertThat(calls.get(0).partial()).isFalse();
	}

	@Test
	void fallsBackToConcatenatingPartialsWhenNoFinalFrame() {
		// Defensive path: provider emits only partials and no terminating non-partial
		// frame. The aggregator must still produce a usable concatenated arguments
		// string (otherwise downstream tool execution would have nothing to parse).
		ChatResponse aggregated = aggregate(Flux.just(
				responseWithToolCalls(List.of(new ToolCall("call_1", "function", "writeCode", "{\"a\":", true))),
				responseWithToolCalls(List.of(new ToolCall("call_1", "function", "writeCode", "1}", true)))));

		List<ToolCall> calls = aggregated.getResult().getOutput().getToolCalls();
		assertThat(calls).hasSize(1);
		assertThat(calls.get(0).arguments()).isEqualTo("{\"a\":1}");
		assertThat(calls.get(0).partial()).isFalse();
	}

	@Test
	void laterPartialsCanCarryOnlyArgumentDeltasWithStableId() {
		// Contract: providers must repeat the tool-call id on every partial frame so
		// the aggregator can correlate fragments. Type/name may be omitted after the
		// first frame.
		ChatResponse aggregated = aggregate(
				Flux.just(responseWithToolCalls(List.of(new ToolCall("call_1", "function", "writeCode", "", true))),
						responseWithToolCalls(List.of(new ToolCall("call_1", null, null, "{\"a\":", true))),
						responseWithToolCalls(List.of(new ToolCall("call_1", null, null, "1}", true)))));

		List<ToolCall> calls = aggregated.getResult().getOutput().getToolCalls();
		assertThat(calls).hasSize(1);
		assertThat(calls.get(0).id()).isEqualTo("call_1");
		assertThat(calls.get(0).type()).isEqualTo("function");
		assertThat(calls.get(0).name()).isEqualTo("writeCode");
		assertThat(calls.get(0).arguments()).isEqualTo("{\"a\":1}");
	}

	@Test
	void aggregatesMultipleConcurrentToolCallsByIdInOrder() {
		ChatResponse aggregated = aggregate(Flux.just(
				responseWithToolCalls(List.of(new ToolCall("call_1", "function", "a", "{\"x\":", true),
						new ToolCall("call_2", "function", "b", "{\"y\":", true))),
				responseWithToolCalls(List.of(new ToolCall("call_1", "function", "a", "1}", true),
						new ToolCall("call_2", "function", "b", "2}", true))),
				responseWithToolCalls(List.of(new ToolCall("call_1", "function", "a", "{\"x\":1}", false),
						new ToolCall("call_2", "function", "b", "{\"y\":2}", false)))));

		List<ToolCall> calls = aggregated.getResult().getOutput().getToolCalls();
		assertThat(calls).hasSize(2);
		assertThat(calls.get(0).id()).isEqualTo("call_1");
		assertThat(calls.get(0).arguments()).isEqualTo("{\"x\":1}");
		assertThat(calls.get(1).id()).isEqualTo("call_2");
		assertThat(calls.get(1).arguments()).isEqualTo("{\"y\":2}");
	}

}
