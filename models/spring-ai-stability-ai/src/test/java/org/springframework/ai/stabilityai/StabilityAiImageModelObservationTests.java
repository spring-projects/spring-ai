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
import org.springframework.ai.image.observation.DefaultImageModelObservationConvention;
import org.springframework.ai.observation.conventions.AiObservationAttributes;
import org.springframework.ai.observation.conventions.AiOperationType;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.ai.stabilityai.api.StabilityAiApi;
import org.springframework.ai.stabilityai.api.StabilityAiImageOptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Observation tests for {@link StabilityAiImageModel}, against a mocked API.
 *
 * @author Christian Tzolov
 */
class StabilityAiImageModelObservationTests {

	private final TestObservationRegistry observationRegistry = TestObservationRegistry.create();

	@Test
	void observationForCall() {
		StabilityAiApi api = mock(StabilityAiApi.class);
		when(api.generateImage(eq("stable-diffusion-v1-6"), any())).thenReturn(new StabilityAiApi.GenerateImageResponse(
				"success", List.of(new StabilityAiApi.GenerateImageResponse.Artifacts(7L, "aGVsbG8=", "SUCCESS"))));
		StabilityAiImageModel model = new StabilityAiImageModel(api,
				StabilityAiImageOptions.builder().model("stable-diffusion-v1-6").width(512).height(512).build(),
				this.observationRegistry);

		var response = model.call(new ImagePrompt("a lighthouse at dusk"));

		assertThat(response.getResults()).hasSize(1);
		TestObservationRegistryAssert.assertThat(this.observationRegistry)
			.doesNotHaveAnyRemainingCurrentObservation()
			.hasObservationWithNameEqualTo(DefaultImageModelObservationConvention.DEFAULT_NAME)
			.that()
			.hasContextualNameEqualTo("image stable-diffusion-v1-6")
			.hasLowCardinalityKeyValue(AiObservationAttributes.AI_OPERATION_TYPE.value(), AiOperationType.IMAGE.value())
			.hasLowCardinalityKeyValue(AiObservationAttributes.AI_PROVIDER.value(), AiProvider.STABILITY_AI.value())
			.hasLowCardinalityKeyValue(AiObservationAttributes.REQUEST_MODEL.value(), "stable-diffusion-v1-6")
			.hasHighCardinalityKeyValue(AiObservationAttributes.REQUEST_IMAGE_SIZE.value(), "512x512")
			.hasBeenStarted()
			.hasBeenStopped();
	}

}
