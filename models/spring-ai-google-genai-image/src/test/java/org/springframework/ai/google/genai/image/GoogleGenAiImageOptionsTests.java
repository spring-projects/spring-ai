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

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link GoogleGenAiImageOptions}.
 *
 * @author Olivier Le Quellec
 */
class GoogleGenAiImageOptionsTests {

	@Test
	void buildsAllFields() {
		GoogleGenAiImageOptions options = GoogleGenAiImageOptions.builder()
			.model(GoogleGenAiImageModelName.IMAGEN_4_0_GENERATE)
			.n(2)
			.outputGcsUri("gs://bucket/out")
			.negativePrompt("blurry")
			.aspectRatio("16:9")
			.guidanceScale(5.0f)
			.seed(42)
			.safetyFilterLevel(GoogleGenAiImageOptions.SafetyFilterLevel.BLOCK_ONLY_HIGH)
			.personGeneration(GoogleGenAiImageOptions.PersonGeneration.ALLOW_ADULT)
			.includeSafetyAttributes(true)
			.includeRaiReason(true)
			.language("en")
			.outputMimeType("image/png")
			.outputCompressionQuality(80)
			.addWatermark(true)
			.labels(Map.of("env", "test"))
			.imageSize("2K")
			.enhancePrompt(true)
			.build();

		assertThat(options.getModel()).isEqualTo("imagen-4.0-generate-001");
		assertThat(options.getN()).isEqualTo(2);
		assertThat(options.getOutputGcsUri()).isEqualTo("gs://bucket/out");
		assertThat(options.getNegativePrompt()).isEqualTo("blurry");
		assertThat(options.getAspectRatio()).isEqualTo("16:9");
		assertThat(options.getGuidanceScale()).isEqualTo(5.0f);
		assertThat(options.getSeed()).isEqualTo(42);
		assertThat(options.getSafetyFilterLevel()).isEqualTo(GoogleGenAiImageOptions.SafetyFilterLevel.BLOCK_ONLY_HIGH);
		assertThat(options.getPersonGeneration()).isEqualTo(GoogleGenAiImageOptions.PersonGeneration.ALLOW_ADULT);
		assertThat(options.getIncludeSafetyAttributes()).isTrue();
		assertThat(options.getIncludeRaiReason()).isTrue();
		assertThat(options.getLanguage()).isEqualTo("en");
		assertThat(options.getOutputMimeType()).isEqualTo("image/png");
		assertThat(options.getResponseFormat()).isEqualTo("image/png");
		assertThat(options.getOutputCompressionQuality()).isEqualTo(80);
		assertThat(options.getAddWatermark()).isTrue();
		assertThat(options.getLabels()).containsEntry("env", "test");
		assertThat(options.getImageSize()).isEqualTo("2K");
		assertThat(options.getEnhancePrompt()).isTrue();
		assertThat(options.getWidth()).isNull();
		assertThat(options.getHeight()).isNull();
		assertThat(options.getStyle()).isNull();
	}

	@Test
	void fromCopiesAllValues() {
		GoogleGenAiImageOptions source = GoogleGenAiImageOptions.builder()
			.model("imagen-4.0-fast-generate-001")
			.n(3)
			.aspectRatio("4:3")
			.seed(7)
			.build();

		GoogleGenAiImageOptions copy = GoogleGenAiImageOptions.builder().from(source).build();

		assertThat(copy.getModel()).isEqualTo("imagen-4.0-fast-generate-001");
		assertThat(copy.getN()).isEqualTo(3);
		assertThat(copy.getAspectRatio()).isEqualTo("4:3");
		assertThat(copy.getSeed()).isEqualTo(7);
	}

	@Test
	void equalsAndHashCode() {
		GoogleGenAiImageOptions a = GoogleGenAiImageOptions.builder().model("m").n(1).build();
		GoogleGenAiImageOptions b = GoogleGenAiImageOptions.builder().model("m").n(1).build();
		assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
	}

}
