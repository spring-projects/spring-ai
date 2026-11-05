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

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.systemone.api.question.SystemOneRequest;
import org.springframework.ai.systemone.api.response.ListModelsResponse;
import org.springframework.ai.systemone.api.response.SystemOneResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;

/**
 * Single class implementation of the System One HTTP API.
 *
 * <p>
 * This is the thin transport layer: it serializes a request, issues it and hands back the
 * whole {@link ResponseEntity} so the caller can read response headers. Retries, defaults
 * and the ergonomic surface live in {@code SystemOneClient}.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public class SystemOneApi {

	/** Default path of the evaluation endpoint. */
	public static final String DEFAULT_SYSTEM_ONE_PATH = "/v1/systemone";

	/** Default path of the model listing endpoint. */
	public static final String DEFAULT_MODELS_PATH = "/v1/models";

	private final String systemOnePath;

	private final String modelsPath;

	private final RestClient restClient;

	/**
	 * Creates an API instance that builds its own {@link RestClient}.
	 * @param baseUrl the API root
	 * @param apiKey supplies the bearer token; consulted on every request so a rotated
	 * key takes effect without rebuilding the client
	 * @param headers additional headers to send on every request
	 * @param systemOnePath the path of the evaluation endpoint
	 * @param modelsPath the path of the model listing endpoint
	 * @param restClientBuilder the builder to derive the client from; it is cloned, never
	 * mutated
	 * @param responseErrorHandler the handler mapping error responses onto exceptions
	 */
	public SystemOneApi(String baseUrl, Supplier<String> apiKey, HttpHeaders headers, String systemOnePath,
			String modelsPath, RestClient.Builder restClientBuilder, ResponseErrorHandler responseErrorHandler) {

		Assert.hasText(baseUrl, "baseUrl must not be empty");
		Assert.notNull(apiKey, "apiKey must not be null");
		Assert.notNull(headers, "headers must not be null");
		Assert.hasText(systemOnePath, "systemOnePath must not be empty");
		Assert.hasText(modelsPath, "modelsPath must not be empty");
		Assert.notNull(restClientBuilder, "restClientBuilder must not be null");
		Assert.notNull(responseErrorHandler, "responseErrorHandler must not be null");

		this.systemOnePath = systemOnePath;
		this.modelsPath = modelsPath;

		// Caller headers first; then the ones this client owns are *set*, not added, so a
		// caller-supplied Content-Type or Accept is replaced rather than sent twice.
		Consumer<HttpHeaders> defaultHeaders = h -> {
			h.addAll(HttpHeaders.readOnlyHttpHeaders(headers));
			h.setContentType(MediaType.APPLICATION_JSON);
			h.setAccept(List.of(MediaType.APPLICATION_JSON));
		};

		this.restClient = restClientBuilder.clone()
			.baseUrl(baseUrl)
			.defaultHeaders(defaultHeaders)
			// Likewise set rather than add: a caller-supplied Authorization header must
			// not ride alongside the key. Re-read per request so a rotated key takes
			// effect.
			.defaultRequest(request -> request.headers(h -> h.setBearerAuth(apiKey.get())))
			.defaultStatusHandler(responseErrorHandler)
			.build();
	}

	/**
	 * Creates an API instance over a pre-built {@link RestClient}. Useful in tests and
	 * wherever the HTTP client is assembled elsewhere.
	 * @param systemOnePath the path of the evaluation endpoint
	 * @param modelsPath the path of the model listing endpoint
	 * @param restClient the client to use
	 */
	public SystemOneApi(String systemOnePath, String modelsPath, RestClient restClient) {
		Assert.hasText(systemOnePath, "systemOnePath must not be empty");
		Assert.hasText(modelsPath, "modelsPath must not be empty");
		Assert.notNull(restClient, "restClient must not be null");

		this.systemOnePath = systemOnePath;
		this.modelsPath = modelsPath;
		this.restClient = restClient;
	}

	/**
	 * Answers the request's questions against its state.
	 * @param request the request body
	 * @return the response entity, including the headers that carry the request id
	 */
	public ResponseEntity<SystemOneResponse> systemOne(SystemOneRequest request) {
		Assert.notNull(request, "request must not be null");
		// The model is optional on the request so SystemOneClient can supply its default;
		// nothing without one may reach the wire.
		Assert.hasText(request.model(), "request model must be set; SystemOneClient fills in its default");
		return this.restClient.post()
			.uri(this.systemOnePath)
			.body(request)
			.retrieve()
			.toEntity(SystemOneResponse.class);
	}

	/**
	 * Lists the models available to the account.
	 * @return the response entity
	 */
	public ResponseEntity<ListModelsResponse> listModels() {
		return this.restClient.get().uri(this.modelsPath).retrieve().toEntity(ListModelsResponse.class);
	}

	/**
	 * @return a new builder
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builder for {@link SystemOneApi}.
	 */
	public static final class Builder {

		private String baseUrl = SystemOneApiConstants.DEFAULT_BASE_URL;

		private @Nullable Supplier<String> apiKey;

		private HttpHeaders headers = new HttpHeaders();

		private String systemOnePath = DEFAULT_SYSTEM_ONE_PATH;

		private String modelsPath = DEFAULT_MODELS_PATH;

		private RestClient.Builder restClientBuilder = RestClient.builder();

		private ResponseErrorHandler responseErrorHandler = new SystemOneResponseErrorHandler();

		private Builder() {
		}

		public Builder baseUrl(String baseUrl) {
			Assert.hasText(baseUrl, "baseUrl must not be empty");
			this.baseUrl = baseUrl;
			return this;
		}

		public Builder apiKey(String apiKey) {
			Assert.hasText(apiKey, "apiKey must not be empty");
			return apiKey(() -> apiKey);
		}

		public Builder apiKey(Supplier<String> apiKey) {
			Assert.notNull(apiKey, "apiKey must not be null");
			this.apiKey = apiKey;
			return this;
		}

		public Builder headers(HttpHeaders headers) {
			Assert.notNull(headers, "headers must not be null");
			this.headers = headers;
			return this;
		}

		public Builder systemOnePath(String systemOnePath) {
			Assert.hasText(systemOnePath, "systemOnePath must not be empty");
			this.systemOnePath = systemOnePath;
			return this;
		}

		public Builder modelsPath(String modelsPath) {
			Assert.hasText(modelsPath, "modelsPath must not be empty");
			this.modelsPath = modelsPath;
			return this;
		}

		public Builder restClientBuilder(RestClient.Builder restClientBuilder) {
			Assert.notNull(restClientBuilder, "restClientBuilder must not be null");
			this.restClientBuilder = restClientBuilder;
			return this;
		}

		public Builder responseErrorHandler(ResponseErrorHandler responseErrorHandler) {
			Assert.notNull(responseErrorHandler, "responseErrorHandler must not be null");
			this.responseErrorHandler = responseErrorHandler;
			return this;
		}

		public SystemOneApi build() {
			Assert.notNull(this.apiKey, "apiKey must be set");
			return new SystemOneApi(this.baseUrl, this.apiKey, this.headers, this.systemOnePath, this.modelsPath,
					this.restClientBuilder, this.responseErrorHandler);
		}

	}

}
