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

package org.springframework.ai.openai.headers;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.core.RequestOptions;
import com.openai.core.http.Headers;
import com.openai.core.http.HttpResponse;
import com.openai.models.audio.speech.SpeechCreateParams;
import com.openai.models.audio.transcriptions.Transcription;
import com.openai.models.audio.transcriptions.TranscriptionCreateParams;
import com.openai.models.audio.transcriptions.TranscriptionCreateResponse;
import com.openai.models.moderations.ModerationCreateParams;
import com.openai.models.moderations.ModerationCreateResponse;
import com.openai.services.blocking.AudioService;
import com.openai.services.blocking.audio.SpeechService;
import com.openai.services.blocking.audio.TranscriptionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.moderation.ModerationPrompt;
import org.springframework.ai.openai.OpenAiAudioSpeechModel;
import org.springframework.ai.openai.OpenAiAudioSpeechOptions;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions;
import org.springframework.ai.openai.OpenAiModerationModel;
import org.springframework.ai.openai.OpenAiModerationOptions;
import org.springframework.core.io.ByteArrayResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PerCallCustomHeadersTests {

	private static final String HEADER = "x-some-header-id";

	private static final String VALUE = "VALUE_123";

	@Test
	void transcriptionPropagatesPerCallCustomHeaders() {
		OpenAIClient mockClient = mock(OpenAIClient.class);
		AudioService audioService = mock(AudioService.class);
		TranscriptionService transcriptionService = mock(TranscriptionService.class);
		when(mockClient.audio()).thenReturn(audioService);
		when(audioService.transcriptions()).thenReturn(transcriptionService);
		when(transcriptionService.create(any(TranscriptionCreateParams.class), any(RequestOptions.class)))
			.thenReturn(TranscriptionCreateResponse.ofTranscription(Transcription.builder().text("hi").build()));

		OpenAiAudioTranscriptionModel model = OpenAiAudioTranscriptionModel.builder()
			.openAiClient(mockClient)
			.openAiClientAsync(mock(OpenAIClientAsync.class))
			.options(OpenAiAudioTranscriptionOptions.builder().model("whisper-1").build())
			.build();

		model.call(new AudioTranscriptionPrompt(new ByteArrayResource(new byte[] { 1 }),
				OpenAiAudioTranscriptionOptions.builder()
					.model("whisper-1")
					.customHeaders(Map.of(HEADER, VALUE))
					.build()));

		ArgumentCaptor<TranscriptionCreateParams> captor = ArgumentCaptor.forClass(TranscriptionCreateParams.class);
		verify(transcriptionService).create(captor.capture(), any(RequestOptions.class));
		assertThat(captor.getValue()._additionalHeaders().values(HEADER)).containsExactly(VALUE);
	}

	@Test
	void speechPropagatesPerCallCustomHeaders() {
		OpenAIClient mockClient = mock(OpenAIClient.class);
		AudioService audioService = mock(AudioService.class);
		SpeechService speechService = mock(SpeechService.class);
		HttpResponse httpResponse = mock(HttpResponse.class);
		Headers headers = mock(Headers.class);
		when(mockClient.audio()).thenReturn(audioService);
		when(audioService.speech()).thenReturn(speechService);
		when(speechService.create(any(SpeechCreateParams.class), any(RequestOptions.class))).thenReturn(httpResponse);
		when(httpResponse.body()).thenReturn(new ByteArrayInputStream(new byte[] { 1, 2, 3 }));
		when(httpResponse.headers()).thenReturn(headers);
		when(headers.values(anyString())).thenReturn(List.of());

		OpenAiAudioSpeechModel model = OpenAiAudioSpeechModel.builder().openAiClient(mockClient).build();

		model.call(new TextToSpeechPrompt("hello",
				OpenAiAudioSpeechOptions.builder().customHeaders(Map.of(HEADER, VALUE)).build()));

		ArgumentCaptor<SpeechCreateParams> captor = ArgumentCaptor.forClass(SpeechCreateParams.class);
		verify(speechService).create(captor.capture(), any(RequestOptions.class));
		assertThat(captor.getValue()._additionalHeaders().values(HEADER)).containsExactly(VALUE);
	}

	@Test
	void moderationPropagatesPerCallCustomHeaders() {
		OpenAIClient mockClient = mock(OpenAIClient.class, RETURNS_DEEP_STUBS);
		when(mockClient.moderations().create(any(ModerationCreateParams.class), any(RequestOptions.class))).thenReturn(
				ModerationCreateResponse.builder().id("TEST_ID").model("TEST_MODEL").results(List.of()).build());

		OpenAiModerationModel model = OpenAiModerationModel.builder().openAiClient(mockClient).build();

		model.call(new ModerationPrompt("hi",
				OpenAiModerationOptions.builder().customHeaders(Map.of(HEADER, VALUE)).build()));

		ArgumentCaptor<ModerationCreateParams> captor = ArgumentCaptor.forClass(ModerationCreateParams.class);
		verify(mockClient.moderations()).create(captor.capture(), any(RequestOptions.class));
		assertThat(captor.getValue()._additionalHeaders().values(HEADER)).containsExactly(VALUE);
	}

}
