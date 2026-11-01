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

package org.springframework.ai.google.genai.tts;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.audio.tts.TextToSpeechResponse;
import org.springframework.ai.audio.tts.observation.DefaultTextToSpeechModelObservationConvention;
import org.springframework.ai.audio.tts.observation.TextToSpeechModelObservationDocumentation.LowCardinalityKeyNames;
import org.springframework.ai.observation.conventions.AiOperationType;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for observation instrumentation in
 * {@link GoogleGenAiTextToSpeechModel}.
 *
 * @author Olivier Le Quellec
 */
@SpringBootTest(classes = GoogleGenAiTextToSpeechModelObservationIT.Config.class)
@EnabledIfEnvironmentVariable(named = "GOOGLE_CLOUD_PROJECT", matches = ".+")
class GoogleGenAiTextToSpeechModelObservationIT {

	@Autowired
	private TestObservationRegistry observationRegistry;

	@Autowired
	private GoogleGenAiTextToSpeechModel textToSpeechModel;

	@BeforeEach
	void setUp() {
		this.observationRegistry.clear();
	}

	@Test
	void observationForTextToSpeechOperation() {
		GoogleGenAiAudioSpeechOptions options = GoogleGenAiAudioSpeechOptions.builder()
			.model("gemini-2.5-flash-tts")
			.voiceName("Kore")
			.languageCode("en-us")
			.build();

		TextToSpeechResponse response = this.textToSpeechModel
			.call(new TextToSpeechPrompt("Hello from Spring AI and Gemini text to speech.", options));
		assertThat(response.getResult()).isNotNull();
		assertThat(response.getResult().getOutput()).isNotEmpty();

		TestObservationRegistryAssert.assertThat(this.observationRegistry)
			.doesNotHaveAnyRemainingCurrentObservation()
			.hasObservationWithNameEqualTo(DefaultTextToSpeechModelObservationConvention.DEFAULT_NAME)
			.that()
			.hasContextualNameEqualTo("text_to_speech gemini-2.5-flash-tts")
			.hasLowCardinalityKeyValue(LowCardinalityKeyNames.AI_OPERATION_TYPE.asString(),
					AiOperationType.TEXT_TO_SPEECH.value())
			.hasLowCardinalityKeyValue(LowCardinalityKeyNames.AI_PROVIDER.asString(),
					AiProvider.GOOGLE_GENAI_AI.value())
			.hasLowCardinalityKeyValue(LowCardinalityKeyNames.REQUEST_MODEL.asString(), "gemini-2.5-flash-tts")
			.hasBeenStarted()
			.hasBeenStopped();
	}

	@SpringBootConfiguration
	static class Config {

		@Bean
		TestObservationRegistry observationRegistry() {
			return TestObservationRegistry.create();
		}

		@Bean
		GoogleGenAiTextToSpeechConnectionDetails connectionDetails() {
			return GoogleGenAiTextToSpeechConnectionDetails.builder()
				.projectId(System.getenv("GOOGLE_CLOUD_PROJECT"))
				.location(System.getenv()
					.getOrDefault("GOOGLE_CLOUD_LOCATION", GoogleGenAiTextToSpeechConnectionDetails.DEFAULT_LOCATION))
				.build();
		}

		@Bean
		GoogleGenAiTextToSpeechModel textToSpeechModel(GoogleGenAiTextToSpeechConnectionDetails connectionDetails,
				ObservationRegistry observationRegistry) {
			return GoogleGenAiTextToSpeechModel.builder()
				.connectionDetails(connectionDetails)
				.options(GoogleGenAiAudioSpeechOptions.builder()
					.model(GoogleGenAiAudioSpeechOptions.DEFAULT_MODEL)
					.build())
				.retryTemplate(RetryUtils.DEFAULT_RETRY_TEMPLATE)
				.observationRegistry(observationRegistry)
				.build();
		}

	}

}
