/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.elevenlabs;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.ai.audio.transcription.AudioTranscriptionOptions;
import org.springframework.ai.elevenlabs.api.ElevenLabsSpeechToTextApi;

/**
 * Options for ElevenLabs audio transcription.
 *
 * @author Alexandros Pappas
 * @since 1.0.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ElevenLabsAudioTranscriptionOptions implements AudioTranscriptionOptions {

	@JsonProperty("model_id")
	private String modelId;

	@JsonProperty("language_code")
	private String languageCode;

	@JsonProperty("temperature")
	private Float temperature;

	@JsonProperty("tag_audio_events")
	private Boolean tagAudioEvents;

	@JsonProperty("num_speakers")
	private Integer numSpeakers;

	@JsonProperty("timestamps_granularity")
	private ElevenLabsSpeechToTextApi.TimestampsGranularity timestampsGranularity;

	@JsonProperty("diarize")
	private Boolean diarize;

	@JsonProperty("diarization_threshold")
	private Float diarizationThreshold;

	@JsonProperty("file_format")
	private ElevenLabsSpeechToTextApi.FileFormat fileFormat;

	@JsonProperty("seed")
	private Integer seed;

	@JsonProperty("webhook")
	private Boolean webhook;

	@JsonProperty("webhook_id")
	private String webhookId;

	@JsonProperty("webhook_metadata")
	private Map<String, Object> webhookMetadata;

	@JsonProperty("cloud_storage_url")
	private String cloudStorageUrl;

	@JsonProperty("enable_logging")
	private Boolean enableLogging;

	public static Builder builder() {
		return new Builder();
	}

	@Override
	@JsonIgnore
	public String getModel() {
		return getModelId();
	}

	@JsonIgnore
	public void setModel(String model) {
		setModelId(model);
	}

	public String getModelId() {
		return this.modelId;
	}

	public void setModelId(String modelId) {
		this.modelId = modelId;
	}

	public String getLanguageCode() {
		return this.languageCode;
	}

	public void setLanguageCode(String languageCode) {
		this.languageCode = languageCode;
	}

	public Float getTemperature() {
		return this.temperature;
	}

	public void setTemperature(Float temperature) {
		this.temperature = temperature;
	}

	public Boolean getTagAudioEvents() {
		return this.tagAudioEvents;
	}

	public void setTagAudioEvents(Boolean tagAudioEvents) {
		this.tagAudioEvents = tagAudioEvents;
	}

	public Integer getNumSpeakers() {
		return this.numSpeakers;
	}

	public void setNumSpeakers(Integer numSpeakers) {
		this.numSpeakers = numSpeakers;
	}

	public ElevenLabsSpeechToTextApi.TimestampsGranularity getTimestampsGranularity() {
		return this.timestampsGranularity;
	}

	public void setTimestampsGranularity(ElevenLabsSpeechToTextApi.TimestampsGranularity timestampsGranularity) {
		this.timestampsGranularity = timestampsGranularity;
	}

	public Boolean getDiarize() {
		return this.diarize;
	}

	public void setDiarize(Boolean diarize) {
		this.diarize = diarize;
	}

	public Float getDiarizationThreshold() {
		return this.diarizationThreshold;
	}

	public void setDiarizationThreshold(Float diarizationThreshold) {
		this.diarizationThreshold = diarizationThreshold;
	}

	public ElevenLabsSpeechToTextApi.FileFormat getFileFormat() {
		return this.fileFormat;
	}

	public void setFileFormat(ElevenLabsSpeechToTextApi.FileFormat fileFormat) {
		this.fileFormat = fileFormat;
	}

	public Integer getSeed() {
		return this.seed;
	}

	public void setSeed(Integer seed) {
		this.seed = seed;
	}

	public Boolean getWebhook() {
		return this.webhook;
	}

	public void setWebhook(Boolean webhook) {
		this.webhook = webhook;
	}

	public String getWebhookId() {
		return this.webhookId;
	}

	public void setWebhookId(String webhookId) {
		this.webhookId = webhookId;
	}

	public Map<String, Object> getWebhookMetadata() {
		return this.webhookMetadata;
	}

	public void setWebhookMetadata(Map<String, Object> webhookMetadata) {
		this.webhookMetadata = webhookMetadata;
	}

	public String getCloudStorageUrl() {
		return this.cloudStorageUrl;
	}

	public void setCloudStorageUrl(String cloudStorageUrl) {
		this.cloudStorageUrl = cloudStorageUrl;
	}

	public Boolean getEnableLogging() {
		return this.enableLogging;
	}

	public void setEnableLogging(Boolean enableLogging) {
		this.enableLogging = enableLogging;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof ElevenLabsAudioTranscriptionOptions that)) {
			return false;
		}
		return Objects.equals(this.modelId, that.modelId) && Objects.equals(this.languageCode, that.languageCode)
				&& Objects.equals(this.temperature, that.temperature)
				&& Objects.equals(this.tagAudioEvents, that.tagAudioEvents)
				&& Objects.equals(this.numSpeakers, that.numSpeakers)
				&& Objects.equals(this.timestampsGranularity, that.timestampsGranularity)
				&& Objects.equals(this.diarize, that.diarize)
				&& Objects.equals(this.diarizationThreshold, that.diarizationThreshold)
				&& Objects.equals(this.fileFormat, that.fileFormat) && Objects.equals(this.seed, that.seed)
				&& Objects.equals(this.webhook, that.webhook) && Objects.equals(this.webhookId, that.webhookId)
				&& Objects.equals(this.webhookMetadata, that.webhookMetadata)
				&& Objects.equals(this.cloudStorageUrl, that.cloudStorageUrl)
				&& Objects.equals(this.enableLogging, that.enableLogging);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.modelId, this.languageCode, this.temperature, this.tagAudioEvents, this.numSpeakers,
				this.timestampsGranularity, this.diarize, this.diarizationThreshold, this.fileFormat, this.seed,
				this.webhook, this.webhookId, this.webhookMetadata, this.cloudStorageUrl, this.enableLogging);
	}

	@Override
	public String toString() {
		return "ElevenLabsAudioTranscriptionOptions{" + "modelId='" + this.modelId + '\'' + ", languageCode='"
				+ this.languageCode + '\'' + ", temperature=" + this.temperature + ", tagAudioEvents="
				+ this.tagAudioEvents + ", numSpeakers=" + this.numSpeakers + ", timestampsGranularity="
				+ this.timestampsGranularity + ", diarize=" + this.diarize + ", diarizationThreshold="
				+ this.diarizationThreshold + ", fileFormat=" + this.fileFormat + ", seed=" + this.seed + ", webhook="
				+ this.webhook + ", webhookId='" + this.webhookId + '\'' + ", cloudStorageUrl='" + this.cloudStorageUrl
				+ '\'' + ", enableLogging=" + this.enableLogging + '}';
	}

	public ElevenLabsAudioTranscriptionOptions copy() {
		return ElevenLabsAudioTranscriptionOptions.builder()
			.modelId(this.modelId)
			.languageCode(this.languageCode)
			.temperature(this.temperature)
			.tagAudioEvents(this.tagAudioEvents)
			.numSpeakers(this.numSpeakers)
			.timestampsGranularity(this.timestampsGranularity)
			.diarize(this.diarize)
			.diarizationThreshold(this.diarizationThreshold)
			.fileFormat(this.fileFormat)
			.seed(this.seed)
			.webhook(this.webhook)
			.webhookId(this.webhookId)
			.webhookMetadata(this.webhookMetadata != null ? new HashMap<>(this.webhookMetadata) : null)
			.cloudStorageUrl(this.cloudStorageUrl)
			.enableLogging(this.enableLogging)
			.build();
	}

	public static final class Builder {

		private final ElevenLabsAudioTranscriptionOptions options = new ElevenLabsAudioTranscriptionOptions();

		public Builder model(String model) {
			this.options.setModel(model);
			return this;
		}

		public Builder modelId(String modelId) {
			this.options.setModelId(modelId);
			return this;
		}

		public Builder languageCode(String languageCode) {
			this.options.setLanguageCode(languageCode);
			return this;
		}

		public Builder temperature(Float temperature) {
			this.options.setTemperature(temperature);
			return this;
		}

		public Builder tagAudioEvents(Boolean tagAudioEvents) {
			this.options.setTagAudioEvents(tagAudioEvents);
			return this;
		}

		public Builder numSpeakers(Integer numSpeakers) {
			this.options.setNumSpeakers(numSpeakers);
			return this;
		}

		public Builder timestampsGranularity(ElevenLabsSpeechToTextApi.TimestampsGranularity timestampsGranularity) {
			this.options.setTimestampsGranularity(timestampsGranularity);
			return this;
		}

		public Builder diarize(Boolean diarize) {
			this.options.setDiarize(diarize);
			return this;
		}

		public Builder diarizationThreshold(Float diarizationThreshold) {
			this.options.setDiarizationThreshold(diarizationThreshold);
			return this;
		}

		public Builder fileFormat(ElevenLabsSpeechToTextApi.FileFormat fileFormat) {
			this.options.setFileFormat(fileFormat);
			return this;
		}

		public Builder seed(Integer seed) {
			this.options.setSeed(seed);
			return this;
		}

		public Builder webhook(Boolean webhook) {
			this.options.setWebhook(webhook);
			return this;
		}

		public Builder webhookId(String webhookId) {
			this.options.setWebhookId(webhookId);
			return this;
		}

		public Builder webhookMetadata(Map<String, Object> webhookMetadata) {
			this.options.setWebhookMetadata(webhookMetadata);
			return this;
		}

		public Builder cloudStorageUrl(String cloudStorageUrl) {
			this.options.setCloudStorageUrl(cloudStorageUrl);
			return this;
		}

		public Builder enableLogging(Boolean enableLogging) {
			this.options.setEnableLogging(enableLogging);
			return this;
		}

		public ElevenLabsAudioTranscriptionOptions build() {
			return this.options;
		}

	}

}
