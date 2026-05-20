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
 * Options for Google GenAI Imagen image generation.
 *
 * <p>
 * Maps to the fields exposed by {@code com.google.genai.types.GenerateImagesConfig}.
 *
 * @author Olivier Le Quellec
 * @since 1.1.0
 */
public class GoogleGenAiImageOptions implements ImageOptions {

	/**
	 * Default Imagen model name.
	 */
	public static final String DEFAULT_MODEL_NAME = GoogleGenAiImageModelName.IMAGEN_4_0_GENERATE.getValue();

	/**
	 * Default aspect ratio.
	 */
	public static final String DEFAULT_ASPECT_RATIO = "1:1";

	// @formatter:off

	/**
	 * The Imagen model to use.
	 */
	private @Nullable String model;

	/**
	 * Number of images to generate. Must be between 1 and 4.
	 */
	private @Nullable Integer n;

	/**
	 * The Cloud Storage URI used to store the generated images.
	 */
	private @Nullable String outputGcsUri;

	/**
	 * A description of what to discourage in the generated images.
	 */
	private @Nullable String negativePrompt;

	/**
	 * Aspect ratio of the generated images. Supported values: 1:1, 3:4, 4:3, 9:16, 16:9.
	 */
	private @Nullable String aspectRatio;

	/**
	 * Controls how much the model adheres to the text prompt.
	 */
	private @Nullable Float guidanceScale;

	/**
	 * Random seed for image generation. Not available when {@code addWatermark} is true.
	 */
	private @Nullable Integer seed;

	/**
	 * Filter level for safety filtering.
	 */
	private @Nullable SafetyFilterLevel safetyFilterLevel;

	/**
	 * Allows generation of people by the model.
	 */
	private @Nullable PersonGeneration personGeneration;

	/**
	 * Whether to report the safety scores of each generated image in the response.
	 */
	private @Nullable Boolean includeSafetyAttributes;

	/**
	 * Whether to include the Responsible AI filter reason if the image is filtered out.
	 */
	private @Nullable Boolean includeRaiReason;

	/**
	 * Language of the text in the prompt.
	 */
	private @Nullable String language;

	/**
	 * MIME type of the generated image (e.g. {@code image/png}, {@code image/jpeg}).
	 */
	private @Nullable String outputMimeType;

	/**
	 * Compression quality of the generated image (for {@code image/jpeg} only).
	 */
	private @Nullable Integer outputCompressionQuality;

	/**
	 * Whether to add a watermark to the generated images.
	 */
	private @Nullable Boolean addWatermark;

	/**
	 * User specified labels to track billing usage.
	 */
	private @Nullable Map<String, String> labels;

	/**
	 * The size of the largest dimension of the generated image. Supported: {@code 1K},
	 * {@code 2K}.
	 */
	private @Nullable String imageSize;

	/**
	 * Whether to use the prompt rewriting logic.
	 */
	private @Nullable Boolean enhancePrompt;

	// @formatter:on

	public static Builder builder() {
		return new Builder();
	}

	@Override
	public @Nullable String getModel() {
		return this.model;
	}

	public void setModel(@Nullable String model) {
		this.model = model;
	}

	@Override
	public @Nullable Integer getN() {
		return this.n;
	}

	public void setN(@Nullable Integer n) {
		this.n = n;
	}

	/**
	 * Image width is not directly configurable with Imagen; use {@link #getAspectRatio()}
	 * or {@link #getImageSize()} instead.
	 * @return always {@code null}
	 */
	@Override
	public @Nullable Integer getWidth() {
		return null;
	}

	/**
	 * Image height is not directly configurable with Imagen; use
	 * {@link #getAspectRatio()} or {@link #getImageSize()} instead.
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

	public @Nullable String getOutputGcsUri() {
		return this.outputGcsUri;
	}

	public void setOutputGcsUri(@Nullable String outputGcsUri) {
		this.outputGcsUri = outputGcsUri;
	}

	public @Nullable String getNegativePrompt() {
		return this.negativePrompt;
	}

	public void setNegativePrompt(@Nullable String negativePrompt) {
		this.negativePrompt = negativePrompt;
	}

	public @Nullable String getAspectRatio() {
		return this.aspectRatio;
	}

	public void setAspectRatio(@Nullable String aspectRatio) {
		this.aspectRatio = aspectRatio;
	}

	public @Nullable Float getGuidanceScale() {
		return this.guidanceScale;
	}

	public void setGuidanceScale(@Nullable Float guidanceScale) {
		this.guidanceScale = guidanceScale;
	}

	public @Nullable Integer getSeed() {
		return this.seed;
	}

	public void setSeed(@Nullable Integer seed) {
		this.seed = seed;
	}

	public @Nullable SafetyFilterLevel getSafetyFilterLevel() {
		return this.safetyFilterLevel;
	}

	public void setSafetyFilterLevel(@Nullable SafetyFilterLevel safetyFilterLevel) {
		this.safetyFilterLevel = safetyFilterLevel;
	}

	public @Nullable PersonGeneration getPersonGeneration() {
		return this.personGeneration;
	}

	public void setPersonGeneration(@Nullable PersonGeneration personGeneration) {
		this.personGeneration = personGeneration;
	}

	public @Nullable Boolean getIncludeSafetyAttributes() {
		return this.includeSafetyAttributes;
	}

	public void setIncludeSafetyAttributes(@Nullable Boolean includeSafetyAttributes) {
		this.includeSafetyAttributes = includeSafetyAttributes;
	}

	public @Nullable Boolean getIncludeRaiReason() {
		return this.includeRaiReason;
	}

	public void setIncludeRaiReason(@Nullable Boolean includeRaiReason) {
		this.includeRaiReason = includeRaiReason;
	}

	public @Nullable String getLanguage() {
		return this.language;
	}

	public void setLanguage(@Nullable String language) {
		this.language = language;
	}

	public @Nullable String getOutputMimeType() {
		return this.outputMimeType;
	}

	public void setOutputMimeType(@Nullable String outputMimeType) {
		this.outputMimeType = outputMimeType;
	}

	public @Nullable Integer getOutputCompressionQuality() {
		return this.outputCompressionQuality;
	}

	public void setOutputCompressionQuality(@Nullable Integer outputCompressionQuality) {
		this.outputCompressionQuality = outputCompressionQuality;
	}

	public @Nullable Boolean getAddWatermark() {
		return this.addWatermark;
	}

	public void setAddWatermark(@Nullable Boolean addWatermark) {
		this.addWatermark = addWatermark;
	}

	public @Nullable Map<String, String> getLabels() {
		return this.labels;
	}

	public void setLabels(@Nullable Map<String, String> labels) {
		this.labels = labels;
	}

	public @Nullable String getImageSize() {
		return this.imageSize;
	}

	public void setImageSize(@Nullable String imageSize) {
		this.imageSize = imageSize;
	}

	public @Nullable Boolean getEnhancePrompt() {
		return this.enhancePrompt;
	}

	public void setEnhancePrompt(@Nullable Boolean enhancePrompt) {
		this.enhancePrompt = enhancePrompt;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof GoogleGenAiImageOptions that)) {
			return false;
		}
		return Objects.equals(this.model, that.model) && Objects.equals(this.n, that.n)
				&& Objects.equals(this.outputGcsUri, that.outputGcsUri)
				&& Objects.equals(this.negativePrompt, that.negativePrompt)
				&& Objects.equals(this.aspectRatio, that.aspectRatio)
				&& Objects.equals(this.guidanceScale, that.guidanceScale) && Objects.equals(this.seed, that.seed)
				&& Objects.equals(this.safetyFilterLevel, that.safetyFilterLevel)
				&& Objects.equals(this.personGeneration, that.personGeneration)
				&& Objects.equals(this.includeSafetyAttributes, that.includeSafetyAttributes)
				&& Objects.equals(this.includeRaiReason, that.includeRaiReason)
				&& Objects.equals(this.language, that.language)
				&& Objects.equals(this.outputMimeType, that.outputMimeType)
				&& Objects.equals(this.outputCompressionQuality, that.outputCompressionQuality)
				&& Objects.equals(this.addWatermark, that.addWatermark) && Objects.equals(this.labels, that.labels)
				&& Objects.equals(this.imageSize, that.imageSize)
				&& Objects.equals(this.enhancePrompt, that.enhancePrompt);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.model, this.n, this.outputGcsUri, this.negativePrompt, this.aspectRatio,
				this.guidanceScale, this.seed, this.safetyFilterLevel, this.personGeneration,
				this.includeSafetyAttributes, this.includeRaiReason, this.language, this.outputMimeType,
				this.outputCompressionQuality, this.addWatermark, this.labels, this.imageSize, this.enhancePrompt);
	}

	/**
	 * Safety filter level for image generation. Mirrors the SDK enum.
	 */
	public enum SafetyFilterLevel {

		BLOCK_LOW_AND_ABOVE, BLOCK_MEDIUM_AND_ABOVE, BLOCK_ONLY_HIGH, BLOCK_NONE, SAFETY_FILTER_LEVEL_UNSPECIFIED

	}

	/**
	 * Person generation policy. Mirrors the SDK enum.
	 */
	public enum PersonGeneration {

		DONT_ALLOW, ALLOW_ADULT, ALLOW_ALL, PERSON_GENERATION_UNSPECIFIED

	}

	public static final class Builder {

		private final GoogleGenAiImageOptions options = new GoogleGenAiImageOptions();

		private Builder() {
		}

		public Builder from(GoogleGenAiImageOptions from) {
			if (StringUtils.hasText(from.model)) {
				this.options.model = from.model;
			}
			if (from.n != null) {
				this.options.n = from.n;
			}
			if (StringUtils.hasText(from.outputGcsUri)) {
				this.options.outputGcsUri = from.outputGcsUri;
			}
			if (StringUtils.hasText(from.negativePrompt)) {
				this.options.negativePrompt = from.negativePrompt;
			}
			if (StringUtils.hasText(from.aspectRatio)) {
				this.options.aspectRatio = from.aspectRatio;
			}
			if (from.guidanceScale != null) {
				this.options.guidanceScale = from.guidanceScale;
			}
			if (from.seed != null) {
				this.options.seed = from.seed;
			}
			if (from.safetyFilterLevel != null) {
				this.options.safetyFilterLevel = from.safetyFilterLevel;
			}
			if (from.personGeneration != null) {
				this.options.personGeneration = from.personGeneration;
			}
			if (from.includeSafetyAttributes != null) {
				this.options.includeSafetyAttributes = from.includeSafetyAttributes;
			}
			if (from.includeRaiReason != null) {
				this.options.includeRaiReason = from.includeRaiReason;
			}
			if (StringUtils.hasText(from.language)) {
				this.options.language = from.language;
			}
			if (StringUtils.hasText(from.outputMimeType)) {
				this.options.outputMimeType = from.outputMimeType;
			}
			if (from.outputCompressionQuality != null) {
				this.options.outputCompressionQuality = from.outputCompressionQuality;
			}
			if (from.addWatermark != null) {
				this.options.addWatermark = from.addWatermark;
			}
			if (from.labels != null) {
				this.options.labels = new LinkedHashMap<>(from.labels);
			}
			if (StringUtils.hasText(from.imageSize)) {
				this.options.imageSize = from.imageSize;
			}
			if (from.enhancePrompt != null) {
				this.options.enhancePrompt = from.enhancePrompt;
			}
			return this;
		}

		public Builder model(@Nullable String model) {
			this.options.model = model;
			return this;
		}

		public Builder model(GoogleGenAiImageModelName modelName) {
			this.options.model = modelName.getValue();
			return this;
		}

		public Builder N(@Nullable Integer n) {
			this.options.n = n;
			return this;
		}

		public Builder n(@Nullable Integer n) {
			this.options.n = n;
			return this;
		}

		public Builder outputGcsUri(@Nullable String outputGcsUri) {
			this.options.outputGcsUri = outputGcsUri;
			return this;
		}

		public Builder negativePrompt(@Nullable String negativePrompt) {
			this.options.negativePrompt = negativePrompt;
			return this;
		}

		public Builder aspectRatio(@Nullable String aspectRatio) {
			this.options.aspectRatio = aspectRatio;
			return this;
		}

		public Builder guidanceScale(@Nullable Float guidanceScale) {
			this.options.guidanceScale = guidanceScale;
			return this;
		}

		public Builder seed(@Nullable Integer seed) {
			this.options.seed = seed;
			return this;
		}

		public Builder safetyFilterLevel(@Nullable SafetyFilterLevel safetyFilterLevel) {
			this.options.safetyFilterLevel = safetyFilterLevel;
			return this;
		}

		public Builder personGeneration(@Nullable PersonGeneration personGeneration) {
			this.options.personGeneration = personGeneration;
			return this;
		}

		public Builder includeSafetyAttributes(@Nullable Boolean includeSafetyAttributes) {
			this.options.includeSafetyAttributes = includeSafetyAttributes;
			return this;
		}

		public Builder includeRaiReason(@Nullable Boolean includeRaiReason) {
			this.options.includeRaiReason = includeRaiReason;
			return this;
		}

		public Builder language(@Nullable String language) {
			this.options.language = language;
			return this;
		}

		public Builder outputMimeType(@Nullable String outputMimeType) {
			this.options.outputMimeType = outputMimeType;
			return this;
		}

		public Builder outputCompressionQuality(@Nullable Integer outputCompressionQuality) {
			this.options.outputCompressionQuality = outputCompressionQuality;
			return this;
		}

		public Builder addWatermark(@Nullable Boolean addWatermark) {
			this.options.addWatermark = addWatermark;
			return this;
		}

		public Builder labels(@Nullable Map<String, String> labels) {
			this.options.labels = (labels == null) ? null : new LinkedHashMap<>(labels);
			return this;
		}

		public Builder imageSize(@Nullable String imageSize) {
			this.options.imageSize = imageSize;
			return this;
		}

		public Builder enhancePrompt(@Nullable Boolean enhancePrompt) {
			this.options.enhancePrompt = enhancePrompt;
			return this;
		}

		public GoogleGenAiImageOptions build() {
			return this.options;
		}

	}

}
