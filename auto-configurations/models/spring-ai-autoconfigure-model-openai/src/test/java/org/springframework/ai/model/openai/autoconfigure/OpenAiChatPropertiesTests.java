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

import java.util.List;

import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit Tests for {@link OpenAiCommonProperties}, {@link OpenAiChatProperties} and
 * {@link OpenAiEmbeddingProperties}.
 *
 * @author Christian Tzolov
 * @author Sebastien Deleuze
 * @author guan xu
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
				var commonProperties = context.getBean(OpenAiCommonProperties.class);

				assertThat(commonProperties.getApiKey()).isEqualTo("abc123");
				assertThat(commonProperties.getBaseUrl()).isEqualTo("http://TEST.BASE.URL");

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
				"spring.ai.openai.chat.frequency-penalty=-1.5",
				"spring.ai.openai.chat.logit-bias.myTokenId=-5",
				"spring.ai.openai.chat.max-tokens=123",
				"spring.ai.openai.chat.n=10",
				"spring.ai.openai.chat.presence-penalty=0",
				"spring.ai.openai.chat.seed=66",
				"spring.ai.openai.chat.stop=boza,koza",
				"spring.ai.openai.chat.temperature=0.55",
				"spring.ai.openai.chat.top-p=0.56",
				"spring.ai.openai.chat.user=userXYZ",
				"spring.ai.openai.chat.tool-choice={\"type\":\"function\",\"function\":{\"name\":\"toolChoiceFunctionName\"}}",
				"spring.ai.openai.chat.stream-options.include-usage=true",
				"spring.ai.openai.chat.stream-options.include-obfuscation=true",
				"spring.ai.openai.chat.stream-options.additional-properties.foo=bar",
				"spring.ai.openai.chat.prompt-cache-key=test-cache-key"

			)
			// @formatter:on
			.withConfiguration(
					AutoConfigurations.of(OpenAiChatAutoConfiguration.class, ToolCallingAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(OpenAiChatProperties.class);
				var commonProperties = context.getBean(OpenAiCommonProperties.class);

				assertThat(commonProperties.getBaseUrl()).isEqualTo("http://TEST.BASE.URL");
				assertThat(commonProperties.getApiKey()).isEqualTo("API_KEY");

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

				assertThat(options.getPromptCacheKey()).isEqualTo("test-cache-key");
			});
	}

	@Test
	public void chatToolChoiceStringValuesTest() {

		for (String toolChoice : List.of("none", "auto", "required")) {
			this.contextRunner.withPropertyValues("spring.ai.openai.api-key=API_KEY",
					"spring.ai.openai.base-url=http://TEST.BASE.URL", "spring.ai.openai.chat.tool-choice=" + toolChoice)
				.withConfiguration(
						AutoConfigurations.of(OpenAiChatAutoConfiguration.class, ToolCallingAutoConfiguration.class))
				.run(context -> {
					var chatProperties = context.getBean(OpenAiChatProperties.class);
					assertThat(chatProperties.toOptions().getToolChoice()).isEqualTo(toolChoice);
				});
		}
	}

	@Test
	public void chatExtraBodyTest() {

		this.contextRunner
			.withPropertyValues(// @formatter:off
				"spring.ai.openai.api-key=API_KEY",
				"spring.ai.openai.base-url=http://TEST.BASE.URL",

				"spring.ai.openai.chat.extra-body.key1=value1",
				"spring.ai.openai.chat.extra-body.key2=123",
				"spring.ai.openai.chat.extra-body.nested.key3=true"
			)
			// @formatter:on
			.withConfiguration(
					AutoConfigurations.of(OpenAiChatAutoConfiguration.class, ToolCallingAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(OpenAiChatProperties.class);

				assertThat(chatProperties.getExtraBody()).isNotNull();
				assertThat(chatProperties.getExtraBody()).containsEntry("key1", "value1");
				assertThat(chatProperties.getExtraBody()).containsEntry("key2", "123");
				assertThat(chatProperties.getExtraBody()).containsKey("nested");

				var options = chatProperties.toOptions();
				assertThat(options.getExtraBody()).isNotNull();
				assertThat(options.getExtraBody()).containsEntry("key1", "value1");
				assertThat(options.getExtraBody()).containsEntry("key2", "123");
				assertThat(options.getExtraBody()).containsKey("nested");
			});
	}

}
