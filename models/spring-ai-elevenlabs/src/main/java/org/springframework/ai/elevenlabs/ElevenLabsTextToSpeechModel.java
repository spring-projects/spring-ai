/*
 * Copyright 2023-present the original author or authors.
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
import java.util.Objects;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
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
 * @author Sebastien Deleuze
 */
public class ElevenLabsTextToSpeechModel implements TextToSpeechModel {

	private final Log logger = LogFactory.getLog(getClass());

	private final ElevenLabsApi elevenLabsApi;

	private final RetryTemplate retryTemplate;

	private final ElevenLabsTextToSpeechOptions options;

	public ElevenLabsTextToSpeechModel(ElevenLabsApi elevenLabsApi, ElevenLabsTextToSpeechOptions options) {
		this(elevenLabsApi, options, RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	public ElevenLabsTextToSpeechModel(ElevenLabsApi elevenLabsApi, ElevenLabsTextToSpeechOptions options,
			RetryTemplate retryTemplate) {
		Assert.notNull(elevenLabsApi, "ElevenLabsApi must not be null");
		Assert.notNull(options, "ElevenLabsSpeechOptions must not be null");
		Assert.notNull(retryTemplate, "RetryTemplate must not be null");

		this.elevenLabsApi = elevenLabsApi;
		this.options = options;
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
				if (logger.isWarnEnabled()) {
					logger.warn("No speech response returned for request: " + requestContext.request);
				}
				return new byte[0];
			}
			return response.getBody();
		});

		return new TextToSpeechResponse(List.of(new Speech(audioData)));
	}

	@Override
	public Flux<TextToSpeechResponse> stream(TextToSpeechPrompt prompt) {
		RequestContext requestContext = prepareRequest(prompt);

		return RetryUtils.execute(this.retryTemplate, () -> this.elevenLabsApi
			.textToSpeechStream(requestContext.request, requestContext.voiceId, requestContext.queryParameters)
			.map(entity -> new TextToSpeechResponse(List.of(new Speech(Objects.requireNonNull(entity.getBody()))))));
	}

	private RequestContext prepareRequest(TextToSpeechPrompt prompt) {
		ElevenLabsApi.SpeechRequest request = createRequest(prompt);
		ElevenLabsTextToSpeechOptions options = getOptions(prompt);
		String voiceId = options.getVoice();
		Assert.state(voiceId != null, "voiceId must not be null");
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
		return (runtimeOptions != null) ? merge(runtimeOptions, this.options) : this.options;
	}

	private ElevenLabsTextToSpeechOptions merge(ElevenLabsTextToSpeechOptions runtimeOptions,
			ElevenLabsTextToSpeechOptions options) {
		return ElevenLabsTextToSpeechOptions.builder()
			.modelId(getOrDefault(runtimeOptions.getModelId(), options.getModelId()))
			.voice(getOrDefault(runtimeOptions.getVoice(), options.getVoice()))
			.voiceId(getOrDefault(runtimeOptions.getVoiceId(), options.getVoiceId()))
			.format(getOrDefault(runtimeOptions.getFormat(), options.getFormat()))
			.outputFormat(getOrDefault(runtimeOptions.getOutputFormat(), options.getOutputFormat()))
			.voiceSettings(getOrDefault(runtimeOptions.getVoiceSettings(), options.getVoiceSettings()))
			.languageCode(getOrDefault(runtimeOptions.getLanguageCode(), options.getLanguageCode()))
			.pronunciationDictionaryLocators(getOrDefault(runtimeOptions.getPronunciationDictionaryLocators(),
					options.getPronunciationDictionaryLocators()))
			.seed(getOrDefault(runtimeOptions.getSeed(), options.getSeed()))
			.previousText(getOrDefault(runtimeOptions.getPreviousText(), options.getPreviousText()))
			.nextText(getOrDefault(runtimeOptions.getNextText(), options.getNextText()))
			.previousRequestIds(getOrDefault(runtimeOptions.getPreviousRequestIds(), options.getPreviousRequestIds()))
			.nextRequestIds(getOrDefault(runtimeOptions.getNextRequestIds(), options.getNextRequestIds()))
			.applyTextNormalization(
					getOrDefault(runtimeOptions.getApplyTextNormalization(), options.getApplyTextNormalization()))
			.applyLanguageTextNormalization(getOrDefault(runtimeOptions.getApplyLanguageTextNormalization(),
					options.getApplyLanguageTextNormalization()))
			.build();
	}

	private <T> @Nullable T getOrDefault(@Nullable T runtimeValue, @Nullable T defaultValue) {
		return runtimeValue != null ? runtimeValue : defaultValue;
	}

	/**
	 * @since 2.0.0
	 */
	@Override
	public ElevenLabsTextToSpeechOptions getOptions() {
		return this.options;
	}

	/**
	 * @deprecated use {@link #getOptions()} instead.
	 */
	@Deprecated(forRemoval = true)
	@Override
	@SuppressWarnings("removal")
	public ElevenLabsTextToSpeechOptions getDefaultOptions() {
		return this.options;
	}

	public static final class Builder {

		private @Nullable ElevenLabsApi elevenLabsApi;

		private RetryTemplate retryTemplate = RetryUtils.DEFAULT_RETRY_TEMPLATE;

		private ElevenLabsTextToSpeechOptions options = ElevenLabsTextToSpeechOptions.builder().build();

		public Builder elevenLabsApi(ElevenLabsApi elevenLabsApi) {
			this.elevenLabsApi = elevenLabsApi;
			return this;
		}

		public Builder retryTemplate(RetryTemplate retryTemplate) {
			this.retryTemplate = retryTemplate;
			return this;
		}

		public Builder options(ElevenLabsTextToSpeechOptions options) {
			this.options = options;
			return this;
		}

		public ElevenLabsTextToSpeechModel build() {
			Assert.notNull(this.elevenLabsApi, "ElevenLabsApi must not be null");
			Assert.notNull(this.options, "ElevenLabsSpeechOptions must not be null");
			return new ElevenLabsTextToSpeechModel(this.elevenLabsApi, this.options, this.retryTemplate);
		}

	}

	private record RequestContext(ElevenLabsApi.SpeechRequest request, String voiceId,
			MultiValueMap<String, String> queryParameters) {
	}

}
