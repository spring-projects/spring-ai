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

/**
 * Known Google GenAI Imagen image generation model names.
 *
 * @author Olivier Le Quellec
 * @since 1.1.0
 */
public enum GoogleGenAiImageModelName {

	/**
	 * Standard Imagen 4 image generation model.
	 */
	IMAGEN_4_0_GENERATE("imagen-4.0-generate-001"),

	/**
	 * High-fidelity Imagen 4 Ultra image generation model.
	 */
	IMAGEN_4_0_ULTRA_GENERATE("imagen-4.0-ultra-generate-001"),

	/**
	 * Fast variant of Imagen 4 for low-latency use cases.
	 */
	IMAGEN_4_0_FAST_GENERATE("imagen-4.0-fast-generate-001");

	private final String value;

	GoogleGenAiImageModelName(String value) {
		this.value = value;
	}

	public String getValue() {
		return this.value;
	}

}
