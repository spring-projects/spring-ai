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

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import org.junit.jupiter.api.Test;

import org.springframework.ai.audio.tts.TextToSpeechOptions;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.observation.conventions.AiObservationAttributes;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DefaultTextToSpeechModelObservationConvention}.
 *
 * @author Christian Tzolov
 */
class DefaultTextToSpeechModelObservationConventionTests {

	private final DefaultTextToSpeechModelObservationConvention observationConvention = new DefaultTextToSpeechModelObservationConvention();

	private static TextToSpeechModelObservationContext context(TextToSpeechOptions options) {
		return TextToSpeechModelObservationContext.builder()
			.prompt(options == null ? new TextToSpeechPrompt("Welcome") : new TextToSpeechPrompt("Welcome", options))
			.provider("superprovider")
			.build();
	}

	@Test
	void shouldHaveName() {
		assertThat(this.observationConvention.getName())
			.isEqualTo(DefaultTextToSpeechModelObservationConvention.DEFAULT_NAME);
	}

	@Test
	void contextualNameWhenModelIsDefined() {
		var context = context(TextToSpeechOptions.builder().model("tts-1").build());
		assertThat(this.observationConvention.getContextualName(context)).isEqualTo("text_to_speech tts-1");
	}

	@Test
	void contextualNameWhenModelIsNotDefined() {
		assertThat(this.observationConvention.getContextualName(context(null))).isEqualTo("text_to_speech");
	}

	@Test
	void supportsOnlyTextToSpeechModelObservationContext() {
		assertThat(this.observationConvention.supportsContext(context(null))).isTrue();
		assertThat(this.observationConvention.supportsContext(new Observation.Context())).isFalse();
	}

	@Test
	void shouldHaveLowCardinalityKeyValuesWhenDefined() {
		var context = context(TextToSpeechOptions.builder().model("tts-1").build());
		assertThat(this.observationConvention.getLowCardinalityKeyValues(context)).contains(
				KeyValue.of(AiObservationAttributes.AI_OPERATION_TYPE.value(), "text_to_speech"),
				KeyValue.of(AiObservationAttributes.AI_PROVIDER.value(), "superprovider"),
				KeyValue.of(AiObservationAttributes.REQUEST_MODEL.value(), "tts-1"));
	}

	@Test
	void shouldHaveHighCardinalityKeyValuesWhenDefined() {
		var context = context(
				TextToSpeechOptions.builder().model("tts-1").voice("nova").format("mp3").speed(1.5).build());
		assertThat(this.observationConvention.getHighCardinalityKeyValues(context)).contains(
				KeyValue.of(AiObservationAttributes.REQUEST_AUDIO_VOICE.value(), "nova"),
				KeyValue.of(AiObservationAttributes.REQUEST_AUDIO_RESPONSE_FORMAT.value(), "mp3"),
				KeyValue.of(AiObservationAttributes.REQUEST_AUDIO_SPEED.value(), "1.5"));
	}

	@Test
	void shouldHaveStreamKeyValueOnlyWhenStreaming() {
		var streaming = TextToSpeechModelObservationContext.builder()
			.prompt(new TextToSpeechPrompt("Welcome"))
			.provider("superprovider")
			.streaming(true)
			.build();
		assertThat(this.observationConvention.getHighCardinalityKeyValues(streaming))
			.contains(KeyValue.of(AiObservationAttributes.REQUEST_STREAM.value(), "true"));
		assertThat(this.observationConvention.getHighCardinalityKeyValues(context(null)))
			.noneMatch(keyValue -> keyValue.getKey().equals(AiObservationAttributes.REQUEST_STREAM.value()));
	}

	@Test
	void shouldNotHaveKeyValuesWhenEmptyValues() {
		var context = context(null);
		assertThat(this.observationConvention.getLowCardinalityKeyValues(context))
			.contains(KeyValue.of(AiObservationAttributes.REQUEST_MODEL.value(), KeyValue.NONE_VALUE));
		assertThat(this.observationConvention.getHighCardinalityKeyValues(context)).isEmpty();
	}

}
