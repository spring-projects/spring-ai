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
 * Unit tests for {@link GoogleGenAiImageGenerationMetadata}.
 *
 * @author Olivier Le Quellec
 */
class GoogleGenAiImageGenerationMetadataTests {

	@Test
	void gettersReturnConstructorValues() {
		GoogleGenAiImageGenerationMetadata metadata = new GoogleGenAiImageGenerationMetadata("enhanced prompt",
				"rai reason", "image/png", "gs://bucket/file.png");

		assertThat(metadata.getEnhancedPrompt()).isEqualTo("enhanced prompt");
		assertThat(metadata.getRaiFilteredReason()).isEqualTo("rai reason");
		assertThat(metadata.getMimeType()).isEqualTo("image/png");
		assertThat(metadata.getGcsUri()).isEqualTo("gs://bucket/file.png");
	}

	@Test
	void gettersReturnNullWhenNotSet() {
		GoogleGenAiImageGenerationMetadata metadata = new GoogleGenAiImageGenerationMetadata(null, null, null, null);

		assertThat(metadata.getEnhancedPrompt()).isNull();
		assertThat(metadata.getRaiFilteredReason()).isNull();
		assertThat(metadata.getMimeType()).isNull();
		assertThat(metadata.getGcsUri()).isNull();
	}

	@Test
	void equalsAndHashCodeForEqualObjects() {
		GoogleGenAiImageGenerationMetadata a = new GoogleGenAiImageGenerationMetadata("p", "r", "m", "g");
		GoogleGenAiImageGenerationMetadata b = new GoogleGenAiImageGenerationMetadata("p", "r", "m", "g");

		assertThat(a).isEqualTo(b);
		assertThat(a).hasSameHashCodeAs(b);
	}

	@Test
	void equalsReturnsTrueForSameReference() {
		GoogleGenAiImageGenerationMetadata a = new GoogleGenAiImageGenerationMetadata("p", "r", "m", "g");
		assertThat(a).isEqualTo(a);
	}

	@Test
	void equalsReturnsFalseForNullOrDifferentClass() {
		GoogleGenAiImageGenerationMetadata a = new GoogleGenAiImageGenerationMetadata("p", "r", "m", "g");
		assertThat(a).isNotEqualTo(null);
		assertThat(a).isNotEqualTo("not metadata");
	}

	@Test
	void equalsReturnsFalseForDifferentFieldValues() {
		GoogleGenAiImageGenerationMetadata base = new GoogleGenAiImageGenerationMetadata("p", "r", "m", "g");

		assertThat(base).isNotEqualTo(new GoogleGenAiImageGenerationMetadata("other", "r", "m", "g"));
		assertThat(base).isNotEqualTo(new GoogleGenAiImageGenerationMetadata("p", "other", "m", "g"));
		assertThat(base).isNotEqualTo(new GoogleGenAiImageGenerationMetadata("p", "r", "other", "g"));
		assertThat(base).isNotEqualTo(new GoogleGenAiImageGenerationMetadata("p", "r", "m", "other"));
	}

	@Test
	void toStringContainsAllFields() {
		GoogleGenAiImageGenerationMetadata metadata = new GoogleGenAiImageGenerationMetadata("p", "r", "m", "g");

		assertThat(metadata.toString()).contains("p").contains("r").contains("m").contains("g");
	}

}
