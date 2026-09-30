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

import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * Transport attributes that let a streaming chat model deliver a message part in
 * increments and let {@code MessageAggregator} rebuild the ordered parts list.
 * <p>
 * The contract for a streamed {@code ChatResponse} chunk:
 * <ul>
 * <li>A chunk's assistant message carries at most one part per content block index.</li>
 * <li>A part stamped with {@link #partial(MessagePart, int)} is a delta: text and
 * arguments are appended to what was already received for that index, a non-null payload
 * replaces the accumulated one.</li>
 * <li>A part stamped with {@link #complete(MessagePart, int)} replaces whatever was
 * accumulated for that index.</li>
 * <li>Parts without an index are aggregated the pre-streaming way: text is concatenated,
 * tool calls are appended, other parts are carried in arrival order.</li>
 * <li>The chat model sets {@code ChatResponseMetadata.id} on every chunk of one model
 * response; the aggregator groups indexed parts by that id so that consecutive tool-call
 * rounds flowing through one stream do not merge into each other.</li>
 * </ul>
 * The two attributes are removed from the aggregated message, so they never take part in
 * message equality or persistence.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public final class StreamingParts {

	/** Attribute holding the zero-based content block index of a streamed part. */
	public static final String PART_INDEX_ATTRIBUTE = "partIndex";

	/** Attribute set to {@code true} on a streamed part that is a delta. */
	public static final String PARTIAL_ATTRIBUTE = "partial";

	private StreamingParts() {
	}

	/**
	 * Marks a part as a delta of the content block at the given index.
	 * @param part the part
	 * @param index the content block index
	 * @return a copy of the part with the streaming attributes set
	 */
	public static MessagePart partial(MessagePart part, int index) {
		return stamp(part, index, true);
	}

	/**
	 * Marks a part as the complete content block at the given index.
	 * @param part the part
	 * @param index the content block index
	 * @return a copy of the part with the index set and no partial flag
	 */
	public static MessagePart complete(MessagePart part, int index) {
		return stamp(part, index, false);
	}

	/**
	 * The content block index of a streamed part.
	 * @param part the part
	 * @return the index, or {@code null} when absent or malformed
	 */
	public static @Nullable Integer partIndex(MessagePart part) {
		String value = part.attributes().get(PART_INDEX_ATTRIBUTE);
		if (value == null) {
			return null;
		}
		try {
			return Integer.valueOf(value);
		}
		catch (NumberFormatException ex) {
			return null;
		}
	}

	/**
	 * Whether a streamed part is a delta.
	 * @param part the part
	 * @return {@code true} only when the partial attribute is {@code "true"}
	 */
	public static boolean isPartial(MessagePart part) {
		return Boolean.parseBoolean(part.attributes().get(PARTIAL_ATTRIBUTE));
	}

	/**
	 * Removes the streaming attributes from a part.
	 * @param part the part
	 * @return the same value when nothing was stamped, else a copy without the streaming
	 * attributes
	 */
	public static MessagePart strip(MessagePart part) {
		Map<String, String> attributes = part.attributes();
		if (!attributes.containsKey(PART_INDEX_ATTRIBUTE) && !attributes.containsKey(PARTIAL_ATTRIBUTE)) {
			return part;
		}
		Map<String, String> stripped = new HashMap<>(attributes);
		stripped.remove(PART_INDEX_ATTRIBUTE);
		stripped.remove(PARTIAL_ATTRIBUTE);
		return part.withAttributes(stripped);
	}

	private static MessagePart stamp(MessagePart part, int index, boolean partial) {
		Assert.notNull(part, "part must not be null");
		Assert.isTrue(index >= 0, "index must not be negative");
		Map<String, String> attributes = new HashMap<>(part.attributes());
		attributes.put(PART_INDEX_ATTRIBUTE, Integer.toString(index));
		if (partial) {
			attributes.put(PARTIAL_ATTRIBUTE, "true");
		}
		else {
			attributes.remove(PARTIAL_ATTRIBUTE);
		}
		return part.withAttributes(attributes);
	}

}
