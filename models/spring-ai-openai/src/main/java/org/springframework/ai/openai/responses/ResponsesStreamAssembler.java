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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseStreamEvent;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.part.MessagePart;
import org.springframework.ai.chat.messages.part.ReasoningPart;
import org.springframework.ai.chat.messages.part.StreamingParts;
import org.springframework.ai.chat.messages.part.TextPart;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.MessageAggregator;

/**
 * Turns the Responses API's typed stream events into {@link ChatResponse} chunks.
 * <p>
 * A pure function over the event sequence, deliberately free of Reactor so that the whole
 * emission table is unit-testable. Stateful across a single subscription, and not
 * thread-safe: one instance per stream.
 * <p>
 * The emission rules follow the streaming contract of {@link StreamingParts}, which
 * {@link MessageAggregator} implements. The Responses {@code output_index} is the content
 * block index, so every part is stamped with it and the aggregator rebuilds the ordered
 * transcript per index:
 * <ul>
 * <li>Text, reasoning text and reasoning summary deltas are stamped
 * {@link StreamingParts#partial partial}, so the aggregator appends them.
 * <li>When an item completes, the part it maps to is emitted for its index. A part whose
 * content already arrived as deltas is emitted <em>partial and emptied</em>: it
 * contributes the encrypted reasoning payload and the item attributes to the aggregated
 * part without repeating the text a consumer of the raw flux has already seen. A part
 * nothing streamed for - a function call, a hosted tool item, generated media - is
 * emitted {@link StreamingParts#complete complete}.
 * <li>Every chunk carries the response id on its {@link ChatResponseMetadata}. The
 * aggregator groups indexed parts by it, which is what keeps the parts of consecutive
 * tool-calling rounds flowing through one flux from merging into each other.
 * <li>Refusals have no part type, so they are emitted as the <em>running total</em> under
 * a {@link ChatGenerationMetadata generation metadata} key. The flattened reasoning
 * summary is published the same way in addition to its part, for parity with
 * {@code OpenAiChatModel} and DeepSeek. The aggregator keeps the last generation metadata
 * it is given rather than merging key by key, and the terminal chunk repeats both values,
 * so a consumer of either the raw flux or the aggregated response ends up with the
 * complete text.
 * <li>The finish reason, usage and response metadata go on the terminal chunk, which
 * carries no parts: they were all emitted as their items completed.
 * <li>No chunk puts anything on its assistant message's metadata. Message metadata is
 * persisted by some chat memory repositories and dropped by others, so this model keeps
 * everything a turn reports on the generation and response metadata instead.
 * </ul>
 * Unlike the Chat Completions path there is no tool-call argument buffering, because the
 * SDK delivers whole items.
 *
 * @author Dimitar Proynov
 * @since 2.1.0
 */
final class ResponsesStreamAssembler {

	private final List<ResponseOutputItem> outputItems = new ArrayList<>();

	private final StringBuilder reasoning = new StringBuilder();

	private final StringBuilder refusal = new StringBuilder();

	/**
	 * The output indices that received text, reasoning text and reasoning summary deltas.
	 * A completing item only re-emits the content that never streamed, so that nothing is
	 * either lost or shown twice.
	 */
	private final Set<Integer> streamedText = new HashSet<>();

	private final Set<Integer> streamedReasoningText = new HashSet<>();

	private final Set<Integer> streamedReasoningSummary = new HashSet<>();

	private @Nullable String responseId;

	/**
	 * The output index whose reasoning summary is currently being accumulated, or -1 before
	 * the first summary delta, so that the summaries of two different items end up
	 * separated the same way the non-streaming path separates them.
	 */
	private int reasoningIndex = -1;

	/**
	 * Map one event to zero or more chunks.
	 * @throws OpenAiResponsesException if the stream reports an error, or the run failed
	 */
	List<ChatResponse> apply(ResponseStreamEvent event) {
		if (event.isCreated()) {
			// Captured here and put on every chunk: the aggregator groups by it, and a
			// consumer that cancels mid-stream should still be able to learn the id of
			// the response OpenAI produced.
			this.responseId = event.created().orElseThrow().response().id();
			return List.of();
		}
		if (event.isOutputTextDelta()) {
			var delta = event.outputTextDelta().orElseThrow();
			int index = index(delta.outputIndex());
			this.streamedText.add(index);
			return List.of(chunk(StreamingParts.partial(TextPart.of(delta.delta()), index)));
		}
		if (event.isReasoningTextDelta()) {
			var delta = event.reasoningTextDelta().orElseThrow();
			int index = index(delta.outputIndex());
			this.streamedReasoningText.add(index);
			return List
				.of(chunk(StreamingParts.partial(new ReasoningPart(delta.delta(), null, null, Map.of()), index)));
		}
		if (event.isReasoningSummaryTextDelta()) {
			var delta = event.reasoningSummaryTextDelta().orElseThrow();
			int index = index(delta.outputIndex());
			this.streamedReasoningSummary.add(index);
			appendReasoning(index, delta.delta());
			MessagePart part = StreamingParts.partial(new ReasoningPart(null, delta.delta(), null, Map.of()), index);
			return List.of(chunk(AssistantMessage.builder().part(part).build(), ChatGenerationMetadata.builder()
				.metadata(OpenAiResponsesMetadata.REASONING_CONTENT, this.reasoning.toString())
				.build()));
		}
		if (event.isRefusalDelta()) {
			this.refusal.append(event.refusalDelta().orElseThrow().delta());
			// No refusal part type, so the running total on the generation metadata is the
			// only channel. The aggregator keeps the last generation metadata it is given,
			// and the terminal chunk repeats this value, so the total survives either way.
			return List.of(chunk(AssistantMessage.builder().content("").build(), ChatGenerationMetadata.builder()
				.metadata(OpenAiResponsesMetadata.REFUSAL, this.refusal.toString())
				.build()));
		}
		if (event.isOutputItemDone()) {
			var done = event.outputItemDone().orElseThrow();
			this.outputItems.add(done.item());
			return List.of(chunk(indexedPart(done.item(), index(done.outputIndex()))));
		}
		if (event.isCompleted()) {
			return List.of(terminalChunk(event.completed().orElseThrow().response()));
		}
		if (event.isIncomplete()) {
			return List.of(terminalChunk(event.incomplete().orElseThrow().response()));
		}
		if (event.isFailed()) {
			throw ResponsesItemMapper.failure(event.failed().orElseThrow().response());
		}
		if (event.isError()) {
			var error = event.error().orElseThrow();
			throw new OpenAiResponsesException(error.code().orElse(null), error.message());
		}
		// Everything else - lifecycle and hosted-tool progress events - is already
		// reflected by the item and terminal events, so there is nothing to emit.
		return List.of();
	}

	/**
	 * The part of an output item, stamped for the aggregator: emptied of whatever already
	 * streamed as deltas for this index, so the aggregated part ends up with the streamed
	 * text plus the item's payload and attributes, and the raw flux never repeats text it
	 * already emitted.
	 */
	private MessagePart indexedPart(ResponseOutputItem item, int index) {
		MessagePart part = ResponsesItemMapper.toPart(item);
		if (part instanceof TextPart textPart) {
			String text = this.streamedText.contains(index) ? "" : textPart.text();
			return StreamingParts.partial(new TextPart(text, textPart.payload(), textPart.attributes()), index);
		}
		if (part instanceof ReasoningPart reasoningPart) {
			String text = this.streamedReasoningText.contains(index) ? null : reasoningPart.text();
			String summary = this.streamedReasoningSummary.contains(index) ? null : reasoningPart.summary();
			return StreamingParts
				.partial(new ReasoningPart(text, summary, reasoningPart.payload(), reasoningPart.attributes()), index);
		}
		return StreamingParts.complete(part, index);
	}

	/**
	 * Accumulate the flattened reasoning summary, separating the summaries of two
	 * different items exactly as the non-streaming path does, so the same response reports
	 * the same {@link OpenAiResponsesMetadata#REASONING_CONTENT} either way.
	 */
	private void appendReasoning(int index, String delta) {
		if (this.reasoningIndex >= 0 && this.reasoningIndex != index) {
			this.reasoning.append(ResponsesItemMapper.REASONING_ITEM_SEPARATOR);
		}
		this.reasoningIndex = index;
		this.reasoning.append(delta);
	}

	/**
	 * The terminal chunk carries the finish reason, the usage and the response metadata,
	 * and no parts: every part was emitted as its item completed. Values are derived from
	 * the items collected off the stream, falling back to the terminal response only when
	 * no item event arrived, so the finish reason cannot disagree with the transcript.
	 * <p>
	 * Only the metadata is derived here. The parts were built once already, as their items
	 * completed, and rebuilding them would decode a generated image and re-serialize every
	 * hosted-tool item a second time.
	 */
	private ChatResponse terminalChunk(Response response) {
		boolean noItemEvents = this.outputItems.isEmpty();
		List<ResponseOutputItem> items = noItemEvents ? response.output() : this.outputItems;

		// What arrived over the stream is the complete value for these two: the terminal
		// response object does not always repeat them, and deriving an empty value from
		// the items would silently erase what the stream delivered.
		ChatGenerationMetadata generationMetadata = ResponsesItemMapper.toGenerationMetadata(response, items,
				this.reasoning.isEmpty() ? null : this.reasoning.toString(),
				this.refusal.isEmpty() ? null : this.refusal.toString());

		// An empty text rather than no part at all, so that a consumer of the raw flux
		// never meets a null text on a chunk.
		AssistantMessage.Builder<?> terminal = AssistantMessage.builder().content("");
		if (noItemEvents) {
			// The stream delivered no output_item.done events, so no part was ever
			// emitted. Carry the whole transcript here instead, one part per index.
			terminal.parts(indexedParts(items));
		}

		return new ChatResponse(List.of(new Generation(terminal.build(), generationMetadata)),
				ResponsesItemMapper.toResponseMetadata(response, ResponsesItemMapper.toUsage(response)));
	}

	/**
	 * The whole transcript as indexed parts. A block whose content already arrived as
	 * deltas is emptied rather than skipped: the aggregator still needs the item's payload
	 * and attributes for that index, and the raw flux must not show its text twice.
	 */
	private List<MessagePart> indexedParts(List<ResponseOutputItem> items) {
		List<MessagePart> parts = new ArrayList<>();
		for (int index = 0; index < items.size(); index++) {
			parts.add(indexedPart(items.get(index), index));
		}
		return parts;
	}

	private ChatResponse chunk(MessagePart part) {
		return chunk(AssistantMessage.builder().part(part).build(), ChatGenerationMetadata.NULL);
	}

	/**
	 * A chunk of one streamed response. The assistant message carries no metadata: what a
	 * turn reports beyond its parts goes on the generation metadata, which is the only one
	 * of the two that {@code MessageAggregator} carries through verbatim rather than
	 * merging key by key.
	 */
	private ChatResponse chunk(AssistantMessage message, ChatGenerationMetadata generationMetadata) {
		return new ChatResponse(List.of(new Generation(message, generationMetadata)), chunkMetadata());
	}

	/**
	 * The response metadata every chunk carries. The id is what the aggregator groups
	 * indexed parts by, so it has to be on every chunk of one response and not only on
	 * the terminal one.
	 */
	private ChatResponseMetadata chunkMetadata() {
		ChatResponseMetadata.Builder metadata = ChatResponseMetadata.builder();
		if (this.responseId != null) {
			metadata.id(this.responseId);
		}
		return metadata.build();
	}

	private static int index(long outputIndex) {
		return Math.toIntExact(outputIndex);
	}

}
