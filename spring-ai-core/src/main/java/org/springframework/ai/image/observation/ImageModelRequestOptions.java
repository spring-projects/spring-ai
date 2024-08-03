/*
 * Copyright 2024 the original author or authors.
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
package org.springframework.ai.image.observation;

import org.springframework.ai.image.ImageOptions;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Represents client-side options for image model requests.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public class ImageModelRequestOptions implements ImageOptions {

	private final String model;

	@Nullable
	private final Integer n;

	@Nullable
	private final Integer width;

	@Nullable
	private final Integer height;

	@Nullable
	private final String responseFormat;

	@Nullable
	private final String style;

	ImageModelRequestOptions(Builder builder) {
		Assert.hasText(builder.model, "model cannot be null or empty");

		this.model = builder.model;
		this.n = builder.n;
		this.width = builder.width;
		this.height = builder.height;
		this.responseFormat = builder.responseFormat;
		this.style = builder.style;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private String model;

		@Nullable
		private Integer n;

		@Nullable
		private Integer width;

		@Nullable
		private Integer height;

		@Nullable
		private String responseFormat;

		@Nullable
		private String style;

		private Builder() {
		}

		public Builder model(String model) {
			this.model = model;
			return this;
		}

		public Builder n(@Nullable Integer n) {
			this.n = n;
			return this;
		}

		public Builder width(@Nullable Integer width) {
			this.width = width;
			return this;
		}

		public Builder height(@Nullable Integer height) {
			this.height = height;
			return this;
		}

		public Builder responseFormat(@Nullable String responseFormat) {
			this.responseFormat = responseFormat;
			return this;
		}

		public Builder style(@Nullable String style) {
			this.style = style;
			return this;
		}

		public ImageModelRequestOptions build() {
			return new ImageModelRequestOptions(this);
		}

	}

	@Override
	public String getModel() {
		return model;
	}

	@Override
	@Nullable
	public Integer getN() {
		return n;
	}

	@Override
	@Nullable
	public Integer getWidth() {
		return width;
	}

	@Override
	@Nullable
	public Integer getHeight() {
		return height;
	}

	@Override
	@Nullable
	public String getResponseFormat() {
		return responseFormat;
	}

	@Nullable
	public String getStyle() {
		return style;
	}

}
