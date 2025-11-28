/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.model.openaisdk.autoconfigure;

import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import org.springframework.ai.utils.SpringAiTestAutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit Tests for {@link OpenAiConnectionProperties}, {@link OpenAiSdkChatProperties} and
 * {@link OpenAiSdkEmbeddingProperties}.
 *
 * @author Christian Tzolov
 */
public class OpenAiSdkChatPropertiesTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();

	@Test
	public void chatProperties() {

		this.contextRunner.withPropertyValues(
		// @formatter:off
				"spring.ai.openai-sdk.base-url=http://TEST.BASE.URL",
				"spring.ai.openai-sdk.api-key=abc123",
				"spring.ai.openai-sdk.chat.options.model=MODEL_XYZ",
				"spring.ai.openai-sdk.chat.options.temperature=0.55")
				// @formatter:on
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiSdkChatAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(OpenAiSdkChatProperties.class);
				var connectionProperties = context.getBean(OpenAiSdkConnectionProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("abc123");
				assertThat(connectionProperties.getBaseUrl()).isEqualTo("http://TEST.BASE.URL");

				assertThat(chatProperties.getApiKey()).isNull();
				assertThat(chatProperties.getBaseUrl()).isNull();

				assertThat(chatProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
				assertThat(chatProperties.getOptions().getTemperature()).isEqualTo(0.55);
			});
	}

	@Test
	public void chatOverrideConnectionProperties() {

		this.contextRunner.withPropertyValues(
		// @formatter:off
				"spring.ai.openai-sdk.base-url=http://TEST.BASE.URL",
				"spring.ai.openai-sdk.api-key=abc123",
				"spring.ai.openai-sdk.chat.base-url=http://TEST.BASE.URL2",
				"spring.ai.openai-sdk.chat.api-key=456",
				"spring.ai.openai-sdk.chat.options.model=MODEL_XYZ",
				"spring.ai.openai-sdk.chat.options.temperature=0.55")
				// @formatter:on
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiSdkChatAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(OpenAiSdkChatProperties.class);
				var connectionProperties = context.getBean(OpenAiSdkConnectionProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("abc123");
				assertThat(connectionProperties.getBaseUrl()).isEqualTo("http://TEST.BASE.URL");

				assertThat(chatProperties.getApiKey()).isEqualTo("456");
				assertThat(chatProperties.getBaseUrl()).isEqualTo("http://TEST.BASE.URL2");

				assertThat(chatProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
				assertThat(chatProperties.getOptions().getTemperature()).isEqualTo(0.55);
			});
	}

	@Test
	public void chatOptionsTest() {

		this.contextRunner
			.withPropertyValues(// @formatter:off
				"spring.ai.openai-sdk.api-key=API_KEY",
				"spring.ai.openai-sdk.base-url=http://TEST.BASE.URL",

				"spring.ai.openai-sdk.chat.options.model=MODEL_XYZ",
				"spring.ai.openai-sdk.chat.options.frequencyPenalty=-1.5",
				"spring.ai.openai-sdk.chat.options.logitBias.myTokenId=-5",
				"spring.ai.openai-sdk.chat.options.maxTokens=123",
				"spring.ai.openai-sdk.chat.options.n=10",
				"spring.ai.openai-sdk.chat.options.presencePenalty=0",
				"spring.ai.openai-sdk.chat.options.seed=66",
				"spring.ai.openai-sdk.chat.options.stop=boza,koza",
				"spring.ai.openai-sdk.chat.options.temperature=0.55",
				"spring.ai.openai-sdk.chat.options.topP=0.56",
				"spring.ai.openai-sdk.chat.options.user=userXYZ",
				"spring.ai.openai-sdk.chat.options.toolChoice={\"type\":\"function\",\"function\":{\"name\":\"toolChoiceFunctionName\"}}",
				"spring.ai.openai-sdk.chat.options.streamOptions.includeUsage=true",
				"spring.ai.openai-sdk.chat.options.streamOptions.includeObfuscation=true",
				"spring.ai.openai-sdk.chat.options.streamOptions.additionalProperties.foo=bar"

			)
			// @formatter:on
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiSdkChatAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(OpenAiSdkChatProperties.class);
				var connectionProperties = context.getBean(OpenAiSdkConnectionProperties.class);

				assertThat(connectionProperties.getBaseUrl()).isEqualTo("http://TEST.BASE.URL");
				assertThat(connectionProperties.getApiKey()).isEqualTo("API_KEY");

				assertThat(chatProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
				assertThat(chatProperties.getOptions().getFrequencyPenalty()).isEqualTo(-1.5);
				assertThat(chatProperties.getOptions().getLogitBias().get("myTokenId")).isEqualTo(-5);
				assertThat(chatProperties.getOptions().getMaxTokens()).isEqualTo(123);
				assertThat(chatProperties.getOptions().getN()).isEqualTo(10);
				assertThat(chatProperties.getOptions().getPresencePenalty()).isEqualTo(0);
				assertThat(chatProperties.getOptions().getSeed()).isEqualTo(66);
				assertThat(chatProperties.getOptions().getStop()).contains("boza", "koza");
				assertThat(chatProperties.getOptions().getTemperature()).isEqualTo(0.55);
				assertThat(chatProperties.getOptions().getTopP()).isEqualTo(0.56);

				JSONAssert.assertEquals("{\"type\":\"function\",\"function\":{\"name\":\"toolChoiceFunctionName\"}}",
						"" + chatProperties.getOptions().getToolChoice(), JSONCompareMode.LENIENT);

				assertThat(chatProperties.getOptions().getUser()).isEqualTo("userXYZ");

				assertThat(chatProperties.getOptions().getStreamOptions()).isNotNull();
				assertThat(chatProperties.getOptions().getStreamOptions().includeObfuscation()).isTrue();
				assertThat(chatProperties.getOptions().getStreamOptions().additionalProperties().get("foo"))
					.isEqualTo("bar");
			});
	}

}
