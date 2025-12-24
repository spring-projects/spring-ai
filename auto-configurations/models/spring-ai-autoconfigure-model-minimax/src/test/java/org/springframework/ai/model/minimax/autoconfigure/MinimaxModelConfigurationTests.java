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

package org.springframework.ai.model.minimax.autoconfigure;

import org.junit.jupiter.api.Test;

import org.springframework.ai.minimax.MiniMaxChatModel;
import org.springframework.ai.minimax.MiniMaxEmbeddingModel;
import org.springframework.ai.utils.SpringAiTestAutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit Tests for MiniMax auto-configurations' conditional enabling of models.
 *
 * @author Ilayaperumal Gopinathan
 * @author Issam El-atif
 */
public class MinimaxModelConfigurationTests {

	private final ApplicationContextRunner chatContextRunner = new ApplicationContextRunner()
		.withConfiguration(SpringAiTestAutoConfigurations.of(MiniMaxChatAutoConfiguration.class))
		.withPropertyValues("spring.ai.minimax.api-key=API_KEY", "spring.ai.minimax.base-url=TEST_BASE_URL");

	private final ApplicationContextRunner embeddingContextRunner = new ApplicationContextRunner()
		.withConfiguration(SpringAiTestAutoConfigurations.of(MiniMaxEmbeddingAutoConfiguration.class))
		.withPropertyValues("spring.ai.minimax.api-key=API_KEY", "spring.ai.minimax.base-url=TEST_BASE_URL");

	@Test
	void chatModelActivation() {
		this.chatContextRunner.run(context -> {
			assertThat(context.getBeansOfType(MiniMaxChatProperties.class)).isNotEmpty();
			assertThat(context.getBeansOfType(MiniMaxChatModel.class)).isNotEmpty();
		});

		this.chatContextRunner.withPropertyValues("spring.ai.model.chat=none", "spring.ai.model.embedding=none")
			.run(context -> {
				assertThat(context.getBeansOfType(MiniMaxChatProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(MiniMaxChatModel.class)).isEmpty();
			});

		this.chatContextRunner.withPropertyValues("spring.ai.model.chat=minimax", "spring.ai.model.embedding=none")
			.run(context -> {
				assertThat(context.getBeansOfType(MiniMaxChatProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(MiniMaxChatModel.class)).isNotEmpty();
			});
	}

	@Test
	void embeddingModelActivation() {
		this.embeddingContextRunner.run(context -> {
			assertThat(context.getBeansOfType(MiniMaxEmbeddingModel.class)).isNotEmpty();
			assertThat(context.getBeansOfType(MiniMaxEmbeddingProperties.class)).isNotEmpty();
		});

		this.embeddingContextRunner.withPropertyValues("spring.ai.model.embedding=none").run(context -> {
			assertThat(context.getBeansOfType(MiniMaxEmbeddingProperties.class)).isEmpty();
			assertThat(context.getBeansOfType(MiniMaxEmbeddingModel.class)).isEmpty();
		});

		this.embeddingContextRunner.withPropertyValues("spring.ai.model.embedding=minimax").run(context -> {
			assertThat(context.getBeansOfType(MiniMaxEmbeddingProperties.class)).isNotEmpty();
			assertThat(context.getBeansOfType(MiniMaxEmbeddingModel.class)).isNotEmpty();
		});
	}

}
