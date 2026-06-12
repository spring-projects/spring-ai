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

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import com.google.genai.Client;
import com.google.genai.Models;
import com.google.genai.types.Blob;
import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.core.retry.RetryListener;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.core.retry.Retryable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author Olivier Le Quellec
 */
@ExtendWith(MockitoExtension.class)
public class GoogleGenAiImageRetryTests {

	private TestRetryListener retryListener;

	private RetryTemplate retryTemplate;

	private Client mockGenAiClient;

	@Mock
	private Models mockModels;

	@Mock
	private GoogleGenAiImageConnectionDetails mockConnectionDetails;

	private GoogleGenAiImageModel imageModel;

	@BeforeEach
	public void setUp() throws Exception {
		this.retryTemplate = RetryUtils.SHORT_RETRY_TEMPLATE;
		this.retryListener = new TestRetryListener();
		this.retryTemplate.setRetryListener(this.retryListener);

		// Create a mock Client and use reflection to set the models field
		this.mockGenAiClient = mock(Client.class);
		Field modelsField = Client.class.getDeclaredField("models");
		modelsField.setAccessible(true);
		modelsField.set(this.mockGenAiClient, this.mockModels);

		// Set up the mock connection details to return the mock client
		given(this.mockConnectionDetails.getGenAiClient()).willReturn(this.mockGenAiClient);
		given(this.mockConnectionDetails.getModelEndpointName(anyString()))
			.willAnswer(invocation -> invocation.getArgument(0));

		this.imageModel = new GoogleGenAiImageModel(this.mockConnectionDetails,
				GoogleGenAiImageOptions.builder().build(), this.retryTemplate);
	}

	@Test
	public void googleGenAiImageTransientError() {
		// Create mock image response: candidate -> content -> part -> inline image data
		Blob mockBlob = mock(Blob.class);
		given(mockBlob.data()).willReturn(Optional.of(new byte[] { 1, 2, 3 }));
		given(mockBlob.mimeType()).willReturn(Optional.of("image/png"));

		Part mockPart = mock(Part.class);
		given(mockPart.inlineData()).willReturn(Optional.of(mockBlob));

		Content mockContent = mock(Content.class);
		given(mockContent.parts()).willReturn(Optional.of(List.of(mockPart)));

		Candidate mockCandidate = mock(Candidate.class);
		given(mockCandidate.content()).willReturn(Optional.of(mockContent));

		GenerateContentResponse mockResponse = mock(GenerateContentResponse.class);
		given(mockResponse.candidates()).willReturn(Optional.of(List.of(mockCandidate)));

		// Setup the mock client to throw transient errors then succeed
		given(this.mockModels.generateContent(anyString(), anyString(), any(GenerateContentConfig.class)))
			.willThrow(new TransientAiException("Transient Error 1"))
			.willThrow(new TransientAiException("Transient Error 2"))
			.willReturn(mockResponse);

		var options = GoogleGenAiImageOptions.builder().model("model").build();
		ImageResponse result = this.imageModel
			.call(new ImagePrompt("A light cream colored mini golden doodle", options));

		assertThat(result).isNotNull();
		assertThat(result.getResults()).hasSize(1);
		assertThat(result.getResults().get(0).getOutput()).isNotNull();
		assertThat(this.retryListener.onSuccessRetryCount).isEqualTo(1);
		assertThat(this.retryListener.onErrorRetryCount).isEqualTo(2);

		verify(this.mockModels, times(3)).generateContent(anyString(), anyString(), any(GenerateContentConfig.class));
	}

	@Test
	public void googleGenAiImageNonTransientError() {
		// Setup the mock client to throw a non-transient error
		given(this.mockModels.generateContent(anyString(), anyString(), any(GenerateContentConfig.class)))
			.willThrow(new RuntimeException("Non Transient Error"));

		var options = GoogleGenAiImageOptions.builder().model("model").build();
		// Assert that a RuntimeException is thrown and not retried
		assertThatThrownBy(
				() -> this.imageModel.call(new ImagePrompt("A light cream colored mini golden doodle", options)))
			.isInstanceOf(RuntimeException.class);

		// Verify that generateContent was called only once (no retries for non-transient
		// errors)
		verify(this.mockModels, times(1)).generateContent(anyString(), anyString(), any(GenerateContentConfig.class));
	}

	private static class TestRetryListener implements RetryListener {

		int onErrorRetryCount = 0;

		int onSuccessRetryCount = 0;

		@Override
		public void beforeRetry(final RetryPolicy retryPolicy, final Retryable<?> retryable) {
			// Count each retry attempt
			this.onErrorRetryCount++;
		}

		@Override
		public void onRetrySuccess(final RetryPolicy retryPolicy, final Retryable<?> retryable, final Object result) {
			// Count successful retries - we increment when we succeed after a failure
			this.onSuccessRetryCount++;
		}

	}

}
