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

package org.springframework.ai.model.openai.autoconfigure;

import java.util.List;

import com.openai.models.audio.AudioResponseFormat;
import com.openai.models.audio.transcriptions.TranscriptionCreateParams.TimestampGranularity;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;

/**
 * Configuration properties for OpenAI SDK audio transcription.
 *
 * @author Michael Lavelle
 * @author Christian Tzolov
 * @author Piotr Olaszewski
 * @author Ilayaperumal Gopinathan
 */
@ConfigurationProperties(OpenAiAudioTranscriptionProperties.CONFIG_PREFIX)
public class OpenAiAudioTranscriptionProperties extends AbstractOpenAiProperties {

	public static final String CONFIG_PREFIX = "spring.ai.openai.audio.transcription";

	private @Nullable String model = OpenAiAudioTranscriptionOptions.DEFAULT_TRANSCRIPTION_MODEL;

	private AudioResponseFormat responseFormat = OpenAiAudioTranscriptionOptions.DEFAULT_RESPONSE_FORMAT;

	private @Nullable String prompt;

	private @Nullable String language;

	private @Nullable Float temperature;

	private @Nullable List<TimestampGranularity> timestampGranularities;

	public @Nullable String getModel() {
		return this.model;
	}

	public void setModel(@Nullable String model) {
		this.model = model;
	}

	public AudioResponseFormat getResponseFormat() {
		return this.responseFormat;
	}

	public void setResponseFormat(AudioResponseFormat responseFormat) {
		this.responseFormat = responseFormat;
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

	public @Nullable List<TimestampGranularity> getTimestampGranularities() {
		return this.timestampGranularities;
	}

	public void setTimestampGranularities(@Nullable List<TimestampGranularity> timestampGranularities) {
		this.timestampGranularities = timestampGranularities;
	}

	public OpenAiAudioTranscriptionOptions toOptions() {
		OpenAiAudioTranscriptionOptions.Builder builder = OpenAiAudioTranscriptionOptions.builder();
		builder.model(this.getModel());
		if (this.responseFormat != null) {
			builder.responseFormat(this.responseFormat);
		}
		if (this.prompt != null) {
			builder.prompt(this.prompt);
		}
		if (this.language != null) {
			builder.language(this.language);
		}
		if (this.temperature != null) {
			builder.temperature(this.temperature);
		}
		if (this.timestampGranularities != null) {
			builder.timestampGranularities(this.timestampGranularities);
		}
		return builder.build();
	}

	private Options options = new Options();

	@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.audio.transcription")
	@Deprecated(since = "2.0.0", forRemoval = true)
	public Options getOptions() {
		return this.options;
	}

	public void setOptions(Options options) {
		this.options = options;
	}

	public class Options {

		@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.audio.transcription.model")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable String getModel() {
			return OpenAiAudioTranscriptionProperties.this.getModel();
		}

		public void setModel(@Nullable String model) {
			OpenAiAudioTranscriptionProperties.this.setModel(model);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.audio.transcription.response-format")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public AudioResponseFormat getResponseFormat() {
			return OpenAiAudioTranscriptionProperties.this.getResponseFormat();
		}

		public void setResponseFormat(AudioResponseFormat responseFormat) {
			OpenAiAudioTranscriptionProperties.this.setResponseFormat(responseFormat);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.audio.transcription.prompt")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable String getPrompt() {
			return OpenAiAudioTranscriptionProperties.this.getPrompt();
		}

		public void setPrompt(@Nullable String prompt) {
			OpenAiAudioTranscriptionProperties.this.setPrompt(prompt);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.audio.transcription.language")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable String getLanguage() {
			return OpenAiAudioTranscriptionProperties.this.getLanguage();
		}

		public void setLanguage(@Nullable String language) {
			OpenAiAudioTranscriptionProperties.this.setLanguage(language);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.audio.transcription.temperature")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Float getTemperature() {
			return OpenAiAudioTranscriptionProperties.this.getTemperature();
		}

		public void setTemperature(@Nullable Float temperature) {
			OpenAiAudioTranscriptionProperties.this.setTemperature(temperature);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.audio.transcription.timestamp-granularities")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable List<TimestampGranularity> getTimestampGranularities() {
			return OpenAiAudioTranscriptionProperties.this.getTimestampGranularities();
		}

		public void setTimestampGranularities(@Nullable List<TimestampGranularity> timestampGranularities) {
			OpenAiAudioTranscriptionProperties.this.setTimestampGranularities(timestampGranularities);
		}

	}

}
