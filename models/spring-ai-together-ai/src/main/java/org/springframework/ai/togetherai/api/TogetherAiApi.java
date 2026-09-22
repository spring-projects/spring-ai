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

package org.springframework.ai.togetherai.api;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import com.fasterxml.jackson.annotation.JsonCreator;
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
 * Together AI API client.
 *
 * @since 2.0.1
 * @author Maksym Uimanov
 */
public class TogetherAiApi {

	public static final String DEFAULT_BASE_URL = "https://api.together.ai";

	public static final String IMAGE_GENERATION_ENDPOINT = "/v1/images/generations";

	private final RestClient restClient;

	public TogetherAiApi(String apiKey) {
		this(apiKey, DEFAULT_BASE_URL, RestClient.builder());
	}

	public TogetherAiApi(String apiKey, String baseUrl) {
		this(apiKey, baseUrl, RestClient.builder());
	}

	public TogetherAiApi(String apiKey, String baseUrl, RestClient.Builder restClientBuilder) {
		Assert.notNull(apiKey, "'apiKey' must not be null");
		Assert.notNull(baseUrl, "'baseUrl' must not be null");
		Assert.notNull(restClientBuilder, "'restClientBuilder' must not be null");

		Consumer<HttpHeaders> jsonContentHeaders = headers -> {
			headers.setBearerAuth(apiKey);
			headers.setAccept(List.of(MediaType.APPLICATION_JSON));
			headers.setContentType(MediaType.APPLICATION_JSON);
		};

		this.restClient = restClientBuilder.clone()
			.baseUrl(baseUrl)
			.defaultHeaders(jsonContentHeaders)
			.defaultStatusHandler(RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER)
			.build();
	}

	public GenerateImageResponse generateImage(GenerateImageRequest request) {
		Assert.notNull(request, "The request body can not be null.");
		return Objects.requireNonNull(this.restClient.post()
			.uri(IMAGE_GENERATION_ENDPOINT)
			.body(request)
			.retrieve()
			.body(GenerateImageResponse.class), "received a response without a body");
	}

	// See: https://docs.together.ai/reference/post-images-generations
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record GenerateImageRequest(@JsonProperty(value = "prompt", required = true) String prompt,
			@JsonProperty(value = "model", required = true) String model,
			@Nullable @JsonProperty("steps") Integer steps, @Nullable @JsonProperty("image_url") String imageUrl,
			@Nullable @JsonProperty("seed") Long seed, @Nullable @JsonProperty("n") Integer n,
			@Nullable @JsonProperty("height") Integer height, @Nullable @JsonProperty("width") Integer width,
			@Nullable @JsonProperty("negative_prompt") String negativePrompt,
			@Nullable @JsonProperty("response_format") String responseFormat,
			@Nullable @JsonProperty("guidance_scale") Float guidanceScale,
			@Nullable @JsonProperty("output_format") String outputFormat,
			@Nullable @JsonProperty("image_loras") List<ImageLora> imageLoras,
			@Nullable @JsonProperty("reference_images") List<String> referenceImages,
			@Nullable @JsonProperty("disable_safety_checker") Boolean disableSafetyChecker) {

		public static Builder builder() {
			return new Builder();
		}

		public record ImageLora(@JsonProperty(value = "path", required = true) String path,
				@JsonProperty(value = "scale", required = true) Float scale) {
			public static ImageLora from(TogetherAiImageOptions.ImageLora imageLora) {
				return new ImageLora(imageLora.path(), imageLora.scale());
			}
		}

		public static final class Builder {

			@Nullable private String prompt;

			@Nullable private String model;

			@Nullable private Integer steps;

			@Nullable private String imageUrl;

			@Nullable private Long seed;

			@Nullable private Integer n;

			@Nullable private Integer height;

			@Nullable private Integer width;

			@Nullable private String negativePrompt;

			@Nullable private String responseFormat;

			@Nullable private Float guidanceScale;

			@Nullable private String outputFormat;

			@Nullable private List<ImageLora> imageLoras;

			@Nullable private List<String> referenceImages;

			@Nullable private Boolean disableSafetyChecker;

			private Builder() {
			}

			public Builder prompt(@Nullable String prompt) {
				this.prompt = prompt;
				return this;
			}

			public Builder model(@Nullable String model) {
				this.model = model;
				return this;
			}

			public Builder steps(@Nullable Integer steps) {
				this.steps = steps;
				return this;
			}

			public Builder imageUrl(@Nullable String imageUrl) {
				this.imageUrl = imageUrl;
				return this;
			}

			public Builder seed(@Nullable Long seed) {
				this.seed = seed;
				return this;
			}

			public Builder n(@Nullable Integer n) {
				this.n = n;
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

			public Builder negativePrompt(@Nullable String negativePrompt) {
				this.negativePrompt = negativePrompt;
				return this;
			}

			public Builder responseFormat(@Nullable String responseFormat) {
				this.responseFormat = responseFormat;
				return this;
			}

			public Builder guidanceScale(@Nullable Float guidanceScale) {
				this.guidanceScale = guidanceScale;
				return this;
			}

			public Builder outputFormat(@Nullable String outputFormat) {
				this.outputFormat = outputFormat;
				return this;
			}

			public Builder imageLoras(@Nullable List<ImageLora> imageLoras) {
				this.imageLoras = imageLoras;
				return this;
			}

			public Builder referenceImages(@Nullable List<String> referenceImages) {
				this.referenceImages = referenceImages;
				return this;
			}

			public Builder disableSafetyChecker(@Nullable Boolean disableSafetyChecker) {
				this.disableSafetyChecker = disableSafetyChecker;
				return this;
			}

			public GenerateImageRequest build() {
				Assert.state(Objects.nonNull(this.prompt), "prompt must not be null.");
				Assert.state(Objects.nonNull(this.model), "model must not be null.");
				return new GenerateImageRequest(this.prompt, this.model, this.steps, this.imageUrl, this.seed, this.n,
						this.height, this.width, this.negativePrompt, this.responseFormat, this.guidanceScale,
						this.outputFormat, this.imageLoras, this.referenceImages, this.disableSafetyChecker);
			}

		}
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record GenerateImageResponse(@JsonProperty(value = "id", required = true) String id,
			@JsonProperty(value = "model", required = true) String model,
			@JsonProperty(value = "object", required = true) String object,
			@JsonProperty(value = "data", required = true) List<Image> data) {
		@JsonInclude(JsonInclude.Include.NON_NULL)
		@JsonIgnoreProperties(ignoreUnknown = true)
		public record Image(@JsonProperty(value = "index", required = true) Integer index,
				@Nullable @JsonProperty("b64_json") String b64Json, @Nullable @JsonProperty("url") String url,
				@Nullable @JsonProperty("type") ImageType type) {
		}

		public enum ImageType {

			BASE64_JSON("b64_json"), URL("url");

			private final String value;

			@JsonCreator
			public static ImageType from(String value) {
				for (ImageType type : values()) {
					if (type.value.equals(value)) {
						return type;
					}
				}
				throw new IllegalArgumentException("Unknown image type: " + value);
			}

			ImageType(String value) {
				this.value = value;
			}

			public String getValue() {
				return this.value;
			}

		}
	}

}
