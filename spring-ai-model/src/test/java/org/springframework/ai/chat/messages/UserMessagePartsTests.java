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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.part.MediaPart;
import org.springframework.ai.chat.messages.part.TextPart;
import org.springframework.ai.chat.messages.part.UnknownPart;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the {@link org.springframework.ai.chat.messages.part.MessagePart} view of
 * {@link UserMessage}: ordered parts, derived text and media, builder semantics and
 * backward compatibility of the legacy constructors and setters.
 *
 * @author Christian Tzolov
 */
class UserMessagePartsTests {

	private static final Media IMAGE = Media.builder()
		.mimeType(MimeTypeUtils.IMAGE_PNG)
		.data("https://example.com/a.png")
		.build();

	private static final Media AUDIO = Media.builder()
		.mimeType(MimeTypeUtils.parseMimeType("audio/wav"))
		.data("https://example.com/a.wav")
		.build();

	// -- parts-first construction --

	@Test
	void partsKeepTheirOrderAndDriveTextAndMedia() {
		UserMessage message = UserMessage.builder()
			.part(TextPart.of("Look at "))
			.part(MediaPart.of(IMAGE))
			.part(TextPart.of("this."))
			.build();

		assertThat(message.getParts()).containsExactly(TextPart.of("Look at "), MediaPart.of(IMAGE),
				TextPart.of("this."));
		assertThat(message.getText()).isEqualTo("Look at this.");
		assertThat(message.getMedia()).containsExactly(IMAGE);
		assertThat(message.getMessageType()).isEqualTo(MessageType.USER);
	}

	@Test
	void mediaOnlyMessageHasEmptyText() {
		UserMessage message = UserMessage.builder().part(MediaPart.of(IMAGE)).build();

		assertThat(message.getText()).isEmpty();
		assertThat(message.getMedia()).containsExactly(IMAGE);
	}

	@Test
	void unknownPartsAreKeptAndIgnoredByTheViews() {
		UnknownPart document = new UnknownPart("anthropic", "document", "{\"type\":\"document\"}", null, Map.of());
		UserMessage message = UserMessage.builder().part(document).part(TextPart.of("Summarize.")).build();

		assertThat(message.getParts()).containsExactly(document, TextPart.of("Summarize."));
		assertThat(message.getText()).isEqualTo("Summarize.");
		assertThat(message.getMedia()).isEmpty();
	}

	@Test
	void partsAndMediaViewsAreUnmodifiable() {
		UserMessage message = UserMessage.builder().text("hi").media(IMAGE).build();

		assertThatThrownBy(() -> message.getParts().add(TextPart.of("x")))
			.isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> message.getMedia().add(AUDIO)).isInstanceOf(UnsupportedOperationException.class);
	}

	// -- legacy constructors and setters materialize parts in the legacy order --

	@Test
	void legacyConstructorYieldsASingleTextPart() {
		UserMessage message = new UserMessage("hello");

		assertThat(message.getParts()).containsExactly(TextPart.of("hello"));
		assertThat(message.getText()).isSameAs("hello");
	}

	@Test
	void legacyBuilderOrdersTextBeforeMedia() {
		UserMessage message = UserMessage.builder().media(IMAGE, AUDIO).text("describe").build();

		assertThat(message.getParts()).containsExactly(TextPart.of("describe"), MediaPart.of(IMAGE),
				MediaPart.of(AUDIO));
		assertThat(message.getMedia()).containsExactly(IMAGE, AUDIO);
	}

	@Test
	void legacyMediaFieldIsStillPopulated() {
		@SuppressWarnings("removal")
		FieldReadingSubclass message = new FieldReadingSubclass(UserMessage.builder().text("t").media(IMAGE).build());

		assertThat(message.mediaFromField()).containsExactly(IMAGE);
	}

	// -- builder text/media replace the corresponding parts, so rewrites keep working --

	@Test
	void textOnABuilderWithPartsReplacesTheTextPartsInPlace() {
		UserMessage original = UserMessage.builder()
			.part(MediaPart.of(IMAGE))
			.part(TextPart.of("old"))
			.part(TextPart.of(" question"))
			.build();

		UserMessage rewritten = original.mutate().text("augmented question").build();

		assertThat(rewritten.getParts()).containsExactly(MediaPart.of(IMAGE), TextPart.of("augmented question"));
		assertThat(rewritten.getText()).isEqualTo("augmented question");
		assertThat(rewritten.getMedia()).containsExactly(IMAGE);
	}

	@Test
	void textOnABuilderWithoutTextPartsIsPrepended() {
		UserMessage rewritten = UserMessage.builder().part(MediaPart.of(IMAGE)).text("what is this?").build();

		assertThat(rewritten.getParts()).containsExactly(TextPart.of("what is this?"), MediaPart.of(IMAGE));
	}

	@Test
	void mediaOnABuilderWithPartsReplacesTheMediaParts() {
		UserMessage original = UserMessage.builder().part(MediaPart.of(IMAGE)).part(TextPart.of("look")).build();

		UserMessage rewritten = original.mutate().media(AUDIO).build();

		// The replacement takes the place of the first media part
		assertThat(rewritten.getParts()).containsExactly(MediaPart.of(AUDIO), TextPart.of("look"));
		assertThat(rewritten.getMedia()).containsExactly(AUDIO);
	}

	@Test
	void mediaOnABuilderWithoutMediaPartsIsAppended() {
		UserMessage rewritten = UserMessage.builder().part(TextPart.of("look")).media(IMAGE).build();

		assertThat(rewritten.getParts()).containsExactly(TextPart.of("look"), MediaPart.of(IMAGE));
	}

	@Test
	void resourceTextOnABuilderWithPartsReplacesTheTextParts() {
		UserMessage rewritten = UserMessage.builder()
			.part(TextPart.of("old"))
			.part(MediaPart.of(IMAGE))
			.text(new ByteArrayResource("from resource".getBytes(StandardCharsets.UTF_8)))
			.build();

		assertThat(rewritten.getParts()).containsExactly(TextPart.of("from resource"), MediaPart.of(IMAGE));
	}

	@Test
	void messagesWithEqualButDistinctMediaAreEqual() {
		// What a memory repository rebuilds after persistence: same data, new instances
		Media rebuilt = Media.builder().mimeType(MimeTypeUtils.IMAGE_PNG).data("https://example.com/a.png").build();
		UserMessage original = UserMessage.builder().text("look").media(IMAGE).build();
		UserMessage copy = UserMessage.builder().text("look").media(rebuilt).build();

		assertThat(rebuilt).isNotSameAs(IMAGE);
		assertThat(copy).isEqualTo(original).hasSameHashCodeAs(original);
	}

	@Test
	void mutateWithoutChangesKeepsPartsMetadataAndEquality() {
		UserMessage original = UserMessage.builder()
			.part(TextPart.of("a"))
			.part(MediaPart.of(IMAGE))
			.part(TextPart.of("b"))
			.metadata(Map.of("k", "v"))
			.build();

		UserMessage copy = original.copy();

		assertThat(copy).isEqualTo(original).isNotSameAs(original);
		assertThat(copy.hashCode()).isEqualTo(original.hashCode());
		assertThat(copy.getParts()).isEqualTo(original.getParts());
		assertThat(copy.getMetadata()).isEqualTo(original.getMetadata());
	}

	@Test
	void equalityIsPartOrderSensitive() {
		UserMessage textFirst = UserMessage.builder().part(TextPart.of("a")).part(MediaPart.of(IMAGE)).build();
		UserMessage mediaFirst = UserMessage.builder().part(MediaPart.of(IMAGE)).part(TextPart.of("a")).build();
		UserMessage legacy = UserMessage.builder().text("a").media(IMAGE).build();

		assertThat(textFirst).isEqualTo(legacy);
		assertThat(textFirst).isNotEqualTo(mediaFirst);
	}

	@Test
	void toStringIncludesTheParts() {
		UserMessage message = UserMessage.builder().text("hi").media(IMAGE).build();

		assertThat(message.toString()).contains("UserMessage").contains("parts=").contains("TextPart");
	}

	@Test
	void nullPartsAreRejected() {
		assertThatThrownBy(() -> UserMessage.builder().part(null).build()).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> UserMessage.builder().parts(List.of()).parts(null).build())
			.isInstanceOf(IllegalArgumentException.class);
	}

	/**
	 * A subclass reading the deprecated protected {@code media} field, the way
	 * out-of-tree subclasses did before the parts model.
	 */
	@SuppressWarnings("removal")
	static final class FieldReadingSubclass extends UserMessage {

		FieldReadingSubclass(UserMessage source) {
			super(source.getParts(), source.getMetadata());
		}

		List<Media> mediaFromField() {
			return this.media;
		}

	}

}
