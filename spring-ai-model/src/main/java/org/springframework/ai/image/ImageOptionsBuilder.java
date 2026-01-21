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

package org.springframework.ai.image;

import org.jspecify.annotations.Nullable;

public final class ImageOptionsBuilder {

	private final DefaultImageModelOptions options = new DefaultImageModelOptions();

	private ImageOptionsBuilder() {

	}

	public static ImageOptionsBuilder builder() {
		return new ImageOptionsBuilder();
	}

	public ImageOptionsBuilder N(Integer n) {
		this.options.setN(n);
		return this;
	}

	public ImageOptionsBuilder model(String model) {
		this.options.setModel(model);
		return this;
	}

	public ImageOptionsBuilder responseFormat(String responseFormat) {
		this.options.setResponseFormat(responseFormat);
		return this;
	}

	public ImageOptionsBuilder width(Integer width) {
		this.options.setWidth(width);
		return this;
	}

	public ImageOptionsBuilder height(Integer height) {
		this.options.setHeight(height);
		return this;
	}

	public ImageOptionsBuilder style(String style) {
		this.options.setStyle(style);
		return this;
	}

	public ImageOptions build() {
		return this.options;
	}

	private static class DefaultImageModelOptions implements ImageOptions {

		private @Nullable Integer n;

		private @Nullable String model;

		private @Nullable Integer width;

		private @Nullable Integer height;

		private @Nullable String responseFormat;

		private @Nullable String style;

		@Override
		public @Nullable Integer getN() {
			return this.n;
		}

		public void setN(Integer n) {
			this.n = n;
		}

		@Override
		public @Nullable String getModel() {
			return this.model;
		}

		public void setModel(String model) {
			this.model = model;
		}

		@Override
		public @Nullable String getResponseFormat() {
			return this.responseFormat;
		}

		public void setResponseFormat(String responseFormat) {
			this.responseFormat = responseFormat;
		}

		@Override
		public @Nullable Integer getWidth() {
			return this.width;
		}

		public void setWidth(Integer width) {
			this.width = width;
		}

		@Override
		public @Nullable Integer getHeight() {
			return this.height;
		}

		public void setHeight(Integer height) {
			this.height = height;
		}

		@Override
		public @Nullable String getStyle() {
			return this.style;
		}

		public void setStyle(String style) {
			this.style = style;
		}

	}

}
