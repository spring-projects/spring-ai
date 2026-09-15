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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse;
import org.springframework.ai.content.Media;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link MessagePart} records, and for the standalone
 * {@link ReasoningPart}.
 *
 * @author Christian Tzolov
 */
class MessagePartTests {

	private static final OpaquePayload ANTHROPIC_SIGNATURE = new OpaquePayload("anthropic", "signature", "sig-1");

	@Test
	void textPartOfHasNoPayloadAndEmptyAttributes() {
		TextPart part = TextPart.of("hello");

		assertThat(part.text()).isEqualTo("hello");
		assertThat(part.payload()).isNull();
		assertThat(part.attributes()).isEmpty();
	}

	@Test
	void whenTextIsNullThenThrow() {
		assertThatThrownBy(() -> new TextPart(null, null, Map.of())).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("text");
	}

	@Test
	void whenAttributesIsNullThenThrow() {
		assertThatThrownBy(() -> new TextPart("hello", null, null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("attributes");
	}

	@Test
	void attributesAreDefensivelyCopiedAndUnmodifiable() {
		Map<String, String> attributes = new HashMap<>();
		attributes.put("partIndex", "0");

		TextPart part = new TextPart("hello", null, attributes);
		attributes.put("partIndex", "9");

		assertThat(part.attributes()).containsEntry("partIndex", "0");
		assertThatThrownBy(() -> part.attributes().put("x", "y")).isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void reasoningPartIsAMessagePart() {
		assertThat(MessagePart.class.isAssignableFrom(ReasoningPart.class)).isTrue();
	}

	@Test
	void reasoningPartOfIsPlainText() {
		ReasoningPart part = ReasoningPart.of("thinking...");

		assertThat(part.text()).isEqualTo("thinking...");
		assertThat(part.summary()).isNull();
		assertThat(part.redacted()).isFalse();
		assertThat(part.payload()).isNull();
	}

	@Test
	void reasoningPartWithoutPayloadIsReplayableEverywhere() {
		ReasoningPart part = ReasoningPart.of("thinking...");

		assertThat(part.replayableTo("anthropic")).isTrue();
		assertThat(part.replayableTo("openai")).isTrue();
	}

	@Test
	void reasoningPartWithPayloadIsReplayableOnlyToItsProvider() {
		ReasoningPart part = new ReasoningPart("thinking...", null, false, ANTHROPIC_SIGNATURE, Map.of());

		assertThat(part.replayableTo("anthropic")).isTrue();
		assertThat(part.replayableTo("openai")).isFalse();
	}

	@Test
	void redactedReasoningPartMayHaveNullText() {
		OpaquePayload redacted = new OpaquePayload("anthropic", "redacted_thinking", "blob");

		ReasoningPart part = new ReasoningPart(null, null, true, redacted, Map.of());

		assertThat(part.text()).isNull();
		assertThat(part.redacted()).isTrue();
		assertThat(part.payload()).isEqualTo(redacted);
	}

	@Test
	void reasoningPartWithAttributesReturnsACopy() {
		ReasoningPart part = ReasoningPart.of("thinking...").withAttributes(Map.of("k", "v"));

		assertThat(part.attributes()).containsEntry("k", "v");
		assertThat(part.text()).isEqualTo("thinking...");
	}

	@Test
	void toolCallPartOfWrapsToolCall() {
		ToolCall toolCall = new ToolCall("id-1", "function", "getWeather", "{\"city\":\"Amsterdam\"}");

		ToolCallPart part = ToolCallPart.of(toolCall);

		assertThat(part.toolCall()).isEqualTo(toolCall);
		assertThat(part.payload()).isNull();
		assertThat(part.attributes()).isEmpty();
	}

	@Test
	void whenToolCallIsNullThenThrow() {
		assertThatThrownBy(() -> ToolCallPart.of(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("toolCall");
	}

	@Test
	void toolResultPartOfWrapsToolResponseAsSingleTextPart() {
		ToolResponse response = new ToolResponse("id-1", "getWeather", "{\"tempC\":18}");

		ToolResultPart part = ToolResultPart.of(response);

		assertThat(part.id()).isEqualTo("id-1");
		assertThat(part.name()).isEqualTo("getWeather");
		assertThat(part.isError()).isFalse();
		assertThat(part.content()).containsExactly(TextPart.of("{\"tempC\":18}"));
	}

	@Test
	void toolResultPartToToolResponseJoinsTextContentOnly() {
		Media image = Media.builder().mimeType(MimeTypeUtils.IMAGE_PNG).data(new byte[] { 1, 2, 3 }).build();
		ToolResultPart part = new ToolResultPart("id-1", "getWeather",
				List.of(TextPart.of("{\"tempC\""), MediaPart.of(image), TextPart.of(":18}")), false, null, Map.of());

		ToolResponse response = part.toToolResponse();

		assertThat(response).isEqualTo(new ToolResponse("id-1", "getWeather", "{\"tempC\":18}"));
	}

	@Test
	void toolResultPartContentIsDefensivelyCopied() {
		List<MessagePart> content = new ArrayList<>();
		content.add(TextPart.of("a"));

		ToolResultPart part = new ToolResultPart("id-1", "tool", content, false, null, Map.of());
		content.add(TextPart.of("b"));

		assertThat(part.content()).hasSize(1);
		assertThatThrownBy(() -> part.content().add(TextPart.of("c")))
			.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void mediaPartOfWrapsMedia() {
		Media image = Media.builder().mimeType(MimeTypeUtils.IMAGE_PNG).data("https://example.com/a.png").build();

		MediaPart part = MediaPart.of(image);

		assertThat(part.media()).isSameAs(image);
		assertThat(part.payload()).isNull();
	}

	@Test
	void unknownPartKeepsRawJson() {
		UnknownPart part = new UnknownPart("anthropic", "server_tool_use", "{\"type\":\"server_tool_use\"}", null,
				Map.of());

		assertThat(part.provider()).isEqualTo("anthropic");
		assertThat(part.kind()).isEqualTo("server_tool_use");
		assertThat(part.rawJson()).isEqualTo("{\"type\":\"server_tool_use\"}");
	}

	@Test
	void whenOpaquePayloadHasNullComponentThenThrow() {
		assertThatThrownBy(() -> new OpaquePayload("anthropic", "signature", null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("data");
	}

	@Test
	void partsWithSameComponentsAreEqual() {
		assertThat(TextPart.of("a")).isEqualTo(TextPart.of("a")).hasSameHashCodeAs(TextPart.of("a"));
		assertThat(new ReasoningPart("t", null, false, ANTHROPIC_SIGNATURE, Map.of()))
			.isEqualTo(new ReasoningPart("t", null, false, ANTHROPIC_SIGNATURE, Map.of()));
		assertThat(TextPart.of("a")).isNotEqualTo(ReasoningPart.of("a"));
	}

}
