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

package org.springframework.ai.openai.responses;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCompletedEvent;
import com.openai.models.responses.ResponseCreatedEvent;
import com.openai.models.responses.ResponseErrorEvent;
import com.openai.models.responses.ResponseFailedEvent;
import com.openai.models.responses.ResponseIncompleteEvent;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseOutputItemDoneEvent;
import com.openai.models.responses.ResponseReasoningSummaryTextDeltaEvent;
import com.openai.models.responses.ResponseRefusalDeltaEvent;
import com.openai.models.responses.ResponseStreamEvent;
import com.openai.models.responses.ResponseTextDeltaEvent;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.part.ReasoningPart;
import org.springframework.ai.chat.messages.part.StreamingParts;
import org.springframework.ai.chat.messages.part.TextPart;
import org.springframework.ai.chat.messages.part.ToolCallPart;
import org.springframework.ai.chat.messages.part.UnknownPart;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.MessageAggregator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The stream assembler is a pure function over the event sequence, so the whole emission
 * table is testable without Reactor. The interplay with {@link MessageAggregator} is
 * asserted separately, because that is what every consumer above actually observes.
 *
 * @author Dimitar Proynov
 */
class ResponsesStreamAssemblerTests {

	private final ResponsesStreamAssembler assembler = new ResponsesStreamAssembler();

	@Test
	void responseCreatedEmitsNothingButItsIdLandsOnEveryLaterChunk() {
		assertThat(this.assembler.apply(created("text-with-reasoning-summary.json"))).isEmpty();

		List<ChatResponse> chunks = this.assembler.apply(textDelta("Hello", 0));

		assertThat(chunks).singleElement().satisfies(chunk -> {
			assertThat(chunk.getResult().getOutput().getText()).isEqualTo("Hello");
			// The aggregator groups indexed parts by this id, so it has to be on every
			// chunk and not only on the terminal one.
			assertThat(chunk.getMetadata().getId()).isEqualTo("resp_text_1");
		});
	}

	/**
	 * Nothing this model reports goes on a chunk's assistant message: message metadata is
	 * persisted by some chat memory repositories and dropped by others, so a turn's
	 * reported values live on the generation and response metadata instead.
	 */
	@Test
	void noChunkPutsAnythingOnItsAssistantMessagesMetadata() {
		Response response = ResponsesTestFixtures.response("text-with-reasoning-summary.json");
		List<ResponseStreamEvent> events = List.of(created("text-with-reasoning-summary.json"),
				reasoningDelta("Report it.", 0), itemDone(response.output().get(0), 0), textDelta("It is 18 ", 1),
				refusalDelta("no"), itemDone(response.output().get(1), 1), completed(response));

		List<ChatResponse> chunks = events.stream().flatMap(event -> this.assembler.apply(event).stream()).toList();

		assertThat(chunks).isNotEmpty()
			.allSatisfy(chunk -> assertThat(chunk.getResult().getOutput().getMetadata())
				.containsOnlyKeys(AbstractMessage.MESSAGE_TYPE));
	}

	@Test
	void textDeltasAreStampedPartialWithTheirOutputIndex() {
		this.assembler.apply(created("text-with-reasoning-summary.json"));

		List<ChatResponse> chunks = this.assembler.apply(textDelta("Hello", 1));

		assertThat(chunks).singleElement().satisfies(chunk -> {
			var part = chunk.getResult().getOutput().getParts().get(0);
			assertThat(part).isInstanceOf(TextPart.class);
			assertThat(StreamingParts.partIndex(part)).isEqualTo(1);
			assertThat(StreamingParts.isPartial(part)).isTrue();
		});
	}

	@Test
	void reasoningSummaryDeltasBecomePartialReasoningPartsAndTheRunningTotalInMetadata() {
		this.assembler.apply(created("text-with-reasoning-summary.json"));

		this.assembler.apply(reasoningDelta("Think", 0));
		List<ChatResponse> second = this.assembler.apply(reasoningDelta("ing hard", 0));

		assertThat(second).singleElement().satisfies(chunk -> {
			var part = chunk.getResult().getOutput().getParts().get(0);
			assertThat(((ReasoningPart) part).summary()).isEqualTo("ing hard");
			assertThat(StreamingParts.isPartial(part)).isTrue();
			// Also published flat, for parity with OpenAiChatModel and DeepSeek. The
			// running total is what makes the complete summary survive aggregation, which
			// keeps only the last generation metadata it sees.
			assertThat(reasoningContent(chunk)).isEqualTo("Thinking hard");
		});
	}

	/**
	 * The deltas of one item concatenate, and two different items are separated by the
	 * same separator the non-streaming path uses, so the same response reports the same
	 * flattened summary whether it was called or streamed.
	 */
	@Test
	void reasoningSummaryDeltasConcatenateWithinAnItemAndSeparateBetweenItems() {
		Response response = ResponsesTestFixtures.response("reasoning-with-function-call.json");
		this.assembler.apply(created("reasoning-with-function-call.json"));

		this.assembler.apply(reasoningDelta("First ", 0));
		this.assembler.apply(reasoningDelta("thought", 0));
		this.assembler.apply(reasoningDelta("Second thought", 2));
		List<ChatResponse> chunks = this.assembler.apply(completed(response));

		assertThat(chunks).singleElement()
			.satisfies(chunk -> assertThat(reasoningContent(chunk)).isEqualTo("First thought\nSecond thought"));
	}

	/**
	 * The completing item is what carries the encrypted reasoning blob and the item id,
	 * neither of which any delta event contains.
	 */
	@Test
	void aCompletedReasoningItemContributesItsEncryptedPayloadAndItemId() {
		this.assembler.apply(created("reasoning-with-function-call.json"));
		Response response = ResponsesTestFixtures.response("reasoning-with-function-call.json");

		List<ChatResponse> chunks = this.assembler.apply(itemDone(response.output().get(0), 0));

		assertThat(chunks).singleElement().satisfies(chunk -> {
			ReasoningPart part = (ReasoningPart) chunk.getResult().getOutput().getParts().get(0);
			assertThat(part.payload()).isNotNull();
			assertThat(part.payload().data()).isEqualTo("gAAAAABopaque-encrypted-reasoning-blob");
			assertThat(part.attributes()).containsEntry(OpenAiResponsesMetadata.ITEM_ID_ATTRIBUTE, "rs_1");
		});
	}

	@Test
	void completedFunctionCallItemsBecomeCompleteToolCallParts() {
		this.assembler.apply(created("reasoning-with-function-call.json"));
		Response response = ResponsesTestFixtures.response("reasoning-with-function-call.json");

		List<ChatResponse> chunks = this.assembler.apply(itemDone(response.output().get(1), 1));

		assertThat(chunks).singleElement().satisfies(chunk -> {
			AssistantMessage message = chunk.getResult().getOutput();
			assertThat(message.getText()).isEmpty();
			var part = message.getParts().get(0);
			assertThat(StreamingParts.isPartial(part)).isFalse();
			assertThat(((ToolCallPart) part).toolCall().id()).isEqualTo("call_abc123");
			assertThat(message.getToolCalls()).singleElement()
				.satisfies(toolCall -> assertThat(toolCall.name()).isEqualTo("getCurrentWeather"));
		});
	}

	/**
	 * A tool OpenAI ran itself is carried verbatim so it still replays, but it must never
	 * look like a tool call, or {@code ToolCallingAdvisor} would try to execute
	 * {@code web_search} locally.
	 */
	@Test
	void hostedToolItemsBecomeUnknownPartsAndNeverToolCalls() {
		Response response = ResponsesTestFixtures.response("web-search-then-message.json");

		List<ChatResponse> chunks = this.assembler.apply(itemDone(response.output().get(0), 0));

		assertThat(chunks).singleElement().satisfies(chunk -> {
			AssistantMessage message = chunk.getResult().getOutput();
			assertThat(message.hasToolCalls()).isFalse();
			UnknownPart part = (UnknownPart) message.getParts().get(0);
			assertThat(part.provider()).isEqualTo(OpenAiResponsesMetadata.PROVIDER);
			assertThat(part.kind()).isEqualTo("web_search_call");
			assertThat(part.rawJson()).contains("ws_1");
		});
	}

	@Test
	void theTerminalChunkCarriesTheUsageAndFinishReasonButNoTextOrToolCalls() {
		this.assembler.apply(created("reasoning-with-function-call.json"));
		Response response = ResponsesTestFixtures.response("reasoning-with-function-call.json");
		this.assembler.apply(itemDone(response.output().get(0), 0));
		this.assembler.apply(itemDone(response.output().get(1), 1));

		List<ChatResponse> chunks = this.assembler.apply(completed(response));

		assertThat(chunks).singleElement().satisfies(chunk -> {
			AssistantMessage message = chunk.getResult().getOutput();
			// The parts were emitted as their items completed; repeating them here would
			// show the text twice on the raw flux.
			assertThat(message.getText()).isEmpty();
			assertThat(message.getToolCalls()).isEmpty();
			assertThat(chunk.getResult().getMetadata().getFinishReason()).isEqualTo("TOOL_CALLS");
			assertThat(chunk.getMetadata().getId()).isEqualTo("resp_reasoning_tool_1");
			assertThat(chunk.getMetadata().getUsage().getTotalTokens()).isEqualTo(160);
		});
	}

	/**
	 * A stream that delivers no item events at all must not lose the transcript: the
	 * terminal chunk stands in for the item chunks that never arrived.
	 */
	@Test
	void theTerminalChunkCarriesTheTranscriptWhenNoItemEventArrived() {
		Response response = ResponsesTestFixtures.response("reasoning-with-function-call.json");
		this.assembler.apply(created("reasoning-with-function-call.json"));

		List<ChatResponse> chunks = this.assembler.apply(completed(response));

		assertThat(chunks).singleElement().satisfies(chunk -> {
			List<?> parts = chunk.getResult().getOutput().getParts();
			assertThat(parts).hasSize(3);
			assertThat(parts.get(0)).isInstanceOf(ReasoningPart.class);
			assertThat(parts.get(1)).isInstanceOf(ToolCallPart.class);
			// Plus the empty text part every chunk ends with
			assertThat(parts.get(2)).isInstanceOf(TextPart.class);
			assertThat(chunk.getResult().getMetadata().getFinishReason()).isEqualTo("TOOL_CALLS");
		});
	}

	/**
	 * A terminal event that does not repeat the output must not cost the turn its finish
	 * reason: taking the streamed items for one and the empty response for the other is
	 * what used to report STOP on a turn full of tool calls.
	 */
	@Test
	void theTerminalChunkFallsBackToTheStreamedTranscriptConsistently() {
		Response response = ResponsesTestFixtures.response("reasoning-with-function-call.json");
		this.assembler.apply(created("reasoning-with-function-call.json"));
		this.assembler.apply(itemDone(response.output().get(0), 0));
		this.assembler.apply(itemDone(response.output().get(1), 1));

		List<ChatResponse> chunks = this.assembler.apply(completed(response.toBuilder().output(List.of()).build()));

		assertThat(chunks).singleElement().satisfies(chunk -> {
			assertThat(chunk.getResult().getMetadata().getFinishReason()).isEqualTo("TOOL_CALLS");
			assertThat(chunk.getResult().getMetadata().<String>get(OpenAiResponsesMetadata.STATUS))
				.isEqualTo("completed");
		});
	}

	@Test
	void anIncompleteResponseStillTerminatesTheStreamWithAFinishReason() {
		List<ChatResponse> chunks = this.assembler
			.apply(incomplete(ResponsesTestFixtures.response("incomplete-max-output-tokens.json")));

		assertThat(chunks).singleElement()
			.satisfies(chunk -> assertThat(chunk.getResult().getMetadata().getFinishReason()).isEqualTo("LENGTH"));
	}

	@Test
	void aFailedResponseIsRaisedRatherThanSwallowed() {
		assertThatThrownBy(() -> this.assembler.apply(failed(ResponsesTestFixtures.response("failed.json"))))
			.isInstanceOf(OpenAiResponsesException.class)
			.hasMessageContaining("server_error")
			.hasMessageContaining("The model run failed");
	}

	@Test
	void anErrorEventIsRaised() {
		ResponseStreamEvent event = ResponseStreamEvent.ofError(ResponseErrorEvent.builder()
			.code("rate_limit")
			.message("Slow down")
			.param(Optional.empty())
			.sequenceNumber(1)
			.build());

		assertThatThrownBy(() -> this.assembler.apply(event)).isInstanceOf(OpenAiResponsesException.class)
			.hasMessageContaining("Slow down");
	}

	/**
	 * What consumers above the model actually see: one message whose parts are the
	 * transcript, in order, with the encrypted reasoning attached to the reasoning part
	 * and the transport attributes stripped.
	 */
	@Test
	void aggregationYieldsOneMessageWhosePartsAreTheTranscript() {
		Response response = ResponsesTestFixtures.response("reasoning-with-function-call.json");
		List<ResponseStreamEvent> events = List.of(created("reasoning-with-function-call.json"),
				reasoningDelta("Call the weather tool", 0), itemDone(response.output().get(0), 0),
				itemDone(response.output().get(1), 1), completed(response));

		ChatResponse aggregated = aggregate(events);
		AssistantMessage message = aggregated.getResult().getOutput();

		assertThat(reasoningContent(aggregated)).isEqualTo("Call the weather tool");
		assertThat(message.getParts()).satisfiesExactly(first -> {
			ReasoningPart reasoning = (ReasoningPart) first;
			assertThat(reasoning.summary()).isEqualTo("Call the weather tool");
			assertThat(reasoning.payload()).isNotNull();
			assertThat(reasoning.payload().data()).isEqualTo("gAAAAABopaque-encrypted-reasoning-blob");
			assertThat(reasoning.attributes()).containsEntry(OpenAiResponsesMetadata.ITEM_ID_ATTRIBUTE, "rs_1")
				.doesNotContainKey(StreamingParts.PART_INDEX_ATTRIBUTE)
				.doesNotContainKey(StreamingParts.PARTIAL_ATTRIBUTE);
		}, second -> assertThat(((ToolCallPart) second).toolCall().id()).isEqualTo("call_abc123"));
		assertThat(message.getToolCalls()).hasSize(1);
		// The message itself carries nothing but its parts
		assertThat(message.getMetadata()).containsOnlyKeys(AbstractMessage.MESSAGE_TYPE);
	}

	/**
	 * The completing message item repeats the whole text the deltas already delivered, so
	 * it is emitted emptied: the aggregated text must be the text once, with the item's
	 * attributes on it.
	 */
	@Test
	void aggregatedTextIsNotDoubledByTheCompletingMessageItem() {
		Response response = ResponsesTestFixtures.response("text-with-reasoning-summary.json");
		List<ResponseStreamEvent> events = List.of(created("text-with-reasoning-summary.json"),
				reasoningDelta("Report the temperature the tool returned.", 0), itemDone(response.output().get(0), 0),
				textDelta("It is 18 ", 1), textDelta("degrees in Paris.", 1), itemDone(response.output().get(1), 1),
				completed(response));

		AssistantMessage message = aggregate(events).getResult().getOutput();

		assertThat(message.getText()).isEqualTo("It is 18 degrees in Paris.");
		assertThat(message.getParts()).hasSize(2);
		TextPart text = (TextPart) message.getParts().get(1);
		assertThat(text.text()).isEqualTo("It is 18 degrees in Paris.");
		assertThat(text.attributes()).containsEntry(OpenAiResponsesMetadata.ITEM_ID_ATTRIBUTE, "msg_1")
			.containsEntry(OpenAiResponsesMetadata.PHASE_ATTRIBUTE, "final_answer");
	}

	/**
	 * A stream with no text deltas, only the completing item, must still carry the text:
	 * emptying the completing part is conditional on the deltas having arrived.
	 */
	@Test
	void aggregatedTextComesFromTheCompletingItemWhenNoTextDeltaArrived() {
		Response response = ResponsesTestFixtures.response("text-with-reasoning-summary.json");
		List<ResponseStreamEvent> events = List.of(created("text-with-reasoning-summary.json"),
				itemDone(response.output().get(1), 1), completed(response));

		assertThat(aggregate(events).getResult().getOutput().getText()).isEqualTo("It is 18 degrees in Paris.");
	}

	@Test
	void refusalDeltasAreEmittedAsTheRunningTotalSoTheySurviveAggregation() {
		this.assembler.apply(created("text-with-reasoning-summary.json"));

		this.assembler.apply(refusalDelta("I cannot"));
		List<ChatResponse> second = this.assembler.apply(refusalDelta(" help with that."));

		assertThat(second).singleElement()
			.satisfies(chunk -> assertThat(chunk.getResult().getMetadata().<String>get(OpenAiResponsesMetadata.REFUSAL))
				.isEqualTo("I cannot help with that."));
	}

	/**
	 * The terminal response object does not always repeat what was streamed, and it is
	 * the last thing the aggregator sees, so it must not erase the streamed value.
	 */
	@Test
	void theTerminalChunkDoesNotOverwriteStreamedReasoningWithTheResponseObjectsCopy() {
		this.assembler.apply(created("reasoning-with-function-call.json"));
		this.assembler.apply(reasoningDelta("Call the weather tool", 0));

		List<ChatResponse> chunks = this.assembler
			.apply(completed(ResponsesTestFixtures.response("reasoning-with-function-call.json")));

		assertThat(chunks).singleElement()
			.satisfies(chunk -> assertThat(reasoningContent(chunk)).isEqualTo("Call the weather tool"));
	}

	private static @Nullable String reasoningContent(ChatResponse chunk) {
		return chunk.getResult().getMetadata().get(OpenAiResponsesMetadata.REASONING_CONTENT);
	}

	private ChatResponse aggregate(List<ResponseStreamEvent> events) {
		List<ChatResponse> aggregated = new ArrayList<>();
		Flux<ChatResponse> chunks = Flux.fromIterable(events).concatMapIterable(this.assembler::apply);
		new MessageAggregator().aggregate(chunks, aggregated::add).blockLast();
		assertThat(aggregated).hasSize(1);
		return aggregated.get(0);
	}

	private static ResponseStreamEvent refusalDelta(String delta) {
		return ResponseStreamEvent.ofRefusalDelta(ResponseRefusalDeltaEvent.builder()
			.contentIndex(0)
			.delta(delta)
			.itemId("msg_1")
			.outputIndex(0)
			.sequenceNumber(1)
			.build());
	}

	private static ResponseStreamEvent created(String fixture) {
		return ResponseStreamEvent.ofCreated(ResponseCreatedEvent.builder()
			.response(ResponsesTestFixtures.response(fixture))
			.sequenceNumber(0)
			.build());
	}

	private static ResponseStreamEvent textDelta(String delta, int outputIndex) {
		return ResponseStreamEvent.ofOutputTextDelta(ResponseTextDeltaEvent.builder()
			.contentIndex(0)
			.delta(delta)
			.itemId("msg_1")
			.logprobs(List.of())
			.outputIndex(outputIndex)
			.sequenceNumber(1)
			.build());
	}

	private static ResponseStreamEvent reasoningDelta(String delta, int outputIndex) {
		return ResponseStreamEvent.ofReasoningSummaryTextDelta(ResponseReasoningSummaryTextDeltaEvent.builder()
			.delta(delta)
			.itemId("rs_1")
			.outputIndex(outputIndex)
			.sequenceNumber(1)
			.summaryIndex(0)
			.build());
	}

	private static ResponseStreamEvent itemDone(ResponseOutputItem item, int outputIndex) {
		return ResponseStreamEvent.ofOutputItemDone(
				ResponseOutputItemDoneEvent.builder().item(item).outputIndex(outputIndex).sequenceNumber(1).build());
	}

	private static ResponseStreamEvent completed(Response response) {
		return ResponseStreamEvent
			.ofCompleted(ResponseCompletedEvent.builder().response(response).sequenceNumber(9).build());
	}

	private static ResponseStreamEvent incomplete(Response response) {
		return ResponseStreamEvent
			.ofIncomplete(ResponseIncompleteEvent.builder().response(response).sequenceNumber(9).build());
	}

	private static ResponseStreamEvent failed(Response response) {
		return ResponseStreamEvent.ofFailed(ResponseFailedEvent.builder().response(response).sequenceNumber(9).build());
	}

}
