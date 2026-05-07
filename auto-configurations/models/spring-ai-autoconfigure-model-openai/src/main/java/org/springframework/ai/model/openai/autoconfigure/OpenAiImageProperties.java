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

package org.springframework.ai.model.openai.autoconfigure;

import com.openai.models.images.ImageModel;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.openai.OpenAiImageOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;

/**
 * OpenAI SDK Image autoconfiguration properties.
 *
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author lambochen
 */
@ConfigurationProperties(OpenAiImageProperties.CONFIG_PREFIX)
public class OpenAiImageProperties extends AbstractOpenAiProperties {

	public static final String CONFIG_PREFIX = "spring.ai.openai.image";

	public static final String DEFAULT_IMAGE_MODEL = ImageModel.DALL_E_3.toString();

	private @Nullable String model = DEFAULT_IMAGE_MODEL;

	private @Nullable Integer n;

	private @Nullable Integer width;

	private @Nullable Integer height;

	private @Nullable String quality;

	private @Nullable String responseFormat;

	private @Nullable String size;

	private @Nullable String style;

	private @Nullable String user;

	public @Nullable String getModel() {
		return this.model;
	}

	public void setModel(@Nullable String model) {
		this.model = model;
	}

	public @Nullable Integer getN() {
		return this.n;
	}

	public void setN(@Nullable Integer n) {
		this.n = n;
	}

	public @Nullable Integer getWidth() {
		return this.width;
	}

	public void setWidth(@Nullable Integer width) {
		this.width = width;
	}

	public @Nullable Integer getHeight() {
		return this.height;
	}

	public void setHeight(@Nullable Integer height) {
		this.height = height;
	}

	public @Nullable String getQuality() {
		return this.quality;
	}

	public void setQuality(@Nullable String quality) {
		this.quality = quality;
	}

	public @Nullable String getResponseFormat() {
		return this.responseFormat;
	}

	public void setResponseFormat(@Nullable String responseFormat) {
		this.responseFormat = responseFormat;
	}

	public @Nullable String getSize() {
		return this.size;
	}

	public void setSize(@Nullable String size) {
		this.size = size;
	}

	public @Nullable String getStyle() {
		return this.style;
	}

	public void setStyle(@Nullable String style) {
		this.style = style;
	}

	public @Nullable String getUser() {
		return this.user;
	}

	public void setUser(@Nullable String user) {
		this.user = user;
	}

	public OpenAiImageOptions toOptions() {
		OpenAiImageOptions.Builder builder = OpenAiImageOptions.builder();
		if (this.getModel() != null) {
			builder.model(this.getModel());
		}
		if (this.n != null) {
			builder.N(this.n);
		}
		if (this.width != null) {
			builder.width(this.width);
		}
		if (this.height != null) {
			builder.height(this.height);
		}
		if (this.responseFormat != null) {
			builder.responseFormat(this.responseFormat);
		}
		if (this.style != null) {
			builder.style(this.style);
		}
		if (this.user != null) {
			builder.user(this.user);
		}
		if (this.quality != null) {
			builder.quality(this.quality);
		}
		if (this.size != null) {
			builder.size(this.size);
		}
		return builder.build();
	}

	private Options options = new Options();

	@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.image")
	@Deprecated(since = "2.0.0", forRemoval = true)
	public Options getOptions() {
		return this.options;
	}

	public void setOptions(Options options) {
		this.options = options;
	}

	public class Options {

		@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.image.model")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable String getModel() {
			return OpenAiImageProperties.this.getModel();
		}

		public void setModel(@Nullable String model) {
			OpenAiImageProperties.this.setModel(model);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.image.n")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Integer getN() {
			return OpenAiImageProperties.this.getN();
		}

		public void setN(@Nullable Integer n) {
			OpenAiImageProperties.this.setN(n);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.image.width")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Integer getWidth() {
			return OpenAiImageProperties.this.getWidth();
		}

		public void setWidth(@Nullable Integer width) {
			OpenAiImageProperties.this.setWidth(width);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.image.height")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Integer getHeight() {
			return OpenAiImageProperties.this.getHeight();
		}

		public void setHeight(@Nullable Integer height) {
			OpenAiImageProperties.this.setHeight(height);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.image.quality")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable String getQuality() {
			return OpenAiImageProperties.this.getQuality();
		}

		public void setQuality(@Nullable String quality) {
			OpenAiImageProperties.this.setQuality(quality);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.image.response-format")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable String getResponseFormat() {
			return OpenAiImageProperties.this.getResponseFormat();
		}

		public void setResponseFormat(@Nullable String responseFormat) {
			OpenAiImageProperties.this.setResponseFormat(responseFormat);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.image.size")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable String getSize() {
			return OpenAiImageProperties.this.getSize();
		}

		public void setSize(@Nullable String size) {
			OpenAiImageProperties.this.setSize(size);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.image.style")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable String getStyle() {
			return OpenAiImageProperties.this.getStyle();
		}

		public void setStyle(@Nullable String style) {
			OpenAiImageProperties.this.setStyle(style);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.image.user")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable String getUser() {
			return OpenAiImageProperties.this.getUser();
		}

		public void setUser(@Nullable String user) {
			OpenAiImageProperties.this.setUser(user);
		}

	}

}
