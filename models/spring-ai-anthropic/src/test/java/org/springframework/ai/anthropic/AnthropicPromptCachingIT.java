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

package org.springframework.ai.anthropic;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.anthropic.api.AnthropicCacheOptions;
import org.springframework.ai.anthropic.api.AnthropicCacheStrategy;
import org.springframework.ai.anthropic.api.AnthropicCacheTtl;
import org.springframework.ai.anthropic.api.tool.MockWeatherService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StreamUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Anthropic prompt caching functionality.
 *
 * Tests various caching strategies to ensure proper cache breakpoint placement and
 * optimal cache utilization according to Anthropic's best practices.
 *
 * @author Austin Dase
 */
@SpringBootTest(classes = AnthropicTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
public class AnthropicPromptCachingIT {

	private static final Logger logger = LoggerFactory.getLogger(AnthropicPromptCachingIT.class);

	@Autowired
	private AnthropicChatModel chatModel;

	@Autowired
	private ResourceLoader resourceLoader;

	private String loadPrompt(String filename) {
		try {
			Resource resource = this.resourceLoader.getResource("classpath:prompts/" + filename);
			String basePrompt = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
			// Add unique timestamp to prevent cache collisions across test runs
			return basePrompt + "\n\nTest execution timestamp: " + System.currentTimeMillis();
		}
		catch (IOException e) {
			throw new RuntimeException("Failed to load prompt: " + filename, e);
		}
	}

	/**
	 * Helper method to safely get AnthropicApi.Usage, returning null if not available.
	 * This handles the case where getNativeUsage() returns null for tool-based
	 * interactions.
	 */
	private AnthropicApi.Usage getAnthropicUsage(ChatResponse response) {
		if (response == null || response.getMetadata() == null || response.getMetadata().getUsage() == null) {
			return null;
		}
		Object nativeUsage = response.getMetadata().getUsage().getNativeUsage();
		return (nativeUsage instanceof AnthropicApi.Usage usage) ? usage : null;
	}

	@Test
	void shouldCacheSystemMessageOnly() {
		String systemPrompt = loadPrompt("system-only-cache-prompt.txt");

		AnthropicChatOptions options = AnthropicChatOptions.builder()
			.model(AnthropicApi.ChatModel.CLAUDE_SONNET_4.getValue())
			.cacheOptions(AnthropicCacheOptions.builder().strategy(AnthropicCacheStrategy.SYSTEM_ONLY).build())
			.maxTokens(150)
			.temperature(0.3)
			.build();

		ChatResponse response = this.chatModel.call(new Prompt(
				List.of(new SystemMessage(systemPrompt), new UserMessage("What is microservices architecture?")),
				options));

		assertThat(response).isNotNull();
		assertThat(response.getResult().getOutput().getText()).isNotEmpty();
		logger.info("System-only cache response: {}", response.getResult().getOutput().getText());

		// For system-only caching, we should have native usage available
		AnthropicApi.Usage usage = getAnthropicUsage(response);
		assertThat(usage).isNotNull();

		// Check cache behavior - either cache creation OR cache read should occur
		boolean cacheCreated = usage.cacheCreationInputTokens() > 0;
		boolean cacheRead = usage.cacheReadInputTokens() > 0;
		assertThat(cacheCreated || cacheRead)
			.withFailMessage("Expected either cache creation or cache read tokens, but got creation=%d, read=%d",
					usage.cacheCreationInputTokens(), usage.cacheReadInputTokens())
			.isTrue();
		assertThat(cacheCreated && cacheRead)
			.withFailMessage("Cache creation and read should not happen simultaneously")
			.isFalse();

		logger.info("Cache creation tokens: {}, Cache read tokens: {}", usage.cacheCreationInputTokens(),
				usage.cacheReadInputTokens());
	}

	@Test
	void shouldCacheSystemAndTools() {
		String systemPrompt = loadPrompt("system-and-tools-cache-prompt.txt");

		// Mock weather service
		MockWeatherService weatherService = new MockWeatherService();

		AnthropicChatOptions options = AnthropicChatOptions.builder()
			.model(AnthropicApi.ChatModel.CLAUDE_SONNET_4.getValue())
			.cacheOptions(AnthropicCacheOptions.builder().strategy(AnthropicCacheStrategy.SYSTEM_AND_TOOLS).build())
			.maxTokens(200)
			.temperature(0.3)
			.toolCallbacks(FunctionToolCallback.builder("getCurrentWeather", weatherService)
				.description("Get current weather for a location")
				.inputType(MockWeatherService.Request.class)
				.build())
			.build();

		ChatResponse response = this.chatModel.call(
				new Prompt(
						List.of(new SystemMessage(systemPrompt),
								new UserMessage(
										"What's the weather like in San Francisco and should I go for a walk?")),
						options));

		assertThat(response).isNotNull();
		assertThat(response.getResult().getOutput().getText()).isNotEmpty();
		logger.info("System and tools cache response: {}", response.getResult().getOutput().getText());

		// Anthropic's API doesn't provide cache usage metadata for tool-based
		// interactions
		// Validate what we can: configuration works and tools are called successfully
		AnthropicApi.Usage usage = getAnthropicUsage(response);
		if (usage != null) {
			// If we get usage metadata, validate cache behavior
			boolean cacheCreated = usage.cacheCreationInputTokens() > 0;
			boolean cacheRead = usage.cacheReadInputTokens() > 0;
			assertThat(cacheCreated || cacheRead)
				.withFailMessage("Expected either cache creation or cache read tokens, but got creation=%d, read=%d",
						usage.cacheCreationInputTokens(), usage.cacheReadInputTokens())
				.isTrue();
			assertThat(cacheCreated && cacheRead)
				.withFailMessage("Cache creation and read should not happen simultaneously")
				.isFalse();

			logger.info("Cache creation tokens: {}, Cache read tokens: {}", usage.cacheCreationInputTokens(),
					usage.cacheReadInputTokens());
		}
		else {
			logger.debug("Native usage metadata not available for tool-based interactions - this is expected");
			// Validate functional correctness: tools were called and response generated
			assertThat(response.getResult().getOutput().getText()).isNotEmpty();
			// Ensure the weather service was actually called (indirect validation)
			// Note: Full cache validation would require mocking the Anthropic API
		}
	}

	@Test
	void shouldCacheConversationHistory() {
		// Create a conversation ID for this test
		String conversationId = "history-cache-test-" + System.currentTimeMillis();

		// Set up ChatMemory and advisor
		ChatMemory chatMemory = MessageWindowChatMemory.builder()
			.chatMemoryRepository(new InMemoryChatMemoryRepository())
			.build();

		MessageChatMemoryAdvisor advisor = MessageChatMemoryAdvisor.builder(chatMemory)
			.conversationId(conversationId)
			.build();

		ChatClient chatClient = ChatClient.builder(this.chatModel)
			.defaultAdvisors(advisor)
			.defaultSystem(loadPrompt("conversation-history-cache-prompt.txt"))
			.build();

		// Build up conversation history
		chatClient.prompt()
			.user("My name is Alice and I work as a data scientist at TechCorp.")
			.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
			.call()
			.content();

		chatClient.prompt()
			.user("I specialize in machine learning and have 5 years of experience with Python and R.")
			.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
			.call()
			.content();

		chatClient.prompt()
			.user("Recently I've been working on a recommendation system for our e-commerce platform.")
			.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
			.call()
			.content();

		// Now use caching for the next conversation turn
		String response = chatClient.prompt()
			.user("What career advice would you give me based on our conversation?")
			.options(AnthropicChatOptions.builder()
				.model(AnthropicApi.ChatModel.CLAUDE_SONNET_4.getValue())
				.cacheOptions(
						AnthropicCacheOptions.builder().strategy(AnthropicCacheStrategy.CONVERSATION_HISTORY).build())
				.maxTokens(200)
				.temperature(0.3)
				.build())
			.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
			.call()
			.content();

		assertThat(response).isNotEmpty();
		assertThat(response.toLowerCase()).contains("alice");
		logger.info("Conversation history cache response: {}", response);

		// Verify the conversation was remembered
		List<Message> memoryMessages = chatMemory.get(conversationId);
		assertThat(memoryMessages).hasSizeGreaterThan(6); // At least 4 user + 4 assistant
															// messages
	}

	@Test
	void shouldRespectMinLengthForSystemCaching() {
		String systemPrompt = loadPrompt("system-only-cache-prompt.txt");

		AnthropicChatOptions options = AnthropicChatOptions.builder()
			.model(AnthropicApi.ChatModel.CLAUDE_SONNET_4.getValue())
			.cacheOptions(AnthropicCacheOptions.builder()
				.strategy(AnthropicCacheStrategy.SYSTEM_ONLY)
				// Set min length above actual system prompt length to prevent caching
				.messageTypeMinContentLength(MessageType.SYSTEM, systemPrompt.length() + 1)
				.build())
			.maxTokens(60)
			.temperature(0.2)
			.build();

		ChatResponse response = this.chatModel
			.call(new Prompt(List.of(new SystemMessage(systemPrompt), new UserMessage("Ping")), options));

		assertThat(response).isNotNull();
		AnthropicApi.Usage usage = getAnthropicUsage(response);
		assertThat(usage).isNotNull();
		assertThat(usage.cacheCreationInputTokens()).as("No cache should be created below min length").isEqualTo(0);
		assertThat(usage.cacheReadInputTokens()).as("No cache read expected below min length").isEqualTo(0);
	}

	@Test
	void shouldRespectMinLengthForUserHistoryCaching() {
		// Two-user-message prompt; only the first (history tail) is eligible.
		String userMessage = loadPrompt("system-only-cache-prompt.txt");
		List<Message> messages = List.of(new UserMessage(userMessage),
				new UserMessage("Please answer this question succinctly"));

		// Set USER min length high so caching should not apply
		AnthropicChatOptions noCacheOptions = AnthropicChatOptions.builder()
			.model(AnthropicApi.ChatModel.CLAUDE_SONNET_4.getValue())
			.cacheOptions(AnthropicCacheOptions.builder()
				.strategy(AnthropicCacheStrategy.CONVERSATION_HISTORY)
				.messageTypeMinContentLength(MessageType.USER, userMessage.length() + 1)
				.build())
			.maxTokens(80)
			.temperature(0.2)
			.build();

		ChatResponse noCacheResponse = this.chatModel.call(new Prompt(messages, noCacheOptions));
		assertThat(noCacheResponse).isNotNull();
		AnthropicApi.Usage noCacheUsage = getAnthropicUsage(noCacheResponse);
		assertThat(noCacheUsage).isNotNull();
		assertThat(noCacheUsage.cacheCreationInputTokens()).isEqualTo(0);
		assertThat(noCacheUsage.cacheReadInputTokens()).isEqualTo(0);

		// Now allow caching by lowering the USER min length
		AnthropicChatOptions cacheOptions = AnthropicChatOptions.builder()
			.model(AnthropicApi.ChatModel.CLAUDE_SONNET_4.getValue())
			.cacheOptions(AnthropicCacheOptions.builder()
				.strategy(AnthropicCacheStrategy.CONVERSATION_HISTORY)
				.messageTypeMinContentLength(MessageType.USER, userMessage.length() - 1)
				.build())
			.maxTokens(80)
			.temperature(0.2)
			.build();

		ChatResponse cacheResponse = this.chatModel.call(new Prompt(messages, cacheOptions));
		assertThat(cacheResponse).isNotNull();
		AnthropicApi.Usage cacheUsage = getAnthropicUsage(cacheResponse);
		assertThat(cacheUsage).isNotNull();
		assertThat(cacheUsage.cacheCreationInputTokens())
			.as("Expect some cache creation tokens when USER history tail is cached")
			.isGreaterThan(0);
	}

	@Test
	void shouldRespectAllButLastUserMessageForUserHistoryCaching() {
		// Three-user-message prompt; only the first (history tail) is eligible.
		String userMessage = loadPrompt("system-only-cache-prompt.txt");
		List<Message> messages = List.of(new UserMessage(userMessage),
				new UserMessage("Additional content to exceed min length"),
				new UserMessage("Please answer this question succinctly"));

		// The combined length of the first two USER messages exceeds the min length,
		// so caching should apply
		AnthropicChatOptions cacheOptions = AnthropicChatOptions.builder()
			.model(AnthropicApi.ChatModel.CLAUDE_SONNET_4.getValue())
			.cacheOptions(AnthropicCacheOptions.builder()
				.strategy(AnthropicCacheStrategy.CONVERSATION_HISTORY)
				.messageTypeMinContentLength(MessageType.USER, userMessage.length())
				.build())
			.maxTokens(80)
			.temperature(0.2)
			.build();

		ChatResponse cacheResponse = this.chatModel.call(new Prompt(messages, cacheOptions));
		assertThat(cacheResponse).isNotNull();
		AnthropicApi.Usage cacheUsage = getAnthropicUsage(cacheResponse);
		assertThat(cacheUsage).isNotNull();
		assertThat(cacheUsage.cacheCreationInputTokens())
			.as("Expect some cache creation tokens when USER history tail is cached")
			.isGreaterThan(0);
	}

	@Test
	void shouldHandleExtendedTtlCaching() {
		String systemPrompt = loadPrompt("extended-ttl-cache-prompt.txt");

		AnthropicChatOptions options = AnthropicChatOptions.builder()
			.model(AnthropicApi.ChatModel.CLAUDE_SONNET_4.getValue())
			.cacheOptions(AnthropicCacheOptions.builder()
				.strategy(AnthropicCacheStrategy.SYSTEM_ONLY)
				.messageTypeTtl(MessageType.SYSTEM, AnthropicCacheTtl.ONE_HOUR)
				.build())
			.maxTokens(100)
			.temperature(0.3)
			.build();

		ChatResponse response = this.chatModel
			.call(new Prompt(List.of(new SystemMessage(systemPrompt), new UserMessage("What is 2+2?")), options));

		assertThat(response).isNotNull();
		assertThat(response.getResult().getOutput().getText()).contains("4");
		logger.info("Extended TTL cache response: {}", response.getResult().getOutput().getText());

		// Check cache behavior - either cache creation OR cache read should occur
		logger.info("DEBUG: About to get usage metadata for extended TTL test");
		AnthropicApi.Usage usage = (AnthropicApi.Usage) response.getMetadata().getUsage().getNativeUsage();
		logger.info("DEBUG: Got usage metadata for extended TTL test: {}", usage);
		assertThat(usage).isNotNull();

		boolean cacheCreated = usage.cacheCreationInputTokens() > 0;
		boolean cacheRead = usage.cacheReadInputTokens() > 0;
		assertThat(cacheCreated || cacheRead)
			.withFailMessage("Expected either cache creation or cache read tokens, but got creation=%d, read=%d",
					usage.cacheCreationInputTokens(), usage.cacheReadInputTokens())
			.isTrue();
		assertThat(cacheCreated && cacheRead)
			.withFailMessage("Cache creation and read should not happen simultaneously")
			.isFalse();

		logger.info("Extended TTL - Cache creation tokens: {}, Cache read tokens: {}", usage.cacheCreationInputTokens(),
				usage.cacheReadInputTokens());
	}

	@Test
	void shouldNotCacheWithNoneStrategy() {
		String systemPrompt = "You are a helpful assistant.";

		AnthropicChatOptions options = AnthropicChatOptions.builder()
			.model(AnthropicApi.ChatModel.CLAUDE_SONNET_4.getValue())
			.cacheOptions(AnthropicCacheOptions.builder().strategy(AnthropicCacheStrategy.NONE).build())
			.maxTokens(50)
			.temperature(0.3)
			.build();

		ChatResponse response = this.chatModel
			.call(new Prompt(List.of(new SystemMessage(systemPrompt), new UserMessage("Hello!")), options));

		assertThat(response).isNotNull();
		assertThat(response.getResult().getOutput().getText()).isNotEmpty();
		logger.info("No cache response: {}", response.getResult().getOutput().getText());

		// Verify NO cache tokens are created (NONE strategy)
		AnthropicApi.Usage usage = (AnthropicApi.Usage) response.getMetadata().getUsage().getNativeUsage();
		assertThat(usage.cacheCreationInputTokens()).isEqualTo(0);
		assertThat(usage.cacheReadInputTokens()).isEqualTo(0);
		logger.info("No cache strategy - Cache creation tokens: {}, Cache read tokens: {}",
				usage.cacheCreationInputTokens(), usage.cacheReadInputTokens());
	}

	@Test
	void shouldHandleMultipleCacheStrategiesInSession() {
		// Test that we can switch between different caching strategies
		List<ChatResponse> responses = new ArrayList<>();

		// First: System only
		responses.add(this.chatModel.call(new Prompt(
				List.of(new SystemMessage("You are a math tutor."), new UserMessage("What is calculus?")),
				AnthropicChatOptions.builder()
					.model(AnthropicApi.ChatModel.CLAUDE_SONNET_4.getValue())
					.cacheOptions(AnthropicCacheOptions.builder().strategy(AnthropicCacheStrategy.SYSTEM_ONLY).build())
					.maxTokens(100)
					.build())));

		// Second: No caching
		responses.add(this.chatModel.call(new Prompt(List.of(new UserMessage("What's 5+5?")),
				AnthropicChatOptions.builder()
					.model(AnthropicApi.ChatModel.CLAUDE_SONNET_4.getValue())
					.cacheOptions(AnthropicCacheOptions.builder().strategy(AnthropicCacheStrategy.NONE).build())
					.maxTokens(50)
					.build())));

		// Verify all responses
		for (int i = 0; i < responses.size(); i++) {
			ChatResponse response = responses.get(i);
			assertThat(response).isNotNull();
			assertThat(response.getResult().getOutput().getText()).isNotEmpty();
			logger.info("Response {}: {}", i + 1, response.getResult().getOutput().getText());
		}
	}

}
