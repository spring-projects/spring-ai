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
import java.util.function.Consumer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.publisher.Flux;

import org.springframework.ai.model.ApiKey;
import org.springframework.ai.model.NoopApiKey;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.openai.api.common.OpenAiApiConstants;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

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

	private final WebClient webClient;

	private final ApiKey apiKey;

	private final ObjectMapper objectMapper = new ObjectMapper();

	private final String imagesPath;

	/**
	 * Create a new OpenAI Image API with the provided base URL.
	 * @param baseUrl the base URL for the OpenAI API.
	 * @param apiKey OpenAI apiKey.
	 * @param headers the http headers to use.
	 * @param imagesPath the images path to use.
	 * @param restClientBuilder the rest client builder to use.
	 * @param webClientBuilder the web client builder to use for streaming.
	 * @param responseErrorHandler the response error handler to use.
	 */
	public OpenAiImageApi(String baseUrl, ApiKey apiKey, MultiValueMap<String, String> headers, String imagesPath,
			RestClient.Builder restClientBuilder, WebClient.Builder webClientBuilder,
			ResponseErrorHandler responseErrorHandler) {

		this.apiKey = apiKey;

		// @formatter:off
		Consumer<HttpHeaders> defaultHeaders = h -> {
			h.setContentType(MediaType.APPLICATION_JSON);
			h.addAll(headers);
		};

		this.restClient = restClientBuilder.clone()
			.baseUrl(baseUrl)
			.defaultHeaders(defaultHeaders)
			.defaultStatusHandler(responseErrorHandler)
			.build();

		this.webClient = webClientBuilder.clone()
			.baseUrl(baseUrl)
			.defaultHeaders(defaultHeaders)
			.build();
		// @formatter:on

		this.imagesPath = imagesPath;
	}

	public ResponseEntity<OpenAiImageResponse> createImage(OpenAiImageRequest openAiImageRequest) {
		Assert.notNull(openAiImageRequest, "Image request cannot be null.");
		Assert.hasLength(openAiImageRequest.prompt(), "Prompt cannot be empty.");

		// @formatter:off
		return this.restClient.post()
			.uri(this.imagesPath)
			.headers(this::addDefaultHeadersIfMissing)
			.body(openAiImageRequest)
			.retrieve()
			.toEntity(OpenAiImageResponse.class);
		// @formatter:on
	}

	/**
	 * Creates a streaming image generation response for the given image request.
	 * @param imageRequest The image generation request. Must have the stream property set
	 * to true.
	 * @return Returns a {@link Flux} stream of image generation events including partial
	 * images (type: "image_generation.partial_image") and the final complete image (type:
	 * "image_generation.completed").
	 */
	public Flux<OpenAiImageStreamEvent> streamImage(OpenAiImageRequest imageRequest) {
		Assert.notNull(imageRequest, "Image request cannot be null.");
		Assert.hasLength(imageRequest.prompt(), "Prompt cannot be empty.");
		Assert.isTrue(imageRequest.stream() != null && imageRequest.stream(),
				"Request must set the stream property to true.");

		// @formatter:off
		return this.webClient.post()
			.uri(this.imagesPath)
			.headers(this::addDefaultHeadersIfMissing)
			.bodyValue(imageRequest)
			.retrieve()
			.bodyToFlux(String.class)
			// Parse the JSON event data - each chunk is a complete JSON object
			.mapNotNull(content -> {
				try {
					// Skip empty lines
					if (content == null || content.trim().isEmpty()) {
						return null;
					}
					return this.objectMapper.readValue(content.trim(), OpenAiImageStreamEvent.class);
				}
				catch (JsonProcessingException ex) {
					throw new RuntimeException("Failed to parse streaming image event: " + content, ex);
				}
			})
			// Complete the stream after receiving the "image_generation.completed" event
			.takeUntil(event -> "image_generation.completed".equals(event.type()));
		// @formatter:on
	}

	private void addDefaultHeadersIfMissing(HttpHeaders headers) {
		if (!headers.containsKey(HttpHeaders.AUTHORIZATION) && !(this.apiKey instanceof NoopApiKey)) {
			headers.setBearerAuth(this.apiKey.getValue());
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * OpenAI Image API model.
	 * <a href="https://platform.openai.com/docs/models">Models</a>
	 */
	public enum ImageModel {

		/**
		 * Multimodal language model that accepts both text and image inputs, and produces
		 * image outputs.
		 */
		GPT_IMAGE_1("gpt-image-1"),

		/**
		 * A cost-efficient version of GPT Image 1. It is a natively multimodal language
		 * model that accepts both text and image inputs, and produces image outputs.
		 */
		GPT_IMAGE_1_MINI("gpt-image-1-mini"),

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
		@JsonProperty("user") String user,
		@JsonProperty("background") String background,
		@JsonProperty("moderation") String moderation,
		@JsonProperty("output_compression") Integer outputCompression,
		@JsonProperty("output_format") String outputFormat,
		@JsonProperty("partial_images") Integer partialImages,
		@JsonProperty("stream") Boolean stream) {

		public OpenAiImageRequest(String prompt, String model) {
			this(prompt, model, null, null, null, null, null, null, null, null, null, null, null, null);
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
	 * Represents a Server-Sent Event (SSE) for streaming image generation. This event is
	 * emitted during streaming image generation when partial images become available
	 * (type: "image_generation.partial_image") or when generation completes (type:
	 * "image_generation.completed").
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record OpenAiImageStreamEvent(@JsonProperty("type") String type, @JsonProperty("b64_json") String b64Json,
			@JsonProperty("created_at") Long createdAt, @JsonProperty("size") String size,
			@JsonProperty("quality") String quality, @JsonProperty("background") String background,
			@JsonProperty("output_format") String outputFormat,
			@JsonProperty("partial_image_index") Integer partialImageIndex, @JsonProperty("usage") Usage usage) {

		/**
		 * Token usage information for image generation (only present in
		 * image_generation.completed event).
		 */
		@JsonInclude(JsonInclude.Include.NON_NULL)
		@JsonIgnoreProperties(ignoreUnknown = true)
		public record Usage(@JsonProperty("total_tokens") Integer totalTokens,
				@JsonProperty("input_tokens") Integer inputTokens, @JsonProperty("output_tokens") Integer outputTokens,
				@JsonProperty("input_tokens_details") InputTokensDetails inputTokensDetails) {

			@JsonInclude(JsonInclude.Include.NON_NULL)
			@JsonIgnoreProperties(ignoreUnknown = true)
			public record InputTokensDetails(@JsonProperty("text_tokens") Integer textTokens,
					@JsonProperty("image_tokens") Integer imageTokens) {
			}
		}
	}

	/**
	 * Builder to construct {@link OpenAiImageApi} instance.
	 */
	public static final class Builder {

		private String baseUrl = OpenAiApiConstants.DEFAULT_BASE_URL;

		private ApiKey apiKey;

		private MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();

		private RestClient.Builder restClientBuilder = RestClient.builder();

		private WebClient.Builder webClientBuilder = WebClient.builder();

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

		public Builder headers(MultiValueMap<String, String> headers) {
			Assert.notNull(headers, "headers cannot be null");
			this.headers = headers;
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

		public OpenAiImageApi build() {
			Assert.notNull(this.apiKey, "apiKey must be set");
			return new OpenAiImageApi(this.baseUrl, this.apiKey, this.headers, this.imagesPath, this.restClientBuilder,
					this.webClientBuilder, this.responseErrorHandler);
		}

	}

}
