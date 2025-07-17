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

package org.springframework.ai.model.openai.autoconfigure;

import org.springframework.ai.openai.OpenAiImageOptions;
import org.springframework.ai.openai.api.OpenAiImageApi;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * OpenAI Image autoconfiguration properties.
 *
 * @author Thomas Vitale
 * @author lambochen
 * @since 0.8.0
 */
@ConfigurationProperties(OpenAiImageProperties.CONFIG_PREFIX)
public class OpenAiImageProperties extends OpenAiParentProperties {

	public static final String CONFIG_PREFIX = "spring.ai.openai.image";

	public static final String DEFAULT_IMAGES_PATH = "v1/images/generations";

	private String imagesPath = DEFAULT_IMAGES_PATH;

	public static final String DEFAULT_IMAGE_MODEL = OpenAiImageApi.ImageModel.DALL_E_3.getValue();

	/**
	 * Options for OpenAI Image API.
	 */
	@NestedConfigurationProperty
	private OpenAiImageOptions options = OpenAiImageOptions.builder().model(DEFAULT_IMAGE_MODEL).build();

	public OpenAiImageOptions getOptions() {
		return this.options;
	}

	public void setOptions(OpenAiImageOptions options) {
		this.options = options;
	}

	public String getImagesPath() {
		return this.imagesPath;
	}

	public void setImagesPath(String imagesPath) {
		this.imagesPath = imagesPath;
	}

}
