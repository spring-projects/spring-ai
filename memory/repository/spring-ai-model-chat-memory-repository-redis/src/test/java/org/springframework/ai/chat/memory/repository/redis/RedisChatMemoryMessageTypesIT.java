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
package org.springframework.ai.chat.memory.repository.redis;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import redis.clients.jedis.JedisPooled;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for RedisChatMemoryRepository focusing on different message types.
 *
 * @author Brian Sam-Bodden
 */
@Testcontainers
class RedisChatMemoryMessageTypesIT {

	@Container
	static RedisContainer redisContainer = new RedisContainer("redis/redis-stack:latest");

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestApplication.class);

	private RedisChatMemoryRepository chatMemory;

	private JedisPooled jedisClient;

	@BeforeEach
	void setUp() {
		jedisClient = new JedisPooled(redisContainer.getHost(), redisContainer.getFirstMappedPort());
		chatMemory = RedisChatMemoryRepository.builder()
			.jedisClient(jedisClient)
			.indexName("test-" + RedisChatMemoryConfig.DEFAULT_INDEX_NAME)
			.build();

		chatMemory.clear("test-conversation");
	}

	@AfterEach
	void tearDown() {
		if (jedisClient != null) {
			jedisClient.close();
		}
	}

	@Test
	void shouldHandleAllMessageTypes() {
		this.contextRunner.run(context -> {
			String conversationId = "test-conversation";

			// Create messages of different types with various content
			SystemMessage systemMessage = new SystemMessage("You are a helpful assistant");
			UserMessage userMessage = new UserMessage("What's the capital of France?");
			AssistantMessage assistantMessage = new AssistantMessage("The capital of France is Paris.");

			// Store each message type
			chatMemory.add(conversationId, systemMessage);
			chatMemory.add(conversationId, userMessage);
			chatMemory.add(conversationId, assistantMessage);

			// Retrieve and verify messages
			List<Message> messages = chatMemory.get(conversationId, 10);

			// Verify correct number of messages
			assertThat(messages).hasSize(3);

			// Verify message order and content
			assertThat(messages.get(0).getText()).isEqualTo("You are a helpful assistant");
			assertThat(messages.get(1).getText()).isEqualTo("What's the capital of France?");
			assertThat(messages.get(2).getText()).isEqualTo("The capital of France is Paris.");

			// Verify message types
			assertThat(messages.get(0)).isInstanceOf(SystemMessage.class);
			assertThat(messages.get(1)).isInstanceOf(UserMessage.class);
			assertThat(messages.get(2)).isInstanceOf(AssistantMessage.class);
		});
	}

	@ParameterizedTest
	@CsvSource({ "Message from assistant,ASSISTANT", "Message from user,USER", "Message from system,SYSTEM" })
	void shouldStoreAndRetrieveSingleMessage(String content, MessageType messageType) {
		this.contextRunner.run(context -> {
			String conversationId = UUID.randomUUID().toString();

			// Create a message of the specified type
			Message message = switch (messageType) {
				case ASSISTANT -> new AssistantMessage(content + " - " + conversationId);
				case USER -> new UserMessage(content + " - " + conversationId);
				case SYSTEM -> new SystemMessage(content + " - " + conversationId);
				default -> throw new IllegalArgumentException("Type not supported: " + messageType);
			};

			// Store the message
			chatMemory.add(conversationId, message);

			// Retrieve messages
			List<Message> messages = chatMemory.get(conversationId, 10);

			// Verify message was stored and retrieved correctly
			assertThat(messages).hasSize(1);
			Message retrievedMessage = messages.get(0);

			// Verify the message type
			assertThat(retrievedMessage.getMessageType()).isEqualTo(messageType);

			// Verify the content
			assertThat(retrievedMessage.getText()).isEqualTo(content + " - " + conversationId);

			// Verify the correct class type
			switch (messageType) {
				case ASSISTANT -> assertThat(retrievedMessage).isInstanceOf(AssistantMessage.class);
				case USER -> assertThat(retrievedMessage).isInstanceOf(UserMessage.class);
				case SYSTEM -> assertThat(retrievedMessage).isInstanceOf(SystemMessage.class);
				default -> throw new IllegalArgumentException("Type not supported: " + messageType);
			}
		});
	}

	@Test
	void shouldHandleSystemMessageWithMetadata() {
		this.contextRunner.run(context -> {
			String conversationId = "test-conversation-system";

			// Create a System message with metadata using builder
			SystemMessage systemMessage = SystemMessage.builder()
				.text("You are a specialized AI assistant for legal questions")
				.metadata(Map.of("domain", "legal", "version", "2.0", "restricted", "true"))
				.build();

			// Store the message
			chatMemory.add(conversationId, systemMessage);

			// Retrieve messages
			List<Message> messages = chatMemory.get(conversationId, 10);

			// Verify message count
			assertThat(messages).hasSize(1);
			assertThat(messages.get(0)).isInstanceOf(SystemMessage.class);

			// Verify content
			SystemMessage retrievedMessage = (SystemMessage) messages.get(0);
			assertThat(retrievedMessage.getText()).isEqualTo("You are a specialized AI assistant for legal questions");

			// Verify metadata is preserved
			assertThat(retrievedMessage.getMetadata()).containsEntry("domain", "legal");
			assertThat(retrievedMessage.getMetadata()).containsEntry("version", "2.0");
			assertThat(retrievedMessage.getMetadata()).containsEntry("restricted", "true");
		});
	}

	@Test
	void shouldHandleMultipleSystemMessages() {
		this.contextRunner.run(context -> {
			String conversationId = "multi-system-test";

			// Create multiple system messages with different content
			SystemMessage systemMessage1 = new SystemMessage("You are a helpful assistant");
			SystemMessage systemMessage2 = new SystemMessage("Always provide concise answers");
			SystemMessage systemMessage3 = new SystemMessage("Do not share personal information");

			// Create a batch of system messages
			List<Message> systemMessages = List.of(systemMessage1, systemMessage2, systemMessage3);

			// Store all messages at once
			chatMemory.add(conversationId, systemMessages);

			// Retrieve messages
			List<Message> retrievedMessages = chatMemory.get(conversationId, 10);

			// Verify all messages were stored and retrieved
			assertThat(retrievedMessages).hasSize(3);
			retrievedMessages.forEach(message -> assertThat(message).isInstanceOf(SystemMessage.class));

			// Verify content
			assertThat(retrievedMessages.get(0).getText()).isEqualTo(systemMessage1.getText());
			assertThat(retrievedMessages.get(1).getText()).isEqualTo(systemMessage2.getText());
			assertThat(retrievedMessages.get(2).getText()).isEqualTo(systemMessage3.getText());
		});
	}

	@Test
	void shouldHandleMessageWithMetadata() {
		this.contextRunner.run(context -> {
			String conversationId = "test-conversation";

			// Create messages with metadata using builder
			UserMessage userMessage = UserMessage.builder()
				.text("Hello with metadata")
				.metadata(Map.of("source", "web", "user_id", "12345"))
				.build();

			AssistantMessage assistantMessage = AssistantMessage.builder()
				.content("Hi there!")
				.properties(Map.of("model", "gpt-4", "temperature", "0.7"))
				.build();

			// Store messages with metadata
			chatMemory.add(conversationId, userMessage);
			chatMemory.add(conversationId, assistantMessage);

			// Retrieve messages
			List<Message> messages = chatMemory.get(conversationId, 10);

			// Verify message count
			assertThat(messages).hasSize(2);

			// Verify metadata is preserved
			assertThat(messages.get(0).getMetadata()).containsEntry("source", "web");
			assertThat(messages.get(0).getMetadata()).containsEntry("user_id", "12345");
			assertThat(messages.get(1).getMetadata()).containsEntry("model", "gpt-4");
			assertThat(messages.get(1).getMetadata()).containsEntry("temperature", "0.7");
		});
	}

	@ParameterizedTest
	@CsvSource({ "ASSISTANT,model=gpt-4;temperature=0.7;api_version=1.0", "USER,source=web;user_id=12345;client=mobile",
			"SYSTEM,domain=legal;version=2.0;restricted=true" })
	void shouldStoreAndRetrieveMessageWithMetadata(MessageType messageType, String metadataString) {
		this.contextRunner.run(context -> {
			String conversationId = UUID.randomUUID().toString();
			String content = "Message with metadata - " + messageType;

			// Parse metadata from string
			Map<String, Object> metadata = parseMetadata(metadataString);

			// Create a message with metadata
			Message message = switch (messageType) {
				case ASSISTANT -> AssistantMessage.builder().content(content).properties(metadata).build();
				case USER -> UserMessage.builder().text(content).metadata(metadata).build();
				case SYSTEM -> SystemMessage.builder().text(content).metadata(metadata).build();
				default -> throw new IllegalArgumentException("Type not supported: " + messageType);
			};

			// Store the message
			chatMemory.add(conversationId, message);

			// Retrieve the message
			List<Message> messages = chatMemory.get(conversationId, 10);

			// Verify message was stored correctly
			assertThat(messages).hasSize(1);
			Message retrievedMessage = messages.get(0);

			// Verify message type
			assertThat(retrievedMessage.getMessageType()).isEqualTo(messageType);

			// Verify all metadata entries are present
			metadata.forEach((key, value) -> assertThat(retrievedMessage.getMetadata()).containsEntry(key, value));
		});
	}

	// Helper method to parse metadata from string in format
	// "key1=value1;key2=value2;key3=value3"
	private Map<String, Object> parseMetadata(String metadataString) {
		Map<String, Object> metadata = new HashMap<>();
		String[] pairs = metadataString.split(";");

		for (String pair : pairs) {
			String[] keyValue = pair.split("=");
			if (keyValue.length == 2) {
				metadata.put(keyValue[0], keyValue[1]);
			}
		}

		return metadata;
	}

	@Test
	void shouldHandleAssistantMessageWithToolCalls() {
		this.contextRunner.run(context -> {
			String conversationId = "test-conversation";

			// Create an AssistantMessage with tool calls
			List<AssistantMessage.ToolCall> toolCalls = Arrays.asList(
					new AssistantMessage.ToolCall("tool-1", "function", "weather", "{\"location\": \"Paris\"}"),
					new AssistantMessage.ToolCall("tool-2", "function", "calculator",
							"{\"operation\": \"add\", \"args\": [1, 2]}"));

			AssistantMessage assistantMessage = AssistantMessage.builder()
				.content("I'll check that for you.")
				.properties(Map.of("model", "gpt-4"))
				.toolCalls(toolCalls)
				.media(List.of())
				.build();

			// Store message with tool calls
			chatMemory.add(conversationId, assistantMessage);

			// Retrieve the message
			List<Message> messages = chatMemory.get(conversationId, 10);

			// Verify we get back the same type of message
			assertThat(messages).hasSize(1);
			assertThat(messages.get(0)).isInstanceOf(AssistantMessage.class);

			// Cast and verify tool calls
			AssistantMessage retrievedMessage = (AssistantMessage) messages.get(0);
			assertThat(retrievedMessage.getToolCalls()).hasSize(2);

			// Verify tool call content
			AssistantMessage.ToolCall firstToolCall = retrievedMessage.getToolCalls().get(0);
			assertThat(firstToolCall.name()).isEqualTo("weather");
			assertThat(firstToolCall.arguments()).isEqualTo("{\"location\": \"Paris\"}");

			AssistantMessage.ToolCall secondToolCall = retrievedMessage.getToolCalls().get(1);
			assertThat(secondToolCall.name()).isEqualTo("calculator");
			assertThat(secondToolCall.arguments()).contains("\"operation\": \"add\"");
		});
	}

	@Test
	void shouldHandleBasicToolResponseMessage() {
		this.contextRunner.run(context -> {
			String conversationId = "tool-response-conversation";

			// Create a simple ToolResponseMessage with a single tool response
			ToolResponseMessage.ToolResponse weatherResponse = new ToolResponseMessage.ToolResponse("tool-1", "weather",
					"{\"location\":\"Paris\",\"temperature\":\"22°C\",\"conditions\":\"Partly Cloudy\"}");

			// Create the message with a single tool response
			ToolResponseMessage toolResponseMessage = ToolResponseMessage.builder()
				.responses(List.of(weatherResponse))
				.build();

			// Store the message
			chatMemory.add(conversationId, toolResponseMessage);

			// Retrieve the message
			List<Message> messages = chatMemory.get(conversationId, 10);

			// Verify we get back the correct message
			assertThat(messages).hasSize(1);
			assertThat(messages.get(0)).isInstanceOf(ToolResponseMessage.class);
			assertThat(messages.get(0).getMessageType()).isEqualTo(MessageType.TOOL);

			// Cast and verify tool responses
			ToolResponseMessage retrievedMessage = (ToolResponseMessage) messages.get(0);
			List<ToolResponseMessage.ToolResponse> toolResponses = retrievedMessage.getResponses();

			// Verify tool response content
			assertThat(toolResponses).hasSize(1);
			ToolResponseMessage.ToolResponse response = toolResponses.get(0);
			assertThat(response.id()).isEqualTo("tool-1");
			assertThat(response.name()).isEqualTo("weather");
			assertThat(response.responseData()).contains("Paris");
			assertThat(response.responseData()).contains("22°C");
		});
	}

	@Test
	void shouldHandleToolResponseMessageWithMultipleResponses() {
		this.contextRunner.run(context -> {
			String conversationId = "multi-tool-response-conversation";

			// Create multiple tool responses
			ToolResponseMessage.ToolResponse weatherResponse = new ToolResponseMessage.ToolResponse("tool-1", "weather",
					"{\"location\":\"Paris\",\"temperature\":\"22°C\",\"conditions\":\"Partly Cloudy\"}");

			ToolResponseMessage.ToolResponse calculatorResponse = new ToolResponseMessage.ToolResponse("tool-2",
					"calculator", "{\"operation\":\"add\",\"args\":[1,2],\"result\":3}");

			ToolResponseMessage.ToolResponse databaseResponse = new ToolResponseMessage.ToolResponse("tool-3",
					"database", "{\"query\":\"SELECT * FROM users\",\"count\":42}");

			// Create the message with multiple tool responses and metadata
			ToolResponseMessage toolResponseMessage = ToolResponseMessage.builder()
				.responses(List.of(weatherResponse, calculatorResponse, databaseResponse))
				.metadata(Map.of("source", "tools-api", "version", "1.0"))
				.build();

			// Store the message
			chatMemory.add(conversationId, toolResponseMessage);

			// Retrieve the message
			List<Message> messages = chatMemory.get(conversationId, 10);

			// Verify message type and count
			assertThat(messages).hasSize(1);
			assertThat(messages.get(0)).isInstanceOf(ToolResponseMessage.class);

			// Cast and verify
			ToolResponseMessage retrievedMessage = (ToolResponseMessage) messages.get(0);

			// Verify metadata
			assertThat(retrievedMessage.getMetadata()).containsEntry("source", "tools-api");
			assertThat(retrievedMessage.getMetadata()).containsEntry("version", "1.0");

			// Verify tool responses
			List<ToolResponseMessage.ToolResponse> toolResponses = retrievedMessage.getResponses();
			assertThat(toolResponses).hasSize(3);

			// Verify first response (weather)
			ToolResponseMessage.ToolResponse response1 = toolResponses.get(0);
			assertThat(response1.id()).isEqualTo("tool-1");
			assertThat(response1.name()).isEqualTo("weather");
			assertThat(response1.responseData()).contains("Paris");

			// Verify second response (calculator)
			ToolResponseMessage.ToolResponse response2 = toolResponses.get(1);
			assertThat(response2.id()).isEqualTo("tool-2");
			assertThat(response2.name()).isEqualTo("calculator");
			assertThat(response2.responseData()).contains("result");

			// Verify third response (database)
			ToolResponseMessage.ToolResponse response3 = toolResponses.get(2);
			assertThat(response3.id()).isEqualTo("tool-3");
			assertThat(response3.name()).isEqualTo("database");
			assertThat(response3.responseData()).contains("count");
		});
	}

	@Test
	void shouldHandleToolResponseInConversationFlow() {
		this.contextRunner.run(context -> {
			String conversationId = "tool-conversation-flow";

			// Create a typical conversation flow with tool responses
			UserMessage userMessage = new UserMessage("What's the weather in Paris?");

			// Assistant requests weather information via tool
			List<AssistantMessage.ToolCall> toolCalls = List
				.of(new AssistantMessage.ToolCall("weather-req-1", "function", "weather", "{\"location\":\"Paris\"}"));
			AssistantMessage assistantMessage = AssistantMessage.builder()
				.content("I'll check the weather for you.")
				.properties(Map.of())
				.toolCalls(toolCalls)
				.media(List.of())
				.build();

			// Tool provides weather information
			ToolResponseMessage.ToolResponse weatherResponse = new ToolResponseMessage.ToolResponse("weather-req-1",
					"weather", "{\"location\":\"Paris\",\"temperature\":\"22°C\",\"conditions\":\"Partly Cloudy\"}");
			ToolResponseMessage toolResponseMessage = ToolResponseMessage.builder()
				.responses(List.of(weatherResponse))
				.build();

			// Assistant summarizes the information
			AssistantMessage finalResponse = new AssistantMessage(
					"The current weather in Paris is 22°C and partly cloudy.");

			// Store the conversation
			List<Message> conversation = List.of(userMessage, assistantMessage, toolResponseMessage, finalResponse);
			chatMemory.add(conversationId, conversation);

			// Retrieve the conversation
			List<Message> messages = chatMemory.get(conversationId, 10);

			// Verify the conversation flow
			assertThat(messages).hasSize(4);
			assertThat(messages.get(0)).isInstanceOf(UserMessage.class);
			assertThat(messages.get(1)).isInstanceOf(AssistantMessage.class);
			assertThat(messages.get(2)).isInstanceOf(ToolResponseMessage.class);
			assertThat(messages.get(3)).isInstanceOf(AssistantMessage.class);

			// Verify the tool response
			ToolResponseMessage retrievedToolResponse = (ToolResponseMessage) messages.get(2);
			assertThat(retrievedToolResponse.getResponses()).hasSize(1);
			assertThat(retrievedToolResponse.getResponses().get(0).name()).isEqualTo("weather");
			assertThat(retrievedToolResponse.getResponses().get(0).responseData()).contains("Paris");

			// Verify the final response includes information from the tool
			AssistantMessage retrievedFinalResponse = (AssistantMessage) messages.get(3);
			assertThat(retrievedFinalResponse.getText()).contains("22°C");
			assertThat(retrievedFinalResponse.getText()).contains("partly cloudy");
		});
	}

	@Test
	void getMessages_withAllMessageTypes_shouldPreserveMessageOrder() {
		this.contextRunner.run(context -> {
			String conversationId = "complex-order-test";

			// Create a complex conversation with all message types in a specific order
			SystemMessage systemMessage = new SystemMessage("You are a helpful AI assistant.");
			UserMessage userMessage1 = new UserMessage("What's the capital of France?");
			AssistantMessage assistantMessage1 = new AssistantMessage("The capital of France is Paris.");
			UserMessage userMessage2 = new UserMessage("What's the weather there?");

			// Assistant using tool to check weather
			List<AssistantMessage.ToolCall> toolCalls = List
				.of(new AssistantMessage.ToolCall("weather-tool-1", "function", "weather", "{\"location\":\"Paris\"}"));
			AssistantMessage assistantToolCall = AssistantMessage.builder()
				.content("I'll check the weather in Paris for you.")
				.properties(Map.of())
				.toolCalls(toolCalls)
				.media(List.of())
				.build();

			// Tool response
			ToolResponseMessage.ToolResponse weatherResponse = new ToolResponseMessage.ToolResponse("weather-tool-1",
					"weather", "{\"location\":\"Paris\",\"temperature\":\"24°C\",\"conditions\":\"Sunny\"}");
			ToolResponseMessage toolResponseMessage = ToolResponseMessage.builder()
				.responses(List.of(weatherResponse))
				.build();

			// Final assistant response using the tool information
			AssistantMessage assistantFinal = new AssistantMessage("The weather in Paris is currently 24°C and sunny.");

			// Create ordered list of messages
			List<Message> expectedMessages = List.of(systemMessage, userMessage1, assistantMessage1, userMessage2,
					assistantToolCall, toolResponseMessage, assistantFinal);

			// Add each message individually with small delays
			for (Message message : expectedMessages) {
				chatMemory.add(conversationId, message);
				Thread.sleep(10); // Small delay to ensure distinct timestamps
			}

			// Retrieve and verify messages
			List<Message> retrievedMessages = chatMemory.get(conversationId, 10);

			// Check the total count matches
			assertThat(retrievedMessages).hasSize(expectedMessages.size());

			// Check each message is in the expected order
			for (int i = 0; i < expectedMessages.size(); i++) {
				Message expected = expectedMessages.get(i);
				Message actual = retrievedMessages.get(i);

				// Verify message types match
				assertThat(actual.getMessageType()).isEqualTo(expected.getMessageType());

				// Verify message content matches
				assertThat(actual.getText()).isEqualTo(expected.getText());

				// For each specific message type, verify type-specific properties
				if (expected instanceof SystemMessage) {
					assertThat(actual).isInstanceOf(SystemMessage.class);
				}
				else if (expected instanceof UserMessage) {
					assertThat(actual).isInstanceOf(UserMessage.class);
				}
				else if (expected instanceof AssistantMessage) {
					assertThat(actual).isInstanceOf(AssistantMessage.class);

					// If the original had tool calls, verify they're preserved
					if (((AssistantMessage) expected).hasToolCalls()) {
						AssistantMessage expectedAssistant = (AssistantMessage) expected;
						AssistantMessage actualAssistant = (AssistantMessage) actual;

						assertThat(actualAssistant.hasToolCalls()).isTrue();
						assertThat(actualAssistant.getToolCalls()).hasSameSizeAs(expectedAssistant.getToolCalls());

						// Check first tool call details
						assertThat(actualAssistant.getToolCalls().get(0).name())
							.isEqualTo(expectedAssistant.getToolCalls().get(0).name());
					}
				}
				else if (expected instanceof ToolResponseMessage) {
					assertThat(actual).isInstanceOf(ToolResponseMessage.class);

					ToolResponseMessage expectedTool = (ToolResponseMessage) expected;
					ToolResponseMessage actualTool = (ToolResponseMessage) actual;

					assertThat(actualTool.getResponses()).hasSameSizeAs(expectedTool.getResponses());

					// Check response details
					assertThat(actualTool.getResponses().get(0).name())
						.isEqualTo(expectedTool.getResponses().get(0).name());
					assertThat(actualTool.getResponses().get(0).id())
						.isEqualTo(expectedTool.getResponses().get(0).id());
				}
			}
		});
	}

	@Test
	void getMessages_afterMultipleAdds_shouldReturnMessagesInCorrectOrder() {
		this.contextRunner.run(context -> {
			String conversationId = "sequential-adds-test";

			// Create messages that will be added individually
			UserMessage userMessage1 = new UserMessage("First user message");
			AssistantMessage assistantMessage1 = new AssistantMessage("First assistant response");
			UserMessage userMessage2 = new UserMessage("Second user message");
			AssistantMessage assistantMessage2 = new AssistantMessage("Second assistant response");
			UserMessage userMessage3 = new UserMessage("Third user message");
			AssistantMessage assistantMessage3 = new AssistantMessage("Third assistant response");

			// Add messages one at a time with delays to simulate real conversation
			chatMemory.add(conversationId, userMessage1);
			Thread.sleep(50);
			chatMemory.add(conversationId, assistantMessage1);
			Thread.sleep(50);
			chatMemory.add(conversationId, userMessage2);
			Thread.sleep(50);
			chatMemory.add(conversationId, assistantMessage2);
			Thread.sleep(50);
			chatMemory.add(conversationId, userMessage3);
			Thread.sleep(50);
			chatMemory.add(conversationId, assistantMessage3);

			// Create the expected message order
			List<Message> expectedMessages = List.of(userMessage1, assistantMessage1, userMessage2, assistantMessage2,
					userMessage3, assistantMessage3);

			// Retrieve all messages
			List<Message> retrievedMessages = chatMemory.get(conversationId, 10);

			// Check count matches
			assertThat(retrievedMessages).hasSize(expectedMessages.size());

			// Verify each message is in the correct order with correct content
			for (int i = 0; i < expectedMessages.size(); i++) {
				Message expected = expectedMessages.get(i);
				Message actual = retrievedMessages.get(i);

				assertThat(actual.getMessageType()).isEqualTo(expected.getMessageType());
				assertThat(actual.getText()).isEqualTo(expected.getText());
			}

			// Test with a limit
			List<Message> limitedMessages = chatMemory.get(conversationId, 3);

			// Should get the 3 oldest messages
			assertThat(limitedMessages).hasSize(3);
			assertThat(limitedMessages.get(0).getText()).isEqualTo(userMessage1.getText());
			assertThat(limitedMessages.get(1).getText()).isEqualTo(assistantMessage1.getText());
			assertThat(limitedMessages.get(2).getText()).isEqualTo(userMessage2.getText());
		});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration(exclude = { DataSourceAutoConfiguration.class })
	static class TestApplication {

		@Bean
		RedisChatMemoryRepository chatMemory() {
			return RedisChatMemoryRepository.builder()
				.jedisClient(new JedisPooled(redisContainer.getHost(), redisContainer.getFirstMappedPort()))
				.indexName("test-" + RedisChatMemoryConfig.DEFAULT_INDEX_NAME)
				.build();
		}

	}

}
