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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.openaisdk.OpenAiSdkImageModel;
import org.springframework.ai.utils.SpringAiTestAutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".*")
public class OpenAiSdkImageAutoConfigurationIT {

	private static final Log logger = LogFactory.getLog(OpenAiSdkImageAutoConfigurationIT.class);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.openai-sdk.apiKey=" + System.getenv("OPENAI_API_KEY"));

	@Test
	void generateImage() {
		this.contextRunner.withPropertyValues("spring.ai.openai-sdk.image.options.size=1024x1024")
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiSdkImageAutoConfiguration.class))
			.run(context -> {
				OpenAiSdkImageModel imageModel = context.getBean(OpenAiSdkImageModel.class);
				ImageResponse imageResponse = imageModel.call(new ImagePrompt("forest"));
				assertThat(imageResponse.getResults()).hasSize(1);
				assertThat(imageResponse.getResult().getOutput().getUrl()).isNotEmpty();
				logger.info("Generated image: " + imageResponse.getResult().getOutput().getUrl());
			});
	}

	@Test
	void generateImageWithModel() {
		// The 256x256 size is supported by dall-e-2, but not by dall-e-3.
		this.contextRunner
			.withPropertyValues("spring.ai.openai-sdk.image.options.model=dall-e-2",
					"spring.ai.openai-sdk.image.options.size=256x256")
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiSdkImageAutoConfiguration.class))
			.run(context -> {
				OpenAiSdkImageModel imageModel = context.getBean(OpenAiSdkImageModel.class);
				ImageResponse imageResponse = imageModel.call(new ImagePrompt("forest"));
				assertThat(imageResponse.getResults()).hasSize(1);
				assertThat(imageResponse.getResult().getOutput().getUrl()).isNotEmpty();
				logger.info("Generated image: " + imageResponse.getResult().getOutput().getUrl());
			});
	}

	@Test
	void imageActivation() {
		this.contextRunner
			.withPropertyValues("spring.ai.openai-sdk.api-key=API_KEY",
					"spring.ai.openai-sdk.base-url=http://TEST.BASE.URL", "spring.ai.model.image=none")
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiSdkImageAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiSdkImageProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAiSdkImageModel.class)).isEmpty();
			});

		this.contextRunner
			.withPropertyValues("spring.ai.openai-sdk.api-key=API_KEY",
					"spring.ai.openai-sdk.base-url=http://TEST.BASE.URL")
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiSdkImageAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiSdkImageProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAiSdkImageModel.class)).isNotEmpty();
			});

		this.contextRunner
			.withPropertyValues("spring.ai.openai-sdk.api-key=API_KEY",
					"spring.ai.openai-sdk.base-url=http://TEST.BASE.URL", "spring.ai.model.image=openai-sdk")
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiSdkImageAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiSdkImageProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAiSdkImageModel.class)).isNotEmpty();
			});

	}

	@Test
	public void imageOptionsTest() {
		this.contextRunner.withPropertyValues(
		// @formatter:off
			"spring.ai.openai-sdk.api-key=API_KEY",
			"spring.ai.openai-sdk.base-url=http://TEST.BASE.URL",
			"spring.ai.openai-sdk.image.options.n=3",
			"spring.ai.openai-sdk.image.options.model=MODEL_XYZ",
			"spring.ai.openai-sdk.image.options.quality=hd",
			"spring.ai.openai-sdk.image.options.response_format=url",
			"spring.ai.openai-sdk.image.options.size=1024x1024",
			"spring.ai.openai-sdk.image.options.width=1024",
			"spring.ai.openai-sdk.image.options.height=1024",
			"spring.ai.openai-sdk.image.options.style=vivid",
			"spring.ai.openai-sdk.image.options.user=userXYZ") // @formatter:on
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiSdkImageAutoConfiguration.class))
			.run(context -> {
				var imageProperties = context.getBean(OpenAiSdkImageProperties.class);
				var connectionProperties = context.getBean(OpenAiSdkConnectionProperties.class);

				assertThat(connectionProperties.getBaseUrl()).isEqualTo("http://TEST.BASE.URL");
				assertThat(connectionProperties.getApiKey()).isEqualTo("API_KEY");

				assertThat(imageProperties.getOptions().getN()).isEqualTo(3);
				assertThat(imageProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
				assertThat(imageProperties.getOptions().getQuality()).isEqualTo("hd");
				assertThat(imageProperties.getOptions().getResponseFormat()).isEqualTo("url");
				assertThat(imageProperties.getOptions().getSize()).isEqualTo("1024x1024");
				assertThat(imageProperties.getOptions().getWidth()).isEqualTo(1024);
				assertThat(imageProperties.getOptions().getHeight()).isEqualTo(1024);
				assertThat(imageProperties.getOptions().getStyle()).isEqualTo("vivid");
				assertThat(imageProperties.getOptions().getUser()).isEqualTo("userXYZ");
			});
	}

}
