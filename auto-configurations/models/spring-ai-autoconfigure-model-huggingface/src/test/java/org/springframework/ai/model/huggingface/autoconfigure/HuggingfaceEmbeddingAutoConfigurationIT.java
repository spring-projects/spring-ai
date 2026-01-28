/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.model.huggingface.autoconfigure;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.huggingface.HuggingfaceEmbeddingModel;
import org.springframework.ai.utils.SpringAiTestAutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for HuggingFace Embedding Auto Configuration.
 *
 * @author Myeongdeok Kang
 */
@EnabledIfEnvironmentVariable(named = "HUGGINGFACE_API_KEY", matches = ".+")
public class HuggingfaceEmbeddingAutoConfigurationIT {

	private static final Log logger = LogFactory.getLog(HuggingfaceEmbeddingAutoConfigurationIT.class);

	private static final String MODEL_NAME = "sentence-transformers/all-MiniLM-L6-v2";

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner().withPropertyValues(
	// @formatter:off
			"spring.ai.huggingface.api-key=" + System.getenv("HUGGINGFACE_API_KEY"),
			"spring.ai.huggingface.embedding.options.model=" + MODEL_NAME)
			// @formatter:on
		.withConfiguration(SpringAiTestAutoConfigurations.of(HuggingfaceEmbeddingAutoConfiguration.class));

	@Test
	void singleTextEmbedding() {
		this.contextRunner.run(context -> {
			HuggingfaceEmbeddingModel embeddingModel = context.getBean(HuggingfaceEmbeddingModel.class);
			assertThat(embeddingModel).isNotNull();

			EmbeddingResponse embeddingResponse = embeddingModel.embedForResponse(List.of("Hello World"));
			assertThat(embeddingResponse.getResults()).hasSize(1);
			assertThat(embeddingResponse.getResults().get(0).getOutput()).isNotEmpty();
			assertThat(embeddingResponse.getResults().get(0).getOutput()).hasSizeGreaterThan(0);

			logger.info("Embedding dimensions: " + embeddingResponse.getResults().get(0).getOutput().length);
		});
	}

	@Test
	void batchTextEmbedding() {
		this.contextRunner.run(context -> {
			HuggingfaceEmbeddingModel embeddingModel = context.getBean(HuggingfaceEmbeddingModel.class);
			assertThat(embeddingModel).isNotNull();

			List<String> texts = List.of("Hello World", "Spring AI", "HuggingFace Integration");
			EmbeddingResponse embeddingResponse = embeddingModel.embedForResponse(texts);
			assertThat(embeddingResponse.getResults()).hasSize(3);

			embeddingResponse.getResults().forEach(result -> {
				assertThat(result.getOutput()).isNotEmpty();
				assertThat(result.getOutput()).hasSizeGreaterThan(0);
			});

			logger.info("Batch embedding completed for " + texts.size() + " texts");
		});
	}

	@Test
	void embeddingActivation() {
		// Default activation
		this.contextRunner.run(context -> {
			assertThat(context.getBeansOfType(HuggingfaceEmbeddingProperties.class)).isNotEmpty();
			assertThat(context.getBeansOfType(HuggingfaceEmbeddingModel.class)).isNotEmpty();
		});

		// Disabled via property
		this.contextRunner.withPropertyValues("spring.ai.model.embedding=none").run(context -> {
			assertThat(context.getBeansOfType(HuggingfaceEmbeddingProperties.class)).isEmpty();
			assertThat(context.getBeansOfType(HuggingfaceEmbeddingModel.class)).isEmpty();
		});

		// Explicitly enabled
		this.contextRunner.withPropertyValues("spring.ai.model.embedding=huggingface").run(context -> {
			assertThat(context.getBeansOfType(HuggingfaceEmbeddingProperties.class)).isNotEmpty();
			assertThat(context.getBeansOfType(HuggingfaceEmbeddingModel.class)).isNotEmpty();
		});
	}

	@Test
	void embeddingProperties() {
		this.contextRunner
			.withPropertyValues("spring.ai.huggingface.embedding.options.model=" + MODEL_NAME,
					"spring.ai.huggingface.embedding.options.normalize=true",
					"spring.ai.huggingface.embedding.options.prompt-name=query")
			.run(context -> {
				var embeddingProperties = context.getBean(HuggingfaceEmbeddingProperties.class);
				assertThat(embeddingProperties.getOptions().getModel()).isEqualTo(MODEL_NAME);
				assertThat(embeddingProperties.getOptions().getNormalize()).isTrue();
				assertThat(embeddingProperties.getOptions().getPromptName()).isEqualTo("query");
			});
	}

}
