/*
 * Copyright 2023-2023 the original author or authors.
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

package org.springframework.ai.autoconfigure.ollama;

import org.junit.jupiter.api.Test;

import org.springframework.ai.ollama.OllamaChatClient;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 * @since 0.8.0
 */
public class OllamaChatAutoConfigurationTests {

	@Test
	public void propertiesTest() {

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.ollama.chat.enabled=true", "spring.ai.ollama.chat.model=MODEL_XYZ",
					"spring.ai.ollama.chat.temperature=0.55", "spring.ai.ollama.chat.topP=0.55",
					"spring.ai.ollama.chat.topK=123")
			.withConfiguration(AutoConfigurations.of(OllamaChatAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(OllamaChatProperties.class);

				assertThat(chatProperties.isEnabled()).isTrue();
				assertThat(chatProperties.getModel()).isEqualTo("MODEL_XYZ");

				assertThat(chatProperties.getTemperature()).isEqualTo(0.55f);
				assertThat(chatProperties.getTopP()).isEqualTo(0.55f);

				assertThat(chatProperties.getTopK()).isEqualTo(123);
			});
	}

	@Test
	public void enablingDisablingTest() {

		// It is enabled by default
		new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(OllamaChatAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OllamaChatProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OllamaChatClient.class)).isNotEmpty();
			});

		// Explicitly enable the chat auto-configuration.
		new ApplicationContextRunner().withPropertyValues("spring.ai.ollama.chat.enabled=true")
			.withConfiguration(AutoConfigurations.of(OllamaChatAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OllamaChatProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OllamaChatClient.class)).isNotEmpty();
			});

		// Explicitly disable the chat auto-configuration.
		new ApplicationContextRunner().withPropertyValues("spring.ai.ollama.chat.enabled=false")
			.withConfiguration(AutoConfigurations.of(OllamaChatAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OllamaChatProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(OllamaChatClient.class)).isEmpty();
			});
	}

}
