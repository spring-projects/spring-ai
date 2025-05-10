package org.springframework.ai.chat.messages;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.ai.chat.messages.AbstractMessage.MESSAGE_TYPE;

class DeveloperMessageTests {

	@Test
	void developerMessageWithNullText() {
		assertThrows(IllegalArgumentException.class, () -> new DeveloperMessage((String) null));
	}

	@Test
	void developerMessageWithTextContent() {
		String text = "Developer instructions for the model.";
		DeveloperMessage message = new DeveloperMessage(text);
		assertEquals(text, message.getText());
		assertEquals(MessageType.DEVELOPER, message.getMetadata().get(MESSAGE_TYPE));
	}

	@Test
	void developerMessageWithNullResource() {
		assertThrows(IllegalArgumentException.class, () -> new DeveloperMessage((Resource) null));
	}

	@Test
	void developerMessageWithResource() {
		DeveloperMessage message = new DeveloperMessage(new ClassPathResource("prompt-developer.txt"));
		assertEquals("Tell me, did you sail across the sun?", message.getText());
		assertEquals(MessageType.DEVELOPER, message.getMetadata().get(MESSAGE_TYPE));
	}

	@Test
	void developerMessageFromBuilderWithText() {
		String text = "Developer instructions for {name}";
		DeveloperMessage message = DeveloperMessage.builder().text(text).metadata(Map.of("key", "value")).build();
		assertEquals(text, message.getText());
		assertThat(message.getMetadata()).hasSize(2)
			.containsEntry(MESSAGE_TYPE, MessageType.DEVELOPER)
			.containsEntry("key", "value");
	}

	@Test
	void developerMessageFromBuilderWithResource() {
		Resource resource = new ClassPathResource("prompt-developer.txt");
		DeveloperMessage message = DeveloperMessage.builder().text(resource).metadata(Map.of("key", "value")).build();
		assertEquals("Tell me, did you sail across the sun?", message.getText());
		assertThat(message.getMetadata()).hasSize(2)
			.containsEntry(MESSAGE_TYPE, MessageType.DEVELOPER)
			.containsEntry("key", "value");
	}

	@Test
	void developerMessageCopy() {
		String text1 = "Developer instructions";
		Map<String, Object> metadata1 = Map.of("key", "value");
		DeveloperMessage message1 = DeveloperMessage.builder().text(text1).metadata(metadata1).build();

		DeveloperMessage message2 = message1.copy();

		assertThat(message2.getText()).isEqualTo(text1);
		assertThat(message2.getMetadata()).hasSize(2).isNotSameAs(metadata1);
	}

	@Test
	void developerMessageMutate() {
		String text1 = "Developer instructions";
		Map<String, Object> metadata1 = Map.of("key", "value");
		DeveloperMessage message1 = DeveloperMessage.builder().text(text1).metadata(metadata1).build();

		DeveloperMessage message2 = message1.mutate().build();

		assertThat(message2.getText()).isEqualTo(text1);
		assertThat(message2.getMetadata()).hasSize(2).isNotSameAs(metadata1);

		String newText = "Updated developer instructions";
		DeveloperMessage message3 = message2.mutate().text(newText).build();

		assertThat(message3.getText()).isEqualTo(newText);
		assertThat(message3.getMetadata()).hasSize(2);
	}

}
