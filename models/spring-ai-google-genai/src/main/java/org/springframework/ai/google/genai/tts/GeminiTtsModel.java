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

package org.springframework.ai.google.genai.tts;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import org.springframework.ai.audio.tts.Speech;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.audio.tts.TextToSpeechResponse;
import org.springframework.ai.google.genai.tts.api.GeminiTtsApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;

/**
 * Google Gemini Text-to-Speech model implementation.
 *
 * @author Alexandros Pappas
 * @since 1.1.0
 */
public class GeminiTtsModel implements TextToSpeechModel {

	private static final Logger logger = LoggerFactory.getLogger(GeminiTtsModel.class);

	private final GeminiTtsApi geminiTtsApi;

	private final GeminiTtsOptions defaultOptions;

	private final RetryTemplate retryTemplate;

	/**
	 * Create a new GeminiTtsModel with default retry template.
	 * @param geminiTtsApi The API client
	 * @param defaultOptions Default options
	 */
	public GeminiTtsModel(GeminiTtsApi geminiTtsApi, GeminiTtsOptions defaultOptions) {
		this(geminiTtsApi, defaultOptions, RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	/**
	 * Create a new GeminiTtsModel with custom retry template.
	 * @param geminiTtsApi The API client
	 * @param defaultOptions Default options
	 * @param retryTemplate Retry template
	 */
	public GeminiTtsModel(GeminiTtsApi geminiTtsApi, GeminiTtsOptions defaultOptions, RetryTemplate retryTemplate) {
		Assert.notNull(geminiTtsApi, "GeminiTtsApi must not be null");
		Assert.notNull(defaultOptions, "GeminiTtsOptions must not be null");
		Assert.notNull(retryTemplate, "RetryTemplate must not be null");

		this.geminiTtsApi = geminiTtsApi;
		this.defaultOptions = defaultOptions;
		this.retryTemplate = retryTemplate;
	}

	/**
	 * Create a new builder for GeminiTtsModel.
	 * @return a new builder instance
	 */
	public static Builder builder() {
		return new Builder();
	}

	@Override
	public TextToSpeechResponse call(TextToSpeechPrompt prompt) {
		Assert.notNull(prompt, "Prompt must not be null");

		GeminiTtsOptions options = mergeOptions(prompt);
		GeminiTtsApi.GenerateContentRequest request = createRequest(prompt, options);
		String model = options.getModel();

		ResponseEntity<GeminiTtsApi.GenerateContentResponse> responseEntity = RetryUtils.execute(this.retryTemplate,
				() -> this.geminiTtsApi.generateContent(model, request));

		GeminiTtsApi.GenerateContentResponse response = responseEntity.getBody();
		if (response == null) {
			logger.warn("No response returned for model: {}", model);
			return new TextToSpeechResponse(List.of(new Speech(new byte[0])));
		}

		byte[] audioData = GeminiTtsApi.extractAudioData(response);
		return new TextToSpeechResponse(List.of(new Speech(audioData)));
	}

	@Override
	public Flux<TextToSpeechResponse> stream(TextToSpeechPrompt prompt) {
		// Gemini TTS API doesn't support streaming, return single response
		return Flux.just(call(prompt));
	}

	@Override
	public GeminiTtsOptions getDefaultOptions() {
		return this.defaultOptions;
	}

	private GeminiTtsOptions mergeOptions(TextToSpeechPrompt prompt) {
		GeminiTtsOptions runtimeOptions = (prompt.getOptions() instanceof GeminiTtsOptions geminiOptions)
				? geminiOptions : null;

		if (runtimeOptions == null) {
			return this.defaultOptions;
		}

		return GeminiTtsOptions.builder()
			.model(getOrDefault(runtimeOptions.getModel(), this.defaultOptions.getModel()))
			.voice(getOrDefault(runtimeOptions.getVoice(), this.defaultOptions.getVoice()))
			.speakerVoiceConfigs(
					getOrDefault(runtimeOptions.getSpeakerVoiceConfigs(), this.defaultOptions.getSpeakerVoiceConfigs()))
			.speed(getOrDefault(runtimeOptions.getSpeed(), this.defaultOptions.getSpeed()))
			.build();
	}

	private <T> T getOrDefault(T runtimeValue, T defaultValue) {
		return runtimeValue != null ? runtimeValue : defaultValue;
	}

	private GeminiTtsApi.GenerateContentRequest createRequest(TextToSpeechPrompt prompt, GeminiTtsOptions options) {
		String text = prompt.getInstructions().getText();
		Assert.hasText(text, "Prompt text must not be empty");

		// Create content part with text
		GeminiTtsApi.Part part = new GeminiTtsApi.Part(text);
		GeminiTtsApi.Content content = new GeminiTtsApi.Content(List.of(part));

		// Create speech config (single-speaker or multi-speaker)
		GeminiTtsApi.SpeechConfig speechConfig = createSpeechConfig(options);

		// Create generation config
		GeminiTtsApi.GenerationConfig generationConfig = new GeminiTtsApi.GenerationConfig(List.of("AUDIO"),
				speechConfig);

		return new GeminiTtsApi.GenerateContentRequest(List.of(content), generationConfig);
	}

	private GeminiTtsApi.SpeechConfig createSpeechConfig(GeminiTtsOptions options) {
		// Multi-speaker configuration takes precedence
		if (options.getSpeakerVoiceConfigs() != null && !options.getSpeakerVoiceConfigs().isEmpty()) {
			GeminiTtsApi.MultiSpeakerVoiceConfig multiSpeakerConfig = new GeminiTtsApi.MultiSpeakerVoiceConfig(
					options.getSpeakerVoiceConfigs());

			return new GeminiTtsApi.SpeechConfig(null, multiSpeakerConfig);
		}

		// Single-speaker configuration
		String voiceName = options.getVoice();
		Assert.hasText(voiceName, "Voice name must be specified for single-speaker TTS");

		GeminiTtsApi.VoiceConfig voiceConfig = new GeminiTtsApi.VoiceConfig(
				new GeminiTtsApi.PrebuiltVoiceConfig(voiceName));

		return new GeminiTtsApi.SpeechConfig(voiceConfig, null);
	}

	/**
	 * Builder for creating GeminiTtsModel instances.
	 */
	public static final class Builder {

		private GeminiTtsApi geminiTtsApi;

		private RetryTemplate retryTemplate = RetryUtils.DEFAULT_RETRY_TEMPLATE;

		private GeminiTtsOptions defaultOptions;

		/**
		 * Sets the Gemini TTS API client.
		 * @param geminiTtsApi the API client to use
		 * @return this builder
		 */
		public Builder geminiTtsApi(GeminiTtsApi geminiTtsApi) {
			this.geminiTtsApi = geminiTtsApi;
			return this;
		}

		/**
		 * Sets the retry template for handling transient failures. If not specified, uses
		 * {@link RetryUtils#DEFAULT_RETRY_TEMPLATE}.
		 * @param retryTemplate the retry template to use
		 * @return this builder
		 */
		public Builder retryTemplate(RetryTemplate retryTemplate) {
			this.retryTemplate = retryTemplate;
			return this;
		}

		/**
		 * Sets the default options for text-to-speech generation. These options will be
		 * used when no runtime options are specified in the prompt.
		 * @param defaultOptions the default options to use
		 * @return this builder
		 */
		public Builder defaultOptions(GeminiTtsOptions defaultOptions) {
			this.defaultOptions = defaultOptions;
			return this;
		}

		/**
		 * Builds the GeminiTtsModel instance.
		 * @return a new GeminiTtsModel instance
		 * @throws IllegalArgumentException if geminiTtsApi or defaultOptions are null
		 */
		public GeminiTtsModel build() {
			Assert.notNull(this.geminiTtsApi, "GeminiTtsApi must not be null");
			Assert.notNull(this.defaultOptions, "GeminiTtsOptions must not be null");
			return new GeminiTtsModel(this.geminiTtsApi, this.defaultOptions, this.retryTemplate);
		}

	}

}
