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

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.BaseChatMemoryAdvisor;
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
 * Abstract base class for chat memory advisor integration tests. Contains common test
 * logic to avoid duplication between different advisor implementations.
 */
@SpringBootTest(classes = OpenAiTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public abstract class AbstractChatMemoryAdvisorIT {

	protected final Logger logger = LoggerFactory.getLogger(getClass());

	@Autowired
	protected org.springframework.ai.chat.model.ChatModel chatModel;

	/**
	 * Create an advisor instance for testing.
	 * @param chatMemory The chat memory to use
	 * @return An instance of the advisor to test
	 */
	protected abstract BaseChatMemoryAdvisor createAdvisor(ChatMemory chatMemory);

	/**
	 * Assert the follow-up response meets the expectations for this advisor type. Default
	 * implementation expects the model to remember "John" from the first message.
	 * Subclasses can override this to implement advisor-specific assertions.
	 * @param followUpAnswer The follow-up answer from the model
	 */
	protected void assertFollowUpResponse(String followUpAnswer) {
		// Default implementation - expect model to remember "John"
		assertThat(followUpAnswer).containsIgnoringCase("John");
	}

	/**
	 * Common test logic for handling multiple user messages in the same prompt. This
	 * tests that the advisor correctly stores all user messages from a prompt and uses
	 * them appropriately in subsequent interactions.
	 */
	protected void testMultipleUserMessagesInPrompt() {
		String conversationId = "multi-user-messages-" + System.currentTimeMillis();
		ChatMemory chatMemory = MessageWindowChatMemory.builder()
			.chatMemoryRepository(new InMemoryChatMemoryRepository())
			.build();

		var advisor = createAdvisor(chatMemory);

		ChatClient chatClient = ChatClient.builder(this.chatModel).defaultAdvisors(advisor).build();

		// Create a prompt with multiple user messages
		List<Message> messages = new ArrayList<>();
		messages.add(new UserMessage("My name is David."));
		messages.add(new UserMessage("I work as a software engineer."));
		messages.add(new UserMessage("What is my profession?"));

		Prompt prompt = new Prompt(messages);

		String answer = chatClient.prompt(prompt)
			.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
			.call()
			.content();

		logger.info("Answer: {}", answer);
		assertThat(answer).containsIgnoringCase("software engineer");

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
		assertThat(followUpAnswer).containsIgnoringCase("David");
	}

	/**
	 * Common test logic for handling multiple user messages in the same prompt. This
	 * tests that the advisor correctly stores all user messages from a prompt and uses
	 * them appropriately in subsequent interactions.
	 */
	protected void testMultipleUserMessagesInSamePrompt() {
		// Arrange
		String conversationId = "test-conversation-multi-user-" + System.currentTimeMillis();
		ChatMemory chatMemory = MessageWindowChatMemory.builder()
			.chatMemoryRepository(new InMemoryChatMemoryRepository())
			.build();

		// Create advisor with the conversation ID
		var advisor = createAdvisor(chatMemory);

		ChatClient chatClient = ChatClient.builder(this.chatModel).defaultAdvisors(advisor).build();

		// Act - Create a list of messages for the prompt
		List<Message> messages = new ArrayList<>();
		messages.add(new UserMessage("My name is John."));
		messages.add(new UserMessage("I am from New York."));
		messages.add(new UserMessage("What city am I from?"));

		// Create a prompt with the list of messages
		Prompt prompt = new Prompt(messages);

		// Send the prompt to the chat client
		String answer = chatClient.prompt(prompt)
			.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
			.call()
			.content();

		logger.info("Multiple user messages answer: {}", answer);

		// Assert response is relevant to the last question
		assertThat(answer).containsIgnoringCase("New York");

		// Verify memory contains all user messages and the response
		List<Message> memoryMessages = chatMemory.get(conversationId);
		assertThat(memoryMessages).hasSize(4); // 3 user messages + 1 assistant response
		assertThat(memoryMessages.get(0).getText()).isEqualTo("My name is John.");
		assertThat(memoryMessages.get(1).getText()).isEqualTo("I am from New York.");
		assertThat(memoryMessages.get(2).getText()).isEqualTo("What city am I from?");

		// Act - Send a follow-up question
		String followUpAnswer = chatClient.prompt()
			.user("What is my name?")
			.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
			.call()
			.content();

		logger.info("Follow-up answer: {}", followUpAnswer);

		// Use the subclass-specific assertion for the follow-up response
		assertFollowUpResponse(followUpAnswer);

		// Verify memory now contains all previous messages plus the follow-up and its
		// response
		memoryMessages = chatMemory.get(conversationId);
		assertThat(memoryMessages).hasSize(6); // 3 user + 1 assistant + 1 user + 1
												// assistant
		assertThat(memoryMessages.get(4).getText()).isEqualTo("What is my name?");
	}

	/**
	 * Tests that the advisor correctly uses a custom conversation ID when provided.
	 */
	protected void testUseCustomConversationId() {
		// Arrange
		String customConversationId = "custom-conversation-id-" + System.currentTimeMillis();
		ChatMemory chatMemory = MessageWindowChatMemory.builder()
			.chatMemoryRepository(new InMemoryChatMemoryRepository())
			.build();

		// Create advisor without a default conversation ID
		var advisor = createAdvisor(chatMemory);

		ChatClient chatClient = ChatClient.builder(this.chatModel).defaultAdvisors(advisor).build();

		String question = "What is the capital of Germany?";

		String answer = chatClient.prompt()
			.user(question)
			.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, customConversationId))
			.call()
			.content();

		logger.info("Question: {}", question);
		logger.info("Answer: {}", answer);

		// Assert response is relevant
		assertThat(answer).containsIgnoringCase("Berlin");

		// Verify memory contains the question and answer
		List<Message> memoryMessages = chatMemory.get(customConversationId);
		assertThat(memoryMessages).hasSize(2);
		assertThat(memoryMessages.get(0).getText()).isEqualTo(question);
	}

	/**
	 * Tests that the advisor maintains separate conversations for different conversation
	 * IDs.
	 */
	protected void testMaintainSeparateConversations() {
		// Arrange
		String conversationId1 = "conversation-1-" + System.currentTimeMillis();
		String conversationId2 = "conversation-2-" + System.currentTimeMillis();

		ChatMemory chatMemory = MessageWindowChatMemory.builder()
			.chatMemoryRepository(new InMemoryChatMemoryRepository())
			.build();

		// Create advisor without a default conversation ID
		var advisor = createAdvisor(chatMemory);

		ChatClient chatClient = ChatClient.builder(this.chatModel).defaultAdvisors(advisor).build();

		// Act - First conversation
		String answer1 = chatClient.prompt()
			.user("My name is Alice.")
			.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId1))
			.call()
			.content();

		logger.info("Answer 1: {}", answer1);

		// Act - Second conversation
		String answer2 = chatClient.prompt()
			.user("My name is Bob.")
			.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId2))
			.call()
			.content();

		logger.info("Answer 2: {}", answer2);

		// Verify memory contains separate conversations
		List<Message> memoryMessages1 = chatMemory.get(conversationId1);
		List<Message> memoryMessages2 = chatMemory.get(conversationId2);

		assertThat(memoryMessages1).hasSize(2); // 1 user + 1 assistant
		assertThat(memoryMessages2).hasSize(2); // 1 user + 1 assistant
		assertThat(memoryMessages1.get(0).getText()).isEqualTo("My name is Alice.");
		assertThat(memoryMessages2.get(0).getText()).isEqualTo("My name is Bob.");

		// Act - Follow-up in first conversation
		String followUpAnswer1 = chatClient.prompt()
			.user("What is my name?")
			.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId1))
			.call()
			.content();

		logger.info("Follow-up Answer 1: {}", followUpAnswer1);

		// Act - Follow-up in second conversation
		String followUpAnswer2 = chatClient.prompt()
			.user("What is my name?")
			.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId2))
			.call()
			.content();

		logger.info("Follow-up Answer 2: {}", followUpAnswer2);

		// Assert responses are relevant to their respective conversations
		assertFollowUpResponseForName(followUpAnswer1, "Alice");
		assertFollowUpResponseForName(followUpAnswer2, "Bob");

		// Verify memory now contains all messages for both conversations
		memoryMessages1 = chatMemory.get(conversationId1);
		memoryMessages2 = chatMemory.get(conversationId2);

		assertThat(memoryMessages1).hasSize(4); // 2 user + 2 assistant
		assertThat(memoryMessages2).hasSize(4); // 2 user + 2 assistant
		assertThat(memoryMessages1.get(2).getText()).isEqualTo("What is my name?");
		assertThat(memoryMessages2.get(2).getText()).isEqualTo("What is my name?");
	}

	/**
	 * Assert the follow-up response for a specific name. Default implementation expects
	 * the model to remember the name from the first message. Subclasses can override this
	 * to implement advisor-specific assertions.
	 * @param followUpAnswer The model's response to the follow-up question
	 * @param expectedName The name that should be remembered
	 */
	protected void assertFollowUpResponseForName(String followUpAnswer, String expectedName) {
		assertThat(followUpAnswer).containsIgnoringCase(expectedName);
	}

	/**
	 * Tests that the advisor handles a non-existent conversation ID gracefully.
	 */
	protected void testHandleNonExistentConversation() {
		// Arrange
		String nonExistentId = "non-existent-conversation-" + System.currentTimeMillis();
		ChatMemory chatMemory = MessageWindowChatMemory.builder()
			.chatMemoryRepository(new InMemoryChatMemoryRepository())
			.build();

		// Create advisor without a default conversation ID
		var advisor = createAdvisor(chatMemory);

		ChatClient chatClient = ChatClient.builder(this.chatModel).defaultAdvisors(advisor).build();

		// Act - Send a question to a non-existent conversation
		String question = "Do you remember our previous conversation?";

		String answer = chatClient.prompt()
			.user(question)
			.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, nonExistentId))
			.call()
			.content();

		logger.info("Question: {}", question);
		logger.info("Answer: {}", answer);

		// Assert response indicates no previous conversation
		assertNonExistentConversationResponse(answer);

		// Verify memory now contains this message
		List<Message> memoryMessages = chatMemory.get(nonExistentId);
		assertThat(memoryMessages).hasSize(2); // 1 user message + 1 assistant response
		assertThat(memoryMessages.get(0).getText()).isEqualTo(question);
	}

	/**
	 * Assert the response for a non-existent conversation. Default implementation expects
	 * the model to indicate there's no previous conversation. Subclasses can override
	 * this to implement advisor-specific assertions.
	 * @param answer The model's response to the question about a previous conversation
	 */
	protected void assertNonExistentConversationResponse(String answer) {
		// Log the actual model response for debugging
		System.out.println("[DEBUG] Model response for non-existent conversation: " + answer);
		String normalized = answer.toLowerCase().replace('’', '\'');
		boolean containsExpectedWord = normalized.contains("don't") || normalized.contains("no")
				|| normalized.contains("not") || normalized.contains("previous")
				|| normalized.contains("past conversation") || normalized.contains("independent")
				|| normalized.contains("retain information");
		assertThat(containsExpectedWord).as("Response should indicate no previous conversation").isTrue();
	}

	/**
	 * Assert the follow-up response for reactive mode test. Default implementation
	 * expects the model to remember the name and location. Subclasses can override this
	 * to implement advisor-specific assertions.
	 * @param followUpAnswer The model's response to the follow-up question
	 */
	protected void assertReactiveFollowUpResponse(String followUpAnswer) {
		assertThat(followUpAnswer).containsIgnoringCase("Charlie");
		assertThat(followUpAnswer).containsIgnoringCase("London");
	}

	protected void testHandleMultipleMessagesInReactiveMode() {
		String conversationId = "reactive-conversation-" + System.currentTimeMillis();
		ChatMemory chatMemory = MessageWindowChatMemory.builder()
			.chatMemoryRepository(new InMemoryChatMemoryRepository())
			.build();

		var advisor = createAdvisor(chatMemory);

		ChatClient chatClient = ChatClient.builder(this.chatModel).defaultAdvisors(advisor).build();

		List<String> responseList = new ArrayList<>();
		for (String message : List.of("My name is Charlie.", "I am 30 years old.", "I live in London.")) {
			String response = chatClient.prompt()
				.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
				.user(message)
				.call()
				.content();
			responseList.add(response);
		}

		for (int i = 0; i < responseList.size(); i++) {
			logger.info("Response {}: {}", i, responseList.get(i));
		}

		List<Message> memoryMessages = chatMemory.get(conversationId);
		assertThat(memoryMessages).hasSize(6); // 3 user + 3 assistant
		assertThat(memoryMessages.get(0).getText()).isEqualTo("My name is Charlie.");
		assertThat(memoryMessages.get(2).getText()).isEqualTo("I am 30 years old.");
		assertThat(memoryMessages.get(4).getText()).isEqualTo("I live in London.");

		String followUpAnswer = chatClient.prompt()
			.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
			.user("What is my name and where do I live?")
			.call()
			.content();

		logger.info("Follow-up answer: {}", followUpAnswer);

		assertReactiveFollowUpResponse(followUpAnswer);

		memoryMessages = chatMemory.get(conversationId);
		assertThat(memoryMessages).hasSize(8); // 4 user messages + 4 assistant responses
		assertThat(memoryMessages.get(6).getText()).isEqualTo("What is my name and where do I live?");
	}

	/**
	 * Tests that the advisor correctly handles streaming responses and updates the
	 * memory. This verifies that the adviseStream method in chat memory advisors is
	 * working correctly.
	 */
	protected void testStreamingWithChatMemory() {
		// Arrange
		String conversationId = "streaming-conversation-" + System.currentTimeMillis();
		ChatMemory chatMemory = MessageWindowChatMemory.builder()
			.chatMemoryRepository(new InMemoryChatMemoryRepository())
			.build();

		// Create advisor with the conversation ID
		var advisor = createAdvisor(chatMemory);

		ChatClient chatClient = ChatClient.builder(this.chatModel).defaultAdvisors(advisor).build();

		// Act - Send a message using streaming
		String initialQuestion = "My name is David and I live in Seattle.";

		// Collect all streaming chunks
		List<String> streamingChunks = new ArrayList<>();
		Flux<String> responseStream = chatClient.prompt()
			.user(initialQuestion)
			.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
			.stream()
			.content();

		// Block and collect all streaming chunks
		responseStream.doOnNext(streamingChunks::add).blockLast();

		// Join all chunks to get the complete response
		String completeResponse = String.join("", streamingChunks);

		logger.info("Streaming response: {}", completeResponse);

		// Verify memory contains the initial question and the response
		List<Message> memoryMessages = chatMemory.get(conversationId);
		assertThat(memoryMessages).hasSize(2); // 1 user message + 1 assistant response
		assertThat(memoryMessages.get(0).getText()).isEqualTo(initialQuestion);

		// Send a follow-up question using streaming
		String followUpQuestion = "Where do I live?";

		// Collect all streaming chunks for the follow-up
		List<String> followUpStreamingChunks = new ArrayList<>();
		Flux<String> followUpResponseStream = chatClient.prompt()
			.user(followUpQuestion)
			.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
			.stream()
			.content();

		// Block and collect all streaming chunks
		followUpResponseStream.doOnNext(followUpStreamingChunks::add).blockLast();

		// Join all chunks to get the complete follow-up response
		String followUpCompleteResponse = String.join("", followUpStreamingChunks);

		logger.info("Follow-up streaming response: {}", followUpCompleteResponse);

		// Verify the follow-up response contains the location
		assertThat(followUpCompleteResponse).containsIgnoringCase("Seattle");

		// Verify memory now contains all messages
		memoryMessages = chatMemory.get(conversationId);
		assertThat(memoryMessages).hasSize(4); // 2 user messages + 2 assistant responses
		assertThat(memoryMessages.get(0).getText()).isEqualTo(initialQuestion);
		assertThat(memoryMessages.get(2).getText()).isEqualTo(followUpQuestion);
	}

}
