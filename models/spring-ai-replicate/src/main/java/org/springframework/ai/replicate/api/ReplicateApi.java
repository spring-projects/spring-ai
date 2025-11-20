/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.replicate.api;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import org.springframework.ai.model.ApiKey;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Client for the Replicate Predictions API
 *
 * @author Rene Maierhofer
 * @since 1.1.0
 */
public final class ReplicateApi {

	private static final Logger logger = LoggerFactory.getLogger(ReplicateApi.class);

	private static final String DEFAULT_BASE_URL = "https://api.replicate.com/v1";

	private static final String PREDICTIONS_PATH = "/predictions";

	private final RestClient restClient;

	private final WebClient webClient;

	private final RetryTemplate retryTemplate = RetryTemplate.builder()
		.retryOn(ReplicatePredictionNotFinishedException.class)
		.maxAttempts(60)
		.fixedBackoff(5000)
		.withListener(new RetryListener() {
			@Override
			public <T, E extends Throwable> void onError(RetryContext context, RetryCallback<T, E> callback,
					Throwable throwable) {
				logger.debug("Polling Replicate Prediction: {}/10 attempts.", context.getRetryCount());
			}
		})
		.build();

	public static final String PROVIDER_NAME = AiProvider.REPLICATE.value();

	private ReplicateApi(String baseUrl, ApiKey apiKey, RestClient.Builder restClientBuilder,
			WebClient.Builder webClientBuilder, ResponseErrorHandler responseErrorHandler) {
		Consumer<HttpHeaders> headers = h -> {
			h.setContentType(MediaType.APPLICATION_JSON);
			h.setBearerAuth(apiKey.getValue());
		};

		this.restClient = restClientBuilder.baseUrl(baseUrl)
			.defaultHeaders(headers)
			.defaultStatusHandler(responseErrorHandler)
			.build();

		this.webClient = webClientBuilder.clone().baseUrl(baseUrl).defaultHeaders(headers).build();
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Creates a Prediction using the model's endpoint.
	 * @param modelName The model name in format "owner/name" (e.g., "openai/gpt-5")
	 * @param request The prediction request
	 * @return The prediction response
	 */
	public PredictionResponse createPrediction(String modelName, PredictionRequest request) {
		Assert.hasText(modelName, "Model name must not be empty");

		String uri = "/models/" + modelName + PREDICTIONS_PATH;
		ResponseEntity<PredictionResponse> response = this.restClient.post()
			.uri(uri)
			.body(request)
			.retrieve()
			.toEntity(PredictionResponse.class);
		return response.getBody();
	}

	/**
	 * Retrieves the current status of the Prediction
	 * @param predictionId The prediction ID
	 * @return The prediction response
	 */
	public PredictionResponse getPrediction(String predictionId) {
		Assert.hasText(predictionId, "Prediction ID must not be empty");

		return this.restClient.get()
			.uri(PREDICTIONS_PATH + "/{id}", predictionId)
			.retrieve()
			.body(PredictionResponse.class);
	}

	/**
	 * Creates a prediction and waits for it to complete by polling the status. Uses the
	 * configured retry template.
	 * @param modelName The model name in format "owner/name"
	 * @param request The prediction request
	 * @return The completed prediction response
	 */
	public PredictionResponse createPredictionAndWait(String modelName, PredictionRequest request) {
		PredictionResponse prediction = createPrediction(modelName, request);
		if (prediction == null || prediction.id == null) {
			throw new ReplicatePredictionException("PredictionRequest did not return a valid response.");
		}
		return waitForCompletion(prediction.id());
	}

	/**
	 * Waits for the completed Prediction and returns the final Response.
	 * @param predictionId id of the prediction
	 * @return the final PredictionResponse
	 */
	public PredictionResponse waitForCompletion(String predictionId) {
		Assert.hasText(predictionId, "Prediction ID must not be empty");
		return this.retryTemplate.execute(context -> pollStatusFromReplicate(predictionId));
	}

	/**
	 * Polls the prediction status from replicate.
	 * @param predictionId the Prediction's id
	 * @return the final Prediction Response
	 */
	private PredictionResponse pollStatusFromReplicate(String predictionId) {
		PredictionResponse prediction = getPrediction(predictionId);
		if (prediction == null || prediction.id == null) {
			throw new ReplicatePredictionException("Polling for Prediction did not return a valid response.");
		}
		PredictionStatus status = prediction.status();
		if (status == PredictionStatus.SUCCEEDED) {
			return prediction;
		}
		else if (status == PredictionStatus.PROCESSING || status == PredictionStatus.STARTING) {
			throw new ReplicatePredictionNotFinishedException("Prediction not finished yet.");
		}
		else if (status == PredictionStatus.FAILED) {
			String error = prediction.error() != null ? prediction.error() : "Unknown error";
			throw new ReplicatePredictionException("Prediction failed: " + error);
		}
		else if (status == PredictionStatus.CANCELED || status == PredictionStatus.ABORTED) {
			throw new ReplicatePredictionException("Prediction was canceled");
		}
		throw new ReplicatePredictionException("Unknown Replicate Prediction Status");
	}

	/**
	 * Uploads a file to Replicate for usage in a request. <a href=
	 * "https://replicate.com/docs/topics/predictions/create-a-prediction#file-upload">Replicate
	 * Files API</a>
	 * @param fileResource The file to upload
	 * @param filename The filename to use for the uploaded file
	 * @return Upload response containing the URL to later send with a request.
	 */
	public FileUploadResponse uploadFile(Resource fileResource, String filename) {
		Assert.notNull(fileResource, "File resource must not be null");
		Assert.hasText(filename, "Filename must not be empty");

		MultipartBodyBuilder builder = new MultipartBodyBuilder();
		builder.part("content", fileResource)
			.headers(h -> h
				.setContentDisposition(ContentDisposition.formData().name("content").filename(filename).build()))
			.contentType(MediaType.APPLICATION_OCTET_STREAM);

		return this.webClient.post()
			.uri("/files")
			.contentType(MediaType.MULTIPART_FORM_DATA)
			.bodyValue(builder.build())
			.retrieve()
			.bodyToMono(FileUploadResponse.class)
			.block();
	}

	/**
	 * Creates a streaming prediction response. Replicate uses SSE for Streaming.
	 * <a href="https://replicate.com/docs/topics/predictions/streaming">Replicate
	 * Docs</a>
	 * @param modelName The model name in format "owner/name"
	 * @param request The prediction request (must have stream=true)
	 * @return A Flux stream of prediction response events with incremental output
	 */
	public Flux<PredictionResponse> createPredictionStream(String modelName, PredictionRequest request) {
		PredictionResponse initialResponse = createPrediction(modelName, request);
		if (initialResponse.urls() == null || initialResponse.urls().stream() == null) {
			logger.error("No stream URL in response: {}", initialResponse);
			return Flux.error(new ReplicatePredictionException("No stream URL returned from prediction"));
		}
		String streamUrl = initialResponse.urls().stream();
		ParameterizedTypeReference<ServerSentEvent<String>> typeRef = new ParameterizedTypeReference<>() {
		};

		return this.webClient.get()
			.uri(streamUrl)
			.accept(MediaType.TEXT_EVENT_STREAM)
			.header(HttpHeaders.CACHE_CONTROL, "no-store")
			.retrieve()
			.bodyToFlux(typeRef)
			.handle((event, sink) -> {
				String eventType = event.event();
				if ("error".equals(eventType)) {
					String errorMessage = event.data() != null ? event.data() : "Unknown error";
					sink.error(new ReplicatePredictionException("Streaming error: " + errorMessage));
					return;
				}
				if ("done".equals(eventType)) {
					sink.complete();
					return;
				}
				if ("output".equals(eventType)) {
					String dataContent = event.data() != null ? event.data() : "";
					PredictionResponse response = new PredictionResponse(initialResponse.id(), initialResponse.model(),
							initialResponse.version(), PredictionStatus.PROCESSING, initialResponse.input(),
							dataContent, // The output chunk
							null, null, null, initialResponse.urls(), initialResponse.createdAt(),
							initialResponse.startedAt(), null);

					sink.next(response);
				}
			});
	}

	/**
	 * Request to create a prediction
	 *
	 * @param version Optional model version
	 * @param input The input parameters for the model
	 * @param webhook Optional webhook URL for async notifications
	 * @param webhookEventsFilter Optional list of webhook events to subscribe to
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record PredictionRequest(@JsonProperty("version") String version,
			@JsonProperty("input") Map<String, Object> input, @JsonProperty("webhook") String webhook,
			@JsonProperty("webhook_events_filter") List<String> webhookEventsFilter,
			@JsonProperty("stream") boolean stream) {
	}

	/**
	 * Response from Replicate prediction API.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record PredictionResponse(@JsonProperty("id") String id, @JsonProperty("model") String model,
			@JsonProperty("version") String version, @JsonProperty("status") PredictionStatus status,
			@JsonProperty("input") Map<String, Object> input, @JsonProperty("output") Object output,
			@JsonProperty("error") String error, @JsonProperty("logs") String logs,
			@JsonProperty("metrics") Metrics metrics, @JsonProperty("urls") Urls urls,
			@JsonProperty("created_at") String createdAt, @JsonProperty("started_at") String startedAt,
			@JsonProperty("completed_at") String completedAt) {
	}

	/**
	 * Prediction status.
	 */
	public enum PredictionStatus {

		@JsonProperty("starting")
		STARTING,

		@JsonProperty("processing")
		PROCESSING,

		@JsonProperty("succeeded")
		SUCCEEDED,

		@JsonProperty("failed")
		FAILED,

		@JsonProperty("canceled")
		CANCELED,

		@JsonProperty("aborted")
		ABORTED

	}

	/**
	 * Metrics from a prediction including token counts and timing.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Metrics(@JsonProperty("predict_time") Double predictTime,
			@JsonProperty("input_token_count") Integer inputTokenCount,
			@JsonProperty("output_token_count") Integer outputTokenCount) {
	}

	/**
	 * URLs for interacting with a prediction.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Urls(@JsonProperty("get") String get, @JsonProperty("cancel") String cancel,
			@JsonProperty("stream") String stream) {
	}

	/**
	 * Response from Replicate file upload API.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record FileUploadResponse(@JsonProperty("id") String id, @JsonProperty("name") String name,
			@JsonProperty("content_type") String contentType, @JsonProperty("size") Long size,
			@JsonProperty("urls") FileUrls urls, @JsonProperty("created_at") String createdAt,
			@JsonProperty("expires_at") String expiresAt) {
	}

	/**
	 * URLs for accessing an uploaded file.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record FileUrls(@JsonProperty("get") String get) {
	}

	/**
	 * Builder to Construct a {@link ReplicateApi} instance
	 */
	public static final class Builder {

		private String baseUrl = DEFAULT_BASE_URL;

		private ApiKey apiKey;

		private RestClient.Builder restClientBuilder = RestClient.builder();

		private WebClient.Builder webClientBuilder = WebClient.builder();

		private ResponseErrorHandler responseErrorHandler = RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER;

		public Builder baseUrl(String baseUrl) {
			Assert.hasText(baseUrl, "baseUrl cannot be empty");
			this.baseUrl = baseUrl;
			return this;
		}

		public Builder apiKey(String apiKey) {
			Assert.notNull(apiKey, "ApiKey cannot be null");
			this.apiKey = new SimpleApiKey(apiKey);
			return this;
		}

		public Builder restClientBuilder(RestClient.Builder restClientBuilder) {
			Assert.notNull(restClientBuilder, "restClientBuilder cannot be null");
			this.restClientBuilder = restClientBuilder;
			return this;
		}

		public Builder webClientBuilder(WebClient.Builder webClientBuilder) {
			Assert.notNull(webClientBuilder, "webClientBuilder cannot be null");
			this.webClientBuilder = webClientBuilder;
			return this;
		}

		public Builder responseErrorHandler(ResponseErrorHandler responseErrorHandler) {
			Assert.notNull(responseErrorHandler, "responseErrorHandler cannot be null");
			this.responseErrorHandler = responseErrorHandler;
			return this;
		}

		public ReplicateApi build() {
			Assert.notNull(this.apiKey, "cannot construct instance without apiKey");
			return new ReplicateApi(this.baseUrl, this.apiKey, this.restClientBuilder, this.webClientBuilder,
					this.responseErrorHandler);
		}

	}

	/**
	 * Exception thrown when a Replicate prediction fails or times out.
	 */
	public static class ReplicatePredictionException extends RuntimeException {

		public ReplicatePredictionException(String message) {
			super(message);
		}

	}

	/**
	 * Exception thrown when a Replicate prediction has not finished yet. Used for
	 * RetryTemplate.
	 */
	public static class ReplicatePredictionNotFinishedException extends RuntimeException {

		public ReplicatePredictionNotFinishedException(String message) {
			super(message);
		}

	}

}
