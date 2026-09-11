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

package org.springframework.ai.model.anthropic.autoconfigure;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import org.springframework.ai.anthropic.AnthropicBatch;
import org.springframework.ai.anthropic.AnthropicBatchModel;
import org.springframework.ai.anthropic.AnthropicBatchRequest;
import org.springframework.ai.anthropic.AnthropicBatchResult;
import org.springframework.ai.anthropic.DefaultAnthropicBatchModel;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link AnthropicBatchAutoConfiguration}: the batch model must be opt-in, must
 * back off when the application defines its own bean, and must inherit the shared
 * Anthropic connection and chat defaults.
 *
 * @author Ricken Bazolo
 */
class AnthropicBatchAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(ToolCallingAutoConfiguration.class,
				AnthropicChatAutoConfiguration.class, AnthropicBatchAutoConfiguration.class))
		.withPropertyValues("spring.ai.anthropic.api-key=test-key");

	@Test
	void batchModelIsNotCreatedByDefault() {
		this.contextRunner.run(context -> assertThat(context).doesNotHaveBean(AnthropicBatchModel.class));
	}

	@Test
	void batchModelIsNotCreatedWhenExplicitlyDisabled() {
		this.contextRunner.withPropertyValues("spring.ai.anthropic.batch.enabled=false")
			.run(context -> assertThat(context).doesNotHaveBean(AnthropicBatchModel.class));
	}

	@Test
	void batchModelIsCreatedWhenEnabled() {
		this.contextRunner.withPropertyValues("spring.ai.anthropic.batch.enabled=true").run(context -> {
			assertThat(context).hasSingleBean(AnthropicBatchModel.class);
			assertThat(context.getBean(AnthropicBatchModel.class)).isInstanceOf(DefaultAnthropicBatchModel.class);
		});
	}

	@Test
	void batchModelInheritsConnectionAndChatDefaults() {
		this.contextRunner
			.withPropertyValues("spring.ai.anthropic.batch.enabled=true", "spring.ai.anthropic.base-url=https://proxy",
					"spring.ai.anthropic.timeout=45s", "spring.ai.anthropic.max-retries=5",
					"spring.ai.anthropic.chat.model=claude-sonnet-4-5", "spring.ai.anthropic.chat.max-tokens=1234")
			.run(context -> {
				var options = context.getBean(DefaultAnthropicBatchModel.class).getOptions();
				assertThat(options.getApiKey()).isEqualTo("test-key");
				assertThat(options.getBaseUrl()).isEqualTo("https://proxy");
				assertThat(options.getTimeout()).isEqualTo(Duration.ofSeconds(45));
				assertThat(options.getMaxRetries()).isEqualTo(5);
				assertThat(options.getModel()).isEqualTo("claude-sonnet-4-5");
				assertThat(options.getMaxTokens()).isEqualTo(1234);
			});
	}

	@Test
	void batchPropertiesOverrideChatDefaults() {
		this.contextRunner
			.withPropertyValues("spring.ai.anthropic.batch.enabled=true",
					"spring.ai.anthropic.chat.model=claude-sonnet-4-5", "spring.ai.anthropic.chat.max-tokens=1234",
					"spring.ai.anthropic.batch.model=claude-haiku-4-5", "spring.ai.anthropic.batch.max-tokens=64")
			.run(context -> {
				var options = context.getBean(DefaultAnthropicBatchModel.class).getOptions();
				assertThat(options.getModel()).isEqualTo("claude-haiku-4-5");
				assertThat(options.getMaxTokens()).isEqualTo(64);
			});
	}

	@Test
	void applicationDefinedBatchModelWins() {
		this.contextRunner.withPropertyValues("spring.ai.anthropic.batch.enabled=true")
			.withUserConfiguration(CustomBatchModelConfiguration.class)
			.run(context -> {
				assertThat(context).hasSingleBean(AnthropicBatchModel.class);
				assertThat(context.getBean(AnthropicBatchModel.class))
					.isNotInstanceOf(DefaultAnthropicBatchModel.class);
			});
	}

	@Configuration(proxyBeanMethods = false)
	static class CustomBatchModelConfiguration {

		@Bean
		AnthropicBatchModel customBatchModel() {
			return new AnthropicBatchModel() {

				@Override
				public AnthropicBatch submit(List<AnthropicBatchRequest> requests) {
					throw new UnsupportedOperationException();
				}

				@Override
				public AnthropicBatch retrieve(String batchId) {
					throw new UnsupportedOperationException();
				}

				@Override
				public Flux<AnthropicBatchResult> results(String batchId) {
					return Flux.empty();
				}

				@Override
				public AnthropicBatch cancel(String batchId) {
					throw new UnsupportedOperationException();
				}

				@Override
				public void delete(String batchId) {
				}

			};
		}

	}

}
