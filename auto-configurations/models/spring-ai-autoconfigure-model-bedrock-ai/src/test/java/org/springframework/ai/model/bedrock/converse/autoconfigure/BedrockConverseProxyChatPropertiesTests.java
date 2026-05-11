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

package org.springframework.ai.model.bedrock.converse.autoconfigure;

import org.junit.jupiter.api.Test;

import org.springframework.ai.bedrock.converse.BedrockProxyChatModel;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 * @author Pawel Potaczala
 * @author Issam El-atif
 * @author Sebastien Deleuze
 *
 * Unit Tests for {@link BedrockConverseProxyChatProperties}.
 */
public class BedrockConverseProxyChatPropertiesTests {

	@Test
	public void chatOptionsTest() {

		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
				"spring.ai.bedrock.converse.chat.model=MODEL_XYZ",

				"spring.ai.bedrock.converse.chat.max-tokens=123",
				"spring.ai.bedrock.converse.chat.stop-sequences=boza,koza",

				"spring.ai.bedrock.converse.chat.temperature=0.55",
				"spring.ai.bedrock.converse.chat.top-p=0.56",
				"spring.ai.bedrock.converse.chat.top-k=100"
				)
			// @formatter:on
			.withConfiguration(AutoConfigurations.of(BedrockConverseProxyChatAutoConfiguration.class,
					ToolCallingAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(BedrockConverseProxyChatProperties.class);

				assertThat(chatProperties.getModel()).isEqualTo("MODEL_XYZ");
				assertThat(chatProperties.getMaxTokens()).isEqualTo(123);
				assertThat(chatProperties.getStopSequences()).contains("boza", "koza");
				assertThat(chatProperties.getTemperature()).isEqualTo(0.55);
				assertThat(chatProperties.getTopP()).isEqualTo(0.56);
				assertThat(chatProperties.getTopK()).isEqualTo(100);

			});
	}

	@Test
	public void chatCompletionDisabled() {

		// It is enabled by default
		new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(BedrockConverseProxyChatAutoConfiguration.class,
					ToolCallingAutoConfiguration.class))
			.run(context -> assertThat(context.getBeansOfType(BedrockConverseProxyChatProperties.class)).isNotEmpty());

		// Explicitly enable the chat auto-configuration.
		new ApplicationContextRunner().withPropertyValues("spring.ai.model.chat=bedrock-converse")
			.withConfiguration(AutoConfigurations.of(BedrockConverseProxyChatAutoConfiguration.class,
					ToolCallingAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(BedrockConverseProxyChatProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(BedrockProxyChatModel.class)).isNotEmpty();
			});

		// Explicitly disable the chat auto-configuration.
		new ApplicationContextRunner().withPropertyValues("spring.ai.model.chat=none")
			.withConfiguration(AutoConfigurations.of(BedrockConverseProxyChatAutoConfiguration.class,
					ToolCallingAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(BedrockConverseProxyChatProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(BedrockProxyChatModel.class)).isEmpty();
			});
	}

}
