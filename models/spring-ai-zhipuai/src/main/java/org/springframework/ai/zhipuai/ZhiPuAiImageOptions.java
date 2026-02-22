/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.zhipuai;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.ai.image.ImageOptions;
import org.springframework.ai.zhipuai.api.ZhiPuAiImageApi;

/**
 * ZhiPuAiImageOptions represents the options for image generation using ZhiPuAI image
 * model.
 *
 * <p>
 * It implements the ImageOptions interface, which is portable across different image
 * generation models.
 * </p>
 *
 * <p>
 * Default values:
 * </p>
 * <ul>
 * <li>model: ZhiPuAiImageApi.DEFAULT_IMAGE_MODEL</li>
 * <li>user: null</li>
 * <li>size: null</li>
 * <li>quality: null</li>
 * <li>watermarkEnabled: null</li>
 * </ul>
 *
 * @author Geng Rong
 * @author Ilayaperumal Gopinathan
 * @author YunKui Lu
 * @since 1.0.0 M1
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ZhiPuAiImageOptions implements ImageOptions {

	/**
	 * The model to use for image generation.
	 */
	@JsonProperty("model")
	private String model = ZhiPuAiImageApi.DEFAULT_IMAGE_MODEL;

	/**
	 * A unique identifier representing your end-user, which can help ZhiPuAI to monitor
	 * and detect abuse. User ID length requirement: minimum of 6 characters, maximum of
	 * 128 characters
	 */
	@JsonProperty("user_id")
	private String user;

	/**
	 * The image size, for example 1024x1024. Both dimensions must be between 512 and
	 * 2048, divisible by 16, and total pixels must not exceed 2^21.
	 */
	@JsonProperty("size")
	private String size;

	/**
	 * The quality of the generated image. Defaults to standard and only supported by
	 * cogview-4-250304. Supported values: hd, standard.
	 */
	@JsonProperty("quality")
	private String quality;

	/**
	 * Whether to enable watermarking on generated images.
	 */
	@JsonProperty("watermark_enabled")
	private Boolean watermarkEnabled;

	public static Builder builder() {
		return new Builder();
	}

	@Override
	@JsonIgnore
	public Integer getN() {
		return null;
	}

	@Override
	public String getModel() {
		return this.model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	@Override
	@JsonIgnore
	public Integer getWidth() {
		return null;
	}

	@Override
	@JsonIgnore
	public Integer getHeight() {
		return null;
	}

	@Override
	@JsonIgnore
	public String getResponseFormat() {
		return null;
	}

	@Override
	@JsonIgnore
	public String getStyle() {
		return null;
	}

	public String getUser() {
		return this.user;
	}

	public void setUser(String user) {
		this.user = user;
	}

	public String getSize() {
		return this.size;
	}

	public void setSize(String size) {
		this.size = size;
	}

	public String getQuality() {
		return this.quality;
	}

	public void setQuality(String quality) {
		this.quality = quality;
	}

	public void setQuality(ZhiPuAiImageApi.Quality quality) {
		this.quality = (quality != null) ? quality.getValue() : null;
	}

	public Boolean getWatermarkEnabled() {
		return this.watermarkEnabled;
	}

	public void setWatermarkEnabled(Boolean watermarkEnabled) {
		this.watermarkEnabled = watermarkEnabled;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof ZhiPuAiImageOptions that)) {
			return false;
		}
		return Objects.equals(this.model, that.model) && Objects.equals(this.user, that.user)
				&& Objects.equals(this.size, that.size) && Objects.equals(this.quality, that.quality)
				&& Objects.equals(this.watermarkEnabled, that.watermarkEnabled);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.model, this.user, this.size, this.quality, this.watermarkEnabled);
	}

	@Override
	public String toString() {
		return "ZhiPuAiImageOptions{model='" + this.model + '\'' + ", user='" + this.user + '\'' + ", size='"
				+ this.size + '\'' + ", quality='" + this.quality + '\'' + ", watermarkEnabled=" + this.watermarkEnabled
				+ '}';
	}

	public static final class Builder {

		private final ZhiPuAiImageOptions options;

		private Builder() {
			this.options = new ZhiPuAiImageOptions();
		}

		public Builder model(String model) {
			this.options.setModel(model);
			return this;
		}

		public Builder user(String user) {
			this.options.setUser(user);
			return this;
		}

		public Builder size(String size) {
			this.options.setSize(size);
			return this;
		}

		public Builder quality(String quality) {
			this.options.setQuality(quality);
			return this;
		}

		public Builder quality(ZhiPuAiImageApi.Quality quality) {
			this.options.setQuality(quality);
			return this;
		}

		public Builder watermarkEnabled(Boolean watermarkEnabled) {
			this.options.setWatermarkEnabled(watermarkEnabled);
			return this;
		}

		public ZhiPuAiImageOptions build() {
			return this.options;
		}

	}

}
