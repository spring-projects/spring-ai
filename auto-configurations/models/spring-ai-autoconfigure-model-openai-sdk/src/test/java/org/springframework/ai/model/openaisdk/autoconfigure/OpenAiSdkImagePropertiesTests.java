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

import org.springframework.ai.utils.SpringAiTestAutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit Tests for {@link OpenAiConnectionProperties} and {@link OpenAiSdkImageProperties}.
 *
 * @author Christian Tzolov
 */
public class OpenAiSdkImagePropertiesTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();

	@Test
	public void imageProperties() {

		this.contextRunner.withPropertyValues(
		// @formatter:off
				"spring.ai.openai-sdk.base-url=http://TEST.BASE.URL",
				"spring.ai.openai-sdk.api-key=abc123",
				"spring.ai.openai-sdk.image.options.model=MODEL_XYZ",
				"spring.ai.openai-sdk.image.options.n=2")
				// @formatter:on
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiSdkImageAutoConfiguration.class))
			.run(context -> {
				var imageProperties = context.getBean(OpenAiSdkImageProperties.class);
				var connectionProperties = context.getBean(OpenAiSdkConnectionProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("abc123");
				assertThat(connectionProperties.getBaseUrl()).isEqualTo("http://TEST.BASE.URL");

				assertThat(imageProperties.getApiKey()).isNull();
				assertThat(imageProperties.getBaseUrl()).isNull();

				assertThat(imageProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
				assertThat(imageProperties.getOptions().getN()).isEqualTo(2);
			});
	}

	@Test
	public void imageOverrideConnectionProperties() {

		this.contextRunner.withPropertyValues(
		// @formatter:off
				"spring.ai.openai-sdk.base-url=http://TEST.BASE.URL",
				"spring.ai.openai-sdk.api-key=abc123",
				"spring.ai.openai-sdk.image.base-url=http://TEST.BASE.URL2",
				"spring.ai.openai-sdk.image.api-key=456",
				"spring.ai.openai-sdk.image.options.model=MODEL_XYZ",
				"spring.ai.openai-sdk.image.options.n=2")
				// @formatter:on
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiSdkImageAutoConfiguration.class))
			.run(context -> {
				var imageProperties = context.getBean(OpenAiSdkImageProperties.class);
				var connectionProperties = context.getBean(OpenAiSdkConnectionProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("abc123");
				assertThat(connectionProperties.getBaseUrl()).isEqualTo("http://TEST.BASE.URL");

				assertThat(imageProperties.getApiKey()).isEqualTo("456");
				assertThat(imageProperties.getBaseUrl()).isEqualTo("http://TEST.BASE.URL2");

				assertThat(imageProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
				assertThat(imageProperties.getOptions().getN()).isEqualTo(2);
			});
	}

	@Test
	public void imageOptionsTest() {

		this.contextRunner
			.withPropertyValues(// @formatter:off
				"spring.ai.openai-sdk.api-key=API_KEY",
				"spring.ai.openai-sdk.base-url=http://TEST.BASE.URL",

				"spring.ai.openai-sdk.image.options.model=MODEL_XYZ",
				"spring.ai.openai-sdk.image.options.n=3",
				"spring.ai.openai-sdk.image.options.width=1024",
				"spring.ai.openai-sdk.image.options.height=1792",
				"spring.ai.openai-sdk.image.options.quality=hd",
				"spring.ai.openai-sdk.image.options.responseFormat=url",
				"spring.ai.openai-sdk.image.options.size=1024x1792",
				"spring.ai.openai-sdk.image.options.style=vivid",
				"spring.ai.openai-sdk.image.options.user=userXYZ"
			)
			// @formatter:on
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiSdkImageAutoConfiguration.class))
			.run(context -> {
				var imageProperties = context.getBean(OpenAiSdkImageProperties.class);
				var connectionProperties = context.getBean(OpenAiSdkConnectionProperties.class);

				assertThat(connectionProperties.getBaseUrl()).isEqualTo("http://TEST.BASE.URL");
				assertThat(connectionProperties.getApiKey()).isEqualTo("API_KEY");

				assertThat(imageProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
				assertThat(imageProperties.getOptions().getN()).isEqualTo(3);
				assertThat(imageProperties.getOptions().getWidth()).isEqualTo(1024);
				assertThat(imageProperties.getOptions().getHeight()).isEqualTo(1792);
				assertThat(imageProperties.getOptions().getQuality()).isEqualTo("hd");
				assertThat(imageProperties.getOptions().getResponseFormat()).isEqualTo("url");
				assertThat(imageProperties.getOptions().getSize()).isEqualTo("1024x1792");
				assertThat(imageProperties.getOptions().getStyle()).isEqualTo("vivid");
				assertThat(imageProperties.getOptions().getUser()).isEqualTo("userXYZ");
			});
	}

}
