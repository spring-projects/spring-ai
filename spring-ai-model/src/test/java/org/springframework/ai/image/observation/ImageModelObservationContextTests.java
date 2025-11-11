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

package org.springframework.ai.image.observation;

import org.junit.jupiter.api.Test;

import org.springframework.ai.image.ImageOptions;
import org.springframework.ai.image.ImageOptionsBuilder;
import org.springframework.ai.image.ImagePrompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ImageModelObservationContext}.
 *
 * @author Thomas Vitale
 */
class ImageModelObservationContextTests {

	@Test
	void whenMandatoryRequestOptionsThenReturn() {
		var observationContext = ImageModelObservationContext.builder()
			.imagePrompt(generateImagePrompt(ImageOptionsBuilder.builder().model("supersun").build()))
			.provider("superprovider")
			.build();

		assertThat(observationContext).isNotNull();
	}

	@Test
	void shouldBuildContextWithImageOptions() {
		var imageOptions = ImageOptionsBuilder.builder().model("test-model").build();
		var imagePrompt = new ImagePrompt("test prompt", imageOptions);

		var observationContext = ImageModelObservationContext.builder()
			.imagePrompt(imagePrompt)
			.provider("test-provider")
			.build();

		assertThat(observationContext).isNotNull();
	}

	@Test
	void shouldThrowExceptionWhenImagePromptIsNull() {
		assertThatThrownBy(
				() -> ImageModelObservationContext.builder().imagePrompt(null).provider("test-provider").build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("request cannot be null");
	}

	@Test
	void shouldThrowExceptionWhenProviderIsNull() {
		var imagePrompt = new ImagePrompt("test prompt");

		assertThatThrownBy(() -> ImageModelObservationContext.builder().imagePrompt(imagePrompt).provider(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("provider cannot be null or empty");
	}

	@Test
	void shouldThrowExceptionWhenProviderIsEmpty() {
		var imagePrompt = new ImagePrompt("test prompt");

		assertThatThrownBy(() -> ImageModelObservationContext.builder().imagePrompt(imagePrompt).provider("").build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("provider cannot be null or empty");
	}

	@Test
	void shouldThrowExceptionWhenProviderIsBlank() {
		var imagePrompt = new ImagePrompt("test prompt");

		assertThatThrownBy(
				() -> ImageModelObservationContext.builder().imagePrompt(imagePrompt).provider("   ").build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("provider cannot be null or empty");
	}

	@Test
	void shouldBuildMultipleContextsIndependently() {
		var imagePrompt1 = new ImagePrompt("first prompt");
		var imagePrompt2 = new ImagePrompt("second prompt");

		var context1 = ImageModelObservationContext.builder()
			.imagePrompt(imagePrompt1)
			.provider("provider-alpha")
			.build();

		var context2 = ImageModelObservationContext.builder()
			.imagePrompt(imagePrompt2)
			.provider("provider-beta")
			.build();

		assertThat(context1).isNotNull();
		assertThat(context2).isNotNull();
		assertThat(context1).isNotEqualTo(context2);
	}

	private ImagePrompt generateImagePrompt(ImageOptions imageOptions) {
		return new ImagePrompt("here comes the sun");
	}

}
