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
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Google GenAI (Gemini) Transcription.
 *
 * @author Olivier Le Quellec
 * @since 2.0.1
 */
@ConfigurationProperties(GoogleGenAiTranscriptionProperties.CONFIG_PREFIX)
public class GoogleGenAiTranscriptionProperties {

	public static final String CONFIG_PREFIX = "spring.ai.google.genai.transcription";

	/**
	 * The Gemini transcription model to use.
	 */
	private @Nullable String model = GoogleGenAiAudioTranscriptionOptions.DEFAULT_MODEL_NAME;

	/**
	 * Natural-language instruction guiding the transcription.
	 */
	private @Nullable String prompt;

	/**
	 * BCP-47 language code of the audio (e.g. {@code en-US}).
	 */
	private @Nullable String language;

	/**
	 * Sampling temperature to use for the transcription request.
	 */
	private @Nullable Float temperature;

	/**
	 * The maximum cumulative probability of tokens to consider when sampling (nucleus
	 * sampling).
	 */
	private @Nullable Float topP;

	/**
	 * The maximum number of tokens to consider when sampling (top-k sampling).
	 */
	private @Nullable Float topK;

	/**
	 * Number of transcription candidates to generate.
	 */
	private @Nullable Integer candidateCount;

	/**
	 * The maximum number of tokens to generate in the transcription.
	 */
	private @Nullable Integer maxOutputTokens;

	/**
	 * Sequences where the model stops generating the transcription.
	 */
	private @Nullable List<String> stopSequences;

	/**
	 * The MIME type of the transcription response (e.g. {@code text/plain}).
	 */
	private @Nullable String responseMimeType;

	/**
	 * Penalty applied to tokens based on their presence in the generated transcription.
	 */
	private @Nullable Float presencePenalty;

	/**
	 * Penalty applied to tokens based on their frequency in the generated transcription.
	 */
	private @Nullable Float frequencyPenalty;

	/**
	 * Seed used for deterministic sampling.
	 */
	private @Nullable Integer seed;

	public @Nullable String getModel() {
		return this.model;
	}

	public void setModel(@Nullable String model) {
		this.model = model;
	}

	public @Nullable String getPrompt() {
		return this.prompt;
	}

	public void setPrompt(@Nullable String prompt) {
		this.prompt = prompt;
	}

	public @Nullable String getLanguage() {
		return this.language;
	}

	public void setLanguage(@Nullable String language) {
		this.language = language;
	}

	public @Nullable Float getTemperature() {
		return this.temperature;
	}

	public void setTemperature(@Nullable Float temperature) {
		this.temperature = temperature;
	}

	public @Nullable Float getTopP() {
		return this.topP;
	}

	public void setTopP(@Nullable Float topP) {
		this.topP = topP;
	}

	public @Nullable Float getTopK() {
		return this.topK;
	}

	public void setTopK(@Nullable Float topK) {
		this.topK = topK;
	}

	public @Nullable Integer getCandidateCount() {
		return this.candidateCount;
	}

	public void setCandidateCount(@Nullable Integer candidateCount) {
		this.candidateCount = candidateCount;
	}

	public @Nullable Integer getMaxOutputTokens() {
		return this.maxOutputTokens;
	}

	public void setMaxOutputTokens(@Nullable Integer maxOutputTokens) {
		this.maxOutputTokens = maxOutputTokens;
	}

	public @Nullable List<String> getStopSequences() {
		return this.stopSequences;
	}

	public void setStopSequences(@Nullable List<String> stopSequences) {
		this.stopSequences = stopSequences;
	}

	public @Nullable String getResponseMimeType() {
		return this.responseMimeType;
	}

	public void setResponseMimeType(@Nullable String responseMimeType) {
		this.responseMimeType = responseMimeType;
	}

	public @Nullable Float getPresencePenalty() {
		return this.presencePenalty;
	}

	public void setPresencePenalty(@Nullable Float presencePenalty) {
		this.presencePenalty = presencePenalty;
	}

	public @Nullable Float getFrequencyPenalty() {
		return this.frequencyPenalty;
	}

	public void setFrequencyPenalty(@Nullable Float frequencyPenalty) {
		this.frequencyPenalty = frequencyPenalty;
	}

	public @Nullable Integer getSeed() {
		return this.seed;
	}

	public void setSeed(@Nullable Integer seed) {
		this.seed = seed;
	}

	public GoogleGenAiAudioTranscriptionOptions toOptions() {
		return GoogleGenAiAudioTranscriptionOptions.builder()
			.model(this.model)
			.prompt(this.prompt)
			.language(this.language)
			.temperature(this.temperature)
			.topP(this.topP)
			.topK(this.topK)
			.candidateCount(this.candidateCount)
			.maxOutputTokens(this.maxOutputTokens)
			.stopSequences(this.stopSequences)
			.responseMimeType(this.responseMimeType)
			.presencePenalty(this.presencePenalty)
			.frequencyPenalty(this.frequencyPenalty)
			.seed(this.seed)
			.build();
	}

}
