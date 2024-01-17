package org.springframework.ai.image;

public class ImageOptionsBuilder {

	private class ImageModelOptionsImpl implements ImageModelOptions {

		private Integer n;

		private String model;

		private String quality;

		private String responseFormat;

		private String size;

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
		public String getSize() {
			return size;
		}

		public void setSize(String size) {
			this.size = size;
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

	public ImageOptionsBuilder withQuality(String quality) {
		options.setQuality(quality);
		return this;
	}

	public ImageOptionsBuilder withResponseFormat(String responseFormat) {
		options.setResponseFormat(responseFormat);
		return this;
	}

	public ImageOptionsBuilder withSize(String size) {
		options.setSize(size);
		return this;
	}

	public ImageOptionsBuilder withStyle(String style) {
		options.setStyle(style);
		return this;
	}

	public ImageOptionsBuilder withUser(String user) {
		options.setUser(user);
		return this;
	}

	public ImageModelOptions build() {
		return options;
	}

}
