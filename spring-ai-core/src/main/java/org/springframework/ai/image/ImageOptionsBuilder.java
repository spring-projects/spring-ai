/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.image;

public class ImageOptionsBuilder {

	private static class DefaultImageModelOptions implements ImageOptions {

		private Integer n;

		private String model;

		private Integer width;

		private Integer height;

		private String responseFormat;

		private String style;

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

		@Override
		public String getStyle() {
			return style;
		}

		public void setStyle(String style) {
			this.style = style;
		}

	}

	private final DefaultImageModelOptions options = new DefaultImageModelOptions();

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

	public ImageOptionsBuilder withStyle(String style) {
		options.setStyle(style);
		return this;
	}

	public ImageOptions build() {
		return options;
	}

}
