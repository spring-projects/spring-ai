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

import io.micrometer.common.KeyValue;
import org.junit.jupiter.api.Test;

import org.springframework.ai.audio.transcription.AudioTranscriptionOptions;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.observation.conventions.AiObservationAttributes;
import org.springframework.ai.observation.conventions.AiOperationType;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.core.io.ByteArrayResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DefaultAudioTranscriptionModelObservationConvention}.
 *
 * @author Olivier Le Quellec
 */
class DefaultAudioTranscriptionModelObservationConventionTests {

	private final DefaultAudioTranscriptionModelObservationConvention convention = new DefaultAudioTranscriptionModelObservationConvention();

	@Test
	void shouldHaveDefaultName() {
		assertThat(this.convention.getName()).isEqualTo("gen_ai.client.operation");
	}

	@Test
	void shouldHaveContextualName() {
		AudioTranscriptionModelObservationContext context = context(new TestOptions("chirp_2"));
		assertThat(this.convention.getContextualName(context)).isEqualTo("transcription chirp_2");
	}

	@Test
	void shouldHaveContextualNameWithoutModel() {
		AudioTranscriptionModelObservationContext context = context(new TestOptions(""));
		assertThat(this.convention.getContextualName(context)).isEqualTo("transcription");
	}

	@Test
	void shouldPopulateLowCardinalityKeyValues() {
		AudioTranscriptionModelObservationContext context = context(new TestOptions("chirp_2"));
		assertThat(this.convention.getLowCardinalityKeyValues(context)).contains(
				KeyValue.of(AiObservationAttributes.AI_OPERATION_TYPE.value(), AiOperationType.TRANSCRIPTION.value()),
				KeyValue.of(AiObservationAttributes.AI_PROVIDER.value(), AiProvider.GOOGLE_GENAI_AI.value()),
				KeyValue.of(AiObservationAttributes.REQUEST_MODEL.value(), "chirp_2"));
	}

	@Test
	void shouldPopulateRequestModelNoneWhenMissing() {
		AudioTranscriptionModelObservationContext context = context(new TestOptions(""));
		assertThat(this.convention.getLowCardinalityKeyValues(context))
			.contains(KeyValue.of(AiObservationAttributes.REQUEST_MODEL.value(), KeyValue.NONE_VALUE));
	}

	private AudioTranscriptionModelObservationContext context(AudioTranscriptionOptions options) {
		AudioTranscriptionPrompt prompt = new AudioTranscriptionPrompt(new ByteArrayResource(new byte[] { 1, 2, 3 }),
				options);
		return AudioTranscriptionModelObservationContext.builder()
			.transcriptionPrompt(prompt)
			.provider(AiProvider.GOOGLE_GENAI_AI.value())
			.build();
	}

	private record TestOptions(String model) implements AudioTranscriptionOptions {

		@Override
		public String getModel() {
			return this.model;
		}

	}

}
