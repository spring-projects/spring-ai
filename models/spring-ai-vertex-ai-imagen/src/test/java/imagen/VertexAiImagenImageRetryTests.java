/*
 * Copyright 2025-2026 the original author or authors.
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

package imagen;

import com.google.cloud.aiplatform.v1.PredictRequest;
import com.google.cloud.aiplatform.v1.PredictResponse;
import com.google.cloud.aiplatform.v1.PredictionServiceClient;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.ai.vertexai.imagen.VertexAiImagenConnectionDetails;
import org.springframework.ai.vertexai.imagen.VertexAiImagenImageOptions;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.support.RetryTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author Sami Marzouki
 */
@ExtendWith(MockitoExtension.class)
public class VertexAiImagenImageRetryTests {

	private TestRetryListener retryListener;

	@Mock
	private PredictionServiceClient mockPredictionServiceClient;

	@Mock
	private VertexAiImagenConnectionDetails mockConnectionDetails;

	@Mock
	private PredictRequest.Builder mockPredictRequestBuilder;

	private TestVertexAiImagenImageModel imageModel;

	@BeforeEach
	public void setUp() {
		RetryTemplate retryTemplate = RetryUtils.SHORT_RETRY_TEMPLATE;
		this.retryListener = new TestRetryListener();
		retryTemplate.registerListener(this.retryListener);

		this.imageModel = new TestVertexAiImagenImageModel(this.mockConnectionDetails,
				VertexAiImagenImageOptions.builder().build(), retryTemplate);
		this.imageModel.setMockPredictionServiceClient(this.mockPredictionServiceClient);
		this.imageModel.setMockPredictRequestBuilder(this.mockPredictRequestBuilder);
		given(this.mockPredictRequestBuilder.build()).willReturn(PredictRequest.getDefaultInstance());
	}

	@Test
	public void vertexAiImageGeneratorTransientError() {
		// Set up the mock PredictResponse
		PredictResponse mockResponse = PredictResponse.newBuilder()
			.addPredictions(Value.newBuilder()
				.setStructValue(Struct.newBuilder()
					.putFields("bytesBase64Encoded", Value.newBuilder().setStringValue("BASE64_IMG_BYTES").build())
					.putFields("mimeType", Value.newBuilder().setStringValue("image/png").build())
					.build())
				.build())
			.addPredictions(Value.newBuilder()
				.setStructValue(Struct.newBuilder()
					.putFields("mimeType", Value.newBuilder().setStringValue("image/png").build())
					.putFields("bytesBase64Encoded", Value.newBuilder().setStringValue("BASE64_IMG_BYTES").build())
					.build())
				.build())
			.build();

		// Set up the mock PredictionServiceClient
		given(this.mockPredictionServiceClient.predict(any())).willThrow(new TransientAiException("Transient Error 1"))
			.willThrow(new TransientAiException("Transient Error 2"))
			.willReturn(mockResponse);

		ImageResponse result = this.imageModel.call(new ImagePrompt("text1", null));

		assertThat(result).isNotNull();
		assertThat(result.getResults()).hasSize(2);
		assertThat(result.getResults().get(0).getOutput().getB64Json()).isEqualTo("BASE64_IMG_BYTES");
		assertThat(result.getResults().get(1).getOutput().getB64Json()).isEqualTo("BASE64_IMG_BYTES");
		assertThat(this.retryListener.onSuccessRetryCount).isEqualTo(2);
		assertThat(this.retryListener.onErrorRetryCount).isEqualTo(2);

		verify(this.mockPredictRequestBuilder, times(3)).build();
	}

	@Test
	public void vertexAiImageGeneratorNonTransientError() {
		// Set up the mock PredictionServiceClient to throw a non-transient error
		given(this.mockPredictionServiceClient.predict(any())).willThrow(new RuntimeException("Non Transient Error"));

		// Assert that a RuntimeException is thrown and not retried
		assertThatThrownBy(() -> this.imageModel.call(new ImagePrompt("text1", null)))
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
