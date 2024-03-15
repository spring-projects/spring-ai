package org.springframework.ai.openai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;
import org.springframework.ai.image.ImageOptions;
import org.springframework.ai.image.ImageOptionsBuilder;
import org.springframework.ai.openai.api.OpenAiImageApi;

public class OpenAiImageOptionsBuilder {

	private final ImageOptionsBuilder imageOptionsBuilder = ImageOptionsBuilder.builder()
		.withModel(OpenAiImageApi.DEFAULT_IMAGE_MODEL);

	private String quality;

	private String size;

	private String style;

	private String user;

	public static OpenAiImageOptionsBuilder builder() {
		return new OpenAiImageOptionsBuilder();
	}

	public static OpenAiImageOptionsBuilder builder(final ImageOptions options) {
		return builder().withN(options.getN())
			.withModel(options.getModel())
			.withWidth(options.getWidth())
			.withHeight(options.getHeight());
	}

	public static OpenAiImageOptionsBuilder builder(final OpenAiImageOptions options) {
		return builder((ImageOptions) options).withQuality(options.getQuality())
			.withResponseFormat(options.getResponseFormat())
			.withSize(options.getSize())
			.withStyle(options.getStyle())
			.withUser(options.getUser());
	}

	public OpenAiImageOptionsImpl build() {
		return new OpenAiImageOptionsImpl(this);
	}

	public OpenAiImageOptionsBuilder withN(final Integer n) {
		this.imageOptionsBuilder.withN(n);
		return this;
	}

	public OpenAiImageOptionsBuilder withModel(final String model) {
		this.imageOptionsBuilder.withModel(model);
		return this;
	};

	public OpenAiImageOptionsBuilder withWidth(final Integer width) {
		this.imageOptionsBuilder.withWidth(width);
		return this;
	}

	public OpenAiImageOptionsBuilder withHeight(final Integer height) {
		this.imageOptionsBuilder.withHeight(height);
		return this;
	}

	public OpenAiImageOptionsBuilder withResponseFormat(final String responseFormat) {
		this.imageOptionsBuilder.withResponseFormat(responseFormat);
		return this;
	}

	public OpenAiImageOptionsBuilder withQuality(final String quality) {
		this.quality = quality;
		return this;
	}

	public OpenAiImageOptionsBuilder withSize(final String size) {
		this.size = size;
		return this;
	}

	public OpenAiImageOptionsBuilder withStyle(final String style) {
		this.style = style;
		return this;
	}

	public OpenAiImageOptionsBuilder withUser(final String user) {
		this.user = user;
		return this;
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	private static class OpenAiImageOptionsImpl implements OpenAiImageOptions {

		private OpenAiImageOptionsImpl(OpenAiImageOptionsBuilder builder) {
			this.options = builder.imageOptionsBuilder.build();
			this.quality = builder.quality;
			this.user = builder.user;
			this.style = builder.style;
			if (builder.size != null) {
				this.size = builder.size;
			}
			else
				this.size = (this.options.getWidth() != null && this.options.getWidth() != null)
						? this.options.getWidth() + "x" + this.options.getHeight() : null;
		}

		private final ImageOptions options;

		private final String quality;

		private final String size;

		private final String style;

		private final String user;

		/**
		 * The number of images to generate. Must be between 1 and 10. For dall-e-3, only
		 * n=1 is supported.
		 */
		@Override
		@JsonProperty("n")
		public Integer getN() {
			return this.options.getN();
		}

		/**
		 * The model to use for image generation.
		 */
		@Override
		@JsonProperty("model")
		public String getModel() {
			return this.options.getModel();
		}

		/**
		 * The format in which the generated images are returned. Must be one of url or
		 * b64_json.
		 */
		@Override
		@JsonProperty("response_format")
		public String getResponseFormat() {
			return this.options.getResponseFormat();
		}

		/**
		 * The width of the generated images. Must be one of 256, 512, or 1024 for
		 * dall-e-2.
		 */
		@Override
		@JsonProperty("size_width")
		public Integer getWidth() {
			return this.options.getWidth();
		}

		/**
		 * The height of the generated images. Must be one of 256, 512, or 1024 for
		 * dall-e-2.
		 */
		@Override
		@JsonProperty("size_height")
		public Integer getHeight() {
			return this.options.getHeight();
		}

		@Override
		public String getQuality() {
			return this.quality;
		}

		@Override
		public String getStyle() {
			return this.style;
		}

		@Override
		public String getUser() {
			return this.user;
		}

		@Override
		public String getSize() {
			return this.size;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (!(o instanceof OpenAiImageOptionsImpl that))
				return false;
			return Objects.equals(options, that.options) && Objects.equals(quality, that.quality)
					&& Objects.equals(size, that.size) && Objects.equals(style, that.style)
					&& Objects.equals(user, that.user);
		}

		@Override
		public int hashCode() {
			return Objects.hash(options, quality, size, style, user);
		}

		@Override
		public String toString() {
			return "OpenAiImageOptions{" + "n=" + options.getN() + ", model='" + options.getModel() + '\'' + ", width="
					+ options.getWidth() + ", height=" + options.getHeight() + ", quality='" + quality + '\''
					+ ", responseFormat='" + options.getResponseFormat() + '\'' + ", size='" + size + '\'' + ", style='"
					+ style + '\'' + ", user='" + user + '\'' + '}';
		}

	}

}
