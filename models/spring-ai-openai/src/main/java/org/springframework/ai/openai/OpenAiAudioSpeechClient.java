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

package org.springframework.ai.openai;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.RateLimit;
import org.springframework.ai.openai.api.OpenAiAudioApi;
import org.springframework.ai.openai.api.OpenAiAudioApi.SpeechRequest.AudioResponseFormat;
import org.springframework.ai.openai.api.common.OpenAiApiException;
import org.springframework.ai.openai.audio.speech.*;
import org.springframework.ai.openai.metadata.audio.OpenAiAudioSpeechResponseMetadata;
import org.springframework.ai.openai.metadata.support.OpenAiResponseHeaderExtractor;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;

import java.time.Duration;

/**
 * OpenAI audio speech client implementation for backed by {@link OpenAiAudioApi}.
 *
 * @author Ahmed Yousri
 * @see OpenAiAudioApi
 */
public class OpenAiAudioSpeechClient implements SpeechClient, StreamingSpeechClient {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final OpenAiAudioSpeechOptions defaultOptions;

	private static final Float SPEED = 1.0f;

	public final RetryTemplate retryTemplate = RetryTemplate.builder()
		.maxAttempts(10)
		.retryOn(OpenAiApiException.class)
		.exponentialBackoff(Duration.ofMillis(2000), 5, Duration.ofMillis(3 * 60000))
		.build();

	private final OpenAiAudioApi audioApi;

	public OpenAiAudioSpeechClient(OpenAiAudioApi audioApi) {
		this(audioApi,
				OpenAiAudioSpeechOptions.builder()
					.withModel(OpenAiAudioApi.TtsModel.TTS_1.getValue())
					.withResponseFormat(AudioResponseFormat.MP3)
					.withVoice(OpenAiAudioApi.SpeechRequest.Voice.ALLOY)
					.withSpeed(SPEED)
					.build());
	}

	public OpenAiAudioSpeechClient(OpenAiAudioApi audioApi, OpenAiAudioSpeechOptions options) {
		Assert.notNull(audioApi, "OpenAiAudioApi must not be null");
		Assert.notNull(options, "OpenAiSpeechOptions must not be null");
		this.audioApi = audioApi;
		this.defaultOptions = options;
	}

	@Override
	public byte[] call(String text) {
		SpeechPrompt speechRequest = new SpeechPrompt(text);
		return call(speechRequest).getResult().getOutput();
	}

	@Override
	public SpeechResponse call(SpeechPrompt speechPrompt) {

		return this.retryTemplate.execute(ctx -> {

			OpenAiAudioApi.SpeechRequest speechRequest = createRequestBody(speechPrompt);

			ResponseEntity<byte[]> speechEntity = this.audioApi.createSpeech(speechRequest);
			var speech = speechEntity.getBody();

			if (speech == null) {
				logger.warn("No speech response returned for speechRequest: {}", speechRequest);
				return new SpeechResponse(new Speech(new byte[0]));
			}

			RateLimit rateLimits = OpenAiResponseHeaderExtractor.extractAiResponseHeaders(speechEntity);

			return new SpeechResponse(new Speech(speech), new OpenAiAudioSpeechResponseMetadata(rateLimits));

		});
	}

	/**
	 * Streams the audio response for the given speech prompt.
	 * @param prompt The speech prompt containing the text and options for speech
	 * synthesis.
	 * @return A Flux of SpeechResponse objects containing the streamed audio and
	 * metadata.
	 */

	@Override
	public Flux<SpeechResponse> stream(SpeechPrompt prompt) {
		return this.audioApi.stream(this.createRequestBody(prompt))
			.map(entity -> new SpeechResponse(new Speech(entity.getBody()), new OpenAiAudioSpeechResponseMetadata(
					OpenAiResponseHeaderExtractor.extractAiResponseHeaders(entity))));
	}

	private OpenAiAudioApi.SpeechRequest createRequestBody(SpeechPrompt request) {
		OpenAiAudioSpeechOptions options = this.defaultOptions;

		if (request.getOptions() != null) {
			if (request.getOptions() instanceof OpenAiAudioSpeechOptions runtimeOptions) {
				options = this.merge(options, runtimeOptions);
			}
			else {
				throw new IllegalArgumentException("Prompt options are not of type SpeechOptions: "
						+ request.getOptions().getClass().getSimpleName());
			}
		}

		String input = StringUtils.isNotBlank(options.getInput()) ? options.getInput()
				: request.getInstructions().get(0).getText();

		OpenAiAudioApi.SpeechRequest.Builder requestBuilder = OpenAiAudioApi.SpeechRequest.builder()
			.withModel(options.getModel())
			.withInput(input)
			.withVoice(options.getVoice())
			.withResponseFormat(options.getResponseFormat())
			.withSpeed(options.getSpeed());

		return requestBuilder.build();
	}

	private OpenAiAudioSpeechOptions merge(OpenAiAudioSpeechOptions source, OpenAiAudioSpeechOptions target) {
		OpenAiAudioSpeechOptions.Builder mergedBuilder = OpenAiAudioSpeechOptions.builder();

		mergedBuilder.withModel(source.getModel() != null ? source.getModel() : target.getModel());
		mergedBuilder.withInput(source.getInput() != null ? source.getInput() : target.getInput());
		mergedBuilder.withVoice(source.getVoice() != null ? source.getVoice() : target.getVoice());
		mergedBuilder.withResponseFormat(
				source.getResponseFormat() != null ? source.getResponseFormat() : target.getResponseFormat());
		mergedBuilder.withSpeed(source.getSpeed() != null ? source.getSpeed() : target.getSpeed());

		return mergedBuilder.build();
	}

}
