/*
 * Copyright 2026-2026 the original author or authors.
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

package org.springframework.ai.openaisdk;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;

import com.openai.client.OpenAIClient;
import com.openai.core.http.Headers;
import com.openai.models.audio.speech.SpeechCreateParams;
import com.openai.models.audio.speech.SpeechModel;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import org.springframework.ai.audio.tts.Speech;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.audio.tts.TextToSpeechOptions;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.audio.tts.TextToSpeechResponse;
import org.springframework.ai.openaisdk.metadata.OpenAiSdkAudioSpeechResponseMetadata;
import org.springframework.ai.openaisdk.setup.OpenAiSdkSetup;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * OpenAI audio speech client implementation using the OpenAI Java SDK.
 *
 * @author Ilayaperumal Gopinathan
 * @author Ahmed Yousri
 * @author Hyunjoon Choi
 * @author Thomas Vitale
 * @author Jonghoon Park
 * @since 2.0.0
 */
public final class OpenAiSdkAudioSpeechModel implements TextToSpeechModel {

	private static final Logger logger = LoggerFactory.getLogger(OpenAiSdkAudioSpeechModel.class);

	private static final Double DEFAULT_SPEED = 1.0;

	private static final String DEFAULT_MODEL_NAME = OpenAiSdkAudioSpeechOptions.DEFAULT_SPEECH_MODEL;

	private final OpenAIClient openAiClient;

	private final OpenAiSdkAudioSpeechOptions defaultOptions;

	/**
	 * Private constructor that takes a Builder instance.
	 * @param builder The builder containing the configuration.
	 */
	private OpenAiSdkAudioSpeechModel(Builder builder) {
		this.defaultOptions = builder.defaultOptions;
		this.openAiClient = Objects.requireNonNullElseGet(builder.openAiClient,
				() -> OpenAiSdkSetup.setupSyncClient(this.defaultOptions.getBaseUrl(), this.defaultOptions.getApiKey(),
						this.defaultOptions.getCredential(), this.defaultOptions.getMicrosoftDeploymentName(),
						this.defaultOptions.getMicrosoftFoundryServiceVersion(),
						this.defaultOptions.getOrganizationId(), this.defaultOptions.isMicrosoftFoundry(),
						this.defaultOptions.isGitHubModels(), this.defaultOptions.getModel(),
						this.defaultOptions.getTimeout(), this.defaultOptions.getMaxRetries(),
						this.defaultOptions.getProxy(), this.defaultOptions.getCustomHeaders()));
	}

	/**
	 * Creates a new builder instance with default configuration.
	 * @return A new builder instance
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Creates a builder initialized with this model's configuration.
	 * @return A builder for creating a modified copy
	 */
	public Builder mutate() {
		return new Builder(this);
	}

	@NotNull
	@Override
	public byte[] call(@NotNull String text) {
		Assert.hasText(text, "Text must not be null or empty");
		TextToSpeechPrompt prompt = new TextToSpeechPrompt(text);
		return call(prompt).getResult().getOutput();
	}

	@Override
	public TextToSpeechResponse call(TextToSpeechPrompt prompt) {
		Assert.notNull(prompt, "Prompt must not be null");

		OpenAiSdkAudioSpeechOptions mergedOptions = mergeOptions(prompt);
		String inputText = getInputText(prompt, mergedOptions);

		if (logger.isTraceEnabled()) {
			logger.trace("Calling OpenAI SDK audio speech with model: {}, voice: {}, format: {}, speed: {}",
					mergedOptions.getModel(), mergedOptions.getVoice(), mergedOptions.getResponseFormat(),
					mergedOptions.getSpeed());
		}

		Assert.notNull(mergedOptions.getModel(), "Model must not be null");
		Assert.notNull(mergedOptions.getVoice(), "Voice must not be null");
		SpeechCreateParams.Builder paramsBuilder = SpeechCreateParams.builder()
			.model(SpeechModel.of(mergedOptions.getModel()))
			.input(inputText)
			.voice(SpeechCreateParams.Voice.of(mergedOptions.getVoice()));

		if (mergedOptions.getResponseFormat() != null) {
			paramsBuilder.responseFormat(SpeechCreateParams.ResponseFormat.of(mergedOptions.getResponseFormat()));
		}

		if (mergedOptions.getSpeed() != null) {
			paramsBuilder.speed(mergedOptions.getSpeed());
		}

		SpeechCreateParams params = paramsBuilder.build();

		com.openai.core.http.HttpResponse httpResponse = this.openAiClient.audio().speech().create(params);
		Headers headers = httpResponse.headers();

		byte[] audioBytes;
		try (InputStream inputStream = httpResponse.body()) {
			audioBytes = inputStream.readAllBytes();
		}
		catch (IOException e) {
			throw new RuntimeException("Failed to read audio speech response", e);
		}

		if (audioBytes.length == 0) {
			logger.warn("No speech response returned for prompt: {}", prompt);
			return new TextToSpeechResponse(List.of(new Speech(new byte[0])));
		}

		Speech speech = new Speech(audioBytes);
		OpenAiSdkAudioSpeechResponseMetadata metadata = OpenAiSdkAudioSpeechResponseMetadata.from(headers);

		return new TextToSpeechResponse(List.of(speech), metadata);
	}

	@NotNull
	@Override
	public Flux<TextToSpeechResponse> stream(@NotNull TextToSpeechPrompt prompt) {
		// TODO: The OpenAI SDK audio().speech() API does not support streaming yet.
		// Return the full response as a single element Flux.
		return Flux.just(call(prompt));
	}

	@NotNull
	@Override
	public TextToSpeechOptions getDefaultOptions() {
		return this.defaultOptions;
	}

	@NotNull
	private OpenAiSdkAudioSpeechOptions mergeOptions(TextToSpeechPrompt prompt) {
		OpenAiSdkAudioSpeechOptions runtimeOptions = (prompt
			.getOptions() instanceof OpenAiSdkAudioSpeechOptions openAiSdkOptions) ? openAiSdkOptions : null;

		if (runtimeOptions != null) {
			return merge(runtimeOptions, this.defaultOptions);
		}
		return this.defaultOptions;
	}

	private OpenAiSdkAudioSpeechOptions merge(OpenAiSdkAudioSpeechOptions source, OpenAiSdkAudioSpeechOptions target) {
		OpenAiSdkAudioSpeechOptions.Builder builder = OpenAiSdkAudioSpeechOptions.builder();

		builder.model(source.getModel() != null ? source.getModel() : target.getModel());
		builder.input(source.getInput() != null ? source.getInput() : target.getInput());
		builder.voice(source.getVoice() != null ? source.getVoice() : target.getVoice());
		builder.responseFormat(
				source.getResponseFormat() != null ? source.getResponseFormat() : target.getResponseFormat());
		builder.speed(source.getSpeed() != null ? source.getSpeed() : target.getSpeed());

		// Merge parent class fields
		builder.baseUrl(source.getBaseUrl() != null ? source.getBaseUrl() : target.getBaseUrl());
		builder.apiKey(source.getApiKey() != null ? source.getApiKey() : target.getApiKey());
		builder.credential(source.getCredential() != null ? source.getCredential() : target.getCredential());
		builder.deploymentName(
				source.getDeploymentName() != null ? source.getDeploymentName() : target.getDeploymentName());
		builder.microsoftFoundryServiceVersion(source.getMicrosoftFoundryServiceVersion() != null
				? source.getMicrosoftFoundryServiceVersion() : target.getMicrosoftFoundryServiceVersion());
		builder.organizationId(
				source.getOrganizationId() != null ? source.getOrganizationId() : target.getOrganizationId());
		builder.microsoftFoundry(source.isMicrosoftFoundry() || target.isMicrosoftFoundry());
		builder.gitHubModels(source.isGitHubModels() || target.isGitHubModels());
		builder.timeout(source.getTimeout() != null ? source.getTimeout() : target.getTimeout());
		builder.maxRetries(source.getMaxRetries() != null ? source.getMaxRetries() : target.getMaxRetries());
		builder.proxy(source.getProxy() != null ? source.getProxy() : target.getProxy());
		builder
			.customHeaders(source.getCustomHeaders() != null ? source.getCustomHeaders() : target.getCustomHeaders());

		return builder.build();
	}

	private String getInputText(TextToSpeechPrompt prompt, OpenAiSdkAudioSpeechOptions options) {
		if (StringUtils.hasText(options.getInput())) {
			return options.getInput();
		}
		return prompt.getInstructions().getText();
	}

	/**
	 * Builder for creating OpenAiSdkAudioSpeechModel instances.
	 */
	public static final class Builder {

		private OpenAIClient openAiClient;

		private OpenAiSdkAudioSpeechOptions defaultOptions;

		/**
		 * Default constructor with default options.
		 */
		private Builder() {
			this.defaultOptions = OpenAiSdkAudioSpeechOptions.builder()
				.model(DEFAULT_MODEL_NAME)
				.voice(OpenAiSdkAudioSpeechOptions.Voice.ALLOY)
				.responseFormat(OpenAiSdkAudioSpeechOptions.AudioResponseFormat.MP3)
				.speed(DEFAULT_SPEED)
				.build();
		}

		/**
		 * Copy constructor for creating a builder from an existing model.
		 * @param model The model to copy configuration from
		 */
		private Builder(OpenAiSdkAudioSpeechModel model) {
			this.openAiClient = model.openAiClient;
			this.defaultOptions = model.defaultOptions;
		}

		/**
		 * Sets the OpenAIClient.
		 * @param openAiClient The OpenAIClient to use
		 * @return This builder
		 */
		public Builder openAiClient(OpenAIClient openAiClient) {
			this.openAiClient = openAiClient;
			return this;
		}

		/**
		 * Sets the default options.
		 * @param defaultOptions The default options to use
		 * @return This builder
		 */
		public Builder defaultOptions(OpenAiSdkAudioSpeechOptions defaultOptions) {
			if (defaultOptions != null) {
				this.defaultOptions = defaultOptions;
			}
			return this;
		}

		/**
		 * Builds the OpenAiSdkAudioSpeechModel instance.
		 * @return A new OpenAiSdkAudioSpeechModel instance
		 */
		public OpenAiSdkAudioSpeechModel build() {
			return new OpenAiSdkAudioSpeechModel(this);
		}

	}

}
