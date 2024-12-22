/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.autoconfigure.bedrock.converse;

import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 *
 * Unit Tests for {@link BedrockConverseProxyChatProperties}.
 */
public class BedrockConverseProxyChatPropertiesTests {

	@Test
	public void chatOptionsTest() {

		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
				"spring.ai.bedrock.converse.chat.options.model=MODEL_XYZ",

				"spring.ai.bedrock.converse.chat.options.max-tokens=123",
				"spring.ai.bedrock.converse.chat.options.metadata.user-id=MyUserId",
				"spring.ai.bedrock.converse.chat.options.stop_sequences=boza,koza",

				"spring.ai.bedrock.converse.chat.options.temperature=0.55",
				"spring.ai.bedrock.converse.chat.options.top-p=0.56",
				"spring.ai.bedrock.converse.chat.options.top-k=100"
				)
			// @formatter:on
			.withConfiguration(AutoConfigurations.of(BedrockConverseProxyChatAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(BedrockConverseProxyChatProperties.class);

				assertThat(chatProperties.isEnabled()).isTrue();

				assertThat(chatProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
				assertThat(chatProperties.getOptions().getMaxTokens()).isEqualTo(123);
				assertThat(chatProperties.getOptions().getStopSequences()).contains("boza", "koza");
				assertThat(chatProperties.getOptions().getTemperature()).isEqualTo(0.55);
				assertThat(chatProperties.getOptions().getTopP()).isEqualTo(0.56);
				assertThat(chatProperties.getOptions().getTopK()).isEqualTo(100);

			});
	}

	@Test
	public void chatCompletionDisabled() {

		// It is enabled by default
		new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(BedrockConverseProxyChatAutoConfiguration.class))
			.run(context -> assertThat(context.getBeansOfType(BedrockConverseProxyChatProperties.class)).isNotEmpty());

		// Explicitly enable the chat auto-configuration.
		new ApplicationContextRunner().withPropertyValues("spring.ai.bedrock.converse.chat.enabled=true")
			.withConfiguration(AutoConfigurations.of(BedrockConverseProxyChatAutoConfiguration.class))
			.run(context -> assertThat(context.getBeansOfType(BedrockConverseProxyChatProperties.class)).isNotEmpty());

		// Explicitly disable the chat auto-configuration.
		new ApplicationContextRunner().withPropertyValues("spring.ai.bedrock.converse.chat.enabled=false")
			.withConfiguration(AutoConfigurations.of(BedrockConverseProxyChatAutoConfiguration.class))
			.run(context -> assertThat(context.getBeansOfType(BedrockConverseProxyChatProperties.class)).isEmpty());
	}

}
