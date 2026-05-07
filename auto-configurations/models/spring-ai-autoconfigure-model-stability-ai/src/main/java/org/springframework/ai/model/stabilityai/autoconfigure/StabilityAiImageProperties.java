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

package org.springframework.ai.model.stabilityai.autoconfigure;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.stabilityai.api.StabilityAiApi;
import org.springframework.ai.stabilityai.api.StabilityAiImageOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;

/**
 * Configuration properties for Stability AI image model.
 *
 * @author Mark Pollack
 * @author Christian Tzolov
 * @author Sebastien Deleuze
 * @since 0.8.0
 */
@ConfigurationProperties(StabilityAiImageProperties.CONFIG_PREFIX)
public class StabilityAiImageProperties extends StabilityAiParentProperties {

	public static final String CONFIG_PREFIX = "spring.ai.stabilityai.image";

	private @Nullable Integer n;

	private String model = StabilityAiApi.DEFAULT_IMAGE_MODEL;

	private @Nullable Integer width;

	private @Nullable Integer height;

	private @Nullable String responseFormat;

	private @Nullable Float cfgScale;

	private @Nullable String clipGuidancePreset;

	private @Nullable String sampler;

	private @Nullable Long seed;

	private @Nullable Integer steps;

	private @Nullable String stylePreset;

	public @Nullable Integer getN() {
		return this.n;
	}

	public void setN(@Nullable Integer n) {
		this.n = n;
	}

	public String getModel() {
		return this.model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public @Nullable Integer getWidth() {
		return this.width;
	}

	public void setWidth(@Nullable Integer width) {
		this.width = width;
	}

	public @Nullable Integer getHeight() {
		return this.height;
	}

	public void setHeight(@Nullable Integer height) {
		this.height = height;
	}

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

	public @Nullable String getStylePreset() {
		return this.stylePreset;
	}

	public void setStylePreset(@Nullable String stylePreset) {
		this.stylePreset = stylePreset;
	}

	public StabilityAiImageOptions toOptions() {
		StabilityAiImageOptions.Builder builder = StabilityAiImageOptions.builder();
		builder.model(this.model);
		if (this.n != null) {
			builder.N(this.n);
		}
		if (this.width != null) {
			builder.width(this.width);
		}
		if (this.height != null) {
			builder.height(this.height);
		}
		if (this.responseFormat != null) {
			builder.responseFormat(this.responseFormat);
		}
		if (this.cfgScale != null) {
			builder.cfgScale(this.cfgScale);
		}
		if (this.clipGuidancePreset != null) {
			builder.clipGuidancePreset(this.clipGuidancePreset);
		}
		if (this.sampler != null) {
			builder.sampler(this.sampler);
		}
		if (this.seed != null) {
			builder.seed(this.seed);
		}
		if (this.steps != null) {
			builder.steps(this.steps);
		}
		if (this.stylePreset != null) {
			builder.stylePreset(this.stylePreset);
		}
		return builder.build();
	}

	private Options options = new Options();

	@DeprecatedConfigurationProperty(replacement = "spring.ai.stabilityai.image")
	@Deprecated(since = "2.0.0", forRemoval = true)
	public Options getOptions() {
		return this.options;
	}

	public void setOptions(Options options) {
		this.options = options;
	}

	public class Options {

		@DeprecatedConfigurationProperty(replacement = "spring.ai.stabilityai.image.n")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Integer getN() {
			return StabilityAiImageProperties.this.getN();
		}

		public void setN(@Nullable Integer n) {
			StabilityAiImageProperties.this.setN(n);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.stabilityai.image.model")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public String getModel() {
			return StabilityAiImageProperties.this.getModel();
		}

		public void setModel(String model) {
			StabilityAiImageProperties.this.setModel(model);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.stabilityai.image.width")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Integer getWidth() {
			return StabilityAiImageProperties.this.getWidth();
		}

		public void setWidth(@Nullable Integer width) {
			StabilityAiImageProperties.this.setWidth(width);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.stabilityai.image.height")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Integer getHeight() {
			return StabilityAiImageProperties.this.getHeight();
		}

		public void setHeight(@Nullable Integer height) {
			StabilityAiImageProperties.this.setHeight(height);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.stabilityai.image.response-format")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable String getResponseFormat() {
			return StabilityAiImageProperties.this.getResponseFormat();
		}

		public void setResponseFormat(@Nullable String responseFormat) {
			StabilityAiImageProperties.this.setResponseFormat(responseFormat);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.stabilityai.image.cfg-scale")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Float getCfgScale() {
			return StabilityAiImageProperties.this.getCfgScale();
		}

		public void setCfgScale(@Nullable Float cfgScale) {
			StabilityAiImageProperties.this.setCfgScale(cfgScale);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.stabilityai.image.clip-guidance-preset")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable String getClipGuidancePreset() {
			return StabilityAiImageProperties.this.getClipGuidancePreset();
		}

		public void setClipGuidancePreset(@Nullable String clipGuidancePreset) {
			StabilityAiImageProperties.this.setClipGuidancePreset(clipGuidancePreset);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.stabilityai.image.sampler")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable String getSampler() {
			return StabilityAiImageProperties.this.getSampler();
		}

		public void setSampler(@Nullable String sampler) {
			StabilityAiImageProperties.this.setSampler(sampler);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.stabilityai.image.seed")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Long getSeed() {
			return StabilityAiImageProperties.this.getSeed();
		}

		public void setSeed(@Nullable Long seed) {
			StabilityAiImageProperties.this.setSeed(seed);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.stabilityai.image.steps")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Integer getSteps() {
			return StabilityAiImageProperties.this.getSteps();
		}

		public void setSteps(@Nullable Integer steps) {
			StabilityAiImageProperties.this.setSteps(steps);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.stabilityai.image.style-preset")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable String getStylePreset() {
			return StabilityAiImageProperties.this.getStylePreset();
		}

		public void setStylePreset(@Nullable String stylePreset) {
			StabilityAiImageProperties.this.setStylePreset(stylePreset);
		}

	}

}
