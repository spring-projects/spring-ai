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

package org.springframework.ai.chat.messages.part;

import org.jspecify.annotations.Nullable;

/**
 * Synthesizes the {@link StreamingParts} index for providers that stream message content
 * without a content block index, so that
 * {@link org.springframework.ai.chat.model.MessageAggregator MessageAggregator} can
 * rebuild the complete parts from the chunks.
 * <p>
 * The aggregator merges streamed parts by index: a {@linkplain StreamingParts#partial
 * partial} part is appended to what was received earlier at the same index, and a
 * {@linkplain StreamingParts#complete complete} part replaces it. Providers that stream
 * content blocks, such as Anthropic, send that index with every delta. Others, such as
 * Chat Completions, Gemini or Ollama, send reasoning deltas, text deltas and complete
 * tool calls without an index, possibly several of them in one chunk. This class derives
 * the index from the order in which the parts arrive:
 * <ul>
 * <li>A {@link ReasoningPart} or {@link TextPart} is a delta. It continues the current
 * index when the previous delta was of the same kind, and starts the next index
 * otherwise. So a run of reasoning deltas becomes one {@link ReasoningPart}, and a run of
 * text deltas one {@link TextPart}.</li>
 * <li>Any other part, such as a tool call, is complete when it arrives and gets the next
 * index for itself. It also ends the current run, so a text delta that follows it starts
 * a new text part.</li>
 * <li>An empty {@link TextPart} without a payload, such as the placeholder of a chunk
 * that only carries usage, carries nothing. It is returned unchanged and does not end the
 * current run, so that such a chunk in the middle of the reasoning does not split it in
 * two.</li>
 * </ul>
 * For example, the deltas {@code reasoning("Think ")}, {@code reasoning("more.")},
 * {@code text("Hel")} and {@code text("lo")}, followed by one tool call, are stamped as
 * partial 0, partial 0, partial 1, partial 1 and complete 2. They aggregate to a
 * reasoning part {@code "Think more."}, a text part {@code "Hello"} and the tool call, in
 * that order.
 * <p>
 * A caller that must not index a part, for instance to keep streamed media out of the
 * aggregated message, keeps that part on the chunk unstamped rather than passing it to
 * {@link #stamp(MessagePart)}.
 * <p>
 * The aggregator groups indexed parts by response id, so one instance must serve all the
 * parts that share a response id, in the order they appear in the stream. It is stateful
 * and not thread-safe; the chunks of a stream are processed one at a time.
 *
 * @author Christian Tzolov
 * @author Dimitar Proynov
 * @since 2.1.0
 * @see StreamingParts
 */
public final class StreamingPartIndexer {

	/**
	 * The index of the most recently stamped part, {@code -1} before the first one.
	 */
	private int index = -1;

	/**
	 * The type of the most recently stamped delta, {@link TextPart} or
	 * {@link ReasoningPart}, or {@code null} when the most recent part was complete,
	 * meaning no run is in progress.
	 */
	private @Nullable Class<? extends MessagePart> lastDeltaKind;

	/**
	 * Returns a copy of the part with its stream index set, and advances this indexer.
	 * Call it once per part, in the order the parts appear in the stream.
	 * @param part the part of a streamed chunk, without an index
	 * @return an empty {@link TextPart} without a payload unchanged; any other
	 * {@link TextPart} or a {@link ReasoningPart} stamped as partial, with the index of
	 * the current run of deltas of its kind, or the next index when it starts a new run;
	 * any other part stamped as complete, with the next index
	 */
	public MessagePart stamp(MessagePart part) {
		if (part instanceof TextPart textPart && textPart.text().isEmpty() && textPart.payload() == null) {
			return part;
		}
		boolean delta = part instanceof TextPart || part instanceof ReasoningPart;
		if (!delta || part.getClass() != this.lastDeltaKind) {
			this.index++;
		}
		this.lastDeltaKind = delta ? part.getClass() : null;
		return delta ? StreamingParts.partial(part, this.index) : StreamingParts.complete(part, this.index);
	}

}
