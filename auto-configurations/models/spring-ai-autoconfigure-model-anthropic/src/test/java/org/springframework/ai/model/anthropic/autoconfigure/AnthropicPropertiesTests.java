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
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit Tests for {@link AnthropicChatProperties} and
 * {@link AnthropicConnectionProperties}.
 *
 * @author Soby Chacko
 * @author Sebastien Deleuze
 */
class AnthropicPropertiesTests {

	@Test
	void connectionProperties() {
		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.anthropic.base-url=TEST_BASE_URL", "spring.ai.anthropic.api-key=abc123",
					"spring.ai.anthropic.custom-headers.test-header=test-value",
					"spring.ai.anthropic.chat.model=MODEL_XYZ", "spring.ai.anthropic.chat.temperature=0.55")
			.withConfiguration(
					AutoConfigurations.of(AnthropicChatAutoConfiguration.class, ToolCallingAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(AnthropicChatProperties.class);
				var connectionProperties = context.getBean(AnthropicConnectionProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("abc123");
				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");
				assertThat(connectionProperties.getCustomHeaders()).containsEntry("test-header", "test-value");

				assertThat(chatProperties.toOptions().getModel()).isEqualTo("MODEL_XYZ");
				assertThat(chatProperties.toOptions().getTemperature()).isEqualTo(0.55);
			});
	}

	@Test
	void chatOptionsTest() {
		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.anthropic.api-key=API_KEY", "spring.ai.anthropic.base-url=TEST_BASE_URL",
					"spring.ai.anthropic.chat.model=MODEL_XYZ", "spring.ai.anthropic.chat.max-tokens=123",
					"spring.ai.anthropic.chat.stop-sequences=boza,koza", "spring.ai.anthropic.chat.temperature=0.55",
					"spring.ai.anthropic.chat.top-p=0.56", "spring.ai.anthropic.chat.top-k=100",
					"spring.ai.anthropic.chat.cache-options.multi-block-system-caching=true",
					"spring.ai.anthropic.chat.http-headers.foo=bar")
			.withConfiguration(
					AutoConfigurations.of(AnthropicChatAutoConfiguration.class, ToolCallingAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(AnthropicChatProperties.class);
				var connectionProperties = context.getBean(AnthropicConnectionProperties.class);

				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");
				assertThat(connectionProperties.getApiKey()).isEqualTo("API_KEY");
				assertThat(chatProperties.toOptions().getModel()).isEqualTo("MODEL_XYZ");
				assertThat(chatProperties.toOptions().getMaxTokens()).isEqualTo(123);
				assertThat(chatProperties.toOptions().getStopSequences()).contains("boza", "koza");
				assertThat(chatProperties.toOptions().getTemperature()).isEqualTo(0.55);
				assertThat(chatProperties.toOptions().getTopP()).isEqualTo(0.56);
				assertThat(chatProperties.toOptions().getTopK()).isEqualTo(100);
				assertThat(chatProperties.toOptions().getCacheOptions().isMultiBlockSystemCaching()).isTrue();
				assertThat(chatProperties.toOptions().getHttpHeaders()).containsEntry("foo", "bar");
			});
	}

	@Test
	void webSearchToolProperties() {
		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.anthropic.api-key=API_KEY",
					"spring.ai.anthropic.chat.web-search-tool.max-uses=5",
					"spring.ai.anthropic.chat.web-search-tool.allowed-domains=docs.spring.io,github.com",
					"spring.ai.anthropic.chat.web-search-tool.blocked-domains=example.com",
					"spring.ai.anthropic.chat.web-search-tool.user-location.city=San Francisco",
					"spring.ai.anthropic.chat.web-search-tool.user-location.country=US",
					"spring.ai.anthropic.chat.web-search-tool.user-location.region=California",
					"spring.ai.anthropic.chat.web-search-tool.user-location.timezone=America/Los_Angeles")
			.withConfiguration(
					AutoConfigurations.of(AnthropicChatAutoConfiguration.class, ToolCallingAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(AnthropicChatProperties.class);
				var webSearch = chatProperties.toOptions().getWebSearchTool();

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
