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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.Assert;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

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

		ResponseErrorHandler responseErrorHandler = new ResponseErrorHandler() {
			@Override
			public boolean hasError(ClientHttpResponse response) throws IOException {
				return response.getStatusCode().isError();
			}

			@Override
			public void handleError(ClientHttpResponse response) throws IOException {
				if (response.getStatusCode().isError()) {
					throw new RuntimeException(String.format("%s - %s", response.getStatusCode().value(),
							new ObjectMapper().readValue(response.getBody(), ResponseError.class)));
				}
			}
		};

		this.restClient = restClientBuilder.baseUrl(baseUrl)
			.defaultHeaders(jsonContentHeaders)
			.defaultStatusHandler(responseErrorHandler)
			.build();
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ResponseError(@JsonProperty("id") String id, @JsonProperty("name") String name,
			@JsonProperty("message") String message

	) {

	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record GenerateImageRequest(@JsonProperty("text_prompts") List<TextPrompts> textPrompts,
			@JsonProperty("height") Integer height, @JsonProperty("width") Integer width,
			@JsonProperty("cfg_scale") Float cfgScale, @JsonProperty("clip_guidance_preset") String clipGuidancePreset,
			@JsonProperty("sampler") String sampler, @JsonProperty("samples") Integer samples,
			@JsonProperty("seed") Long seed, @JsonProperty("steps") Integer steps,
			@JsonProperty("style_present") String stylePreset) {

		@JsonInclude(JsonInclude.Include.NON_NULL)
		public record TextPrompts(@JsonProperty("text") String text, @JsonProperty("weight") Float weight) {

		}

		public static Builder builder() {
			return new Builder();
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

			public Builder withTextPrompts(List<TextPrompts> textPrompts) {
				this.textPrompts = textPrompts;
				return this;
			}

			public Builder withHeight(Integer height) {
				this.height = height;
				return this;
			}

			public Builder withWidth(Integer width) {
				this.width = width;
				return this;
			}

			public Builder withCfgScale(Float cfgScale) {
				this.cfgScale = cfgScale;
				return this;
			}

			public Builder withClipGuidancePreset(String clipGuidancePreset) {
				this.clipGuidancePreset = clipGuidancePreset;
				return this;
			}

			public Builder withSampler(String sampler) {
				this.sampler = sampler;
				return this;
			}

			public Builder withSamples(Integer samples) {
				this.samples = samples;
				return this;
			}

			public Builder withSeed(Long seed) {
				this.seed = seed;
				return this;
			}

			public Builder withSteps(Integer steps) {
				this.steps = steps;
				return this;
			}

			public Builder withStylePreset(String stylePreset) {
				this.stylePreset = stylePreset;
				return this;
			}

			public GenerateImageRequest build() {
				return new GenerateImageRequest(textPrompts, height, width, cfgScale, clipGuidancePreset, sampler,
						samples, seed, steps, stylePreset);
			}

		}
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record GenerateImageResponse(@JsonProperty("result") String result,
			@JsonProperty("artifacts") List<Artifacts> artifacts) {
		public record Artifacts(@JsonProperty("seed") long seed, @JsonProperty("base64") String base64,
				@JsonProperty("finishReason") String finishReason) {
		}
	}

	public GenerateImageResponse generateImage(GenerateImageRequest request) {
		Assert.notNull(request, "The request body can not be null.");
		return this.restClient.post()
			.uri("/generation/{model}/text-to-image", this.model)
			.body(request)
			.retrieve()
			.body(GenerateImageResponse.class);
	}

}
