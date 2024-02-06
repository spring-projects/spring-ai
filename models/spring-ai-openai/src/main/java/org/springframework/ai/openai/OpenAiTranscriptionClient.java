package org.springframework.ai.openai;/*
										* Copyright 2023-2023 the original author or authors.
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.ChatOptions;
import org.springframework.ai.chat.metadata.RateLimit;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.OpenAiApi.OpenAiApiException;
import org.springframework.ai.openai.metadata.OpenAiTranscriptionResponseMetadata;
import org.springframework.ai.openai.metadata.support.OpenAiResponseHeaderExtractor;
import org.springframework.ai.transcription.*;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.time.Duration;
import java.util.List;

/**
 * {@link TranscriptionClient} implementation for {@literal OpenAI} backed by
 * {@link OpenAiApi}.
 *
 * @author Michael Lavelle
 * @see TranscriptionClient
 * @see OpenAiApi
 */
public class OpenAiTranscriptionClient implements TranscriptionClient {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private OpenAiTranscriptionOptions defaultOptions = OpenAiTranscriptionOptions.builder()
		.withModel("whisper-1")
		.withTemperature(0.7f)
		.build();

	public final RetryTemplate retryTemplate = RetryTemplate.builder()
		.maxAttempts(10)
		.retryOn(OpenAiApiException.class)
		.exponentialBackoff(Duration.ofMillis(2000), 5, Duration.ofMillis(3 * 60000))
		.build();

	private final OpenAiApi openAiApi;

	public OpenAiTranscriptionClient(OpenAiApi openAiApi) {
		Assert.notNull(openAiApi, "OpenAiApi must not be null");
		this.openAiApi = openAiApi;
	}

	public OpenAiTranscriptionClient withDefaultOptions(OpenAiTranscriptionOptions options) {
		this.defaultOptions = options;
		return this;
	}

	@Override
	public TranscriptionResponse call(TranscriptionRequest request) {

		return this.retryTemplate.execute(ctx -> {
			Resource audioResource = request.getInstructions();

			MultiValueMap<String, Object> requestBody = createRequestBody(request);

			boolean jsonResponse = !requestBody.containsKey("response_format")
					|| requestBody.get("response_format").contains("json");

			if (jsonResponse) {

				ResponseEntity<OpenAiApi.Transcription> transcriptionEntity = this.openAiApi
					.transcriptionEntityJson(requestBody);

				var transcription = transcriptionEntity.getBody();

				if (transcription == null) {
					logger.warn("No transcription returned for request: {}", audioResource);
					return new TranscriptionResponse(null);
				}

				Transcript transcript = new Transcript(transcription.text());

				RateLimit rateLimits = OpenAiResponseHeaderExtractor.extractAiResponseHeaders(transcriptionEntity);

				return new TranscriptionResponse(transcript,
						OpenAiTranscriptionResponseMetadata.from(transcriptionEntity.getBody())
							.withRateLimit(rateLimits));

			}
			else {
				ResponseEntity<String> transcriptionEntity = this.openAiApi.transcriptionEntityText(requestBody);

				var transcription = transcriptionEntity.getBody();

				if (transcription == null) {
					logger.warn("No transcription returned for request: {}", audioResource);
					return new TranscriptionResponse(null);
				}

				Transcript transcript = new Transcript(transcription);

				RateLimit rateLimits = OpenAiResponseHeaderExtractor.extractAiResponseHeaders(transcriptionEntity);

				return new TranscriptionResponse(transcript,
						OpenAiTranscriptionResponseMetadata.from(transcriptionEntity.getBody())
							.withRateLimit(rateLimits));

			}

		});
	}

	private MultiValueMap<String, Object> createRequestBody(TranscriptionRequest transcriptionRequest) {

		OpenAiApi.TranscriptionRequest request = new OpenAiApi.TranscriptionRequest();

		if (this.defaultOptions != null) {
			request = ModelOptionsUtils.merge(request, this.defaultOptions, OpenAiApi.TranscriptionRequest.class);
		}

		if (transcriptionRequest.getOptions() != null) {
			if (transcriptionRequest.getOptions() instanceof TranscriptionOptions runtimeOptions) {
				OpenAiTranscriptionOptions updatedRuntimeOptions = ModelOptionsUtils.copyToTarget(runtimeOptions,
						TranscriptionOptions.class, OpenAiTranscriptionOptions.class);
				request = ModelOptionsUtils.merge(updatedRuntimeOptions, request, OpenAiApi.TranscriptionRequest.class);
			}
			else {
				throw new IllegalArgumentException("Prompt options are not of type TranscriptionOptions: "
						+ transcriptionRequest.getOptions().getClass().getSimpleName());
			}
		}
		MultiValueMap<String, Object> requestBody = new LinkedMultiValueMap<>();
		if (request.responseFormat() != null) {
			requestBody.add("response_format", request.responseFormat().type());
		}
		if (request.prompt() != null) {
			requestBody.add("prompt", request.prompt());
		}
		if (request.temperature() != null) {
			requestBody.add("temperature", request.temperature());
		}
		if (request.language() != null) {
			requestBody.add("language", request.language());
		}
		if (request.model() != null) {
			requestBody.add("model", request.model());
		}
		if (transcriptionRequest.getInstructions() != null) {
			requestBody.add("file", transcriptionRequest.getInstructions());
		}
		return requestBody;
	}

}
