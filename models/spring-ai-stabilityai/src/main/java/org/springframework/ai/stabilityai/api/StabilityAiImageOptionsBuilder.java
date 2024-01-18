package org.springframework.ai.stabilityai.api;

public class StabilityAiImageOptionsBuilder {

	private class StabilityAiImageOptionsImpl implements StabilityAiImageOptions {

		private Integer n;

		private String model;

		private Integer width;

		private Integer height;

		private String responseFormat;

		private Float cfgScale;

		private String clipGuidancePreset;

		private String sampler;

		private Integer samples;

		private Integer seed;

		private Integer steps;

		private String stylePreset;

		@Override
		public Integer getN() {
			return this.n;
		}

		public void setN(Integer n) {
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
		public Integer getWidth() {
			return this.width;
		}

		public void setWidth(Integer width) {
			this.width = width;
		}

		@Override
		public Integer getHeight() {
			return this.height;
		}

		public void setHeight(Integer height) {
			this.height = height;
		}

		@Override
		public String getResponseFormat() {
			return this.responseFormat;
		}

		public void setResponseFormat(String responseFormat) {
			this.responseFormat = responseFormat;
		}

		@Override
		public Float getCfgScale() {
			return this.cfgScale;
		}

		public void setCfgScale(Float cfgScale) {
			this.cfgScale = cfgScale;
		}

		@Override
		public String getClipGuidancePreset() {
			return this.clipGuidancePreset;
		}

		public void setClipGuidancePreset(String clipGuidancePreset) {
			this.clipGuidancePreset = clipGuidancePreset;
		}

		@Override
		public String getSampler() {
			return this.sampler;
		}

		public void setSampler(String sampler) {
			this.sampler = sampler;
		}

		@Override
		public Integer getSamples() {
			return this.samples;
		}

		public void setSamples(Integer samples) {
			this.samples = samples;
		}

		@Override
		public Integer getSeed() {
			return this.seed;
		}

		public void setSeed(Integer seed) {
			this.seed = seed;
		}

		@Override
		public Integer getSteps() {
			return this.steps;
		}

		public void setSteps(Integer steps) {
			this.steps = steps;
		}

		@Override
		public String getStylePreset() {
			return this.stylePreset;
		}

		public void setStylePreset(String stylePreset) {
			this.stylePreset = stylePreset;
		}

	}

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

	public StabilityAiImageOptionsBuilder withSeed(Integer seed) {
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
