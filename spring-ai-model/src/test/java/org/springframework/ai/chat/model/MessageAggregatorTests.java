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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.chat.messages.part.MessagePart;
import org.springframework.ai.chat.messages.part.OpaquePayload;
import org.springframework.ai.chat.messages.part.ReasoningPart;
import org.springframework.ai.chat.messages.part.StreamingParts;
import org.springframework.ai.chat.messages.part.TextPart;
import org.springframework.ai.chat.messages.part.ToolCallPart;
import org.springframework.ai.chat.messages.part.UnknownPart;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.EmptyRateLimit;
import org.springframework.ai.chat.metadata.RateLimit;
import org.springframework.ai.content.Media;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MessageAggregator}.
 *
 * @author Soby Chacko
 * @author Christian Tzolov
 */
class MessageAggregatorTests {

	@Test
	void rateLimitFromStreamedChunkSurvivesAggregation() {
		RateLimit rateLimit = new TestRateLimit();

		Flux<ChatResponse> responses = Flux.just(chunk("Hello", new EmptyRateLimit()), chunk(" world", rateLimit));

		AtomicReference<ChatResponse> aggregated = new AtomicReference<>();
		new MessageAggregator().aggregate(responses, aggregated::set).blockLast();

		assertThat(aggregated.get().getMetadata().getRateLimit()).isSameAs(rateLimit);
	}

	@Test
	void rateLimitStaysEmptyWhenNoChunkCarriesOne() {
		Flux<ChatResponse> responses = Flux.just(chunk("Hello", new EmptyRateLimit()),
				chunk(" world", new EmptyRateLimit()));

		AtomicReference<ChatResponse> aggregated = new AtomicReference<>();
		new MessageAggregator().aggregate(responses, aggregated::set).blockLast();

		assertThat(aggregated.get().getMetadata().getRateLimit()).isInstanceOf(EmptyRateLimit.class);
	}

	@Test
	void reasoningAndUnknownPartsAreCarriedThroughAggregation() {
		ReasoningPart reasoning = new ReasoningPart("think", null, new OpaquePayload("anthropic", "signature", "sig"),
				Map.of());
		UnknownPart unknown = new UnknownPart("anthropic", "server_tool_use", "{\"type\":\"server_tool_use\"}", null,
				Map.of());
		ToolCall toolCall = new ToolCall("toolu_1", "function", "getWeather", "{}");
		ChatResponse finalChunk = new ChatResponse(List.of(new Generation(AssistantMessage.builder()
			.part(reasoning)
			.part(unknown)
			.content("")
			.toolCalls(List.of(toolCall))
			.build())));

		Flux<ChatResponse> responses = Flux.just(chunk("Hello", new EmptyRateLimit()),
				chunk(" world", new EmptyRateLimit()), finalChunk);

		AtomicReference<ChatResponse> aggregated = new AtomicReference<>();
		new MessageAggregator().aggregate(responses, aggregated::set).blockLast();

		AssistantMessage output = aggregated.get().getResult().getOutput();
		assertThat(output.getText()).isEqualTo("Hello world");
		assertThat(output.getToolCalls()).containsExactly(toolCall);
		assertThat(output.getReasoning()).containsExactly(reasoning);
		assertThat(output.getParts()).containsExactly(reasoning, unknown, TextPart.of("Hello world"),
				ToolCallPart.of(toolCall));
	}

	@Test
	void indexedDeltasMergeByIndexInIndexOrder() {
		OpaquePayload signature = new OpaquePayload("anthropic", "signature", "sig");
		ToolCall toolCall = new ToolCall("toolu_1", "function", "getWeather", "{\"city\":\"Paris\"}");
		Flux<ChatResponse> responses = Flux.just(indexed("msg_1", StreamingParts.partial(ReasoningPart.of("think"), 0)),
				indexed("msg_1", StreamingParts.partial(ReasoningPart.of("ing"), 0)),
				indexed("msg_1", StreamingParts.partial(new ReasoningPart("", null, signature, Map.of()), 0)),
				indexed("msg_1", StreamingParts.partial(TextPart.of("Let me "), 1)),
				indexed("msg_1", StreamingParts.partial(TextPart.of("check."), 1)),
				indexed("msg_1", StreamingParts.partial(TextPart.of(" Done."), 3)),
				indexed("msg_1", StreamingParts.complete(ToolCallPart.of(toolCall), 2)));

		AssistantMessage output = aggregate(responses);

		assertThat(output.getParts()).containsExactly(new ReasoningPart("thinking", null, signature, Map.of()),
				TextPart.of("Let me check."), ToolCallPart.of(toolCall), TextPart.of(" Done."));
		assertThat(output.getText()).isEqualTo("Let me check. Done.");
		assertThat(output.getToolCalls()).containsExactly(toolCall);
		assertThat(output.getReasoning()).hasSize(1);
	}

	@Test
	void toolCallDeltasKeepTheIdAndNameSeenOnAnEarlierChunk() {
		// Matches how OpenAI-style APIs actually stream tool calls: id, type and name
		// arrive on the first delta for an index, later deltas carry only an arguments
		// fragment.
		ToolCall started = new ToolCall("call_1", "function", "getWeather", "{\"city\":\"Pa");
		ToolCall continued = new ToolCall(null, null, null, "ris\"}");
		Flux<ChatResponse> responses = Flux.just(
				indexed("msg_1", StreamingParts.partial(new ToolCallPart(started, null, Map.of()), 0)),
				indexed("msg_1", StreamingParts.partial(new ToolCallPart(continued, null, Map.of()), 0)));

		AssistantMessage output = aggregate(responses);

		assertThat(output.getToolCalls())
			.containsExactly(new ToolCall("call_1", "function", "getWeather", "{\"city\":\"Paris\"}"));
	}

	@Test
	void streamingAttributesAreStrippedFromAggregatedParts() {
		Flux<ChatResponse> responses = Flux.just(indexed("msg_1", StreamingParts.partial(TextPart.of("a"), 0)),
				indexed("msg_1", StreamingParts.partial(TextPart.of("b"), 0)));

		AssistantMessage output = aggregate(responses);

		assertThat(output.getParts()).containsExactly(TextPart.of("ab"));
		assertThat(output.getParts().get(0).attributes()).isEmpty();
	}

	@Test
	void completePartReplacesAccumulatedDeltas() {
		Flux<ChatResponse> responses = Flux.just(indexed("msg_1", StreamingParts.partial(ReasoningPart.of("par"), 0)),
				indexed("msg_1", StreamingParts.partial(ReasoningPart.of("tial"), 0)),
				indexed("msg_1", StreamingParts.complete(ReasoningPart.of("final"), 0)));

		AssistantMessage output = aggregate(responses);

		assertThat(output.getReasoning()).containsExactly(ReasoningPart.of("final"));
	}

	@Test
	void redactedFlagAndNullTextMergeSafely() {
		OpaquePayload redacted = new OpaquePayload("anthropic", "redacted_thinking", "blob");
		Flux<ChatResponse> responses = Flux.just(
				indexed("msg_1", StreamingParts.complete(new ReasoningPart(null, null, redacted, Map.of()), 0)),
				indexed("msg_1", StreamingParts.partial(TextPart.of("ok"), 1)));

		AssistantMessage output = aggregate(responses);

		assertThat(output.getParts()).containsExactly(new ReasoningPart(null, null, redacted, Map.of()),
				TextPart.of("ok"));
	}

	@Test
	void differentResponseIdsStayInSeparateGroupsInFirstSeenOrder() {
		OpaquePayload sig1 = new OpaquePayload("anthropic", "signature", "sig-1");
		OpaquePayload sig2 = new OpaquePayload("anthropic", "signature", "sig-2");
		Flux<ChatResponse> responses = Flux.just(
				indexed("msg_1", StreamingParts.partial(ReasoningPart.of("round one"), 0)),
				indexed("msg_1", StreamingParts.partial(new ReasoningPart("", null, sig1, Map.of()), 0)),
				indexed("msg_1", StreamingParts.partial(TextPart.of("Checking. "), 1)),
				indexed("msg_2", StreamingParts.partial(ReasoningPart.of("round two"), 0)),
				indexed("msg_2", StreamingParts.partial(new ReasoningPart("", null, sig2, Map.of()), 0)),
				indexed("msg_2", StreamingParts.partial(TextPart.of("Sunny."), 1)));

		AssistantMessage output = aggregate(responses);

		assertThat(output.getParts()).containsExactly(new ReasoningPart("round one", null, sig1, Map.of()),
				TextPart.of("Checking. "), new ReasoningPart("round two", null, sig2, Map.of()), TextPart.of("Sunny."));
		assertThat(output.getText()).isEqualTo("Checking. Sunny.");
	}

	@Test
	void legacyUnindexedChunksStillConcatenateTextAndAppendToolCalls() {
		ToolCall toolCall = new ToolCall("toolu_1", "function", "getWeather", "{}");
		ChatResponse finalChunk = new ChatResponse(
				List.of(new Generation(AssistantMessage.builder().content("").toolCalls(List.of(toolCall)).build())));
		Flux<ChatResponse> responses = Flux.just(chunk("Hello", new EmptyRateLimit()),
				chunk(" world", new EmptyRateLimit()), finalChunk);

		AssistantMessage output = aggregate(responses);

		assertThat(output.getText()).isEqualTo("Hello world");
		assertThat(output.getParts()).containsExactly(TextPart.of("Hello world"), ToolCallPart.of(toolCall));
	}

	@Test
	void legacyMediaOnChunksIsNotCarried() {
		Media fragment = Media.builder()
			.mimeType(MimeTypeUtils.parseMimeType("audio/mp3"))
			.data(new byte[] { 1 })
			.build();
		Flux<ChatResponse> responses = Flux.just(
				new ChatResponse(List
					.of(new Generation(AssistantMessage.builder().content("a").media(List.of(fragment)).build()))),
				new ChatResponse(List
					.of(new Generation(AssistantMessage.builder().content("b").media(List.of(fragment)).build()))));

		AssistantMessage output = aggregate(responses);

		assertThat(output.getText()).isEqualTo("ab");
		assertThat(output.getMedia()).isEmpty();
	}

	@Test
	void aggregatingTheSameFluxInTwoLayersGivesEqualMessages() {
		Flux<ChatResponse> responses = Flux.just(indexed("msg_1", StreamingParts.partial(ReasoningPart.of("t"), 0)),
				indexed("msg_1", StreamingParts.partial(TextPart.of("a"), 1)),
				indexed("msg_1", StreamingParts.partial(TextPart.of("b"), 1)));

		AtomicReference<ChatResponse> inner = new AtomicReference<>();
		AtomicReference<ChatResponse> outer = new AtomicReference<>();
		Flux<ChatResponse> innerFlux = new MessageAggregator().aggregate(responses, inner::set);
		new MessageAggregator().aggregate(innerFlux, outer::set).blockLast();

		assertThat(outer.get().getResult().getOutput()).isEqualTo(inner.get().getResult().getOutput());
		assertThat(outer.get().getResult().getOutput().getParts()).containsExactly(ReasoningPart.of("t"),
				TextPart.of("ab"));
	}

	@Test
	void legacyThoughtMetadataIsStillSynthesizedForUnindexedChunks() {
		ChatResponse thought = new ChatResponse(List.of(new Generation(
				AssistantMessage.builder().content("hidden").properties(Map.of("isThought", true)).build())));
		ChatResponse answer = new ChatResponse(List.of(new Generation(
				AssistantMessage.builder().content("shown").properties(Map.of("isThought", false)).build())));

		AssistantMessage output = aggregate(Flux.just(thought, answer));

		assertThat(output.getText()).isEqualTo("hiddenshown");
		assertThat(output.getMetadata()).containsEntry("thoughts", "hidden")
			.containsEntry("outputWithoutThoughts", "shown");
	}

	@Test
	void responsesWithoutIdsStartANewGroupWhenTheIndexRestarts() {
		OpaquePayload sig1 = new OpaquePayload("google", "thought_signature", "s1");
		OpaquePayload sig2 = new OpaquePayload("google", "thought_signature", "s2");
		ToolCall toolCall = new ToolCall("", "function", "getWeather", "{}");
		Flux<ChatResponse> responses = Flux.just(indexed("", StreamingParts.partial(ReasoningPart.of("round one"), 0)),
				indexed("", StreamingParts.partial(TextPart.of("Checking. "), 1)),
				indexed("", StreamingParts.complete(new ToolCallPart(toolCall, sig1, Map.of()), 2)),
				indexed("", StreamingParts.partial(ReasoningPart.of("round two"), 0)),
				indexed("", StreamingParts.partial(new ReasoningPart("", null, sig2, Map.of()), 0)),
				indexed("", StreamingParts.partial(TextPart.of("Sunny."), 1)));

		AssistantMessage output = aggregate(responses);

		assertThat(output.getParts()).containsExactly(ReasoningPart.of("round one"), TextPart.of("Checking. "),
				new ToolCallPart(toolCall, sig1, Map.of()), new ReasoningPart("round two", null, sig2, Map.of()),
				TextPart.of("Sunny."));
	}

	@Test
	void responsesWithoutIdsStartANewGroupWhenACompleteSlotIsHitAgain() {
		Flux<ChatResponse> responses = Flux.just(indexed("", StreamingParts.complete(ReasoningPart.of("one"), 0)),
				indexed("", StreamingParts.complete(ReasoningPart.of("two"), 0)));

		AssistantMessage output = aggregate(responses);

		assertThat(output.getReasoning()).containsExactly(ReasoningPart.of("one"), ReasoningPart.of("two"));
	}

	@Test
	void responsesWithoutIdsFinalizeAnOpenSlotInPlaceWhenACompletePartArrives() {
		// A complete part must replace what an open (not yet complete) slot at the same
		// index accumulated, not start a new group alongside it.
		Flux<ChatResponse> responses = Flux.just(indexed("", StreamingParts.partial(ReasoningPart.of("par"), 0)),
				indexed("", StreamingParts.complete(ReasoningPart.of("final"), 0)));

		AssistantMessage output = aggregate(responses);

		assertThat(output.getParts()).containsExactly(ReasoningPart.of("final"));
	}

	@Test
	void manyDeltasAggregateToTheFullText() {
		List<ChatResponse> chunks = new ArrayList<>();
		StringBuilder expected = new StringBuilder();
		for (int i = 0; i < 2000; i++) {
			chunks.add(indexed("msg_1", StreamingParts.partial(TextPart.of("w" + i + " "), 0)));
			expected.append("w").append(i).append(' ');
		}

		AssistantMessage output = aggregate(Flux.fromIterable(chunks));

		assertThat(output.getParts()).containsExactly(TextPart.of(expected.toString()));
	}

	@Test
	void defaultUsageRecordIsStillAvailable() {
		MessageAggregator.DefaultUsage usage = new MessageAggregator.DefaultUsage(1, 2, 3);

		assertThat(usage.getPromptTokens()).isEqualTo(1);
		assertThat(usage.getCompletionTokens()).isEqualTo(2);
		assertThat(usage.getTotalTokens()).isEqualTo(3);
		assertThat(usage.getNativeUsage()).containsEntry("totalTokens", 3);
	}

	private static AssistantMessage aggregate(Flux<ChatResponse> responses) {
		AtomicReference<ChatResponse> aggregated = new AtomicReference<>();
		new MessageAggregator().aggregate(responses, aggregated::set).blockLast();
		return aggregated.get().getResult().getOutput();
	}

	private static ChatResponse indexed(String responseId, MessagePart part) {
		ChatResponseMetadata metadata = ChatResponseMetadata.builder().id(responseId).build();
		return new ChatResponse(List.of(new Generation(AssistantMessage.builder().part(part).build())), metadata);
	}

	private static ChatResponse chunk(String text, RateLimit rateLimit) {
		ChatResponseMetadata metadata = ChatResponseMetadata.builder().rateLimit(rateLimit).build();
		return new ChatResponse(List.of(new Generation(new AssistantMessage(text))), metadata);
	}

	private static final class TestRateLimit implements RateLimit {

		@Override
		public Long getRequestsLimit() {
			return 100L;
		}

		@Override
		public Long getRequestsRemaining() {
			return 99L;
		}

		@Override
		public Duration getRequestsReset() {
			return Duration.ofSeconds(1);
		}

		@Override
		public Long getTokensLimit() {
			return 1000L;
		}

		@Override
		public Long getTokensRemaining() {
			return 999L;
		}

		@Override
		public Duration getTokensReset() {
			return Duration.ofSeconds(2);
		}

	}

}
