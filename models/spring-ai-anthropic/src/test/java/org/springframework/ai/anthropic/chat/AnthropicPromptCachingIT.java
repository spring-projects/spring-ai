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

package org.springframework.ai.anthropic.chat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.Usage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.anthropic.AnthropicCacheOptions;
import org.springframework.ai.anthropic.AnthropicCacheStrategy;
import org.springframework.ai.anthropic.AnthropicCacheTtl;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.AnthropicTestConfiguration;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
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
@SpringBootTest(classes = AnthropicTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class AnthropicPromptCachingIT {

	@Autowired
	private AnthropicChatModel chatModel;

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

		AnthropicChatOptions options = AnthropicChatOptions.builder()
			.model(Model.CLAUDE_SONNET_4_20250514.asString())
			.cacheOptions(AnthropicCacheOptions.builder().strategy(AnthropicCacheStrategy.SYSTEM_ONLY).build())
			.maxTokens(150)
			.temperature(0.3)
			.build();

		ChatResponse response = this.chatModel.call(new Prompt(
				List.of(new SystemMessage(systemPrompt), new UserMessage("What is microservices architecture?")),
				options));

		assertThat(response).isNotNull();
		assertThat(response.getResult().getOutput().getText()).isNotEmpty();
		Usage usage = getSdkUsage(response);
		assertThat(usage).isNotNull();

		long cacheCreation = usage.cacheCreationInputTokens().orElse(0L);
		long cacheRead = usage.cacheReadInputTokens().orElse(0L);
		assertThat(cacheCreation > 0 || cacheRead > 0)
			.withFailMessage("Expected either cache creation or cache read tokens, but got creation=%d, read=%d",
					cacheCreation, cacheRead)
			.isTrue();

		// Verify unified Usage interface reports the same cache metrics
		org.springframework.ai.chat.metadata.Usage springUsage = response.getMetadata().getUsage();
		assertThat(springUsage.getCacheWriteInputTokens() != null || springUsage.getCacheReadInputTokens() != null)
			.withFailMessage("Expected cache metrics on Usage interface")
			.isTrue();
		if (cacheCreation > 0) {
			assertThat(springUsage.getCacheWriteInputTokens()).isEqualTo(cacheCreation);
		}
		if (cacheRead > 0) {
			assertThat(springUsage.getCacheReadInputTokens()).isEqualTo(cacheRead);
		}
	}

	@Test
	void shouldCacheSystemAndTools() {
		String systemPrompt = loadPrompt("system-and-tools-cache-prompt.txt");

		ToolCallingManager toolCallingManager = DefaultToolCallingManager.builder().build();

		AnthropicChatOptions options = AnthropicChatOptions.builder()
			.model(Model.CLAUDE_SONNET_4_20250514.asString())
			.cacheOptions(AnthropicCacheOptions.builder().strategy(AnthropicCacheStrategy.SYSTEM_AND_TOOLS).build())
			.maxTokens(200)
			.temperature(0.3)
			.toolCallbacks(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
				.description("Get current weather for a location")
				.inputType(MockWeatherService.Request.class)
				.build())
			.build();

		Prompt prompt = new Prompt(
				List.of(new SystemMessage(systemPrompt),
						new UserMessage("What's the weather like in San Francisco and should I go for a walk?")),
				options);

		ChatResponse response = this.chatModel.call(prompt);

		while (response.hasToolCalls()) {
			ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, response);
			prompt = new Prompt(toolExecutionResult.conversationHistory(), options);
			response = this.chatModel.call(prompt);
		}

		assertThat(response).isNotNull();
		assertThat(response.getResult().getOutput().getText()).isNotEmpty();
		Usage usage = getSdkUsage(response);
		if (usage != null) {
			long cacheCreation = usage.cacheCreationInputTokens().orElse(0L);
			long cacheRead = usage.cacheReadInputTokens().orElse(0L);
			assertThat(cacheCreation > 0 || cacheRead > 0)
				.withFailMessage("Expected either cache creation or cache read tokens, but got creation=%d, read=%d",
						cacheCreation, cacheRead)
				.isTrue();
		}
		else {
			assertThat(response.getResult().getOutput().getText()).isNotEmpty();
		}
	}

	@Test
	void shouldCacheConversationHistory() {
		String systemPrompt = loadPrompt("system-only-cache-prompt.txt");

		AnthropicChatOptions options = AnthropicChatOptions.builder()
			.model(Model.CLAUDE_SONNET_4_20250514.asString())
			.cacheOptions(AnthropicCacheOptions.builder()
				.strategy(AnthropicCacheStrategy.CONVERSATION_HISTORY)
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
		// Turn 2
		conversationHistory.add(new UserMessage("How does quantum entanglement work?"));
		ChatResponse turn2 = this.chatModel.call(new Prompt(conversationHistory, options));
		assertThat(turn2).isNotNull();
		conversationHistory.add(turn2.getResult().getOutput());

		Usage usage2 = getSdkUsage(turn2);
		assertThat(usage2).isNotNull();
		long turn2Read = usage2.cacheReadInputTokens().orElse(0L);
		// If caching started in turn 1, turn 2 should see cache reads
		if (turn1Creation > 0) {
			assertThat(turn2Read).as("Turn 2 should read cache from Turn 1").isGreaterThan(0);
		}
	}

	@Test
	void shouldRespectMinLengthForSystemCaching() {
		String systemPrompt = loadPrompt("system-only-cache-prompt.txt");

		AnthropicChatOptions options = AnthropicChatOptions.builder()
			.model(Model.CLAUDE_SONNET_4_20250514.asString())
			.cacheOptions(AnthropicCacheOptions.builder()
				.strategy(AnthropicCacheStrategy.SYSTEM_ONLY)
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

		AnthropicChatOptions options = AnthropicChatOptions.builder()
			.model(Model.CLAUDE_SONNET_4_20250514.asString())
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
		Usage usage = getSdkUsage(response);
		assertThat(usage).isNotNull();
		long cacheCreation = usage.cacheCreationInputTokens().orElse(0L);
		long cacheRead = usage.cacheReadInputTokens().orElse(0L);
		assertThat(cacheCreation > 0 || cacheRead > 0)
			.withFailMessage("Expected either cache creation or cache read tokens, but got creation=%d, read=%d",
					cacheCreation, cacheRead)
			.isTrue();
	}

	@Test
	void shouldNotCacheWithNoneStrategy() {
		AnthropicChatOptions options = AnthropicChatOptions.builder()
			.cacheOptions(AnthropicCacheOptions.builder().strategy(AnthropicCacheStrategy.NONE).build())
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

		AnthropicChatOptions options = AnthropicChatOptions.builder()
			.model(Model.CLAUDE_SONNET_4_20250514.asString())
			.cacheOptions(AnthropicCacheOptions.builder()
				.strategy(AnthropicCacheStrategy.CONVERSATION_HISTORY)
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

	}

	@Test
	void shouldCacheToolResultsAcrossToolCallingRounds() {
		// A deliberately large tool result, comfortably above Anthropic's minimum
		// cacheable prefix (~1024 tokens). The system/tool/user prefix in front of it is
		// intentionally small: on its own it is below the minimum and would not cache, so
		// any cache read can only come from the tool result being part of the cached
		// prefix, which is exactly what the cacheToolResults breakpoint enables.
		StringBuilder largeToolResult = new StringBuilder("Detailed hourly weather report for San Francisco.\n");
		for (int hour = 0; hour < 160; hour++) {
			largeToolResult.append("Hour ")
				.append(hour)
				.append(": temperature 18C, humidity 72%, wind 12 km/h from the west, visibility 10 km, ")
				.append("pressure 1013 hPa, no precipitation expected, UV index moderate.\n");
		}

		// A marker captured once keeps this test's two calls sharing a single cache entry
		// while avoiding collisions with content cached by previous runs.
		String runMarker = "Session " + System.currentTimeMillis();

		AnthropicChatOptions options = AnthropicChatOptions.builder()
			.model(Model.CLAUDE_SONNET_4_20250514.asString())
			.cacheOptions(AnthropicCacheOptions.builder()
				.strategy(AnthropicCacheStrategy.CONVERSATION_HISTORY)
				.cacheToolResults(true)
				.messageTypeMinContentLength(MessageType.USER, 0)
				.build())
			.toolCallbacks(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
				.description("Get current weather for a location")
				.inputType(MockWeatherService.Request.class)
				.build())
			.maxTokens(80)
			.temperature(0.3)
			.build();

		List<Message> conversation = List.of(new SystemMessage("You are a weather assistant. " + runMarker),
				new UserMessage("What's the detailed weather in San Francisco?"),
				AssistantMessage.builder()
					.content("")
					.toolCalls(List.of(new AssistantMessage.ToolCall("toolu_sf_1", "function", "getCurrentWeather",
							"{\"location\":\"San Francisco, CA\",\"unit\":\"C\"}")))
					.build(),
				ToolResponseMessage.builder()
					.responses(List.of(new ToolResponseMessage.ToolResponse("toolu_sf_1", "getCurrentWeather",
							largeToolResult.toString())))
					.build());

		Prompt prompt = new Prompt(conversation, options);

		// First call writes the prefix, including the large tool result, to cache.
		ChatResponse first = this.chatModel.call(prompt);
		assertThat(first).isNotNull();
		Usage firstUsage = getSdkUsage(first);
		assertThat(firstUsage).isNotNull();
		long firstCreation = firstUsage.cacheCreationInputTokens().orElse(0L);
		long firstRead = firstUsage.cacheReadInputTokens().orElse(0L);
		assertThat(firstCreation > 0 || firstRead > 0)
			.withFailMessage("Expected the tool result prefix to be cached, but got creation=%d, read=%d",
					firstCreation, firstRead)
			.isTrue();

		// Second identical call reads that prefix back. Because the non-tool-result
		// prefix
		// is below the cache minimum, a non-zero read confirms the tool result itself was
		// cached by the cacheToolResults breakpoint.
		ChatResponse second = this.chatModel.call(prompt);
		assertThat(second).isNotNull();
		Usage secondUsage = getSdkUsage(second);
		assertThat(secondUsage).isNotNull();
		long secondRead = secondUsage.cacheReadInputTokens().orElse(0L);
		assertThat(secondRead).as("Second call should read the cached tool result").isGreaterThan(0);
	}

	@Test
	void shouldCacheStaticPrefixWithMultiBlockSystemCaching() {
		String staticSystemPrompt = loadPrompt("system-only-cache-prompt.txt");
		String dynamicSystemPrompt = "Current user session ID: " + System.currentTimeMillis();

		AnthropicChatOptions options = AnthropicChatOptions.builder()
			.model(Model.CLAUDE_SONNET_4_20250514.asString())
			.cacheOptions(AnthropicCacheOptions.builder()
				.strategy(AnthropicCacheStrategy.SYSTEM_ONLY)
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
		Usage usage = getSdkUsage(response);
		assertThat(usage).isNotNull();
		long cacheCreation = usage.cacheCreationInputTokens().orElse(0L);
		long cacheRead = usage.cacheReadInputTokens().orElse(0L);
		assertThat(cacheCreation > 0 || cacheRead > 0)
			.withFailMessage("Expected either cache creation or cache read tokens, but got creation=%d, read=%d",
					cacheCreation, cacheRead)
			.isTrue();
	}

}
