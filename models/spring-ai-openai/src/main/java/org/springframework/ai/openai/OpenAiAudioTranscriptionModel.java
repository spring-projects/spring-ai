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

package org.springframework.ai.openai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.audio.transcription.AudioTranscription;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.chat.metadata.RateLimit;
import org.springframework.ai.openai.api.OpenAiAudioApi;
import org.springframework.ai.openai.api.OpenAiAudioApi.StructuredResponse;
import org.springframework.ai.openai.metadata.audio.OpenAiAudioTranscriptionResponseMetadata;
import org.springframework.ai.openai.metadata.support.OpenAiResponseHeaderExtractor;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;

/**
 * OpenAI audio transcription client implementation for backed by {@link OpenAiAudioApi}.
 * You provide as input the audio file you want to transcribe and the desired output file
 * format of the transcription of the audio.
 *
 * @author Michael Lavelle
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @see OpenAiAudioApi
 * @since 0.8.1
 */
public class OpenAiAudioTranscriptionModel implements TranscriptionModel {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final OpenAiAudioTranscriptionOptions defaultOptions;

	private final RetryTemplate retryTemplate;

	private final OpenAiAudioApi audioApi;

	/**
	 * OpenAiAudioTranscriptionModel is a client class used to interact with the OpenAI
	 * Audio Transcription API.
	 * @param audioApi The OpenAiAudioApi instance to be used for making API calls.
	 */
	public OpenAiAudioTranscriptionModel(OpenAiAudioApi audioApi) {
		this(audioApi,
				OpenAiAudioTranscriptionOptions.builder()
					.model(OpenAiAudioApi.TranscriptionModels.WHISPER_1.getValue())
					.responseFormat(OpenAiAudioApi.TranscriptResponseFormat.JSON)
					.temperature(0.7f)
					.build());
	}

	/**
	 * OpenAiAudioTranscriptionModel is a client class used to interact with the OpenAI
	 * Audio Transcription API.
	 * @param audioApi The OpenAiAudioApi instance to be used for making API calls.
	 * @param options The OpenAiAudioTranscriptionOptions instance for configuring the
	 * audio transcription.
	 */
	public OpenAiAudioTranscriptionModel(OpenAiAudioApi audioApi, OpenAiAudioTranscriptionOptions options) {
		this(audioApi, options, RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	/**
	 * OpenAiAudioTranscriptionModel is a client class used to interact with the OpenAI
	 * Audio Transcription API.
	 * @param audioApi The OpenAiAudioApi instance to be used for making API calls.
	 * @param options The OpenAiAudioTranscriptionOptions instance for configuring the
	 * audio transcription.
	 * @param retryTemplate The RetryTemplate instance for retrying failed API calls.
	 */
	public OpenAiAudioTranscriptionModel(OpenAiAudioApi audioApi, OpenAiAudioTranscriptionOptions options,
			RetryTemplate retryTemplate) {
		Assert.notNull(audioApi, "OpenAiAudioApi must not be null");
		Assert.notNull(options, "OpenAiTranscriptionOptions must not be null");
		Assert.notNull(retryTemplate, "RetryTemplate must not be null");
		this.audioApi = audioApi;
		this.defaultOptions = options;
		this.retryTemplate = retryTemplate;
	}

	public String call(Resource audioResource) {
		AudioTranscriptionPrompt transcriptionRequest = new AudioTranscriptionPrompt(audioResource);
		return call(transcriptionRequest).getResult().getOutput();
	}

	@Override
	public AudioTranscriptionResponse call(AudioTranscriptionPrompt transcriptionPrompt) {

		Resource audioResource = transcriptionPrompt.getInstructions();

		OpenAiAudioApi.TranscriptionRequest request = createRequest(transcriptionPrompt);

		if (request.responseFormat().isJsonType()) {

			ResponseEntity<StructuredResponse> transcriptionEntity;
			try {
				transcriptionEntity = this.retryTemplate
					.execute(() -> this.audioApi.createTranscription(request, StructuredResponse.class));
			}
			catch (Exception e) {
				throw new RuntimeException("Error calling OpenAI transcription API", e);
			}

			var transcription = transcriptionEntity.getBody();

			if (transcription == null) {
				logger.warn("No transcription returned for request: {}", audioResource);
				return new AudioTranscriptionResponse(null);
			}

			AudioTranscription transcript = new AudioTranscription(transcription.text());

			RateLimit rateLimits = OpenAiResponseHeaderExtractor.extractAiResponseHeaders(transcriptionEntity);

			return new AudioTranscriptionResponse(transcript,
					OpenAiAudioTranscriptionResponseMetadata.from(transcriptionEntity.getBody())
						.withRateLimit(rateLimits));

		}
		else {

			ResponseEntity<String> transcriptionEntity;
			try {
				transcriptionEntity = this.retryTemplate
					.execute(() -> this.audioApi.createTranscription(request, String.class));
			}
			catch (Exception e) {
				throw new RuntimeException("Error calling OpenAI transcription API", e);
			}

			var transcription = transcriptionEntity.getBody();

			if (transcription == null) {
				logger.warn("No transcription returned for request: {}", audioResource);
				return new AudioTranscriptionResponse(null);
			}

			AudioTranscription transcript = new AudioTranscription(transcription);

			RateLimit rateLimits = OpenAiResponseHeaderExtractor.extractAiResponseHeaders(transcriptionEntity);

			return new AudioTranscriptionResponse(transcript,
					OpenAiAudioTranscriptionResponseMetadata.from(transcriptionEntity.getBody())
						.withRateLimit(rateLimits));
		}
	}

	OpenAiAudioApi.TranscriptionRequest createRequest(AudioTranscriptionPrompt transcriptionPrompt) {

		OpenAiAudioTranscriptionOptions options = this.defaultOptions;

		if (transcriptionPrompt.getOptions() != null) {
			if (transcriptionPrompt.getOptions() instanceof OpenAiAudioTranscriptionOptions runtimeOptions) {
				options = this.merge(runtimeOptions, options);
			}
			else {
				throw new IllegalArgumentException("Prompt options are not of type TranscriptionOptions: "
						+ transcriptionPrompt.getOptions().getClass().getSimpleName());
			}
		}

		Resource instructions = transcriptionPrompt.getInstructions();
		return OpenAiAudioApi.TranscriptionRequest.builder()
			.file(toBytes(instructions))
			.fileName(instructions.getFilename())
			.responseFormat(options.getResponseFormat())
			.prompt(options.getPrompt())
			.temperature(options.getTemperature())
			.language(options.getLanguage())
			.model(options.getModel())
			.granularityType(options.getGranularityType())
			.build();
	}

	private byte[] toBytes(Resource resource) {
		try {
			return resource.getInputStream().readAllBytes();
		}
		catch (Exception e) {
			throw new IllegalArgumentException("Failed to read resource: " + resource, e);
		}
	}

	private OpenAiAudioTranscriptionOptions merge(OpenAiAudioTranscriptionOptions source,
			OpenAiAudioTranscriptionOptions target) {

		if (source == null) {
			source = new OpenAiAudioTranscriptionOptions();
		}

		OpenAiAudioTranscriptionOptions merged = new OpenAiAudioTranscriptionOptions();
		merged.setLanguage(source.getLanguage() != null ? source.getLanguage() : target.getLanguage());
		merged.setModel(source.getModel() != null ? source.getModel() : target.getModel());
		merged.setPrompt(source.getPrompt() != null ? source.getPrompt() : target.getPrompt());
		merged.setResponseFormat(
				source.getResponseFormat() != null ? source.getResponseFormat() : target.getResponseFormat());
		merged.setTemperature(source.getTemperature() != null ? source.getTemperature() : target.getTemperature());
		merged.setGranularityType(
				source.getGranularityType() != null ? source.getGranularityType() : target.getGranularityType());
		return merged;
	}

}
