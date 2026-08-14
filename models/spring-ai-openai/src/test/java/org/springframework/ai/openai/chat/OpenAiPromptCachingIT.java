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

package org.springframework.ai.openai.chat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiTestConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StreamUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for OpenAI prompt caching functionality.
 */
@SpringBootTest(classes = OpenAiTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiPromptCachingIT {

	@Autowired
	private OpenAiChatModel chatModel;

	@Autowired
	private ResourceLoader resourceLoader;

	private String loadLargePrompt() {
		try {
			Resource resource = this.resourceLoader.getResource("classpath:prompts/system-only-cache-prompt.txt");
			String basePrompt = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
			// Duplicate it to ensure it exceeds OpenAI's 1024 token threshold for caching
			StringBuilder sb = new StringBuilder();
			// Prepend a unique UUID so that the prefix is unique per test run,
			// ensuring a cache miss on the first call.
			sb.append("Unique test ID: ").append(java.util.UUID.randomUUID().toString()).append("\n\n");
			for (int i = 0; i < 4; i++) {
				sb.append(basePrompt).append("\n\n");
			}
			return sb.toString();
		}
		catch (IOException e) {
			throw new RuntimeException("Failed to load prompt", e);
		}
	}

	@Test
	void shouldCacheSystemMessageWithAutomaticCaching() {
		String systemPrompt = loadLargePrompt();

		// OpenAI prompt caching is automatic for prompts > 1024 tokens.
		// We use gpt-4o-mini as it supports prompt caching.
		OpenAiChatOptions options = OpenAiChatOptions.builder().model("gpt-4o-mini").temperature(0.1).build();

		// First call - populates the cache
		ChatResponse response1 = this.chatModel.call(new Prompt(
				List.of(new SystemMessage(systemPrompt), new UserMessage("What is microservices architecture?")),
				options));

		assertThat(response1).isNotNull();
		assertThat(response1.getResult().getOutput().getText()).isNotEmpty();

		// Second call with the same prefix - should use the cache
		ChatResponse response2 = this.chatModel.call(new Prompt(
				List.of(new SystemMessage(systemPrompt), new UserMessage("Tell me about security best practices.")),
				options));

		assertThat(response2).isNotNull();
		assertThat(response2.getResult().getOutput().getText()).isNotEmpty();

		Usage springUsage2 = response2.getMetadata().getUsage();
		assertThat(springUsage2).isNotNull();

		// Cache read tokens should be greater than 0 since the prefix is identical and
		// >1024 tokens
		Long cacheRead = springUsage2.getCacheReadInputTokens();
		assertThat(cacheRead).withFailMessage("Expected cache read tokens to be greater than 0, but got %s", cacheRead)
			.isNotNull()
			.isGreaterThan(0L);
	}

	@Test
	void shouldSendPromptCacheKeyInRequest() {
		String systemPrompt = loadLargePrompt();
		String cacheKey = "spring-ai-test-" + java.util.UUID.randomUUID();

		// Use promptCacheKey to explicitly key the cached prefix
		OpenAiChatOptions options = OpenAiChatOptions.builder()
			.model("gpt-4o-mini")
			.temperature(0.1)
			.promptCacheKey(cacheKey)
			.build();

		// First call - populates the cache under the given key
		ChatResponse response1 = this.chatModel.call(new Prompt(
				List.of(new SystemMessage(systemPrompt), new UserMessage("What is microservices architecture?")),
				options));

		assertThat(response1).isNotNull();
		assertThat(response1.getResult().getOutput().getText()).isNotEmpty();

		// Second call with the same key and prefix - should read from cache
		ChatResponse response2 = this.chatModel.call(new Prompt(
				List.of(new SystemMessage(systemPrompt), new UserMessage("Tell me about security best practices.")),
				options));

		assertThat(response2).isNotNull();
		assertThat(response2.getResult().getOutput().getText()).isNotEmpty();

		Usage springUsage2 = response2.getMetadata().getUsage();
		assertThat(springUsage2).isNotNull();

		Long cacheRead = springUsage2.getCacheReadInputTokens();
		assertThat(cacheRead).withFailMessage("Expected cache read tokens to be greater than 0, but got %s", cacheRead)
			.isNotNull()
			.isGreaterThan(0L);
	}

}
