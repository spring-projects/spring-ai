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

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

import com.google.genai.Client;
import com.google.genai.Models;
import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.retry.RetryListener;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.core.retry.Retryable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Retry tests for {@link GoogleGenAiTranscriptionModel}.
 *
 * @author Olivier Le Quellec
 */
@ExtendWith(MockitoExtension.class)
class GoogleGenAiTranscriptionRetryTests {

	private TestRetryListener retryListener;

	private RetryTemplate retryTemplate;

	private Client mockGenAiClient;

	@Mock
	private Models mockModels;

	private GoogleGenAiTranscriptionModel transcriptionModel;

	@BeforeEach
	void setUp() throws Exception {
		this.retryTemplate = RetryUtils.SHORT_RETRY_TEMPLATE;
		this.retryListener = new TestRetryListener();
		this.retryTemplate.setRetryListener(this.retryListener);

		// Client and Models are final classes; 'models' is a public final field, so set
		// it via reflection on the mock.
		this.mockGenAiClient = mock(Client.class);
		Field modelsField = Client.class.getDeclaredField("models");
		modelsField.setAccessible(true);
		modelsField.set(this.mockGenAiClient, this.mockModels);

		GoogleGenAiTranscriptionConnectionDetails connectionDetails = GoogleGenAiTranscriptionConnectionDetails
			.builder()
			.client(this.mockGenAiClient)
			.build();

		this.transcriptionModel = new GoogleGenAiTranscriptionModel(connectionDetails,
				GoogleGenAiAudioTranscriptionOptions.builder().build(), this.retryTemplate);
	}

	@Test
	void transientErrorIsRetried() {
		GenerateContentResponse mockResponse = GenerateContentResponse.builder()
			.candidates(Candidate.builder()
				.content(Content.builder().role("model").parts(Part.fromText("hello world")).build())
				.build())
			.build();

		given(this.mockModels.generateContent(anyString(), any(Content.class), nullable(GenerateContentConfig.class)))
			.willThrow(new TransientAiException("Transient Error 1"))
			.willThrow(new TransientAiException("Transient Error 2"))
			.willReturn(mockResponse);

		AudioTranscriptionResponse response = this.transcriptionModel
			.call(new AudioTranscriptionPrompt(audioResource()));

		assertThat(response).isNotNull();
		assertThat(response.getResult().getOutput()).isEqualTo("hello world");
		assertThat(this.retryListener.onSuccessRetryCount).isEqualTo(1);
		assertThat(this.retryListener.onErrorRetryCount).isEqualTo(2);

		verify(this.mockModels, times(3)).generateContent(anyString(), any(Content.class),
				nullable(GenerateContentConfig.class));
	}

	@Test
	void nonTransientErrorIsNotRetried() {
		given(this.mockModels.generateContent(anyString(), any(Content.class), nullable(GenerateContentConfig.class)))
			.willThrow(new RuntimeException("Non Transient Error"));

		assertThatThrownBy(() -> this.transcriptionModel.call(new AudioTranscriptionPrompt(audioResource())))
			.isInstanceOf(RuntimeException.class);

		verify(this.mockModels, times(1)).generateContent(anyString(), any(Content.class),
				nullable(GenerateContentConfig.class));
	}

	private static Resource audioResource() {
		return new ByteArrayResource("fake-audio".getBytes(StandardCharsets.UTF_8)) {
			@Override
			public String getFilename() {
				return "sample.wav";
			}
		};
	}

	private static class TestRetryListener implements RetryListener {

		int onErrorRetryCount = 0;

		int onSuccessRetryCount = 0;

		@Override
		public void beforeRetry(final @Nullable RetryPolicy retryPolicy, final @Nullable Retryable<?> retryable) {
			// Count each retry attempt
			this.onErrorRetryCount++;
		}

		@Override
		public void onRetrySuccess(final @Nullable RetryPolicy retryPolicy, final @Nullable Retryable<?> retryable,
				final @Nullable Object result) {
			// Count successful retries — we increment when we succeed after a failure
			this.onSuccessRetryCount++;
		}

	}

}
