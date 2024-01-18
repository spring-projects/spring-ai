package org.springframework.ai.image;

public class ImageOptionsBuilder {

	private class ImageModelOptionsImpl implements ImageOptions {

		private Integer n;

		private String model;

		private Integer width;

		private Integer height;

		private String responseFormat;

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

	}

	private final ImageModelOptionsImpl options = new ImageModelOptionsImpl();

	private ImageOptionsBuilder() {

	}

	public static ImageOptionsBuilder builder() {
		return new ImageOptionsBuilder();
	}

	public ImageOptionsBuilder withN(Integer n) {
		options.setN(n);
		return this;
	}

	public ImageOptionsBuilder withModel(String model) {
		options.setModel(model);
		return this;
	}

	public ImageOptionsBuilder withResponseFormat(String responseFormat) {
		options.setResponseFormat(responseFormat);
		return this;
	}

	public ImageOptionsBuilder withWidth(Integer width) {
		options.setWidth(width);
		return this;
	}

	public ImageOptionsBuilder withHeight(Integer height) {
		options.setHeight(height);
		return this;
	}

	public ImageOptions build() {
		return options;
	}

}
