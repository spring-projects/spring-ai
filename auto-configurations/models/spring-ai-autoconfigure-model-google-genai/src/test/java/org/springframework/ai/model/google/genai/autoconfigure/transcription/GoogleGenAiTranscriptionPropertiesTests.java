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

package org.springframework.ai.model.google.genai.autoconfigure.transcription;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.google.genai.transcription.GoogleGenAiAudioTranscriptionOptions;
import org.springframework.ai.google.genai.transcription.GoogleGenAiAudioTranscriptionOptions.AudioEncoding;
import org.springframework.ai.google.genai.transcription.GoogleGenAiAudioTranscriptionOptions.MultiChannelMode;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for Google GenAI Transcription properties binding.
 *
 * @author Olivier Le Quellec
 */
class GoogleGenAiTranscriptionPropertiesTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(PropertiesTestConfiguration.class);

	@Test
	void connectionPropertiesBinding() {
		this.contextRunner
			.withPropertyValues("spring.ai.google.genai.transcription.project-id=test-project",
					"spring.ai.google.genai.transcription.location=us")
			.run(context -> {
				GoogleGenAiTranscriptionConnectionProperties props = context
					.getBean(GoogleGenAiTranscriptionConnectionProperties.class);
				assertThat(props.getProjectId()).isEqualTo("test-project");
				assertThat(props.getLocation()).isEqualTo("us");
			});
	}

	@Test
	void optionsPropertiesBinding() {
		this.contextRunner
			.withPropertyValues("spring.ai.google.genai.transcription.model=chirp_3",
					"spring.ai.google.genai.transcription.language=en-US",
					"spring.ai.google.genai.transcription.language-codes=en-US,fr-FR",
					"spring.ai.google.genai.transcription.enable-automatic-punctuation=true",
					"spring.ai.google.genai.transcription.enable-spoken-punctuation=true",
					"spring.ai.google.genai.transcription.enable-spoken-emojis=false",
					"spring.ai.google.genai.transcription.profanity-filter=true",
					"spring.ai.google.genai.transcription.enable-word-time-offsets=true",
					"spring.ai.google.genai.transcription.enable-word-confidence=true",
					"spring.ai.google.genai.transcription.max-alternatives=3",
					"spring.ai.google.genai.transcription.multi-channel-mode=SEPARATE_RECOGNITION_PER_CHANNEL",
					"spring.ai.google.genai.transcription.enable-speaker-diarization=true",
					"spring.ai.google.genai.transcription.min-speaker-count=1",
					"spring.ai.google.genai.transcription.max-speaker-count=4",
					"spring.ai.google.genai.transcription.translation-target-language=es-ES",
					"spring.ai.google.genai.transcription.encoding=LINEAR16",
					"spring.ai.google.genai.transcription.sample-rate-hertz=16000",
					"spring.ai.google.genai.transcription.audio-channel-count=2",
					"spring.ai.google.genai.transcription.denoise-audio=true",
					"spring.ai.google.genai.transcription.snr-threshold=5.0",
					"spring.ai.google.genai.transcription.custom-prompt=Transcribe verbatim.")
			.run(context -> {
				GoogleGenAiTranscriptionProperties props = context.getBean(GoogleGenAiTranscriptionProperties.class);
				GoogleGenAiAudioTranscriptionOptions options = props.toOptions();
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
				assertThat(options.getCustomPrompt()).isEqualTo("Transcribe verbatim.");
			});
	}

	@Test
	void phraseHintsAndTranscriptNormalizationPropertiesBinding() {
		this.contextRunner
			.withPropertyValues("spring.ai.google.genai.transcription.phrase-hints[0].value=Spring AI",
					"spring.ai.google.genai.transcription.phrase-hints[1].value=Chirp",
					"spring.ai.google.genai.transcription.phrase-hints[1].boost=15.0",
					"spring.ai.google.genai.transcription.phrase-set-boost=5.0",
					"spring.ai.google.genai.transcription.transcript-normalization-entries[0].search=Mister",
					"spring.ai.google.genai.transcription.transcript-normalization-entries[0].replace=Mr.",
					"spring.ai.google.genai.transcription.transcript-normalization-entries[0].case-sensitive=true")
			.run(context -> {
				GoogleGenAiTranscriptionProperties props = context.getBean(GoogleGenAiTranscriptionProperties.class);
				GoogleGenAiAudioTranscriptionOptions options = props.toOptions();

				assertThat(options.getPhraseHints())
					.extracting(GoogleGenAiAudioTranscriptionOptions.PhraseHint::getValue)
					.containsExactly("Spring AI", "Chirp");
				assertThat(options.getPhraseHints().get(0).getBoost()).isNull();
				assertThat(options.getPhraseHints().get(1).getBoost()).isEqualTo(15.0f);
				assertThat(options.getPhraseSetBoost()).isEqualTo(5.0f);

				assertThat(options.getTranscriptNormalizationEntries()).hasSize(1);
				assertThat(options.getTranscriptNormalizationEntries().get(0).getSearch()).isEqualTo("Mister");
				assertThat(options.getTranscriptNormalizationEntries().get(0).getReplace()).isEqualTo("Mr.");
				assertThat(options.getTranscriptNormalizationEntries().get(0).isCaseSensitive()).isTrue();
			});
	}

	@Test
	void noPhraseHintsOrTranscriptNormalizationWhenNotConfigured() {
		this.contextRunner.run(context -> {
			GoogleGenAiTranscriptionProperties props = context.getBean(GoogleGenAiTranscriptionProperties.class);
			GoogleGenAiAudioTranscriptionOptions options = props.toOptions();

			assertThat(options.getPhraseHints()).isNull();
			assertThat(options.getPhraseSetBoost()).isNull();
			assertThat(options.getTranscriptNormalizationEntries()).isNull();
		});
	}

	@Test
	void defaultOptionsBinding() {
		this.contextRunner.run(context -> {
			GoogleGenAiTranscriptionProperties props = context.getBean(GoogleGenAiTranscriptionProperties.class);
			assertThat(props.toOptions().getModel()).isEqualTo(GoogleGenAiAudioTranscriptionOptions.DEFAULT_MODEL_NAME);
		});
	}

	@Test
	void connectionPropertiesGettersAndSetters() {
		GoogleGenAiTranscriptionConnectionProperties props = new GoogleGenAiTranscriptionConnectionProperties();
		props.setProjectId("project-id");
		props.setLocation("global");
		org.springframework.core.io.Resource credentialsUri = new org.springframework.core.io.ClassPathResource(
				"fake-credentials.json");
		props.setCredentialsUri(credentialsUri);

		assertThat(props.getProjectId()).isEqualTo("project-id");
		assertThat(props.getLocation()).isEqualTo("global");
		assertThat(props.getCredentialsUri()).isEqualTo(credentialsUri);
	}

	@Test
	void transcriptionPropertiesGettersAndSetters() {
		GoogleGenAiTranscriptionProperties props = new GoogleGenAiTranscriptionProperties();
		props.setModel("chirp_2");
		props.setLanguage("en-US");
		props.setLanguageCodes(List.of("en-US"));
		props.setEnableWordConfidence(true);
		props.setEncoding(AudioEncoding.FLAC);
		props.setSampleRateHertz(8000);
		props.setAudioChannelCount(1);
		props.setDenoiseAudio(true);
		props.setSnrThreshold(3.5f);
		props.setCustomPrompt("Include speaker labels.");

		assertThat(props.getModel()).isEqualTo("chirp_2");
		assertThat(props.getLanguage()).isEqualTo("en-US");
		assertThat(props.getLanguageCodes()).containsExactly("en-US");
		assertThat(props.getEnableWordConfidence()).isTrue();
		assertThat(props.getEncoding()).isEqualTo(AudioEncoding.FLAC);
		assertThat(props.getSampleRateHertz()).isEqualTo(8000);
		assertThat(props.getAudioChannelCount()).isEqualTo(1);
		assertThat(props.getDenoiseAudio()).isTrue();
		assertThat(props.getSnrThreshold()).isEqualTo(3.5f);
		assertThat(props.getCustomPrompt()).isEqualTo("Include speaker labels.");
	}

	@Test
	void phraseHintAndTranscriptNormalizationEntryPropertiesGettersAndSetters() {
		GoogleGenAiTranscriptionProperties.PhraseHintProperties phraseHint = new GoogleGenAiTranscriptionProperties.PhraseHintProperties();
		phraseHint.setValue("Spring AI");
		phraseHint.setBoost(10.0f);

		assertThat(phraseHint.getValue()).isEqualTo("Spring AI");
		assertThat(phraseHint.getBoost()).isEqualTo(10.0f);

		GoogleGenAiTranscriptionProperties.TranscriptNormalizationEntryProperties entry = new GoogleGenAiTranscriptionProperties.TranscriptNormalizationEntryProperties();
		entry.setSearch("Mister");
		entry.setReplace("Mr.");
		entry.setCaseSensitive(true);

		assertThat(entry.getSearch()).isEqualTo("Mister");
		assertThat(entry.getReplace()).isEqualTo("Mr.");
		assertThat(entry.isCaseSensitive()).isTrue();
	}

	@Configuration
	@EnableConfigurationProperties({ GoogleGenAiTranscriptionConnectionProperties.class,
			GoogleGenAiTranscriptionProperties.class })
	static class PropertiesTestConfiguration {

	}

}
