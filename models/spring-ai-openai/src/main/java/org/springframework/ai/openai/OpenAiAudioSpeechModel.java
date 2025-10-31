/*
 * Copyright 2023-2025 the original author or authors.
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

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import org.springframework.ai.audio.tts.Speech;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.audio.tts.TextToSpeechOptions;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.audio.tts.TextToSpeechResponse;
import org.springframework.ai.chat.metadata.RateLimit;
import org.springframework.ai.openai.api.OpenAiAudioApi;
import org.springframework.ai.openai.api.OpenAiAudioApi.SpeechRequest.AudioResponseFormat;
import org.springframework.ai.openai.metadata.audio.OpenAiAudioSpeechResponseMetadata;
import org.springframework.ai.openai.metadata.support.OpenAiResponseHeaderExtractor;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * OpenAI audio speech client implementation for backed by {@link OpenAiAudioApi}.
 *
 * @author Ahmed Yousri
 * @author Hyunjoon Choi
 * @author Thomas Vitale
 * @author Jonghoon Park
 * @see OpenAiAudioApi
 * @since 1.0.0-M1
 */
public class OpenAiAudioSpeechModel implements TextToSpeechModel {

	/**
	 * The speed of the default voice synthesis.
	 * @see OpenAiAudioSpeechOptions
	 */
	private static final Double SPEED = 1.0;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	/**
	 * The default options used for the audio completion requests.
	 */
	private final OpenAiAudioSpeechOptions defaultOptions;

	/**
	 * The retry template used to retry the OpenAI Audio API calls.
	 */
	private final RetryTemplate retryTemplate;

	/**
	 * Low-level access to the OpenAI Audio API.
	 */
	private final OpenAiAudioApi audioApi;

	/**
	 * Initializes a new instance of the OpenAiAudioSpeechModel class with the provided
	 * OpenAiAudioApi. It uses the model tts-1, response format mp3, voice alloy, and the
	 * default speed of 1.0.
	 * @param audioApi The OpenAiAudioApi to use for speech synthesis.
	 */
	public OpenAiAudioSpeechModel(OpenAiAudioApi audioApi) {
		this(audioApi,
				OpenAiAudioSpeechOptions.builder()
					.model(OpenAiAudioApi.TtsModel.GPT_4_O_MINI_TTS.getValue())
					.responseFormat(AudioResponseFormat.MP3)
					.voice(OpenAiAudioApi.SpeechRequest.Voice.ALLOY.getValue())
					.speed(SPEED)
					.build());
	}

	/**
	 * Initializes a new instance of the OpenAiAudioSpeechModel class with the provided
	 * OpenAiAudioApi and options.
	 * @param audioApi The OpenAiAudioApi to use for speech synthesis.
	 * @param options The OpenAiAudioSpeechOptions containing the speech synthesis
	 * options.
	 */
	public OpenAiAudioSpeechModel(OpenAiAudioApi audioApi, OpenAiAudioSpeechOptions options) {
		this(audioApi, options, RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	/**
	 * Initializes a new instance of the OpenAiAudioSpeechModel class with the provided
	 * OpenAiAudioApi and options.
	 * @param audioApi The OpenAiAudioApi to use for speech synthesis.
	 * @param options The OpenAiAudioSpeechOptions containing the speech synthesis
	 * options.
	 * @param retryTemplate The retry template.
	 */
	public OpenAiAudioSpeechModel(OpenAiAudioApi audioApi, OpenAiAudioSpeechOptions options,
			RetryTemplate retryTemplate) {
		Assert.notNull(audioApi, "OpenAiAudioApi must not be null");
		Assert.notNull(options, "OpenAiSpeechOptions must not be null");
		Assert.notNull(options, "RetryTemplate must not be null");
		this.audioApi = audioApi;
		this.defaultOptions = options;
		this.retryTemplate = retryTemplate;
	}

	@Override
	public byte[] call(String text) {
		TextToSpeechPrompt prompt = new TextToSpeechPrompt(text);
		return call(prompt).getResult().getOutput();
	}

	@Override
	public TextToSpeechResponse call(TextToSpeechPrompt prompt) {

		OpenAiAudioApi.SpeechRequest speechRequest = createRequest(prompt);

		ResponseEntity<byte[]> speechEntity;
		try {
			speechEntity = this.retryTemplate.execute(() -> this.audioApi.createSpeech(speechRequest));
		}
		catch (Exception e) {
			throw new RuntimeException("Error calling OpenAI audio speech API", e);
		}

		var speech = speechEntity.getBody();

		if (speech == null) {
			logger.warn("No speech response returned for speechRequest: {}", speechRequest);
			return new TextToSpeechResponse(List.of(new Speech(new byte[0])));
		}

		RateLimit rateLimits = OpenAiResponseHeaderExtractor.extractAiResponseHeaders(speechEntity);

		return new TextToSpeechResponse(List.of(new Speech(speech)), new OpenAiAudioSpeechResponseMetadata(rateLimits));
	}

	/**
	 * Streams the audio response for the given speech prompt.
	 * @param prompt The speech prompt containing the text and options for speech
	 * synthesis.
	 * @return A Flux of TextToSpeechResponse objects containing the streamed audio and
	 * metadata.
	 */
	@Override
	public Flux<TextToSpeechResponse> stream(TextToSpeechPrompt prompt) {

		OpenAiAudioApi.SpeechRequest speechRequest = createRequest(prompt);

		Flux<ResponseEntity<byte[]>> speechEntity;
		try {
			speechEntity = this.retryTemplate.execute(() -> this.audioApi.stream(speechRequest));
		}
		catch (Exception e) {
			throw new RuntimeException("Error calling OpenAI audio speech streaming API", e);
		}

		return speechEntity.map(entity -> new TextToSpeechResponse(List.of(new Speech(entity.getBody())),
				new OpenAiAudioSpeechResponseMetadata(OpenAiResponseHeaderExtractor.extractAiResponseHeaders(entity))));
	}

	private OpenAiAudioApi.SpeechRequest createRequest(TextToSpeechPrompt prompt) {
		OpenAiAudioSpeechOptions runtimeOptions = (prompt
			.getOptions() instanceof OpenAiAudioSpeechOptions openAiAudioSpeechOptions) ? openAiAudioSpeechOptions
					: null;
		OpenAiAudioSpeechOptions options = (runtimeOptions != null) ? this.merge(runtimeOptions, this.defaultOptions)
				: this.defaultOptions;

		String input = StringUtils.hasText(options.getInput()) ? options.getInput()
				: prompt.getInstructions().getText();

		OpenAiAudioApi.SpeechRequest.Builder requestBuilder = OpenAiAudioApi.SpeechRequest.builder()
			.model(options.getModel())
			.input(input)
			.voice(options.getVoice())
			.responseFormat(options.getResponseFormat())
			.speed(options.getSpeed());

		return requestBuilder.build();
	}

	@Override
	public TextToSpeechOptions getDefaultOptions() {
		return this.defaultOptions;
	}

	private OpenAiAudioSpeechOptions merge(OpenAiAudioSpeechOptions source, OpenAiAudioSpeechOptions target) {
		OpenAiAudioSpeechOptions.Builder mergedBuilder = OpenAiAudioSpeechOptions.builder();

		mergedBuilder.model(source.getModel() != null ? source.getModel() : target.getModel());
		mergedBuilder.input(source.getInput() != null ? source.getInput() : target.getInput());
		mergedBuilder.voice(source.getVoice() != null ? source.getVoice() : target.getVoice());
		mergedBuilder.responseFormat(
				source.getResponseFormat() != null ? source.getResponseFormat() : target.getResponseFormat());
		mergedBuilder.speed(source.getSpeed() != null ? source.getSpeed() : target.getSpeed());

		return mergedBuilder.build();
	}

}
