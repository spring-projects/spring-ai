/*
 * Copyright 2026-2026 the original author or authors.
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

package org.springframework.ai.stabilityai;

import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.image.observation.DefaultImageModelObservationConvention;
import org.springframework.ai.observation.conventions.AiOperationType;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.ai.stabilityai.api.StabilityAiApi;
import org.springframework.ai.stabilityai.api.StabilityAiImageOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.ai.image.observation.ImageModelObservationDocumentation.HighCardinalityKeyNames;
import static org.springframework.ai.image.observation.ImageModelObservationDocumentation.LowCardinalityKeyNames;

/**
 * Integration tests for observation instrumentation in {@link StabilityAiImageModel}.
 */
@SpringBootTest(classes = StabilityAiImageModelObservationIT.Config.class)
@EnabledIfEnvironmentVariable(named = "STABILITYAI_API_KEY", matches = ".+")
public class StabilityAiImageModelObservationIT {

	@Autowired
	TestObservationRegistry observationRegistry;

	@Autowired
	StabilityAiImageModel imageModel;

	@Test
	void observationForImageOperation() {
		var options = StabilityAiImageOptions.builder()
			.model(StabilityAiApi.DEFAULT_IMAGE_MODEL)
			.N(1)
			.height(1024)
			.width(1024)
			.build();

		var instructions = "A cute baby sea otter";

		ImagePrompt imagePrompt = new ImagePrompt(instructions, options);

		ImageResponse imageResponse = this.imageModel.call(imagePrompt);
		assertThat(imageResponse.getResults()).hasSize(1);

		TestObservationRegistryAssert.assertThat(this.observationRegistry)
			.doesNotHaveAnyRemainingCurrentObservation()
			.hasObservationWithNameEqualTo(DefaultImageModelObservationConvention.DEFAULT_NAME)
			.that()
			.hasContextualNameEqualTo("image " + StabilityAiApi.DEFAULT_IMAGE_MODEL)
			.hasLowCardinalityKeyValue(LowCardinalityKeyNames.AI_OPERATION_TYPE.asString(),
					AiOperationType.IMAGE.value())
			.hasLowCardinalityKeyValue(LowCardinalityKeyNames.AI_PROVIDER.asString(), AiProvider.STABILITY_AI.value())
			.hasLowCardinalityKeyValue(LowCardinalityKeyNames.REQUEST_MODEL.asString(),
					StabilityAiApi.DEFAULT_IMAGE_MODEL)
			.hasHighCardinalityKeyValue(HighCardinalityKeyNames.REQUEST_IMAGE_SIZE.asString(), "1024x1024")
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
		public StabilityAiApi stabilityAiApi() {
			return new StabilityAiApi(System.getenv("STABILITYAI_API_KEY"));
		}

		@Bean
		public StabilityAiImageModel stabilityAiImageModel(StabilityAiApi stabilityAiApi,
				TestObservationRegistry observationRegistry) {
			return new StabilityAiImageModel(stabilityAiApi, StabilityAiImageOptions.builder().build(),
					observationRegistry);
		}

	}

}
