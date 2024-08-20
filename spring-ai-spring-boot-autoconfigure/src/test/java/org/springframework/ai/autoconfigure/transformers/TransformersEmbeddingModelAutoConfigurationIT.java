/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.autoconfigure.transformers;

import java.io.File;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 */
public class TransformersEmbeddingModelAutoConfigurationIT {

	@TempDir
	File tempDir;

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(TransformersEmbeddingModelAutoConfiguration.class));

	@Test
	public void embedding() {
		contextRunner.run(context -> {
			var properties = context.getBean(TransformersEmbeddingModelProperties.class);
			assertThat(properties.getCache().isEnabled()).isTrue();
			assertThat(properties.getCache().getDirectory()).isEqualTo(
					new File(System.getProperty("java.io.tmpdir"), "spring-ai-onnx-generative").getAbsolutePath());

			EmbeddingModel embeddingModel = context.getBean(EmbeddingModel.class);
			assertThat(embeddingModel).isInstanceOf(TransformersEmbeddingModel.class);

			List<float[]> embeddings = embeddingModel.embed(List.of("Spring Framework", "Spring AI"));

			assertThat(embeddings.size()).isEqualTo(2); // batch size
			assertThat(embeddings.get(0).length).isEqualTo(embeddingModel.dimensions()); // dimensions
																							// size
		});
	}

	@Test
	public void remoteOnnxModel() {
		// https://huggingface.co/intfloat/e5-small-v2
		contextRunner.withPropertyValues("spring.ai.embedding.transformer.cache.directory=" + tempDir.getAbsolutePath(),
				"spring.ai.embedding.transformer.onnx.modelUri=https://huggingface.co/intfloat/e5-small-v2/resolve/main/model.onnx",
				"spring.ai.embedding.transformer.tokenizer.uri=https://huggingface.co/intfloat/e5-small-v2/raw/main/tokenizer.json")
			.run(context -> {
				var properties = context.getBean(TransformersEmbeddingModelProperties.class);
				assertThat(properties.getOnnx().getModelUri())
					.isEqualTo("https://huggingface.co/intfloat/e5-small-v2/resolve/main/model.onnx");
				assertThat(properties.getTokenizer().getUri())
					.isEqualTo("https://huggingface.co/intfloat/e5-small-v2/raw/main/tokenizer.json");

				assertThat(properties.getCache().isEnabled()).isTrue();
				assertThat(properties.getCache().getDirectory()).isEqualTo(tempDir.getAbsolutePath());
				assertThat(tempDir.listFiles()).hasSize(2);

				EmbeddingModel embeddingModel = context.getBean(EmbeddingModel.class);
				assertThat(embeddingModel).isInstanceOf(TransformersEmbeddingModel.class);

				assertThat(embeddingModel.dimensions()).isEqualTo(384);

				List<float[]> embeddings = embeddingModel.embed(List.of("Spring Framework", "Spring AI"));

				assertThat(embeddings.size()).isEqualTo(2); // batch size
				assertThat(embeddings.get(0).length).isEqualTo(embeddingModel.dimensions()); // dimensions
																								// size
			});
	}

	@Test
	void embeddingActivation() {
		contextRunner.withPropertyValues("spring.ai.embedding.transformer.enabled=false").run(context -> {
			assertThat(context.getBeansOfType(TransformersEmbeddingModelProperties.class)).isNotEmpty();
			assertThat(context.getBeansOfType(TransformersEmbeddingModel.class)).isEmpty();
		});

		contextRunner.withPropertyValues("spring.ai.embedding.transformer.enabled=true").run(context -> {
			assertThat(context.getBeansOfType(TransformersEmbeddingModelProperties.class)).isNotEmpty();
			assertThat(context.getBeansOfType(TransformersEmbeddingModel.class)).isNotEmpty();
		});

		contextRunner.run(context -> {
			assertThat(context.getBeansOfType(TransformersEmbeddingModelProperties.class)).isNotEmpty();
			assertThat(context.getBeansOfType(TransformersEmbeddingModel.class)).isNotEmpty();
		});

	}

}
