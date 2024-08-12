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
package org.springframework.ai.openai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.ai.image.ImageOptions;

import java.util.Objects;

/**
 * OpenAI Image API options. OpenAiImageOptions.java
 *
 * @author Mark Pollack
 * @author Christian Tzolov
 * @since 0.8.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenAiImageOptions implements ImageOptions {

	/**
	 * The number of images to generate. Must be between 1 and 10. For dall-e-3, only n=1
	 * is supported.
	 */
	@JsonProperty("n")
	private Integer n;

	/**
	 * The model to use for image generation.
	 */
	@JsonProperty("model")
	private String model;

	/**
	 * The width of the generated images. Must be one of 256, 512, or 1024 for dall-e-2.
	 */
	@JsonProperty("size_width")
	private Integer width;

	/**
	 * The height of the generated images. Must be one of 256, 512, or 1024 for dall-e-2.
	 */
	@JsonProperty("size_height")
	private Integer height;

	/**
	 * The quality of the image that will be generated. hd creates images with finer
	 * details and greater consistency across the image. This param is only supported for
	 * dall-e-3.
	 */
	@JsonProperty("quality")
	private String quality;

	/**
	 * The format in which the generated images are returned. Must be one of url or
	 * b64_json.
	 */
	@JsonProperty("response_format")
	private String responseFormat;

	/**
	 * The size of the generated images. Must be one of 256x256, 512x512, or 1024x1024 for
	 * dall-e-2. Must be one of 1024x1024, 1792x1024, or 1024x1792 for dall-e-3 models.
	 */
	@JsonProperty("size")
	private String size;

	/**
	 * The style of the generated images. Must be one of vivid or natural. Vivid causes
	 * the model to lean towards generating hyper-real and dramatic images. Natural causes
	 * the model to produce more natural, less hyper-real looking images. This param is
	 * only supported for dall-e-3.
	 */
	@JsonProperty("style")
	private String style;

	/**
	 * A unique identifier representing your end-user, which can help OpenAI to monitor
	 * and detect abuse.
	 */
	@JsonProperty("user")
	private String user;

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private final OpenAiImageOptions options;

		private Builder() {
			this.options = new OpenAiImageOptions();
		}

		public Builder withN(Integer n) {
			options.setN(n);
			return this;
		}

		public Builder withModel(String model) {
			options.setModel(model);
			return this;
		}

		public Builder withQuality(String quality) {
			options.setQuality(quality);
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

		public Builder withStyle(String style) {
			options.setStyle(style);
			return this;
		}

		public Builder withUser(String user) {
			options.setUser(user);
			return this;
		}

		public OpenAiImageOptions build() {
			return options;
		}

	}

	@Override
	public Integer getN() {
		return this.n;
	}

	public void setN(Integer n) {
		this.n = n;
	}

	@Override
	public String getModel() {
		return this.model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public String getQuality() {
		return this.quality;
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
		if (this.width != null) {
			return this.width;
		}
		else if (this.size != null) {
			try {
				return Integer.parseInt(this.size.split("x")[0]);
			}
			catch (NumberFormatException ex) {
				return null;
			}
		}
		return null;
	}

	public void setWidth(Integer width) {
		this.width = width;
		this.size = this.width + "x" + this.height;
	}

	@Override
	public Integer getHeight() {
		if (this.height != null) {
			return this.height;
		}
		else if (this.size != null) {
			try {
				return Integer.parseInt(this.size.split("x")[1]);
			}
			catch (NumberFormatException ex) {
				return null;
			}
		}
		return null;
	}

	public void setHeight(Integer height) {
		this.height = height;
		this.size = this.width + "x" + this.height;
	}

	@Override
	public String getStyle() {
		return this.style;
	}

	public void setStyle(String style) {
		this.style = style;
	}

	public String getUser() {
		return this.user;
	}

	public void setUser(String user) {
		this.user = user;
	}

	public void setSize(String size) {
		this.size = size;
	}

	public String getSize() {
		if (this.size != null) {
			return this.size;
		}
		return (this.width != null && this.height != null) ? this.width + "x" + this.height : null;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof OpenAiImageOptions that))
			return false;
		return Objects.equals(n, that.n) && Objects.equals(model, that.model) && Objects.equals(width, that.width)
				&& Objects.equals(height, that.height) && Objects.equals(quality, that.quality)
				&& Objects.equals(responseFormat, that.responseFormat) && Objects.equals(size, that.size)
				&& Objects.equals(style, that.style) && Objects.equals(user, that.user);
	}

	@Override
	public int hashCode() {
		return Objects.hash(n, model, width, height, quality, responseFormat, size, style, user);
	}

	@Override
	public String toString() {
		return "OpenAiImageOptions{" + "n=" + n + ", model='" + model + '\'' + ", width=" + width + ", height=" + height
				+ ", quality='" + quality + '\'' + ", responseFormat='" + responseFormat + '\'' + ", size='" + size
				+ '\'' + ", style='" + style + '\'' + ", user='" + user + '\'' + '}';
	}

}
