/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.image.observation;

import org.junit.jupiter.api.Test;
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
			.imagePrompt(generateImagePrompt())
			.provider("superprovider")
			.requestOptions(ImageOptionsBuilder.builder().withModel("supersun").build())
			.build();

		assertThat(observationContext).isNotNull();
	}

	@Test
	void whenRequestOptionsIsNullThenThrow() {
		assertThatThrownBy(() -> ImageModelObservationContext.builder()
			.imagePrompt(generateImagePrompt())
			.provider("superprovider")
			.requestOptions(null)
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("requestOptions cannot be null");
	}

	private ImagePrompt generateImagePrompt() {
		return new ImagePrompt("here comes the sun");
	}

}
