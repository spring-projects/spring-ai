package org.springframework.ai.vertexai.embedding.text;

import com.google.cloud.aiplatform.v1.PredictRequest;
import com.google.cloud.aiplatform.v1.PredictionServiceClient;
import com.google.cloud.aiplatform.v1.PredictResponse;
import com.google.cloud.aiplatform.v1.PredictionServiceSettings;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.ai.vertexai.embedding.VertexAiEmbeddingConnectionDetails;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.support.RetryTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class VertexAiTextEmbeddingRetryTests {

	private static class TestRetryListener implements RetryListener {

		int onErrorRetryCount = 0;

		int onSuccessRetryCount = 0;

		@Override
		public <T, E extends Throwable> void onSuccess(RetryContext context, RetryCallback<T, E> callback, T result) {
			onSuccessRetryCount = context.getRetryCount();
		}

		@Override
		public <T, E extends Throwable> void onError(RetryContext context, RetryCallback<T, E> callback,
				Throwable throwable) {
			onErrorRetryCount = context.getRetryCount();
		}

	}

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
		retryTemplate = RetryUtils.DEFAULT_RETRY_TEMPLATE;
		retryListener = new TestRetryListener();
		retryTemplate.registerListener(retryListener);

		embeddingModel = new TestVertexAiTextEmbeddingModel(mockConnectionDetails,
				VertexAiTextEmbeddingOptions.builder().build(), retryTemplate);
		embeddingModel.setMockPredictionServiceClient(mockPredictionServiceClient);
		embeddingModel.setMockPredictRequestBuilder(mockPredictRequestBuilder);
		when(mockPredictRequestBuilder.build()).thenReturn(PredictRequest.getDefaultInstance());
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
		when(mockPredictionServiceClient.predict(any())).thenThrow(new TransientAiException("Transient Error 1"))
			.thenThrow(new TransientAiException("Transient Error 2"))
			.thenReturn(mockResponse);

		EmbeddingResponse result = embeddingModel.call(new EmbeddingRequest(List.of("text1", "text2"), null));

		assertThat(result).isNotNull();
		assertThat(result.getResults()).hasSize(1);
		assertThat(result.getResults().get(0).getOutput()).isEqualTo(new float[] { 9.9f, 8.8f });
		assertThat(retryListener.onSuccessRetryCount).isEqualTo(2);
		assertThat(retryListener.onErrorRetryCount).isEqualTo(2);

		verify(mockPredictRequestBuilder, times(3)).build();
	}

	@Test
	public void vertexAiEmbeddingNonTransientError() {
		// Setup the mock PredictionServiceClient to throw a non-transient error
		when(mockPredictionServiceClient.predict(any()))
				.thenThrow(new RuntimeException("Non Transient Error"));

		// Assert that a RuntimeException is thrown and not retried
		assertThrows(RuntimeException.class, () -> embeddingModel
				.call(new EmbeddingRequest(List.of("text1", "text2"), null)));

		// Verify that predict was called only once (no retries for non-transient errors)
		verify(mockPredictionServiceClient, times(1)).predict(any());
	}

}
