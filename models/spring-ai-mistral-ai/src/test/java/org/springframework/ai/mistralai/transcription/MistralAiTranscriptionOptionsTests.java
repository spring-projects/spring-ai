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

package org.springframework.ai.mistralai.transcription;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.audio.transcription.AudioTranscriptionOptions;
import org.springframework.ai.mistralai.api.MistralAiAudioApi.TimestampGranularity;
import org.springframework.ai.mistralai.api.MistralAiAudioApi.TranscriptionModel;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MistralAiTranscriptionOptions}.
 *
 * @author Thomas Vitale
 */
class MistralAiTranscriptionOptionsTests {

	@Test
	void defaults() {
		var options = MistralAiTranscriptionOptions.builder().build();

		assertThat(options.getModel()).isEqualTo(MistralAiTranscriptionOptions.DEFAULT_MODEL)
			.isEqualTo("voxtral-mini-latest");
		assertThat(options.getLanguage()).isNull();
		assertThat(options.getTemperature()).isNull();
		assertThat(options.getDiarize()).isNull();
		assertThat(options.getContextBias()).isNull();
		assertThat(options.getTimestampGranularities()).isNull();
	}

	@Test
	void builderSetsAllOptions() {
		var options = MistralAiTranscriptionOptions.builder()
			.model(TranscriptionModel.VOXTRAL_SMALL_TRANSCRIBE_3)
			.language("fr")
			.temperature(0.3)
			.diarize(true)
			.contextBias(List.of("Northwind"))
			.timestampGranularities(List.of(TimestampGranularity.SEGMENT))
			.build();

		assertThat(options.getModel()).isEqualTo("voxtral-small-transcribe-3");
		assertThat(options.getLanguage()).isEqualTo("fr");
		assertThat(options.getTemperature()).isEqualTo(0.3);
		assertThat(options.getDiarize()).isTrue();
		assertThat(options.getContextBias()).containsExactly("Northwind");
		assertThat(options.getTimestampGranularities()).containsExactly(TimestampGranularity.SEGMENT);
	}

	@Test
	void fromCopiesAllOptions() {
		var options = MistralAiTranscriptionOptions.builder()
			.model("voxtral-mini-transcribe-3")
			.language("fr")
			.temperature(0.3)
			.diarize(true)
			.contextBias(List.of("Northwind"))
			.timestampGranularities(List.of(TimestampGranularity.SEGMENT))
			.build();

		assertThat(MistralAiTranscriptionOptions.builder().from(options).build()).isEqualTo(options)
			.hasSameHashCodeAs(options);
	}

	@Test
	void mergeOverridesOnlySetOptions() {
		var defaults = MistralAiTranscriptionOptions.builder()
			.model("voxtral-small-transcribe-3")
			.language("fr")
			.temperature(0.3)
			.contextBias(List.of("Northwind"))
			.build();
		var runtime = MistralAiTranscriptionOptions.builder()
			.language("en")
			.diarize(true)
			.timestampGranularities(List.of(TimestampGranularity.SEGMENT))
			.build();

		var merged = MistralAiTranscriptionOptions.builder().from(defaults).merge(runtime).build();

		// The runtime options leave the model unset, so the default one is kept.
		assertThat(merged.getModel()).isEqualTo("voxtral-small-transcribe-3");
		assertThat(merged.getLanguage()).isEqualTo("en");
		assertThat(merged.getTemperature()).isEqualTo(0.3);
		assertThat(merged.getDiarize()).isTrue();
		assertThat(merged.getContextBias()).containsExactly("Northwind");
		assertThat(merged.getTimestampGranularities()).containsExactly(TimestampGranularity.SEGMENT);
	}

	@Test
	void mergeTakesModelOfPortableOptions() {
		var defaults = MistralAiTranscriptionOptions.builder()
			.model("voxtral-small-transcribe-3")
			.language("fr")
			.build();
		AudioTranscriptionOptions portable = () -> "voxtral-mini-transcribe-3";

		var merged = MistralAiTranscriptionOptions.builder().from(defaults).merge(portable).build();

		assertThat(merged.getModel()).isEqualTo("voxtral-mini-transcribe-3");
		assertThat(merged.getLanguage()).isEqualTo("fr");
	}

	@Test
	void mergeWithNullKeepsOptions() {
		var defaults = MistralAiTranscriptionOptions.builder().model("voxtral-small-transcribe-3").build();

		assertThat(MistralAiTranscriptionOptions.builder().from(defaults).merge(null).build()).isEqualTo(defaults);
	}

}
