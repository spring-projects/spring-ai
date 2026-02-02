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

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.image.ImageOptions;
import org.springframework.ai.stabilityai.StyleEnum;

/**
 * StabilityAiImageOptions is an interface that extends ImageOptions. It provides
 * additional stability AI specific image options.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StabilityAiImageOptions implements ImageOptions {

	/**
	 * The number of images to be generated.
	 *
	 * Defaults to 1 if not explicitly set, indicating a single image will be generated.
	 *
	 * <p>
	 * This method specifies the total number of images to generate. It allows for
	 * controlling the volume of output from a single operation, facilitating batch
	 * generation of images based on the provided settings.
	 * </p>
	 *
	 * <p>
	 * Valid range of values: 1 to 10. This ensures that the request remains within a
	 * manageable scale and aligns with system capabilities or limitations.
	 * </p>
	 *
	 *
	 */
	@JsonProperty("samples")
	private @Nullable Integer n;

	/**
	 * The engine/model to use in Stability AI The model is passed in the URL as a path
	 * parameter
	 *
	 * The default value is stable-diffusion-v1-6
	 */
	private String model = StabilityAiApi.DEFAULT_IMAGE_MODEL;

	/**
	 * Retrieves the width of the image to be generated, in pixels.
	 * <p>
	 * Specifies the desired width for the output image. The value must be a multiple of
	 * 64 and at least 128 pixels. This parameter is adjusted to comply with the
	 * specifications of the selected generation engine, which may have unique
	 * requirements based on its version.
	 * </p>
	 *
	 * <p>
	 * Default value: 512.
	 * </p>
	 *
	 * <p>
	 * Engine-specific dimension validation:
	 * </p>
	 * <ul>
	 * <li>SDXL Beta: Width must be between 128 and 896 pixels, with only one dimension
	 * allowed to exceed 512.</li>
	 * <li>SDXL v0.9 and v1.0: Width must match one of the predefined dimension
	 * pairs.</li>
	 * <li>SD v1.6: Width must be between 320 and 1536 pixels.</li>
	 * </ul>
	 *
	 */
	@JsonProperty("width")
	private @Nullable Integer width;

	/**
	 * Retrieves the height of the image to be generated, in pixels.
	 * <p>
	 * Specifies the desired height for the output image. The value must be a multiple of
	 * 64 and at least 128 pixels. This setting is crucial for ensuring compatibility with
	 * the underlying generation engine, which may impose additional restrictions based on
	 * the engine version.
	 * </p>
	 *
	 * <p>
	 * Default value: 512.
	 * </p>
	 *
	 * <p>
	 * Engine-specific dimension validation:
	 * </p>
	 * <ul>
	 * <li>SDXL Beta: Height must be between 128 and 896 pixels, with only one dimension
	 * allowed to exceed 512.</li>
	 * <li>SDXL v0.9 and v1.0: Height must match one of the predefined dimension
	 * pairs.</li>
	 * <li>SD v1.6: Height must be between 320 and 1536 pixels.</li>
	 * </ul>
	 *
	 */
	@JsonProperty("height")
	private @Nullable Integer height;

	/**
	 * The format in which the generated images are returned. It is sent as part of the
	 * accept header. Must be "application/json" or "image/png"
	 */
	@JsonProperty("response_format")
	private @Nullable String responseFormat;

	/**
	 * The strictness level of the diffusion process adherence to the prompt text.
	 * <p>
	 * This field determines how closely the generated image will match the provided
	 * prompt. Higher values indicate that the image will adhere more closely to the
	 * prompt text, ensuring a closer match to the expected output.
	 * </p>
	 *
	 * <ul>
	 * <li>Range: 0 to 35</li>
	 * <li>Default value: 7</li>
	 * </ul>
	 *
	 */
	@JsonProperty("cfg_scale")
	private @Nullable Float cfgScale;

	/**
	 * The preset for clip guidance.
	 * <p>
	 * This field indicates the preset configuration for clip guidance, affecting the
	 * processing speed and characteristics. The choice of preset can influence the
	 * behavior of the guidance system, potentially impacting performance and output
	 * quality.
	 * </p>
	 *
	 * <p>
	 * Available presets are:
	 * <ul>
	 * <li>{@code FAST_BLUE}: An optimized preset for quicker processing with a focus on
	 * blue tones.</li>
	 * <li>{@code FAST_GREEN}: An optimized preset for quicker processing with a focus on
	 * green tones.</li>
	 * <li>{@code NONE}: No preset is applied, default processing.</li>
	 * <li>{@code SIMPLE}: A basic level of clip guidance for general use.</li>
	 * <li>{@code SLOW}: A slower processing preset for more detailed guidance.</li>
	 * <li>{@code SLOWER}: Further reduces the processing speed for enhanced detail in
	 * guidance.</li>
	 * <li>{@code SLOWEST}: The slowest processing speed, offering the highest level of
	 * detail in clip guidance.</li>
	 * </ul>
	 * </p>
	 *
	 * Defaults to {@code NONE} if no specific preset is configured.
	 *
	 */
	@JsonProperty("clip_guidance_preset")
	private @Nullable String clipGuidancePreset;

	/**
	 * The name of the sampler used for the diffusion process.
	 * <p>
	 * This field specifies the sampler algorithm to be used during the diffusion process.
	 * Selecting a specific sampler can influence the quality and characteristics of the
	 * generated output. If no sampler is explicitly selected, an appropriate sampler will
	 * be automatically chosen based on the context or other settings.
	 * </p>
	 *
	 * <p>
	 * Available samplers are:
	 * <ul>
	 * <li>{@code DDIM}: A deterministic diffusion inverse model for stable and
	 * predictable outputs.</li>
	 * <li>{@code DDPM}: Denoising diffusion probabilistic models for high-quality
	 * generation.</li>
	 * <li>{@code K_DPMPP_2M}: A specific configuration of DPM++ model with medium
	 * settings.</li>
	 * <li>{@code K_DPMPP_2S_ANCESTRAL}: An ancestral sampling variant of the DPM++ model
	 * with small settings.</li>
	 * <li>{@code K_DPM_2}: A variant of the DPM model designed for balanced
	 * performance.</li>
	 * <li>{@code K_DPM_2_ANCESTRAL}: An ancestral sampling variant of the DPM model.</li>
	 * <li>{@code K_EULER}: Utilizes the Euler method for diffusion, offering a different
	 * trade-off between speed and quality.</li>
	 * <li>{@code K_EULER_ANCESTRAL}: An ancestral version of the Euler method for nuanced
	 * sampling control.</li>
	 * <li>{@code K_HEUN}: Employs the Heun's method for a more accurate approximation in
	 * the diffusion process.</li>
	 * <li>{@code K_LMS}: Leverages the linear multistep method for potentially improved
	 * diffusion quality.</li>
	 * </ul>
	 * </p>
	 *
	 * An appropriate sampler is automatically selected if this value is omitted.
	 *
	 */
	@JsonProperty("sampler")
	private @Nullable String sampler;

	/**
	 * The seed used for generating random noise.
	 * <p>
	 * This value serves as the seed for random noise generation, influencing the
	 * randomness and uniqueness of the output. A specific seed ensures reproducibility of
	 * results. Omitting this option or using 0 triggers the selection of a random seed.
	 * </p>
	 *
	 * <p>
	 * Valid range of values: 0 to 4294967295.
	 * </p>
	 *
	 * Default is 0, which indicates that a random seed will be used.
	 */
	@JsonProperty("seed")
	private @Nullable Long seed;

	/**
	 * The number of diffusion steps to run.
	 * <p>
	 * Specifies the total number of steps in the diffusion process, affecting the detail
	 * and quality of the generated output. More steps can lead to higher quality but
	 * require more processing time.
	 * </p>
	 *
	 * <p>
	 * Valid range of values: 10 to 50.
	 * </p>
	 *
	 * Defaults to 30 if not explicitly set.
	 */
	@JsonProperty("steps")
	private @Nullable Integer steps;

	/**
	 * The style preset intended to guide the image model towards a specific artistic
	 * style.
	 * <p>
	 * This string parameter allows for the selection of a predefined style preset,
	 * influencing the aesthetic characteristics of the generated image. The choice of
	 * preset can significantly impact the visual outcome, aligning it with particular
	 * artistic genres or techniques.
	 * </p>
	 *
	 * <p>
	 * Possible values include:
	 * </p>
	 * <ul>
	 * <li>{@code 3d-model}</li>
	 * <li>{@code analog-film}</li>
	 * <li>{@code anime}</li>
	 * <li>{@code cinematic}</li>
	 * <li>{@code comic-book}</li>
	 * <li>{@code digital-art}</li>
	 * <li>{@code enhance}</li>
	 * <li>{@code fantasy-art}</li>
	 * <li>{@code isometric}</li>
	 * <li>{@code line-art}</li>
	 * <li>{@code low-poly}</li>
	 * <li>{@code modeling-compound}</li>
	 * <li>{@code neon-punk}</li>
	 * <li>{@code origami}</li>
	 * <li>{@code photographic}</li>
	 * <li>{@code pixel-art}</li>
	 * <li>{@code tile-texture}</li>
	 * </ul>
	 * <p>
	 * Note: This list of style presets is subject to change.
	 * </p>
	 *
	 */
	@JsonProperty("style_preset")
	private @Nullable String stylePreset;

	public static Builder builder() {
		return new Builder();
	}

	@Override
	public @Nullable Integer getN() {
		return this.n;
	}

	public void setN(@Nullable Integer n) {
		this.n = n;
	}

	@Override
	public String getModel() {
		return this.model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	@Override
	public @Nullable Integer getWidth() {
		return this.width;
	}

	public void setWidth(@Nullable Integer width) {
		this.width = width;
	}

	@Override
	public @Nullable Integer getHeight() {
		return this.height;
	}

	public void setHeight(@Nullable Integer height) {
		this.height = height;
	}

	@Override
	public @Nullable String getResponseFormat() {
		return this.responseFormat;
	}

	public void setResponseFormat(@Nullable String responseFormat) {
		this.responseFormat = responseFormat;
	}

	public @Nullable Float getCfgScale() {
		return this.cfgScale;
	}

	public void setCfgScale(@Nullable Float cfgScale) {
		this.cfgScale = cfgScale;
	}

	public @Nullable String getClipGuidancePreset() {
		return this.clipGuidancePreset;
	}

	public void setClipGuidancePreset(@Nullable String clipGuidancePreset) {
		this.clipGuidancePreset = clipGuidancePreset;
	}

	public @Nullable String getSampler() {
		return this.sampler;
	}

	public void setSampler(@Nullable String sampler) {
		this.sampler = sampler;
	}

	public @Nullable Long getSeed() {
		return this.seed;
	}

	public void setSeed(@Nullable Long seed) {
		this.seed = seed;
	}

	public @Nullable Integer getSteps() {
		return this.steps;
	}

	public void setSteps(@Nullable Integer steps) {
		this.steps = steps;
	}

	@Override
	@JsonIgnore
	public @Nullable String getStyle() {
		return getStylePreset();
	}

	@JsonIgnore
	public void setStyle(@Nullable String style) {
		setStylePreset(style);
	}

	public @Nullable String getStylePreset() {
		return this.stylePreset;
	}

	public void setStylePreset(@Nullable String stylePreset) {
		this.stylePreset = stylePreset;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof StabilityAiImageOptions that)) {
			return false;
		}
		return Objects.equals(this.n, that.n) && Objects.equals(this.model, that.model)
				&& Objects.equals(this.width, that.width) && Objects.equals(this.height, that.height)
				&& Objects.equals(this.responseFormat, that.responseFormat)
				&& Objects.equals(this.cfgScale, that.cfgScale)
				&& Objects.equals(this.clipGuidancePreset, that.clipGuidancePreset)
				&& Objects.equals(this.sampler, that.sampler) && Objects.equals(this.seed, that.seed)
				&& Objects.equals(this.steps, that.steps) && Objects.equals(this.stylePreset, that.stylePreset);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.n, this.model, this.width, this.height, this.responseFormat, this.cfgScale,
				this.clipGuidancePreset, this.sampler, this.seed, this.steps, this.stylePreset);
	}

	@Override
	public String toString() {
		return "StabilityAiImageOptions{" + "n=" + this.n + ", model='" + this.model + '\'' + ", width=" + this.width
				+ ", height=" + this.height + ", responseFormat='" + this.responseFormat + '\'' + ", cfgScale="
				+ this.cfgScale + ", clipGuidancePreset='" + this.clipGuidancePreset + '\'' + ", sampler='"
				+ this.sampler + '\'' + ", seed=" + this.seed + ", steps=" + this.steps + ", stylePreset='"
				+ this.stylePreset + '\'' + '}';
	}

	public static final class Builder {

		private final StabilityAiImageOptions options;

		private Builder() {
			this.options = new StabilityAiImageOptions();
		}

		public Builder N(@Nullable Integer n) {
			this.options.setN(n);
			return this;
		}

		public Builder model(String model) {
			this.options.setModel(model);
			return this;
		}

		public Builder width(@Nullable Integer width) {
			this.options.setWidth(width);
			return this;
		}

		public Builder height(@Nullable Integer height) {
			this.options.setHeight(height);
			return this;
		}

		public Builder responseFormat(@Nullable String responseFormat) {
			this.options.setResponseFormat(responseFormat);
			return this;
		}

		public Builder cfgScale(@Nullable Float cfgScale) {
			this.options.setCfgScale(cfgScale);
			return this;
		}

		public Builder clipGuidancePreset(@Nullable String clipGuidancePreset) {
			this.options.setClipGuidancePreset(clipGuidancePreset);
			return this;
		}

		public Builder sampler(@Nullable String sampler) {
			this.options.setSampler(sampler);
			return this;
		}

		public Builder seed(@Nullable Long seed) {
			this.options.setSeed(seed);
			return this;
		}

		public Builder steps(@Nullable Integer steps) {
			this.options.setSteps(steps);
			return this;
		}

		public Builder stylePreset(@Nullable String stylePreset) {
			this.options.setStylePreset(stylePreset);
			return this;
		}

		public Builder stylePreset(@Nullable StyleEnum styleEnum) {
			this.options.setStylePreset(styleEnum != null ? styleEnum.toString() : null);
			return this;
		}

		public StabilityAiImageOptions build() {
			return this.options;
		}

	}

}
