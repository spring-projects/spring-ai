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

import com.google.genai.Client;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.audio.transcription.observation.AudioTranscriptionModelObservationDocumentation;
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
 * Observation integration tests for {@link GoogleGenAiTranscriptionModel}.
 * <p>
 * Requires a {@code GOOGLE_GENAI_API_KEY} environment variable with a valid Gemini
 * Developer API key. The tests are skipped when the variable is not set.
 *
 * @author Olivier Le Quellec
 */
@SpringBootTest(classes = GoogleGenAiTranscriptionModelObservationIT.Config.class)
@EnabledIfEnvironmentVariable(named = "GOOGLE_GENAI_API_KEY", matches = ".+")
class GoogleGenAiTranscriptionModelObservationIT {

	@Value("classpath:/speech-mono.wav")
	private Resource audioFile;

	@Autowired
	private TestObservationRegistry observationRegistry;

	@Autowired
	private GoogleGenAiTranscriptionModel transcriptionModel;

	@BeforeEach
	void beforeEach() {
		this.observationRegistry.clear();
	}

	@Test
	void observationForTranscriptionOperation() {
		GoogleGenAiAudioTranscriptionOptions options = GoogleGenAiAudioTranscriptionOptions.builder()
			.model(GoogleGenAiAudioTranscriptionOptions.DEFAULT_MODEL_NAME)
			.temperature(0.0f)
			.build();

		AudioTranscriptionResponse response = this.transcriptionModel
			.call(new AudioTranscriptionPrompt(this.audioFile, options));

		assertThat(response).isNotNull();
		assertThat(response.getResult().getOutput()).isNotBlank();

		TestObservationRegistryAssert.assertThat(this.observationRegistry)
			.doesNotHaveAnyRemainingCurrentObservation()
			.hasObservationWithNameEqualTo(DefaultAudioTranscriptionModelObservationConvention.DEFAULT_NAME)
			.that()
			.hasContextualNameEqualTo("%s %s".formatted(AiOperationType.TRANSCRIPTION.value(),
					GoogleGenAiAudioTranscriptionOptions.DEFAULT_MODEL_NAME))
			.hasLowCardinalityKeyValue(
					AudioTranscriptionModelObservationDocumentation.LowCardinalityKeyNames.AI_OPERATION_TYPE.asString(),
					AiOperationType.TRANSCRIPTION.value())
			.hasLowCardinalityKeyValue(
					AudioTranscriptionModelObservationDocumentation.LowCardinalityKeyNames.AI_PROVIDER.asString(),
					AiProvider.GOOGLE_GENAI_AI.value())
			.hasLowCardinalityKeyValue(
					AudioTranscriptionModelObservationDocumentation.LowCardinalityKeyNames.REQUEST_MODEL.asString(),
					GoogleGenAiAudioTranscriptionOptions.DEFAULT_MODEL_NAME)
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
		GoogleGenAiTranscriptionModel googleGenAiTranscriptionModel(TestObservationRegistry observationRegistry) {
			String apiKey = System.getenv("GOOGLE_GENAI_API_KEY");
			Client client = Client.builder().apiKey(apiKey).build();
			GoogleGenAiTranscriptionConnectionDetails connectionDetails = GoogleGenAiTranscriptionConnectionDetails
				.builder()
				.client(client)
				.build();
			return new GoogleGenAiTranscriptionModel(connectionDetails,
					GoogleGenAiAudioTranscriptionOptions.builder().build(), RetryUtils.DEFAULT_RETRY_TEMPLATE,
					observationRegistry);
		}

	}

}
