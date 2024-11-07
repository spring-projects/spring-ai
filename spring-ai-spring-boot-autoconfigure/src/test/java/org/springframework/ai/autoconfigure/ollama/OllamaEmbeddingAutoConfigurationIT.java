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

package org.springframework.ai.autoconfigure.ollama;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaModel;
import org.springframework.ai.ollama.management.OllamaModelManager;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @since 1.0.0
 */
public class OllamaEmbeddingAutoConfigurationIT extends BaseOllamaIT {

	private static final String MODEL_NAME = OllamaModel.NOMIC_EMBED_TEXT.getName();

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.ollama.embedding.options.model=" + MODEL_NAME,
				"spring.ai.ollama.base-url=" + getBaseUrl())
		.withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration.class, OllamaAutoConfiguration.class));

	@BeforeAll
	public static void beforeAll() throws IOException, InterruptedException {
		initializeOllama(MODEL_NAME);
	}

	@Test
	public void singleTextEmbedding() {
		this.contextRunner.run(context -> {
			OllamaEmbeddingModel embeddingModel = context.getBean(OllamaEmbeddingModel.class);
			assertThat(embeddingModel).isNotNull();
			EmbeddingResponse embeddingResponse = embeddingModel.embedForResponse(List.of("Hello World"));
			assertThat(embeddingResponse.getResults()).hasSize(1);
			assertThat(embeddingResponse.getResults().get(0).getOutput()).isNotEmpty();
			assertThat(embeddingModel.dimensions()).isEqualTo(768);
		});
	}

	@Test
	public void embeddingWithPull() {
		this.contextRunner.withPropertyValues("spring.ai.ollama.init.pull-model-strategy=when_missing")
			.withPropertyValues("spring.ai.ollama.embedding.options.model=all-minilm")
			.run(context -> {
				var model = "all-minilm";
				OllamaApi ollamaApi = context.getBean(OllamaApi.class);
				var modelManager = new OllamaModelManager(ollamaApi);
				assertThat(modelManager.isModelAvailable(model)).isTrue();

				OllamaEmbeddingModel embeddingModel = context.getBean(OllamaEmbeddingModel.class);
				EmbeddingResponse embeddingResponse = embeddingModel.embedForResponse(List.of("Hello World"));
				assertThat(embeddingResponse.getResults().get(0).getOutput()).isNotEmpty();
				modelManager.deleteModel(model);
			});
	}

	@Test
	void embeddingActivation() {
		this.contextRunner.withPropertyValues("spring.ai.ollama.embedding.enabled=false").run(context -> {
			assertThat(context.getBeansOfType(OllamaEmbeddingProperties.class)).isNotEmpty();
			assertThat(context.getBeansOfType(OllamaEmbeddingModel.class)).isEmpty();
		});

		this.contextRunner.run(context -> {
			assertThat(context.getBeansOfType(OllamaEmbeddingProperties.class)).isNotEmpty();
			assertThat(context.getBeansOfType(OllamaEmbeddingModel.class)).isNotEmpty();
		});

		this.contextRunner.withPropertyValues("spring.ai.ollama.embedding.enabled=true").run(context -> {
			assertThat(context.getBeansOfType(OllamaEmbeddingProperties.class)).isNotEmpty();
			assertThat(context.getBeansOfType(OllamaEmbeddingModel.class)).isNotEmpty();
		});

	}

}
