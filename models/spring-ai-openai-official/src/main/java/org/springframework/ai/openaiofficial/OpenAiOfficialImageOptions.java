/*
 * Copyright 2025 the original author or authors.
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
package org.springframework.ai.openaiofficial;

import com.openai.models.images.ImageGenerateParams;
import com.openai.models.images.ImageModel;
import org.springframework.ai.image.ImageOptions;
import org.springframework.ai.image.ImagePrompt;

import java.util.Objects;

/**
 * Configuration information for the Image Model implementation using the OpenAI Java SDK.
 *
 * @author Julien Dubois
 */
public class OpenAiOfficialImageOptions extends AbstractOpenAiOfficialOptions implements ImageOptions {

	public static final String DEFAULT_IMAGE_MODEL = ImageModel.DALL_E_3.toString();

	/**
	 * The number of images to generate. Must be between 1 and 10. For dall-e-3, only n=1
	 * is supported.
	 */
	private Integer n;

	/**
	 * The width of the generated images. Must be one of 256, 512, or 1024 for dall-e-2.
	 */
	private Integer width;

	/**
	 * The height of the generated images. Must be one of 256, 512, or 1024 for dall-e-2.
	 */
	private Integer height;

	/**
	 * The quality of the image that will be generated. hd creates images with finer
	 * details and greater consistency across the image. This param is only supported for
	 * dall-e-3. standard or hd
	 */
	private String quality;

	/**
	 * The format in which the generated images are returned. Must be one of url or
	 * b64_json.
	 */
	private String responseFormat;

	/**
	 * The size of the generated images. Must be one of 256x256, 512x512, or 1024x1024 for
	 * dall-e-2. Must be one of 1024x1024, 1792x1024, or 1024x1792 for dall-e-3 models.
	 */
	private String size;

	/**
	 * The style of the generated images. Must be one of vivid or natural. Vivid causes
	 * the model to lean towards generating hyper-real and dramatic images. Natural causes
	 * the model to produce more natural, less hyper-real looking images. This param is
	 * only supported for dall-e-3. natural or vivid
	 */
	private String style;

	/**
	 * A unique identifier representing your end-user, which can help OpenAI to monitor
	 * and detect abuse.
	 */
	private String user;

	public static Builder builder() {
		return new Builder();
	}

	@Override
	public Integer getN() {
		return this.n;
	}

	public void setN(Integer n) {
		this.n = n;
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
	public String getResponseFormat() {
		return this.responseFormat;
	}

	public void setResponseFormat(String responseFormat) {
		this.responseFormat = responseFormat;
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

	public String getUser() {
		return this.user;
	}

	public void setUser(String user) {
		this.user = user;
	}

	public String getQuality() {
		return this.quality;
	}

	public void setQuality(String quality) {
		this.quality = quality;
	}

	@Override
	public String getStyle() {
		return this.style;
	}

	public void setStyle(String style) {
		this.style = style;
	}

	@Override
	public boolean equals(Object o) {
		if (o == null || getClass() != o.getClass())
			return false;
		OpenAiOfficialImageOptions that = (OpenAiOfficialImageOptions) o;
		return Objects.equals(n, that.n) && Objects.equals(width, that.width) && Objects.equals(height, that.height)
				&& Objects.equals(quality, that.quality) && Objects.equals(responseFormat, that.responseFormat)
				&& Objects.equals(size, that.size) && Objects.equals(style, that.style)
				&& Objects.equals(user, that.user);
	}

	@Override
	public int hashCode() {
		return Objects.hash(n, width, height, quality, responseFormat, size, style, user);
	}

	@Override
	public String toString() {
		return "OpenAiOfficialImageOptions{" + "n=" + n + ", width=" + width + ", height=" + height + ", quality='"
				+ quality + '\'' + ", responseFormat='" + responseFormat + '\'' + ", size='" + size + '\'' + ", style='"
				+ style + '\'' + ", user='" + user + '\'' + '}';
	}

	public ImageGenerateParams toOpenAiImageGenerateParams(ImagePrompt imagePrompt) {
		if (imagePrompt.getInstructions().isEmpty()) {
			throw new IllegalArgumentException("Image prompt instructions cannot be empty");
		}

		String prompt = imagePrompt.getInstructions().get(0).getText();
		ImageGenerateParams.Builder builder = ImageGenerateParams.builder().prompt(prompt);

		// Use deployment name if available (for Azure AI Foundry), otherwise use model
		// name
		if (this.getDeploymentName() != null) {
			builder.model(this.getDeploymentName());
		}
		else if (this.getModel() != null) {
			builder.model(this.getModel());
		}

		if (this.getN() != null) {
			builder.n(this.getN().longValue());
		}
		if (this.getQuality() != null) {
			builder.quality(ImageGenerateParams.Quality.of(this.getQuality().toLowerCase()));
		}
		if (this.getResponseFormat() != null) {
			builder.responseFormat(ImageGenerateParams.ResponseFormat.of(this.getResponseFormat().toLowerCase()));
		}
		if (this.getSize() != null) {
			builder.size(ImageGenerateParams.Size.of(this.getSize()));
		}
		if (this.getStyle() != null) {
			builder.style(ImageGenerateParams.Style.of(this.getStyle().toLowerCase()));
		}
		if (this.getUser() != null) {
			builder.user(this.getUser());
		}

		return builder.build();
	}

	public static final class Builder {

		private final OpenAiOfficialImageOptions options;

		private Builder() {
			this.options = new OpenAiOfficialImageOptions();
		}

		public Builder from(OpenAiOfficialImageOptions fromOptions) {
			this.options.setN(fromOptions.getN());
			this.options.setModel(fromOptions.getModel());
			this.options.setDeploymentName(fromOptions.getDeploymentName());
			this.options.setWidth(fromOptions.getWidth());
			this.options.setHeight(fromOptions.getHeight());
			this.options.setQuality(fromOptions.getQuality());
			this.options.setResponseFormat(fromOptions.getResponseFormat());
			this.options.setSize(fromOptions.getSize());
			this.options.setStyle(fromOptions.getStyle());
			this.options.setUser(fromOptions.getUser());
			return this;
		}

		public Builder merge(ImageOptions from) {
			if (from instanceof OpenAiOfficialImageOptions castFrom) {
				if (castFrom.getN() != null) {
					this.options.setN(castFrom.getN());
				}
				if (castFrom.getModel() != null) {
					this.options.setModel(castFrom.getModel());
				}
				if (castFrom.getDeploymentName() != null) {
					this.options.setDeploymentName(castFrom.getDeploymentName());
				}
				if (castFrom.getWidth() != null) {
					this.options.setWidth(castFrom.getWidth());
				}
				if (castFrom.getHeight() != null) {
					this.options.setHeight(castFrom.getHeight());
				}
				if (castFrom.getQuality() != null) {
					this.options.setQuality(castFrom.getQuality());
				}
				if (castFrom.getResponseFormat() != null) {
					this.options.setResponseFormat(castFrom.getResponseFormat());
				}
				if (castFrom.getSize() != null) {
					this.options.setSize(castFrom.getSize());
				}
				if (castFrom.getStyle() != null) {
					this.options.setStyle(castFrom.getStyle());
				}
				if (castFrom.getUser() != null) {
					this.options.setUser(castFrom.getUser());
				}
			}
			return this;
		}

		public Builder N(Integer n) {
			this.options.setN(n);
			return this;
		}

		public Builder model(String model) {
			this.options.setModel(model);
			return this;
		}

		public Builder deploymentName(String deploymentName) {
			this.options.setDeploymentName(deploymentName);
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

		public Builder user(String user) {
			this.options.setUser(user);
			return this;
		}

		public Builder style(String style) {
			this.options.setStyle(style);
			return this;
		}

		public OpenAiOfficialImageOptions build() {
			return this.options;
		}

	}

}
