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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.audio.transcription.AudioTranscription;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponseMetadata;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.elevenlabs.api.ElevenLabsSpeechToTextApi;
import org.springframework.ai.elevenlabs.metadata.ElevenLabsAudioTranscriptionMetadata;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;

/**
 * ElevenLabs implementation of {@link TranscriptionModel} for audio transcription.
 *
 * @author Alexandros Pappas
 * @since 1.0.0
 */
public class ElevenLabsAudioTranscriptionModel implements TranscriptionModel {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final ElevenLabsSpeechToTextApi api;

	private final ElevenLabsAudioTranscriptionOptions defaultOptions;

	private final RetryTemplate retryTemplate;

	public ElevenLabsAudioTranscriptionModel(ElevenLabsSpeechToTextApi api) {
		this(api, ElevenLabsAudioTranscriptionOptions.builder().modelId("scribe_v1").build());
	}

	public ElevenLabsAudioTranscriptionModel(ElevenLabsSpeechToTextApi api,
			ElevenLabsAudioTranscriptionOptions defaultOptions) {
		this(api, defaultOptions, RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	public ElevenLabsAudioTranscriptionModel(ElevenLabsSpeechToTextApi api,
			ElevenLabsAudioTranscriptionOptions defaultOptions, RetryTemplate retryTemplate) {
		Assert.notNull(api, "ElevenLabsSpeechToTextApi must not be null");
		Assert.notNull(defaultOptions, "ElevenLabsAudioTranscriptionOptions must not be null");
		Assert.notNull(retryTemplate, "RetryTemplate must not be null");
		this.api = api;
		this.defaultOptions = defaultOptions;
		this.retryTemplate = retryTemplate;
	}

	public static Builder builder() {
		return new Builder();
	}

	@Override
	public AudioTranscriptionResponse call(AudioTranscriptionPrompt prompt) {
		ElevenLabsSpeechToTextApi.TranscriptionRequest request = createRequest(prompt);

		ResponseEntity<ElevenLabsSpeechToTextApi.SpeechToTextResponse> responseEntity;
		try {
			responseEntity = this.retryTemplate.execute(() -> this.api.createTranscription(request));
		}
		catch (Exception e) {
			throw new RuntimeException("Error calling ElevenLabs transcription API", e);
		}

		ElevenLabsSpeechToTextApi.SpeechToTextResponse response = responseEntity.getBody();

		if (response == null) {
			logger.warn("No transcription returned for request");
			return new AudioTranscriptionResponse(new AudioTranscription(""));
		}

		AudioTranscription transcription = new AudioTranscription(response.text())
			.withTranscriptionMetadata(ElevenLabsAudioTranscriptionMetadata.from(response));

		return new AudioTranscriptionResponse(transcription, new AudioTranscriptionResponseMetadata());
	}

	/**
	 * Retrieve a previously created transcription by ID. Use when webhook=true was set
	 * during creation.
	 * @param transcriptionId The ID of the transcription to retrieve.
	 * @return The transcription response.
	 */
	public AudioTranscriptionResponse getTranscription(String transcriptionId) {
		Assert.hasText(transcriptionId, "transcriptionId must not be empty");

		ResponseEntity<ElevenLabsSpeechToTextApi.SpeechToTextResponse> responseEntity;
		try {
			responseEntity = this.retryTemplate.execute(() -> this.api.getTranscription(transcriptionId));
		}
		catch (Exception e) {
			throw new RuntimeException("Error retrieving ElevenLabs transcription", e);
		}

		ElevenLabsSpeechToTextApi.SpeechToTextResponse response = responseEntity.getBody();

		if (response == null) {
			logger.warn("No transcription found for ID: {}", transcriptionId);
			return new AudioTranscriptionResponse(new AudioTranscription(""));
		}

		AudioTranscription transcription = new AudioTranscription(response.text())
			.withTranscriptionMetadata(ElevenLabsAudioTranscriptionMetadata.from(response));

		return new AudioTranscriptionResponse(transcription, new AudioTranscriptionResponseMetadata());
	}

	public ElevenLabsAudioTranscriptionOptions getDefaultOptions() {
		return this.defaultOptions;
	}

	private ElevenLabsSpeechToTextApi.TranscriptionRequest createRequest(AudioTranscriptionPrompt prompt) {
		ElevenLabsAudioTranscriptionOptions options = this.defaultOptions;

		if (prompt.getOptions() != null) {
			if (prompt.getOptions() instanceof ElevenLabsAudioTranscriptionOptions runtimeOptions) {
				options = merge(runtimeOptions, options);
			}
			else {
				throw new IllegalArgumentException(
						"Prompt options are not of type ElevenLabsAudioTranscriptionOptions: "
								+ prompt.getOptions().getClass().getSimpleName());
			}
		}

		Resource audioResource = prompt.getInstructions();

		return ElevenLabsSpeechToTextApi.TranscriptionRequest.builder()
			.file(toBytes(audioResource))
			.fileName(audioResource.getFilename())
			.modelId(options.getModelId())
			.languageCode(options.getLanguageCode())
			.temperature(options.getTemperature())
			.tagAudioEvents(options.getTagAudioEvents())
			.numSpeakers(options.getNumSpeakers())
			.timestampsGranularity(options.getTimestampsGranularity())
			.diarize(options.getDiarize())
			.diarizationThreshold(options.getDiarizationThreshold())
			.fileFormat(options.getFileFormat())
			.seed(options.getSeed())
			.webhook(options.getWebhook())
			.webhookId(options.getWebhookId())
			.webhookMetadata(options.getWebhookMetadata())
			.cloudStorageUrl(options.getCloudStorageUrl())
			.enableLogging(options.getEnableLogging())
			.build();
	}

	private byte[] toBytes(Resource resource) {
		try {
			return resource.getInputStream().readAllBytes();
		}
		catch (Exception e) {
			throw new IllegalArgumentException("Failed to read audio resource: " + resource, e);
		}
	}

	private ElevenLabsAudioTranscriptionOptions merge(ElevenLabsAudioTranscriptionOptions runtime,
			ElevenLabsAudioTranscriptionOptions defaults) {
		return ElevenLabsAudioTranscriptionOptions.builder()
			.modelId(getOrDefault(runtime.getModelId(), defaults.getModelId()))
			.languageCode(getOrDefault(runtime.getLanguageCode(), defaults.getLanguageCode()))
			.temperature(getOrDefault(runtime.getTemperature(), defaults.getTemperature()))
			.tagAudioEvents(getOrDefault(runtime.getTagAudioEvents(), defaults.getTagAudioEvents()))
			.numSpeakers(getOrDefault(runtime.getNumSpeakers(), defaults.getNumSpeakers()))
			.timestampsGranularity(
					getOrDefault(runtime.getTimestampsGranularity(), defaults.getTimestampsGranularity()))
			.diarize(getOrDefault(runtime.getDiarize(), defaults.getDiarize()))
			.diarizationThreshold(getOrDefault(runtime.getDiarizationThreshold(), defaults.getDiarizationThreshold()))
			.fileFormat(getOrDefault(runtime.getFileFormat(), defaults.getFileFormat()))
			.seed(getOrDefault(runtime.getSeed(), defaults.getSeed()))
			.webhook(getOrDefault(runtime.getWebhook(), defaults.getWebhook()))
			.webhookId(getOrDefault(runtime.getWebhookId(), defaults.getWebhookId()))
			.webhookMetadata(getOrDefault(runtime.getWebhookMetadata(), defaults.getWebhookMetadata()))
			.cloudStorageUrl(getOrDefault(runtime.getCloudStorageUrl(), defaults.getCloudStorageUrl()))
			.enableLogging(getOrDefault(runtime.getEnableLogging(), defaults.getEnableLogging()))
			.build();
	}

	private <T> T getOrDefault(T runtimeValue, T defaultValue) {
		return runtimeValue != null ? runtimeValue : defaultValue;
	}

	public static final class Builder {

		private ElevenLabsSpeechToTextApi api;

		private ElevenLabsAudioTranscriptionOptions defaultOptions = ElevenLabsAudioTranscriptionOptions.builder()
			.modelId("scribe_v1")
			.build();

		private RetryTemplate retryTemplate = RetryUtils.DEFAULT_RETRY_TEMPLATE;

		public Builder api(ElevenLabsSpeechToTextApi api) {
			this.api = api;
			return this;
		}

		public Builder defaultOptions(ElevenLabsAudioTranscriptionOptions defaultOptions) {
			this.defaultOptions = defaultOptions;
			return this;
		}

		public Builder retryTemplate(RetryTemplate retryTemplate) {
			this.retryTemplate = retryTemplate;
			return this;
		}

		public ElevenLabsAudioTranscriptionModel build() {
			Assert.notNull(this.api, "ElevenLabsSpeechToTextApi must not be null");
			return new ElevenLabsAudioTranscriptionModel(this.api, this.defaultOptions, this.retryTemplate);
		}

	}

}
