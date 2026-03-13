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

package org.springframework.ai.model.mistralai.autoconfigure;

import org.junit.jupiter.api.Test;

import org.springframework.ai.mistralai.MistralAiChatModel;
import org.springframework.ai.mistralai.MistralAiEmbeddingModel;
import org.springframework.ai.mistralai.moderation.MistralAiModerationModel;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.webclient.autoconfigure.WebClientAutoConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit Tests for Mistral AI auto-configurations conditional enabling of models.
 *
 * @author Ilayaperumal Gopinathan
 * @author Ricken Bazolo
 * @author Issam El-atif
 */
public class MistralModelConfigurationTests {

	private final ApplicationContextRunner chatContextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.mistralai.apiKey=" + System.getenv("MISTRAL_AI_API_KEY"))
		.withConfiguration(AutoConfigurations.of(MistralAiChatAutoConfiguration.class,
				RestClientAutoConfiguration.class, SpringAiRetryAutoConfiguration.class,
				ToolCallingAutoConfiguration.class, WebClientAutoConfiguration.class));

	private final ApplicationContextRunner embeddingContextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.mistralai.apiKey=" + System.getenv("MISTRAL_AI_API_KEY"))
		.withConfiguration(AutoConfigurations.of(MistralAiEmbeddingAutoConfiguration.class,
				RestClientAutoConfiguration.class, SpringAiRetryAutoConfiguration.class));

	private final ApplicationContextRunner moderationContextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.mistralai.apiKey=" + System.getenv("MISTRAL_AI_API_KEY"))
		.withConfiguration(
				AutoConfigurations.of(MistralAiModerationAutoConfiguration.class, RestClientAutoConfiguration.class,
						SpringAiRetryAutoConfiguration.class, WebClientAutoConfiguration.class));

	@Test
	void chatModelActivation() {
		this.chatContextRunner.run(context -> {
			assertThat(context.getBeansOfType(MistralAiChatProperties.class)).isNotEmpty();
			assertThat(context.getBeansOfType(MistralAiChatModel.class)).isNotEmpty();
			assertThat(context.getBeansOfType(MistralAiEmbeddingProperties.class)).isEmpty();
			assertThat(context.getBeansOfType(MistralAiEmbeddingModel.class)).isEmpty();
			assertThat(context.getBeansOfType(MistralAiModerationProperties.class)).isEmpty();
			assertThat(context.getBeansOfType(MistralAiModerationModel.class)).isEmpty();
		});

		this.chatContextRunner.withPropertyValues("spring.ai.model.chat=none", "spring.ai.model.embedding=none")
			.run(context -> {
				assertThat(context.getBeansOfType(MistralAiChatProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(MistralAiChatModel.class)).isEmpty();
			});

		this.chatContextRunner.withPropertyValues("spring.ai.model.chat=mistral", "spring.ai.model.embedding=none")
			.run(context -> {
				assertThat(context.getBeansOfType(MistralAiChatProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(MistralAiChatModel.class)).isNotEmpty();
				assertThat(context.getBeansOfType(MistralAiEmbeddingProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(MistralAiEmbeddingModel.class)).isEmpty();
				assertThat(context.getBeansOfType(MistralAiModerationProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(MistralAiModerationModel.class)).isEmpty();
			});
	}

	@Test
	void embeddingModelActivation() {
		this.embeddingContextRunner
			.run(context -> assertThat(context.getBeansOfType(MistralAiEmbeddingModel.class)).isNotEmpty());

		this.embeddingContextRunner.withPropertyValues("spring.ai.model.embedding=none").run(context -> {
			assertThat(context.getBeansOfType(MistralAiEmbeddingProperties.class)).isEmpty();
			assertThat(context.getBeansOfType(MistralAiEmbeddingModel.class)).isEmpty();
		});

		this.embeddingContextRunner.withPropertyValues("spring.ai.model.embedding=mistral").run(context -> {
			assertThat(context.getBeansOfType(MistralAiEmbeddingProperties.class)).isNotEmpty();
			assertThat(context.getBeansOfType(MistralAiEmbeddingModel.class)).isNotEmpty();
		});
	}

	@Test
	void moderationModelActivation() {
		this.moderationContextRunner.run(context -> {
			assertThat(context.getBeansOfType(MistralAiModerationModel.class)).isNotEmpty();
			assertThat(context.getBeansOfType(MistralAiModerationProperties.class)).isNotEmpty();
			assertThat(context.getBeansOfType(MistralAiChatModel.class)).isEmpty();
			assertThat(context.getBeansOfType(MistralAiChatProperties.class)).isEmpty();
			assertThat(context.getBeansOfType(MistralAiEmbeddingProperties.class)).isEmpty();
			assertThat(context.getBeansOfType(MistralAiEmbeddingModel.class)).isEmpty();
		});

		this.moderationContextRunner.withPropertyValues("spring.ai.model.moderation=none").run(context -> {
			assertThat(context.getBeansOfType(MistralAiModerationProperties.class)).isEmpty();
			assertThat(context.getBeansOfType(MistralAiModerationModel.class)).isEmpty();
		});

		this.moderationContextRunner.withPropertyValues("spring.ai.model.moderation=mistral").run(context -> {
			assertThat(context.getBeansOfType(MistralAiModerationProperties.class)).isNotEmpty();
			assertThat(context.getBeansOfType(MistralAiModerationModel.class)).isNotEmpty();
		});

		this.moderationContextRunner
			.withPropertyValues("spring.ai.model.chat=none", "spring.ai.model.embedding=none",
					"spring.ai.model.moderation=mistral")
			.run(context -> {
				assertThat(context.getBeansOfType(MistralAiModerationModel.class)).isNotEmpty();
				assertThat(context.getBeansOfType(MistralAiEmbeddingModel.class)).isEmpty();
				assertThat(context.getBeansOfType(MistralAiChatModel.class)).isEmpty();
			});
	}

}
