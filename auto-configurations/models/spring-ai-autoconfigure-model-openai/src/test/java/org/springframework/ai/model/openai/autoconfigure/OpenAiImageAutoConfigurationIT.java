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

package org.springframework.ai.model.openai.autoconfigure;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.openai.OpenAiImageModel;
import org.springframework.ai.openai.api.OpenAiImageApi;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link OpenAiImageAutoConfiguration}.
 *
 * @author Alexandros Pappas
 * @since 1.1.0
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class OpenAiImageAutoConfigurationIT {

	private static final Log logger = LogFactory.getLog(OpenAiImageAutoConfigurationIT.class);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.openai.apiKey=" + System.getenv("OPENAI_API_KEY"))
		.withConfiguration(
				AutoConfigurations.of(SpringAiRetryAutoConfiguration.class, RestClientAutoConfiguration.class,
						WebClientAutoConfiguration.class, ToolCallingAutoConfiguration.class));

	@Test
	void imageModelAutoConfigured() {
		this.contextRunner.withConfiguration(AutoConfigurations.of(OpenAiImageAutoConfiguration.class)).run(context -> {
			assertThat(context.getBeansOfType(OpenAiImageModel.class)).isNotEmpty();
			assertThat(context.getBeansOfType(OpenAiImageApi.class)).isNotEmpty();
		});
	}

	@Test
	void generateImage() {
		this.contextRunner
			.withPropertyValues("spring.ai.openai.image.options.model=dall-e-2",
					"spring.ai.openai.image.options.response-format=b64_json")
			.withConfiguration(AutoConfigurations.of(OpenAiImageAutoConfiguration.class))
			.run(context -> {
				OpenAiImageModel imageModel = context.getBean(OpenAiImageModel.class);
				ImagePrompt prompt = new ImagePrompt("A simple red circle");
				ImageResponse response = imageModel.call(prompt);

				assertThat(response).isNotNull();
				assertThat(response.getResults()).hasSize(1);
				assertThat(response.getResult().getOutput().getB64Json()).isNotEmpty();

				logger.info("Generated image with base64 length: "
						+ response.getResult().getOutput().getB64Json().length());
			});
	}

	@Test
	void imageModelDisabled() {
		this.contextRunner.withPropertyValues("spring.ai.model.image=none")
			.withConfiguration(AutoConfigurations.of(OpenAiImageAutoConfiguration.class))
			.run(context -> assertThat(context.getBeansOfType(OpenAiImageModel.class)).isEmpty());
	}

	@Test
	void imageModelExplicitlyEnabled() {
		this.contextRunner.withPropertyValues("spring.ai.model.image=openai")
			.withConfiguration(AutoConfigurations.of(OpenAiImageAutoConfiguration.class))
			.run(context -> assertThat(context.getBeansOfType(OpenAiImageModel.class)).isNotEmpty());
	}

}
