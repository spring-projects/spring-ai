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

package org.springframework.ai.model.replicate.autoconfigure;

import org.junit.jupiter.api.Test;

import org.springframework.ai.replicate.ReplicateChatOptions;
import org.springframework.ai.replicate.ReplicateOptions;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for Replicate configuration properties.
 *
 * @author Rene Maierhofer
 */
class ReplicatePropertiesTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(ReplicateChatAutoConfiguration.class));

	@Test
	void testConnectionPropertiesBinding() {
		this.contextRunner
			.withPropertyValues("spring.ai.replicate.api-token=test-token",
					"spring.ai.replicate.base-url=https://127.0.0.1/v1")
			.run(context -> {
				ReplicateConnectionProperties properties = context.getBean(ReplicateConnectionProperties.class);
				assertThat(properties.getApiToken()).isEqualTo("test-token");
				assertThat(properties.getBaseUrl()).isEqualTo("https://127.0.0.1/v1");
			});
	}

	@Test
	void testConnectionPropertiesDefaults() {
		this.contextRunner.withPropertyValues("spring.ai.replicate.api-token=test-token").run(context -> {
			ReplicateConnectionProperties properties = context.getBean(ReplicateConnectionProperties.class);
			assertThat(properties.getBaseUrl()).isEqualTo(ReplicateConnectionProperties.DEFAULT_BASE_URL);
		});
	}

	@Test
	void testChatPropertiesWithInputParameters() {
		this.contextRunner
			.withPropertyValues("spring.ai.replicate.api-token=test-token",
					"spring.ai.replicate.chat.options.model=meta/meta-llama-3-8b-instruct",
					"spring.ai.replicate.chat.options.input.temperature=0.7",
					"spring.ai.replicate.chat.options.input.max_tokens=100",
					"spring.ai.replicate.chat.options.input.enabled=true")
			.run(context -> {
				ReplicateChatProperties properties = context.getBean(ReplicateChatProperties.class);
				ReplicateChatOptions options = properties.getOptions();

				assertThat(options.getInput()).isNotEmpty();
				assertThat(options.getInput().get("temperature")).isInstanceOf(Double.class).isEqualTo(0.7);
				assertThat(options.getInput().get("max_tokens")).isInstanceOf(Integer.class).isEqualTo(100);
				assertThat(options.getInput().get("enabled")).isInstanceOf(Boolean.class).isEqualTo(true);
			});
	}

	@Test
	void testMediaPropertiesBinding() {
		this.contextRunner
			.withPropertyValues("spring.ai.replicate.api-token=test-token",
					"spring.ai.replicate.media.options.model=black-forest-labs/flux-schnell",
					"spring.ai.replicate.media.options.version=media-version")
			.run(context -> {
				ReplicateMediaProperties properties = context.getBean(ReplicateMediaProperties.class);
				ReplicateOptions options = properties.getOptions();

				assertThat(options).isNotNull();
				assertThat(options.getModel()).isEqualTo("black-forest-labs/flux-schnell");
				assertThat(options.getVersion()).isEqualTo("media-version");
			});
	}

	@Test
	void testMediaPropertiesWithInputParameters() {
		this.contextRunner
			.withPropertyValues("spring.ai.replicate.api-token=test-token",
					"spring.ai.replicate.media.options.model=black-forest-labs/flux-schnell",
					"spring.ai.replicate.media.options.input.prompt=test prompt",
					"spring.ai.replicate.media.options.input.num_outputs=2")
			.run(context -> {
				ReplicateMediaProperties properties = context.getBean(ReplicateMediaProperties.class);
				ReplicateOptions options = properties.getOptions();

				assertThat(options.getInput()).isNotEmpty();
				assertThat(options.getInput().get("prompt")).isEqualTo("test prompt");
				assertThat(options.getInput().get("num_outputs")).isInstanceOf(Integer.class).isEqualTo(2);
			});
	}

	@Test
	void testStringPropertiesBinding() {
		this.contextRunner
			.withPropertyValues("spring.ai.replicate.api-token=test-token",
					"spring.ai.replicate.string.options.model=falcons-ai/nsfw_image_detection")
			.run(context -> {
				ReplicateStringProperties properties = context.getBean(ReplicateStringProperties.class);
				ReplicateOptions options = properties.getOptions();

				assertThat(options).isNotNull();
				assertThat(options.getModel()).isEqualTo("falcons-ai/nsfw_image_detection");
			});
	}

	@Test
	void testStructuredPropertiesBinding() {
		this.contextRunner
			.withPropertyValues("spring.ai.replicate.api-token=test-token",
					"spring.ai.replicate.structured.options.model=openai/clip")
			.run(context -> {
				ReplicateStructuredProperties properties = context.getBean(ReplicateStructuredProperties.class);
				ReplicateOptions options = properties.getOptions();

				assertThat(options).isNotNull();
				assertThat(options.getModel()).isEqualTo("openai/clip");
			});
	}

}
