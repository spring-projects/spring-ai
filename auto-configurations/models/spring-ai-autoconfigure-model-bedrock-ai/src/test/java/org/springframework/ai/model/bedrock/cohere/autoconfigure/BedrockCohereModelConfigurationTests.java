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

package org.springframework.ai.model.bedrock.cohere.autoconfigure;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.bedrock.cohere.BedrockCohereEmbeddingModel;
import org.springframework.ai.model.bedrock.autoconfigure.BedrockAwsCredentialsAndRegionAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit Tests for {@link BedrockCohereEmbeddingAutoConfiguration}'s conditional enabling
 * of models.
 *
 * @author Ilayaperumal Gopinathan
 */
public class BedrockCohereModelConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(BedrockCohereEmbeddingAutoConfiguration.class,
				BedrockAwsCredentialsAndRegionAutoConfiguration.class))
		.withBean(JsonMapper.class, JsonMapper::new);

	@Test
	void embeddingModelActivation() {
		this.contextRunner
			.run(context -> assertThat(context.getBeansOfType(BedrockCohereEmbeddingModel.class)).isNotEmpty());

		this.contextRunner.withPropertyValues("spring.ai.model.embedding=none").run(context -> {
			assertThat(context.getBeansOfType(BedrockCohereEmbeddingProperties.class)).isEmpty();
			assertThat(context.getBeansOfType(BedrockCohereEmbeddingModel.class)).isEmpty();
		});

		this.contextRunner.withPropertyValues("spring.ai.model.embedding=bedrock-cohere").run(context -> {
			assertThat(context.getBeansOfType(BedrockCohereEmbeddingProperties.class)).isNotEmpty();
			assertThat(context.getBeansOfType(BedrockCohereEmbeddingModel.class)).isNotEmpty();
		});
	}

}
