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
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit Tests for {@link MiniMaxAutoConfiguration}'s conditional enabling of models.
 *
 * @author Ilayaperumal Gopinathan
 */
public class MinimaxModelConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(MiniMaxAutoConfiguration.class, SpringAiRetryAutoConfiguration.class))
		.withPropertyValues("spring.ai.minimax.api-key=API_KEY", "spring.ai.minimax.base-url=TEST_BASE_URL");

	@Test
	void chatModelActivation() {
		this.contextRunner.run(context -> {
			assertThat(context.getBeansOfType(MiniMaxChatProperties.class)).isNotEmpty();
			assertThat(context.getBeansOfType(MiniMaxChatModel.class)).isNotEmpty();
			assertThat(context.getBeansOfType(MiniMaxEmbeddingProperties.class)).isNotEmpty();
			assertThat(context.getBeansOfType(MiniMaxEmbeddingModel.class)).isNotEmpty();
		});

		this.contextRunner.withPropertyValues("spring.ai.model.chat=none", "spring.ai.model.embedding=none")
			.run(context -> {
				assertThat(context.getBeansOfType(MiniMaxChatProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(MiniMaxChatModel.class)).isEmpty();
				assertThat(context.getBeansOfType(MiniMaxEmbeddingProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(MiniMaxEmbeddingModel.class)).isEmpty();
			});

		this.contextRunner.withPropertyValues("spring.ai.model.chat=minimax", "spring.ai.model.embedding=none")
			.run(context -> {
				assertThat(context.getBeansOfType(MiniMaxChatProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(MiniMaxChatModel.class)).isNotEmpty();
				assertThat(context.getBeansOfType(MiniMaxEmbeddingProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(MiniMaxEmbeddingModel.class)).isEmpty();
			});
	}

	@Test
	void embeddingModelActivation() {
		this.contextRunner.run(context -> {
			assertThat(context.getBeansOfType(MiniMaxChatModel.class)).isNotEmpty();
		});

		this.contextRunner.withPropertyValues("spring.ai.model.embedding=none").run(context -> {
			assertThat(context.getBeansOfType(MiniMaxEmbeddingProperties.class)).isNotEmpty();
			assertThat(context.getBeansOfType(MiniMaxEmbeddingModel.class)).isEmpty();
		});

		this.contextRunner.withPropertyValues("spring.ai.model.embedding=minimax").run(context -> {
			assertThat(context.getBeansOfType(MiniMaxEmbeddingProperties.class)).isNotEmpty();
			assertThat(context.getBeansOfType(MiniMaxEmbeddingModel.class)).isNotEmpty();
		});
	}

}
