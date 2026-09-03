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

import java.io.IOException;

import com.google.cloud.speech.v2.RecognizeRequest;
import com.google.cloud.speech.v2.RecognizeResponse;
import com.google.cloud.speech.v2.SpeechClient;
import com.google.cloud.speech.v2.SpeechRecognitionAlternative;
import com.google.cloud.speech.v2.SpeechRecognitionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.retry.RetryTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Retry tests for {@link GoogleGenAiTranscriptionModel}.
 *
 * @author Olivier Le Quellec
 */
class GoogleGenAiTranscriptionRetryTests {

	private SpeechClient mockSpeechClient;

	private GoogleGenAiTranscriptionModel transcriptionModel;

	private final Resource audioResource = new ByteArrayResource("audio-bytes".getBytes());

	@BeforeEach
	void setUp() throws IOException {
		RetryTemplate retryTemplate = RetryUtils.SHORT_RETRY_TEMPLATE;
		this.mockSpeechClient = mock(SpeechClient.class);

		GoogleGenAiTranscriptionConnectionDetails connectionDetails = GoogleGenAiTranscriptionConnectionDetails
			.builder()
			.projectId("my-project")
			.speechClient(this.mockSpeechClient)
			.build();

		this.transcriptionModel = new GoogleGenAiTranscriptionModel(connectionDetails,
				GoogleGenAiAudioTranscriptionOptions.builder().build(), retryTemplate);
	}

	@Test
	void transientErrorIsRetried() {
		given(this.mockSpeechClient.recognize(any(RecognizeRequest.class)))
			.willThrow(new TransientAiException("Transient Error 1"))
			.willThrow(new TransientAiException("Transient Error 2"))
			.willReturn(buildResponse("hello world"));

		AudioTranscriptionResponse response = this.transcriptionModel
			.call(new AudioTranscriptionPrompt(this.audioResource));

		assertThat(response.getResult().getOutput()).isEqualTo("hello world");
		verify(this.mockSpeechClient, times(3)).recognize(any(RecognizeRequest.class));
	}

	@Test
	void nonTransientErrorIsNotRetried() {
		given(this.mockSpeechClient.recognize(any(RecognizeRequest.class)))
			.willThrow(new RuntimeException("Non Transient Error"));

		assertThatThrownBy(() -> this.transcriptionModel.call(new AudioTranscriptionPrompt(this.audioResource)))
			.isInstanceOf(RuntimeException.class);

		verify(this.mockSpeechClient, times(1)).recognize(any(RecognizeRequest.class));
	}

	private RecognizeResponse buildResponse(String transcript) {
		return RecognizeResponse.newBuilder()
			.addResults(SpeechRecognitionResult.newBuilder()
				.addAlternatives(SpeechRecognitionAlternative.newBuilder().setTranscript(transcript).build())
				.build())
			.build();
	}

}
