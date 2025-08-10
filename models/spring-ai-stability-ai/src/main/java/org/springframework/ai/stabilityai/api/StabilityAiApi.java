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
import java.util.function.Consumer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

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
		return this.restClient.post()
			.uri("/generation/{model}/text-to-image", this.model)
			.body(request)
			.retrieve()
			.body(GenerateImageResponse.class);
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record GenerateImageRequest(@JsonProperty("text_prompts") List<TextPrompts> textPrompts,
			@JsonProperty("height") Integer height, @JsonProperty("width") Integer width,
			@JsonProperty("cfg_scale") Float cfgScale, @JsonProperty("clip_guidance_preset") String clipGuidancePreset,
			@JsonProperty("sampler") String sampler, @JsonProperty("samples") Integer samples,
			@JsonProperty("seed") Long seed, @JsonProperty("steps") Integer steps,
			@JsonProperty("style_preset") String stylePreset) {

		public static Builder builder() {
			return new Builder();
		}

		@JsonInclude(JsonInclude.Include.NON_NULL)
		public record TextPrompts(@JsonProperty("text") String text, @JsonProperty("weight") Float weight) {

		}

		public static class Builder {

			List<TextPrompts> textPrompts;

			Integer height;

			Integer width;

			Float cfgScale;

			String clipGuidancePreset;

			String sampler;

			Integer samples;

			Long seed;

			Integer steps;

			String stylePreset;

			public Builder() {

			}

			public Builder textPrompts(List<TextPrompts> textPrompts) {
				this.textPrompts = textPrompts;
				return this;
			}

			public Builder height(Integer height) {
				this.height = height;
				return this;
			}

			public Builder width(Integer width) {
				this.width = width;
				return this;
			}

			public Builder cfgScale(Float cfgScale) {
				this.cfgScale = cfgScale;
				return this;
			}

			public Builder clipGuidancePreset(String clipGuidancePreset) {
				this.clipGuidancePreset = clipGuidancePreset;
				return this;
			}

			public Builder sampler(String sampler) {
				this.sampler = sampler;
				return this;
			}

			public Builder samples(Integer samples) {
				this.samples = samples;
				return this;
			}

			public Builder seed(Long seed) {
				this.seed = seed;
				return this;
			}

			public Builder steps(Integer steps) {
				this.steps = steps;
				return this;
			}

			public Builder stylePreset(String stylePreset) {
				this.stylePreset = stylePreset;
				return this;
			}

			public GenerateImageRequest build() {
				return new GenerateImageRequest(this.textPrompts, this.height, this.width, this.cfgScale,
						this.clipGuidancePreset, this.sampler, this.samples, this.seed, this.steps, this.stylePreset);
			}

		}

	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record GenerateImageResponse(@JsonProperty("result") String result,
			@JsonProperty("artifacts") List<Artifacts> artifacts) {

		@JsonInclude(JsonInclude.Include.NON_NULL)
		@JsonIgnoreProperties(ignoreUnknown = true)
		public record Artifacts(@JsonProperty("seed") long seed, @JsonProperty("base64") String base64,
				@JsonProperty("finishReason") String finishReason) {

		}

	}

}
