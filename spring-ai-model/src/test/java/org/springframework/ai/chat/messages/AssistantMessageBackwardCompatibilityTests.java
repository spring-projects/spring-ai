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
import org.springframework.ai.chat.messages.part.TextPart;
import org.springframework.ai.chat.messages.part.ToolCallPart;
import org.springframework.ai.content.Media;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.ai.chat.messages.AbstractMessage.MESSAGE_TYPE;

/**
 * Backward-compatibility tests for {@link AssistantMessage}: the pre-parts API (public
 * constructor, protected 4-arg constructor, generic builder and legacy getters) must
 * behave exactly as before the content-part model was introduced.
 *
 * @author Christian Tzolov
 */
class AssistantMessageBackwardCompatibilityTests {

	private static final ToolCall TOOL_CALL = new ToolCall("id", "function", "name", "{}");

	private static final Media IMAGE = Media.builder()
		.mimeType(MimeTypeUtils.IMAGE_PNG)
		.data("https://example.com/a.png")
		.build();

	@Test
	void publicConstructorYieldsSingleTextPart() {
		AssistantMessage message = new AssistantMessage("hello");

		assertThat(message.getText()).isEqualTo("hello");
		assertThat(message.getParts()).containsExactly(TextPart.of("hello"));
		assertThat(message.getToolCalls()).isEmpty();
		assertThat(message.hasToolCalls()).isFalse();
		assertThat(message.getMedia()).isEmpty();
		assertThat(message.getMessageType()).isEqualTo(MessageType.ASSISTANT);
	}

	@Test
	void nullContentYieldsNoTextPartAndNullText() {
		AssistantMessage message = new AssistantMessage(null);

		assertThat(message.getText()).isNull();
		assertThat(message.getParts()).isEmpty();
	}

	@Test
	void emptyContentIsPreservedAsEmptyText() {
		AssistantMessage message = AssistantMessage.builder().content("").build();

		assertThat(message.getText()).isEmpty();
		assertThat(message.getParts()).containsExactly(TextPart.of(""));
	}

	@Test
	void legacyBuilderProducesLegacyViews() {
		AssistantMessage message = AssistantMessage.builder()
			.content("hello")
			.properties(Map.of("k", "v"))
			.toolCalls(List.of(TOOL_CALL))
			.media(List.of(IMAGE))
			.build();

		assertThat(message.getText()).isEqualTo("hello");
		assertThat(message.getToolCalls()).containsExactly(TOOL_CALL);
		assertThat(message.hasToolCalls()).isTrue();
		assertThat(message.getMedia()).containsExactly(IMAGE);
		assertThat(message.getMetadata()).containsEntry("k", "v").containsEntry(MESSAGE_TYPE, MessageType.ASSISTANT);
	}

	@Test
	void legacyBuilderMessagesAreEqualWhenFieldsAreEqual() {
		AssistantMessage a = AssistantMessage.builder().content("hello").toolCalls(List.of(TOOL_CALL)).build();
		AssistantMessage b = AssistantMessage.builder().content("hello").toolCalls(List.of(TOOL_CALL)).build();

		assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
		assertThat(a).isNotEqualTo(new AssistantMessage("hello"));
	}

	@Test
	void mutateOnLegacyMessageRoundTrips() {
		AssistantMessage original = AssistantMessage.builder()
			.content("hello")
			.toolCalls(List.of(TOOL_CALL))
			.media(List.of(IMAGE))
			.build();

		AssistantMessage copy = original.mutate().build();

		assertThat(copy).isEqualTo(original);
		assertThat(copy.getParts()).containsExactly(TextPart.of("hello"), ToolCallPart.of(TOOL_CALL),
				MediaPart.of(IMAGE));
	}

	@Test
	void subclassUsingProtectedConstructorSeesLegacyOrderedParts() {
		LegacySubclass message = LegacySubclass.builder()
			.content("hello")
			.toolCalls(List.of(TOOL_CALL))
			.media(List.of(IMAGE))
			.marker("m")
			.build();

		assertThat(message.getMarker()).isEqualTo("m");
		assertThat(message.getText()).isEqualTo("hello");
		assertThat(message.getParts()).containsExactly(TextPart.of("hello"), ToolCallPart.of(TOOL_CALL),
				MediaPart.of(IMAGE));
	}

	@Test
	void subclassMutateStillWorksThroughLegacyBuilderMethods() {
		LegacySubclass original = LegacySubclass.builder().content("hello").marker("m").build();

		LegacySubclass copy = original.mutate().build();

		assertThat(copy).isNotSameAs(original);
		assertThat(copy.getMarker()).isEqualTo("m");
		assertThat(copy.getText()).isEqualTo("hello");
		assertThat(copy.getParts()).containsExactly(TextPart.of("hello"));
	}

	@Test
	void toStringIsNonEmpty() {
		assertThat(new AssistantMessage("hello").toString()).isNotBlank();
	}

	@Test
	void subclassBuilderWithoutPartsSupportRejectsExplicitParts() {
		assertThatThrownBy(() -> LegacySubclass.builder().part(TextPart.of("x")))
			.isInstanceOf(UnsupportedOperationException.class)
			.hasMessageContaining("supportsParts");
	}

	@Test
	void subclassBuilderCanOptIntoExplicitParts() {
		PartsSubclass message = PartsSubclass.builder()
			.part(TextPart.of("a"))
			.content("b")
			.toolCalls(List.of(TOOL_CALL))
			.marker("m")
			.build();

		assertThat(message.getMarker()).isEqualTo("m");
		assertThat(message.getParts()).containsExactly(TextPart.of("a"), TextPart.of("b"), ToolCallPart.of(TOOL_CALL));
		assertThat(message.getText()).isEqualTo("ab");
	}

	/**
	 * A subclass whose builder opts into explicit parts by overriding
	 * {@code supportsParts()} and building from {@code buildParts()}.
	 */
	static final class PartsSubclass extends AssistantMessage {

		private final String marker;

		private PartsSubclass(List<MessagePart> parts, String marker, Map<String, Object> properties) {
			super(parts, properties);
			this.marker = marker;
		}

		String getMarker() {
			return this.marker;
		}

		public static PartsBuilder builder() {
			return new PartsBuilder();
		}

		static final class PartsBuilder extends AssistantMessage.Builder<PartsBuilder> {

			private String marker;

			private PartsBuilder() {
			}

			PartsBuilder marker(String marker) {
				this.marker = marker;
				return self();
			}

			@Override
			protected boolean supportsParts() {
				return true;
			}

			@Override
			public PartsSubclass build() {
				return new PartsSubclass(buildParts(), this.marker, this.properties);
			}

		}

	}

	/**
	 * Mirrors how provider modules extend {@link AssistantMessage} today: the protected
	 * 4-arg constructor plus a generic builder subclass that reads the protected builder
	 * fields.
	 */
	static final class LegacySubclass extends AssistantMessage {

		private final String marker;

		private LegacySubclass(String content, String marker, Map<String, Object> properties, List<ToolCall> toolCalls,
				List<Media> media) {
			super(content, properties, toolCalls, media);
			this.marker = marker;
		}

		String getMarker() {
			return this.marker;
		}

		@Override
		public LegacyBuilder mutate() {
			return builder().content(getText())
				.properties(getMetadata())
				.toolCalls(getToolCalls())
				.media(getMedia())
				.marker(getMarker());
		}

		public static LegacyBuilder builder() {
			return new LegacyBuilder();
		}

		static final class LegacyBuilder extends AssistantMessage.Builder<LegacyBuilder> {

			private String marker;

			private LegacyBuilder() {
			}

			LegacyBuilder marker(String marker) {
				this.marker = marker;
				return self();
			}

			@Override
			public LegacySubclass build() {
				return new LegacySubclass(this.content, this.marker, this.properties, this.toolCalls, this.media);
			}

		}

	}

}
