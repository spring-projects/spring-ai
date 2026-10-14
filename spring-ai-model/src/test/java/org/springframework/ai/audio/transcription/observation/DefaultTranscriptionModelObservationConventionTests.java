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
import io.micrometer.observation.Observation;
import org.junit.jupiter.api.Test;

import org.springframework.ai.audio.transcription.AudioTranscriptionOptions;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.observation.conventions.AiObservationAttributes;
import org.springframework.core.io.ByteArrayResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DefaultTranscriptionModelObservationConvention}.
 *
 * @author Christian Tzolov
 */
class DefaultTranscriptionModelObservationConventionTests {

	private final DefaultTranscriptionModelObservationConvention observationConvention = new DefaultTranscriptionModelObservationConvention();

	private static TranscriptionModelObservationContext context(AudioTranscriptionOptions options) {
		return TranscriptionModelObservationContext.builder()
			.prompt(new AudioTranscriptionPrompt(new ByteArrayResource(new byte[] { 1 }), options))
			.provider("superprovider")
			.build();
	}

	@Test
	void shouldHaveName() {
		assertThat(this.observationConvention.getName())
			.isEqualTo(DefaultTranscriptionModelObservationConvention.DEFAULT_NAME);
	}

	@Test
	void contextualNameWhenModelIsDefined() {
		assertThat(this.observationConvention.getContextualName(context(() -> "whisper-1")))
			.isEqualTo("transcription whisper-1");
	}

	@Test
	void contextualNameWhenModelIsNotDefined() {
		assertThat(this.observationConvention.getContextualName(context(null))).isEqualTo("transcription");
	}

	@Test
	void supportsOnlyTranscriptionModelObservationContext() {
		assertThat(this.observationConvention.supportsContext(context(null))).isTrue();
		assertThat(this.observationConvention.supportsContext(new Observation.Context())).isFalse();
	}

	@Test
	void shouldHaveLowCardinalityKeyValuesWhenDefined() {
		assertThat(this.observationConvention.getLowCardinalityKeyValues(context(() -> "whisper-1"))).contains(
				KeyValue.of(AiObservationAttributes.AI_OPERATION_TYPE.value(), "transcription"),
				KeyValue.of(AiObservationAttributes.AI_PROVIDER.value(), "superprovider"),
				KeyValue.of(AiObservationAttributes.REQUEST_MODEL.value(), "whisper-1"));
	}

	@Test
	void shouldHaveNoHighCardinalityKeyValuesForACall() {
		// The audio itself is never described: its name would be a path on the caller's
		// machine.
		assertThat(this.observationConvention.getHighCardinalityKeyValues(context(() -> "whisper-1"))).isEmpty();
	}

	@Test
	void shouldHaveStreamKeyValueWhenStreaming() {
		var streaming = TranscriptionModelObservationContext.builder()
			.prompt(new AudioTranscriptionPrompt(new ByteArrayResource(new byte[] { 1 })))
			.provider("superprovider")
			.streaming(true)
			.build();
		assertThat(this.observationConvention.getHighCardinalityKeyValues(streaming))
			.containsExactly(KeyValue.of(AiObservationAttributes.REQUEST_STREAM.value(), "true"));
	}

	@Test
	void shouldNotHaveKeyValuesWhenEmptyValues() {
		assertThat(this.observationConvention.getLowCardinalityKeyValues(context(null)))
			.contains(KeyValue.of(AiObservationAttributes.REQUEST_MODEL.value(), KeyValue.NONE_VALUE));
	}

}
