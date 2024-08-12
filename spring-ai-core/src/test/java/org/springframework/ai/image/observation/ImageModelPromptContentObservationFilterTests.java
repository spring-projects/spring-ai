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

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import org.junit.jupiter.api.Test;
import org.springframework.ai.image.ImageMessage;
import org.springframework.ai.image.ImageOptionsBuilder;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.observation.conventions.AiObservationAttributes;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ImageModelPromptContentObservationFilter}.
 *
 * @author Thomas Vitale
 */
class ImageModelPromptContentObservationFilterTests {

	private final ImageModelPromptContentObservationFilter observationFilter = new ImageModelPromptContentObservationFilter();

	@Test
	void whenNotSupportedObservationContextThenReturnOriginalContext() {
		var expectedContext = new Observation.Context();
		var actualContext = observationFilter.map(expectedContext);

		assertThat(actualContext).isEqualTo(expectedContext);
	}

	@Test
	void whenEmptyPromptThenReturnOriginalContext() {
		var expectedContext = ImageModelObservationContext.builder()
			.imagePrompt(new ImagePrompt(""))
			.provider("superprovider")
			.requestOptions(ImageOptionsBuilder.builder().withModel("mistral").build())
			.build();
		var actualContext = observationFilter.map(expectedContext);

		assertThat(actualContext).isEqualTo(expectedContext);
	}

	@Test
	void whenPromptWithTextThenAugmentContext() {
		var originalContext = ImageModelObservationContext.builder()
			.imagePrompt(new ImagePrompt("supercalifragilisticexpialidocious"))
			.provider("superprovider")
			.requestOptions(ImageOptionsBuilder.builder().withModel("mistral").build())
			.build();
		var augmentedContext = observationFilter.map(originalContext);

		assertThat(augmentedContext.getHighCardinalityKeyValues())
			.contains(KeyValue.of(AiObservationAttributes.PROMPT.value(), "[\"supercalifragilisticexpialidocious\"]"));
	}

	@Test
	void whenPromptWithMessagesThenAugmentContext() {
		var originalContext = ImageModelObservationContext.builder()
			.imagePrompt(new ImagePrompt(List.of(new ImageMessage("you're a chimney sweep"),
					new ImageMessage("supercalifragilisticexpialidocious"))))
			.provider("superprovider")
			.requestOptions(ImageOptionsBuilder.builder().withModel("mistral").build())
			.build();
		var augmentedContext = observationFilter.map(originalContext);

		assertThat(augmentedContext.getHighCardinalityKeyValues())
			.contains(KeyValue.of(AiObservationAttributes.PROMPT.value(),
					"[\"you're a chimney sweep\", \"supercalifragilisticexpialidocious\"]"));
	}

}
