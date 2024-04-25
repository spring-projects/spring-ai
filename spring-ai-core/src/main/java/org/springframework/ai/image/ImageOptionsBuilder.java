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

/**
 * Builder for {@link ImageOptions}. This builder creates option objects required for
 * image generation.
 *
 * @author youngmon
 * @since 0.8.1
 */
public class ImageOptionsBuilder {

	private Integer n;

	private String model;

	private Integer width;

	private Integer height;

	private String responseFormat;

	private ImageOptionsBuilder() {
	}

	/**
	 * Creates a new {@link ImageOptionsBuilder} instance.
	 * @return A new instance of ImageOptionsBuilder.
	 */
	public static ImageOptionsBuilder builder() {
		return new ImageOptionsBuilder();
	}

	/**
	 * Initializes a new {@link ImageOptionsBuilder} with settings from an existing
	 * {@link ImageOptions} object.
	 * @param options The ImageOptions object whose settings are to be used.
	 * @return A ImageOptionsBuilder instance initialized with the provided ImageOptions
	 * settings.
	 */
	public static ImageOptionsBuilder builder(final ImageOptions options) {
		return builder().withN(options.getN())
			.withModel(options.getModel())
			.withHeight(options.getHeight())
			.withWidth(options.getWidth())
			.withResponseFormat(options.getResponseFormat());
	}

	public ImageOptions build() {
		return new ImageOptionsImpl(this.n, this.model, this.width, this.height, this.responseFormat);
	}

	public ImageOptionsBuilder withN(final Integer n) {
		this.n = n;
		return this;
	}

	public ImageOptionsBuilder withModel(final String model) {
		this.model = model;
		return this;
	}

	public ImageOptionsBuilder withResponseFormat(final String responseFormat) {
		this.responseFormat = responseFormat;
		return this;
	}

	public ImageOptionsBuilder withWidth(final Integer width) {
		this.width = width;
		return this;
	}

	public ImageOptionsBuilder withHeight(final Integer height) {
		this.height = height;
		return this;
	}

	/**
	 * Created only by ImageOptionsBuilder for controlled setup. Hidden implementation,
	 * accessed via ImageOptions interface. Promotes modularity and easy use.
	 */
	private static class ImageOptionsImpl implements ImageOptions {

		private final Integer n;

		private final String model;

		private final Integer width;

		private final Integer height;

		private final String responseFormat;

		private ImageOptionsImpl(final Integer n, final String model, final Integer width, final Integer height,
				final String responseFormat) {
			this.n = n;
			this.model = model;
			this.width = width;
			this.height = height;
			this.responseFormat = responseFormat;
		}

		@Override
		public Integer getN() {
			return n;
		}

		@Override
		public String getModel() {
			return model;
		}

		@Override
		public String getResponseFormat() {
			return responseFormat;
		}

		@Override
		public Integer getWidth() {
			return width;
		}

		@Override
		public Integer getHeight() {
			return height;
		}

	}

}
