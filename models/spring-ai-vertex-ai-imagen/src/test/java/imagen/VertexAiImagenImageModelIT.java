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

package imagen;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.vertexai.imagen.VertexAiImagenConnectionDetails;
import org.springframework.ai.vertexai.imagen.VertexAiImagenImageModel;
import org.springframework.ai.vertexai.imagen.VertexAiImagenImageOptions;
import org.springframework.ai.vertexai.imagen.metadata.VertexAiImagenImageGenerationMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

/**
 * @author Marzouki Sami
 */
@SpringBootTest(classes = VertexAiImagenImageModelIT.Config.class)
@EnabledIfEnvironmentVariable(named = "VERTEX_AI_IMAGEN_PROJECT_ID", matches = ".*")
@EnabledIfEnvironmentVariable(named = "VERTEX_AI_IMAGEN_LOCATION", matches = ".*")
public class VertexAiImagenImageModelIT {

	@Autowired
	protected VertexAiImagenImageModel imageModel;

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "imagen-4.0-generate-001", "imagen-4.0-fast-generate-001", "imagen-4.0-ultra-generate-001",
			"imagen-3.0-generate-002", "imagen-3.0-generate-001", "imagen-3.0-fast-generate-001",
			"imagen-3.0-capability-001" })
	void defaultImageGenerator(String modelName) {
		Assertions.assertThat(this.imageModel).isNotNull();

		var options = VertexAiImagenImageOptions.builder().model(modelName).N(1).build();

		ImageResponse imageResponse = this.imageModel
			.call(new ImagePrompt("little kitten sitting on a purple cushion", options));

		Assertions.assertThat(imageResponse.getResults()).hasSize(2);
		Assertions.assertThat(imageResponse.getResults().get(0).getOutput().getB64Json()).isNotEmpty();
		Assertions
			.assertThat(((VertexAiImagenImageGenerationMetadata) imageResponse.getResults().get(0).getMetadata())
				.getModel())
			.isNotEmpty();
		Assertions
			.assertThat(((VertexAiImagenImageGenerationMetadata) imageResponse.getResults().get(0).getMetadata())
				.getPrompt())
			.isNotEmpty();
		Assertions
			.assertThat(((VertexAiImagenImageGenerationMetadata) imageResponse.getResults().get(0).getMetadata())
				.getMimeType())
			.isNotEmpty();
	}

	@SpringBootConfiguration
	static class Config {

		@Bean
		public VertexAiImagenConnectionDetails connectionDetails() {
			return VertexAiImagenConnectionDetails.builder()
				.projectId(System.getenv("VERTEX_AI_IMAGEN_PROJECT_ID"))
				.location(System.getenv("VERTEX_AI_IMAGEN_LOCATION"))
				.build();
		}

		@Bean
		public VertexAiImagenImageModel imageModel(VertexAiImagenConnectionDetails connectionDetails) {

			VertexAiImagenImageOptions options = VertexAiImagenImageOptions.builder()
				.model(VertexAiImagenImageOptions.DEFAULT_MODEL_NAME)
				.build();

			return new VertexAiImagenImageModel(connectionDetails, options);
		}

	}

}
