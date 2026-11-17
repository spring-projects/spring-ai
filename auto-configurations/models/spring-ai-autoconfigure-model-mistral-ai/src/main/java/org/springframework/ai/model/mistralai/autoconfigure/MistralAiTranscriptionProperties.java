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

package org.springframework.ai.model.mistralai.autoconfigure;

import java.util.List;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.mistralai.api.MistralAiAudioApi.TimestampGranularity;
import org.springframework.ai.mistralai.transcription.MistralAiTranscriptionOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Mistral AI transcription model.
 *
 * @author Thomas Vitale
 * @since 2.1.0
 */
@ConfigurationProperties(MistralAiTranscriptionProperties.CONFIG_PREFIX)
public class MistralAiTranscriptionProperties extends MistralAiParentProperties {

	public static final String CONFIG_PREFIX = "spring.ai.mistralai.audio.transcription";

	/**
	 * ID of the model to use.
	 */
	private @Nullable String model;

	/**
	 * The ISO 639-1 language of the audio, which can improve accuracy.
	 */
	private @Nullable String language;

	/**
	 * The sampling temperature.
	 */
	private @Nullable Double temperature;

	/**
	 * Whether to identify the speaker of each segment, which requires segment timestamps.
	 */
	private @Nullable Boolean diarize;

	/**
	 * Words or phrases to bias the transcription towards, such as names or domain terms.
	 */
	private @Nullable List<String> contextBias;

	/**
	 * The granularities of the timestamps to include in the transcription.
	 */
	private @Nullable List<TimestampGranularity> timestampGranularities;

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

	public @Nullable Double getTemperature() {
		return this.temperature;
	}

	public void setTemperature(@Nullable Double temperature) {
		this.temperature = temperature;
	}

	public @Nullable Boolean getDiarize() {
		return this.diarize;
	}

	public void setDiarize(@Nullable Boolean diarize) {
		this.diarize = diarize;
	}

	public @Nullable List<String> getContextBias() {
		return this.contextBias;
	}

	public void setContextBias(@Nullable List<String> contextBias) {
		this.contextBias = contextBias;
	}

	public @Nullable List<TimestampGranularity> getTimestampGranularities() {
		return this.timestampGranularities;
	}

	public void setTimestampGranularities(@Nullable List<TimestampGranularity> timestampGranularities) {
		this.timestampGranularities = timestampGranularities;
	}

	public MistralAiTranscriptionOptions toOptions() {
		return MistralAiTranscriptionOptions.builder()
			.model(this.model)
			.language(this.language)
			.temperature(this.temperature)
			.diarize(this.diarize)
			.contextBias(this.contextBias)
			.timestampGranularities(this.timestampGranularities)
			.build();
	}

}
