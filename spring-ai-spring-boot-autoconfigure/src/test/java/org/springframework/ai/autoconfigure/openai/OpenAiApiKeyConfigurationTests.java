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

package org.springframework.ai.autoconfigure.openai;

import org.junit.jupiter.api.Test;

import org.springframework.ai.model.ApiKey;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.openai.api.OpenAiApiKey;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for OpenAI ApiKey configuration behavior.
 *
 * @author Mark Pollack
 */
class OpenAiApiKeyConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.openai.apiKey=test-key")
		.withConfiguration(AutoConfigurations.of(OpenAiAutoConfiguration.class));

	@Configuration
	static class CustomConfiguration {

		@Bean
		@OpenAiApiKey
		public ApiKey customOpenAiKey() {
			return new ApiKey() {
				@Override
				public String getValue() {
					return "custom-key";
				}
			};
		}

	}

	@Test
	void defaultApiKeyConfiguration() {
		this.contextRunner.run(context -> {
			ApiKey apiKey = context.getBean(ApiKey.class, ApiKey.class);
			assertThat(apiKey).isNotNull();
			assertThat(apiKey).isInstanceOf(SimpleApiKey.class);
			assertThat(apiKey.getValue()).isEqualTo("test-key");
		});
	}

	@Test
	void customApiKeyConfiguration() {
		this.contextRunner.withUserConfiguration(CustomConfiguration.class).run(context -> {
			ApiKey apiKey = context.getBean(ApiKey.class, ApiKey.class);
			assertThat(apiKey).isNotNull();
			assertThat(apiKey).isNotInstanceOf(SimpleApiKey.class);
			assertThat(apiKey.getValue()).isEqualTo("custom-key");
		});
	}

	@Test
	void multipleUnqualifiedApiKeysFailsToStart() {
		this.contextRunner.withUserConfiguration(MultipleUnqualifiedApiKeysConfiguration.class).run(context -> {
			assertThat(context).hasFailed();
			Throwable failure = context.getStartupFailure();
			while (failure.getCause() != null && !(failure instanceof NoSuchBeanDefinitionException)) {
				failure = failure.getCause();
			}
			assertThat(failure).isInstanceOf(NoSuchBeanDefinitionException.class)
				.hasMessageContaining(
						"No qualifying bean of type 'org.springframework.ai.model.ApiKey' available: expected at least 1 bean which qualifies as autowire candidate. Dependency annotations: {@org.springframework.ai.openai.api.OpenAiApiKey()}");
		});
	}

	@Configuration
	static class MultipleUnqualifiedApiKeysConfiguration {

		@Bean
		public ApiKey openAiKey() {
			return () -> "openai-key";
		}

		@Bean
		public ApiKey otherKey() {
			return () -> "other-key";
		}

	}

	@Configuration
	static class MultipleQualifiedApiKeysConfiguration {

		@Bean
		@OpenAiApiKey
		public ApiKey openAiKey() {
			return () -> "openai-key";
		}

		@Bean
		@OtherApiKey
		public ApiKey otherKey() {
			return () -> "other-key";
		}

	}

}
