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
package org.springframework.ai.azure.openai;

import com.azure.ai.openai.models.AudioTranscriptionFormat;
import com.azure.ai.openai.models.AudioTranscriptionTimestampGranularity;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.ai.audio.transcription.AudioTranscriptionOptions;
import org.springframework.util.Assert;

import java.util.List;

/**
 * @author Piotr Olaszewski
 */
@JsonInclude(Include.NON_NULL)
public class AzureOpenAiAudioTranscriptionOptions implements AudioTranscriptionOptions {

	public static final String DEFAULT_AUDIO_TRANSCRIPTION_MODEL = WhisperModel.WHISPER.getValue();

	// @formatter:off
	/**
	 * ID of the model to use.
	 */
	private @JsonProperty("model") String model = DEFAULT_AUDIO_TRANSCRIPTION_MODEL;

	/**
	 * The deployment name as defined in Azure Open AI Studio when creating a deployment
	 * backed by an Azure OpenAI base model.
	 */
	private @JsonProperty(value = "deployment_name") String deploymentName;

	/**
	 * The format of the transcript output, in one of these options: json, text, srt, verbose_json, or vtt.
	 */
	private @JsonProperty("response_format") TranscriptResponseFormat responseFormat = TranscriptResponseFormat.JSON;

	private @JsonProperty("prompt") String prompt;

	private @JsonProperty("language") String language;

	/**
	 * What sampling temperature to use, between 0 and 1. Higher values like 0.8 will make the output
	 * more random, while lower values like 0.2 will make it more focused and deterministic.
	 */
	private @JsonProperty("temperature") Float temperature = 0F;

	private @JsonProperty("timestamp_granularities") List<GranularityType> granularityType;

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		protected AzureOpenAiAudioTranscriptionOptions options;

		public Builder() {
			this.options = new AzureOpenAiAudioTranscriptionOptions();
		}

		public Builder(AzureOpenAiAudioTranscriptionOptions options) {
			this.options = options;
		}

		public Builder withModel(String model) {
			this.options.model = model;
			return this;
		}

		public Builder withDeploymentName(String deploymentName) {
			this.options.setDeploymentName(deploymentName);
			return this;
		}

		public Builder withLanguage(String language) {
			this.options.language = language;
			return this;
		}

		public Builder withPrompt(String prompt) {
			this.options.prompt = prompt;
			return this;
		}

		public Builder withResponseFormat(TranscriptResponseFormat responseFormat) {
			this.options.responseFormat = responseFormat;
			return this;
		}

		public Builder withTemperature(Float temperature) {
			this.options.temperature = temperature;
			return this;
		}

		public Builder withGranularityType(List<GranularityType> granularityType) {
			this.options.granularityType = granularityType;
			return this;
		}

		public AzureOpenAiAudioTranscriptionOptions build() {
			Assert.hasText(options.model, "model must not be empty");
			Assert.notNull(options.responseFormat, "response_format must not be null");

			return this.options;
		}

	}

	@Override
	public String getModel() {
		return this.model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public String getDeploymentName() {
		return deploymentName;
	}

	public void setDeploymentName(String deploymentName) {
		this.deploymentName = deploymentName;
	}

	public String getLanguage() {
		return this.language;
	}

	public void setLanguage(String language) {
		this.language = language;
	}

	public String getPrompt() {
		return this.prompt;
	}

	public void setPrompt(String prompt) {
		this.prompt = prompt;
	}

	public Float getTemperature() {
		return this.temperature;
	}

	public void setTemperature(Float temperature) {
		this.temperature = temperature;
	}


	public TranscriptResponseFormat getResponseFormat() {
		return this.responseFormat;
	}

	public void setResponseFormat(TranscriptResponseFormat responseFormat) {
		this.responseFormat = responseFormat;
	}

	public List<GranularityType> getGranularityType() {
		return this.granularityType;
	}

	public void setGranularityType(List<GranularityType> granularityType) {
		this.granularityType = granularityType;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((model == null) ? 0 : model.hashCode());
		result = prime * result + ((prompt == null) ? 0 : prompt.hashCode());
		result = prime * result + ((language == null) ? 0 : language.hashCode());
		result = prime * result + ((responseFormat == null) ? 0 : responseFormat.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		AzureOpenAiAudioTranscriptionOptions other = (AzureOpenAiAudioTranscriptionOptions) obj;
		if (this.model == null) {
			if (other.model != null)
				return false;
		}
		else if (!model.equals(other.model))
			return false;
		if (this.prompt == null) {
			if (other.prompt != null)
				return false;
		}
		else if (!this.prompt.equals(other.prompt))
			return false;
		if (this.language == null) {
			if (other.language != null)
				return false;
		}
		else if (!this.language.equals(other.language))
			return false;
		if (this.responseFormat == null) {
			return other.responseFormat==null;
		}
		else return this.responseFormat.equals(other.responseFormat);
	}

	public enum WhisperModel {

		// @formatter:off
		@JsonProperty("whisper") WHISPER("whisper");
		// @formatter:on

		public final String value;

		WhisperModel(String value) {
			this.value = value;
		}

		public String getValue() {
			return value;
		}

	}

	public enum TranscriptResponseFormat {

		// @formatter:off
		@JsonProperty("json") JSON(AudioTranscriptionFormat.JSON),
		@JsonProperty("text") TEXT(AudioTranscriptionFormat.TEXT),
		@JsonProperty("srt") SRT(AudioTranscriptionFormat.SRT),
		@JsonProperty("verbose_json") VERBOSE_JSON(AudioTranscriptionFormat.VERBOSE_JSON),
		@JsonProperty("vtt") VTT(AudioTranscriptionFormat.VTT);

		public final AudioTranscriptionFormat value;

		TranscriptResponseFormat(AudioTranscriptionFormat value) {
			this.value = value;
		}

		public AudioTranscriptionFormat getValue() {
			return this.value;
		}
	}

	public enum GranularityType {

		// @formatter:off
		@JsonProperty("word") WORD(AudioTranscriptionTimestampGranularity.WORD),
		@JsonProperty("segment") SEGMENT(AudioTranscriptionTimestampGranularity.SEGMENT);
		// @formatter:on

		public final AudioTranscriptionTimestampGranularity value;

		GranularityType(AudioTranscriptionTimestampGranularity value) {
			this.value = value;
		}

		public AudioTranscriptionTimestampGranularity getValue() {
			return this.value;
		}

	}

}
