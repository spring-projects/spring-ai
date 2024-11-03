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

package org.springframework.ai.vertexai.embedding.text;

import java.util.List;

import com.google.cloud.aiplatform.v1.PredictRequest;
import com.google.cloud.aiplatform.v1.PredictResponse;
import com.google.cloud.aiplatform.v1.PredictionServiceClient;
import com.google.cloud.aiplatform.v1.PredictionServiceSettings;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.ai.vertexai.embedding.VertexAiEmbeddingConnectionDetails;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.support.RetryTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author Mark Pollack
 */
@ExtendWith(MockitoExtension.class)
public class VertexAiTextEmbeddingRetryTests {

	private TestRetryListener retryListener;

	private RetryTemplate retryTemplate;

	@Mock
	private PredictionServiceClient mockPredictionServiceClient;

	@Mock
	private VertexAiEmbeddingConnectionDetails mockConnectionDetails;

	@Mock
	private PredictRequest.Builder mockPredictRequestBuilder;

	@Mock
	private PredictionServiceSettings mockPredictionServiceSettings;

	private TestVertexAiTextEmbeddingModel embeddingModel;

	@BeforeEach
	public void setUp() {
		this.retryTemplate = RetryUtils.DEFAULT_RETRY_TEMPLATE;
		this.retryListener = new TestRetryListener();
		this.retryTemplate.registerListener(this.retryListener);

		this.embeddingModel = new TestVertexAiTextEmbeddingModel(this.mockConnectionDetails,
				VertexAiTextEmbeddingOptions.builder().build(), this.retryTemplate);
		this.embeddingModel.setMockPredictionServiceClient(this.mockPredictionServiceClient);
		this.embeddingModel.setMockPredictRequestBuilder(this.mockPredictRequestBuilder);
		given(this.mockPredictRequestBuilder.build()).willReturn(PredictRequest.getDefaultInstance());
	}

	@Test
	public void vertexAiEmbeddingTransientError() {
		// Setup the mock PredictResponse
		PredictResponse mockResponse = PredictResponse.newBuilder()
			.addPredictions(Value.newBuilder()
				.setStructValue(Struct.newBuilder()
					.putFields("embeddings", Value.newBuilder()
						.setStructValue(Struct.newBuilder()
							.putFields("values",
									Value.newBuilder()
										.setListValue(com.google.protobuf.ListValue.newBuilder()
											.addValues(Value.newBuilder().setNumberValue(9.9))
											.addValues(Value.newBuilder().setNumberValue(8.8))
											.build())
										.build())
							.putFields("statistics",
									Value.newBuilder()
										.setStructValue(Struct.newBuilder()
											.putFields("token_count", Value.newBuilder().setNumberValue(10).build())
											.build())
										.build())
							.build())
						.build())
					.build())
				.build())
			.build();

		// Setup the mock PredictionServiceClient
		given(this.mockPredictionServiceClient.predict(any())).willThrow(new TransientAiException("Transient Error 1"))
			.willThrow(new TransientAiException("Transient Error 2"))
			.willReturn(mockResponse);

		EmbeddingResponse result = this.embeddingModel.call(new EmbeddingRequest(List.of("text1", "text2"), null));

		assertThat(result).isNotNull();
		assertThat(result.getResults()).hasSize(1);
		assertThat(result.getResults().get(0).getOutput()).isEqualTo(new float[] { 9.9f, 8.8f });
		assertThat(this.retryListener.onSuccessRetryCount).isEqualTo(2);
		assertThat(this.retryListener.onErrorRetryCount).isEqualTo(2);

		verify(this.mockPredictRequestBuilder, times(3)).build();
	}

	@Test
	public void vertexAiEmbeddingNonTransientError() {
		// Setup the mock PredictionServiceClient to throw a non-transient error
		given(this.mockPredictionServiceClient.predict(any())).willThrow(new RuntimeException("Non Transient Error"));

		// Assert that a RuntimeException is thrown and not retried
		assertThatThrownBy(() -> this.embeddingModel.call(new EmbeddingRequest(List.of("text1", "text2"), null)))
			.isInstanceOf(RuntimeException.class);

		// Verify that predict was called only once (no retries for non-transient errors)
		verify(this.mockPredictionServiceClient, times(1)).predict(any());
	}

	private static class TestRetryListener implements RetryListener {

		int onErrorRetryCount = 0;

		int onSuccessRetryCount = 0;

		@Override
		public <T, E extends Throwable> void onSuccess(RetryContext context, RetryCallback<T, E> callback, T result) {
			this.onSuccessRetryCount = context.getRetryCount();
		}

		@Override
		public <T, E extends Throwable> void onError(RetryContext context, RetryCallback<T, E> callback,
				Throwable throwable) {
			this.onErrorRetryCount = context.getRetryCount();
		}

	}

}
