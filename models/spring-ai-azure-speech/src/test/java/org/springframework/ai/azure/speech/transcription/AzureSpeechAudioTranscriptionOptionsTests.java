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

package org.springframework.ai.azure.speech.transcription;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.audio.transcription.AudioTranscriptionOptions;
import org.springframework.ai.azure.speech.transcription.AzureSpeechAudioTranscriptionOptions.LanguageIdMode;
import org.springframework.ai.azure.speech.transcription.AzureSpeechAudioTranscriptionOptions.OutputFormat;
import org.springframework.ai.azure.speech.transcription.AzureSpeechAudioTranscriptionOptions.Profanity;
import org.springframework.util.unit.DataSize;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link AzureSpeechAudioTranscriptionOptions}.
 *
 * @author Spring AI Contributors
 */
class AzureSpeechAudioTranscriptionOptionsTests {

	@Test
	void builderSetsEveryField() {
		AzureSpeechAudioTranscriptionOptions options = AzureSpeechAudioTranscriptionOptions.builder()
			.model("my-model")
			.language("en-US")
			.profanity(Profanity.REMOVED)
			.outputFormat(OutputFormat.DETAILED)
			.wordLevelTimestamps(true)
			.phrases(List.of("Contoso"))
			.timeout(Duration.ofSeconds(30))
			.maxAudioSize(DataSize.ofMegabytes(5))
			.build();

		assertThat(options.getRecognitionModel()).isEqualTo("my-model");
		assertThat(options.getLanguage()).isEqualTo("en-US");
		assertThat(options.getProfanity()).isEqualTo(Profanity.REMOVED);
		assertThat(options.getOutputFormat()).isEqualTo(OutputFormat.DETAILED);
		assertThat(options.isWordLevelTimestampsEnabled()).isTrue();
		assertThat(options.isDetailedOutput()).isTrue();
		assertThat(options.getPhrases()).containsExactly("Contoso");
		assertThat(options.resolvedTimeout()).isEqualTo(Duration.ofSeconds(30));
		assertThat(options.resolvedMaxAudioSize()).isEqualTo(DataSize.ofMegabytes(5));
	}

	@Test
	void unsetTimeoutAndSizeFallBackToTheDocumentedDefaults() {
		AzureSpeechAudioTranscriptionOptions options = AzureSpeechAudioTranscriptionOptions.builder().build();

		assertThat(options.getTimeout()).isNull();
		assertThat(options.resolvedTimeout()).isEqualTo(AzureSpeechAudioTranscriptionOptions.DEFAULT_TIMEOUT);
		assertThat(options.resolvedMaxAudioSize())
			.isEqualTo(AzureSpeechAudioTranscriptionOptions.DEFAULT_MAX_AUDIO_SIZE);
	}

	@Test
	void requestValuesOverrideDefaultsAndUnsetFieldsInherit() {
		AzureSpeechAudioTranscriptionOptions defaults = AzureSpeechAudioTranscriptionOptions.builder()
			.language("en-US")
			.profanity(Profanity.MASKED)
			.timeout(Duration.ofMinutes(2))
			.build();
		AzureSpeechAudioTranscriptionOptions request = AzureSpeechAudioTranscriptionOptions.builder()
			.profanity(Profanity.RAW)
			.build();

		AzureSpeechAudioTranscriptionOptions merged = AzureSpeechAudioTranscriptionOptions.builder()
			.from(defaults)
			.merge(request)
			.build();

		assertThat(merged.getProfanity()).isEqualTo(Profanity.RAW);
		assertThat(merged.getLanguage()).isEqualTo("en-US");
		assertThat(merged.resolvedTimeout()).isEqualTo(Duration.ofMinutes(2));
	}

	@Test
	void anExplicitRequestTimeoutOverridesTheDefault() {
		AzureSpeechAudioTranscriptionOptions defaults = AzureSpeechAudioTranscriptionOptions.builder()
			.timeout(Duration.ofSeconds(5))
			.build();
		AzureSpeechAudioTranscriptionOptions request = AzureSpeechAudioTranscriptionOptions.builder()
			.timeout(AzureSpeechAudioTranscriptionOptions.DEFAULT_TIMEOUT)
			.build();

		AzureSpeechAudioTranscriptionOptions merged = AzureSpeechAudioTranscriptionOptions.builder()
			.from(defaults)
			.merge(request)
			.build();

		assertThat(merged.resolvedTimeout()).isEqualTo(AzureSpeechAudioTranscriptionOptions.DEFAULT_TIMEOUT);
	}

	@Test
	void aForeignOptionsTypeContributesOnlyItsModel() {
		AzureSpeechAudioTranscriptionOptions defaults = AzureSpeechAudioTranscriptionOptions.builder()
			.model("default-model")
			.language("en-US")
			.build();
		AudioTranscriptionOptions foreign = () -> "foreign-model";

		AzureSpeechAudioTranscriptionOptions merged = AzureSpeechAudioTranscriptionOptions.builder()
			.from(defaults)
			.merge(foreign)
			.build();

		assertThat(merged.getRecognitionModel()).isEqualTo("foreign-model");
		assertThat(merged.getLanguage()).isEqualTo("en-US");
	}

	@Test
	void mergingNullLeavesTheDefaultsAlone() {
		AzureSpeechAudioTranscriptionOptions defaults = AzureSpeechAudioTranscriptionOptions.builder()
			.language("de-DE")
			.build();

		AzureSpeechAudioTranscriptionOptions merged = AzureSpeechAudioTranscriptionOptions.builder()
			.from(defaults)
			.merge(null)
			.build();

		assertThat(merged).isEqualTo(defaults);
	}

	@Test
	void getModelPrefersTheRecognitionModelThenTheEndpointIdThenTheConstant() {
		assertThat(AzureSpeechAudioTranscriptionOptions.builder().model("m").endpointId("e").build().getModel())
			.isEqualTo("m");
		assertThat(AzureSpeechAudioTranscriptionOptions.builder().endpointId("e").build().getModel()).isEqualTo("e");
		assertThat(AzureSpeechAudioTranscriptionOptions.builder().build().getModel())
			.isEqualTo(AzureSpeechAudioTranscriptionOptions.DEFAULT_MODEL);
		assertThat(AzureSpeechAudioTranscriptionOptions.builder().model("  ").endpointId("e").build().getModel())
			.isEqualTo("e");
	}

	@Test
	void copyEqualsAndHashCodeAgree() {
		AzureSpeechAudioTranscriptionOptions options = AzureSpeechAudioTranscriptionOptions.builder()
			.candidateLanguages(List.of("en-US", "de-DE"))
			.languageIdMode(LanguageIdMode.CONTINUOUS)
			.build();

		AzureSpeechAudioTranscriptionOptions copy = options.copy();

		assertThat(copy).isEqualTo(options).isNotSameAs(options);
		assertThat(copy.hashCode()).isEqualTo(options.hashCode());
		assertThat(copy.isLanguageIdentificationEnabled()).isTrue();
	}

	@Test
	void theBuilderDoesNotLeakItsWorkingInstance() {
		AzureSpeechAudioTranscriptionOptions.Builder builder = AzureSpeechAudioTranscriptionOptions.builder()
			.language("en-US");
		AzureSpeechAudioTranscriptionOptions first = builder.build();
		builder.language("de-DE");

		assertThat(first.getLanguage()).isEqualTo("en-US");
	}

	@Test
	void toStringCarriesNoCredential() {
		String rendered = AzureSpeechAudioTranscriptionOptions.builder()
			.model("m")
			.endpointId("deployment-guid")
			.build()
			.toString();

		assertThat(rendered).contains("deployment-guid").doesNotContain("api-key", "Bearer");
	}

}
