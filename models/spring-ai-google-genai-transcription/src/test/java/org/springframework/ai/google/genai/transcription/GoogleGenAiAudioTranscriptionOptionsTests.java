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

package org.springframework.ai.google.genai.transcription;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.audio.transcription.AudioTranscriptionOptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GoogleGenAiAudioTranscriptionOptions}.
 *
 * @author Olivier Le Quellec
 */
class GoogleGenAiAudioTranscriptionOptionsTests {

	@Test
	void defaultModelIsAppliedWhenNotSet() {
		GoogleGenAiAudioTranscriptionOptions options = GoogleGenAiAudioTranscriptionOptions.builder().build();
		assertThat(options.getModel()).isEqualTo(GoogleGenAiAudioTranscriptionOptions.DEFAULT_MODEL_NAME);
		assertThat(options.getPrompt()).isNull();
		assertThat(options.getLanguage()).isNull();
		assertThat(options.getTemperature()).isNull();
	}

	@Test
	void builderSetsEveryProperty() {
		GoogleGenAiAudioTranscriptionOptions options = GoogleGenAiAudioTranscriptionOptions.builder()
			.model("gemini-3.5-transcribe")
			.prompt("Transcribe this interview verbatim.")
			.language("en-US")
			.temperature(0.1f)
			.topP(0.9f)
			.topK(40f)
			.candidateCount(1)
			.maxOutputTokens(2048)
			.stopSequences(List.of("\n\n"))
			.responseMimeType("text/plain")
			.presencePenalty(0.1f)
			.frequencyPenalty(0.2f)
			.seed(42)
			.build();

		assertThat(options.getModel()).isEqualTo("gemini-3.5-transcribe");
		assertThat(options.getPrompt()).isEqualTo("Transcribe this interview verbatim.");
		assertThat(options.getLanguage()).isEqualTo("en-US");
		assertThat(options.getTemperature()).isEqualTo(0.1f);
		assertThat(options.getTopP()).isEqualTo(0.9f);
		assertThat(options.getTopK()).isEqualTo(40f);
		assertThat(options.getCandidateCount()).isEqualTo(1);
		assertThat(options.getMaxOutputTokens()).isEqualTo(2048);
		assertThat(options.getStopSequences()).containsExactly("\n\n");
		assertThat(options.getResponseMimeType()).isEqualTo("text/plain");
		assertThat(options.getPresencePenalty()).isEqualTo(0.1f);
		assertThat(options.getFrequencyPenalty()).isEqualTo(0.2f);
		assertThat(options.getSeed()).isEqualTo(42);
	}

	@Test
	void fromCopiesAllFields() {
		GoogleGenAiAudioTranscriptionOptions source = GoogleGenAiAudioTranscriptionOptions.builder()
			.model("gemini-3.5-transcribe")
			.prompt("Transcribe.")
			.language("fr-FR")
			.temperature(0.5f)
			.build();

		GoogleGenAiAudioTranscriptionOptions copy = GoogleGenAiAudioTranscriptionOptions.builder().from(source).build();

		assertThat(copy).isEqualTo(source);
		assertThat(copy.hashCode()).isEqualTo(source.hashCode());
	}

	@Test
	void equalsAndHashCode() {
		GoogleGenAiAudioTranscriptionOptions first = GoogleGenAiAudioTranscriptionOptions.builder()
			.model("model-a")
			.language("en-US")
			.build();
		GoogleGenAiAudioTranscriptionOptions second = GoogleGenAiAudioTranscriptionOptions.builder()
			.model("model-a")
			.language("en-US")
			.build();
		GoogleGenAiAudioTranscriptionOptions different = GoogleGenAiAudioTranscriptionOptions.builder()
			.model("model-b")
			.language("en-US")
			.build();

		assertThat(first).isEqualTo(second);
		assertThat(first.hashCode()).isEqualTo(second.hashCode());
		assertThat(first).isNotEqualTo(different);
		assertThat(first).isNotEqualTo(null);
		assertThat(first).isNotEqualTo("not-options");
	}

	@Test
	void mergeWithNullRuntimeOptionsKeepsDefaults() {
		GoogleGenAiAudioTranscriptionOptions defaults = GoogleGenAiAudioTranscriptionOptions.builder()
			.model("model-a")
			.language("en-US")
			.build();

		GoogleGenAiAudioTranscriptionOptions merged = GoogleGenAiAudioTranscriptionOptions.builder()
			.from(defaults)
			.merge(null)
			.build();

		assertThat(merged).isEqualTo(defaults);
	}

	@Test
	void mergeWithGoogleOptionsOverridesOnlyNonNullFields() {
		GoogleGenAiAudioTranscriptionOptions defaults = GoogleGenAiAudioTranscriptionOptions.builder()
			.model("model-a")
			.prompt("Default prompt.")
			.language("en-US")
			.temperature(0.2f)
			.topP(0.9f)
			.seed(42)
			.build();

		GoogleGenAiAudioTranscriptionOptions runtime = GoogleGenAiAudioTranscriptionOptions.builder()
			.model("model-b")
			.temperature(0.8f)
			.maxOutputTokens(1024)
			.build();

		GoogleGenAiAudioTranscriptionOptions merged = GoogleGenAiAudioTranscriptionOptions.builder()
			.from(defaults)
			.merge(runtime)
			.build();

		assertThat(merged.getModel()).isEqualTo("model-b");
		assertThat(merged.getPrompt()).isEqualTo("Default prompt.");
		assertThat(merged.getLanguage()).isEqualTo("en-US");
		assertThat(merged.getTemperature()).isEqualTo(0.8f);
		assertThat(merged.getTopP()).isEqualTo(0.9f);
		assertThat(merged.getSeed()).isEqualTo(42);
		assertThat(merged.getMaxOutputTokens()).isEqualTo(1024);
	}

	@Test
	void mergeWithForeignOptionsMergesOnlyModel() {
		AudioTranscriptionOptions foreign = mock(AudioTranscriptionOptions.class);
		when(foreign.getModel()).thenReturn("foreign-model");

		GoogleGenAiAudioTranscriptionOptions merged = GoogleGenAiAudioTranscriptionOptions.builder()
			.prompt("Keep me.")
			.language("de-DE")
			.merge(foreign)
			.build();

		assertThat(merged.getModel()).isEqualTo("foreign-model");
		assertThat(merged.getPrompt()).isEqualTo("Keep me.");
		assertThat(merged.getLanguage()).isEqualTo("de-DE");
	}

	@Test
	void mergeWithForeignOptionsWithoutModelKeepsDefault() {
		AudioTranscriptionOptions foreign = mock(AudioTranscriptionOptions.class);
		when(foreign.getModel()).thenReturn(null);

		GoogleGenAiAudioTranscriptionOptions merged = GoogleGenAiAudioTranscriptionOptions.builder()
			.merge(foreign)
			.build();

		assertThat(merged.getModel()).isEqualTo(GoogleGenAiAudioTranscriptionOptions.DEFAULT_MODEL_NAME);
	}

}
