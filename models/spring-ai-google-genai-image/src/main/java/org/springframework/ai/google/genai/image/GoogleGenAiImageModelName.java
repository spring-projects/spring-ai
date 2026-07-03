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

package org.springframework.ai.google.genai.image;

import org.springframework.ai.model.ModelDescription;

/**
 * Known Google GenAI image generation model names.
 *
 * @author Olivier Le Quellec
 * @since 1.1.0
 */
public enum GoogleGenAiImageModelName implements ModelDescription {

	GEMINI_2_5_FLASH_IMAGE("gemini-2.5-flash-image", "Gemini 2.5 Flash Image Model"),

	GEMINI_3_PRO_IMAGE("gemini-3-pro-image", "Gemini 3 Pro Image Model"),

	GEMINI_3_1_FLASH_IMAGE("gemini-3.1-flash-image", "Gemini 3.1 Flash Image Model");

	private final String modelName;

	private final String description;

	GoogleGenAiImageModelName(String value, String description) {
		this.modelName = value;
		this.description = description;
	}

	@Override
	public String getName() {
		return this.modelName;
	}

	@Override
	public String getDescription() {
		return this.description;
	}

}
