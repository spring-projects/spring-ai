/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.google.genai.text;

import java.lang.reflect.Field;
import java.util.List;

import com.google.genai.Client;
import com.google.genai.Models;
import com.google.genai.types.ContentEmbedding;
import com.google.genai.types.EmbedContentConfig;
import com.google.genai.types.EmbedContentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.google.genai.GoogleGenAiEmbeddingConnectionDetails;
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
 * @author Mark Pollack
 * @author Dan Dobrin
 */
@ExtendWith(MockitoExtension.class)
public class GoogleGenAiTextEmbeddingRetryTests {

	private TestRetryListener retryListener;

	private RetryTemplate retryTemplate;

	private Client mockGenAiClient;

	@Mock
	private Models mockModels;

	@Mock
	private GoogleGenAiEmbeddingConnectionDetails mockConnectionDetails;

	private GoogleGenAiTextEmbeddingModel embeddingModel;

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

		this.embeddingModel = new GoogleGenAiTextEmbeddingModel(this.mockConnectionDetails,
				GoogleGenAiTextEmbeddingOptions.builder().build(), this.retryTemplate);
	}

	@Test
	public void vertexAiEmbeddingTransientError() {
		// Create mock embedding response
		ContentEmbedding mockEmbedding = mock(ContentEmbedding.class);
		given(mockEmbedding.values()).willReturn(java.util.Optional.of(List.of(9.9f, 8.8f)));
		given(mockEmbedding.statistics()).willReturn(java.util.Optional.empty());

		EmbedContentResponse mockResponse = mock(EmbedContentResponse.class);
		given(mockResponse.embeddings()).willReturn(java.util.Optional.of(List.of(mockEmbedding)));

		// Setup the mock client to throw transient errors then succeed
		given(this.mockModels.embedContent(anyString(), any(List.class), any(EmbedContentConfig.class)))
			.willThrow(new TransientAiException("Transient Error 1"))
			.willThrow(new TransientAiException("Transient Error 2"))
			.willReturn(mockResponse);

		EmbeddingOptions options = GoogleGenAiTextEmbeddingOptions.builder().model("model").build();
		EmbeddingResponse result = this.embeddingModel.call(new EmbeddingRequest(List.of("text1", "text2"), options));

		assertThat(result).isNotNull();
		assertThat(result.getResults()).hasSize(1);
		assertThat(result.getResults().get(0).getOutput()).isEqualTo(new float[] { 9.9f, 8.8f });
		assertThat(this.retryListener.onSuccessRetryCount).isEqualTo(1);
		assertThat(this.retryListener.onErrorRetryCount).isEqualTo(2);

		verify(this.mockModels, times(3)).embedContent(anyString(), any(List.class), any(EmbedContentConfig.class));
	}

	@Test
	public void vertexAiEmbeddingNonTransientError() {
		// Setup the mock client to throw a non-transient error
		given(this.mockModels.embedContent(anyString(), any(List.class), any(EmbedContentConfig.class)))
			.willThrow(new RuntimeException("Non Transient Error"));

		EmbeddingOptions options = GoogleGenAiTextEmbeddingOptions.builder().model("model").build();
		// Assert that a RuntimeException is thrown and not retried
		assertThatThrownBy(() -> this.embeddingModel.call(new EmbeddingRequest(List.of("text1", "text2"), options)))
			.isInstanceOf(RuntimeException.class);

		// Verify that embedContent was called only once (no retries for non-transient
		// errors)
		verify(this.mockModels, times(1)).embedContent(anyString(), any(List.class), any(EmbedContentConfig.class));
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
