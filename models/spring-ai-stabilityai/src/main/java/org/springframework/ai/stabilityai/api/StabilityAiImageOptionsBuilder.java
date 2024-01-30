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

import org.springframework.ai.stabilityai.StyleEnum;

/**
 * The StabilityAiImageOptionsBuilder class provides a convenient way to construct an
 * instance of StabilityAiImageOptions. by allowing you to chain multiple method calls to
 * set the desired options.
 */
public class StabilityAiImageOptionsBuilder {

	private StabilityAiImageOptionsImpl options;

	private StabilityAiImageOptionsBuilder() {
		options = new StabilityAiImageOptionsImpl();
	}

	public static StabilityAiImageOptionsBuilder builder() {
		return new StabilityAiImageOptionsBuilder();
	}

	public StabilityAiImageOptionsBuilder withN(Integer n) {
		options.setN(n);
		return this;
	}

	public StabilityAiImageOptionsBuilder withModel(String model) {
		options.setModel(model);
		return this;
	}

	public StabilityAiImageOptionsBuilder withWidth(Integer width) {
		options.setWidth(width);
		return this;
	}

	public StabilityAiImageOptionsBuilder withHeight(Integer height) {
		options.setHeight(height);
		return this;
	}

	public StabilityAiImageOptionsBuilder withResponseFormat(String responseFormat) {
		options.setResponseFormat(responseFormat);
		return this;
	}

	public StabilityAiImageOptionsBuilder withCfgScale(Float cfgScale) {
		options.setCfgScale(cfgScale);
		return this;
	}

	public StabilityAiImageOptionsBuilder withClipGuidancePreset(String clipGuidancePreset) {
		options.setClipGuidancePreset(clipGuidancePreset);
		return this;
	}

	public StabilityAiImageOptionsBuilder withSampler(String sampler) {
		options.setSampler(sampler);
		return this;
	}

	public StabilityAiImageOptionsBuilder withSeed(Long seed) {
		options.setSeed(seed);
		return this;
	}

	public StabilityAiImageOptionsBuilder withSteps(Integer steps) {
		options.setSteps(steps);
		return this;
	}

	public StabilityAiImageOptionsBuilder withSamples(Integer samples) {
		options.setSamples(samples);
		return this;
	}

	public StabilityAiImageOptionsBuilder withStylePreset(String stylePreset) {
		options.setStylePreset(stylePreset);
		return this;
	}

	public StabilityAiImageOptionsBuilder withStylePreset(StyleEnum styleEnum) {
		options.setStylePreset(styleEnum.toString());
		return this;
	}

	public StabilityAiImageOptions build() {
		return options;
	}

}
