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
package org.springframework.ai.qianfan;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.ai.image.ImageOptions;

import java.util.Objects;

/**
 * QianFan Image API options. QianFanImageOptions.java
 *
 * @author Geng Rong
 * @since 1.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QianFanImageOptions implements ImageOptions {

	/**
	 * The number of images to generate. Must be between 1 and 4.
	 */
	@JsonProperty("n")
	private Integer n;

	/**
	 * The model to use for image generation.
	 */
	@JsonProperty("model")
	private String model;

	/**
	 * The width of the generated images. Must be one of 576, 768, 1024, 1152, 1536 or
	 * 2048 * for sd_xl.
	 */
	@JsonProperty("size_width")
	private Integer width;

	/**
	 * The height of the generated images. Must be one of 576, 768, 1024, 1152, 1536 or
	 * 2048 for sd_xl.
	 */
	@JsonProperty("size_height")
	private Integer height;

	/**
	 * The size of the generated images. The default image dimensions are 1024x1024, with
	 * the following ranges applicable: Suitable for avatars: ["768x768", "1024x1024",
	 * "1536x1536", "2048x2048"] Suitable for article illustrations: ["1024x768",
	 * "2048x1536"] Suitable for posters and flyers: ["768x1024", "1536x2048"] Suitable
	 * for computer wallpapers: ["1024x576", "2048x1152"] Suitable for posters and flyers:
	 * ["576x1024", "1152x2048"]
	 */
	@JsonProperty("size")
	private String size;

	/**
	 * The style of the generated images. The default style is Base. Must be one of:
	 * [Base, 3D Model, Abstract, Analog Film, Anime, Cinematic, Comic Book, Craft Clay,
	 * Digital Art, Enhance, Fantasy Art, Isometric, Line Art, Lowpoly, Neonpunk, Origami,
	 * Photographic, Pixel Art, Texture]
	 */
	@JsonProperty("style")
	private String style;

	/**
	 * A unique identifier representing your end-user, which can help QianFan to monitor
	 * and detect abuse.
	 */
	@JsonProperty("user_id")
	private String user;

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private final QianFanImageOptions options;

		private Builder() {
			this.options = new QianFanImageOptions();
		}

		public Builder withN(Integer n) {
			options.setN(n);
			return this;
		}

		public Builder withModel(String model) {
			options.setModel(model);
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

		public QianFanImageOptions build() {
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

	@Override
	public Integer getWidth() {
		return this.width;
	}

	public void setWidth(Integer width) {
		this.width = width;
		this.size = this.width + "x" + this.height;
	}

	@Override
	public Integer getHeight() {
		return this.height;
	}

	public void setHeight(Integer height) {
		this.height = height;
		this.size = this.width + "x" + this.height;
	}

	@Override
	@JsonIgnore
	public String getResponseFormat() {
		return null;
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
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof QianFanImageOptions that))
			return false;
		return Objects.equals(n, that.n) && Objects.equals(model, that.model) && Objects.equals(width, that.width)
				&& Objects.equals(height, that.height) && Objects.equals(size, that.size)
				&& Objects.equals(style, that.style) && Objects.equals(user, that.user);
	}

	@Override
	public int hashCode() {
		return Objects.hash(n, model, width, height, size, style, user);
	}

	@Override
	public String toString() {
		return "QianFanImageOptions{" + "n=" + n + ", model='" + model + '\'' + ", width=" + width + ", height="
				+ height + ", size='" + size + '\'' + ", style='" + style + '\'' + ", user='" + user + '\'' + '}';
	}

}
