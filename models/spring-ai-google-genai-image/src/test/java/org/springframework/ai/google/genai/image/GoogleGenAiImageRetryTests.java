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

import com.google.genai.Client;
import com.google.genai.Models;
import com.google.genai.types.GenerateImagesConfig;
import com.google.genai.types.GenerateImagesResponse;
import com.google.genai.types.GeneratedImage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Retry tests for {@link GoogleGenAiImageModel}, mirroring the pattern used by
 * {@code GoogleGenAiTextEmbeddingRetryTests}.
 *
 * @author Olivier Le Quellec
 */
@ExtendWith(MockitoExtension.class)
class GoogleGenAiImageRetryTests {

	private TestRetryListener retryListener;

	private RetryTemplate retryTemplate;

	private Client mockGenAiClient;

	private Models mockModels;

	private GoogleGenAiImageConnectionDetails mockConnectionDetails;

	private GoogleGenAiImageModel imageModel;

	@BeforeEach
	void setUp() throws Exception {
		this.retryTemplate = RetryUtils.SHORT_RETRY_TEMPLATE;
		this.retryListener = new TestRetryListener();
		this.retryTemplate.setRetryListener(this.retryListener);

		this.mockGenAiClient = mock(Client.class);
		this.mockModels = mock(Models.class);

		Field modelsField = Client.class.getDeclaredField("models");
		modelsField.setAccessible(true);
		modelsField.set(this.mockGenAiClient, this.mockModels);

		this.mockConnectionDetails = mock(GoogleGenAiImageConnectionDetails.class);
		given(this.mockConnectionDetails.getGenAiClient()).willReturn(this.mockGenAiClient);
		given(this.mockConnectionDetails.getModelEndpointName(anyString()))
			.willAnswer(invocation -> invocation.getArgument(0));

		GoogleGenAiImageOptions defaultOptions = GoogleGenAiImageOptions.builder()
			.model(GoogleGenAiImageModelName.IMAGEN_4_0_GENERATE)
			.n(1)
			.build();

		this.imageModel = new GoogleGenAiImageModel(this.mockConnectionDetails, defaultOptions, this.retryTemplate);
	}

	@Test
	void imageGenerationTransientError() {
		GeneratedImage mockGenerated = mock(GeneratedImage.class);
		given(mockGenerated.image()).willReturn(java.util.Optional.empty());
		given(mockGenerated.enhancedPrompt()).willReturn(java.util.Optional.empty());
		given(mockGenerated.raiFilteredReason()).willReturn(java.util.Optional.empty());

		GenerateImagesResponse mockResponse = mock(GenerateImagesResponse.class);
		given(mockResponse.generatedImages()).willReturn(java.util.Optional.of(List.of(mockGenerated)));

		given(this.mockModels.generateImages(anyString(), anyString(), any(GenerateImagesConfig.class)))
			.willThrow(new TransientAiException("Transient Error 1"))
			.willThrow(new TransientAiException("Transient Error 2"))
			.willReturn(mockResponse);

		ImageResponse response = this.imageModel.call(new ImagePrompt("A serene mountain landscape"));

		assertThat(response).isNotNull();
		assertThat(response.getResults()).hasSize(1);
		assertThat(this.retryListener.onSuccessRetryCount).isEqualTo(1);
		assertThat(this.retryListener.onErrorRetryCount).isEqualTo(2);

		verify(this.mockModels, times(3)).generateImages(anyString(), anyString(), any(GenerateImagesConfig.class));
	}

	@Test
	void imageGenerationNonTransientError() {
		given(this.mockModels.generateImages(anyString(), anyString(), any(GenerateImagesConfig.class)))
			.willThrow(new RuntimeException("Non Transient Error"));

		assertThatThrownBy(() -> this.imageModel.call(new ImagePrompt("A red apple")))
			.isInstanceOf(RuntimeException.class);

		verify(this.mockModels, times(1)).generateImages(anyString(), anyString(), any(GenerateImagesConfig.class));
	}

	private static class TestRetryListener implements RetryListener {

		int onErrorRetryCount = 0;

		int onSuccessRetryCount = 0;

		@Override
		public void beforeRetry(final RetryPolicy retryPolicy, final Retryable<?> retryable) {
			this.onErrorRetryCount++;
		}

		@Override
		public void onRetrySuccess(final RetryPolicy retryPolicy, final Retryable<?> retryable, final Object result) {
			this.onSuccessRetryCount++;
		}

	}

}
