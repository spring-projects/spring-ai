package org.springframework.ai.stabilityai.api;

public class StabilityAiImageOptionsImpl implements StabilityAiImageOptions {

	private Integer n;

	private String model;

	private Integer width;

	private Integer height;

	private String responseFormat;

	private Float cfgScale;

	private String clipGuidancePreset;

	private String sampler;

	private Integer samples;

	private Long seed;

	private Integer steps;

	private String stylePreset;

	public StabilityAiImageOptionsImpl() {
	}

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
	public Long getSeed() {
		return this.seed;
	}

	public void setSeed(Long seed) {
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
