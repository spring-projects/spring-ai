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

import org.springframework.ai.google.genai.transcription.GoogleGenAiAudioTranscriptionOptions.AudioEncoding;
import org.springframework.ai.google.genai.transcription.GoogleGenAiAudioTranscriptionOptions.MultiChannelMode;

import static org.assertj.core.api.Assertions.assertThat;

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
	}

	@Test
	void builderSetsEveryProperty() {
		GoogleGenAiAudioTranscriptionOptions options = GoogleGenAiAudioTranscriptionOptions.builder()
			.model("chirp_3")
			.language("en-US")
			.languageCodes(List.of("en-US", "fr-FR"))
			.enableAutomaticPunctuation(true)
			.enableSpokenPunctuation(true)
			.enableSpokenEmojis(false)
			.profanityFilter(true)
			.enableWordTimeOffsets(true)
			.enableWordConfidence(true)
			.maxAlternatives(3)
			.multiChannelMode(MultiChannelMode.SEPARATE_RECOGNITION_PER_CHANNEL)
			.enableSpeakerDiarization(true)
			.minSpeakerCount(1)
			.maxSpeakerCount(4)
			.translationTargetLanguage("es-ES")
			.encoding(AudioEncoding.LINEAR16)
			.sampleRateHertz(16000)
			.audioChannelCount(2)
			.denoiseAudio(true)
			.snrThreshold(5.0f)
			.customPrompt("Transcribe verbatim, including filler words.")
			.phraseHints(List.of(GoogleGenAiAudioTranscriptionOptions.PhraseHint.of("Spring AI"),
					GoogleGenAiAudioTranscriptionOptions.PhraseHint.of("Chirp", 10.0f)))
			.phraseSetBoost(5.0f)
			.transcriptNormalizationEntries(List
				.of(GoogleGenAiAudioTranscriptionOptions.TranscriptNormalizationEntry.of("Mister", "Mr.", true)))
			.build();

		assertThat(options.getModel()).isEqualTo("chirp_3");
		assertThat(options.getLanguage()).isEqualTo("en-US");
		assertThat(options.getLanguageCodes()).containsExactly("en-US", "fr-FR");
		assertThat(options.getEnableAutomaticPunctuation()).isTrue();
		assertThat(options.getEnableSpokenPunctuation()).isTrue();
		assertThat(options.getEnableSpokenEmojis()).isFalse();
		assertThat(options.getProfanityFilter()).isTrue();
		assertThat(options.getEnableWordTimeOffsets()).isTrue();
		assertThat(options.getEnableWordConfidence()).isTrue();
		assertThat(options.getMaxAlternatives()).isEqualTo(3);
		assertThat(options.getMultiChannelMode()).isEqualTo(MultiChannelMode.SEPARATE_RECOGNITION_PER_CHANNEL);
		assertThat(options.getEnableSpeakerDiarization()).isTrue();
		assertThat(options.getMinSpeakerCount()).isEqualTo(1);
		assertThat(options.getMaxSpeakerCount()).isEqualTo(4);
		assertThat(options.getTranslationTargetLanguage()).isEqualTo("es-ES");
		assertThat(options.getEncoding()).isEqualTo(AudioEncoding.LINEAR16);
		assertThat(options.getSampleRateHertz()).isEqualTo(16000);
		assertThat(options.getAudioChannelCount()).isEqualTo(2);
		assertThat(options.getDenoiseAudio()).isTrue();
		assertThat(options.getSnrThreshold()).isEqualTo(5.0f);
		assertThat(options.getCustomPrompt()).isEqualTo("Transcribe verbatim, including filler words.");
		assertThat(options.getPhraseHints()).extracting(GoogleGenAiAudioTranscriptionOptions.PhraseHint::getValue)
			.containsExactly("Spring AI", "Chirp");
		assertThat(options.getPhraseHints().get(1).getBoost()).isEqualTo(10.0f);
		assertThat(options.getPhraseSetBoost()).isEqualTo(5.0f);
		assertThat(options.getTranscriptNormalizationEntries()).hasSize(1);
		assertThat(options.getTranscriptNormalizationEntries().get(0).getSearch()).isEqualTo("Mister");
		assertThat(options.getTranscriptNormalizationEntries().get(0).getReplace()).isEqualTo("Mr.");
		assertThat(options.getTranscriptNormalizationEntries().get(0).isCaseSensitive()).isTrue();
	}

	@Test
	void fromCopiesSetValuesAndKeepsExisting() {
		GoogleGenAiAudioTranscriptionOptions base = GoogleGenAiAudioTranscriptionOptions.builder()
			.model("chirp_2")
			.language("en-US")
			.enableWordConfidence(true)
			.build();

		GoogleGenAiAudioTranscriptionOptions override = GoogleGenAiAudioTranscriptionOptions.builder()
			.model("chirp_3")
			.enableSpeakerDiarization(true)
			.build();

		GoogleGenAiAudioTranscriptionOptions merged = GoogleGenAiAudioTranscriptionOptions.builder()
			.from(base)
			.from(override)
			.build();

		assertThat(merged.getModel()).isEqualTo("chirp_3");
		assertThat(merged.getLanguage()).isEqualTo("en-US");
		assertThat(merged.getEnableWordConfidence()).isTrue();
		assertThat(merged.getEnableSpeakerDiarization()).isTrue();
	}

	@Test
	void equalsAndHashCode() {
		GoogleGenAiAudioTranscriptionOptions one = GoogleGenAiAudioTranscriptionOptions.builder()
			.model("chirp_2")
			.language("en-US")
			.build();
		GoogleGenAiAudioTranscriptionOptions two = GoogleGenAiAudioTranscriptionOptions.builder()
			.model("chirp_2")
			.language("en-US")
			.build();

		assertThat(one).isEqualTo(two).hasSameHashCodeAs(two);
	}

}
