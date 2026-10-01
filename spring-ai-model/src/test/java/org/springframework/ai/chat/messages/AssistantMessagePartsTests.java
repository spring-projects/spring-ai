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

package org.springframework.ai.chat.messages;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.chat.messages.part.MediaPart;
import org.springframework.ai.chat.messages.part.MessagePart;
import org.springframework.ai.chat.messages.part.OpaquePayload;
import org.springframework.ai.chat.messages.part.ReasoningPart;
import org.springframework.ai.chat.messages.part.TextPart;
import org.springframework.ai.chat.messages.part.ToolCallPart;
import org.springframework.ai.chat.messages.part.UnknownPart;
import org.springframework.ai.content.Media;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the content-part API of {@link AssistantMessage}: ordered parts, derived
 * views and the interaction between explicit parts and the legacy builder setters.
 *
 * @author Christian Tzolov
 */
class AssistantMessagePartsTests {

	private static final ToolCall WEATHER_CALL = new ToolCall("toolu_01", "function", "getWeather",
			"{\"city\":\"Amsterdam\"}");

	private static final ToolCall TIME_CALL = new ToolCall("toolu_02", "function", "getTime", "{}");

	private static final ReasoningPart SIGNED_REASONING = new ReasoningPart("The user wants the weather", null,
			new OpaquePayload("anthropic", "signature", "EqQBCkYIBBgC"), Map.of());

	private static final Media IMAGE = Media.builder()
		.mimeType(MimeTypeUtils.IMAGE_PNG)
		.data("https://example.com/a.png")
		.build();

	@Test
	void explicitPartsPreserveOrder() {
		AssistantMessage message = AssistantMessage.builder()
			.part(SIGNED_REASONING)
			.part(ToolCallPart.of(WEATHER_CALL))
			.part(TextPart.of("Let me check."))
			.part(ToolCallPart.of(TIME_CALL))
			.build();

		assertThat(message.getParts()).containsExactly(SIGNED_REASONING, ToolCallPart.of(WEATHER_CALL),
				TextPart.of("Let me check."), ToolCallPart.of(TIME_CALL));
	}

	@Test
	void partsListIsUnmodifiable() {
		AssistantMessage message = AssistantMessage.builder().part(TextPart.of("a")).build();

		assertThatThrownBy(() -> message.getParts().add(TextPart.of("b")))
			.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void getTextConcatenatesOnlyTextParts() {
		AssistantMessage message = AssistantMessage.builder()
			.part(ReasoningPart.of("hidden"))
			.part(TextPart.of("Hello, "))
			.part(ToolCallPart.of(WEATHER_CALL))
			.part(TextPart.of("world"))
			.build();

		assertThat(message.getText()).isEqualTo("Hello, world");
	}

	@Test
	void getTextIsEmptyWhenThereArePartsButNoTextPart() {
		// Never null for a message with parts, so streamed chunks and tool-call turns
		// can be joined without a null check, as before the parts model
		AssistantMessage message = AssistantMessage.builder()
			.part(ReasoningPart.of("hidden"))
			.part(ToolCallPart.of(WEATHER_CALL))
			.build();

		assertThat(message.getText()).isEmpty();
	}

	@Test
	void getTextIsNullOnlyWithoutAnyPart() {
		assertThat(new AssistantMessage((String) null).getText()).isNull();
		assertThat(AssistantMessage.builder().build().getText()).isNull();
	}

	@Test
	void derivedViewsSelectByPartType() {
		AssistantMessage message = AssistantMessage.builder()
			.part(SIGNED_REASONING)
			.part(ToolCallPart.of(WEATHER_CALL))
			.part(TextPart.of("text"))
			.part(MediaPart.of(IMAGE))
			.part(ToolCallPart.of(TIME_CALL))
			.build();

		assertThat(message.getToolCalls()).containsExactly(WEATHER_CALL, TIME_CALL);
		assertThat(message.hasToolCalls()).isTrue();
		assertThat(message.getMedia()).containsExactly(IMAGE);
		assertThat(message.getReasoning()).containsExactly(SIGNED_REASONING);
	}

	@Test
	void hasToolCallsIsFalseWithoutToolCallParts() {
		AssistantMessage message = AssistantMessage.builder().part(TextPart.of("text")).build();

		assertThat(message.hasToolCalls()).isFalse();
		assertThat(message.getToolCalls()).isEmpty();
		assertThat(message.getReasoning()).isEmpty();
	}

	@Test
	void reasoningBuilderMethodAddsAPart() {
		AssistantMessage message = AssistantMessage.builder().reasoning(SIGNED_REASONING).content("answer").build();

		assertThat(message.getParts()).containsExactly(SIGNED_REASONING, TextPart.of("answer"));
	}

	@Test
	void legacySettersMaterializeInLegacyOrderRegardlessOfCallOrder() {
		AssistantMessage contentFirst = AssistantMessage.builder()
			.content("text")
			.toolCalls(List.of(WEATHER_CALL))
			.media(List.of(IMAGE))
			.build();
		AssistantMessage mediaFirst = AssistantMessage.builder()
			.media(List.of(IMAGE))
			.toolCalls(List.of(WEATHER_CALL))
			.content("text")
			.build();

		assertThat(contentFirst.getParts()).containsExactly(TextPart.of("text"), ToolCallPart.of(WEATHER_CALL),
				MediaPart.of(IMAGE));
		assertThat(mediaFirst).isEqualTo(contentFirst).hasSameHashCodeAs(contentFirst);
	}

	@Test
	void legacySettersReplaceExplicitPartsOfTheirTypeInPlaceAndKeepTheLegacyOrderOtherwise() {
		AssistantMessage message = AssistantMessage.builder()
			.content("legacy text")
			.toolCalls(List.of(TIME_CALL))
			.part(SIGNED_REASONING)
			.part(ToolCallPart.of(WEATHER_CALL))
			.build();

		assertThat(message.getParts()).containsExactly(SIGNED_REASONING, TextPart.of("legacy text"),
				ToolCallPart.of(TIME_CALL));
		assertThat(message.getToolCalls()).containsExactly(TIME_CALL);
	}

	@Test
	void copyPreservesReasoningAndUnknownParts() {
		UnknownPart unknown = new UnknownPart("anthropic", "server_tool_use", "{\"type\":\"server_tool_use\"}", null,
				Map.of());
		AssistantMessage original = AssistantMessage.builder()
			.part(SIGNED_REASONING)
			.part(TextPart.of("text"))
			.part(unknown)
			.properties(Map.of("k", "v"))
			.build();

		AssistantMessage copy = original.copy();

		assertThat(copy).isNotSameAs(original).isEqualTo(original);
		assertThat(copy.getParts()).containsExactly(SIGNED_REASONING, TextPart.of("text"), unknown);
		assertThat(copy.getMetadata()).containsEntry("k", "v");
	}

	@Test
	void mutateAllowsAppendingParts() {
		AssistantMessage original = AssistantMessage.builder().part(TextPart.of("a")).build();

		AssistantMessage extended = original.mutate().part(TextPart.of("b")).build();

		assertThat(extended.getParts()).containsExactly(TextPart.of("a"), TextPart.of("b"));
		assertThat(extended.getText()).isEqualTo("ab");
	}

	@Test
	void mutateContentReplacesTheTextAtTheFirstTextPosition() {
		ReasoningPart secondReasoning = ReasoningPart.of("Now the answer");
		AssistantMessage original = AssistantMessage.builder()
			.part(SIGNED_REASONING)
			.part(TextPart.of("a"))
			.part(secondReasoning)
			.part(TextPart.of("b"))
			.part(ToolCallPart.of(WEATHER_CALL))
			.properties(Map.of("k", "v"))
			.build();

		AssistantMessage updated = original.mutate().content("x").build();

		assertThat(updated.getParts()).containsExactly(SIGNED_REASONING, TextPart.of("x"), secondReasoning,
				ToolCallPart.of(WEATHER_CALL));
		assertThat(updated.getText()).isEqualTo("x");
		assertThat(updated.getMetadata()).containsEntry("k", "v");
		assertThat(original.getText()).isEqualTo("ab");
	}

	@Test
	void mutateContentPutsTheTextBeforeTheToolCallsWhenThereWasNone() {
		AssistantMessage original = AssistantMessage.builder()
			.part(SIGNED_REASONING)
			.part(ToolCallPart.of(WEATHER_CALL))
			.build();

		AssistantMessage updated = original.mutate().content("text").build();

		assertThat(updated.getParts()).containsExactly(SIGNED_REASONING, TextPart.of("text"),
				ToolCallPart.of(WEATHER_CALL));
	}

	@Test
	void mutateContentAppendsTheTextWhenThereAreNoToolCallsOrMedia() {
		AssistantMessage original = AssistantMessage.builder().part(SIGNED_REASONING).build();

		AssistantMessage updated = original.mutate().content("text").build();

		assertThat(updated.getParts()).containsExactly(SIGNED_REASONING, TextPart.of("text"));
	}

	@Test
	void mutateSettersOnAMessageWithoutPartsOfTheirTypeKeepTheLegacyOrder() {
		AssistantMessage legacy = AssistantMessage.builder()
			.content("text")
			.toolCalls(List.of(WEATHER_CALL))
			.media(List.of(IMAGE))
			.build();

		AssistantMessage textAdded = AssistantMessage.builder()
			.toolCalls(List.of(WEATHER_CALL))
			.media(List.of(IMAGE))
			.build()
			.mutate()
			.content("text")
			.build();
		AssistantMessage toolCallsAdded = AssistantMessage.builder()
			.content("text")
			.media(List.of(IMAGE))
			.build()
			.mutate()
			.toolCalls(List.of(WEATHER_CALL))
			.build();
		AssistantMessage mediaAdded = AssistantMessage.builder()
			.content("text")
			.toolCalls(List.of(WEATHER_CALL))
			.build()
			.mutate()
			.media(List.of(IMAGE))
			.build();

		assertThat(legacy.getParts()).containsExactly(TextPart.of("text"), ToolCallPart.of(WEATHER_CALL),
				MediaPart.of(IMAGE));
		assertThat(textAdded).isEqualTo(legacy);
		assertThat(toolCallsAdded).isEqualTo(legacy);
		assertThat(mediaAdded).isEqualTo(legacy);
	}

	@Test
	void mutateToolCallsPutsTheToolCallsBeforeTheMediaWhenThereWereNone() {
		AssistantMessage original = AssistantMessage.builder()
			.part(SIGNED_REASONING)
			.part(TextPart.of("text"))
			.part(MediaPart.of(IMAGE))
			.build();

		AssistantMessage updated = original.mutate().toolCalls(List.of(WEATHER_CALL)).build();

		assertThat(updated.getParts()).containsExactly(SIGNED_REASONING, TextPart.of("text"),
				ToolCallPart.of(WEATHER_CALL), MediaPart.of(IMAGE));
	}

	@Test
	void contentReplacesTextPartsAddedAfterTheSetterWasCalled() {
		AssistantMessage message = AssistantMessage.builder().content("a").part(TextPart.of("b")).build();

		assertThat(message.getParts()).containsExactly(TextPart.of("a"));
		assertThat(message.getText()).isEqualTo("a");
	}

	@Test
	void mutateNullContentRemovesTheText() {
		AssistantMessage original = AssistantMessage.builder()
			.part(SIGNED_REASONING)
			.part(TextPart.of("text"))
			.part(ToolCallPart.of(WEATHER_CALL))
			.build();

		AssistantMessage updated = original.mutate().content(null).build();

		assertThat(updated.getParts()).containsExactly(SIGNED_REASONING, ToolCallPart.of(WEATHER_CALL));
		assertThat(updated.getText()).isEmpty();
	}

	@Test
	void mutateContentAlsoReplacesTextPartsAddedToTheBuilder() {
		AssistantMessage original = AssistantMessage.builder().part(TextPart.of("a")).build();

		AssistantMessage updated = original.mutate().part(TextPart.of("b")).content("x").build();

		assertThat(updated.getParts()).containsExactly(TextPart.of("x"));
	}

	@Test
	void mutateToolCallsReplacesTheToolCallsInPlace() {
		AssistantMessage original = AssistantMessage.builder()
			.part(SIGNED_REASONING)
			.part(ToolCallPart.of(WEATHER_CALL))
			.part(TextPart.of("text"))
			.build();

		AssistantMessage updated = original.mutate().toolCalls(List.of(TIME_CALL, WEATHER_CALL)).build();

		assertThat(updated.getParts()).containsExactly(SIGNED_REASONING, ToolCallPart.of(TIME_CALL),
				ToolCallPart.of(WEATHER_CALL), TextPart.of("text"));
	}

	@Test
	void mutateWithEmptyToolCallsClearsTheToolCalls() {
		AssistantMessage original = AssistantMessage.builder()
			.part(SIGNED_REASONING)
			.part(ToolCallPart.of(WEATHER_CALL))
			.part(TextPart.of("text"))
			.part(ToolCallPart.of(TIME_CALL))
			.build();

		AssistantMessage updated = original.mutate().toolCalls(List.of()).build();

		assertThat(updated.getParts()).containsExactly(SIGNED_REASONING, TextPart.of("text"));
		assertThat(updated.hasToolCalls()).isFalse();
	}

	@Test
	void mutateMediaReplacesTheMediaInPlace() {
		Media chart = Media.builder().mimeType(MimeTypeUtils.IMAGE_PNG).data("https://example.com/b.png").build();
		AssistantMessage original = AssistantMessage.builder()
			.part(TextPart.of("before"))
			.part(MediaPart.of(IMAGE))
			.part(TextPart.of("after"))
			.build();

		AssistantMessage updated = original.mutate().media(List.of(chart)).build();

		assertThat(updated.getParts()).containsExactly(TextPart.of("before"), MediaPart.of(chart),
				TextPart.of("after"));
		assertThat(updated.getMedia()).containsExactly(chart);
	}

	@Test
	void mutateWithEmptyMediaClearsTheMedia() {
		AssistantMessage original = AssistantMessage.builder()
			.part(TextPart.of("text"))
			.part(MediaPart.of(IMAGE))
			.build();

		AssistantMessage updated = original.mutate().media(List.of()).build();

		assertThat(updated.getParts()).containsExactly(TextPart.of("text"));
		assertThat(updated.getMedia()).isEmpty();
	}

	@Test
	void equalityIsDefinedOverParts() {
		AssistantMessage a = AssistantMessage.builder().part(SIGNED_REASONING).part(TextPart.of("text")).build();
		AssistantMessage b = AssistantMessage.builder().part(SIGNED_REASONING).part(TextPart.of("text")).build();
		AssistantMessage reordered = AssistantMessage.builder()
			.part(TextPart.of("text"))
			.part(SIGNED_REASONING)
			.build();

		assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
		assertThat(a).isNotEqualTo(reordered);
	}

	@Test
	void whenPartIsNullThenThrow() {
		assertThatThrownBy(() -> AssistantMessage.builder().part(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("part");
	}

	@Test
	void toStringMentionsParts() {
		AssistantMessage message = AssistantMessage.builder().part(TextPart.of("text")).build();

		assertThat(message.toString()).contains("parts=").contains("text");
	}

	@Test
	void partsAreMessagePartInstances() {
		AssistantMessage message = AssistantMessage.builder().content("text").build();

		List<MessagePart> parts = message.getParts();

		assertThat(parts).allSatisfy(part -> assertThat(part).isInstanceOf(MessagePart.class));
	}

}
