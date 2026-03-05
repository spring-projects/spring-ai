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

package org.springframework.ai.stabilityai.api;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClient;

/**
 * Represents the StabilityAI API.
 */
public class StabilityAiApi {

	public static final String DEFAULT_IMAGE_MODEL = "stable-diffusion-v1-6";

	public static final String DEFAULT_BASE_URL = "https://api.stability.ai/v1";

	private final RestClient restClient;

	private final String apiKey;

	private final String model;

	/**
	 * Create a new StabilityAI API.
	 * @param apiKey StabilityAI apiKey.
	 */
	public StabilityAiApi(String apiKey) {
		this(apiKey, DEFAULT_IMAGE_MODEL, DEFAULT_BASE_URL, RestClient.builder());
	}

	public StabilityAiApi(String apiKey, String model) {
		this(apiKey, model, DEFAULT_BASE_URL, RestClient.builder());
	}

	public StabilityAiApi(String apiKey, String model, String baseUrl) {
		this(apiKey, model, baseUrl, RestClient.builder());
	}

	/**
	 * Create a new StabilityAI API.
	 * @param apiKey StabilityAI apiKey.
	 * @param model StabilityAI model.
	 * @param baseUrl api base URL.
	 * @param restClientBuilder RestClient builder.
	 */
	public StabilityAiApi(String apiKey, String model, String baseUrl, RestClient.Builder restClientBuilder) {
		Assert.notNull(apiKey, "'apiKey' must not be null");
		Assert.notNull(model, "'model' must not be null");
		Assert.notNull(baseUrl, "'baseUrl' must not be null");
		Assert.notNull(restClientBuilder, "'restClientBuilder' must not be null");
		this.model = model;
		this.apiKey = apiKey;

		Consumer<HttpHeaders> jsonContentHeaders = headers -> {
			headers.setBearerAuth(apiKey);
			headers.setAccept(List.of(MediaType.APPLICATION_JSON)); // base64 in JSON +
			// metadata or return
			// image in bytes.
			headers.setContentType(MediaType.APPLICATION_JSON);
		};

		this.restClient = restClientBuilder.baseUrl(baseUrl)
			.defaultHeaders(jsonContentHeaders)
			.defaultStatusHandler(RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER)
			.build();
	}

	public GenerateImageResponse generateImage(GenerateImageRequest request) {
		Assert.notNull(request, "The request body can not be null.");
		return Objects.requireNonNull(this.restClient.post()
			.uri("/generation/{model}/text-to-image", this.model)
			.body(request)
			.retrieve()
			.body(GenerateImageResponse.class), "received a response without a body");
	}

	// See
	// https://platform.stability.ai/docs/api-reference#tag/SDXL-1.0/operation/textToImage

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record GenerateImageRequest(
			@JsonProperty(value = "text_prompts", required = true) List<TextPrompts> textPrompts,
			@JsonProperty("height") @Nullable Integer height, @JsonProperty("width") @Nullable Integer width,
			@JsonProperty("cfg_scale") @Nullable Float cfgScale,
			@JsonProperty("clip_guidance_preset") @Nullable String clipGuidancePreset,
			@JsonProperty("sampler") @Nullable String sampler, @JsonProperty("samples") @Nullable Integer samples,
			@JsonProperty("seed") @Nullable Long seed, @JsonProperty("steps") @Nullable Integer steps,
			@JsonProperty("style_preset") @Nullable String stylePreset) {

		public static Builder builder() {
			return new Builder();
		}

		@JsonInclude(JsonInclude.Include.NON_NULL)
		public record TextPrompts(@JsonProperty(value = "text", required = true) String text,
				@JsonProperty("weight") @Nullable Float weight) {

		}

		public static final class Builder {

			private @Nullable List<TextPrompts> textPrompts;

			private @Nullable Integer height;

			private @Nullable Integer width;

			private @Nullable Float cfgScale;

			private @Nullable String clipGuidancePreset;

			private @Nullable String sampler;

			private @Nullable Integer samples;

			private @Nullable Long seed;

			private @Nullable Integer steps;

			private @Nullable String stylePreset;

			public Builder() {

			}

			public Builder textPrompts(@Nullable List<TextPrompts> textPrompts) {
				this.textPrompts = textPrompts;
				return this;
			}

			public Builder height(@Nullable Integer height) {
				this.height = height;
				return this;
			}

			public Builder width(@Nullable Integer width) {
				this.width = width;
				return this;
			}

			public Builder cfgScale(@Nullable Float cfgScale) {
				this.cfgScale = cfgScale;
				return this;
			}

			public Builder clipGuidancePreset(@Nullable String clipGuidancePreset) {
				this.clipGuidancePreset = clipGuidancePreset;
				return this;
			}

			public Builder sampler(@Nullable String sampler) {
				this.sampler = sampler;
				return this;
			}

			public Builder samples(@Nullable Integer samples) {
				this.samples = samples;
				return this;
			}

			public Builder seed(@Nullable Long seed) {
				this.seed = seed;
				return this;
			}

			public Builder steps(@Nullable Integer steps) {
				this.steps = steps;
				return this;
			}

			public Builder stylePreset(@Nullable String stylePreset) {
				this.stylePreset = stylePreset;
				return this;
			}

			public GenerateImageRequest build() {
				Assert.state(this.textPrompts != null, "textPrompts must not be null.");
				return new GenerateImageRequest(this.textPrompts, this.height, this.width, this.cfgScale,
						this.clipGuidancePreset, this.sampler, this.samples, this.seed, this.steps, this.stylePreset);
			}

		}

	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record GenerateImageResponse(@JsonProperty("result") String result,
			@JsonProperty(value = "artifacts", required = true) List<Artifacts> artifacts) {

		@JsonInclude(JsonInclude.Include.NON_NULL)
		@JsonIgnoreProperties(ignoreUnknown = true)
		public record Artifacts(@JsonProperty(value = "seed", required = true) long seed,
				@JsonProperty(value = "base64", required = true) String base64,
				@JsonProperty(value = "finishReason", required = true) String finishReason) {

		}

	}

}
