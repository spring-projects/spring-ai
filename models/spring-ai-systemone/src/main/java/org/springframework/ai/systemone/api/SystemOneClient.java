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

package org.springframework.ai.systemone.api;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.systemone.api.exception.SystemOneApiConnectionException;
import org.springframework.ai.systemone.api.exception.SystemOneApiException;
import org.springframework.ai.systemone.api.exception.SystemOneApiResponseValidationException;
import org.springframework.ai.systemone.api.exception.SystemOneApiTimeoutException;
import org.springframework.ai.systemone.api.exception.SystemOneException;
import org.springframework.ai.systemone.api.question.Question;
import org.springframework.ai.systemone.api.question.SystemOneRequest;
import org.springframework.ai.systemone.api.response.ListModelsResponse;
import org.springframework.ai.systemone.api.response.ModelMetadata;
import org.springframework.ai.systemone.api.response.SystemOneResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.UnknownContentTypeException;

/**
 * The System One client. Wraps {@link SystemOneApi} with a default model, a retry policy
 * and the typed exceptions, and stitches the {@code x-typesafe-request-id} header onto
 * the response so it travels with the data it describes.
 *
 * <pre>{@code
 * SystemOneClient client = SystemOneClient.builder().apiKey(apiKey).build();
 *
 * SystemOneResponse response = client.systemOne(SystemOneRequest.builder()
 *         .state(Map.of("message", "Help! My payouts have been failing for 3 days."))
 *         .question("is_urgent", Noul.of("Does this convey urgency?"))
 *         .question("department", Choice.builder()
 *                 .instructions("Which team should handle this?")
 *                 .option("billing", "Payments, invoicing, refunds")
 *                 .option("technical", "Bugs, outages, integrations")
 *                 .build())
 *         .build());
 *
 * if (response.noulValue("is_urgent") > 0.8) {
 *     escalate(response.choiceValue("department"));
 * }
 * }</pre>
 *
 * Every question of a call is answered against the same state in parallel, so asking
 * several narrow questions costs little more than asking one, and each answer stays
 * independently thresholdable.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public class SystemOneClient {

	private static final Logger logger = LoggerFactory.getLogger(SystemOneClient.class);

	private static final SystemOneResponseErrorHandler ERROR_HANDLER = new SystemOneResponseErrorHandler();

	private final SystemOneApi systemOneApi;

	private final String defaultModel;

	private final SystemOneRetryPolicy retryPolicy;

	private final @Nullable Duration perAttemptTimeout;

	/**
	 * Creates a client over an API instance whose per-attempt timeout is not known. The
	 * retry budget then bounds only the waits between attempts, not the final attempt.
	 * @param systemOneApi the transport layer
	 * @param defaultModel the model applied to a request that does not name one
	 * @param retryPolicy how transient failures are handled
	 */
	public SystemOneClient(SystemOneApi systemOneApi, String defaultModel, SystemOneRetryPolicy retryPolicy) {
		this(systemOneApi, defaultModel, retryPolicy, null);
	}

	/**
	 * Creates a client over an API instance.
	 * @param systemOneApi the transport layer
	 * @param defaultModel the model applied to a request that does not name one
	 * @param retryPolicy how transient failures are handled
	 * @param perAttemptTimeout the HTTP timeout of a single attempt. It is counted
	 * against {@link SystemOneRetryPolicy#totalTimeout()} before another attempt is
	 * started, so the budget bounds the whole call rather than just the waits;
	 * {@code null} when unknown
	 */
	public SystemOneClient(SystemOneApi systemOneApi, String defaultModel, SystemOneRetryPolicy retryPolicy,
			@Nullable Duration perAttemptTimeout) {
		Assert.notNull(systemOneApi, "systemOneApi must not be null");
		Assert.hasText(defaultModel, "defaultModel must not be empty");
		Assert.notNull(retryPolicy, "retryPolicy must not be null");
		this.systemOneApi = systemOneApi;
		this.defaultModel = defaultModel;
		this.retryPolicy = retryPolicy;
		this.perAttemptTimeout = perAttemptTimeout;
		warnWhenNoRetryCanFit(retryPolicy, perAttemptTimeout);
	}

	/**
	 * A retry is only started when the budget can still absorb a whole attempt, so a
	 * per-attempt timeout at or above the budget means nothing is ever retried, not even
	 * an instant 429. That is a configuration mistake worth saying out loud once, rather
	 * than a debug line per call.
	 */
	private static void warnWhenNoRetryCanFit(SystemOneRetryPolicy retryPolicy, @Nullable Duration perAttemptTimeout) {
		Duration budget = retryPolicy.totalTimeout();
		if (retryPolicy.maxRetries() > 0 && budget != null && perAttemptTimeout != null
				&& perAttemptTimeout.compareTo(budget) >= 0) {
			logger.warn("The per-attempt timeout ({}) is not below the retry budget ({}), so no retry can ever "
					+ "fit in it and failures will not be retried. Raise SystemOneRetryPolicy.totalTimeout, or lower the "
					+ "timeout.", perAttemptTimeout, budget);
		}
	}

	/**
	 * Answers questions about a text state.
	 * @param state the content to evaluate
	 * @param questions the questions, keyed by the names their answers will carry
	 * @return the answers
	 */
	public SystemOneResponse systemOne(String state, Map<String, ? extends Question> questions) {
		return systemOne(JsonContent.of(state), questions);
	}

	/**
	 * Answers questions about a structured state. Naming the parts of the state is the
	 * recommended shape: it lets a question point at a field instead of relying on
	 * position inside a serialized blob.
	 * <p>
	 * The fields and questions are sent in the maps' iteration order, which can change an
	 * answer. Pass ordered maps, or use {@link JsonContent#object(Object...)} for the
	 * state and {@link SystemOneRequest#builder()} for the questions: {@code Map.of}
	 * iterates in an order that changes with every JVM run.
	 * @param state the content to evaluate
	 * @param questions the questions, keyed by the names their answers will carry
	 * @return the answers
	 */
	public SystemOneResponse systemOne(Map<String, ?> state, Map<String, ? extends Question> questions) {
		return systemOne(JsonContent.of(state), questions);
	}

	/**
	 * Answers questions about a sequence, for example the turns of a conversation.
	 * @param state the content to evaluate
	 * @param questions the questions, keyed by the names their answers will carry
	 * @return the answers
	 */
	public SystemOneResponse systemOne(List<?> state, Map<String, ? extends Question> questions) {
		return systemOne(JsonContent.of(state), questions);
	}

	/**
	 * Answers questions about a state.
	 * @param state the content to evaluate
	 * @param questions the questions, keyed by the names their answers will carry
	 * @return the answers
	 */
	public SystemOneResponse systemOne(JsonContent state, Map<String, ? extends Question> questions) {
		return systemOne(SystemOneRequest.builder().state(state).model(this.defaultModel).questions(questions).build());
	}

	/**
	 * Answers a fully assembled request, applying the client's default model when the
	 * request does not name one.
	 * @param request the request
	 * @return the answers, carrying the request id from the response headers
	 */
	public SystemOneResponse systemOne(SystemOneRequest request) {
		Assert.notNull(request, "request must not be null");
		SystemOneRequest effective = StringUtils.hasText(request.model()) ? request
				: request.withModel(this.defaultModel);

		return execute("POST /v1/systemone", () -> {
			ResponseEntity<SystemOneResponse> entity = this.systemOneApi.systemOne(effective);
			SystemOneResponse body = entity.getBody();
			if (body == null) {
				throw new SystemOneApiResponseValidationException("The evaluation response had no body",
						entity.getStatusCode().value(), null, entity.getHeaders(), "POST /v1/systemone", null);
			}
			if (body.answers().isEmpty()) {
				throw new SystemOneApiResponseValidationException("The evaluation response carried no answers",
						entity.getStatusCode().value(), null, entity.getHeaders(), "POST /v1/systemone", "answers");
			}
			return body.withRequestId(entity.getHeaders().getFirst(SystemOneApiException.REQUEST_ID_HEADER));
		});
	}

	/**
	 * Lists the models available to the account.
	 * @return the models, never {@code null}
	 */
	public List<ModelMetadata> listModels() {
		return execute("GET /v1/models", () -> {
			ResponseEntity<ListModelsResponse> entity = this.systemOneApi.listModels();
			ListModelsResponse body = entity.getBody();
			if (body == null) {
				throw new SystemOneApiResponseValidationException("The model listing response had no body",
						entity.getStatusCode().value(), null, entity.getHeaders(), "GET /v1/models", null);
			}
			return body.models();
		});
	}

	/**
	 * @return the model applied to requests that do not name one
	 */
	public String defaultModel() {
		return this.defaultModel;
	}

	/**
	 * The HTTP timeout of a single attempt, as declared to this client. It is what
	 * {@link SystemOneRetryPolicy#totalTimeout()} counts the next attempt against;
	 * {@code null} when nothing was declared and the transport's own default applies.
	 * @return the per-attempt timeout, or {@code null}
	 */
	public @Nullable Duration timeout() {
		return this.perAttemptTimeout;
	}

	/**
	 * @return the retry policy in force
	 */
	public SystemOneRetryPolicy retryPolicy() {
		return this.retryPolicy;
	}

	/**
	 * Runs one call, translating transport failures and repeating the attempt while the
	 * retry policy says a repeat is worthwhile and the budget allows it.
	 */
	private <T> T execute(String endpoint, Supplier<T> call) {
		long startedAt = System.nanoTime();
		int attempt = 0;
		SystemOneException lastFailure;

		while (true) {
			attempt++;
			try {
				return call.get();
			}
			catch (SystemOneException ex) {
				lastFailure = ex;
			}
			catch (ResourceAccessException ex) {
				lastFailure = toConnectionException(endpoint, ex);
			}
			// The two below are body-decoding failures on a response that did arrive.
			// They used to escape as raw Spring exceptions, bypassing the typed contract
			// and the retry policy. Neither is retryable: the server answered, just not
			// with the JSON this client understands.
			catch (UnknownContentTypeException ex) {
				HttpHeaders headers = ex.getResponseHeaders() != null ? ex.getResponseHeaders() : new HttpHeaders();
				lastFailure = new SystemOneApiResponseValidationException(
						"System One %s returned %s where JSON was expected".formatted(endpoint, ex.getContentType()),
						ex.getStatusCode().value(), ex.getResponseBodyAsString(), headers, endpoint, null);
			}
			// An error status that reached here as a Spring exception came through a
			// transport assembled elsewhere, without the error handler. Map it the same
			// way, so the typed hierarchy and the retry policy see the status.
			catch (RestClientResponseException ex) {
				HttpHeaders headers = ex.getResponseHeaders() != null ? ex.getResponseHeaders() : new HttpHeaders();
				lastFailure = ERROR_HANDLER.toException(ex.getStatusCode().value(), ex.getResponseBodyAsString(),
						headers, endpoint);
			}
			catch (RestClientException ex) {
				lastFailure = new SystemOneException(
						"System One %s response could not be read: %s".formatted(endpoint, ex.getMessage()), ex);
			}

			if (attempt > this.retryPolicy.maxRetries() || !this.retryPolicy.isRetryable(lastFailure)) {
				throw lastFailure;
			}

			Duration backoff = this.retryPolicy.backoffFor(attempt, lastFailure);
			if (exceedsBudget(startedAt, backoff)) {
				logger.debug(
						"System One {} giving up after attempt {}: the retry budget cannot absorb another {}ms wait",
						endpoint, attempt, backoff.toMillis());
				throw lastFailure;
			}

			logger.debug("System One {} attempt {} failed ({}), retrying in {}ms", endpoint, attempt,
					lastFailure.getMessage(), backoff.toMillis());
			sleep(backoff, lastFailure);
		}
	}

	private boolean exceedsBudget(long startedAt, Duration backoff) {
		Duration budget = this.retryPolicy.totalTimeout();
		if (budget == null) {
			return false;
		}
		Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);
		// Count the next attempt's own timeout, not only the wait before it. Otherwise
		// the last attempt can overshoot the budget by a whole HTTP timeout, and "30s
		// budget" would really mean "up to 30s plus one more request".
		Duration nextAttempt = this.perAttemptTimeout == null ? Duration.ZERO : this.perAttemptTimeout;
		return elapsed.plus(backoff).plus(nextAttempt).compareTo(budget) >= 0;
	}

	private void sleep(Duration backoff, SystemOneException lastFailure) {
		if (backoff.isZero() || backoff.isNegative()) {
			return;
		}
		try {
			Thread.sleep(backoff.toMillis());
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw lastFailure;
		}
	}

	private SystemOneException toConnectionException(String endpoint, ResourceAccessException ex) {
		Throwable cause = ex.getCause();
		if (cause instanceof SocketTimeoutException || cause instanceof java.net.http.HttpTimeoutException) {
			return new SystemOneApiTimeoutException("System One %s timed out".formatted(endpoint), null, ex);
		}
		return new SystemOneApiConnectionException("System One %s failed without an HTTP response".formatted(endpoint),
				ex);
	}

	/**
	 * @return a new builder
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builder for {@link SystemOneClient}.
	 */
	public static final class Builder {

		private @Nullable Supplier<String> apiKey;

		private @Nullable String baseUrl;

		private @Nullable String defaultModel;

		/** {@code null} until declared; see {@link #timeout(Duration)}. */
		private @Nullable Duration timeout;

		private SystemOneRetryPolicy retryPolicy = SystemOneRetryPolicy.defaults();

		private HttpHeaders headers = new HttpHeaders();

		private RestClient.@Nullable Builder restClientBuilder;

		private @Nullable SystemOneApi systemOneApi;

		private Builder() {
		}

		public Builder apiKey(String apiKey) {
			Assert.hasText(apiKey, "apiKey must not be empty");
			return apiKey(() -> apiKey);
		}

		/**
		 * Supplies the bearer token per request, so a rotated key takes effect without
		 * rebuilding the client.
		 * @param apiKey the token supplier
		 * @return this builder
		 */
		public Builder apiKey(Supplier<String> apiKey) {
			Assert.notNull(apiKey, "apiKey must not be null");
			this.apiKey = apiKey;
			return this;
		}

		public Builder baseUrl(String baseUrl) {
			Assert.hasText(baseUrl, "baseUrl must not be empty");
			this.baseUrl = baseUrl;
			return this;
		}

		public Builder defaultModel(String defaultModel) {
			Assert.hasText(defaultModel, "defaultModel must not be empty");
			this.defaultModel = defaultModel;
			return this;
		}

		/**
		 * Sets the per operation HTTP timeout;
		 * {@link SystemOneApiConstants#DEFAULT_TIMEOUT} when the client builds its own
		 * transport and none is set. Not applied to the transport when
		 * {@link #restClientBuilder(RestClient.Builder)} or
		 * {@link #systemOneApi(SystemOneApi)} supplies it, since that transport then owns
		 * its own timeouts — but it is still counted against
		 * {@link SystemOneRetryPolicy#totalTimeout()} before each retry, so declare the
		 * transport's real timeout here. Left undeclared with a transport of your own,
		 * the timeout is unknown and the budget bounds only the waits between attempts.
		 * @param timeout the timeout
		 * @return this builder
		 */
		public Builder timeout(Duration timeout) {
			Assert.notNull(timeout, "timeout must not be null");
			this.timeout = timeout;
			return this;
		}

		public Builder retryPolicy(SystemOneRetryPolicy retryPolicy) {
			Assert.notNull(retryPolicy, "retryPolicy must not be null");
			this.retryPolicy = retryPolicy;
			return this;
		}

		/**
		 * Adds headers sent on every request.
		 * @param headers the headers
		 * @return this builder
		 */
		public Builder headers(HttpHeaders headers) {
			Assert.notNull(headers, "headers must not be null");
			// Accumulate into a builder-owned copy: a second call adds to the first
			// rather than replacing it, and a caller mutating their own instance
			// afterwards does not reach the client.
			this.headers.addAll(headers);
			return this;
		}

		/**
		 * Supplies the transport. The builder is cloned rather than mutated, and its
		 * request factory is left untouched, so a caller keeps full control of timeouts,
		 * interceptors and observability. This is also the hook a test uses to bind a
		 * {@code MockRestServiceServer}.
		 * @param restClientBuilder the builder to derive the HTTP client from
		 * @return this builder
		 */
		public Builder restClientBuilder(RestClient.Builder restClientBuilder) {
			Assert.notNull(restClientBuilder, "restClientBuilder must not be null");
			this.restClientBuilder = restClientBuilder;
			return this;
		}

		/**
		 * Uses a pre-built transport layer, bypassing every other transport setting.
		 * @param systemOneApi the API instance
		 * @return this builder
		 */
		public Builder systemOneApi(SystemOneApi systemOneApi) {
			Assert.notNull(systemOneApi, "systemOneApi must not be null");
			this.systemOneApi = systemOneApi;
			return this;
		}

		public SystemOneClient build() {
			String model = (this.defaultModel != null) ? this.defaultModel : SystemOneApiConstants.DEFAULT_MODEL;

			if (this.systemOneApi != null) {
				return new SystemOneClient(this.systemOneApi, model, this.retryPolicy, this.timeout);
			}

			Supplier<String> key = this.apiKey;
			Assert.notNull(key, "No API key configured. Call apiKey(..) before build()");

			String url = (this.baseUrl != null) ? this.baseUrl : SystemOneApiConstants.DEFAULT_BASE_URL;

			// Only a transport built here is known to use the default timeout. Assuming
			// it for a caller's own transport would count ten seconds that may not exist
			// against the retry budget, and silently disable retries under a tighter one.
			Duration perAttemptTimeout = this.timeout;
			RestClient.Builder transport = this.restClientBuilder;
			if (transport == null) {
				perAttemptTimeout = (this.timeout != null) ? this.timeout : SystemOneApiConstants.DEFAULT_TIMEOUT;
				JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
				requestFactory.setReadTimeout(perAttemptTimeout);
				transport = RestClient.builder().requestFactory(requestFactory);
			}

			// A copy, so that reusing this builder after build() cannot alter the client.
			HttpHeaders headersCopy = new HttpHeaders();
			headersCopy.addAll(this.headers);

			SystemOneApi api = SystemOneApi.builder()
				.baseUrl(url)
				.apiKey(key)
				.headers(headersCopy)
				.restClientBuilder(transport)
				.build();

			return new SystemOneClient(api, model, this.retryPolicy, perAttemptTimeout);
		}

	}

}
