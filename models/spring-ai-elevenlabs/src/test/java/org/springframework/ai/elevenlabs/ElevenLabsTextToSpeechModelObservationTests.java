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

package org.springframework.ai.elevenlabs;

import java.time.Duration;

import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.audio.tts.observation.DefaultTextToSpeechModelObservationConvention;
import org.springframework.ai.elevenlabs.api.ElevenLabsApi;
import org.springframework.ai.observation.conventions.AiObservationAttributes;
import org.springframework.ai.observation.conventions.AiOperationType;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Observation tests for {@link ElevenLabsTextToSpeechModel}, against a mocked API.
 *
 * @author Christian Tzolov
 */
class ElevenLabsTextToSpeechModelObservationTests {

	private final TestObservationRegistry observationRegistry = TestObservationRegistry.create();

	private final ElevenLabsApi api = mock(ElevenLabsApi.class);

	private ElevenLabsTextToSpeechModel model() {
		return ElevenLabsTextToSpeechModel.builder()
			.elevenLabsApi(this.api)
			.options(ElevenLabsTextToSpeechOptions.builder().modelId("eleven_turbo_v2").voiceId("voice-1").build())
			.retryTemplate(RetryUtils.SHORT_RETRY_TEMPLATE)
			.observationRegistry(this.observationRegistry)
			.build();
	}

	@Test
	void observationForCall() {
		when(this.api.textToSpeech(any(), anyString(), any())).thenReturn(ResponseEntity.ok(new byte[] { 1, 2 }));

		var response = model().call(new TextToSpeechPrompt("Welcome"));

		assertThat(response.getResult().getOutput()).containsExactly(1, 2);
		assertObservation(false);
		TestObservationRegistryAssert.assertThat(this.observationRegistry)
			.hasObservationWithNameEqualTo(DefaultTextToSpeechModelObservationConvention.DEFAULT_NAME)
			.that()
			.doesNotHaveHighCardinalityKeyValueWithKey(AiObservationAttributes.REQUEST_STREAM.value());
	}

	@Test
	void observationForStream() {
		when(this.api.textToSpeechStream(any(), anyString(), any()))
			.thenReturn(Flux.just(ResponseEntity.ok(new byte[] { 1 }), ResponseEntity.ok(new byte[] { 2 })));

		var responses = model().stream(new TextToSpeechPrompt("Welcome")).collectList().block(Duration.ofSeconds(5));

		assertThat(responses).hasSize(2);
		assertObservation(false);
		TestObservationRegistryAssert.assertThat(this.observationRegistry)
			.hasObservationWithNameEqualTo(DefaultTextToSpeechModelObservationConvention.DEFAULT_NAME)
			.that()
			.hasHighCardinalityKeyValue(AiObservationAttributes.REQUEST_STREAM.value(), "true");
	}

	@Test
	void aStreamThatFailsToOpenEndsTheObservationWithItsError() {
		when(this.api.textToSpeechStream(any(), anyString(), any())).thenThrow(new IllegalStateException("boom"));

		assertThatThrownBy(() -> model().stream(new TextToSpeechPrompt("Welcome")).blockLast(Duration.ofSeconds(5)))
			.hasMessageContaining("boom");

		assertObservation(true);
	}

	private void assertObservation(boolean failed) {
		var observation = TestObservationRegistryAssert.assertThat(this.observationRegistry)
			.doesNotHaveAnyRemainingCurrentObservation()
			.hasObservationWithNameEqualTo(DefaultTextToSpeechModelObservationConvention.DEFAULT_NAME)
			.that()
			.hasContextualNameEqualTo("text_to_speech eleven_turbo_v2")
			.hasLowCardinalityKeyValue(AiObservationAttributes.AI_OPERATION_TYPE.value(),
					AiOperationType.TEXT_TO_SPEECH.value())
			.hasLowCardinalityKeyValue(AiObservationAttributes.AI_PROVIDER.value(), AiProvider.ELEVENLABS.value())
			.hasLowCardinalityKeyValue(AiObservationAttributes.REQUEST_MODEL.value(), "eleven_turbo_v2")
			.hasHighCardinalityKeyValue(AiObservationAttributes.REQUEST_AUDIO_VOICE.value(), "voice-1")
			.hasBeenStarted()
			.hasBeenStopped();
		if (failed) {
			observation.hasError();
		}
	}

}
