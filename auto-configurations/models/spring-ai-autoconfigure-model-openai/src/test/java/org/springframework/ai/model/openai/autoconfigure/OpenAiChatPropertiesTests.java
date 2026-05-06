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

package org.springframework.ai.model.openai.autoconfigure;

import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit Tests for {@link OpenAiConnectionProperties}, {@link OpenAiChatProperties} and
 * {@link OpenAiEmbeddingProperties}.
 *
 * @author Christian Tzolov
 * @author Sebastien Deleuze
 */
public class OpenAiChatPropertiesTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();

	@Test
	public void chatProperties() {

		this.contextRunner.withPropertyValues(
		// @formatter:off
				"spring.ai.openai.base-url=http://TEST.BASE.URL",
				"spring.ai.openai.api-key=abc123",
				"spring.ai.openai.chat.model=MODEL_XYZ",
				"spring.ai.openai.chat.temperature=0.55")
				// @formatter:on
			.withConfiguration(
					AutoConfigurations.of(OpenAiChatAutoConfiguration.class, ToolCallingAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(OpenAiChatProperties.class);
				var connectionProperties = context.getBean(OpenAiConnectionProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("abc123");
				assertThat(connectionProperties.getBaseUrl()).isEqualTo("http://TEST.BASE.URL");

				assertThat(chatProperties.getApiKey()).isNull();
				assertThat(chatProperties.getBaseUrl()).isNull();

				var options = chatProperties.toOptions();
				assertThat(options.getModel()).isEqualTo("MODEL_XYZ");
				assertThat(options.getTemperature()).isEqualTo(0.55);
			});
	}

	@Test
	public void chatOverrideConnectionProperties() {

		this.contextRunner.withPropertyValues(
		// @formatter:off
				"spring.ai.openai.base-url=http://TEST.BASE.URL",
				"spring.ai.openai.api-key=abc123",
				"spring.ai.openai.chat.base-url=http://TEST.BASE.URL2",
				"spring.ai.openai.chat.api-key=456",
				"spring.ai.openai.chat.model=MODEL_XYZ",
				"spring.ai.openai.chat.temperature=0.55")
				// @formatter:on
			.withConfiguration(
					AutoConfigurations.of(OpenAiChatAutoConfiguration.class, ToolCallingAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(OpenAiChatProperties.class);
				var connectionProperties = context.getBean(OpenAiConnectionProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("abc123");
				assertThat(connectionProperties.getBaseUrl()).isEqualTo("http://TEST.BASE.URL");

				assertThat(chatProperties.getApiKey()).isEqualTo("456");
				assertThat(chatProperties.getBaseUrl()).isEqualTo("http://TEST.BASE.URL2");

				var options = chatProperties.toOptions();
				assertThat(options.getModel()).isEqualTo("MODEL_XYZ");
				assertThat(options.getTemperature()).isEqualTo(0.55);
			});
	}

	@Test
	public void chatOptionsTest() {

		this.contextRunner
			.withPropertyValues(// @formatter:off
				"spring.ai.openai.api-key=API_KEY",
				"spring.ai.openai.base-url=http://TEST.BASE.URL",

				"spring.ai.openai.chat.model=MODEL_XYZ",
				"spring.ai.openai.chat.frequencyPenalty=-1.5",
				"spring.ai.openai.chat.logitBias.myTokenId=-5",
				"spring.ai.openai.chat.maxTokens=123",
				"spring.ai.openai.chat.n=10",
				"spring.ai.openai.chat.presencePenalty=0",
				"spring.ai.openai.chat.seed=66",
				"spring.ai.openai.chat.stop=boza,koza",
				"spring.ai.openai.chat.temperature=0.55",
				"spring.ai.openai.chat.topP=0.56",
				"spring.ai.openai.chat.user=userXYZ",
				"spring.ai.openai.chat.toolChoice={\"type\":\"function\",\"function\":{\"name\":\"toolChoiceFunctionName\"}}",
				"spring.ai.openai.chat.streamOptions.includeUsage=true",
				"spring.ai.openai.chat.streamOptions.includeObfuscation=true",
				"spring.ai.openai.chat.streamOptions.additionalProperties.foo=bar"

			)
			// @formatter:on
			.withConfiguration(
					AutoConfigurations.of(OpenAiChatAutoConfiguration.class, ToolCallingAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(OpenAiChatProperties.class);
				var connectionProperties = context.getBean(OpenAiConnectionProperties.class);

				assertThat(connectionProperties.getBaseUrl()).isEqualTo("http://TEST.BASE.URL");
				assertThat(connectionProperties.getApiKey()).isEqualTo("API_KEY");

				var options = chatProperties.toOptions();
				assertThat(options.getModel()).isEqualTo("MODEL_XYZ");
				assertThat(options.getFrequencyPenalty()).isEqualTo(-1.5);
				assertThat(options.getLogitBias().get("myTokenId")).isEqualTo(-5);
				assertThat(options.getMaxTokens()).isEqualTo(123);
				assertThat(options.getN()).isEqualTo(10);
				assertThat(options.getPresencePenalty()).isEqualTo(0);
				assertThat(options.getSeed()).isEqualTo(66);
				assertThat(options.getStop()).contains("boza", "koza");
				assertThat(options.getTemperature()).isEqualTo(0.55);
				assertThat(options.getTopP()).isEqualTo(0.56);

				JSONAssert.assertEquals("{\"type\":\"function\",\"function\":{\"name\":\"toolChoiceFunctionName\"}}",
						"" + options.getToolChoice(), JSONCompareMode.LENIENT);

				assertThat(options.getUser()).isEqualTo("userXYZ");

				assertThat(options.getStreamOptions()).isNotNull();
				assertThat(options.getStreamOptions().includeObfuscation()).isTrue();
				assertThat(options.getStreamOptions().additionalProperties().get("foo")).isEqualTo("bar");
			});
	}

}
