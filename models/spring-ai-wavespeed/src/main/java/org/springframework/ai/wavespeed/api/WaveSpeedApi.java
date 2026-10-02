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

package org.springframework.ai.wavespeed.api;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClient;

/**
 * Client for the WaveSpeed AI REST API.
 *
 * <p>
 * The WaveSpeed API is asynchronous: submitting a generation request returns a prediction
 * that is polled until it reaches a terminal status. See the
 * <a href="https://wavespeed.ai/docs">WaveSpeed AI documentation</a> for more details.
 *
 * @author Zeyi Cheng
 * @since 2.0.1
 */
public class WaveSpeedApi {

	public static final String DEFAULT_BASE_URL = "https://api.wavespeed.ai";

	public static final String DEFAULT_IMAGE_MODEL = "bytedance/seedream-v5.0-pro";

	public static final Duration DEFAULT_POLL_INTERVAL = Duration.ofSeconds(1);

	public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(600);

	private static final String CLIENT_NAME_HEADER = "X-Client-Name";

	private static final String CLIENT_NAME = "spring-ai-wavespeed";

	private final RestClient restClient;

	private final Duration pollInterval;

	private final Duration timeout;

	/**
	 * Create a new WaveSpeed API client with default base URL, poll interval and timeout.
	 * @param apiKey WaveSpeed API key
	 */
	public WaveSpeedApi(String apiKey) {
		this(apiKey, DEFAULT_BASE_URL, RestClient.builder(), DEFAULT_POLL_INTERVAL, DEFAULT_TIMEOUT);
	}

	/**
	 * Create a new WaveSpeed API client.
	 * @param apiKey WaveSpeed API key
	 * @param baseUrl API base URL
	 * @param restClientBuilder RestClient builder
	 * @param pollInterval interval between two prediction result polls
	 * @param timeout maximum time to wait for a prediction to complete
	 */
	public WaveSpeedApi(String apiKey, String baseUrl, RestClient.Builder restClientBuilder, Duration pollInterval,
			Duration timeout) {
		Assert.hasText(apiKey, "'apiKey' must not be empty");
		Assert.hasText(baseUrl, "'baseUrl' must not be empty");
		Assert.notNull(restClientBuilder, "'restClientBuilder' must not be null");
		Assert.notNull(pollInterval, "'pollInterval' must not be null");
		Assert.notNull(timeout, "'timeout' must not be null");

		this.pollInterval = pollInterval;
		this.timeout = timeout;

		Consumer<HttpHeaders> defaultHeaders = headers -> {
			headers.setBearerAuth(apiKey);
			headers.setContentType(MediaType.APPLICATION_JSON);
			headers.set(CLIENT_NAME_HEADER, CLIENT_NAME);
		};

		this.restClient = restClientBuilder.clone()
			.baseUrl(baseUrl)
			.defaultHeaders(defaultHeaders)
			.defaultStatusHandler(RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER)
			.build();
	}

	/**
	 * Submit an image generation request for the given model and wait for the resulting
	 * prediction to reach a terminal status.
	 * @param model the model identifier, for example {@code bytedance/seedream-v5.0-pro}
	 * @param request the generation request
	 * @return the completed prediction, with output image URLs
	 */
	public Prediction generateImage(String model, GenerateImageRequest request) {
		Prediction prediction = submitGeneration(model, request);
		return waitForPrediction(prediction.id());
	}

	/**
	 * Submit an image generation request without waiting for its completion.
	 * @param model the model identifier
	 * @param request the generation request
	 * @return the submitted prediction, typically with a {@code created} status
	 */
	public Prediction submitGeneration(String model, GenerateImageRequest request) {
		Assert.hasText(model, "'model' must not be empty");
		Assert.notNull(request, "'request' must not be null");
		// WaveSpeed model identifiers contain slashes (for example
		// "bytedance/seedream-v5.0-pro") that must remain path separators, so the
		// model is concatenated instead of being expanded as a URI template variable.
		PredictionResponse response = Objects.requireNonNull(
				this.restClient.post().uri("/api/v3/" + model).body(request).retrieve().body(PredictionResponse.class),
				"received a response without a body");
		return unwrap(response);
	}

	/**
	 * Fetch the current result of a prediction.
	 * @param predictionId the prediction id
	 * @return the prediction with its current status
	 */
	public Prediction getPrediction(String predictionId) {
		Assert.hasText(predictionId, "'predictionId' must not be empty");
		PredictionResponse response = Objects.requireNonNull(this.restClient.get()
			.uri("/api/v3/predictions/{id}/result", predictionId)
			.retrieve()
			.body(PredictionResponse.class), "received a response without a body");
		return unwrap(response);
	}

	/**
	 * Poll a prediction until it reaches a terminal status or the configured timeout
	 * elapses.
	 * @param predictionId the prediction id
	 * @return the completed prediction
	 */
	public Prediction waitForPrediction(String predictionId) {
		long deadline = System.nanoTime() + this.timeout.toNanos();
		while (true) {
			Prediction prediction = getPrediction(predictionId);
			if (Prediction.STATUS_COMPLETED.equals(prediction.status())) {
				return prediction;
			}
			if (prediction.isTerminal()) {
				throw new NonTransientAiException(
						"WaveSpeed prediction %s ended with status '%s'%s".formatted(predictionId, prediction.status(),
								(prediction.error() != null) ? ": " + prediction.error() : ""));
			}
			if (System.nanoTime() > deadline) {
				throw new TransientAiException("WaveSpeed prediction %s still '%s' after %d seconds, giving up polling"
					.formatted(predictionId, prediction.status(), this.timeout.toSeconds()));
			}
			try {
				Thread.sleep(this.pollInterval.toMillis());
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				throw new NonTransientAiException("Interrupted while waiting for WaveSpeed prediction " + predictionId);
			}
		}
	}

	private Prediction unwrap(PredictionResponse response) {
		if (response.code() != null && response.code() != 200) {
			throw new NonTransientAiException("WaveSpeed API returned code %d%s".formatted(response.code(),
					(response.message() != null) ? ": " + response.message() : ""));
		}
		Prediction prediction = response.data();
		if (prediction == null) {
			throw new NonTransientAiException("WaveSpeed API returned a response without prediction data");
		}
		return prediction;
	}

	/**
	 * An image generation request.
	 *
	 * @param prompt the text prompt describing the image to generate
	 * @param size the image size as {@code {width}*{height}}, for example
	 * {@code 1024*1024}
	 * @param seed the random seed, or {@code -1} for a random seed
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record GenerateImageRequest(@JsonProperty(value = "prompt", required = true) String prompt,
			@JsonProperty("size") @Nullable String size, @JsonProperty("seed") @Nullable Integer seed) {

	}

	/**
	 * The envelope wrapping every WaveSpeed API response.
	 *
	 * @param code the API status code, {@code 200} on success
	 * @param message the API status message
	 * @param data the prediction payload
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record PredictionResponse(@JsonProperty("code") @Nullable Integer code,
			@JsonProperty("message") @Nullable String message, @JsonProperty("data") @Nullable Prediction data) {

	}

	/**
	 * A WaveSpeed prediction.
	 *
	 * @param id the prediction id
	 * @param model the model that handles the prediction
	 * @param status the prediction status, one of {@code created}, {@code processing},
	 * {@code completed}, {@code failed}, {@code cancelled} or {@code timeout}
	 * @param outputs the output image URLs, present once the prediction is completed
	 * @param error the error message, present when the prediction failed
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Prediction(@JsonProperty(value = "id", required = true) String id,
			@JsonProperty("model") @Nullable String model,
			@JsonProperty(value = "status", required = true) String status,
			@JsonProperty("outputs") @Nullable List<String> outputs, @JsonProperty("error") @Nullable String error) {

		public static final String STATUS_COMPLETED = "completed";

		private static final Set<String> TERMINAL_STATUSES = Set.of(STATUS_COMPLETED, "failed", "cancelled", "timeout");

		/**
		 * Return whether the prediction reached a terminal status.
		 * @return {@code true} if the prediction is completed, failed, cancelled or timed
		 * out
		 */
		public boolean isTerminal() {
			return TERMINAL_STATUSES.contains(this.status);
		}

	}

}
