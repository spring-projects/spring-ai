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

package org.springframework.ai.azure.openai;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.ai.image.ImageOptions;

/**
 * The configuration information for a image generation request.
 *
 * @author Benoit Moussaud
 * @author Thomas Vitale
 * @author Ilayaperumal Gopinathan
 * @since 1.0.0 M1
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AzureOpenAiImageOptions implements ImageOptions {

	public static final String DEFAULT_IMAGE_MODEL = ImageModel.DALL_E_3.getValue();

	/**
	 * The number of images to generate. Must be between 1 and 10. For dall-e-3, only n=1
	 * is supported.
	 */
	@JsonProperty("n")
	private Integer n;

	/**
	 * The model dall-e-3 or dall-e-2 By default dall-e-3
	 */
	@JsonProperty("model")
	private String model = ImageModel.DALL_E_3.value;

	/**
	 * The deployment name as defined in Azure Open AI Studio when creating a deployment
	 * backed by an Azure OpenAI base model.
	 */
	@JsonProperty("deployment_name")
	private String deploymentName;

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
	 * dall-e-3. standard or hd
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
	 * only supported for dall-e-3. natural or vivid
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

	public String getDeploymentName() {
		return this.deploymentName;
	}

	public void setDeploymentName(String deploymentName) {
		this.deploymentName = deploymentName;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof AzureOpenAiImageOptions that)) {
			return false;
		}
		return Objects.equals(this.n, that.n) && Objects.equals(this.model, that.model)
				&& Objects.equals(this.deploymentName, that.deploymentName) && Objects.equals(this.width, that.width)
				&& Objects.equals(this.height, that.height) && Objects.equals(this.quality, that.quality)
				&& Objects.equals(this.responseFormat, that.responseFormat) && Objects.equals(this.size, that.size)
				&& Objects.equals(this.style, that.style) && Objects.equals(this.user, that.user);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.n, this.model, this.deploymentName, this.width, this.height, this.quality,
				this.responseFormat, this.size, this.style, this.user);
	}

	@Override
	public String toString() {
		return "AzureOpenAiImageOptions{" + "n=" + this.n + ", model='" + this.model + '\'' + ", deploymentName='"
				+ this.deploymentName + '\'' + ", width=" + this.width + ", height=" + this.height + ", quality='"
				+ this.quality + '\'' + ", responseFormat='" + this.responseFormat + '\'' + ", size='" + this.size
				+ '\'' + ", style='" + this.style + '\'' + ", user='" + this.user + '\'' + '}';
	}

	public enum ImageModel {

		/**
		 * The latest DALL·E model released in Nov 2023.
		 */
		DALL_E_3("dall-e-3"),

		/**
		 * The previous DALL·E model released in Nov 2022. The 2nd iteration of DALL·E
		 * with more realistic, accurate, and 4x greater resolution images than the
		 * original model.
		 */
		DALL_E_2("dall-e-2");

		private final String value;

		ImageModel(String model) {
			this.value = model;
		}

		public String getValue() {
			return this.value;
		}

	}

	public static final class Builder {

		private final AzureOpenAiImageOptions options;

		private Builder() {
			this.options = new AzureOpenAiImageOptions();
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

		public AzureOpenAiImageOptions build() {
			return this.options;
		}

	}

}
