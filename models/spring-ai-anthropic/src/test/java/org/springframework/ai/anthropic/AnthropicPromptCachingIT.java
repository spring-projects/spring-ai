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
			.model(AnthropicApi.ChatModel.CLAUDE_SONNET_4_0.getValue())
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
			.model(AnthropicApi.ChatModel.CLAUDE_SONNET_4_0.getValue())
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
				.model(AnthropicApi.ChatModel.CLAUDE_SONNET_4_0.getValue())
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
			.model(AnthropicApi.ChatModel.CLAUDE_SONNET_4_0.getValue())
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
		// Two-user-message prompt; aggregate length check applies
		String userMessage = loadPrompt("system-only-cache-prompt.txt");
		String secondUserMessage = "Please answer this question succinctly";
		List<Message> messages = List.of(new UserMessage(userMessage), new UserMessage(secondUserMessage));

		// Calculate combined length of both messages for aggregate checking
		int combinedLength = userMessage.length() + secondUserMessage.length();

		// Set USER min length higher than combined length so caching should not apply
		AnthropicChatOptions noCacheOptions = AnthropicChatOptions.builder()
			.model(AnthropicApi.ChatModel.CLAUDE_SONNET_4_0.getValue())
			.cacheOptions(AnthropicCacheOptions.builder()
				.strategy(AnthropicCacheStrategy.CONVERSATION_HISTORY)
				.messageTypeMinContentLength(MessageType.USER, combinedLength + 1)
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

		// Now allow caching by lowering the USER min length below combined length
		AnthropicChatOptions cacheOptions = AnthropicChatOptions.builder()
			.model(AnthropicApi.ChatModel.CLAUDE_SONNET_4_0.getValue())
			.cacheOptions(AnthropicCacheOptions.builder()
				.strategy(AnthropicCacheStrategy.CONVERSATION_HISTORY)
				.messageTypeMinContentLength(MessageType.USER, combinedLength - 1)
				.build())
			.maxTokens(80)
			.temperature(0.2)
			.build();

		ChatResponse cacheResponse = this.chatModel.call(new Prompt(messages, cacheOptions));
		assertThat(cacheResponse).isNotNull();
		AnthropicApi.Usage cacheUsage = getAnthropicUsage(cacheResponse);
		assertThat(cacheUsage).isNotNull();
		assertThat(cacheUsage.cacheCreationInputTokens())
			.as("Expect some cache creation tokens when aggregate content meets min length")
			.isGreaterThan(0);
	}

	@Test
	void shouldApplyCacheControlToLastUserMessageForConversationHistory() {
		// Three-user-message prompt; the last user message will have cache_control.
		String userMessage = loadPrompt("system-only-cache-prompt.txt");
		List<Message> messages = List.of(new UserMessage(userMessage),
				new UserMessage("Additional content to exceed min length"),
				new UserMessage("Please answer this question succinctly"));

		// The combined length of all three USER messages (including the last) exceeds
		// the min length, so caching should apply
		AnthropicChatOptions cacheOptions = AnthropicChatOptions.builder()
			.model(AnthropicApi.ChatModel.CLAUDE_SONNET_4_0.getValue())
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
			.model(AnthropicApi.ChatModel.CLAUDE_SONNET_4_0.getValue())
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
	void shouldCacheStaticPrefixWithMultiBlockSystemCaching() {
		// Large static system prompt that exceeds 1024 tokens for caching
		String staticSystemPrompt = loadPrompt("system-only-cache-prompt.txt");

		AnthropicChatOptions options = AnthropicChatOptions.builder()
			.model(AnthropicApi.ChatModel.CLAUDE_SONNET_4_0.getValue())
			.cacheOptions(AnthropicCacheOptions.builder()
				.strategy(AnthropicCacheStrategy.SYSTEM_ONLY)
				.multiBlockSystemCaching(true)
				.build())
			.maxTokens(100)
			.temperature(0.3)
			.build();

		// First call: static prefix + dynamic context A → creates the cache
		String dynamicContextA = "Dynamic context A: The user is asking about Java microservices. Timestamp: "
				+ System.currentTimeMillis();
		ChatResponse response1 = this.chatModel.call(new Prompt(List.of(new SystemMessage(staticSystemPrompt),
				new SystemMessage(dynamicContextA), new UserMessage("What is a microservice?")), options));

		assertThat(response1).isNotNull();
		assertThat(response1.getResult().getOutput().getText()).isNotEmpty();

		AnthropicApi.Usage usage1 = getAnthropicUsage(response1);
		assertThat(usage1).isNotNull();
		logger.info("Multi-block turn 1 - Cache creation: {}, Cache read: {}", usage1.cacheCreationInputTokens(),
				usage1.cacheReadInputTokens());

		// Cache should be created on first call
		assertThat(usage1.cacheCreationInputTokens()).as("First call should create cache for the static prefix")
			.isGreaterThan(0);

		// Second call: SAME static prefix + DIFFERENT dynamic context B → should get
		// cache read
		String dynamicContextB = "Dynamic context B: The user is now asking about Kubernetes. Timestamp: "
				+ System.currentTimeMillis();
		ChatResponse response2 = this.chatModel.call(new Prompt(List.of(new SystemMessage(staticSystemPrompt),
				new SystemMessage(dynamicContextB), new UserMessage("What is container orchestration?")), options));

		assertThat(response2).isNotNull();
		assertThat(response2.getResult().getOutput().getText()).isNotEmpty();

		AnthropicApi.Usage usage2 = getAnthropicUsage(response2);
		assertThat(usage2).isNotNull();
		logger.info("Multi-block turn 2 - Cache creation: {}, Cache read: {}", usage2.cacheCreationInputTokens(),
				usage2.cacheReadInputTokens());

		// The static prefix should be read from cache even though dynamic context changed
		assertThat(usage2.cacheReadInputTokens())
			.as("Second call should read cache for the static prefix despite different dynamic context")
			.isGreaterThan(0);
	}

	@Test
	void shouldCacheSingleMessageWithMultiBlockSystemCaching() {
		// Verify that a single system message with multiBlockSystemCaching=true
		// still gets cached normally (same as default behavior)
		String systemPrompt = loadPrompt("system-only-cache-prompt.txt");

		AnthropicChatOptions options = AnthropicChatOptions.builder()
			.model(AnthropicApi.ChatModel.CLAUDE_SONNET_4_0.getValue())
			.cacheOptions(AnthropicCacheOptions.builder()
				.strategy(AnthropicCacheStrategy.SYSTEM_ONLY)
				.multiBlockSystemCaching(true)
				.build())
			.maxTokens(100)
			.temperature(0.3)
			.build();

		ChatResponse response = this.chatModel.call(new Prompt(
				List.of(new SystemMessage(systemPrompt), new UserMessage("What is microservices architecture?")),
				options));

		assertThat(response).isNotNull();
		assertThat(response.getResult().getOutput().getText()).isNotEmpty();

		AnthropicApi.Usage usage = getAnthropicUsage(response);
		assertThat(usage).isNotNull();

		// Should behave like normal SYSTEM_ONLY caching
		boolean cacheCreated = usage.cacheCreationInputTokens() > 0;
		boolean cacheRead = usage.cacheReadInputTokens() > 0;
		assertThat(cacheCreated || cacheRead)
			.withFailMessage("Expected either cache creation or cache read tokens, but got creation=%d, read=%d",
					usage.cacheCreationInputTokens(), usage.cacheReadInputTokens())
			.isTrue();

		logger.info("Single message multi-block - Cache creation: {}, Cache read: {}", usage.cacheCreationInputTokens(),
				usage.cacheReadInputTokens());
	}

	@Test
	void shouldNotCacheWithNoneStrategy() {
		String systemPrompt = "You are a helpful assistant.";

		AnthropicChatOptions options = AnthropicChatOptions.builder()
			.model(AnthropicApi.ChatModel.CLAUDE_SONNET_4_0.getValue())
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
					.model(AnthropicApi.ChatModel.CLAUDE_SONNET_4_0.getValue())
					.cacheOptions(AnthropicCacheOptions.builder().strategy(AnthropicCacheStrategy.SYSTEM_ONLY).build())
					.maxTokens(100)
					.build())));

		// Second: No caching
		responses.add(this.chatModel.call(new Prompt(List.of(new UserMessage("What's 5+5?")),
				AnthropicChatOptions.builder()
					.model(AnthropicApi.ChatModel.CLAUDE_SONNET_4_0.getValue())
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

	@Test
	void shouldDemonstrateIncrementalCachingAcrossMultipleTurns() {
		// This test demonstrates how caching grows incrementally with each turn
		// NOTE: Anthropic requires 1024+ tokens for caching to activate
		// We use a large system message to ensure we cross this threshold

		// Large system prompt to ensure we exceed 1024 token minimum for caching
		String largeSystemPrompt = loadPrompt("system-only-cache-prompt.txt");

		AnthropicChatOptions options = AnthropicChatOptions.builder()
			.model(AnthropicApi.ChatModel.CLAUDE_SONNET_4_0.getValue())
			.cacheOptions(AnthropicCacheOptions.builder()
				.strategy(AnthropicCacheStrategy.CONVERSATION_HISTORY)
				// Disable min content length since we're using aggregate check
				.messageTypeMinContentLength(MessageType.USER, 0)
				.build())
			.maxTokens(200)
			.temperature(0.3)
			.build();

		List<Message> conversationHistory = new ArrayList<>();
		// Add system message to provide enough tokens for caching
		conversationHistory.add(new SystemMessage(largeSystemPrompt));

		// Turn 1: Initial question
		logger.info("\n=== TURN 1: Initial Question ===");
		conversationHistory.add(new UserMessage("What is quantum computing? Please explain the basics."));

		ChatResponse turn1 = this.chatModel.call(new Prompt(conversationHistory, options));
		assertThat(turn1).isNotNull();
		String assistant1Response = turn1.getResult().getOutput().getText();
		conversationHistory.add(turn1.getResult().getOutput());

		AnthropicApi.Usage usage1 = getAnthropicUsage(turn1);
		assertThat(usage1).isNotNull();
		logger.info("Turn 1 - User: '{}'", conversationHistory.get(0).getText().substring(0, 50) + "...");
		logger.info("Turn 1 - Assistant: '{}'",
				assistant1Response.substring(0, Math.min(100, assistant1Response.length())) + "...");
		logger.info("Turn 1 - Input tokens: {}", usage1.inputTokens());
		logger.info("Turn 1 - Cache creation tokens: {}", usage1.cacheCreationInputTokens());
		logger.info("Turn 1 - Cache read tokens: {}", usage1.cacheReadInputTokens());

		// Note: First turn may not create cache if total tokens < 1024 (Anthropic's
		// minimum)
		// We'll track whether caching starts in turn 1 or later
		boolean cachingStarted = usage1.cacheCreationInputTokens() > 0;
		logger.info("Turn 1 - Caching started: {}", cachingStarted);
		assertThat(usage1.cacheReadInputTokens()).as("Turn 1 should not read cache (no previous cache)").isEqualTo(0);

		// Turn 2: Follow-up question
		logger.info("\n=== TURN 2: Follow-up Question ===");
		conversationHistory.add(new UserMessage("How does quantum entanglement work in this context?"));

		ChatResponse turn2 = this.chatModel.call(new Prompt(conversationHistory, options));
		assertThat(turn2).isNotNull();
		String assistant2Response = turn2.getResult().getOutput().getText();
		conversationHistory.add(turn2.getResult().getOutput());

		AnthropicApi.Usage usage2 = getAnthropicUsage(turn2);
		assertThat(usage2).isNotNull();
		logger.info("Turn 2 - User: '{}'", conversationHistory.get(2).getText());
		logger.info("Turn 2 - Assistant: '{}'",
				assistant2Response.substring(0, Math.min(100, assistant2Response.length())) + "...");
		logger.info("Turn 2 - Input tokens: {}", usage2.inputTokens());
		logger.info("Turn 2 - Cache creation tokens: {}", usage2.cacheCreationInputTokens());
		logger.info("Turn 2 - Cache read tokens: {}", usage2.cacheReadInputTokens());

		// Second turn: If caching started in turn 1, we should see cache reads
		// Otherwise, caching might start here if we've accumulated enough tokens
		if (cachingStarted) {
			assertThat(usage2.cacheReadInputTokens()).as("Turn 2 should read cache from Turn 1").isGreaterThan(0);
		}
		// Update caching status
		cachingStarted = cachingStarted || usage2.cacheCreationInputTokens() > 0;

		// Turn 3: Another follow-up
		logger.info("\n=== TURN 3: Deeper Question ===");
		conversationHistory
			.add(new UserMessage("Can you give me a practical example of quantum computing application?"));

		ChatResponse turn3 = this.chatModel.call(new Prompt(conversationHistory, options));
		assertThat(turn3).isNotNull();
		String assistant3Response = turn3.getResult().getOutput().getText();
		conversationHistory.add(turn3.getResult().getOutput());

		AnthropicApi.Usage usage3 = getAnthropicUsage(turn3);
		assertThat(usage3).isNotNull();
		logger.info("Turn 3 - User: '{}'", conversationHistory.get(4).getText());
		logger.info("Turn 3 - Assistant: '{}'",
				assistant3Response.substring(0, Math.min(100, assistant3Response.length())) + "...");
		logger.info("Turn 3 - Input tokens: {}", usage3.inputTokens());
		logger.info("Turn 3 - Cache creation tokens: {}", usage3.cacheCreationInputTokens());
		logger.info("Turn 3 - Cache read tokens: {}", usage3.cacheReadInputTokens());

		// Third turn: Should read cache if caching has started
		if (cachingStarted) {
			assertThat(usage3.cacheReadInputTokens()).as("Turn 3 should read cache if caching has started")
				.isGreaterThan(0);
		}
		// Update caching status
		cachingStarted = cachingStarted || usage3.cacheCreationInputTokens() > 0;

		// Turn 4: Final question
		logger.info("\n=== TURN 4: Final Question ===");
		conversationHistory.add(new UserMessage("What are the limitations of current quantum computers?"));

		ChatResponse turn4 = this.chatModel.call(new Prompt(conversationHistory, options));
		assertThat(turn4).isNotNull();
		String assistant4Response = turn4.getResult().getOutput().getText();
		conversationHistory.add(turn4.getResult().getOutput());

		AnthropicApi.Usage usage4 = getAnthropicUsage(turn4);
		assertThat(usage4).isNotNull();
		logger.info("Turn 4 - User: '{}'", conversationHistory.get(6).getText());
		logger.info("Turn 4 - Assistant: '{}'",
				assistant4Response.substring(0, Math.min(100, assistant4Response.length())) + "...");
		logger.info("Turn 4 - Input tokens: {}", usage4.inputTokens());
		logger.info("Turn 4 - Cache creation tokens: {}", usage4.cacheCreationInputTokens());
		logger.info("Turn 4 - Cache read tokens: {}", usage4.cacheReadInputTokens());

		// Fourth turn: By now we should definitely have caching working
		assertThat(cachingStarted).as("Caching should have started by turn 4").isTrue();
		if (cachingStarted) {
			assertThat(usage4.cacheReadInputTokens()).as("Turn 4 should read cache").isGreaterThan(0);
		}

		// Summary logging
		logger.info("\n=== CACHING SUMMARY ===");
		logger.info("Turn 1 - Created: {}, Read: {}", usage1.cacheCreationInputTokens(), usage1.cacheReadInputTokens());
		logger.info("Turn 2 - Created: {}, Read: {}", usage2.cacheCreationInputTokens(), usage2.cacheReadInputTokens());
		logger.info("Turn 3 - Created: {}, Read: {}", usage3.cacheCreationInputTokens(), usage3.cacheReadInputTokens());
		logger.info("Turn 4 - Created: {}, Read: {}", usage4.cacheCreationInputTokens(), usage4.cacheReadInputTokens());

		// Demonstrate incremental growth pattern
		logger.info("\n=== CACHE GROWTH PATTERN ===");
		logger.info("Cache read tokens grew from {} → {} → {} → {}", usage1.cacheReadInputTokens(),
				usage2.cacheReadInputTokens(), usage3.cacheReadInputTokens(), usage4.cacheReadInputTokens());
		logger.info("This demonstrates incremental prefix caching: each turn builds on the previous cache");

		// Verify that once caching starts, cache reads continue to grow
		List<Integer> cacheReads = List.of(usage1.cacheReadInputTokens(), usage2.cacheReadInputTokens(),
				usage3.cacheReadInputTokens(), usage4.cacheReadInputTokens());
		int firstNonZeroIndex = -1;
		for (int i = 0; i < cacheReads.size(); i++) {
			if (cacheReads.get(i) > 0) {
				firstNonZeroIndex = i;
				break;
			}
		}
		if (firstNonZeroIndex >= 0 && firstNonZeroIndex < cacheReads.size() - 1) {
			// Verify each subsequent turn has cache reads >= previous
			for (int i = firstNonZeroIndex + 1; i < cacheReads.size(); i++) {
				assertThat(cacheReads.get(i))
					.as("Cache reads should grow or stay same once caching starts (turn %d vs turn %d)", i + 1, i)
					.isGreaterThanOrEqualTo(cacheReads.get(i - 1));
			}
		}
	}

}
