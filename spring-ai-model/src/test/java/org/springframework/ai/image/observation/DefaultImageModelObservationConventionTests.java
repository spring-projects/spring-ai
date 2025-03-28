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

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import org.junit.jupiter.api.Test;

import org.springframework.ai.image.ImageOptionsBuilder;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.observation.DefaultImageModelObservationConvention;
import org.springframework.ai.image.observation.ImageModelObservationContext;
import org.springframework.ai.observation.conventions.AiObservationAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.ai.image.observation.ImageModelObservationDocumentation.HighCardinalityKeyNames;

/**
 * Unit tests for {@link DefaultImageModelObservationConvention}.
 *
 * @author Thomas Vitale
 */
class DefaultImageModelObservationConventionTests {

	private final DefaultImageModelObservationConvention observationConvention = new DefaultImageModelObservationConvention();

	@Test
	void shouldHaveName() {
		assertThat(this.observationConvention.getName()).isEqualTo(DefaultImageModelObservationConvention.DEFAULT_NAME);
	}

	@Test
	void contextualNameWhenModelIsDefined() {
		ImageModelObservationContext observationContext = ImageModelObservationContext.builder()
			.imagePrompt(generateImagePrompt())
			.provider("superprovider")
			.requestOptions(ImageOptionsBuilder.builder().model("mistral").build())
			.build();
		assertThat(this.observationConvention.getContextualName(observationContext)).isEqualTo("image mistral");
	}

	@Test
	void contextualNameWhenModelIsNotDefined() {
		ImageModelObservationContext observationContext = ImageModelObservationContext.builder()
			.imagePrompt(generateImagePrompt())
			.provider("superprovider")
			.requestOptions(ImageOptionsBuilder.builder().build())
			.build();
		assertThat(this.observationConvention.getContextualName(observationContext)).isEqualTo("image");
	}

	@Test
	void supportsOnlyImageModelObservationContext() {
		ImageModelObservationContext observationContext = ImageModelObservationContext.builder()
			.imagePrompt(generateImagePrompt())
			.provider("superprovider")
			.requestOptions(ImageOptionsBuilder.builder().model("mistral").build())
			.build();
		assertThat(this.observationConvention.supportsContext(observationContext)).isTrue();
		assertThat(this.observationConvention.supportsContext(new Observation.Context())).isFalse();
	}

	@Test
	void shouldHaveLowCardinalityKeyValuesWhenDefined() {
		ImageModelObservationContext observationContext = ImageModelObservationContext.builder()
			.imagePrompt(generateImagePrompt())
			.provider("superprovider")
			.requestOptions(ImageOptionsBuilder.builder().model("mistral").build())
			.build();
		assertThat(this.observationConvention.getLowCardinalityKeyValues(observationContext)).contains(
				KeyValue.of(AiObservationAttributes.AI_OPERATION_TYPE.value(), "image"),
				KeyValue.of(AiObservationAttributes.AI_PROVIDER.value(), "superprovider"),
				KeyValue.of(AiObservationAttributes.REQUEST_MODEL.value(), "mistral"));
	}

	@Test
	void shouldHaveHighCardinalityKeyValuesWhenDefined() {
		ImageModelObservationContext observationContext = ImageModelObservationContext.builder()
			.imagePrompt(generateImagePrompt())
			.provider("superprovider")
			.requestOptions(ImageOptionsBuilder.builder()
				.model("mistral")
				.N(1)
				.height(1080)
				.width(1920)
				.style("sketch")
				.responseFormat("base64")
				.build())
			.build();

		assertThat(this.observationConvention.getHighCardinalityKeyValues(observationContext)).contains(
				KeyValue.of(AiObservationAttributes.REQUEST_IMAGE_RESPONSE_FORMAT.value(), "base64"),
				KeyValue.of(AiObservationAttributes.REQUEST_IMAGE_SIZE.value(), "1920x1080"),
				KeyValue.of(AiObservationAttributes.REQUEST_IMAGE_STYLE.value(), "sketch"));
	}

	@Test
	void shouldNotHaveKeyValuesWhenEmptyValues() {
		ImageModelObservationContext observationContext = ImageModelObservationContext.builder()
			.imagePrompt(generateImagePrompt())
			.provider("superprovider")
			.requestOptions(ImageOptionsBuilder.builder().build())
			.build();

		assertThat(this.observationConvention.getLowCardinalityKeyValues(observationContext))
			.contains(KeyValue.of(AiObservationAttributes.REQUEST_MODEL.value(), KeyValue.NONE_VALUE));
		assertThat(this.observationConvention.getHighCardinalityKeyValues(observationContext)
			.stream()
			.map(KeyValue::getKey)
			.toList()).doesNotContain(HighCardinalityKeyNames.REQUEST_IMAGE_RESPONSE_FORMAT.asString(),
					HighCardinalityKeyNames.REQUEST_IMAGE_SIZE.asString(),
					HighCardinalityKeyNames.REQUEST_IMAGE_STYLE.asString());
	}

	private ImagePrompt generateImagePrompt() {
		return new ImagePrompt("here comes the sun");
	}

}
