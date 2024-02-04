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
import org.springframework.ai.chat.metadata.RateLimit;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.OpenAiApi.OpenAiApiException;
import org.springframework.ai.openai.metadata.OpenAiTranscriptionResponseMetadata;
import org.springframework.ai.openai.metadata.support.OpenAiResponseHeaderExtractor;
import org.springframework.ai.transcription.Transcript;
import org.springframework.ai.transcription.TranscriptionClient;
import org.springframework.ai.transcription.TranscriptionRequest;
import org.springframework.ai.transcription.TranscriptionResponse;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.time.Duration;

/**
 * {@link TranscriptionClient} implementation for {@literal OpenAI} backed by {@link OpenAiApi}.
 *
 * @author Michael Lavelle
 * @see TranscriptionClient
 * @see OpenAiApi
 */
public class OpenAiTranscriptionClient implements TranscriptionClient {

	private Double temperature = 0.7;

	private String model = "whisper-1";

	private final Logger logger = LoggerFactory.getLogger(getClass());

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

	public String getModel() {
		return this.model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public Double getTemperature() {
		return this.temperature;
	}

	public void setTemperature(Double temperature) {
		this.temperature = temperature;
	}

	@Override
	public TranscriptionResponse call(TranscriptionRequest request) {

		return this.retryTemplate.execute(ctx -> {
			Resource audioResource = request.getInstructions();

			MultiValueMap<String, Object> requestBody = new LinkedMultiValueMap<>();
			requestBody.add("file", audioResource);
			requestBody.add("model", this.model);
			requestBody.add("temperature", this.temperature.floatValue());
			// TODO - add language

			ResponseEntity<OpenAiApi.Transcription> transcriptionEntity = this.openAiApi
					.transcriptionEntity(requestBody);

			var transcription = transcriptionEntity.getBody();

			if (transcription == null) {
				logger.warn("No transcription returned for request: {}", audioResource);
				return new TranscriptionResponse(null);
			}

			Transcript transcript = new Transcript(transcription.text());

			RateLimit rateLimits = OpenAiResponseHeaderExtractor.extractAiResponseHeaders(transcriptionEntity);

			return new TranscriptionResponse(transcript, OpenAiTranscriptionResponseMetadata.from(transcriptionEntity.getBody()).withRateLimit(rateLimits));
		});
	}

}
