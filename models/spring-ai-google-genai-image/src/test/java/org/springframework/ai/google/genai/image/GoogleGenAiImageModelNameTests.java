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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link GoogleGenAiImageModelName}.
 *
 * @author Olivier Le Quellec
 */
class GoogleGenAiImageModelNameTests {

	@Test
	void hasExpectedNumberOfModels() {
		assertThat(GoogleGenAiImageModelName.values()).hasSize(3);
	}

	@Test
	void gemini25FlashImageHasExpectedNameAndDescription() {
		assertThat(GoogleGenAiImageModelName.GEMINI_2_5_FLASH_IMAGE.getName()).isEqualTo("gemini-2.5-flash-image");
		assertThat(GoogleGenAiImageModelName.GEMINI_2_5_FLASH_IMAGE.getDescription())
			.isEqualTo("Gemini 2.5 Flash Image Model");
	}

	@Test
	void gemini3ProImageHasExpectedNameAndDescription() {
		assertThat(GoogleGenAiImageModelName.GEMINI_3_PRO_IMAGE.getName()).isEqualTo("gemini-3-pro-image");
		assertThat(GoogleGenAiImageModelName.GEMINI_3_PRO_IMAGE.getDescription()).isEqualTo("Gemini 3 Pro Image Model");
	}

	@Test
	void gemini31FlashImageHasExpectedNameAndDescription() {
		assertThat(GoogleGenAiImageModelName.GEMINI_3_1_FLASH_IMAGE.getName()).isEqualTo("gemini-3.1-flash-image");
		assertThat(GoogleGenAiImageModelName.GEMINI_3_1_FLASH_IMAGE.getDescription())
			.isEqualTo("Gemini 3.1 Flash Image Model");
	}

	@Test
	void valueOfReturnsMatchingConstant() {
		assertThat(GoogleGenAiImageModelName.valueOf("GEMINI_3_PRO_IMAGE"))
			.isEqualTo(GoogleGenAiImageModelName.GEMINI_3_PRO_IMAGE);
	}

}
