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

package org.springframework.ai.openai;

import java.net.Proxy;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

import com.openai.azure.AzureOpenAIServiceVersion;
import com.openai.credential.Credential;
import com.openai.models.images.ImageGenerateParams;
import com.openai.models.images.ImageModel;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.image.ImageOptions;
import org.springframework.ai.image.ImagePrompt;

/**
 * Configuration information for the Image Model implementation using the OpenAI Java SDK.
 *
 * @author Julien Dubois
 * @author Christian Tzolov
 * @author Mark Pollack
 */
public class OpenAiImageOptions extends AbstractOpenAiOptions implements ImageOptions {

	public static final String DEFAULT_IMAGE_MODEL = ImageModel.DALL_E_3.toString();

	/**
	 * The number of images to generate. Must be between 1 and 10. For dall-e-3, only n=1
	 * is supported.
	 */
	private final @Nullable Integer n;

	/**
	 * The width of the generated images. Must be one of 256, 512, or 1024 for dall-e-2.
	 */
	private final @Nullable Integer width;

	/**
	 * The height of the generated images. Must be one of 256, 512, or 1024 for dall-e-2.
	 */
	private final @Nullable Integer height;

	/**
	 * The quality of the image that will be generated. hd creates images with finer
	 * details and greater consistency across the image. This param is only supported for
	 * dall-e-3. standard or hd
	 */
	private final @Nullable String quality;

	/**
	 * The format in which the generated images are returned. Must be one of url or
	 * b64_json.
	 */
	private final @Nullable String responseFormat;

	/**
	 * The size of the generated images. Must be one of 256x256, 512x512, or 1024x1024 for
	 * dall-e-2. Must be one of 1024x1024, 1792x1024, or 1024x1792 for dall-e-3 models.
	 */
	private final @Nullable String size;

	/**
	 * The style of the generated images. Must be one of vivid or natural. Vivid causes
	 * the model to lean towards generating hyper-real and dramatic images. Natural causes
	 * the model to produce more natural, less hyper-real looking images. This param is
	 * only supported for dall-e-3. natural or vivid
	 */
	private final @Nullable String style;

	/**
	 * A unique identifier representing your end-user, which can help OpenAI to monitor
	 * and detect abuse.
	 */
	private final @Nullable String user;

	protected OpenAiImageOptions(@Nullable String baseUrl, @Nullable String apiKey, @Nullable Credential credential,
			@Nullable String model, @Nullable String microsoftDeploymentName,
			@Nullable AzureOpenAIServiceVersion microsoftFoundryServiceVersion, @Nullable String organizationId,
			@Nullable Boolean isMicrosoftFoundry, @Nullable Boolean isGitHubModels, @Nullable Duration timeout,
			@Nullable Integer maxRetries, @Nullable Proxy proxy, @Nullable Map<String, String> customHeaders,
			@Nullable Integer n, @Nullable Integer width, @Nullable Integer height, @Nullable String quality,
			@Nullable String responseFormat, @Nullable String size, @Nullable String style, @Nullable String user) {
		super(baseUrl, apiKey, credential, model, microsoftDeploymentName, microsoftFoundryServiceVersion,
				organizationId, isMicrosoftFoundry, isGitHubModels, timeout, maxRetries, proxy, customHeaders);
		this.n = n;
		this.width = width;
		this.height = height;
		this.quality = quality;
		this.responseFormat = responseFormat;
		this.size = size;
		this.style = style;
		this.user = user;
	}

	public static Builder builder() {
		return new Builder();
	}

	@Override
	public @Nullable Integer getN() {
		return this.n;
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
		return this.responseFormat;
	}

	public @Nullable String getSize() {
		if (this.size != null) {
			return this.size;
		}
		return (this.width != null && this.height != null) ? this.width + "x" + this.height : null;
	}

	public @Nullable String getUser() {
		return this.user;
	}

	public @Nullable String getQuality() {
		return this.quality;
	}

	@Override
	public @Nullable String getStyle() {
		return this.style;
	}

	@Override
	public boolean equals(Object o) {
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		OpenAiImageOptions that = (OpenAiImageOptions) o;
		return Objects.equals(this.n, that.n) && Objects.equals(this.width, that.width)
				&& Objects.equals(this.height, that.height) && Objects.equals(this.quality, that.quality)
				&& Objects.equals(this.responseFormat, that.responseFormat) && Objects.equals(this.size, that.size)
				&& Objects.equals(this.style, that.style) && Objects.equals(this.user, that.user);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.n, this.width, this.height, this.quality, this.responseFormat, this.size, this.style,
				this.user);
	}

	@Override
	public String toString() {
		return "OpenAiImageOptions{" + "n=" + this.n + ", width=" + this.width + ", height=" + this.height
				+ ", quality='" + this.quality + '\'' + ", responseFormat='" + this.responseFormat + '\'' + ", size='"
				+ this.size + '\'' + ", style='" + this.style + '\'' + ", user='" + this.user + '\'' + '}';
	}

	public ImageGenerateParams toOpenAiImageGenerateParams(ImagePrompt imagePrompt) {
		if (imagePrompt.getInstructions().isEmpty()) {
			throw new IllegalArgumentException("Image prompt instructions cannot be empty");
		}

		String prompt = imagePrompt.getInstructions().get(0).getText();
		ImageGenerateParams.Builder builder = ImageGenerateParams.builder().prompt(prompt);

		// Use deployment name if available (for Microsoft Foundry), otherwise use model
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

	public static final class Builder extends AbstractBuilder<OpenAiImageOptions, Builder> {

		private @Nullable Integer n;

		private @Nullable Integer width;

		private @Nullable Integer height;

		private @Nullable String quality;

		private @Nullable String responseFormat;

		private @Nullable String size;

		private @Nullable String style;

		private @Nullable String user;

		private Builder() {
		}

		public Builder from(OpenAiImageOptions fromOptions) {
			// Parent class fields
			this.baseUrl = fromOptions.getBaseUrl();
			this.apiKey = fromOptions.getApiKey();
			this.credential = fromOptions.getCredential();
			this.model = fromOptions.getModel();
			this.microsoftDeploymentName = fromOptions.getDeploymentName();
			this.microsoftFoundryServiceVersion = fromOptions.getMicrosoftFoundryServiceVersion();
			this.organizationId = fromOptions.getOrganizationId();
			this.isMicrosoftFoundry = fromOptions.isMicrosoftFoundry();
			this.isGitHubModels = fromOptions.isGitHubModels();
			this.timeout = fromOptions.getTimeout();
			this.maxRetries = fromOptions.getMaxRetries();
			this.proxy = fromOptions.getProxy();
			this.customHeaders = fromOptions.getCustomHeaders();
			// Child class fields
			this.n = fromOptions.getN();
			this.width = fromOptions.getWidth();
			this.height = fromOptions.getHeight();
			this.quality = fromOptions.getQuality();
			this.responseFormat = fromOptions.getResponseFormat();
			this.size = fromOptions.getSize();
			this.style = fromOptions.getStyle();
			this.user = fromOptions.getUser();
			return this;
		}

		public Builder merge(@Nullable ImageOptions from) {
			if (from == null) {
				return this;
			}
			if (from instanceof OpenAiImageOptions castFrom) {
				// Parent class fields
				if (castFrom.getBaseUrl() != null) {
					this.baseUrl = castFrom.getBaseUrl();
				}
				if (castFrom.getApiKey() != null) {
					this.apiKey = castFrom.getApiKey();
				}
				if (castFrom.getCredential() != null) {
					this.credential = castFrom.getCredential();
				}
				if (castFrom.getModel() != null) {
					this.model = castFrom.getModel();
				}
				if (castFrom.getDeploymentName() != null) {
					this.microsoftDeploymentName = castFrom.getDeploymentName();
				}
				if (castFrom.getMicrosoftFoundryServiceVersion() != null) {
					this.microsoftFoundryServiceVersion = castFrom.getMicrosoftFoundryServiceVersion();
				}
				if (castFrom.getOrganizationId() != null) {
					this.organizationId = castFrom.getOrganizationId();
				}
				this.isMicrosoftFoundry = castFrom.isMicrosoftFoundry();
				this.isGitHubModels = castFrom.isGitHubModels();
				if (castFrom.getTimeout() != null) {
					this.timeout = castFrom.getTimeout();
				}
				this.maxRetries = castFrom.getMaxRetries();
				if (castFrom.getProxy() != null) {
					this.proxy = castFrom.getProxy();
				}
				if (castFrom.getCustomHeaders() != null) {
					this.customHeaders = castFrom.getCustomHeaders();
				}
				// Child class fields
				if (castFrom.getN() != null) {
					this.n = castFrom.getN();
				}
				if (castFrom.getWidth() != null) {
					this.width = castFrom.getWidth();
				}
				if (castFrom.getHeight() != null) {
					this.height = castFrom.getHeight();
				}
				if (castFrom.getQuality() != null) {
					this.quality = castFrom.getQuality();
				}
				if (castFrom.getResponseFormat() != null) {
					this.responseFormat = castFrom.getResponseFormat();
				}
				if (castFrom.getSize() != null) {
					this.size = castFrom.getSize();
				}
				if (castFrom.getStyle() != null) {
					this.style = castFrom.getStyle();
				}
				if (castFrom.getUser() != null) {
					this.user = castFrom.getUser();
				}
			}
			return this;
		}

		public Builder N(Integer n) {
			this.n = n;
			return this;
		}

		public Builder responseFormat(String responseFormat) {
			this.responseFormat = responseFormat;
			return this;
		}

		public Builder width(Integer width) {
			this.width = width;
			if (this.width != null && this.height != null) {
				this.size = this.width + "x" + this.height;
			}
			return this;
		}

		public Builder height(Integer height) {
			this.height = height;
			if (this.width != null && this.height != null) {
				this.size = this.width + "x" + this.height;
			}
			return this;
		}

		public Builder user(String user) {
			this.user = user;
			return this;
		}

		public Builder style(String style) {
			this.style = style;
			return this;
		}

		public Builder quality(String quality) {
			this.quality = quality;
			return this;
		}

		public Builder size(String size) {
			this.size = size;
			return this;
		}

		@Override
		public OpenAiImageOptions build() {
			return new OpenAiImageOptions(this.baseUrl, this.apiKey, this.credential, this.model,
					this.microsoftDeploymentName, this.microsoftFoundryServiceVersion, this.organizationId,
					this.isMicrosoftFoundry, this.isGitHubModels, this.timeout, this.maxRetries, this.proxy,
					this.customHeaders, this.n, this.width, this.height, this.quality, this.responseFormat, this.size,
					this.style, this.user);
		}

	}

}
