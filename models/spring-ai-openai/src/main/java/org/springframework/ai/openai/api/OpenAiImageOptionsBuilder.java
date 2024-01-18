package org.springframework.ai.openai.api;

public class OpenAiImageOptionsBuilder {

	private class OpenAiImageOptionsImpl implements OpenAiImageOptions {

		private Integer n;

		private String model;

		private String quality;

		private String responseFormat;

		private Integer width;

		private Integer height;

		private String style;

		private String user;

		@Override
		public Integer getN() {
			return n;
		}

		public void setN(Integer n) {
			this.n = n;
		}

		@Override
		public String getModel() {
			return model;
		}

		public void setModel(String model) {
			this.model = model;
		}

		@Override
		public String getQuality() {
			return quality;
		}

		public void setQuality(String quality) {
			this.quality = quality;
		}

		@Override
		public String getResponseFormat() {
			return responseFormat;
		}

		public void setResponseFormat(String responseFormat) {
			this.responseFormat = responseFormat;
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
		public String getStyle() {
			return style;
		}

		public void setStyle(String style) {
			this.style = style;
		}

		@Override
		public String getUser() {
			return user;
		}

		public void setUser(String user) {
			this.user = user;
		}

	}

	private final OpenAiImageOptionsImpl options = new OpenAiImageOptionsImpl();

	private OpenAiImageOptionsBuilder() {

	}

	public static OpenAiImageOptionsBuilder builder() {
		return new OpenAiImageOptionsBuilder();
	}

	public OpenAiImageOptionsBuilder withN(Integer n) {
		options.setN(n);
		return this;
	}

	public OpenAiImageOptionsBuilder withModel(String model) {
		options.setModel(model);
		return this;
	}

	public OpenAiImageOptionsBuilder withQuality(String quality) {
		options.setQuality(quality);
		return this;
	}

	public OpenAiImageOptionsBuilder withResponseFormat(String responseFormat) {
		options.setResponseFormat(responseFormat);
		return this;
	}

	public OpenAiImageOptionsBuilder withWidth(Integer width) {
		options.setWidth(width);
		return this;
	}

	public OpenAiImageOptionsBuilder withHeight(Integer height) {
		options.setHeight(height);
		return this;
	}

	public OpenAiImageOptionsBuilder withStyle(String style) {
		options.setStyle(style);
		return this;
	}

	public OpenAiImageOptionsBuilder withUser(String user) {
		options.setUser(user);
		return this;
	}

	public OpenAiImageOptions build() {
		return options;
	}

}
