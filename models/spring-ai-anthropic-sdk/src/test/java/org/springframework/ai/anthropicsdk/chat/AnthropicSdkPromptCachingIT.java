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

package org.springframework.ai.anthropicsdk.chat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.anthropic.models.messages.Usage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.anthropicsdk.AnthropicSdkCacheOptions;
import org.springframework.ai.anthropicsdk.AnthropicSdkCacheStrategy;
import org.springframework.ai.anthropicsdk.AnthropicSdkCacheTtl;
import org.springframework.ai.anthropicsdk.AnthropicSdkChatModel;
import org.springframework.ai.anthropicsdk.AnthropicSdkChatOptions;
import org.springframework.ai.anthropicsdk.AnthropicSdkTestConfiguration;
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
 * Integration tests for Anthropic prompt caching functionality using the Anthropic Java
 * SDK.
 *
 * @author Soby Chacko
 */
@SpringBootTest(classes = AnthropicSdkTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class AnthropicSdkPromptCachingIT {

	private static final Logger logger = LoggerFactory.getLogger(AnthropicSdkPromptCachingIT.class);

	@Autowired
	private AnthropicSdkChatModel chatModel;

	@Autowired
	private ResourceLoader resourceLoader;

	private String loadPrompt(String filename) {
		try {
			Resource resource = this.resourceLoader.getResource("classpath:prompts/" + filename);
			String basePrompt = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
			return basePrompt + "\n\nTest execution timestamp: " + System.currentTimeMillis();
		}
		catch (IOException e) {
			throw new RuntimeException("Failed to load prompt: " + filename, e);
		}
	}

	private Usage getSdkUsage(ChatResponse response) {
		if (response == null || response.getMetadata() == null || response.getMetadata().getUsage() == null) {
			return null;
		}
		Object nativeUsage = response.getMetadata().getUsage().getNativeUsage();
		return (nativeUsage instanceof Usage usage) ? usage : null;
	}

	@Test
	void shouldCacheSystemMessageOnly() {
		String systemPrompt = loadPrompt("system-only-cache-prompt.txt");

		AnthropicSdkChatOptions options = AnthropicSdkChatOptions.builder()
			.cacheOptions(AnthropicSdkCacheOptions.builder().strategy(AnthropicSdkCacheStrategy.SYSTEM_ONLY).build())
			.maxTokens(150)
			.temperature(0.3)
			.build();

		ChatResponse response = this.chatModel.call(new Prompt(
				List.of(new SystemMessage(systemPrompt), new UserMessage("What is microservices architecture?")),
				options));

		assertThat(response).isNotNull();
		assertThat(response.getResult().getOutput().getText()).isNotEmpty();
		logger.info("System-only cache response: {}", response.getResult().getOutput().getText());

		Usage usage = getSdkUsage(response);
		assertThat(usage).isNotNull();

		long cacheCreation = usage.cacheCreationInputTokens().orElse(0L);
		long cacheRead = usage.cacheReadInputTokens().orElse(0L);
		assertThat(cacheCreation > 0 || cacheRead > 0)
			.withFailMessage("Expected either cache creation or cache read tokens, but got creation=%d, read=%d",
					cacheCreation, cacheRead)
			.isTrue();

		logger.info("Cache creation tokens: {}, Cache read tokens: {}", cacheCreation, cacheRead);
	}

	@Test
	void shouldCacheSystemAndTools() {
		String systemPrompt = loadPrompt("system-and-tools-cache-prompt.txt");

		MockWeatherService weatherService = new MockWeatherService();

		AnthropicSdkChatOptions options = AnthropicSdkChatOptions.builder()
			.cacheOptions(
					AnthropicSdkCacheOptions.builder().strategy(AnthropicSdkCacheStrategy.SYSTEM_AND_TOOLS).build())
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

		Usage usage = getSdkUsage(response);
		if (usage != null) {
			long cacheCreation = usage.cacheCreationInputTokens().orElse(0L);
			long cacheRead = usage.cacheReadInputTokens().orElse(0L);
			assertThat(cacheCreation > 0 || cacheRead > 0)
				.withFailMessage("Expected either cache creation or cache read tokens, but got creation=%d, read=%d",
						cacheCreation, cacheRead)
				.isTrue();
			logger.info("Cache creation tokens: {}, Cache read tokens: {}", cacheCreation, cacheRead);
		}
		else {
			logger.debug("Native usage metadata not available for tool-based interactions - this is expected");
			assertThat(response.getResult().getOutput().getText()).isNotEmpty();
		}
	}

	@Test
	void shouldCacheConversationHistory() {
		String systemPrompt = loadPrompt("system-only-cache-prompt.txt");

		AnthropicSdkChatOptions options = AnthropicSdkChatOptions.builder()
			.cacheOptions(AnthropicSdkCacheOptions.builder()
				.strategy(AnthropicSdkCacheStrategy.CONVERSATION_HISTORY)
				.messageTypeMinContentLength(MessageType.USER, 0)
				.build())
			.maxTokens(200)
			.temperature(0.3)
			.build();

		List<Message> conversationHistory = new ArrayList<>();
		conversationHistory.add(new SystemMessage(systemPrompt));

		// Turn 1
		conversationHistory.add(new UserMessage("What is quantum computing? Please explain the basics."));
		ChatResponse turn1 = this.chatModel.call(new Prompt(conversationHistory, options));
		assertThat(turn1).isNotNull();
		conversationHistory.add(turn1.getResult().getOutput());

		Usage usage1 = getSdkUsage(turn1);
		assertThat(usage1).isNotNull();
		long turn1Creation = usage1.cacheCreationInputTokens().orElse(0L);
		logger.info("Turn 1 - Cache creation: {}, Cache read: {}", turn1Creation,
				usage1.cacheReadInputTokens().orElse(0L));

		// Turn 2
		conversationHistory.add(new UserMessage("How does quantum entanglement work?"));
		ChatResponse turn2 = this.chatModel.call(new Prompt(conversationHistory, options));
		assertThat(turn2).isNotNull();
		conversationHistory.add(turn2.getResult().getOutput());

		Usage usage2 = getSdkUsage(turn2);
		assertThat(usage2).isNotNull();
		long turn2Read = usage2.cacheReadInputTokens().orElse(0L);
		logger.info("Turn 2 - Cache creation: {}, Cache read: {}", usage2.cacheCreationInputTokens().orElse(0L),
				turn2Read);

		// If caching started in turn 1, turn 2 should see cache reads
		if (turn1Creation > 0) {
			assertThat(turn2Read).as("Turn 2 should read cache from Turn 1").isGreaterThan(0);
		}
	}

	@Test
	void shouldRespectMinLengthForSystemCaching() {
		String systemPrompt = loadPrompt("system-only-cache-prompt.txt");

		AnthropicSdkChatOptions options = AnthropicSdkChatOptions.builder()
			.cacheOptions(AnthropicSdkCacheOptions.builder()
				.strategy(AnthropicSdkCacheStrategy.SYSTEM_ONLY)
				.messageTypeMinContentLength(MessageType.SYSTEM, systemPrompt.length() + 1)
				.build())
			.maxTokens(60)
			.temperature(0.2)
			.build();

		ChatResponse response = this.chatModel
			.call(new Prompt(List.of(new SystemMessage(systemPrompt), new UserMessage("Ping")), options));

		assertThat(response).isNotNull();
		Usage usage = getSdkUsage(response);
		assertThat(usage).isNotNull();
		assertThat(usage.cacheCreationInputTokens().orElse(0L)).as("No cache should be created below min length")
			.isEqualTo(0);
		assertThat(usage.cacheReadInputTokens().orElse(0L)).as("No cache read expected below min length").isEqualTo(0);
	}

	@Test
	void shouldHandleExtendedTtlCaching() {
		String systemPrompt = loadPrompt("extended-ttl-cache-prompt.txt");

		AnthropicSdkChatOptions options = AnthropicSdkChatOptions.builder()
			.cacheOptions(AnthropicSdkCacheOptions.builder()
				.strategy(AnthropicSdkCacheStrategy.SYSTEM_ONLY)
				.messageTypeTtl(MessageType.SYSTEM, AnthropicSdkCacheTtl.ONE_HOUR)
				.build())
			.maxTokens(100)
			.temperature(0.3)
			.build();

		ChatResponse response = this.chatModel
			.call(new Prompt(List.of(new SystemMessage(systemPrompt), new UserMessage("What is 2+2?")), options));

		assertThat(response).isNotNull();
		assertThat(response.getResult().getOutput().getText()).contains("4");
		logger.info("Extended TTL cache response: {}", response.getResult().getOutput().getText());

		Usage usage = getSdkUsage(response);
		assertThat(usage).isNotNull();
		long cacheCreation = usage.cacheCreationInputTokens().orElse(0L);
		long cacheRead = usage.cacheReadInputTokens().orElse(0L);
		assertThat(cacheCreation > 0 || cacheRead > 0)
			.withFailMessage("Expected either cache creation or cache read tokens, but got creation=%d, read=%d",
					cacheCreation, cacheRead)
			.isTrue();

		logger.info("Extended TTL - Cache creation: {}, Cache read: {}", cacheCreation, cacheRead);
	}

	@Test
	void shouldNotCacheWithNoneStrategy() {
		AnthropicSdkChatOptions options = AnthropicSdkChatOptions.builder()
			.cacheOptions(AnthropicSdkCacheOptions.builder().strategy(AnthropicSdkCacheStrategy.NONE).build())
			.maxTokens(50)
			.temperature(0.3)
			.build();

		ChatResponse response = this.chatModel.call(new Prompt(
				List.of(new SystemMessage("You are a helpful assistant."), new UserMessage("Hello!")), options));

		assertThat(response).isNotNull();
		assertThat(response.getResult().getOutput().getText()).isNotEmpty();

		Usage usage = getSdkUsage(response);
		assertThat(usage).isNotNull();
		assertThat(usage.cacheCreationInputTokens().orElse(0L)).isEqualTo(0);
		assertThat(usage.cacheReadInputTokens().orElse(0L)).isEqualTo(0);
	}

	@Test
	void shouldDemonstrateIncrementalCachingAcrossMultipleTurns() {
		String largeSystemPrompt = loadPrompt("system-only-cache-prompt.txt");

		AnthropicSdkChatOptions options = AnthropicSdkChatOptions.builder()
			.cacheOptions(AnthropicSdkCacheOptions.builder()
				.strategy(AnthropicSdkCacheStrategy.CONVERSATION_HISTORY)
				.messageTypeMinContentLength(MessageType.USER, 0)
				.build())
			.maxTokens(200)
			.temperature(0.3)
			.build();

		List<Message> conversationHistory = new ArrayList<>();
		conversationHistory.add(new SystemMessage(largeSystemPrompt));

		// Turn 1
		conversationHistory.add(new UserMessage("What is quantum computing? Please explain the basics."));
		ChatResponse turn1 = this.chatModel.call(new Prompt(conversationHistory, options));
		assertThat(turn1).isNotNull();
		conversationHistory.add(turn1.getResult().getOutput());

		Usage usage1 = getSdkUsage(turn1);
		assertThat(usage1).isNotNull();
		boolean cachingStarted = usage1.cacheCreationInputTokens().orElse(0L) > 0;

		// Turn 2
		conversationHistory.add(new UserMessage("How does quantum entanglement work in this context?"));
		ChatResponse turn2 = this.chatModel.call(new Prompt(conversationHistory, options));
		assertThat(turn2).isNotNull();
		conversationHistory.add(turn2.getResult().getOutput());

		Usage usage2 = getSdkUsage(turn2);
		assertThat(usage2).isNotNull();
		if (cachingStarted) {
			assertThat(usage2.cacheReadInputTokens().orElse(0L)).as("Turn 2 should read cache from Turn 1")
				.isGreaterThan(0);
		}
		cachingStarted = cachingStarted || usage2.cacheCreationInputTokens().orElse(0L) > 0;

		// Turn 3
		conversationHistory
			.add(new UserMessage("Can you give me a practical example of quantum computing application?"));
		ChatResponse turn3 = this.chatModel.call(new Prompt(conversationHistory, options));
		assertThat(turn3).isNotNull();
		conversationHistory.add(turn3.getResult().getOutput());

		Usage usage3 = getSdkUsage(turn3);
		assertThat(usage3).isNotNull();
		if (cachingStarted) {
			assertThat(usage3.cacheReadInputTokens().orElse(0L)).as("Turn 3 should read cache").isGreaterThan(0);
		}
		cachingStarted = cachingStarted || usage3.cacheCreationInputTokens().orElse(0L) > 0;

		// Turn 4
		conversationHistory.add(new UserMessage("What are the limitations of current quantum computers?"));
		ChatResponse turn4 = this.chatModel.call(new Prompt(conversationHistory, options));
		assertThat(turn4).isNotNull();

		Usage usage4 = getSdkUsage(turn4);
		assertThat(usage4).isNotNull();
		assertThat(cachingStarted).as("Caching should have started by turn 4").isTrue();
		if (cachingStarted) {
			assertThat(usage4.cacheReadInputTokens().orElse(0L)).as("Turn 4 should read cache").isGreaterThan(0);
		}

		// Summary
		logger.info("Turn 1 - Created: {}, Read: {}", usage1.cacheCreationInputTokens().orElse(0L),
				usage1.cacheReadInputTokens().orElse(0L));
		logger.info("Turn 2 - Created: {}, Read: {}", usage2.cacheCreationInputTokens().orElse(0L),
				usage2.cacheReadInputTokens().orElse(0L));
		logger.info("Turn 3 - Created: {}, Read: {}", usage3.cacheCreationInputTokens().orElse(0L),
				usage3.cacheReadInputTokens().orElse(0L));
		logger.info("Turn 4 - Created: {}, Read: {}", usage4.cacheCreationInputTokens().orElse(0L),
				usage4.cacheReadInputTokens().orElse(0L));
	}

	@Test
	void shouldCacheStaticPrefixWithMultiBlockSystemCaching() {
		String staticSystemPrompt = loadPrompt("system-only-cache-prompt.txt");
		String dynamicSystemPrompt = "Current user session ID: " + System.currentTimeMillis();

		AnthropicSdkChatOptions options = AnthropicSdkChatOptions.builder()
			.cacheOptions(AnthropicSdkCacheOptions.builder()
				.strategy(AnthropicSdkCacheStrategy.SYSTEM_ONLY)
				.multiBlockSystemCaching(true)
				.build())
			.maxTokens(150)
			.temperature(0.3)
			.build();

		ChatResponse response = this.chatModel
			.call(new Prompt(List.of(new SystemMessage(staticSystemPrompt), new SystemMessage(dynamicSystemPrompt),
					new UserMessage("What is microservices architecture?")), options));

		assertThat(response).isNotNull();
		assertThat(response.getResult().getOutput().getText()).isNotEmpty();
		logger.info("Multi-block system cache response: {}", response.getResult().getOutput().getText());

		Usage usage = getSdkUsage(response);
		assertThat(usage).isNotNull();
		long cacheCreation = usage.cacheCreationInputTokens().orElse(0L);
		long cacheRead = usage.cacheReadInputTokens().orElse(0L);
		assertThat(cacheCreation > 0 || cacheRead > 0)
			.withFailMessage("Expected either cache creation or cache read tokens, but got creation=%d, read=%d",
					cacheCreation, cacheRead)
			.isTrue();

		logger.info("Multi-block - Cache creation: {}, Cache read: {}", cacheCreation, cacheRead);
	}

}
