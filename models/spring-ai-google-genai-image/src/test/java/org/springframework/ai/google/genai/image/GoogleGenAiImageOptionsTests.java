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

import org.springframework.ai.image.ImageOptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GoogleGenAiImageOptions}.
 *
 * @author Olivier Le Quellec
 */
class GoogleGenAiImageOptionsTests {

	@Test
	void builderDoesNotBakeInDefaultModelWhenNullOrNotProvided() {
		// GoogleGenAiImageOptions no longer bakes in a default model at construction
		// time: this is what allows GoogleGenAiImageModel to correctly distinguish
		// "no model requested" from "an explicit model was requested" when merging
		// per-request options with the model-level default. See
		// GoogleGenAiImageRetryTests#googleGenAiImagePerRequestOptionsDoNotOverrideModelLevelDefaultModel.
		GoogleGenAiImageOptions options = GoogleGenAiImageOptions.builder().build();
		assertThat(options.getModel()).isNull();

		GoogleGenAiImageOptions optionsWithNullModel = GoogleGenAiImageOptions.builder().model((String) null).build();
		assertThat(optionsWithNullModel.getModel()).isNull();
	}

	@Test
	void builderKeepsEmptyModelAsIs() {
		// An explicitly empty model is preserved (not replaced by the default), this is
		// what triggers the "model cannot be null or empty" validation downstream.
		GoogleGenAiImageOptions options = GoogleGenAiImageOptions.builder().model("").build();
		assertThat(options.getModel()).isEmpty();
	}

	@Test
	void builderSetsAllFieldsViaStringModel() {
		Map<String, String> labels = Map.of("env", "test");
		GoogleGenAiImageOptions options = GoogleGenAiImageOptions.builder()
			.model("gemini-2.5-flash-image")
			.n(2)
			.aspectRatio("16:9")
			.seed(42)
			.safetyFilterLevel(GoogleGenAiImageOptions.SafetyFilterLevel.BLOCK_ONLY_HIGH)
			.personGeneration(GoogleGenAiImageOptions.PersonGeneration.ALLOW_ADULT)
			.outputMimeType("image/png")
			.outputCompressionQuality(80)
			.labels(labels)
			.imageSize("2K")
			.temperature(0.7f)
			.topP(0.9f)
			.topK(40f)
			.maxOutputTokens(1024)
			.build();

		assertThat(options.getModel()).isEqualTo("gemini-2.5-flash-image");
		assertThat(options.getN()).isEqualTo(2);
		assertThat(options.getAspectRatio()).isEqualTo("16:9");
		assertThat(options.getSeed()).isEqualTo(42);
		assertThat(options.getSafetyFilterLevel()).isEqualTo(GoogleGenAiImageOptions.SafetyFilterLevel.BLOCK_ONLY_HIGH);
		assertThat(options.getPersonGeneration()).isEqualTo(GoogleGenAiImageOptions.PersonGeneration.ALLOW_ADULT);
		assertThat(options.getOutputMimeType()).isEqualTo("image/png");
		assertThat(options.getResponseFormat()).isEqualTo("image/png");
		assertThat(options.getOutputCompressionQuality()).isEqualTo(80);
		assertThat(options.getLabels()).containsEntry("env", "test");
		assertThat(options.getImageSize()).isEqualTo("2K");
		assertThat(options.getTemperature()).isEqualTo(0.7f);
		assertThat(options.getTopP()).isEqualTo(0.9f);
		assertThat(options.getTopK()).isEqualTo(40f);
		assertThat(options.getMaxOutputTokens()).isEqualTo(1024);
		// Width, height and style are not supported by this model.
		assertThat(options.getWidth()).isNull();
		assertThat(options.getHeight()).isNull();
		assertThat(options.getStyle()).isNull();
	}

	@Test
	void builderSetsModelViaEnum() {
		GoogleGenAiImageOptions options = GoogleGenAiImageOptions.builder()
			.model(GoogleGenAiImageModelName.GEMINI_3_PRO_IMAGE)
			.build();
		assertThat(options.getModel()).isEqualTo(GoogleGenAiImageModelName.GEMINI_3_PRO_IMAGE.getName());
	}

	@Test
	void builderCopiesLabelsDefensively() {
		Map<String, String> mutableLabels = new java.util.HashMap<>(Map.of("k", "v"));
		GoogleGenAiImageOptions options = GoogleGenAiImageOptions.builder().labels(mutableLabels).build();
		mutableLabels.put("k2", "v2");
		assertThat(options.getLabels()).containsOnlyKeys("k");
	}

	@Test
	void builderWithNullLabelsReturnsNull() {
		GoogleGenAiImageOptions options = GoogleGenAiImageOptions.builder().build();
		assertThat(options.getLabels()).isNull();
	}

	// ======= from() tests =======

	@Test
	void fromCopiesAllPresentFields() {
		GoogleGenAiImageOptions source = GoogleGenAiImageOptions.builder()
			.model("gemini-3-pro-image")
			.n(3)
			.aspectRatio("4:3")
			.seed(7)
			.safetyFilterLevel(GoogleGenAiImageOptions.SafetyFilterLevel.BLOCK_NONE)
			.personGeneration(GoogleGenAiImageOptions.PersonGeneration.ALLOW_ALL)
			.outputMimeType("image/jpeg")
			.outputCompressionQuality(90)
			.labels(Map.of("a", "b"))
			.imageSize("1K")
			.temperature(0.5f)
			.topP(0.6f)
			.topK(20f)
			.maxOutputTokens(512)
			.build();

		GoogleGenAiImageOptions copy = GoogleGenAiImageOptions.builder().from(source).build();

		assertThat(copy).isEqualTo(source);
	}

	@Test
	void fromDoesNotOverrideWithBlankOrNullFields() {
		GoogleGenAiImageOptions emptySource = GoogleGenAiImageOptions.builder().model("").build();

		GoogleGenAiImageOptions result = GoogleGenAiImageOptions.builder()
			.model("preset-model")
			.n(5)
			.aspectRatio("preset-ratio")
			.outputMimeType("image/png")
			.imageSize("preset-size")
			.from(emptySource)
			.build();

		// Blank/null fields on the "from" source must not clobber existing values.
		assertThat(result.getModel()).isEqualTo("preset-model");
		assertThat(result.getN()).isEqualTo(5);
		assertThat(result.getAspectRatio()).isEqualTo("preset-ratio");
		assertThat(result.getOutputMimeType()).isEqualTo("image/png");
		assertThat(result.getImageSize()).isEqualTo("preset-size");
	}

	// ======= merge() tests =======

	@Test
	void mergeWithNullReturnsBuilderUnchanged() {
		GoogleGenAiImageOptions.Builder builder = GoogleGenAiImageOptions.builder().model("preset-model");
		GoogleGenAiImageOptions result = builder.merge(null).build();
		assertThat(result.getModel()).isEqualTo("preset-model");
	}

	@Test
	void mergeWithPlainImageOptionsOnlyMergesModelAndN() {
		ImageOptions plainOptions = mock(ImageOptions.class);
		when(plainOptions.getModel()).thenReturn("plain-model");
		when(plainOptions.getN()).thenReturn(3);

		GoogleGenAiImageOptions result = GoogleGenAiImageOptions.builder()
			.aspectRatio("preset-ratio")
			.merge(plainOptions)
			.build();

		assertThat(result.getModel()).isEqualTo("plain-model");
		assertThat(result.getN()).isEqualTo(3);
		// Google-specific fields are untouched because plainOptions is not a
		// GoogleGenAiImageOptions instance.
		assertThat(result.getAspectRatio()).isEqualTo("preset-ratio");
	}

	@Test
	void mergeWithGoogleGenAiImageOptionsMergesAllFields() {
		GoogleGenAiImageOptions from = GoogleGenAiImageOptions.builder()
			.model("gemini-3-pro-image")
			.n(4)
			.aspectRatio("9:16")
			.seed(99)
			.safetyFilterLevel(GoogleGenAiImageOptions.SafetyFilterLevel.BLOCK_MEDIUM_AND_ABOVE)
			.personGeneration(GoogleGenAiImageOptions.PersonGeneration.DONT_ALLOW)
			.outputMimeType("image/webp")
			.outputCompressionQuality(70)
			.labels(Map.of("x", "y"))
			.imageSize("2K")
			.temperature(0.3f)
			.topP(0.4f)
			.topK(10f)
			.maxOutputTokens(256)
			.build();

		GoogleGenAiImageOptions result = GoogleGenAiImageOptions.builder().merge(from).build();

		assertThat(result).isEqualTo(from);
	}

	@Test
	void mergeWithBlankFieldsDoesNotOverride() {
		GoogleGenAiImageOptions from = GoogleGenAiImageOptions.builder().model("").build();

		GoogleGenAiImageOptions result = GoogleGenAiImageOptions.builder()
			.model("preset-model")
			.n(2)
			.aspectRatio("preset-ratio")
			.merge(from)
			.build();

		assertThat(result.getModel()).isEqualTo("preset-model");
		assertThat(result.getN()).isEqualTo(2);
		assertThat(result.getAspectRatio()).isEqualTo("preset-ratio");
	}

	// ======= equals() / hashCode() tests =======

	@Test
	void equalsAndHashCodeForEqualObjects() {
		GoogleGenAiImageOptions a = GoogleGenAiImageOptions.builder().model("m").n(1).build();
		GoogleGenAiImageOptions b = GoogleGenAiImageOptions.builder().model("m").n(1).build();

		assertThat(a).isEqualTo(b);
		assertThat(a).hasSameHashCodeAs(b);
	}

	@Test
	void equalsReturnsTrueForSameReference() {
		GoogleGenAiImageOptions a = GoogleGenAiImageOptions.builder().build();
		assertThat(a).isEqualTo(a);
	}

	@Test
	void equalsReturnsFalseForNullOrDifferentClass() {
		GoogleGenAiImageOptions a = GoogleGenAiImageOptions.builder().build();
		assertThat(a).isNotEqualTo(null);
		assertThat(a).isNotEqualTo("not an options object");
	}

	@Test
	void equalsReturnsFalseForDifferentFieldValues() {
		GoogleGenAiImageOptions base = GoogleGenAiImageOptions.builder().model("m").n(1).build();

		assertThat(base).isNotEqualTo(GoogleGenAiImageOptions.builder().model("other").n(1).build());
		assertThat(base).isNotEqualTo(GoogleGenAiImageOptions.builder().model("m").n(2).build());
		assertThat(base).isNotEqualTo(GoogleGenAiImageOptions.builder().model("m").n(1).aspectRatio("16:9").build());
		assertThat(base).isNotEqualTo(GoogleGenAiImageOptions.builder().model("m").n(1).seed(1).build());
		assertThat(base).isNotEqualTo(GoogleGenAiImageOptions.builder()
			.model("m")
			.n(1)
			.safetyFilterLevel(GoogleGenAiImageOptions.SafetyFilterLevel.BLOCK_NONE)
			.build());
		assertThat(base).isNotEqualTo(GoogleGenAiImageOptions.builder()
			.model("m")
			.n(1)
			.personGeneration(GoogleGenAiImageOptions.PersonGeneration.ALLOW_ALL)
			.build());
		assertThat(base)
			.isNotEqualTo(GoogleGenAiImageOptions.builder().model("m").n(1).outputMimeType("image/png").build());
		assertThat(base)
			.isNotEqualTo(GoogleGenAiImageOptions.builder().model("m").n(1).outputCompressionQuality(1).build());
		assertThat(base)
			.isNotEqualTo(GoogleGenAiImageOptions.builder().model("m").n(1).labels(Map.of("k", "v")).build());
		assertThat(base).isNotEqualTo(GoogleGenAiImageOptions.builder().model("m").n(1).imageSize("1K").build());
		assertThat(base).isNotEqualTo(GoogleGenAiImageOptions.builder().model("m").n(1).temperature(0.1f).build());
		assertThat(base).isNotEqualTo(GoogleGenAiImageOptions.builder().model("m").n(1).topP(0.1f).build());
		assertThat(base).isNotEqualTo(GoogleGenAiImageOptions.builder().model("m").n(1).topK(1f).build());
		assertThat(base).isNotEqualTo(GoogleGenAiImageOptions.builder().model("m").n(1).maxOutputTokens(1).build());
	}

}
