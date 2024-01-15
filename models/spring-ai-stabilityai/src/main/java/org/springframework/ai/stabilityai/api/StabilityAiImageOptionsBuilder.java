package org.springframework.ai.stabilityai.api;

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

	public StabilityAiImageOptions build() {
		return options;
	}

}
