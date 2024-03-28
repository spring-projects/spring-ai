/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.autoconfigure.openai;

import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions;
import org.springframework.ai.openai.api.OpenAiAudioApi;
import org.springframework.ai.openai.api.OpenAiAudioApi.TranscriptResponseFormat;
import org.springframework.ai.openai.api.OpenAiAudioApi.TranscriptionRequest.GranularityType;

/**
 * @author Michael Lavelle
 * @author Christian Tzolov
 * @since 0.8.1
 */
public class OpenAiAudioTranscriptionOptionProperties implements OpenAiAudioTranscriptionOptions {

	public static final String DEFAULT_TRANSCRIPTION_MODEL = OpenAiAudioApi.WhisperModel.WHISPER_1.getValue();

	private static final Double DEFAULT_TEMPERATURE = 0.7;

	private static final OpenAiAudioApi.TranscriptResponseFormat DEFAULT_RESPONSE_FORMAT = OpenAiAudioApi.TranscriptResponseFormat.TEXT;

	private String model = DEFAULT_TRANSCRIPTION_MODEL;

	private Float temperature = DEFAULT_TEMPERATURE.floatValue();

	private TranscriptResponseFormat responseFormat = DEFAULT_RESPONSE_FORMAT;

	private String prompt;

	private String language;

	private GranularityType granularityType;

	@Override
	public String getModel() {
		return this.model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	@Override
	public String getLanguage() {
		return this.language;
	}

	public void setLanguage(String language) {
		this.language = language;
	}

	@Override
	public String getPrompt() {
		return this.prompt;
	}

	public void setPrompt(String prompt) {
		this.prompt = prompt;
	}

	@Override
	public Float getTemperature() {
		return this.temperature;
	}

	public void setTemperature(Float temperature) {
		this.temperature = temperature;
	}

	@Override
	public TranscriptResponseFormat getResponseFormat() {
		return this.responseFormat;
	}

	public void setResponseFormat(TranscriptResponseFormat responseFormat) {
		this.responseFormat = responseFormat;
	}

	@Override
	public GranularityType getGranularityType() {
		return this.granularityType;
	}

	public void setGranularityType(GranularityType granularityType) {
		this.granularityType = granularityType;
	}

}
