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

import io.micrometer.common.KeyValues;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.image.observation.DefaultImageModelObservationConvention;
import org.springframework.ai.image.observation.ImageModelObservationContext;
import org.springframework.ai.image.observation.ImageModelObservationConvention;
import org.springframework.ai.image.observation.ImageModelObservationDocumentation;
import org.springframework.ai.observation.conventions.AiOperationType;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.ai.stabilityai.api.StabilityAiApi;
import org.springframework.ai.stabilityai.api.StabilityAiImageOptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for observation instrumentation in {@link StabilityAiImageModel}. Uses a
 * mocked {@link StabilityAiApi} so the tests do not require network access or API
 * credentials.
 *
 * @author Gaurav Kumar
 */
class StabilityAiImageModelObservationTests {

	private TestObservationRegistry observationRegistry;

	private StabilityAiApi stabilityAiApi;

	private StabilityAiImageModel imageModel;

	@BeforeEach
	void setUp() {
		this.observationRegistry = TestObservationRegistry.create();
		this.stabilityAiApi = mock(StabilityAiApi.class);

		// Record layout: (seed, base64, finishReason)
		var artifact = new StabilityAiApi.GenerateImageResponse.Artifacts(1234L, "base64-bytes", "SUCCESS");
		var response = new StabilityAiApi.GenerateImageResponse(null, List.of(artifact));
		when(this.stabilityAiApi.generateImage(any(StabilityAiApi.GenerateImageRequest.class))).thenReturn(response);

		var defaultOptions = StabilityAiImageOptions.builder()
			.model("stable-diffusion-v1-6")
			.height(512)
			.width(512)
			.responseFormat("image/png")
			.stylePreset("photographic")
			.build();

		this.imageModel = new StabilityAiImageModel(this.stabilityAiApi, defaultOptions, this.observationRegistry);
	}

	@Test
	void emitsObservationWithStabilityAiProviderAndRuntimeOptions() {
		var runtimeOptions = StabilityAiImageOptions.builder()
			.model("stable-diffusion-xl-1024-v1-0")
			.width(1024)
			.height(1024)
			.stylePreset("digital-art")
			.responseFormat("image/png")
			.build();
		ImagePrompt prompt = new ImagePrompt("a cup of coffee in Paris", runtimeOptions);

		ImageResponse response = this.imageModel.call(prompt);

		assertThat(response.getResults()).hasSize(1);

		TestObservationRegistryAssert.assertThat(this.observationRegistry)
			.doesNotHaveAnyRemainingCurrentObservation()
			.hasObservationWithNameEqualTo(DefaultImageModelObservationConvention.DEFAULT_NAME)
			.that()
			.hasContextualNameEqualTo("image stable-diffusion-xl-1024-v1-0")
			.hasLowCardinalityKeyValue(
					ImageModelObservationDocumentation.LowCardinalityKeyNames.AI_OPERATION_TYPE.asString(),
					AiOperationType.IMAGE.value())
			.hasLowCardinalityKeyValue(ImageModelObservationDocumentation.LowCardinalityKeyNames.AI_PROVIDER.asString(),
					AiProvider.STABILITY_AI.value())
			.hasLowCardinalityKeyValue(
					ImageModelObservationDocumentation.LowCardinalityKeyNames.REQUEST_MODEL.asString(),
					"stable-diffusion-xl-1024-v1-0")
			.hasHighCardinalityKeyValue(
					ImageModelObservationDocumentation.HighCardinalityKeyNames.REQUEST_IMAGE_SIZE.asString(),
					"1024x1024")
			.hasHighCardinalityKeyValue(
					ImageModelObservationDocumentation.HighCardinalityKeyNames.REQUEST_IMAGE_STYLE.asString(),
					"digital-art")
			.hasBeenStarted()
			.hasBeenStopped();
	}

	@Test
	void stillEmitsObservationWhenImagePromptHasNoOptions() {
		// Observation context uses the raw ImagePrompt (OpenAI parity). When the prompt
		// carries no runtime options, provider + operation type are still emitted.
		ImagePrompt prompt = new ImagePrompt("a green field");

		this.imageModel.call(prompt);

		TestObservationRegistryAssert.assertThat(this.observationRegistry)
			.hasObservationWithNameEqualTo(DefaultImageModelObservationConvention.DEFAULT_NAME)
			.that()
			.hasLowCardinalityKeyValue(ImageModelObservationDocumentation.LowCardinalityKeyNames.AI_PROVIDER.asString(),
					AiProvider.STABILITY_AI.value())
			.hasBeenStarted()
			.hasBeenStopped();
	}

	@Test
	void emitsObservationAndPropagatesErrorWhenApiFails() {
		when(this.stabilityAiApi.generateImage(any(StabilityAiApi.GenerateImageRequest.class)))
			.thenThrow(new RuntimeException("boom"));

		assertThatThrownBy(() -> this.imageModel.call(new ImagePrompt("anything"))).isInstanceOf(RuntimeException.class)
			.hasMessage("boom");

		// The observation must still be started and stopped so instrumentation is not
		// lost on the error path.
		TestObservationRegistryAssert.assertThat(this.observationRegistry)
			.hasObservationWithNameEqualTo(DefaultImageModelObservationConvention.DEFAULT_NAME)
			.that()
			.hasLowCardinalityKeyValue(ImageModelObservationDocumentation.LowCardinalityKeyNames.AI_PROVIDER.asString(),
					AiProvider.STABILITY_AI.value())
			.hasBeenStarted()
			.hasBeenStopped();
	}

	@Test
	void usesCustomObservationConventionWhenProvided() {
		var customConvention = new ImageModelObservationConvention() {
			@Override
			public String getName() {
				return "custom.stability.observation";
			}

			@Override
			public boolean supportsContext(Observation.Context context) {
				return context instanceof ImageModelObservationContext;
			}

			@Override
			public KeyValues getLowCardinalityKeyValues(ImageModelObservationContext context) {
				return KeyValues.of("custom.tag", "yes");
			}
		};

		this.imageModel.setObservationConvention(customConvention);
		this.imageModel.call(new ImagePrompt("x"));

		TestObservationRegistryAssert.assertThat(this.observationRegistry)
			.hasObservationWithNameEqualTo("custom.stability.observation")
			.that()
			.hasLowCardinalityKeyValue("custom.tag", "yes");
	}

	@Test
	void setObservationConventionRejectsNull() {
		assertThatThrownBy(() -> this.imageModel.setObservationConvention(null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("observationConvention");
	}

	@Test
	void nullObservationRegistryFallsBackToNoop() {
		// Constructor defensively swaps a null registry for ObservationRegistry.NOOP so
		// the model never dereferences a null during call().
		var defaults = StabilityAiImageOptions.builder().model("stable-diffusion-v1-6").build();
		var model = new StabilityAiImageModel(this.stabilityAiApi, defaults, null);

		ImageResponse response = model.call(new ImagePrompt("anything"));
		assertThat(response.getResults()).hasSize(1);
	}

	@Test
	void backwardsCompatibleConstructorsDoNotEmitToProvidedRegistry() {
		// The pre-observability constructors must keep working without any Micrometer
		// infrastructure. They use ObservationRegistry.NOOP internally and must not
		// leak observations into an unrelated test registry.
		var defaults = StabilityAiImageOptions.builder().model("stable-diffusion-v1-6").build();

		var one = new StabilityAiImageModel(this.stabilityAiApi);
		var two = new StabilityAiImageModel(this.stabilityAiApi, defaults);

		assertThat(one.call(new ImagePrompt("one")).getResults()).hasSize(1);
		assertThat(two.call(new ImagePrompt("two")).getResults()).hasSize(1);

		// The shared test registry was not wired into either model — it must remain
		// untouched by these calls.
		assertThat(ObservationRegistry.NOOP).isNotSameAs(this.observationRegistry);
	}

}
