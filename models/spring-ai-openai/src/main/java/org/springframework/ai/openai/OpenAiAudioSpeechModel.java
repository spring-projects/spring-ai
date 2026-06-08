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

package org.springframework.ai.openai;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;

import com.openai.client.OpenAIClient;
import com.openai.core.http.Headers;
import com.openai.models.audio.speech.SpeechCreateParams;
import com.openai.models.audio.speech.SpeechModel;
import io.micrometer.observation.ObservationRegistry;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;

import org.springframework.ai.audio.tts.Speech;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.audio.tts.TextToSpeechOptions;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.audio.tts.TextToSpeechResponse;
import org.springframework.ai.openai.metadata.OpenAiAudioSpeechResponseMetadata;
import org.springframework.ai.openai.setup.OpenAiSetup;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * OpenAI audio speech client implementation using the OpenAI Java SDK.
 *
 * @author Ahmed Yousri
 * @author Hyunjoon Choi
 * @author Thomas Vitale
 * @author Jonghoon Park
 * @author Ilayaperumal Gopinathan
 * @author Sebastien Deleuze
 */
public final class OpenAiAudioSpeechModel implements TextToSpeechModel {

	private static final Log logger = LogFactory.getLog(OpenAiAudioSpeechModel.class);

	private final OpenAIClient openAiClient;

	private final OpenAiAudioSpeechOptions options;

	/**
	 * Private constructor that takes individual configuration parameters.
	 * @param openAiClient The OpenAI client instance.
	 * @param options The default options for speech generation.
	 */
	private OpenAiAudioSpeechModel(@Nullable OpenAIClient openAiClient, @Nullable OpenAiAudioSpeechOptions options) {
		this.options = Objects.requireNonNullElseGet(options, () -> OpenAiAudioSpeechOptions.builder().build());
		this.openAiClient = Objects.requireNonNullElseGet(openAiClient,
				() -> OpenAiSetup.setupSyncClient(this.options.getBaseUrl(), this.options.getApiKey(),
						this.options.getCredential(), this.options.getMicrosoftDeploymentName(),
						this.options.getMicrosoftFoundryServiceVersion(), this.options.getOrganizationId(),
						this.options.isMicrosoftFoundry(), this.options.isGitHubModels(), this.options.getModel(),
						this.options.getTimeout(), this.options.getMaxRetries(), this.options.getProxy(),
						this.options.getCustomHeaders(), ObservationRegistry.NOOP, null, null));
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

	@Override
	public byte[] call(String text) {
		Assert.hasText(text, "Text must not be null or empty");
		TextToSpeechPrompt prompt = new TextToSpeechPrompt(text);
		return call(prompt).getResult().getOutput();
	}

	@Override
	public TextToSpeechResponse call(TextToSpeechPrompt prompt) {
		Assert.notNull(prompt, "Prompt must not be null");

		// Merge request options with default options
		OpenAiAudioSpeechOptions mergedOptions = OpenAiAudioSpeechOptions.builder()
			.from(this.options)
			.merge(prompt.getOptions())
			.build();

		String inputText = getInputText(prompt, mergedOptions);

		if (logger.isTraceEnabled()) {
			logger.trace("Calling OpenAI SDK audio speech with model: " + mergedOptions.getModel() + ", voice: "
					+ mergedOptions.getVoice() + ", format: " + mergedOptions.getResponseFormat() + ", speed: "
					+ mergedOptions.getSpeed());
		}

		String model;
		if (mergedOptions.getDeploymentName() != null) {
			model = mergedOptions.getDeploymentName();
		}
		else {
			model = mergedOptions.getModel();
		}

		Assert.notNull(model, "Model must not be null");
		Assert.notNull(mergedOptions.getVoice(), "Voice must not be null");
		SpeechCreateParams.Builder paramsBuilder = SpeechCreateParams.builder()
			.model(SpeechModel.of(model))
			.input(inputText)
			.voice(SpeechCreateParams.Voice.ofString(mergedOptions.getVoice()));

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
			if (logger.isWarnEnabled()) {
				logger.warn("No speech response returned for prompt: " + prompt);
			}
			return new TextToSpeechResponse(List.of(new Speech(new byte[0])));
		}

		Speech speech = new Speech(audioBytes);
		OpenAiAudioSpeechResponseMetadata metadata = OpenAiAudioSpeechResponseMetadata.from(headers);

		return new TextToSpeechResponse(List.of(speech), metadata);
	}

	@Override
	public Flux<TextToSpeechResponse> stream(TextToSpeechPrompt prompt) {
		// TODO: The OpenAI SDK audio().speech() API does not support streaming yet.
		// Return the full response as a single element Flux.
		return Flux.just(call(prompt));
	}

	/**
	 * @since 2.0.0
	 */
	@Override
	public OpenAiAudioSpeechOptions getOptions() {
		return this.options;
	}

	/**
	 * @deprecated use {@link #getOptions()} instead.
	 */
	@Deprecated(forRemoval = true)
	@Override
	@SuppressWarnings("removal")
	public TextToSpeechOptions getDefaultOptions() {
		return this.options;
	}

	private String getInputText(TextToSpeechPrompt prompt, OpenAiAudioSpeechOptions options) {
		if (StringUtils.hasText(options.getInput())) {
			return options.getInput();
		}
		return prompt.getInstructions().getText();
	}

	/**
	 * Builder for creating OpenAiAudioSpeechModel instances.
	 */
	public static final class Builder {

		private @Nullable OpenAIClient openAiClient;

		private @Nullable OpenAiAudioSpeechOptions options;

		/**
		 * Default constructor with default options.
		 */
		private Builder() {
			this.options = OpenAiAudioSpeechOptions.builder().build();
		}

		/**
		 * Copy constructor for creating a builder from an existing model.
		 * @param model The model to copy configuration from
		 */
		private Builder(OpenAiAudioSpeechModel model) {
			this.openAiClient = model.openAiClient;
			this.options = model.options;
		}

		/**
		 * Sets the OpenAIClient.
		 * @param openAiClient The OpenAIClient to use
		 * @return This builder
		 */
		public Builder openAiClient(@Nullable OpenAIClient openAiClient) {
			this.openAiClient = openAiClient;
			return this;
		}

		/**
		 * Sets the default options.
		 * @param options The default options to use
		 * @return This builder
		 */
		public Builder options(@Nullable OpenAiAudioSpeechOptions options) {
			if (options != null) {
				this.options = options;
			}
			return this;
		}

		/**
		 * Builds the OpenAiAudioSpeechModel instance.
		 * @return A new OpenAiAudioSpeechModel instance
		 */
		public OpenAiAudioSpeechModel build() {
			return new OpenAiAudioSpeechModel(this.openAiClient, this.options);
		}

	}

}
