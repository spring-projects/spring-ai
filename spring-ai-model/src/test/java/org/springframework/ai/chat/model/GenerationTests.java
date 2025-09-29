/*
 * Copyright 2023-2024 the original author or authors.
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

		assertEquals(expectedText, generation.getOutput().getText());
	}

	@Test
	void testConstructorWithMetadata() {
		AssistantMessage assistantMessage = new AssistantMessage("Test Assistant Message");
		Generation generation = new Generation(assistantMessage, this.mockChatGenerationMetadata1);

		assertEquals(this.mockChatGenerationMetadata1, generation.getMetadata());
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
		Generation generation = new Generation(assistantMessage, this.mockChatGenerationMetadata1);
		ChatGenerationMetadata metadata = generation.getMetadata();

		assertEquals(this.mockChatGenerationMetadata1, metadata);
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
		Generation generation1 = new Generation(assistantMessage1, this.mockChatGenerationMetadata1);
		Generation generation2 = new Generation(assistantMessage2, this.mockChatGenerationMetadata1);

		assertTrue(generation1.equals(generation2));
	}

	@Test
	void testEquals_DifferentMetadata() {
		AssistantMessage assistantMessage1 = new AssistantMessage("Test Assistant Message");
		AssistantMessage assistantMessage2 = new AssistantMessage("Test Assistant Message");
		Generation generation1 = new Generation(assistantMessage1, this.mockChatGenerationMetadata1);
		Generation generation2 = new Generation(assistantMessage2, this.mockChatGenerationMetadata2);

		assertFalse(generation1.equals(generation2));
	}

}
