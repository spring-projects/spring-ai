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

package org.springframework.ai.audio.transcription.observation;

import org.junit.jupiter.api.Test;

import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.observation.conventions.AiOperationType;
import org.springframework.core.io.ByteArrayResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link TranscriptionModelObservationContext}.
 *
 * @author Christian Tzolov
 */
class TranscriptionModelObservationContextTests {

	@Test
	void whenMandatoryFieldsThenReturn() {
		var context = TranscriptionModelObservationContext.builder()
			.prompt(new AudioTranscriptionPrompt(new ByteArrayResource(new byte[] { 1 }), () -> "whisper-1"))
			.provider("superprovider")
			.build();

		assertThat(context).isNotNull();
		assertThat(context.isStreaming()).isFalse();
		assertThat(context.getOperationType()).isEqualTo(AiOperationType.TRANSCRIPTION.value());
		assertThat(context.getOperationMetadata().provider()).isEqualTo("superprovider");
		assertThat(context.getRequest().getOptions().getModel()).isEqualTo("whisper-1");
	}

	@Test
	void whenStreamingThenIsStreaming() {
		var context = TranscriptionModelObservationContext.builder()
			.prompt(new AudioTranscriptionPrompt(new ByteArrayResource(new byte[] { 1 })))
			.provider("superprovider")
			.streaming(true)
			.build();

		assertThat(context.isStreaming()).isTrue();
	}

	@Test
	void whenPromptIsNullThenThrow() {
		assertThatThrownBy(() -> TranscriptionModelObservationContext.builder().provider("superprovider").build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("prompt cannot be null");
	}

	@Test
	void whenProviderIsNullThenThrow() {
		assertThatThrownBy(() -> TranscriptionModelObservationContext.builder()
			.prompt(new AudioTranscriptionPrompt(new ByteArrayResource(new byte[] { 1 })))
			.build()).isInstanceOf(IllegalArgumentException.class).hasMessage("provider cannot be null or empty");
	}

}
