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

package org.springframework.ai.model.anthropic.autoconfigure;

import org.junit.jupiter.api.Test;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit Tests for {@link AnthropicChatProperties} and
 * {@link AnthropicConnectionProperties}.
 *
 * @author Soby Chacko
 */
class AnthropicPropertiesTests {

	@Test
	void connectionProperties() {
		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.anthropic.base-url=TEST_BASE_URL", "spring.ai.anthropic.api-key=abc123",
					"spring.ai.anthropic.chat.options.model=MODEL_XYZ",
					"spring.ai.anthropic.chat.options.temperature=0.55")
			.withConfiguration(
					AutoConfigurations.of(AnthropicChatAutoConfiguration.class, ToolCallingAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(AnthropicChatProperties.class);
				var connectionProperties = context.getBean(AnthropicConnectionProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("abc123");
				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");

				assertThat(chatProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
				assertThat(chatProperties.getOptions().getTemperature()).isEqualTo(0.55);
			});
	}

	@Test
	void chatOverrideConnectionProperties() {
		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.anthropic.base-url=TEST_BASE_URL", "spring.ai.anthropic.api-key=abc123",
					"spring.ai.anthropic.chat.base-url=TEST_BASE_URL_2", "spring.ai.anthropic.chat.api-key=456",
					"spring.ai.anthropic.chat.options.model=MODEL_XYZ",
					"spring.ai.anthropic.chat.options.temperature=0.55")
			.withConfiguration(
					AutoConfigurations.of(AnthropicChatAutoConfiguration.class, ToolCallingAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(AnthropicChatProperties.class);
				var connectionProperties = context.getBean(AnthropicConnectionProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("abc123");
				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");

				assertThat(chatProperties.getApiKey()).isEqualTo("456");
				assertThat(chatProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL_2");

				assertThat(chatProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
				assertThat(chatProperties.getOptions().getTemperature()).isEqualTo(0.55);
			});
	}

	@Test
	void chatOptionsTest() {
		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.anthropic.api-key=API_KEY", "spring.ai.anthropic.base-url=TEST_BASE_URL",
					"spring.ai.anthropic.chat.options.model=MODEL_XYZ",
					"spring.ai.anthropic.chat.options.max-tokens=123",
					"spring.ai.anthropic.chat.options.stop-sequences=boza,koza",
					"spring.ai.anthropic.chat.options.temperature=0.55", "spring.ai.anthropic.chat.options.top-p=0.56",
					"spring.ai.anthropic.chat.options.top-k=100")
			.withConfiguration(
					AutoConfigurations.of(AnthropicChatAutoConfiguration.class, ToolCallingAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(AnthropicChatProperties.class);
				var connectionProperties = context.getBean(AnthropicConnectionProperties.class);

				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");
				assertThat(connectionProperties.getApiKey()).isEqualTo("API_KEY");
				assertThat(chatProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
				assertThat(chatProperties.getOptions().getMaxTokens()).isEqualTo(123);
				assertThat(chatProperties.getOptions().getStopSequences()).contains("boza", "koza");
				assertThat(chatProperties.getOptions().getTemperature()).isEqualTo(0.55);
				assertThat(chatProperties.getOptions().getTopP()).isEqualTo(0.56);
				assertThat(chatProperties.getOptions().getTopK()).isEqualTo(100);
			});
	}

	@Test
	void webSearchToolProperties() {
		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.anthropic.api-key=API_KEY",
					"spring.ai.anthropic.chat.options.web-search-tool.max-uses=5",
					"spring.ai.anthropic.chat.options.web-search-tool.allowed-domains=docs.spring.io,github.com",
					"spring.ai.anthropic.chat.options.web-search-tool.blocked-domains=example.com",
					"spring.ai.anthropic.chat.options.web-search-tool.user-location.city=San Francisco",
					"spring.ai.anthropic.chat.options.web-search-tool.user-location.country=US",
					"spring.ai.anthropic.chat.options.web-search-tool.user-location.region=California",
					"spring.ai.anthropic.chat.options.web-search-tool.user-location.timezone=America/Los_Angeles")
			.withConfiguration(
					AutoConfigurations.of(AnthropicChatAutoConfiguration.class, ToolCallingAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(AnthropicChatProperties.class);
				var webSearch = chatProperties.getOptions().getWebSearchTool();

				assertThat(webSearch).isNotNull();
				assertThat(webSearch.getMaxUses()).isEqualTo(5);
				assertThat(webSearch.getAllowedDomains()).containsExactly("docs.spring.io", "github.com");
				assertThat(webSearch.getBlockedDomains()).containsExactly("example.com");
				assertThat(webSearch.getUserLocation()).isNotNull();
				assertThat(webSearch.getUserLocation().city()).isEqualTo("San Francisco");
				assertThat(webSearch.getUserLocation().country()).isEqualTo("US");
				assertThat(webSearch.getUserLocation().region()).isEqualTo("California");
				assertThat(webSearch.getUserLocation().timezone()).isEqualTo("America/Los_Angeles");
			});
	}

	@Test
	void promptCacheProperties() {
		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.anthropic.api-key=API_KEY",
					"spring.ai.prompt-cache.strategy=system-and-tools", "spring.ai.prompt-cache.ttl=5m",
					"spring.ai.prompt-cache.min-content-length=1024")
			.withConfiguration(
					AutoConfigurations.of(AnthropicChatAutoConfiguration.class, ToolCallingAutoConfiguration.class))
			.run(context -> {
				var promptCacheProperties = context.getBean(AnthropicPromptCacheProperties.class);
				assertThat(promptCacheProperties.getStrategy())
					.isEqualTo(org.springframework.ai.chat.prompt.PromptCacheStrategy.SYSTEM_AND_TOOLS);
				assertThat(promptCacheProperties.getTtl()).isEqualTo(java.time.Duration.ofMinutes(5));
				assertThat(promptCacheProperties.getMinContentLength()).isEqualTo(1024);

				// Verify it's applied to the model's default options
				var chatModel = context.getBean(AnthropicChatModel.class);
				var defaultOptions = (AnthropicChatOptions) chatModel.getDefaultOptions();
				assertThat(defaultOptions.getPromptCacheStrategy())
					.isEqualTo(org.springframework.ai.chat.prompt.PromptCacheStrategy.SYSTEM_AND_TOOLS);
			});
	}

	@Test
	void promptCachePropertiesDefaultsToNone() {
		new ApplicationContextRunner().withPropertyValues("spring.ai.anthropic.api-key=API_KEY")
			.withConfiguration(
					AutoConfigurations.of(AnthropicChatAutoConfiguration.class, ToolCallingAutoConfiguration.class))
			.run(context -> {
				var promptCacheProperties = context.getBean(AnthropicPromptCacheProperties.class);
				assertThat(promptCacheProperties.getStrategy())
					.isEqualTo(org.springframework.ai.chat.prompt.PromptCacheStrategy.NONE);

				var chatModel = context.getBean(AnthropicChatModel.class);
				var defaultOptions = (AnthropicChatOptions) chatModel.getDefaultOptions();
				assertThat(defaultOptions.getPromptCacheStrategy()).isNull();
			});
	}

	@Test
	void chatCompletionDisabled() {
		// Enabled by default
		new ApplicationContextRunner().withPropertyValues("spring.ai.anthropic.api-key=API_KEY")
			.withConfiguration(
					AutoConfigurations.of(AnthropicChatAutoConfiguration.class, ToolCallingAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(AnthropicChatProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(AnthropicChatModel.class)).isNotEmpty();
			});

		// Explicitly enable
		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.anthropic.api-key=API_KEY", "spring.ai.model.chat=anthropic")
			.withConfiguration(
					AutoConfigurations.of(AnthropicChatAutoConfiguration.class, ToolCallingAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(AnthropicChatProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(AnthropicChatModel.class)).isNotEmpty();
			});

		// Explicitly disable
		new ApplicationContextRunner().withPropertyValues("spring.ai.model.chat=none")
			.withConfiguration(AutoConfigurations.of(AnthropicChatAutoConfiguration.class))
			.run(context -> assertThat(context.getBeansOfType(AnthropicChatModel.class)).isEmpty());
	}

}
