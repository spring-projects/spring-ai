/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.openai.chat.client.advisor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiTestConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link MessageChatMemoryAdvisor}.
 */
@SpringBootTest(classes = OpenAiTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class MessageChatMemoryAdvisorIT extends AbstractChatMemoryAdvisorIT {

	private static final Logger logger = LoggerFactory.getLogger(MessageChatMemoryAdvisorIT.class);

	@Autowired
	private org.springframework.ai.chat.model.ChatModel chatModel;

	@Override
	protected MessageChatMemoryAdvisor createAdvisor(ChatMemory chatMemory) {
		return MessageChatMemoryAdvisor.builder(chatMemory).build();
	}

	@Test
	@Disabled
	void shouldHandleMultipleUserMessagesInSamePrompt() {
		testMultipleUserMessagesInSamePrompt();
	}

	@Test
	void shouldUseCustomConversationId() {
		testUseCustomConversationId();
	}

	@Test
	void shouldMaintainSeparateConversations() {
		testMaintainSeparateConversations();
	}

	@Test
	void shouldHandleMultipleMessagesInReactiveMode() {
		testHandleMultipleMessagesInReactiveMode();
	}

	@Test
	@Disabled
	void shouldHandleMultipleUserMessagesInPrompt() {
		// Arrange
		String conversationId = "multi-user-messages-" + System.currentTimeMillis();
		ChatMemory chatMemory = MessageWindowChatMemory.builder()
			.chatMemoryRepository(new InMemoryChatMemoryRepository())
			.build();

		// Create MessageChatMemoryAdvisor with the conversation ID
		MessageChatMemoryAdvisor advisor = MessageChatMemoryAdvisor.builder(chatMemory)
			.conversationId(conversationId)
			.build();

		ChatClient chatClient = ChatClient.builder(this.chatModel).defaultAdvisors(advisor).build();

		// Create a prompt with multiple user messages
		List<Message> messages = new ArrayList<>();
		messages.add(new UserMessage("My name is David."));
		messages.add(new UserMessage("I work as a software engineer."));
		messages.add(new UserMessage("What is my profession?"));

		// Create a prompt with the list of messages
		Prompt prompt = new Prompt(messages);

		// Send the prompt to the chat client
		String answer = chatClient.prompt(prompt)
			.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
			.call()
			.content();

		logger.info("Answer: {}", answer);

		// Assert response is relevant
		assertThat(answer).containsIgnoringCase("software engineer");

		// Verify memory contains all user messages
		List<Message> memoryMessages = chatMemory.get(conversationId);
		assertThat(memoryMessages).hasSize(4); // 3 user messages + 1 assistant response
		assertThat(memoryMessages.get(0).getText()).isEqualTo("My name is David.");
		assertThat(memoryMessages.get(1).getText()).isEqualTo("I work as a software engineer.");
		assertThat(memoryMessages.get(2).getText()).isEqualTo("What is my profession?");

		// Send a follow-up question
		String followUpAnswer = chatClient.prompt()
			.user("What is my name?")
			.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
			.call()
			.content();

		logger.info("Follow-up Answer: {}", followUpAnswer);

		// Assert the model remembers the name
		assertThat(followUpAnswer).containsIgnoringCase("David");
	}

	/**
	 * Tests that the advisor correctly uses a conversation ID supplier when provided.
	 */
	@Test
	protected void testUseSupplierConversationId() {
		// Arrange
		ChatMemory chatMemory = MessageWindowChatMemory.builder()
				.chatMemoryRepository(new InMemoryChatMemoryRepository())
				.build();

		// ConversationId circular iterator
		String firstConversationId = "conversationId-1";
		String secondConversationId = "conversationId-2";
		AtomicReference<String> conversationIdHolder = new AtomicReference<>(firstConversationId);

		// Create advisor with conversation id supplier returning conversationId interchangeable
		var advisor = MessageChatMemoryAdvisor.builder(chatMemory).conversationIdSupplier(conversationIdHolder::get).build();

		ChatClient chatClient = ChatClient.builder(this.chatModel).defaultAdvisors(advisor).build();

		String firstQuestion = "What is the capital of Germany?";
		String firstAnswer = chatClient.prompt()
				.user(firstQuestion)
				.call()
				.content();
		logger.info("First question: {}", firstQuestion);
		logger.info("First answer: {}", firstAnswer);
		// Assert response is relevant
		assertThat(firstAnswer).containsIgnoringCase("Berlin");

		conversationIdHolder.set(secondConversationId);
		String secondQuestion = "What is the capital of Poland?";
		String secondAnswer = chatClient.prompt()
				.user(secondQuestion)
				.call()
				.content();
		logger.info("Second question: {}", secondQuestion);
		logger.info("Second answer: {}", secondAnswer);
		// Assert response is relevant
		assertThat(secondAnswer).containsIgnoringCase("Warsaw");

		conversationIdHolder.set(firstConversationId);
		String thirdQuestion = "What is the capital of Spain?";
		String thirdAnswer = chatClient.prompt()
				.user(thirdQuestion)
				.call()
				.content();
		logger.info("Third question: {}", thirdQuestion);
		logger.info("Third answer: {}", thirdAnswer);
		// Assert response is relevant
		assertThat(thirdAnswer).containsIgnoringCase("Madrid");

		// Verify first conversation memory contains the firstQuestion, firstAnswer, thirdQuestion and thirdAnswer
		List<Message> firstMemoryMessages = chatMemory.get(firstConversationId);
		assertThat(firstMemoryMessages).hasSize(4);
		assertThat(firstMemoryMessages.get(0).getText()).isEqualTo(firstQuestion);
		assertThat(firstMemoryMessages.get(2).getText()).isEqualTo(thirdQuestion);

		// Verify second conversation memory contains the secondQuestion and secondAnswer
		List<Message> secondMemoryMessages = chatMemory.get(secondConversationId);
		assertThat(secondMemoryMessages).hasSize(2);
		assertThat(secondMemoryMessages.get(0).getText()).isEqualTo(secondQuestion);
	}

	@Test
	void shouldHandleNonExistentConversation() {
		testHandleNonExistentConversation();
	}

	@Test
	void shouldStoreCompleteContentInStreamingMode() {
		// Arrange
		String conversationId = "streaming-test-" + System.currentTimeMillis();
		ChatMemory chatMemory = MessageWindowChatMemory.builder()
			.chatMemoryRepository(new InMemoryChatMemoryRepository())
			.build();

		// Create MessageChatMemoryAdvisor with the conversation ID
		MessageChatMemoryAdvisor advisor = MessageChatMemoryAdvisor.builder(chatMemory)
			.conversationId(conversationId)
			.build();

		ChatClient chatClient = ChatClient.builder(this.chatModel).defaultAdvisors(advisor).build();

		// Act - Use streaming API
		String userInput = "Tell me a short joke about programming";

		// Collect the streaming responses
		chatClient.prompt()
			.user(userInput)
			.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
			.stream()
			.content()
			.collectList()
			.block();

		// Wait a moment to ensure all processing is complete
		try {
			Thread.sleep(500);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}

		// Assert - Check that the memory contains the complete content
		List<Message> memoryMessages = chatMemory.get(conversationId);

		// Should have at least 2 messages (user + assistant)
		assertThat(memoryMessages).hasSizeGreaterThanOrEqualTo(2);

		// First message should be the user message
		assertThat(memoryMessages.get(0).getText()).isEqualTo(userInput);

		// Last message should be the assistant's response and should have content
		Message assistantMessage = memoryMessages.get(memoryMessages.size() - 1);
		assertThat(assistantMessage.getText()).isNotEmpty();

		logger.info("Assistant response stored in memory: {}", assistantMessage.getText());
	}

	@Test
	void shouldHandleStreamingWithChatMemory() {
		testStreamingWithChatMemory();
	}

}
