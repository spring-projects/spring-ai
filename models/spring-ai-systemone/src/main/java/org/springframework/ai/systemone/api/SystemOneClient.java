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
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.core.retry.Retryable;
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

	private static final SystemOneResponseErrorHandler ERROR_HANDLER = new SystemOneResponseErrorHandler();

	private final SystemOneApi systemOneApi;

	private final String defaultModel;

	private final RetryTemplate retryTemplate;

	/**
	 * Creates a client over an API instance.
	 * @param systemOneApi the transport layer
	 * @param defaultModel the model applied to a request that does not name one
	 * @param retryTemplate how transient failures are retried; see
	 * {@link SystemOneRetryUtils}
	 */
	public SystemOneClient(SystemOneApi systemOneApi, String defaultModel, RetryTemplate retryTemplate) {
		Assert.notNull(systemOneApi, "systemOneApi must not be null");
		Assert.hasText(defaultModel, "defaultModel must not be empty");
		Assert.notNull(retryTemplate, "retryTemplate must not be null");
		this.systemOneApi = systemOneApi;
		this.defaultModel = defaultModel;
		this.retryTemplate = retryTemplate;
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
	 * Runs one call through the retry template, translating transport failures into the
	 * typed exceptions first so that the template can classify them.
	 */
	private <T> T execute(String endpoint, Supplier<T> call) {
		try {
			return this.retryTemplate.execute(new Retryable<T>() {

				@Override
				public T execute() {
					return attempt(endpoint, call);
				}

				@Override
				public String getName() {
					return "System One " + endpoint;
				}

			});
		}
		catch (RetryException ex) {
			// The cause is the last typed failure; earlier ones are its suppressed
			// exceptions.
			Throwable cause = ex.getCause();
			if (cause instanceof RuntimeException runtime) {
				throw runtime;
			}
			if (cause instanceof Error error) {
				throw error;
			}
			throw new IllegalStateException("Unexpected failure of System One " + endpoint, cause);
		}
	}

	private <T> T attempt(String endpoint, Supplier<T> call) {
		try {
			return call.get();
		}
		catch (ResourceAccessException ex) {
			throw toConnectionException(endpoint, ex);
		}
		// The two below are body-decoding failures on a response that did arrive. They
		// used to escape as raw Spring exceptions, bypassing the typed contract and the
		// retry policy. Neither is retryable: the server answered, just not with the
		// JSON this client understands.
		catch (UnknownContentTypeException ex) {
			HttpHeaders headers = ex.getResponseHeaders() != null ? ex.getResponseHeaders() : new HttpHeaders();
			throw new SystemOneApiResponseValidationException(
					"System One %s returned %s where JSON was expected".formatted(endpoint, ex.getContentType()),
					ex.getStatusCode().value(), ex.getResponseBodyAsString(), headers, endpoint, null);
		}
		// An error status that reached here as a Spring exception came through a
		// transport assembled elsewhere, without the error handler. Map it the same way,
		// so the typed hierarchy and the retry policy see the status.
		catch (RestClientResponseException ex) {
			HttpHeaders headers = ex.getResponseHeaders() != null ? ex.getResponseHeaders() : new HttpHeaders();
			throw ERROR_HANDLER.toException(ex.getStatusCode().value(), ex.getResponseBodyAsString(), headers,
					endpoint);
		}
		catch (RestClientException ex) {
			throw new SystemOneException(
					"System One %s response could not be read: %s".formatted(endpoint, ex.getMessage()), ex);
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

		private RetryTemplate retryTemplate = SystemOneRetryUtils.defaultRetryTemplate();

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
		 * Sets the read timeout of the transport the client builds;
		 * {@link SystemOneApiConstants#DEFAULT_TIMEOUT} when none is set. Not applied
		 * when {@link #restClientBuilder(RestClient.Builder)} or
		 * {@link #systemOneApi(SystemOneApi)} supplies the transport, since that
		 * transport then owns its own timeouts.
		 * @param timeout the timeout
		 * @return this builder
		 */
		public Builder timeout(Duration timeout) {
			Assert.notNull(timeout, "timeout must not be null");
			this.timeout = timeout;
			return this;
		}

		/**
		 * Sets how transient failures are retried. The default is
		 * {@link SystemOneRetryUtils#defaultRetryTemplate()}; a policy of your own should
		 * classify with {@link SystemOneRetryUtils#isTransient(Throwable)}.
		 * @param retryTemplate the template
		 * @return this builder
		 */
		public Builder retryTemplate(RetryTemplate retryTemplate) {
			Assert.notNull(retryTemplate, "retryTemplate must not be null");
			this.retryTemplate = retryTemplate;
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
				return new SystemOneClient(this.systemOneApi, model, this.retryTemplate);
			}

			Supplier<String> key = this.apiKey;
			Assert.notNull(key, "No API key configured. Call apiKey(..) before build()");

			String url = (this.baseUrl != null) ? this.baseUrl : SystemOneApiConstants.DEFAULT_BASE_URL;

			// Only a transport built here gets the timeout; a caller's own transport owns
			// its timeouts.
			RestClient.Builder transport = this.restClientBuilder;
			if (transport == null) {
				JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
				requestFactory
					.setReadTimeout((this.timeout != null) ? this.timeout : SystemOneApiConstants.DEFAULT_TIMEOUT);
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

			return new SystemOneClient(api, model, this.retryTemplate);
		}

	}

}
