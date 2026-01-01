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

package org.springframework.ai.model.huggingface.autoconfigure;

import org.junit.jupiter.api.Test;

import org.springframework.ai.utils.SpringAiTestAutoConfigurations;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link HuggingfaceChatAutoConfiguration}.
 *
 * @author Myeongdeok Kang
 */
public class HuggingfaceChatAutoConfigurationTests {

	@Test
	public void propertiesTest() {

		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
					"spring.ai.huggingface.api-key=TEST_API_KEY",
					"spring.ai.huggingface.chat.url=https://test.huggingface.co/v1",
					"spring.ai.huggingface.chat.options.model=meta-llama/Llama-3.2-3B-Instruct",
					"spring.ai.huggingface.chat.options.temperature=0.7",
					"spring.ai.huggingface.chat.options.maxTokens=512",
					"spring.ai.huggingface.chat.options.topP=0.9"
					// @formatter:on
		)
			.withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration.class))
			.withConfiguration(SpringAiTestAutoConfigurations.of(HuggingfaceApiAutoConfiguration.class,
					HuggingfaceChatAutoConfiguration.class))
			.run(context -> {
				assertThat(context).hasSingleBean(HuggingfaceChatProperties.class);
				assertThat(context).hasSingleBean(HuggingfaceConnectionProperties.class);

				var chatProperties = context.getBean(HuggingfaceChatProperties.class);
				var connectionProperties = context.getBean(HuggingfaceConnectionProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("TEST_API_KEY");
				assertThat(chatProperties.getUrl()).isEqualTo("https://test.huggingface.co/v1");
				assertThat(chatProperties.getOptions().getModel()).isEqualTo("meta-llama/Llama-3.2-3B-Instruct");
				assertThat(chatProperties.getOptions().getTemperature()).isEqualTo(0.7);
				assertThat(chatProperties.getOptions().getMaxTokens()).isEqualTo(512);
				assertThat(chatProperties.getOptions().getTopP()).isEqualTo(0.9);
			});
	}

	@Test
	public void chatActivationTest() {
		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.huggingface.api-key=TEST_API_KEY",
					"spring.ai.huggingface.chat.enabled=false")
			.withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration.class))
			.withConfiguration(SpringAiTestAutoConfigurations.of(HuggingfaceApiAutoConfiguration.class,
					HuggingfaceChatAutoConfiguration.class))
			.run(context -> assertThat(context).doesNotHaveBean("huggingfaceChatModel"));

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.huggingface.api-key=TEST_API_KEY", "spring.ai.huggingface.chat.enabled=true")
			.withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration.class))
			.withConfiguration(SpringAiTestAutoConfigurations.of(HuggingfaceApiAutoConfiguration.class,
					HuggingfaceChatAutoConfiguration.class))
			.run(context -> assertThat(context)
				.hasSingleBean(org.springframework.ai.huggingface.HuggingfaceChatModel.class));
	}

	@Test
	public void newParametersTest() {
		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
					"spring.ai.huggingface.api-key=TEST_API_KEY",
					"spring.ai.huggingface.chat.options.seed=42",
					"spring.ai.huggingface.chat.options.tool-prompt=You have access to tools:",
					"spring.ai.huggingface.chat.options.logprobs=true",
					"spring.ai.huggingface.chat.options.top-logprobs=3"
					// @formatter:on
		)
			.withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration.class))
			.withConfiguration(SpringAiTestAutoConfigurations.of(HuggingfaceApiAutoConfiguration.class,
					HuggingfaceChatAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(HuggingfaceChatProperties.class);

				assertThat(chatProperties.getOptions().getSeed()).isEqualTo(42);
				assertThat(chatProperties.getOptions().getToolPrompt()).isEqualTo("You have access to tools:");
				assertThat(chatProperties.getOptions().getLogprobs()).isTrue();
				assertThat(chatProperties.getOptions().getTopLogprobs()).isEqualTo(3);
			});
	}

}
