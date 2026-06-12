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

package org.springframework.ai.google.genai.image;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.image.ImageOptions;
import org.springframework.util.StringUtils;

/**
 * Options for the Image supported by the GenAI SDK
 *
 * @author Olivier Le Quellec
 * @since 1.1.0
 */
public class GoogleGenAiImageOptions implements ImageOptions {

	public static final String DEFAULT_MODEL_NAME = GoogleGenAiImageModelName.GEMINI_2_5_FLASH_IMAGE.getName();

	public static final String DEFAULT_ASPECT_RATIO = "1:1";

	// @formatter:off

	/**
	 * The model to use.
	 */
	private final @Nullable String model;

	/**
	 * Number of images to generate. Must be between 1 and 4.
	 */
	private final @Nullable Integer n;

	/**
	 * Random seed for image generation.
	 */
	private final @Nullable Integer seed;

	/**
	 * Aspect ratio of the generated images. Supported values: 1:1, 3:4, 4:3, 9:16, 16:9.
	 */
	private final @Nullable String aspectRatio;

	/**
	 * Filter level for safety filtering.
	 */
	private final @Nullable SafetyFilterLevel safetyFilterLevel;

	/**
	 * Allows generation of people by the model.
	 */
	private final @Nullable PersonGeneration personGeneration;

	/**
	 * MIME type of the generated image (e.g. {@code image/png}, {@code image/jpeg}).
	 */
	private final @Nullable String outputMimeType;

	/**
	 * Compression quality of the generated image (for {@code image/jpeg} only).
	 */
	private final @Nullable Integer outputCompressionQuality;

	/**
	 * User specified labels to track billing usage.
	 */
	private final @Nullable Map<String, String> labels;

	/**
	 * The size of the largest dimension of the generated image. Supported: {@code 1K},
	 * {@code 2K}.
	 */
	private final @Nullable String imageSize;

	/**
	 * Controls the degree of randomness in token selection. Lower temperatures are good
	 * for prompts that require a less open-ended or creative response, while higher
	 * temperatures can lead to more diverse or creative results.
	 */
	private final @Nullable Float temperature;

	/**
	 * Tokens are selected from the most to least probable until the sum of their
	 * probabilities equals this value.
	 */
	private final @Nullable Float topP;

	/**
	 * For each token selection step, the {@code topK} tokens with the highest
	 * probabilities are sampled.
	 */
	private final @Nullable Float topK;

	/**
	 * Maximum number of tokens that can be generated in the response.
	 */
	private final @Nullable Integer maxOutputTokens;

	protected GoogleGenAiImageOptions(
			@Nullable String model,
			@Nullable Integer n,
			@Nullable String aspectRatio,
			@Nullable Integer seed,
			@Nullable SafetyFilterLevel safetyFilterLevel,
			@Nullable PersonGeneration personGeneration,
			@Nullable String outputMimeType,
			@Nullable Integer outputCompressionQuality,
			@Nullable Map<String, String> labels,
			@Nullable String imageSize,
			@Nullable Float temperature,
			@Nullable Float topP,
			@Nullable Float topK,
			@Nullable Integer maxOutputTokens) {
		this.model = (model != null ? model : DEFAULT_MODEL_NAME);
		this.n = n;
		this.aspectRatio = aspectRatio;
		this.seed = seed;
		this.safetyFilterLevel = safetyFilterLevel;
		this.personGeneration = personGeneration;
		this.outputMimeType = outputMimeType;
		this.outputCompressionQuality = outputCompressionQuality;
		this.labels = (labels == null) ? null : new LinkedHashMap<>(labels);
		this.imageSize = imageSize;
		this.temperature = temperature;
		this.topP = topP;
		this.topK = topK;
		this.maxOutputTokens = maxOutputTokens;
	}

	public static GoogleGenAiImageOptions.Builder builder() {
		return new Builder();
	}


	// @formatter:on

	@Override
	public @Nullable String getModel() {
		return this.model;
	}

	@Override
	public @Nullable Integer getN() {
		return this.n;
	}

	/**
	 * Image width is not directly configurable Use {@link #getAspectRatio()} or
	 * {@link #getImageSize()} instead.
	 * @return always {@code null}
	 */
	@Override
	public @Nullable Integer getWidth() {
		return null;
	}

	/**
	 * Image height is not directly configurable Use {@link #getAspectRatio()} or
	 * {@link #getImageSize()} instead.
	 * @return always {@code null}
	 */
	@Override
	public @Nullable Integer getHeight() {
		return null;
	}

	@Override
	public @Nullable String getResponseFormat() {
		return this.outputMimeType;
	}

	@Override
	public @Nullable String getStyle() {
		return null;
	}

	public @Nullable String getAspectRatio() {
		return this.aspectRatio;
	}

	public @Nullable Integer getSeed() {
		return this.seed;
	}

	public @Nullable SafetyFilterLevel getSafetyFilterLevel() {
		return this.safetyFilterLevel;
	}

	public @Nullable PersonGeneration getPersonGeneration() {
		return this.personGeneration;
	}

	public @Nullable String getOutputMimeType() {
		return this.outputMimeType;
	}

	public @Nullable Integer getOutputCompressionQuality() {
		return this.outputCompressionQuality;
	}

	public @Nullable Map<String, String> getLabels() {
		return this.labels;
	}

	public @Nullable String getImageSize() {
		return this.imageSize;
	}

	public @Nullable Float getTemperature() {
		return this.temperature;
	}

	public @Nullable Float getTopP() {
		return this.topP;
	}

	public @Nullable Float getTopK() {
		return this.topK;
	}

	public @Nullable Integer getMaxOutputTokens() {
		return this.maxOutputTokens;
	}

	/**
	 * Safety filter level for image generation.
	 */
	public enum SafetyFilterLevel {

		BLOCK_LOW_AND_ABOVE, BLOCK_MEDIUM_AND_ABOVE, BLOCK_ONLY_HIGH, BLOCK_NONE, SAFETY_FILTER_LEVEL_UNSPECIFIED

	}

	/**
	 * Person generation policy.
	 */
	public enum PersonGeneration {

		DONT_ALLOW, ALLOW_ADULT, ALLOW_ALL, PERSON_GENERATION_UNSPECIFIED

	}

	public static final class Builder {

		private @Nullable String model;

		private @Nullable Integer n;

		private @Nullable String aspectRatio;

		private @Nullable Integer seed;

		private @Nullable SafetyFilterLevel safetyFilterLevel;

		private @Nullable PersonGeneration personGeneration;

		private @Nullable String outputMimeType;

		private @Nullable Integer outputCompressionQuality;

		private @Nullable Map<String, String> labels;

		private @Nullable String imageSize;

		private @Nullable Float temperature;

		private @Nullable Float topP;

		private @Nullable Float topK;

		private @Nullable Integer maxOutputTokens;

		public Builder() {
		}

		public Builder from(GoogleGenAiImageOptions fromOptions) {
			if (StringUtils.hasText(fromOptions.getModel())) {
				this.model = fromOptions.getModel();
			}
			if (Objects.nonNull(fromOptions.getN())) {
				this.n = fromOptions.getN();
			}
			if (StringUtils.hasText(fromOptions.getAspectRatio())) {
				this.aspectRatio = fromOptions.getAspectRatio();
			}
			if (Objects.nonNull(fromOptions.getSeed())) {
				this.seed = fromOptions.getSeed();
			}
			if (Objects.nonNull(fromOptions.getSafetyFilterLevel())) {
				this.safetyFilterLevel = fromOptions.getSafetyFilterLevel();
			}
			if (Objects.nonNull(fromOptions.getPersonGeneration())) {
				this.personGeneration = fromOptions.getPersonGeneration();
			}
			if (StringUtils.hasText(fromOptions.getOutputMimeType())) {
				this.outputMimeType = fromOptions.getOutputMimeType();
			}
			if (Objects.nonNull(fromOptions.getOutputCompressionQuality())) {
				this.outputCompressionQuality = fromOptions.getOutputCompressionQuality();
			}
			if (Objects.nonNull(fromOptions.getLabels())) {
				this.labels = fromOptions.getLabels();
			}
			if (StringUtils.hasText(fromOptions.getImageSize())) {
				this.imageSize = fromOptions.getImageSize();
			}

			if (Objects.nonNull(fromOptions.getTemperature())) {
				this.temperature = fromOptions.getTemperature();
			}
			if (Objects.nonNull(fromOptions.getTopP())) {
				this.topP = fromOptions.getTopP();
			}
			if (Objects.nonNull(fromOptions.getTopK())) {
				this.topK = fromOptions.getTopK();
			}
			if (Objects.nonNull(fromOptions.getMaxOutputTokens())) {
				this.maxOutputTokens = fromOptions.getMaxOutputTokens();
			}

			return this;
		}

		public Builder model(@Nullable String model) {
			this.model = model;
			return this;
		}

		public Builder model(GoogleGenAiImageModelName model) {
			this.model = model.getName();
			return this;
		}

		public Builder n(@Nullable Integer n) {
			this.n = n;
			return this;
		}

		public Builder aspectRatio(@Nullable String aspectRatio) {
			this.aspectRatio = aspectRatio;
			return this;
		}

		public Builder seed(@Nullable Integer seed) {
			this.seed = seed;
			return this;
		}

		public Builder safetyFilterLevel(@Nullable SafetyFilterLevel safetyFilterLevel) {
			this.safetyFilterLevel = safetyFilterLevel;
			return this;
		}

		public Builder personGeneration(@Nullable PersonGeneration personGeneration) {
			this.personGeneration = personGeneration;
			return this;
		}

		public Builder outputMimeType(@Nullable String outputMimeType) {
			this.outputMimeType = outputMimeType;
			return this;
		}

		public Builder outputCompressionQuality(@Nullable Integer outputCompressionQuality) {
			this.outputCompressionQuality = outputCompressionQuality;
			return this;
		}

		public Builder labels(@Nullable Map<String, String> labels) {
			this.labels = labels;
			return this;
		}

		public Builder imageSize(@Nullable String imageSize) {
			this.imageSize = imageSize;
			return this;
		}

		public Builder temperature(@Nullable Float temperature) {
			this.temperature = temperature;
			return this;
		}

		public Builder topP(@Nullable Float topP) {
			this.topP = topP;
			return this;
		}

		public Builder topK(@Nullable Float topK) {
			this.topK = topK;
			return this;
		}

		public Builder maxOutputTokens(@Nullable Integer maxOutputTokens) {
			this.maxOutputTokens = maxOutputTokens;
			return this;
		}

		public GoogleGenAiImageOptions build() {
			return new GoogleGenAiImageOptions(this.model, this.n, this.aspectRatio, this.seed, this.safetyFilterLevel,
					this.personGeneration, this.outputMimeType, this.outputCompressionQuality, this.labels,
					this.imageSize, this.temperature, this.topP, this.topK, this.maxOutputTokens);
		}

	}

}
