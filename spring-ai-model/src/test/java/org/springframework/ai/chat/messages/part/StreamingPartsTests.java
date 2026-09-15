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

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.content.Media;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link StreamingParts} and {@link MessagePart#withAttributes(Map)}.
 *
 * @author Christian Tzolov
 */
class StreamingPartsTests {

	@Test
	void partialStampsIndexAndPartialFlag() {
		MessagePart part = StreamingParts.partial(TextPart.of("Hel"), 2);

		assertThat(part).isInstanceOf(TextPart.class);
		assertThat(((TextPart) part).text()).isEqualTo("Hel");
		assertThat(StreamingParts.partIndex(part)).isEqualTo(2);
		assertThat(StreamingParts.isPartial(part)).isTrue();
		assertThat(part.attributes()).containsEntry(StreamingParts.PART_INDEX_ATTRIBUTE, "2")
			.containsEntry(StreamingParts.PARTIAL_ATTRIBUTE, "true");
	}

	@Test
	void completeStampsIndexOnly() {
		MessagePart part = StreamingParts.complete(ReasoningPart.of("done"), 0);

		assertThat(StreamingParts.partIndex(part)).isZero();
		assertThat(StreamingParts.isPartial(part)).isFalse();
		assertThat(part.attributes()).containsEntry(StreamingParts.PART_INDEX_ATTRIBUTE, "0")
			.doesNotContainKey(StreamingParts.PARTIAL_ATTRIBUTE);
	}

	@Test
	void stampingPreservesOtherAttributesAndPayload() {
		OpaquePayload payload = new OpaquePayload("anthropic", "signature", "sig");
		ReasoningPart original = new ReasoningPart("t", null, false, payload, Map.of("k", "v"));

		MessagePart stamped = StreamingParts.partial(original, 1);

		assertThat(stamped.payload()).isEqualTo(payload);
		assertThat(stamped.attributes()).containsEntry("k", "v");
	}

	@Test
	void stripRemovesStreamingAttributesOnly() {
		MessagePart stamped = StreamingParts.partial(new TextPart("a", null, Map.of("k", "v")), 3);

		MessagePart stripped = StreamingParts.strip(stamped);

		assertThat(stripped).isEqualTo(new TextPart("a", null, Map.of("k", "v")));
		assertThat(StreamingParts.partIndex(stripped)).isNull();
		assertThat(StreamingParts.isPartial(stripped)).isFalse();
	}

	@Test
	void stripOfUnstampedPartIsSameValue() {
		TextPart part = TextPart.of("a");

		assertThat(StreamingParts.strip(part)).isEqualTo(part);
	}

	@Test
	void partIndexIsNullWhenAbsentOrMalformed() {
		assertThat(StreamingParts.partIndex(TextPart.of("a"))).isNull();
		assertThat(StreamingParts.partIndex(new TextPart("a", null, Map.of(StreamingParts.PART_INDEX_ATTRIBUTE, "x"))))
			.isNull();
		assertThat(StreamingParts.isPartial(new TextPart("a", null, Map.of(StreamingParts.PARTIAL_ATTRIBUTE, "no"))))
			.isFalse();
	}

	@Test
	void withAttributesReplacesAttributesOnEveryRecord() {
		Map<String, String> attributes = Map.of("k", "v");
		OpaquePayload payload = new OpaquePayload("p", "k", "d");
		Media media = Media.builder().mimeType(MimeTypeUtils.IMAGE_PNG).data("https://example.com/a.png").build();
		ToolCall toolCall = new ToolCall("id", "function", "name", "{}");

		assertThat(new TextPart("t", payload, Map.of()).withAttributes(attributes))
			.isEqualTo(new TextPart("t", payload, attributes));
		assertThat(new ReasoningPart("t", "s", true, payload, Map.of()).withAttributes(attributes))
			.isEqualTo(new ReasoningPart("t", "s", true, payload, attributes));
		assertThat(new ToolCallPart(toolCall, payload, Map.of()).withAttributes(attributes))
			.isEqualTo(new ToolCallPart(toolCall, payload, attributes));
		assertThat(new ToolResultPart("id", "name", List.of(TextPart.of("r")), true, payload, Map.of())
			.withAttributes(attributes))
			.isEqualTo(new ToolResultPart("id", "name", List.of(TextPart.of("r")), true, payload, attributes));
		assertThat(new MediaPart(media, payload, Map.of()).withAttributes(attributes))
			.isEqualTo(new MediaPart(media, payload, attributes));
		assertThat(new UnknownPart("p", "k", "{}", payload, Map.of()).withAttributes(attributes))
			.isEqualTo(new UnknownPart("p", "k", "{}", payload, attributes));
	}

}
