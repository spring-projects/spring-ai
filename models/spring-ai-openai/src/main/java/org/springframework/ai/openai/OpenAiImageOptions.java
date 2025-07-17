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

package org.springframework.ai.openai;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.ai.image.ImageOptions;

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
	 * This property is interconnected with the 'size' property - setting both width and
	 * height will automatically compute and set the size in "widthxheight" format.
	 * Conversely, setting a valid size string will parse and set the individual width and
	 * height values.
	 */
	@JsonProperty("size_width")
	private Integer width;

	/**
	 * The height of the generated images. Must be one of 256, 512, or 1024 for dall-e-2.
	 * This property is interconnected with the 'size' property - setting both width and
	 * height will automatically compute and set the size in "widthxheight" format.
	 * Conversely, setting a valid size string will parse and set the individual width and
	 * height values.
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
	 * This property is automatically computed when both width and height are set,
	 * following the format "widthxheight". When setting this property directly, it must
	 * follow the format "WxH" where W and H are valid integers. Invalid formats will
	 * result in null width and height values.
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

	/**
	 * Create a new OpenAiImageOptions instance from an existing one.
	 * @param fromOptions The options to copy from
	 * @return A new OpenAiImageOptions instance
	 */
	public static OpenAiImageOptions fromOptions(OpenAiImageOptions fromOptions) {
		OpenAiImageOptions options = new OpenAiImageOptions();
		options.n = fromOptions.n;
		options.model = fromOptions.model;
		options.width = fromOptions.width;
		options.height = fromOptions.height;
		options.quality = fromOptions.quality;
		options.responseFormat = fromOptions.responseFormat;
		options.size = fromOptions.size;
		options.style = fromOptions.style;
		options.user = fromOptions.user;
		return options;
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
		return this.responseFormat;
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
				String[] dimensions = this.size.split("x");
				if (dimensions.length != 2) {
					return null;
				}
				return Integer.parseInt(dimensions[0]);
			}
			catch (Exception ex) {
				return null;
			}
		}
		return null;
	}

	public void setWidth(Integer width) {
		this.width = width;
		if (this.width != null && this.height != null) {
			this.size = this.width + "x" + this.height;
		}
	}

	@Override
	public Integer getHeight() {
		if (this.height != null) {
			return this.height;
		}
		else if (this.size != null) {
			try {
				String[] dimensions = this.size.split("x");
				if (dimensions.length != 2) {
					return null;
				}
				return Integer.parseInt(dimensions[1]);
			}
			catch (Exception ex) {
				return null;
			}
		}
		return null;
	}

	public void setHeight(Integer height) {
		this.height = height;
		if (this.width != null && this.height != null) {
			this.size = this.width + "x" + this.height;
		}
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

	public String getSize() {
		if (this.size != null) {
			return this.size;
		}
		return (this.width != null && this.height != null) ? this.width + "x" + this.height : null;
	}

	public void setSize(String size) {
		this.size = size;

		// Parse the size string to update width and height
		if (size != null) {
			try {
				String[] dimensions = size.split("x");
				if (dimensions.length == 2) {
					this.width = Integer.parseInt(dimensions[0]);
					this.height = Integer.parseInt(dimensions[1]);
				}
			}
			catch (Exception ex) {
				// If parsing fails, leave width and height unchanged
			}
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof OpenAiImageOptions that)) {
			return false;
		}
		return Objects.equals(this.n, that.n) && Objects.equals(this.model, that.model)
				&& Objects.equals(this.width, that.width) && Objects.equals(this.height, that.height)
				&& Objects.equals(this.quality, that.quality)
				&& Objects.equals(this.responseFormat, that.responseFormat) && Objects.equals(this.size, that.size)
				&& Objects.equals(this.style, that.style) && Objects.equals(this.user, that.user);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.n, this.model, this.width, this.height, this.quality, this.responseFormat, this.size,
				this.style, this.user);
	}

	@Override
	public String toString() {
		return "OpenAiImageOptions{" + "n=" + this.n + ", model='" + this.model + '\'' + ", width=" + this.width
				+ ", height=" + this.height + ", quality='" + this.quality + '\'' + ", responseFormat='"
				+ this.responseFormat + '\'' + ", size='" + this.size + '\'' + ", style='" + this.style + '\''
				+ ", user='" + this.user + '\'' + '}';
	}

	/**
	 * Create a copy of this options instance.
	 * @return A new instance with the same options
	 */
	public OpenAiImageOptions copy() {
		return fromOptions(this);
	}

	public static final class Builder {

		protected OpenAiImageOptions options;

		public Builder() {
			this.options = new OpenAiImageOptions();
		}

		public Builder(OpenAiImageOptions options) {
			this.options = options;
		}

		public Builder N(Integer n) {
			this.options.setN(n);
			return this;
		}

		public Builder model(String model) {
			this.options.setModel(model);
			return this;
		}

		public Builder quality(String quality) {
			this.options.setQuality(quality);
			return this;
		}

		public Builder responseFormat(String responseFormat) {
			this.options.setResponseFormat(responseFormat);
			return this;
		}

		public Builder width(Integer width) {
			this.options.setWidth(width);
			return this;
		}

		public Builder height(Integer height) {
			this.options.setHeight(height);
			return this;
		}

		public Builder style(String style) {
			this.options.setStyle(style);
			return this;
		}

		public Builder user(String user) {
			this.options.setUser(user);
			return this;
		}

		public OpenAiImageOptions build() {
			return this.options;
		}

	}

}
