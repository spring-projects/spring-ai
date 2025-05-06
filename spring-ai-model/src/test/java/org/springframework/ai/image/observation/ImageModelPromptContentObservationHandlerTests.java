/*
 * Copyright 2023-2025 the original author or authors.
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

import io.micrometer.observation.Observation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.ai.image.ImageMessage;
import org.springframework.ai.image.ImageOptionsBuilder;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ImageModelPromptContentObservationHandler}.
 *
 * @author Thomas Vitale
 * @author Jonatan Ivanov
 */
@ExtendWith(OutputCaptureExtension.class)
class ImageModelPromptContentObservationHandlerTests {

	private final ImageModelPromptContentObservationHandler observationHandler = new ImageModelPromptContentObservationHandler();

	@Test
	void whenNotSupportedObservationContextThenReturnFalse() {
		var context = new Observation.Context();
		assertThat(this.observationHandler.supportsContext(context)).isFalse();
	}

	@Test
	void whenSupportedObservationContextThenReturnTrue() {
		var context = ImageModelObservationContext.builder()
			.imagePrompt(new ImagePrompt("", ImageOptionsBuilder.builder().model("mistral").build()))
			.provider("superprovider")
			.build();
		assertThat(this.observationHandler.supportsContext(context)).isTrue();
	}

	@Test
	void whenEmptyPromptThenOutputNothing(CapturedOutput output) {
		var context = ImageModelObservationContext.builder()
			.imagePrompt(new ImagePrompt("", ImageOptionsBuilder.builder().model("mistral").build()))
			.provider("superprovider")
			.build();
		observationHandler.onStop(context);
		assertThat(output).contains("""
				Image Model Prompt Content:
				[""]
				""");
	}

	@Test
	void whenPromptWithTextThenOutputIt(CapturedOutput output) {
		var context = ImageModelObservationContext.builder()
			.imagePrompt(new ImagePrompt("supercalifragilisticexpialidocious",
					ImageOptionsBuilder.builder().model("mistral").build()))
			.provider("superprovider")
			.build();
		observationHandler.onStop(context);
		assertThat(output).contains("""
				Image Model Prompt Content:
				["supercalifragilisticexpialidocious"]
				""");
	}

	@Test
	void whenPromptWithMessagesThenOutputIt(CapturedOutput output) {
		var context = ImageModelObservationContext.builder()
			.imagePrompt(new ImagePrompt(
					List.of(new ImageMessage("you're a chimney sweep"),
							new ImageMessage("supercalifragilisticexpialidocious")),
					ImageOptionsBuilder.builder().model("mistral").build()))
			.provider("superprovider")
			.build();
		observationHandler.onStop(context);
		assertThat(output).contains("""
				Image Model Prompt Content:
				["you're a chimney sweep", "supercalifragilisticexpialidocious"]
				""");
	}

}
