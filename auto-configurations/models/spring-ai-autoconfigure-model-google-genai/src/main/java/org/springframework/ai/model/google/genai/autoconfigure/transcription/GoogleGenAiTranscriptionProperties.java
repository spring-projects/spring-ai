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

import org.jspecify.annotations.Nullable;

import org.springframework.ai.google.genai.transcription.GoogleGenAiAudioTranscriptionOptions;
import org.springframework.ai.google.genai.transcription.GoogleGenAiAudioTranscriptionOptions.AudioEncoding;
import org.springframework.ai.google.genai.transcription.GoogleGenAiAudioTranscriptionOptions.MultiChannelMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Google GenAI Transcription.
 *
 * @author Olivier Le Quellec
 * @since 2.0.1
 */
@ConfigurationProperties(GoogleGenAiTranscriptionProperties.CONFIG_PREFIX)
public class GoogleGenAiTranscriptionProperties {

	public static final String CONFIG_PREFIX = "spring.ai.google.genai.transcription";

	private @Nullable String model = GoogleGenAiAudioTranscriptionOptions.DEFAULT_MODEL_NAME;

	private @Nullable String language;

	private @Nullable List<String> languageCodes;

	private @Nullable Boolean enableAutomaticPunctuation;

	private @Nullable Boolean enableSpokenPunctuation;

	private @Nullable Boolean enableSpokenEmojis;

	private @Nullable Boolean profanityFilter;

	private @Nullable Boolean enableWordTimeOffsets;

	private @Nullable Boolean enableWordConfidence;

	private @Nullable Integer maxAlternatives;

	private @Nullable MultiChannelMode multiChannelMode;

	private @Nullable Boolean enableSpeakerDiarization;

	private @Nullable Integer minSpeakerCount;

	private @Nullable Integer maxSpeakerCount;

	private @Nullable String translationTargetLanguage;

	private @Nullable AudioEncoding encoding;

	private @Nullable Integer sampleRateHertz;

	private @Nullable Integer audioChannelCount;

	private @Nullable Boolean denoiseAudio;

	private @Nullable Float snrThreshold;

	private @Nullable String customPrompt;

	public @Nullable String getModel() {
		return this.model;
	}

	public void setModel(@Nullable String model) {
		this.model = model;
	}

	public @Nullable String getLanguage() {
		return this.language;
	}

	public void setLanguage(@Nullable String language) {
		this.language = language;
	}

	public @Nullable List<String> getLanguageCodes() {
		return this.languageCodes;
	}

	public void setLanguageCodes(@Nullable List<String> languageCodes) {
		this.languageCodes = languageCodes;
	}

	public @Nullable Boolean getEnableAutomaticPunctuation() {
		return this.enableAutomaticPunctuation;
	}

	public void setEnableAutomaticPunctuation(@Nullable Boolean enableAutomaticPunctuation) {
		this.enableAutomaticPunctuation = enableAutomaticPunctuation;
	}

	public @Nullable Boolean getEnableSpokenPunctuation() {
		return this.enableSpokenPunctuation;
	}

	public void setEnableSpokenPunctuation(@Nullable Boolean enableSpokenPunctuation) {
		this.enableSpokenPunctuation = enableSpokenPunctuation;
	}

	public @Nullable Boolean getEnableSpokenEmojis() {
		return this.enableSpokenEmojis;
	}

	public void setEnableSpokenEmojis(@Nullable Boolean enableSpokenEmojis) {
		this.enableSpokenEmojis = enableSpokenEmojis;
	}

	public @Nullable Boolean getProfanityFilter() {
		return this.profanityFilter;
	}

	public void setProfanityFilter(@Nullable Boolean profanityFilter) {
		this.profanityFilter = profanityFilter;
	}

	public @Nullable Boolean getEnableWordTimeOffsets() {
		return this.enableWordTimeOffsets;
	}

	public void setEnableWordTimeOffsets(@Nullable Boolean enableWordTimeOffsets) {
		this.enableWordTimeOffsets = enableWordTimeOffsets;
	}

	public @Nullable Boolean getEnableWordConfidence() {
		return this.enableWordConfidence;
	}

	public void setEnableWordConfidence(@Nullable Boolean enableWordConfidence) {
		this.enableWordConfidence = enableWordConfidence;
	}

	public @Nullable Integer getMaxAlternatives() {
		return this.maxAlternatives;
	}

	public void setMaxAlternatives(@Nullable Integer maxAlternatives) {
		this.maxAlternatives = maxAlternatives;
	}

	public @Nullable MultiChannelMode getMultiChannelMode() {
		return this.multiChannelMode;
	}

	public void setMultiChannelMode(@Nullable MultiChannelMode multiChannelMode) {
		this.multiChannelMode = multiChannelMode;
	}

	public @Nullable Boolean getEnableSpeakerDiarization() {
		return this.enableSpeakerDiarization;
	}

	public void setEnableSpeakerDiarization(@Nullable Boolean enableSpeakerDiarization) {
		this.enableSpeakerDiarization = enableSpeakerDiarization;
	}

	public @Nullable Integer getMinSpeakerCount() {
		return this.minSpeakerCount;
	}

	public void setMinSpeakerCount(@Nullable Integer minSpeakerCount) {
		this.minSpeakerCount = minSpeakerCount;
	}

	public @Nullable Integer getMaxSpeakerCount() {
		return this.maxSpeakerCount;
	}

	public void setMaxSpeakerCount(@Nullable Integer maxSpeakerCount) {
		this.maxSpeakerCount = maxSpeakerCount;
	}

	public @Nullable String getTranslationTargetLanguage() {
		return this.translationTargetLanguage;
	}

	public void setTranslationTargetLanguage(@Nullable String translationTargetLanguage) {
		this.translationTargetLanguage = translationTargetLanguage;
	}

	public @Nullable AudioEncoding getEncoding() {
		return this.encoding;
	}

	public void setEncoding(@Nullable AudioEncoding encoding) {
		this.encoding = encoding;
	}

	public @Nullable Integer getSampleRateHertz() {
		return this.sampleRateHertz;
	}

	public void setSampleRateHertz(@Nullable Integer sampleRateHertz) {
		this.sampleRateHertz = sampleRateHertz;
	}

	public @Nullable Integer getAudioChannelCount() {
		return this.audioChannelCount;
	}

	public void setAudioChannelCount(@Nullable Integer audioChannelCount) {
		this.audioChannelCount = audioChannelCount;
	}

	public @Nullable Boolean getDenoiseAudio() {
		return this.denoiseAudio;
	}

	public void setDenoiseAudio(@Nullable Boolean denoiseAudio) {
		this.denoiseAudio = denoiseAudio;
	}

	public @Nullable Float getSnrThreshold() {
		return this.snrThreshold;
	}

	public void setSnrThreshold(@Nullable Float snrThreshold) {
		this.snrThreshold = snrThreshold;
	}

	public @Nullable String getCustomPrompt() {
		return this.customPrompt;
	}

	public void setCustomPrompt(@Nullable String customPrompt) {
		this.customPrompt = customPrompt;
	}

	public GoogleGenAiAudioTranscriptionOptions toOptions() {
		return GoogleGenAiAudioTranscriptionOptions.builder()
			.model(this.model)
			.language(this.language)
			.languageCodes(this.languageCodes)
			.enableAutomaticPunctuation(this.enableAutomaticPunctuation)
			.enableSpokenPunctuation(this.enableSpokenPunctuation)
			.enableSpokenEmojis(this.enableSpokenEmojis)
			.profanityFilter(this.profanityFilter)
			.enableWordTimeOffsets(this.enableWordTimeOffsets)
			.enableWordConfidence(this.enableWordConfidence)
			.maxAlternatives(this.maxAlternatives)
			.multiChannelMode(this.multiChannelMode)
			.enableSpeakerDiarization(this.enableSpeakerDiarization)
			.minSpeakerCount(this.minSpeakerCount)
			.maxSpeakerCount(this.maxSpeakerCount)
			.translationTargetLanguage(this.translationTargetLanguage)
			.encoding(this.encoding)
			.sampleRateHertz(this.sampleRateHertz)
			.audioChannelCount(this.audioChannelCount)
			.denoiseAudio(this.denoiseAudio)
			.snrThreshold(this.snrThreshold)
			.customPrompt(this.customPrompt)
			.build();
	}

}
