/*
 * Copyright 2025-2026 the original author or authors.
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
package org.springframework.ai.autoconfigure.vertexai.imagen;

import java.io.File;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.vertexai.imagen.VertexAiImagenImageModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Sami Marzouki
 */
@EnabledIfEnvironmentVariable(named = "VERTEX_AI_IMAGEN_PROJECT_ID", matches = ".*")
@EnabledIfEnvironmentVariable(named = "VERTEX_AI_IMAGEN_LOCATION", matches = ".*")
public class VertexAiImagenModelAutoConfigurationIT {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withPropertyValues("spring.ai.vertex.ai.imagen.project-id=" + System.getenv("VERTEX_AI_IMAGEN_PROJECT_ID"),
					"spring.ai.vertex.ai.imagen.location=" + System.getenv("VERTEX_AI_IMAGEN_LOCATION"))
			.withConfiguration(AutoConfigurations.of(VertexAiImagenAutoConfiguration.class));

	@TempDir
	File tempDir;


	@Test
	public void imageGenerator() {
		this.contextRunner.run(context -> {
			var connectionProperties = context.getBean(VertexAiImagenConnectionProperties.class);
			var imageProperties = context.getBean(VertexAiImagenImageProperties.class);

			assertThat(connectionProperties).isNotNull();
			assertThat(imageProperties.isEnabled()).isTrue();

			VertexAiImagenImageModel imageModel = context.getBean(VertexAiImagenImageModel.class);
			assertThat(imageModel).isInstanceOf(VertexAiImagenImageModel.class);

			ImageResponse imageResponse = imageModel.call(new ImagePrompt("Spring Framework, Spring AI"));

			assertThat(imageResponse.getResults().size()).isEqualTo(1);
			assertThat(imageResponse.getResults().get(0).getOutput().getB64Json()).isNotEmpty();
		});
	}

	@Test
	void imageGeneratorActivation() {
		this.contextRunner.withPropertyValues("spring.ai.vertex.ai.imagen.generator.enabled=false").run(context -> {
			assertThat(context.getBeansOfType(VertexAiImagenImageProperties.class)).isNotEmpty();
			assertThat(context.getBeansOfType(VertexAiImagenImageModel.class)).isEmpty();
		});

		this.contextRunner.withPropertyValues("spring.ai.vertex.ai.imagen.generator.enabled=true").run(context -> {
			assertThat(context.getBeansOfType(VertexAiImagenImageProperties.class)).isNotEmpty();
			assertThat(context.getBeansOfType(VertexAiImagenImageModel.class)).isNotEmpty();
		});

		this.contextRunner.run(context -> {
			assertThat(context.getBeansOfType(VertexAiImagenImageProperties.class)).isNotEmpty();
			assertThat(context.getBeansOfType(VertexAiImagenImageModel.class)).isNotEmpty();
		});

	}

}
