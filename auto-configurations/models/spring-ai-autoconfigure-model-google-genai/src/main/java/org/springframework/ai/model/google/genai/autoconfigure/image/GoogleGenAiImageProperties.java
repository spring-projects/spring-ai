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

package org.springframework.ai.model.google.genai.autoconfigure.image;

import org.springframework.ai.google.genai.GoogleGenAiImageOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import static org.springframework.ai.google.genai.GoogleGenAiImageOptions.Models.GEMINI_2_5_FLASH_IMAGE;

/**
 * Configuration properties for Google GenAI Image.
 *
 * @author Danil Temnikov
 */
@ConfigurationProperties(GoogleGenAiImageProperties.CONFIG_PREFIX)
public class GoogleGenAiImageProperties {

	public static final String CONFIG_PREFIX = "spring.ai.google.genai.image";

	public static final String DEFAULT_MODEL = GEMINI_2_5_FLASH_IMAGE;

	/**
	 * Google GenAI API generative options.
	 */
	@NestedConfigurationProperty
	private final GoogleGenAiImageOptions options = GoogleGenAiImageOptions.defaults();

	public GoogleGenAiImageOptions getOptions() {
		return this.options;
	}

}
