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

package org.springframework.ai.model.azure.speech.autoconfigure;

import java.time.Duration;
import java.util.List;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.azure.speech.transcription.AzureSpeechAudioTranscriptionOptions;
import org.springframework.ai.azure.speech.transcription.AzureSpeechAudioTranscriptionOptions.LanguageIdMode;
import org.springframework.ai.azure.speech.transcription.AzureSpeechAudioTranscriptionOptions.OutputFormat;
import org.springframework.ai.azure.speech.transcription.AzureSpeechAudioTranscriptionOptions.Profanity;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * Default transcription options for Azure AI Speech.
 * <p>
 * Every property here is a model default that a request may replace with a value of its
 * own; see {@link AzureSpeechAudioTranscriptionOptions}. A request cannot clear one,
 * because merging only takes the values the request itself sets.
 * <p>
 * Option combinations are validated per request rather than at startup, so defaults that
 * cannot be honored together — a recognition model alongside a Custom Speech endpoint ID,
 * for instance — are rejected on the first request and on every request after it, not
 * when the context starts.
 *
 * @author Spring AI Contributors
 * @since 2.1.0
 */
@ConfigurationProperties(AzureSpeechTranscriptionProperties.CONFIG_PREFIX)
public class AzureSpeechTranscriptionProperties {

	public static final String CONFIG_PREFIX = "spring.ai.azure.speech.transcription";

	/**
	 * Recognition model to use. Mutually exclusive with the Custom Speech endpoint ID.
	 */
	private @Nullable String model;

	/**
	 * Custom Speech endpoint ID of a deployed custom model.
	 */
	private @Nullable String endpointId;

	/**
	 * BCP-47 recognition language, such as {@code en-US}. Mutually exclusive with the
	 * candidate languages.
	 */
	private @Nullable String language;

	/**
	 * BCP-47 languages automatic language identification chooses between. At most 4 at
	 * the start of the audio, or 10 continuously.
	 */
	private @Nullable List<String> candidateLanguages;

	/**
	 * How automatic language identification runs over the audio. Requires the candidate
	 * languages. {@code CONTINUOUS} additionally requires an endpoint-based connection.
	 */
	private @Nullable LanguageIdMode languageIdMode;

	/**
	 * How profanity is handled in the recognized text.
	 */
	private @Nullable Profanity profanity;

	/**
	 * Whether the service returns simple or detailed results.
	 */
	private @Nullable OutputFormat outputFormat;

	/**
	 * Whether word-level timestamps are requested.
	 */
	private @Nullable Boolean wordLevelTimestamps;

	/**
	 * Phrases that bias recognition towards domain vocabulary.
	 */
	private @Nullable List<String> phrases;

	/**
	 * Overall deadline for one transcription operation. Defaults to
	 * {@code AzureSpeechAudioTranscriptionOptions.DEFAULT_TIMEOUT}.
	 */
	private @Nullable Duration timeout;

	/**
	 * Guard on the PCM payload served to the SDK. Defaults to
	 * {@code AzureSpeechAudioTranscriptionOptions.DEFAULT_MAX_AUDIO_SIZE}.
	 */
	private @Nullable DataSize maxAudioSize;

	public @Nullable String getModel() {
		return this.model;
	}

	public void setModel(@Nullable String model) {
		this.model = model;
	}

	public @Nullable String getEndpointId() {
		return this.endpointId;
	}

	public void setEndpointId(@Nullable String endpointId) {
		this.endpointId = endpointId;
	}

	public @Nullable String getLanguage() {
		return this.language;
	}

	public void setLanguage(@Nullable String language) {
		this.language = language;
	}

	public @Nullable List<String> getCandidateLanguages() {
		return this.candidateLanguages;
	}

	public void setCandidateLanguages(@Nullable List<String> candidateLanguages) {
		this.candidateLanguages = candidateLanguages;
	}

	public @Nullable LanguageIdMode getLanguageIdMode() {
		return this.languageIdMode;
	}

	public void setLanguageIdMode(@Nullable LanguageIdMode languageIdMode) {
		this.languageIdMode = languageIdMode;
	}

	public @Nullable Profanity getProfanity() {
		return this.profanity;
	}

	public void setProfanity(@Nullable Profanity profanity) {
		this.profanity = profanity;
	}

	public @Nullable OutputFormat getOutputFormat() {
		return this.outputFormat;
	}

	public void setOutputFormat(@Nullable OutputFormat outputFormat) {
		this.outputFormat = outputFormat;
	}

	public @Nullable Boolean getWordLevelTimestamps() {
		return this.wordLevelTimestamps;
	}

	public void setWordLevelTimestamps(@Nullable Boolean wordLevelTimestamps) {
		this.wordLevelTimestamps = wordLevelTimestamps;
	}

	public @Nullable List<String> getPhrases() {
		return this.phrases;
	}

	public void setPhrases(@Nullable List<String> phrases) {
		this.phrases = phrases;
	}

	public @Nullable Duration getTimeout() {
		return this.timeout;
	}

	public void setTimeout(@Nullable Duration timeout) {
		this.timeout = timeout;
	}

	public @Nullable DataSize getMaxAudioSize() {
		return this.maxAudioSize;
	}

	public void setMaxAudioSize(@Nullable DataSize maxAudioSize) {
		this.maxAudioSize = maxAudioSize;
	}

	public AzureSpeechAudioTranscriptionOptions toOptions() {
		return AzureSpeechAudioTranscriptionOptions.builder()
			.model(this.model)
			.endpointId(this.endpointId)
			.language(this.language)
			.candidateLanguages(this.candidateLanguages)
			.languageIdMode(this.languageIdMode)
			.profanity(this.profanity)
			.outputFormat(this.outputFormat)
			.wordLevelTimestamps(this.wordLevelTimestamps)
			.phrases(this.phrases)
			.timeout(this.timeout)
			.maxAudioSize(this.maxAudioSize)
			.build();
	}

}
