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

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.image.ImageResponseMetadata;
import org.springframework.ai.image.observation.DefaultImageModelObservationConvention;
import org.springframework.ai.image.observation.ImageModelObservationDocumentation;
import org.springframework.ai.observation.conventions.AiOperationType;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.vertexai.imagen.VertexAiImagenConnectionDetails;
import org.springframework.ai.vertexai.imagen.VertexAiImagenImageModel;
import org.springframework.ai.vertexai.imagen.VertexAiImagenImageModelName;
import org.springframework.ai.vertexai.imagen.VertexAiImagenImageOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for observation instrumentation in {@link VertexAiImagenImageModel}.
 *
 * @author Sami Marzouki
 */
@SpringBootTest(classes = VertexAiImagenImageModelObservationIT.Config.class)
@EnabledIfEnvironmentVariable(named = "VERTEX_AI_IMAGEN_PROJECT_ID", matches = ".*")
@EnabledIfEnvironmentVariable(named = "VERTEX_AI_IMAGEN_LOCATION", matches = ".*")
public class VertexAiImagenImageModelObservationIT {

	@Autowired
	TestObservationRegistry observationRegistry;

	@Autowired
	VertexAiImagenImageModel imageModel;

	@Test
	void observationForImageOperation() {
		var options = VertexAiImagenImageOptions.builder()
			.model(VertexAiImagenImageModelName.IMAGEN_3_V002.getValue())
			.N(1)
			.build();

		ImagePrompt imagePrompt = new ImagePrompt("Little kitten sitting on a purple cushion", options);
		ImageResponse imageResponse = this.imageModel.call(imagePrompt);
		assertThat(imageResponse.getResults()).isNotEmpty();

		ImageResponseMetadata responseMetadata = imageResponse.getMetadata();
		assertThat(responseMetadata).isNotNull();

		TestObservationRegistryAssert.assertThat(this.observationRegistry)
			.doesNotHaveAnyRemainingCurrentObservation()
			.hasObservationWithNameEqualTo(DefaultImageModelObservationConvention.DEFAULT_NAME)
			.that()
			.hasContextualNameEqualTo("image " + VertexAiImagenImageModelName.IMAGEN_3_V002.getValue())
			.hasLowCardinalityKeyValue(
					ImageModelObservationDocumentation.LowCardinalityKeyNames.AI_OPERATION_TYPE.asString(),
					AiOperationType.IMAGE.value())
			.hasLowCardinalityKeyValue(ImageModelObservationDocumentation.LowCardinalityKeyNames.AI_PROVIDER.asString(),
					AiProvider.VERTEX_AI.value())
			.hasLowCardinalityKeyValue(
					ImageModelObservationDocumentation.LowCardinalityKeyNames.REQUEST_MODEL.asString(),
					VertexAiImagenImageModelName.IMAGEN_3_V002.getValue())
			.hasBeenStarted()
			.hasBeenStopped();
	}

	@SpringBootConfiguration
	static class Config {

		@Bean
		public TestObservationRegistry observationRegistry() {
			return TestObservationRegistry.create();
		}

		@Bean
		public VertexAiImagenConnectionDetails connectionDetails() {
			return VertexAiImagenConnectionDetails.builder()
				.projectId(System.getenv("VERTEX_AI_IMAGEN_PROJECT_ID"))
				.location(System.getenv("VERTEX_AI_IMAGEN_LOCATION"))
				.build();
		}

		@Bean
		public VertexAiImagenImageModel imageModel(VertexAiImagenConnectionDetails connectionDetails,
				ObservationRegistry observationRegistry) {

			VertexAiImagenImageOptions options = VertexAiImagenImageOptions.builder()
				.model(VertexAiImagenImageOptions.DEFAULT_MODEL_NAME)
				.build();

			return new VertexAiImagenImageModel(connectionDetails, options, RetryUtils.DEFAULT_RETRY_TEMPLATE,
					observationRegistry);
		}

	}

}
