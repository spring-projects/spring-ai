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

package org.springframework.ai.model.openai.autoconfigure;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Sebastien Deleuze
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class OpenAiEmbeddingAutoConfigurationIT {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.openai.apiKey=" + System.getenv("OPENAI_API_KEY"));

	@Test
	void embedding() {
		this.contextRunner.withConfiguration(AutoConfigurations.of(OpenAiEmbeddingAutoConfiguration.class))
			.run(context -> {
				OpenAiEmbeddingModel embeddingModel = context.getBean(OpenAiEmbeddingModel.class);

				EmbeddingResponse embeddingResponse = embeddingModel
					.embedForResponse(List.of("Hello World", "World is big and salvation is near"));
				assertThat(embeddingResponse.getResults()).hasSize(2);
				assertThat(embeddingResponse.getResults().get(0).getOutput()).isNotEmpty();
				assertThat(embeddingResponse.getResults().get(0).getIndex()).isEqualTo(0);
				assertThat(embeddingResponse.getResults().get(1).getOutput()).isNotEmpty();
				assertThat(embeddingResponse.getResults().get(1).getIndex()).isEqualTo(1);

				assertThat(embeddingModel.dimensions()).isEqualTo(1536);
			});
	}

	@Test
	void embeddingActivation() {

		this.contextRunner
			.withPropertyValues("spring.ai.openai.api-key=API_KEY", "spring.ai.openai.base-url=TEST_BASE_URL",
					"spring.ai.model.embedding=none")
			.withConfiguration(AutoConfigurations.of(OpenAiEmbeddingAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiEmbeddingProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAiEmbeddingModel.class)).isEmpty();
			});

		this.contextRunner
			.withPropertyValues("spring.ai.openai.api-key=API_KEY", "spring.ai.openai.base-url=http://TEST.BASE.URL")
			.withConfiguration(AutoConfigurations.of(OpenAiEmbeddingAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiEmbeddingProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAiEmbeddingModel.class)).isNotEmpty();
			});

		this.contextRunner
			.withPropertyValues("spring.ai.openai.api-key=API_KEY", "spring.ai.openai.base-url=http://TEST.BASE.URL",
					"spring.ai.model.embedding=openai")
			.withConfiguration(AutoConfigurations.of(OpenAiEmbeddingAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiEmbeddingProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAiEmbeddingModel.class)).isNotEmpty();
			});
	}

	@Test
	public void embeddingOptionsTest() {

		this.contextRunner.withPropertyValues(
		// @formatter:off
				"spring.ai.openai.api-key=API_KEY",
				"spring.ai.openai.base-url=http://TEST.BASE.URL",

				"spring.ai.openai.embedding.options.model=MODEL_XYZ",
				"spring.ai.openai.embedding.options.encodingFormat=float",
				"spring.ai.openai.embedding.options.user=userXYZ"
				)
			// @formatter:on
			.withConfiguration(AutoConfigurations.of(OpenAiEmbeddingAutoConfiguration.class))
			.run(context -> {
				var commonProperties = context.getBean(OpenAiCommonProperties.class);
				var embeddingProperties = context.getBean(OpenAiEmbeddingProperties.class);

				assertThat(commonProperties.getBaseUrl()).isEqualTo("http://TEST.BASE.URL");
				assertThat(commonProperties.getApiKey()).isEqualTo("API_KEY");

				assertThat(embeddingProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
				assertThat(embeddingProperties.getOptions().getUser()).isEqualTo("userXYZ");
				assertThat(embeddingProperties.getOptions().getEncodingFormat())
					.isEqualTo(OpenAiEmbeddingOptions.EncodingFormat.FLOAT);
			});
	}

}
