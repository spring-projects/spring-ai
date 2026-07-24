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

package org.springframework.ai.google.genai.image;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.image.observation.DefaultImageModelObservationConvention;
import org.springframework.ai.image.observation.ImageModelObservationDocumentation.LowCardinalityKeyNames;
import org.springframework.ai.observation.conventions.AiOperationType;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for observation instrumentation in {@link GoogleGenAiImageModel}.
 *
 * @author Olivier Le Quellec
 */
@SpringBootTest(classes = GoogleGenAiImageModelObservationIT.Config.class)
@EnabledIfEnvironmentVariable(named = "GOOGLE_CLOUD_PROJECT", matches = ".+")
@EnabledIfEnvironmentVariable(named = "GOOGLE_CLOUD_LOCATION", matches = ".+")
public class GoogleGenAiImageModelObservationIT {

	@Autowired
	TestObservationRegistry observationRegistry;

	@Autowired
	GoogleGenAiImageModel imageModel;

	@BeforeEach
	void setUp() {
		this.observationRegistry.clear();
	}

	@Test
	void observationForImageOperation() {

		var options = GoogleGenAiImageOptions.builder()
			.model(GoogleGenAiImageModelName.GEMINI_2_5_FLASH_IMAGE.getName())
			.n(1)
			.build();

		ImagePrompt imagePrompt = new ImagePrompt("A light cream colored mini golden doodle dog", options);

		ImageResponse imageResponse = this.imageModel.call(imagePrompt);
		assertThat(imageResponse.getResults()).isNotEmpty();

		TestObservationRegistryAssert.assertThat(this.observationRegistry)
			.doesNotHaveAnyRemainingCurrentObservation()
			.hasObservationWithNameEqualTo(DefaultImageModelObservationConvention.DEFAULT_NAME)
			.that()
			.hasContextualNameEqualTo("image " + GoogleGenAiImageModelName.GEMINI_2_5_FLASH_IMAGE.getName())
			.hasLowCardinalityKeyValue(LowCardinalityKeyNames.AI_OPERATION_TYPE.asString(),
					AiOperationType.IMAGE.value())
			.hasLowCardinalityKeyValue(LowCardinalityKeyNames.AI_PROVIDER.asString(),
					AiProvider.GOOGLE_GENAI_AI.value())
			.hasLowCardinalityKeyValue(LowCardinalityKeyNames.REQUEST_MODEL.asString(),
					GoogleGenAiImageModelName.GEMINI_2_5_FLASH_IMAGE.getName())
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
		public GoogleGenAiImageConnectionDetails connectionDetails() {
			return GoogleGenAiImageConnectionDetails.builder()
				.projectId(System.getenv("GOOGLE_CLOUD_PROJECT"))
				.location(System.getenv("GOOGLE_CLOUD_LOCATION"))
				.build();
		}

		@Bean
		public GoogleGenAiImageModel googleGenAiImageModel(GoogleGenAiImageConnectionDetails connectionDetails,
				ObservationRegistry observationRegistry) {

			GoogleGenAiImageOptions options = GoogleGenAiImageOptions.builder()
				.model(GoogleGenAiImageOptions.DEFAULT_MODEL_NAME)
				.build();

			return new GoogleGenAiImageModel(connectionDetails, options, RetryUtils.DEFAULT_RETRY_TEMPLATE,
					observationRegistry);
		}

	}

}
