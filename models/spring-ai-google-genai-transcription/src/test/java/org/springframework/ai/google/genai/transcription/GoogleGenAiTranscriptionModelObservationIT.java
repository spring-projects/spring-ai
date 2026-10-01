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

package org.springframework.ai.google.genai.transcription;

import java.util.List;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.audio.transcription.observation.AudioTranscriptionModelObservationDocumentation.LowCardinalityKeyNames;
import org.springframework.ai.audio.transcription.observation.DefaultAudioTranscriptionModelObservationConvention;
import org.springframework.ai.observation.conventions.AiOperationType;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for observation instrumentation in
 * {@link GoogleGenAiTranscriptionModel}.
 *
 * @author Olivier Le Quellec
 */
@SpringBootTest(classes = GoogleGenAiTranscriptionModelObservationIT.Config.class)
@EnabledIfEnvironmentVariable(named = "GOOGLE_CLOUD_PROJECT", matches = ".+")
class GoogleGenAiTranscriptionModelObservationIT {

	@Value("classpath:/speech-mono.wav")
	private Resource audioFile;

	@Autowired
	private TestObservationRegistry observationRegistry;

	@Autowired
	private GoogleGenAiTranscriptionModel transcriptionModel;

	@BeforeEach
	void setUp() {
		this.observationRegistry.clear();
	}

	@Test
	void observationForTranscriptionOperation() {
		GoogleGenAiAudioTranscriptionOptions options = GoogleGenAiAudioTranscriptionOptions.builder()
			.model("chirp_2")
			.languageCodes(List.of("en-US"))
			.build();

		AudioTranscriptionResponse response = this.transcriptionModel
			.call(new AudioTranscriptionPrompt(this.audioFile, options));
		assertThat(response.getResult()).isNotNull();

		TestObservationRegistryAssert.assertThat(this.observationRegistry)
			.doesNotHaveAnyRemainingCurrentObservation()
			.hasObservationWithNameEqualTo(DefaultAudioTranscriptionModelObservationConvention.DEFAULT_NAME)
			.that()
			.hasContextualNameEqualTo("transcription chirp_2")
			.hasLowCardinalityKeyValue(LowCardinalityKeyNames.AI_OPERATION_TYPE.asString(),
					AiOperationType.TRANSCRIPTION.value())
			.hasLowCardinalityKeyValue(LowCardinalityKeyNames.AI_PROVIDER.asString(),
					AiProvider.GOOGLE_GENAI_AI.value())
			.hasLowCardinalityKeyValue(LowCardinalityKeyNames.REQUEST_MODEL.asString(), "chirp_2")
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
		GoogleGenAiTranscriptionConnectionDetails connectionDetails() {
			return GoogleGenAiTranscriptionConnectionDetails.builder()
				.projectId(System.getenv("GOOGLE_CLOUD_PROJECT"))
				.location(System.getenv()
					.getOrDefault("GOOGLE_CLOUD_LOCATION", GoogleGenAiTranscriptionConnectionDetails.DEFAULT_LOCATION))
				.build();
		}

		@Bean
		GoogleGenAiTranscriptionModel transcriptionModel(GoogleGenAiTranscriptionConnectionDetails connectionDetails,
				ObservationRegistry observationRegistry) {
			return new GoogleGenAiTranscriptionModel(connectionDetails,
					GoogleGenAiAudioTranscriptionOptions.builder().build(), RetryUtils.DEFAULT_RETRY_TEMPLATE,
					observationRegistry);
		}

	}

}
