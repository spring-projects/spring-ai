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

import org.junit.jupiter.api.Test;

import org.springframework.ai.audio.tts.TextToSpeechOptions;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.observation.conventions.AiOperationType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link TextToSpeechModelObservationContext}.
 *
 * @author Christian Tzolov
 */
class TextToSpeechModelObservationContextTests {

	@Test
	void whenMandatoryFieldsThenReturn() {
		var context = TextToSpeechModelObservationContext.builder()
			.prompt(new TextToSpeechPrompt("Welcome", TextToSpeechOptions.builder().model("tts-1").build()))
			.provider("superprovider")
			.build();

		assertThat(context).isNotNull();
		assertThat(context.isStreaming()).isFalse();
		assertThat(context.getOperationType()).isEqualTo(AiOperationType.TEXT_TO_SPEECH.value());
		assertThat(context.getOperationMetadata().provider()).isEqualTo("superprovider");
		assertThat(context.getRequest().getInstructions().getText()).isEqualTo("Welcome");
	}

	@Test
	void aPromptWithDefaultOptionsIsFine() {
		var context = TextToSpeechModelObservationContext.builder()
			.prompt(new TextToSpeechPrompt("Welcome")) // default options: no model
			.provider("superprovider")
			.build();

		assertThat(context.getRequest().getOptions().getModel()).isNull();
	}

	@Test
	void whenStreamingThenIsStreaming() {
		var context = TextToSpeechModelObservationContext.builder()
			.prompt(new TextToSpeechPrompt("Welcome"))
			.provider("superprovider")
			.streaming(true)
			.build();

		assertThat(context.isStreaming()).isTrue();
	}

	@Test
	void whenPromptIsNullThenThrow() {
		assertThatThrownBy(() -> TextToSpeechModelObservationContext.builder().provider("superprovider").build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("prompt cannot be null");
	}

	@Test
	void whenProviderIsEmptyThenThrow() {
		assertThatThrownBy(() -> TextToSpeechModelObservationContext.builder()
			.prompt(new TextToSpeechPrompt("Welcome"))
			.provider("")
			.build()).isInstanceOf(IllegalArgumentException.class).hasMessage("provider cannot be null or empty");
	}

}
