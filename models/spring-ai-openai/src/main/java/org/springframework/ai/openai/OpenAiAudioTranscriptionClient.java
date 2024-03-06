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
/*
* Copyright 2024-2024 the original author or authors.
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

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.metadata.RateLimit;
import org.springframework.ai.model.ModelClient;
import org.springframework.ai.openai.api.OpenAiAudioApi;
import org.springframework.ai.openai.api.OpenAiAudioApi.StructuredResponse;
import org.springframework.ai.openai.api.common.OpenAiApiException;
import org.springframework.ai.openai.audio.transcription.AudioTranscription;
import org.springframework.ai.openai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.openai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.openai.metadata.audio.OpenAiAudioTranscriptionResponseMetadata;
import org.springframework.ai.openai.metadata.support.OpenAiResponseHeaderExtractor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;

/**
 * OpenAI audio transcription client implementation for backed by {@link OpenAiAudioApi}.
 *
 * @author Michael Lavelle
 * @author Christian Tzolov
 * @see OpenAiAudioApi
 * @since 0.8.1
 */
public class OpenAiAudioTranscriptionClient
		implements ModelClient<AudioTranscriptionPrompt, AudioTranscriptionResponse> {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final OpenAiAudioTranscriptionOptions defaultOptions;

	public final RetryTemplate retryTemplate = RetryTemplate.builder()
		.maxAttempts(10)
		.retryOn(OpenAiApiException.class)
		.exponentialBackoff(Duration.ofMillis(2000), 5, Duration.ofMillis(3 * 60000))
		.build();

	private final OpenAiAudioApi audioApi;

	public OpenAiAudioTranscriptionClient(OpenAiAudioApi audioApi) {
		this(audioApi,
				OpenAiAudioTranscriptionOptions.builder()
					.withModel(OpenAiAudioApi.WhisperModel.WHISPER_1.getValue())
					.withResponseFormat(OpenAiAudioApi.TranscriptResponseFormat.JSON)
					.withTemperature(0.7f)
					.build());
	}

	public OpenAiAudioTranscriptionClient(OpenAiAudioApi audioApi, OpenAiAudioTranscriptionOptions options) {
		Assert.notNull(audioApi, "OpenAiAudioApi must not be null");
		Assert.notNull(options, "OpenAiTranscriptionOptions must not be null");
		this.audioApi = audioApi;
		this.defaultOptions = options;
	}

	public String call(Resource audioResource) {
		AudioTranscriptionPrompt transcriptionRequest = new AudioTranscriptionPrompt(audioResource);
		return call(transcriptionRequest).getResult().getOutput();
	}

	@Override
	public AudioTranscriptionResponse call(AudioTranscriptionPrompt request) {

		return this.retryTemplate.execute(ctx -> {

			Resource audioResource = request.getInstructions();

			OpenAiAudioApi.TranscriptionRequest requestBody = createRequestBody(request);

			if (requestBody.responseFormat().isJsonType()) {

				ResponseEntity<StructuredResponse> transcriptionEntity = this.audioApi.createTranscription(requestBody,
						StructuredResponse.class);

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

				ResponseEntity<String> transcriptionEntity = this.audioApi.createTranscription(requestBody,
						String.class);

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
		});
	}

	OpenAiAudioApi.TranscriptionRequest createRequestBody(AudioTranscriptionPrompt request) {

		OpenAiAudioTranscriptionOptions options = this.defaultOptions;

		if (request.getOptions() != null) {
			if (request.getOptions() instanceof OpenAiAudioTranscriptionOptions runtimeOptions) {
				options = this.merge(options, runtimeOptions);
			}
			else {
				throw new IllegalArgumentException("Prompt options are not of type TranscriptionOptions: "
						+ request.getOptions().getClass().getSimpleName());
			}
		}

		OpenAiAudioApi.TranscriptionRequest audioTranscriptionRequest = OpenAiAudioApi.TranscriptionRequest.builder()
			.withFile(toBytes(request.getInstructions()))
			.withResponseFormat(options.getResponseFormat())
			.withPrompt(options.getPrompt())
			.withTemperature(options.getTemperature())
			.withLanguage(options.getLanguage())
			.withModel(options.getModel())
			.build();

		return audioTranscriptionRequest;
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
		return merged;
	}

}
