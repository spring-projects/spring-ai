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

package org.springframework.ai.openai.api;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.ai.model.ApiKey;
import org.springframework.ai.model.NoopApiKey;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.openai.api.common.OpenAiApiConstants;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;

/**
 * OpenAI Image API.
 *
 * @see <a href= "https://platform.openai.com/docs/api-reference/images">Images</a>
 * @author lambochen
 * @author Filip Hrisafov
 */
public class OpenAiImageApi {

	public static final String DEFAULT_IMAGE_MODEL = ImageModel.DALL_E_3.getValue();

	private final RestClient restClient;

	private final String imagesPath;

	/**
	 * Create a new OpenAI Image API with the provided base URL.
	 * @param baseUrl the base URL for the OpenAI API.
	 * @param apiKey OpenAI apiKey.
	 * @param headers the http headers to use.
	 * @param imagesPath the images path to use.
	 * @param restClientBuilder the rest client builder to use.
	 * @param responseErrorHandler the response error handler to use.
	 */
	public OpenAiImageApi(String baseUrl, ApiKey apiKey, HttpHeaders headers, String imagesPath,
			RestClient.Builder restClientBuilder, ResponseErrorHandler responseErrorHandler) {

		// @formatter:off
		this.restClient = restClientBuilder.clone()
			.baseUrl(baseUrl)
			.defaultHeaders(h -> {
				h.setContentType(MediaType.APPLICATION_JSON);
				h.addAll(HttpHeaders.readOnlyHttpHeaders(headers));
			})
			.defaultStatusHandler(responseErrorHandler)
			.defaultRequest(requestHeadersSpec -> {
				if (!(apiKey instanceof NoopApiKey)) {
					requestHeadersSpec.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey.getValue());
				}
			})
			.build();
		// @formatter:on

		this.imagesPath = imagesPath;
	}

	/**
	 * Create a new OpenAI Image API with the provided rest client.
	 * @param restClient the rest client instance to use.
	 * @param imagesPath the images path to use.
	 */
	public OpenAiImageApi(RestClient restClient, String imagesPath) {
		this.restClient = restClient;
		this.imagesPath = imagesPath;
	}

	public ResponseEntity<OpenAiImageResponse> createImage(OpenAiImageRequest openAiImageRequest) {
		Assert.notNull(openAiImageRequest, "Image request cannot be null.");
		Assert.hasLength(openAiImageRequest.prompt(), "Prompt cannot be empty.");

		return this.restClient.post()
			.uri(this.imagesPath)
			.body(openAiImageRequest)
			.retrieve()
			.toEntity(OpenAiImageResponse.class);
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * OpenAI Image API model.
	 * <a href="https://platform.openai.com/docs/models/dall-e">DALL·E</a>
	 */
	public enum ImageModel {

		/**
		 * The latest DALL·E model released in Nov 2023.
		 */
		DALL_E_3("dall-e-3"),

		/**
		 * The previous DALL·E model released in Nov 2022. The 2nd iteration of DALL·E
		 * with more realistic, accurate, and 4x greater resolution images than the
		 * original model.
		 */
		DALL_E_2("dall-e-2");

		private final String value;

		ImageModel(String model) {
			this.value = model;
		}

		public String getValue() {
			return this.value;
		}

	}

	// @formatter:off
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record OpenAiImageRequest(
		@JsonProperty("prompt") String prompt,
		@JsonProperty("model") String model,
		@JsonProperty("n") Integer n,
		@JsonProperty("quality") String quality,
		@JsonProperty("response_format") String responseFormat,
		@JsonProperty("size") String size,
		@JsonProperty("style") String style,
		@JsonProperty("user") String user) {

		public OpenAiImageRequest(String prompt, String model) {
			this(prompt, model, null, null, null, null, null, null);
		}
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record OpenAiImageResponse(
		@JsonProperty("created") Long created,
		@JsonProperty("data") List<Data> data) {
	}
	// @formatter:on

	@JsonInclude(JsonInclude.Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Data(@JsonProperty("url") String url, @JsonProperty("b64_json") String b64Json,
			@JsonProperty("revised_prompt") String revisedPrompt) {

	}

	/**
	 * Builder to construct {@link OpenAiImageApi} instance.
	 */
	public static final class Builder {

		private String baseUrl = OpenAiApiConstants.DEFAULT_BASE_URL;

		private ApiKey apiKey;

		private HttpHeaders headers = new HttpHeaders();

		private RestClient.Builder restClientBuilder = RestClient.builder();

		private ResponseErrorHandler responseErrorHandler = RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER;

		private String imagesPath = "v1/images/generations";

		public Builder baseUrl(String baseUrl) {
			Assert.hasText(baseUrl, "baseUrl cannot be null or empty");
			this.baseUrl = baseUrl;
			return this;
		}

		public Builder imagesPath(String imagesPath) {
			Assert.hasText(imagesPath, "imagesPath cannot be null or empty");
			this.imagesPath = imagesPath;
			return this;
		}

		public Builder apiKey(ApiKey apiKey) {
			Assert.notNull(apiKey, "apiKey cannot be null");
			this.apiKey = apiKey;
			return this;
		}

		public Builder apiKey(String simpleApiKey) {
			Assert.notNull(simpleApiKey, "simpleApiKey cannot be null");
			this.apiKey = new SimpleApiKey(simpleApiKey);
			return this;
		}

		public Builder headers(HttpHeaders headers) {
			Assert.notNull(headers, "headers cannot be null");
			this.headers = headers;
			return this;
		}

		public Builder restClientBuilder(RestClient.Builder restClientBuilder) {
			Assert.notNull(restClientBuilder, "restClientBuilder cannot be null");
			this.restClientBuilder = restClientBuilder;
			return this;
		}

		public Builder responseErrorHandler(ResponseErrorHandler responseErrorHandler) {
			Assert.notNull(responseErrorHandler, "responseErrorHandler cannot be null");
			this.responseErrorHandler = responseErrorHandler;
			return this;
		}

		public OpenAiImageApi build() {
			Assert.notNull(this.apiKey, "apiKey must be set");
			return new OpenAiImageApi(this.baseUrl, this.apiKey, this.headers, this.imagesPath, this.restClientBuilder,
					this.responseErrorHandler);
		}

	}

}
