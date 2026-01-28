package org.springframework.ai.huggingface.text2image;

import org.springframework.ai.image.ImageOptions;

public class HuggingfaceImageOptions implements ImageOptions {

	private Integer numImagesPerPrompt;

	private String model;

	private Integer width;

	private Integer height;

	/**
	 * should be one of 'base64' or 'bytes'
	 */
	private String responseFormat;

	private String style;

	/**
	 * considered only if responseFormat = 'bytes' should be one of 'image/png',
	 * 'image/jpg', 'image/tiff' etc.
	 */
	private String responseMimeType;

	private String negativePrompt;

	private Float sigmaItems;

	private Integer timestepItems;

	private Integer clipSkip;

	private Float guidanceScale;

	private Integer numInferenceSteps;

	@Override
	public Integer getN() {
		return numImagesPerPrompt;
	}

	public void setN(Integer numImagesPerPrompt) {
		this.numImagesPerPrompt = numImagesPerPrompt;
	}

	@Override
	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	@Override
	public Integer getWidth() {
		return width;
	}

	public void setWidth(Integer width) {
		this.width = width;
	}

	@Override
	public Integer getHeight() {
		return height;
	}

	public void setHeight(Integer height) {
		this.height = height;
	}

	@Override
	public String getResponseFormat() {
		return responseFormat;
	}

	public void setResponseFormat(String responseFormat) {
		this.responseFormat = responseFormat;
	}

	@Override
	public String getStyle() {
		return style;
	}

	public void setStyle(String style) {
		this.style = style;
	}

	public String getResponseMimeType() {
		return responseMimeType;
	}

	public void setResponseMimeType(String responseMimeType) {
		this.responseMimeType = responseMimeType;
	}

	public String getNegativePrompt() {
		return negativePrompt;
	}

	public void setNegativePrompt(String negativePrompt) {
		this.negativePrompt = negativePrompt;
	}

	public Float getSigmaItems() {
		return sigmaItems;
	}

	public void setSigmaItems(Float sigmaItems) {
		this.sigmaItems = sigmaItems;
	}

	public Integer getTimestepItems() {
		return timestepItems;
	}

	public void setTimestepItems(Integer timestepItems) {
		this.timestepItems = timestepItems;
	}

	public Integer getClipSkip() {
		return clipSkip;
	}

	public void setClipSkip(Integer clipSkip) {
		this.clipSkip = clipSkip;
	}

	public Float getGuidanceScale() {
		return guidanceScale;
	}

	public void setGuidanceScale(Float guidanceScale) {
		this.guidanceScale = guidanceScale;
	}

	public Integer getNumInferenceSteps() {
		return numInferenceSteps;
	}

	public void setNumInferenceSteps(Integer numInferenceSteps) {
		this.numInferenceSteps = numInferenceSteps;
	}

	public static class Builder {

		private final HuggingfaceImageOptions options = new HuggingfaceImageOptions();

		public Builder builder() {
			return new Builder();
		}

		public Builder withNumImagesPerPrompt(Integer numImagesPerPrompt) {
			options.setN(numImagesPerPrompt);
			return this;
		}

		public Builder withModel(String model) {
			options.setModel(model);
			return this;
		}

		public Builder withResponseFormat(String responseFormat) {
			options.setResponseFormat(responseFormat);
			return this;
		}

		public Builder withWidth(Integer width) {
			options.setWidth(width);
			return this;
		}

		public Builder withHeight(Integer height) {
			options.setHeight(height);
			return this;
		}

		public Builder withNegativePrompt(String negativePrompt) {
			options.setNegativePrompt(negativePrompt);
			return this;
		}

		public Builder withSigmaItems(Float sigmaItems) {
			options.setSigmaItems(sigmaItems);
			return this;
		}

		public Builder withTimestepItems(Integer timestepItems) {
			options.setTimestepItems(timestepItems);
			return this;
		}

		public Builder withClipSkip(Integer clipSkip) {
			options.setClipSkip(clipSkip);
			return this;
		}

		public Builder withGuidanceScale(Float guidanceScale) {
			options.setGuidanceScale(guidanceScale);
			return this;
		}

		public Builder withNumInferenceSteps(Integer numInferenceSteps) {
			options.setNumInferenceSteps(numInferenceSteps);
			return this;
		}

		public HuggingfaceImageOptions build() {
			return options;
		}

	}

}
