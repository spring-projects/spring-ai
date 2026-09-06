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

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.content.ContentPart;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.ai.chat.messages.AbstractMessage.MESSAGE_TYPE;

/**
 * Unit tests for {@link UserMessage}.
 *
 * @author Thomas Vitale
 */
class UserMessageTests {

	@Test
	void userMessageWithNullText() {
		assertThatThrownBy(() -> new UserMessage((String) null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Content must not be null for SYSTEM or USER messages");
	}

	@Test
	void userMessageWithTextContent() {
		String text = "Hello, world!";
		UserMessage message = new UserMessage(text);
		assertThat(message.getText()).isEqualTo(text);
		assertThat(message.getMedia()).isEmpty();
		assertThat(message.getMetadata()).hasSize(1).containsEntry(MESSAGE_TYPE, MessageType.USER);
	}

	@Test
	void userMessageWithNullResource() {
		assertThatThrownBy(() -> new UserMessage((Resource) null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("resource cannot be null");
	}

	@Test
	void userMessageWithResource() {
		UserMessage message = new UserMessage(new ClassPathResource("prompt-user.txt"));
		assertThat(message.getText()).isEqualTo("Hello, world!");
		assertThat(message.getMedia()).isEmpty();
		assertThat(message.getMetadata()).hasSize(1).containsEntry(MESSAGE_TYPE, MessageType.USER);
	}

	@Test
	void userMessageFromBuilderWithText() {
		String text = "Hello, world!";
		UserMessage message = UserMessage.builder()
			.text(text)
			.media(new Media(MimeTypeUtils.TEXT_PLAIN, new ClassPathResource("prompt-user.txt")))
			.metadata(Map.of("key", "value"))
			.build();
		assertThat(message.getText()).isEqualTo(text);
		assertThat(message.getMedia()).hasSize(1);
		assertThat(message.getMetadata()).hasSize(2)
			.containsEntry(MESSAGE_TYPE, MessageType.USER)
			.containsEntry("key", "value");
	}

	@Test
	void userMessageFromBuilderWithResource() {
		UserMessage message = UserMessage.builder().text(new ClassPathResource("prompt-user.txt")).build();
		assertThat(message.getText()).isEqualTo("Hello, world!");
		assertThat(message.getMedia()).isEmpty();
		assertThat(message.getMetadata()).hasSize(1).containsEntry(MESSAGE_TYPE, MessageType.USER);
	}

	@Test
	void userMessageCopy() {
		String text1 = "Hello, world!";
		Media media1 = new Media(MimeTypeUtils.TEXT_PLAIN, new ClassPathResource("prompt-user.txt"));
		Map<String, Object> metadata1 = Map.of("key", "value");
		UserMessage userMessage1 = UserMessage.builder().text(text1).media(media1).metadata(metadata1).build();

		UserMessage userMessage2 = userMessage1.copy();

		assertThat(userMessage2.getText()).isEqualTo(text1);
		assertThat(userMessage2.getMedia()).hasSize(1).isNotSameAs(metadata1);
		assertThat(userMessage2.getMetadata()).hasSize(2).isNotSameAs(metadata1);
	}

	@Test
	void userMessageMutate() {
		String text1 = "Hello, world!";
		Media media1 = new Media(MimeTypeUtils.TEXT_PLAIN, new ClassPathResource("prompt-user.txt"));
		Map<String, Object> metadata1 = Map.of("key", "value");
		UserMessage userMessage1 = UserMessage.builder().text(text1).media(media1).metadata(metadata1).build();

		UserMessage userMessage2 = userMessage1.mutate().build();

		assertThat(userMessage2.getText()).isEqualTo(text1);
		assertThat(userMessage2.getMedia()).hasSize(1).isNotSameAs(metadata1);
		assertThat(userMessage2.getMetadata()).hasSize(2).isNotSameAs(metadata1);

		String text3 = "Farewell, Aragog!";
		UserMessage userMessage3 = userMessage2.mutate().text(text3).build();

		assertThat(userMessage3.getText()).isEqualTo(text3);
		assertThat(userMessage3.getMedia()).hasSize(1).isNotSameAs(metadata1);
		assertThat(userMessage3.getMetadata()).hasSize(2).isNotSameAs(metadata1);
	}

	@Test
	void userMessageWithEmptyText() {
		UserMessage message = new UserMessage("");
		assertThat(message.getText()).isEmpty();
		assertThat(message.getMedia()).isEmpty();
		assertThat(message.getMetadata()).hasSize(1).containsEntry(MESSAGE_TYPE, MessageType.USER);
	}

	@Test
	void userMessageWithWhitespaceText() {
		String text = "   \t\n   ";
		UserMessage message = new UserMessage(text);
		assertThat(message.getText()).isEqualTo(text);
		assertThat(message.getMedia()).isEmpty();
		assertThat(message.getMetadata()).hasSize(1).containsEntry(MESSAGE_TYPE, MessageType.USER);
	}

	@Test
	void userMessageBuilderWithNullText() {
		assertThatThrownBy(() -> UserMessage.builder().text((String) null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Content must not be null for SYSTEM or USER messages");
	}

	@Test
	void userMessageBuilderWithEmptyMediaList() {
		String text = "No media attached";
		UserMessage message = UserMessage.builder().text(text).build();

		assertThat(message.getText()).isEqualTo(text);
		assertThat(message.getMedia()).isEmpty();
		assertThat(message.getMetadata()).hasSize(1).containsEntry(MESSAGE_TYPE, MessageType.USER);
	}

	@Test
	void userMessageBuilderWithEmptyMetadata() {
		String text = "Test message";
		UserMessage message = UserMessage.builder().text(text).metadata(Map.of()).build();

		assertThat(message.getText()).isEqualTo(text);
		assertThat(message.getMetadata()).hasSize(1).containsEntry(MESSAGE_TYPE, MessageType.USER);
	}

	@Test
	void userMessageBuilderOverwriteMetadata() {
		String text = "Test message";
		UserMessage message = UserMessage.builder()
			.text(text)
			.metadata(Map.of("key1", "value1"))
			.metadata(Map.of("key2", "value2"))
			.build();

		assertThat(message.getMetadata()).hasSize(2)
			.containsEntry(MESSAGE_TYPE, MessageType.USER)
			.containsEntry("key2", "value2")
			.doesNotContainKey("key1");
	}

	@Test
	void userMessageCopyWithNoMedia() {
		String text = "Simple message";
		Map<String, Object> metadata = Map.of("key", "value");
		UserMessage original = UserMessage.builder().text(text).metadata(metadata).build();

		UserMessage copy = original.copy();

		assertThat(copy).isNotSameAs(original);
		assertThat(copy.getText()).isEqualTo(text);
		assertThat(copy.getMedia()).isEmpty();
		assertThat(copy.getMetadata()).isNotSameAs(original.getMetadata()).isEqualTo(original.getMetadata());
	}

	@Test
	void userMessageMutateAddMedia() {
		String text = "Original message";
		UserMessage original = UserMessage.builder().text(text).build();

		Media newMedia = new Media(MimeTypeUtils.TEXT_PLAIN, new ClassPathResource("prompt-user.txt"));
		UserMessage mutated = original.mutate().media(newMedia).build();

		assertThat(original.getMedia()).isEmpty();
		assertThat(mutated.getMedia()).hasSize(1).contains(newMedia);
		assertThat(mutated.getText()).isEqualTo(text);
	}

	@Test
	void userMessageMutateChaining() {
		UserMessage original = UserMessage.builder().text("Original").build();

		Media media = new Media(MimeTypeUtils.TEXT_PLAIN, new ClassPathResource("prompt-user.txt"));
		UserMessage result = original.mutate().text("Updated").media(media).metadata(Map.of("key", "value")).build();

		assertThat(result.getText()).isEqualTo("Updated");
		assertThat(result.getMedia()).hasSize(1).contains(media);
		assertThat(result.getMetadata()).hasSize(2)
			.containsEntry(MESSAGE_TYPE, MessageType.USER)
			.containsEntry("key", "value");
	}

	@Test
	void userMessageEqualsAndHashCode() {
		String text = "Test message";
		Media media = new Media(MimeTypeUtils.TEXT_PLAIN, new ClassPathResource("prompt-user.txt"));
		Map<String, Object> metadata = Map.of("key", "value");

		UserMessage message1 = UserMessage.builder().text(text).media(media).metadata(metadata).build();

		UserMessage message2 = UserMessage.builder().text(text).media(media).metadata(metadata).build();

		assertThat(message1).isEqualTo(message2);
		assertThat(message1.hashCode()).isEqualTo(message2.hashCode());
	}

	@Test
	void userMessageNotEqualsWithDifferentText() {
		UserMessage message1 = new UserMessage("Text 1");
		UserMessage message2 = new UserMessage("Text 2");

		assertThat(message1).isNotEqualTo(message2);
	}

	@Test
	void userMessageToString() {
		String text = "Test message";
		UserMessage message = new UserMessage(text);

		String toString = message.toString();
		assertThat(toString).contains("UserMessage").contains(text).contains("USER");
	}

	@Test
	void userMessageToStringWithMedia() {
		String text = "Test with media";
		Media media = new Media(MimeTypeUtils.TEXT_PLAIN, new ClassPathResource("prompt-user.txt"));
		UserMessage message = UserMessage.builder().text(text).media(media).build();

		String toString = message.toString();
		assertThat(toString).contains("UserMessage").contains(text).contains("media");
	}

	private static Media imageMedia() {
		return new Media(MimeTypeUtils.IMAGE_PNG, URI.create("https://example.com/image.png"));
	}

	@Test
	void userMessageFromContentPartsPreservesOrder() {
		Media media1 = imageMedia();
		Media media2 = imageMedia();
		UserMessage message = UserMessage.builder()
			.contentParts(ContentPart.text("--- p1 ---"), ContentPart.media(media1), ContentPart.text("--- p2 ---"),
					ContentPart.media(media2))
			.build();

		assertThat(message.getContentParts()).containsExactly(ContentPart.text("--- p1 ---"), ContentPart.media(media1),
				ContentPart.text("--- p2 ---"), ContentPart.media(media2));
		assertThat(message.getText()).isEqualTo("--- p1 ---\n--- p2 ---");
		assertThat(message.getMedia()).containsExactly(media1, media2);
	}

	@Test
	void userMessageFromContentPartsJoinsTextWithLiteralNewline() {
		UserMessage message = UserMessage.builder()
			.contentParts(ContentPart.text("first"), ContentPart.text("second"))
			.build();

		// A literal "\n", never System.lineSeparator(): the projection is persisted and
		// participates in equals, so it must not vary by platform.
		assertThat(message.getText()).isEqualTo("first\nsecond");
	}

	@Test
	void userMessageFromContentPartsWithMediaOnly() {
		Media media = imageMedia();
		UserMessage message = UserMessage.builder().contentParts(ContentPart.media(media)).build();

		assertThat(message.getText()).isEmpty();
		assertThat(message.getMedia()).containsExactly(media);
		assertThat(message.getContentParts()).hasSize(1);
	}

	@Test
	void userMessageFromEmptyContentParts() {
		UserMessage message = UserMessage.builder().contentParts(List.of()).build();

		assertThat(message.getText()).isEmpty();
		assertThat(message.getMedia()).isEmpty();
		assertThat(message.getContentParts()).isEmpty();
	}

	@Test
	void userMessageTextProjectionOmitsMedia() {
		// Components that treat user text as a retrieval query read getText() directly,
		// so
		// no media marker may leak into it.
		Media media = imageMedia();
		UserMessage message = UserMessage.builder()
			.contentParts(ContentPart.text("what is this?"), ContentPart.media(media))
			.build();

		assertThat(message.getText()).isEqualTo("what is this?");
		assertThat(message.getText()).doesNotContain(media.getName());
	}

	@Test
	void userMessageDerivesContentPartsFromTextAndMedia() {
		Media media1 = imageMedia();
		Media media2 = imageMedia();
		UserMessage message = UserMessage.builder().text("Hello").media(media1, media2).build();

		assertThat(message.getContentParts()).containsExactly(ContentPart.text("Hello"), ContentPart.media(media1),
				ContentPart.media(media2));
	}

	@Test
	void userMessageDerivesContentPartsFromBlankText() {
		// Derivation keys on text != null, not on the text being non-blank, so blank text
		// round-trips through copy().
		assertThat(new UserMessage("").getContentParts()).containsExactly(ContentPart.text(""));
		assertThat(new UserMessage("   \t\n   ").getContentParts()).containsExactly(ContentPart.text("   \t\n   "));
	}

	@Test
	void userMessageBuilderTextDiscardsContentParts() {
		Media media = imageMedia();
		UserMessage message = UserMessage.builder()
			.contentParts(ContentPart.text("ordered"), ContentPart.media(media))
			.text("flat")
			.build();

		assertThat(message.getText()).isEqualTo("flat");
		assertThat(message.getContentParts()).containsExactly(ContentPart.text("flat"));
		assertThat(message.getMedia()).isEmpty();
	}

	@Test
	void userMessageBuilderMediaDiscardsContentParts() {
		Media ordered = imageMedia();
		Media flat = imageMedia();
		UserMessage message = UserMessage.builder()
			.contentParts(ContentPart.text("ordered"), ContentPart.media(ordered))
			.text("flat")
			.media(flat)
			.build();

		assertThat(message.getContentParts()).containsExactly(ContentPart.text("flat"), ContentPart.media(flat));
	}

	@Test
	void userMessageBuilderContentPartsOverridesText() {
		UserMessage message = UserMessage.builder().text("discarded").contentParts(ContentPart.text("ordered")).build();

		assertThat(message.getText()).isEqualTo("ordered");
		assertThat(message.getContentParts()).containsExactly(ContentPart.text("ordered"));
	}

	@Test
	void userMessageBuilderStillRejectsTextAndResourceTogether() {
		assertThatThrownBy(
				() -> UserMessage.builder().text("some text").text(new ClassPathResource("prompt-user.txt")).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("textContent and resource cannot be set at the same time");
	}

	@Test
	void userMessageCopyPreservesContentParts() {
		Media media = imageMedia();
		UserMessage original = UserMessage.builder()
			.contentParts(ContentPart.text("a"), ContentPart.media(media), ContentPart.text("b"))
			.build();

		UserMessage copy = original.copy();

		assertThat(copy).isNotSameAs(original);
		assertThat(copy.getContentParts()).isEqualTo(original.getContentParts());
		assertThat(copy.getText()).isEqualTo("a\nb");
	}

	@Test
	void userMessageMutateWithTextFlattensWithoutStaleText() {
		Media media = imageMedia();
		UserMessage original = UserMessage.builder()
			.contentParts(ContentPart.text("old"), ContentPart.media(media))
			.build();

		UserMessage mutated = original.mutate().text("replaced").build();

		// The whole point of eager invalidation: no part may still carry the old text, or
		// a
		// parts-aware provider would silently send a prompt the caller already replaced.
		assertThat(mutated.getText()).isEqualTo("replaced");
		assertThat(mutated.getContentParts()).doesNotContain(ContentPart.text("old"));
		assertThat(mutated.getContentParts()).containsExactly(ContentPart.text("replaced"), ContentPart.media(media));
		assertThat(mutated.getMedia()).containsExactly(media);
	}

	@Test
	void userMessageMutateAppendTextPreservesContentParts() {
		Media media = imageMedia();
		UserMessage original = UserMessage.builder()
			.contentParts(ContentPart.text("body"), ContentPart.media(media))
			.build();

		UserMessage appended = original.mutate().appendText("FORMAT").build();

		assertThat(appended.getContentParts()).containsExactly(ContentPart.text("body"), ContentPart.media(media),
				ContentPart.text("FORMAT"));
		assertThat(appended.getMedia()).containsExactly(media);
	}

	@Test
	void userMessageAppendTextConcatenatesFlatText() {
		UserMessage message = UserMessage.builder().text("body").appendText("\nFORMAT").build();

		assertThat(message.getText()).isEqualTo("body\nFORMAT");
		assertThat(message.getContentParts()).containsExactly(ContentPart.text("body\nFORMAT"));
	}

	@Test
	void userMessageAppendTextIgnoresNullAndBlank() {
		UserMessage message = UserMessage.builder().text("body").appendText(null).appendText("   ").build();

		assertThat(message.getText()).isEqualTo("body");
	}

	@Test
	void userMessageBuilderRejectsNullContentParts() {
		assertThatThrownBy(() -> UserMessage.builder().contentParts((List<ContentPart>) null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("contentParts cannot be null");

		assertThatThrownBy(() -> UserMessage.builder().contentParts(Arrays.asList(ContentPart.text("a"), null)))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("contentParts cannot have null elements");
	}

	@Test
	void userMessageContentPartsAndMediaAreUnmodifiable() {
		Media media = imageMedia();
		UserMessage message = UserMessage.builder()
			.contentParts(ContentPart.text("a"), ContentPart.media(media))
			.build();

		assertThatThrownBy(() -> message.getContentParts().add(ContentPart.text("b")))
			.isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> message.getMedia().add(imageMedia()))
			.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void userMessageContentPartsIndependentOfCallerList() {
		List<ContentPart> parts = new ArrayList<>();
		parts.add(ContentPart.text("a"));
		UserMessage message = UserMessage.builder().contentParts(parts).build();

		parts.add(ContentPart.text("b"));

		assertThat(message.getContentParts()).containsExactly(ContentPart.text("a"));
		assertThat(message.getText()).isEqualTo("a");
	}

	@Test
	void userMessageHasInterleavedContentOnlyWhenFlatteningWouldLose() {
		Media media = imageMedia();

		// Flat-equivalent shapes: a single leading text, then media. Providers with a
		// flat
		// fast path may keep using it for these.
		assertThat(new UserMessage("just text").hasInterleavedContent()).isFalse();
		assertThat(UserMessage.builder().text("text").media(media).build().hasInterleavedContent()).isFalse();
		assertThat(UserMessage.builder()
			.contentParts(ContentPart.text("text"), ContentPart.media(media))
			.build()
			.hasInterleavedContent()).isFalse();
		assertThat(UserMessage.builder().contentParts(ContentPart.media(media)).build().hasInterleavedContent())
			.isFalse();
		assertThat(UserMessage.builder().contentParts(List.of()).build().hasInterleavedContent()).isFalse();

		// Shapes that only the ordered form can express: text after media, or several
		// texts
		// (whose separate blocks a provider concatenates without the projection's
		// newline).
		assertThat(UserMessage.builder()
			.contentParts(ContentPart.media(media), ContentPart.text("after"))
			.build()
			.hasInterleavedContent()).isTrue();
		assertThat(UserMessage.builder()
			.contentParts(ContentPart.text("one"), ContentPart.text("two"))
			.build()
			.hasInterleavedContent()).isTrue();
	}

	@Test
	void userMessageEqualityIgnoresContentPartOrdering() {
		Media media = imageMedia();
		UserMessage textFirst = UserMessage.builder()
			.contentParts(ContentPart.text("a"), ContentPart.media(media))
			.build();
		UserMessage mediaFirst = UserMessage.builder()
			.contentParts(ContentPart.media(media), ContentPart.text("a"))
			.build();

		// Pinning existing semantics, not endorsing them: UserMessage does not override
		// equals/hashCode, so neither media nor part ordering participates in equality.
		// Fixing that requires value-based equals on Media first.
		assertThat(textFirst).isEqualTo(mediaFirst);
	}

}
