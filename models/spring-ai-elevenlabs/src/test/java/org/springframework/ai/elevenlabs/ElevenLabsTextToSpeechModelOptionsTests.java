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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.ai.audio.tts.TextToSpeechOptions;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.elevenlabs.api.ElevenLabsApi;
import org.springframework.ai.elevenlabs.api.ElevenLabsApi.SpeechRequest.VoiceSettings;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for how {@link ElevenLabsTextToSpeechModel} merges runtime and default options.
 *
 * @author Minju Kim
 */
class ElevenLabsTextToSpeechModelOptionsTests {

	private final ElevenLabsApi api = mock(ElevenLabsApi.class);

	private final ArgumentCaptor<ElevenLabsApi.SpeechRequest> request = ArgumentCaptor
		.forClass(ElevenLabsApi.SpeechRequest.class);

	private final ArgumentCaptor<String> voiceId = ArgumentCaptor.forClass(String.class);

	@SuppressWarnings("unchecked")
	private final ArgumentCaptor<MultiValueMap<String, String>> queryParameters = ArgumentCaptor
		.forClass(MultiValueMap.class);

	private ElevenLabsTextToSpeechModel model;

	@BeforeEach
	void setUp() {
		when(this.api.textToSpeech(any(), anyString(), any())).thenReturn(ResponseEntity.ok(new byte[] { 1 }));
		this.model = ElevenLabsTextToSpeechModel.builder()
			.elevenLabsApi(this.api)
			.options(ElevenLabsTextToSpeechOptions.builder()
				.modelId("default-model")
				.voiceId("default-voice")
				.outputFormat("mp3_44100_128")
				.enableLogging(false)
				.voiceSettings(new VoiceSettings(0.5, 0.75, 0.0, true, 1.0))
				.build())
			.retryTemplate(RetryUtils.SHORT_RETRY_TEMPLATE)
			.build();
	}

	@Test
	void portableRuntimeOptionsAreApplied() {
		TextToSpeechOptions runtimeOptions = TextToSpeechOptions.builder()
			.model("runtime-model")
			.voice("runtime-voice")
			.format("pcm_16000")
			.speed(1.2)
			.build();

		this.model.call(new TextToSpeechPrompt("Hello", runtimeOptions));

		verify(this.api).textToSpeech(this.request.capture(), this.voiceId.capture(), this.queryParameters.capture());
		assertThat(this.voiceId.getValue()).isEqualTo("runtime-voice");
		assertThat(this.request.getValue().modelId()).isEqualTo("runtime-model");
		assertThat(this.request.getValue().voiceSettings()).isEqualTo(new VoiceSettings(0.5, 0.75, 0.0, true, 1.2));
		assertThat(this.queryParameters.getValue().getFirst("output_format")).isEqualTo("pcm_16000");
		assertThat(this.queryParameters.getValue().getFirst("enable_logging")).isEqualTo("false");
	}

	@Test
	void defaultOptionsAreUsedWhenRuntimeOptionsAreEmpty() {
		this.model.call(new TextToSpeechPrompt("Hello"));

		verify(this.api).textToSpeech(this.request.capture(), this.voiceId.capture(), this.queryParameters.capture());
		assertThat(this.voiceId.getValue()).isEqualTo("default-voice");
		assertThat(this.request.getValue().modelId()).isEqualTo("default-model");
		assertThat(this.request.getValue().voiceSettings()).isEqualTo(new VoiceSettings(0.5, 0.75, 0.0, true, 1.0));
		assertThat(this.queryParameters.getValue().getFirst("output_format")).isEqualTo("mp3_44100_128");
		assertThat(this.queryParameters.getValue().getFirst("enable_logging")).isEqualTo("false");
	}

	@Test
	void enableLoggingIsKeptWhenMergingElevenLabsRuntimeOptions() {
		this.model.call(new TextToSpeechPrompt("Hello", ElevenLabsTextToSpeechOptions.builder().seed(42).build()));

		verify(this.api).textToSpeech(this.request.capture(), this.voiceId.capture(), this.queryParameters.capture());
		assertThat(this.request.getValue().seed()).isEqualTo(42);
		assertThat(this.queryParameters.getValue().getFirst("enable_logging")).isEqualTo("false");
	}

}
