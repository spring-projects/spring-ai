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

package org.springframework.ai.stabilityai;

import java.util.List;

import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import org.junit.jupiter.api.Test;

import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.image.observation.DefaultImageModelObservationConvention;
import org.springframework.ai.image.observation.ImageModelObservationDocumentation;
import org.springframework.ai.observation.conventions.AiOperationType;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.ai.stabilityai.api.StabilityAiApi;
import org.springframework.ai.stabilityai.api.StabilityAiImageOptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for observation instrumentation in {@link StabilityAiImageModel}.
 */
class StabilityAiImageModelObservationTests {

	@Test
	void observationForImageOperation() {
		var observationRegistry = TestObservationRegistry.create();

		StabilityAiApi stabilityAiApi = mock(StabilityAiApi.class);
		when(stabilityAiApi.generateImage(any())).thenReturn(new StabilityAiApi.GenerateImageResponse("SUCCESS",
				List.of(new StabilityAiApi.GenerateImageResponse.Artifacts(42L, "aGVsbG8=", "SUCCESS"))));

		var imageModel = new StabilityAiImageModel(stabilityAiApi, StabilityAiImageOptions.builder().build(),
				observationRegistry);

		var promptOptions = StabilityAiImageOptions.builder()
			.model("stable-diffusion-v1-6")
			.width(512)
			.height(512)
			.build();

		ImageResponse imageResponse = imageModel.call(new ImagePrompt("Lighthouse on a rocky coast", promptOptions));
		assertThat(imageResponse.getResults()).hasSize(1);

		TestObservationRegistryAssert.assertThat(observationRegistry)
			.doesNotHaveAnyRemainingCurrentObservation()
			.hasObservationWithNameEqualTo(DefaultImageModelObservationConvention.DEFAULT_NAME)
			.that()
			.hasContextualNameEqualTo("image stable-diffusion-v1-6")
			.hasLowCardinalityKeyValue(
					ImageModelObservationDocumentation.LowCardinalityKeyNames.AI_OPERATION_TYPE.asString(),
					AiOperationType.IMAGE.value())
			.hasLowCardinalityKeyValue(ImageModelObservationDocumentation.LowCardinalityKeyNames.AI_PROVIDER.asString(),
					AiProvider.STABILITY_AI.value())
			.hasLowCardinalityKeyValue(
					ImageModelObservationDocumentation.LowCardinalityKeyNames.REQUEST_MODEL.asString(),
					"stable-diffusion-v1-6")
			.hasHighCardinalityKeyValue(
					ImageModelObservationDocumentation.HighCardinalityKeyNames.REQUEST_IMAGE_SIZE.asString(), "512x512")
			.hasBeenStarted()
			.hasBeenStopped();
	}

}
