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

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import org.springframework.ai.audio.tts.Speech;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.audio.tts.TextToSpeechResponse;
import org.springframework.ai.elevenlabs.api.ElevenLabsApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * Implementation of the {@link TextToSpeechModel} interface for ElevenLabs TTS API.
 *
 * @author Alexandros Pappas
 */
public class ElevenLabsTextToSpeechModel implements TextToSpeechModel {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final ElevenLabsApi elevenLabsApi;

	private final RetryTemplate retryTemplate;

	private final ElevenLabsTextToSpeechOptions defaultOptions;

	public ElevenLabsTextToSpeechModel(ElevenLabsApi elevenLabsApi, ElevenLabsTextToSpeechOptions defaultOptions) {
		this(elevenLabsApi, defaultOptions, RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	public ElevenLabsTextToSpeechModel(ElevenLabsApi elevenLabsApi, ElevenLabsTextToSpeechOptions defaultOptions,
			RetryTemplate retryTemplate) {
		Assert.notNull(elevenLabsApi, "ElevenLabsApi must not be null");
		Assert.notNull(defaultOptions, "ElevenLabsSpeechOptions must not be null");
		Assert.notNull(retryTemplate, "RetryTemplate must not be null");

		this.elevenLabsApi = elevenLabsApi;
		this.defaultOptions = defaultOptions;
		this.retryTemplate = retryTemplate;
	}

	public static Builder builder() {
		return new Builder();
	}

	@Override
	public TextToSpeechResponse call(TextToSpeechPrompt prompt) {
		RequestContext requestContext = prepareRequest(prompt);

		byte[] audioData = RetryUtils.execute(this.retryTemplate, () -> {
			var response = this.elevenLabsApi.textToSpeech(requestContext.request, requestContext.voiceId,
					requestContext.queryParameters);
			if (response.getBody() == null) {
				logger.warn("No speech response returned for request: {}", requestContext.request);
				return new byte[0];
			}
			return response.getBody();
		});

		return new TextToSpeechResponse(List.of(new Speech(audioData)));
	}

	@Override
	public Flux<TextToSpeechResponse> stream(TextToSpeechPrompt prompt) {
		RequestContext requestContext = prepareRequest(prompt);

		return RetryUtils.execute(this.retryTemplate,
				() -> this.elevenLabsApi
					.textToSpeechStream(requestContext.request, requestContext.voiceId, requestContext.queryParameters)
					.map(entity -> new TextToSpeechResponse(List.of(new Speech(entity.getBody())))));
	}

	private RequestContext prepareRequest(TextToSpeechPrompt prompt) {
		ElevenLabsApi.SpeechRequest request = createRequest(prompt);
		ElevenLabsTextToSpeechOptions options = getOptions(prompt);
		String voiceId = options.getVoice();
		MultiValueMap<String, String> queryParameters = buildQueryParameters(options);

		return new RequestContext(request, voiceId, queryParameters);
	}

	private MultiValueMap<String, String> buildQueryParameters(ElevenLabsTextToSpeechOptions options) {
		MultiValueMap<String, String> queryParameters = new LinkedMultiValueMap<>();
		if (options.getEnableLogging() != null) {
			queryParameters.add("enable_logging", options.getEnableLogging().toString());
		}
		if (options.getFormat() != null) {
			queryParameters.add("output_format", options.getFormat());
		}
		return queryParameters;
	}

	private ElevenLabsApi.SpeechRequest createRequest(TextToSpeechPrompt prompt) {
		ElevenLabsTextToSpeechOptions options = getOptions(prompt);

		String voiceId = options.getVoice();
		Assert.notNull(voiceId, "A voiceId must be specified in the ElevenLabsSpeechOptions.");

		String text = prompt.getInstructions().getText();
		Assert.hasText(text, "Prompt must contain text to convert to speech.");

		return ElevenLabsApi.SpeechRequest.builder()
			.text(text)
			.modelId(options.getModelId())
			.voiceSettings(options.getVoiceSettings())
			.languageCode(options.getLanguageCode())
			.pronunciationDictionaryLocators(options.getPronunciationDictionaryLocators())
			.seed(options.getSeed())
			.previousText(options.getPreviousText())
			.nextText(options.getNextText())
			.previousRequestIds(options.getPreviousRequestIds())
			.nextRequestIds(options.getNextRequestIds())
			.applyTextNormalization(options.getApplyTextNormalization())
			.applyLanguageTextNormalization(options.getApplyLanguageTextNormalization())
			.build();
	}

	private ElevenLabsTextToSpeechOptions getOptions(TextToSpeechPrompt prompt) {
		ElevenLabsTextToSpeechOptions runtimeOptions = (prompt
			.getOptions() instanceof ElevenLabsTextToSpeechOptions elevenLabsSpeechOptions) ? elevenLabsSpeechOptions
					: null;
		return (runtimeOptions != null) ? merge(runtimeOptions, this.defaultOptions) : this.defaultOptions;
	}

	private ElevenLabsTextToSpeechOptions merge(ElevenLabsTextToSpeechOptions runtimeOptions,
			ElevenLabsTextToSpeechOptions defaultOptions) {
		return ElevenLabsTextToSpeechOptions.builder()
			.modelId(getOrDefault(runtimeOptions.getModelId(), defaultOptions.getModelId()))
			.voice(getOrDefault(runtimeOptions.getVoice(), defaultOptions.getVoice()))
			.voiceId(getOrDefault(runtimeOptions.getVoiceId(), defaultOptions.getVoiceId()))
			.format(getOrDefault(runtimeOptions.getFormat(), defaultOptions.getFormat()))
			.outputFormat(getOrDefault(runtimeOptions.getOutputFormat(), defaultOptions.getOutputFormat()))
			.voiceSettings(getOrDefault(runtimeOptions.getVoiceSettings(), defaultOptions.getVoiceSettings()))
			.languageCode(getOrDefault(runtimeOptions.getLanguageCode(), defaultOptions.getLanguageCode()))
			.pronunciationDictionaryLocators(getOrDefault(runtimeOptions.getPronunciationDictionaryLocators(),
					defaultOptions.getPronunciationDictionaryLocators()))
			.seed(getOrDefault(runtimeOptions.getSeed(), defaultOptions.getSeed()))
			.previousText(getOrDefault(runtimeOptions.getPreviousText(), defaultOptions.getPreviousText()))
			.nextText(getOrDefault(runtimeOptions.getNextText(), defaultOptions.getNextText()))
			.previousRequestIds(
					getOrDefault(runtimeOptions.getPreviousRequestIds(), defaultOptions.getPreviousRequestIds()))
			.nextRequestIds(getOrDefault(runtimeOptions.getNextRequestIds(), defaultOptions.getNextRequestIds()))
			.applyTextNormalization(getOrDefault(runtimeOptions.getApplyTextNormalization(),
					defaultOptions.getApplyTextNormalization()))
			.applyLanguageTextNormalization(getOrDefault(runtimeOptions.getApplyLanguageTextNormalization(),
					defaultOptions.getApplyLanguageTextNormalization()))
			.build();
	}

	private <T> T getOrDefault(T runtimeValue, T defaultValue) {
		return runtimeValue != null ? runtimeValue : defaultValue;
	}

	@Override
	public ElevenLabsTextToSpeechOptions getDefaultOptions() {
		return this.defaultOptions;
	}

	public static final class Builder {

		private ElevenLabsApi elevenLabsApi;

		private RetryTemplate retryTemplate = RetryUtils.DEFAULT_RETRY_TEMPLATE;

		private ElevenLabsTextToSpeechOptions defaultOptions = ElevenLabsTextToSpeechOptions.builder().build();

		public Builder elevenLabsApi(ElevenLabsApi elevenLabsApi) {
			this.elevenLabsApi = elevenLabsApi;
			return this;
		}

		public Builder retryTemplate(RetryTemplate retryTemplate) {
			this.retryTemplate = retryTemplate;
			return this;
		}

		public Builder defaultOptions(ElevenLabsTextToSpeechOptions defaultOptions) {
			this.defaultOptions = defaultOptions;
			return this;
		}

		public ElevenLabsTextToSpeechModel build() {
			Assert.notNull(this.elevenLabsApi, "ElevenLabsApi must not be null");
			Assert.notNull(this.defaultOptions, "ElevenLabsSpeechOptions must not be null");
			return new ElevenLabsTextToSpeechModel(this.elevenLabsApi, this.defaultOptions, this.retryTemplate);
		}

	}

	private record RequestContext(ElevenLabsApi.SpeechRequest request, String voiceId,
			MultiValueMap<String, String> queryParameters) {
	}

}
