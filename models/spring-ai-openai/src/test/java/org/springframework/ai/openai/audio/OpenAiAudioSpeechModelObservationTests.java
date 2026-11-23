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

package org.springframework.ai.openai.audio;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.List;

import com.openai.client.OpenAIClient;
import com.openai.core.RequestOptions;
import com.openai.core.http.Headers;
import com.openai.core.http.HttpResponse;
import com.openai.models.audio.speech.SpeechCreateParams;
import com.openai.services.blocking.AudioService;
import com.openai.services.blocking.audio.SpeechService;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.audio.tts.observation.DefaultTextToSpeechModelObservationConvention;
import org.springframework.ai.audio.tts.observation.TextToSpeechModelObservationDocumentation;
import org.springframework.ai.observation.conventions.AiObservationAttributes;
import org.springframework.ai.observation.conventions.AiOperationType;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.ai.openai.OpenAiAudioSpeechModel;
import org.springframework.ai.openai.OpenAiAudioSpeechOptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Observation tests for {@link OpenAiAudioSpeechModel}, against a mocked client.
 *
 * @author Christian Tzolov
 */
class OpenAiAudioSpeechModelObservationTests {

	private final TestObservationRegistry observationRegistry = TestObservationRegistry.create();

	private OpenAIClient client;

	@BeforeEach
	void mockClient() {
		this.client = mock(OpenAIClient.class);
		AudioService audioService = mock(AudioService.class);
		SpeechService speechService = mock(SpeechService.class);
		HttpResponse httpResponse = mock(HttpResponse.class);
		Headers headers = mock(Headers.class);
		when(this.client.audio()).thenReturn(audioService);
		when(audioService.speech()).thenReturn(speechService);
		when(speechService.create(any(SpeechCreateParams.class), any(RequestOptions.class))).thenReturn(httpResponse);
		when(httpResponse.body()).thenReturn(new ByteArrayInputStream(new byte[] { 1, 2, 3 }));
		when(httpResponse.headers()).thenReturn(headers);
		when(headers.values(anyString())).thenReturn(List.of());
	}

	private OpenAiAudioSpeechModel model() {
		return OpenAiAudioSpeechModel.builder()
			.openAiClient(this.client)
			.options(OpenAiAudioSpeechOptions.builder().model("tts-1").voice("nova").responseFormat("mp3").build())
			.observationRegistry(this.observationRegistry)
			.build();
	}

	@Test
	void observationForCall() {
		var response = model().call(new TextToSpeechPrompt("Welcome"));

		assertThat(response.getResult().getOutput()).containsExactly(1, 2, 3);
		assertObservation();
		TestObservationRegistryAssert.assertThat(this.observationRegistry)
			.hasObservationWithNameEqualTo(DefaultTextToSpeechModelObservationConvention.DEFAULT_NAME)
			.that()
			.doesNotHaveHighCardinalityKeyValueWithKey(AiObservationAttributes.REQUEST_STREAM.value());
	}

	@Test
	void observationForStream() {
		var responses = model().stream(new TextToSpeechPrompt("Welcome")).collectList().block(Duration.ofSeconds(5));

		assertThat(responses).isNotEmpty();
		assertObservation();
		TestObservationRegistryAssert.assertThat(this.observationRegistry)
			.hasObservationWithNameEqualTo(DefaultTextToSpeechModelObservationConvention.DEFAULT_NAME)
			.that()
			.hasHighCardinalityKeyValue(AiObservationAttributes.REQUEST_STREAM.value(), "true");
	}

	@Test
	void aFailingStreamEndsTheObservationWithItsError() {
		when(this.client.audio().speech().create(any(SpeechCreateParams.class), any(RequestOptions.class)))
			.thenThrow(new IllegalStateException("boom"));

		assertThatThrownBy(() -> model().stream(new TextToSpeechPrompt("Welcome")).blockLast(Duration.ofSeconds(5)))
			.hasMessageContaining("boom");

		TestObservationRegistryAssert.assertThat(this.observationRegistry)
			.doesNotHaveAnyRemainingCurrentObservation()
			.hasObservationWithNameEqualTo(DefaultTextToSpeechModelObservationConvention.DEFAULT_NAME)
			.that()
			.hasError()
			.hasBeenStopped();
	}

	@Test
	void aCancelledStreamEndsTheObservation() {
		// Takes one chunk and cancels the rest.
		model().stream(new TextToSpeechPrompt("Welcome")).next().block(Duration.ofSeconds(5));

		TestObservationRegistryAssert.assertThat(this.observationRegistry)
			.doesNotHaveAnyRemainingCurrentObservation()
			.hasObservationWithNameEqualTo(DefaultTextToSpeechModelObservationConvention.DEFAULT_NAME)
			.that()
			.hasBeenStopped();
	}

	@Test
	void noObservationWithoutARegistry() {
		OpenAiAudioSpeechModel.builder().openAiClient(this.client).build().call(new TextToSpeechPrompt("Welcome"));

		TestObservationRegistryAssert.assertThat(this.observationRegistry).doesNotHaveAnyObservation();
	}

	private void assertObservation() {
		TestObservationRegistryAssert.assertThat(this.observationRegistry)
			.doesNotHaveAnyRemainingCurrentObservation()
			.hasObservationWithNameEqualTo(DefaultTextToSpeechModelObservationConvention.DEFAULT_NAME)
			.that()
			.hasContextualNameEqualTo("text_to_speech tts-1")
			.hasLowCardinalityKeyValue(
					TextToSpeechModelObservationDocumentation.LowCardinalityKeyNames.AI_OPERATION_TYPE.asString(),
					AiOperationType.TEXT_TO_SPEECH.value())
			.hasLowCardinalityKeyValue(
					TextToSpeechModelObservationDocumentation.LowCardinalityKeyNames.AI_PROVIDER.asString(),
					AiProvider.OPENAI.value())
			.hasLowCardinalityKeyValue(
					TextToSpeechModelObservationDocumentation.LowCardinalityKeyNames.REQUEST_MODEL.asString(), "tts-1")
			.hasHighCardinalityKeyValue(AiObservationAttributes.REQUEST_AUDIO_VOICE.value(), "nova")
			.hasHighCardinalityKeyValue(AiObservationAttributes.REQUEST_AUDIO_RESPONSE_FORMAT.value(), "mp3")
			.hasBeenStarted()
			.hasBeenStopped();
	}

}
