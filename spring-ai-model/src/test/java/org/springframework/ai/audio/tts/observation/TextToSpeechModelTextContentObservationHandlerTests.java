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

package org.springframework.ai.audio.tts.observation;

import io.micrometer.observation.Observation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TextToSpeechModelTextContentObservationHandler}.
 *
 * @author Christian Tzolov
 */
@ExtendWith(OutputCaptureExtension.class)
class TextToSpeechModelTextContentObservationHandlerTests {

	private final TextToSpeechModelTextContentObservationHandler observationHandler = new TextToSpeechModelTextContentObservationHandler();

	private static TextToSpeechModelObservationContext context(String text) {
		return TextToSpeechModelObservationContext.builder()
			.prompt(new TextToSpeechPrompt(text))
			.provider("superprovider")
			.build();
	}

	@Test
	void whenNotSupportedObservationContextThenReturnFalse() {
		assertThat(this.observationHandler.supportsContext(new Observation.Context())).isFalse();
	}

	@Test
	void whenSupportedObservationContextThenReturnTrue() {
		assertThat(this.observationHandler.supportsContext(context("Welcome"))).isTrue();
	}

	@Test
	void whenTextThenOutputText(CapturedOutput output) {
		this.observationHandler.onStop(context("Welcome to the show"));
		assertThat(output).containsIgnoringNewLines("""
				INFO  o.s.a.a.t.o.TextToSpeechModelTextContentObservationHandler -- Text To Speech Model Text Content:
				Welcome to the show
				""");
	}

	@Test
	void whenEmptyTextThenOutputNothing(CapturedOutput output) {
		this.observationHandler.onStop(context(""));
		assertThat(output).doesNotContain("Text To Speech Model Text Content");
	}

}
