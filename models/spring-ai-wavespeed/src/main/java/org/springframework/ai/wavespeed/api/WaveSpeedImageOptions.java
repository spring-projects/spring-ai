/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.wavespeed.api;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.image.ImageOptions;

/**
 * {@link ImageOptions} implementation for the WaveSpeed AI image generation API.
 *
 * @author Zeyi Cheng
 * @since 2.0.1
 */
public final class WaveSpeedImageOptions implements ImageOptions {

	/**
	 * The model to use for image generation, for example
	 * {@code bytedance/seedream-v5.0-pro}. The model is passed in the URL as a path
	 * parameter.
	 */
	private final @Nullable String model;

	/**
	 * The size of the generated image as {@code {width}*{height}}, for example
	 * {@code 1024*1024}. Takes precedence over {@link #width} and {@link #height} when
	 * both are set.
	 */
	private final @Nullable String size;

	/**
	 * The width of the generated image, in pixels. Combined with {@link #height} into a
	 * {@code {width}*{height}} size when {@link #size} is not set.
	 */
	private final @Nullable Integer width;

	/**
	 * The height of the generated image, in pixels. Combined with {@link #width} into a
	 * {@code {width}*{height}} size when {@link #size} is not set.
	 */
	private final @Nullable Integer height;

	/**
	 * The random seed used for generation, or {@code -1} for a random seed.
	 */
	private final @Nullable Integer seed;

	private WaveSpeedImageOptions(Builder builder) {
		this.model = builder.model;
		this.size = builder.size;
		this.width = builder.width;
		this.height = builder.height;
		this.seed = builder.seed;
	}

	public static Builder builder() {
		return new Builder();
	}

	@Override
	public @Nullable String getModel() {
		return this.model;
	}

	@Override
	public @Nullable Integer getN() {
		return null;
	}

	@Override
	public @Nullable Integer getWidth() {
		return this.width;
	}

	@Override
	public @Nullable Integer getHeight() {
		return this.height;
	}

	@Override
	public @Nullable String getResponseFormat() {
		return null;
	}

	@Override
	public @Nullable String getStyle() {
		return null;
	}

	public @Nullable String getSize() {
		return this.size;
	}

	public @Nullable Integer getSeed() {
		return this.seed;
	}

	/**
	 * Compute the effective {@code {width}*{height}} size to send in the request:
	 * {@link #size} when set, otherwise composed from {@link #width} and {@link #height}.
	 * @return the effective size, or {@code null} to let the model use its default
	 */
	public @Nullable String toSizeString() {
		if (this.size != null) {
			return this.size;
		}
		if (this.width != null && this.height != null) {
			return this.width + "*" + this.height;
		}
		return null;
	}

	@Override
	public boolean equals(@Nullable Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof WaveSpeedImageOptions other)) {
			return false;
		}
		return Objects.equals(this.model, other.model) && Objects.equals(this.size, other.size)
				&& Objects.equals(this.width, other.width) && Objects.equals(this.height, other.height)
				&& Objects.equals(this.seed, other.seed);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.model, this.size, this.width, this.height, this.seed);
	}

	@Override
	public String toString() {
		return "WaveSpeedImageOptions{model='" + this.model + "', size='" + this.size + "', width=" + this.width
				+ ", height=" + this.height + ", seed=" + this.seed + "}";
	}

	public static final class Builder {

		private @Nullable String model;

		private @Nullable String size;

		private @Nullable Integer width;

		private @Nullable Integer height;

		private @Nullable Integer seed;

		private Builder() {

		}

		public Builder model(@Nullable String model) {
			this.model = model;
			return this;
		}

		public Builder size(@Nullable String size) {
			this.size = size;
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

		public Builder seed(@Nullable Integer seed) {
			this.seed = seed;
			return this;
		}

		public WaveSpeedImageOptions build() {
			return new WaveSpeedImageOptions(this);
		}

	}

}
