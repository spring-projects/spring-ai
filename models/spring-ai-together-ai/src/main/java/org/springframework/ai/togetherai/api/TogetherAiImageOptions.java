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

import org.jspecify.annotations.Nullable;

import org.springframework.ai.image.ImageOptions;

/**
 * Together AI image generation options.
 *
 * @since 2.0.1
 * @author Maksym Uimanov
 */
public class TogetherAiImageOptions implements ImageOptions {

	@Nullable private final String model;

	@Nullable private final Integer steps;

	@Nullable private final String imageUrl;

	@Nullable private final Long seed;

	@Nullable private final Integer n;

	@Nullable private final Integer height;

	@Nullable private final Integer width;

	@Nullable private final String negativePrompt;

	@Nullable private final String responseFormat;

	@Nullable private final Float guidanceScale;

	@Nullable private final String outputFormat;

	@Nullable private final List<ImageLora> imageLoras;

	@Nullable private final List<String> referenceImages;

	@Nullable private final Boolean disableSafetyChecker;

	public TogetherAiImageOptions(@Nullable String model, @Nullable Integer steps, @Nullable String imageUrl,
			@Nullable Long seed, @Nullable Integer n, @Nullable Integer height, @Nullable Integer width,
			@Nullable String negativePrompt, @Nullable String responseFormat, @Nullable Float guidanceScale,
			@Nullable String outputFormat, @Nullable List<ImageLora> imageLoras, @Nullable List<String> referenceImages,
			@Nullable Boolean disableSafetyChecker) {
		this.model = model;
		this.steps = steps;
		this.imageUrl = imageUrl;
		this.seed = seed;
		this.n = n;
		this.height = height;
		this.width = width;
		this.negativePrompt = negativePrompt;
		this.responseFormat = responseFormat;
		this.guidanceScale = guidanceScale;
		this.outputFormat = outputFormat;
		this.imageLoras = imageLoras;
		this.referenceImages = referenceImages;
		this.disableSafetyChecker = disableSafetyChecker;
	}

	@Override
	@Nullable public String getModel() {
		return this.model;
	}

	@Nullable public Integer getSteps() {
		return this.steps;
	}

	@Nullable public String getImageUrl() {
		return this.imageUrl;
	}

	@Nullable public Long getSeed() {
		return this.seed;
	}

	@Override
	@Nullable public Integer getN() {
		return this.n;
	}

	@Override
	@Nullable public Integer getHeight() {
		return this.height;
	}

	@Override
	@Nullable public Integer getWidth() {
		return this.width;
	}

	@Nullable public String getNegativePrompt() {
		return this.negativePrompt;
	}

	@Override
	@Nullable public String getResponseFormat() {
		return this.responseFormat;
	}

	@Override
	@Nullable public String getStyle() {
		return null; // Together AI does not support style
	}

	@Nullable public Float getGuidanceScale() {
		return this.guidanceScale;
	}

	@Nullable public String getOutputFormat() {
		return this.outputFormat;
	}

	@Nullable public List<ImageLora> getImageLoras() {
		return this.imageLoras;
	}

	@Nullable public List<String> getReferenceImages() {
		return this.referenceImages;
	}

	@Nullable public Boolean getDisableSafetyChecker() {
		return this.disableSafetyChecker;
	}

	public static Builder builder() {
		return new Builder();
	}

	public record ImageLora(String path, Float scale) {
	}

	public enum OutputFormat {

		JPEG("jpeg"), PNG("png");

		private final String value;

		@Nullable public static OutputFormat from(@Nullable String value) {
			if (value == null) {
				return null;
			}
			for (OutputFormat type : values()) {
				if (type.value.equals(value)) {
					return type;
				}
			}
			throw new IllegalArgumentException("Unknown output format: " + value);
		}

		OutputFormat(String value) {
			this.value = value;
		}

		public String getValue() {
			return this.value;
		}

	}

	public enum ResponseFormat {

		BASE64("base64"), URL("url");

		private final String value;

		@Nullable public static ResponseFormat from(@Nullable String value) {
			if (value == null) {
				return null;
			}
			for (ResponseFormat type : values()) {
				if (type.value.equals(value)) {
					return type;
				}
			}
			throw new IllegalArgumentException("Unknown response format: " + value);
		}

		ResponseFormat(String value) {
			this.value = value;
		}

		public String getValue() {
			return this.value;
		}

	}

	public static final class Builder {

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

		public Builder responseFormat(@Nullable ResponseFormat responseFormat) {
			this.responseFormat = responseFormat != null ? responseFormat.getValue() : null;
			return this;
		}

		public Builder guidanceScale(@Nullable Float guidanceScale) {
			this.guidanceScale = guidanceScale;
			return this;
		}

		public Builder outputFormat(@Nullable OutputFormat outputFormat) {
			this.outputFormat = outputFormat != null ? outputFormat.getValue() : null;
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

		public TogetherAiImageOptions build() {
			return new TogetherAiImageOptions(this.model, this.steps, this.imageUrl, this.seed, this.n, this.height,
					this.width, this.negativePrompt, this.responseFormat, this.guidanceScale, this.outputFormat,
					this.imageLoras, this.referenceImages, this.disableSafetyChecker);
		}

	}

}
