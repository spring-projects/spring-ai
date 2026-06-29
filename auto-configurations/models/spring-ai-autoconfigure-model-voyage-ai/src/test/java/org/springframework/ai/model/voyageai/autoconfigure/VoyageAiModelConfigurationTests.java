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

package org.springframework.ai.model.voyageai.autoconfigure;

import org.junit.jupiter.api.Test;

import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.ai.voyageai.VoyageAiEmbeddingModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit Tests for Voyage AI auto-configuration conditional enabling of models.
 *
 * @author Spring AI
 */
class VoyageAiModelConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.voyageai.api-key=test-api-key")
		.withConfiguration(AutoConfigurations.of(VoyageAiEmbeddingAutoConfiguration.class,
				RestClientAutoConfiguration.class, SpringAiRetryAutoConfiguration.class));

	@Test
	void embeddingModelEnabledByDefault() {
		this.contextRunner.run(context -> {
			assertThat(context.getBeansOfType(VoyageAiEmbeddingProperties.class)).isNotEmpty();
			assertThat(context.getBeansOfType(VoyageAiEmbeddingModel.class)).isNotEmpty();
		});
	}

	@Test
	void embeddingModelActivation() {
		this.contextRunner.withPropertyValues("spring.ai.model.embedding=none").run(context -> {
			assertThat(context.getBeansOfType(VoyageAiEmbeddingProperties.class)).isEmpty();
			assertThat(context.getBeansOfType(VoyageAiEmbeddingModel.class)).isEmpty();
		});

		this.contextRunner.withPropertyValues("spring.ai.model.embedding=voyage").run(context -> {
			assertThat(context.getBeansOfType(VoyageAiEmbeddingProperties.class)).isNotEmpty();
			assertThat(context.getBeansOfType(VoyageAiEmbeddingModel.class)).isNotEmpty();
		});
	}

}
