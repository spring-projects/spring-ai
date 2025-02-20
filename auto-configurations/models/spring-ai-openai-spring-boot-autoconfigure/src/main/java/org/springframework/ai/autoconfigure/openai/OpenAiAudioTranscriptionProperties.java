/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.autoconfigure.openai;

import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions;
import org.springframework.ai.openai.api.OpenAiAudioApi;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(OpenAiAudioTranscriptionProperties.CONFIG_PREFIX)
public class OpenAiAudioTranscriptionProperties extends OpenAiParentProperties {

	public static final String CONFIG_PREFIX = "spring.ai.openai.audio.transcription";

	public static final String DEFAULT_TRANSCRIPTION_MODEL = OpenAiAudioApi.WhisperModel.WHISPER_1.getValue();

	private static final Double DEFAULT_TEMPERATURE = 0.7;

	private static final OpenAiAudioApi.TranscriptResponseFormat DEFAULT_RESPONSE_FORMAT = OpenAiAudioApi.TranscriptResponseFormat.TEXT;

	/**
	 * Enable OpenAI audio transcription model.
	 */
	private boolean enabled = true;

	@NestedConfigurationProperty
	private OpenAiAudioTranscriptionOptions options = OpenAiAudioTranscriptionOptions.builder()
		.model(DEFAULT_TRANSCRIPTION_MODEL)
		.temperature(DEFAULT_TEMPERATURE.floatValue())
		.responseFormat(DEFAULT_RESPONSE_FORMAT)
		.build();

	public OpenAiAudioTranscriptionOptions getOptions() {
		return this.options;
	}

	public void setOptions(OpenAiAudioTranscriptionOptions options) {
		this.options = options;
	}

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

}
