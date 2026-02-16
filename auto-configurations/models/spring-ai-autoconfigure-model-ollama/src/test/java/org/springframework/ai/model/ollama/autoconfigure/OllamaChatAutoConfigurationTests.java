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

package org.springframework.ai.model.ollama.autoconfigure;

import org.junit.jupiter.api.Test;

import org.springframework.ai.ollama.api.ThinkOption;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 * @author Nicolas Krier
 * @since 0.8.0
 */
class OllamaChatAutoConfigurationTests {

	@Test
	void propertiesTest() {
		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
						"spring.ai.ollama.base-url=TEST_BASE_URL",
						"spring.ai.ollama.chat.options.model=MODEL_XYZ",
						"spring.ai.ollama.chat.options.temperature=0.55",
						"spring.ai.ollama.chat.options.topP=0.56",
						"spring.ai.ollama.chat.options.topK=123")
						// @formatter:on

			.withConfiguration(BaseOllamaIT.ollamaAutoConfig(OllamaChatAutoConfiguration.class))
			.run(context -> {
				assertThat(context).hasNotFailed();

				var chatProperties = context.getBean(OllamaChatProperties.class);
				var connectionProperties = context.getBean(OllamaConnectionProperties.class);

				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");

				assertThat(chatProperties.getModel()).isEqualTo("MODEL_XYZ");

				assertThat(chatProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
				assertThat(chatProperties.getOptions().getTemperature()).isEqualTo(0.55);
				assertThat(chatProperties.getOptions().getTopP()).isEqualTo(0.56);
				assertThat(chatProperties.getOptions().getTopK()).isEqualTo(123);
			});
	}

	@Test
	void thinkBooleanPropertiesTest() {
		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
						"spring.ai.ollama.chat.options.model=qwen3:4b-thinking",
						"spring.ai.ollama.chat.options.think-option=enabled"
						// @formatter:on
		).withConfiguration(BaseOllamaIT.ollamaAutoConfig(OllamaChatAutoConfiguration.class)).run(context -> {
			assertThat(context).hasNotFailed();

			var chatProperties = context.getBean(OllamaChatProperties.class);

			assertThat(chatProperties.getModel()).isEqualTo("qwen3:4b-thinking");

			assertThat(chatProperties.getOptions().getModel()).isEqualTo("qwen3:4b-thinking");
			assertThat(chatProperties.getOptions().getThinkOption()).isEqualTo(ThinkOption.ThinkBoolean.ENABLED);
		});
	}

	@Test
	void thinkLevelPropertiesTest() {
		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
						"spring.ai.ollama.chat.options.model=gpt-oss:latest",
						"spring.ai.ollama.chat.options.think-option=low"
						// @formatter:on
		).withConfiguration(BaseOllamaIT.ollamaAutoConfig(OllamaChatAutoConfiguration.class)).run(context -> {
			assertThat(context).hasNotFailed();

			var chatProperties = context.getBean(OllamaChatProperties.class);

			assertThat(chatProperties.getModel()).isEqualTo("gpt-oss:latest");

			assertThat(chatProperties.getOptions().getModel()).isEqualTo("gpt-oss:latest");
			assertThat(chatProperties.getOptions().getThinkOption()).isEqualTo(ThinkOption.ThinkLevel.LOW);
		});
	}

}
