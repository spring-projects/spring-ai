package org.springframework.ai.chat.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This class defines and contains unit tests for the Generation class.
 *
 * @author Vivek504
 */
public class GenerationTests {

	@Mock
	private ChatGenerationMetadata mockChatGenerationMetadata1;

	@Mock
	private ChatGenerationMetadata mockChatGenerationMetadata2;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
	}

	@Test
	void testGetOutput() {
		String expectedText = "Test Assistant Message";
		AssistantMessage assistantMessage = new AssistantMessage(expectedText);
		Generation generation = new Generation(assistantMessage);

		assertEquals(expectedText, generation.getOutput().getContent());
	}

	@Test
	void testConstructorWithMetadata() {
		AssistantMessage assistantMessage = new AssistantMessage("Test Assistant Message");
		Generation generation = new Generation(assistantMessage, mockChatGenerationMetadata1);

		assertEquals(mockChatGenerationMetadata1, generation.getMetadata());
	}

	@Test
	void testGetMetadata_Null() {
		AssistantMessage assistantMessage = new AssistantMessage("Test Assistant Message");
		Generation generation = new Generation(assistantMessage);
		ChatGenerationMetadata metadata = generation.getMetadata();

		assertEquals(ChatGenerationMetadata.NULL, metadata);
	}

	@Test
	void testGetMetadata_NotNull() {
		AssistantMessage assistantMessage = new AssistantMessage("Test Assistant Message");
		Generation generation = new Generation(assistantMessage, mockChatGenerationMetadata1);
		ChatGenerationMetadata metadata = generation.getMetadata();

		assertEquals(mockChatGenerationMetadata1, metadata);
	}

	@Test
	void testEquals_SameObjects() {
		AssistantMessage assistantMessage = new AssistantMessage("Test Assistant Message");
		Generation generation1 = new Generation(assistantMessage);
		Generation generation2 = generation1;

		assertTrue(generation1.equals(generation2));
	}

	@Test
	void testEquals_NotInstanceOfGeneration() {
		AssistantMessage assistantMessage = new AssistantMessage("Test Assistant Message");
		Generation generation = new Generation(assistantMessage);
		Object notGenerationObject = new Object();

		assertFalse(generation.equals(notGenerationObject));
	}

	@Test
	void testEquals_SameMetadata() {
		AssistantMessage assistantMessage1 = new AssistantMessage("Test Assistant Message");
		AssistantMessage assistantMessage2 = new AssistantMessage("Test Assistant Message");
		Generation generation1 = new Generation(assistantMessage1, mockChatGenerationMetadata1);
		Generation generation2 = new Generation(assistantMessage2, mockChatGenerationMetadata1);

		assertTrue(generation1.equals(generation2));
	}

	@Test
	void testEquals_DifferentMetadata() {
		AssistantMessage assistantMessage1 = new AssistantMessage("Test Assistant Message");
		AssistantMessage assistantMessage2 = new AssistantMessage("Test Assistant Message");
		Generation generation1 = new Generation(assistantMessage1, mockChatGenerationMetadata1);
		Generation generation2 = new Generation(assistantMessage2, mockChatGenerationMetadata2);

		assertFalse(generation1.equals(generation2));
	}

}
