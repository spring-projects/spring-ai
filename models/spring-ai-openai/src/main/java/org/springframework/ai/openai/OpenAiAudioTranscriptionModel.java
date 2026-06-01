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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import com.openai.client.OpenAIClient;
import com.openai.core.MultipartField;
import com.openai.models.audio.transcriptions.TranscriptionCreateParams;
import com.openai.models.audio.transcriptions.TranscriptionCreateResponse;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.audio.transcription.AudioTranscription;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponseMetadata;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.openai.setup.OpenAiSetup;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;

/**
 * OpenAI audio transcription model implementation using the OpenAI Java SDK. You provide
 * as input the audio file you want to transcribe and the desired output file format of
 * the transcription of the audio.
 *
 * @author Michael Lavelle
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Ilayaperumal Gopinathan
 */
public final class OpenAiAudioTranscriptionModel implements TranscriptionModel {

	private static final Logger logger = LoggerFactory.getLogger(OpenAiAudioTranscriptionModel.class);

	private final OpenAIClient openAiClient;

	private final OpenAiAudioTranscriptionOptions defaultOptions;

	/**
	 * Creates a new builder for {@link OpenAiAudioTranscriptionModel}.
	 * @return a new builder instance
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Creates a builder initialized with this model's configuration.
	 * @return a builder for creating a modified copy
	 */
	public Builder mutate() {
		return new Builder(this);
	}

	private OpenAiAudioTranscriptionModel(Builder builder) {
		this.defaultOptions = builder.options != null ? builder.options
				: OpenAiAudioTranscriptionOptions.builder().build();
		this.openAiClient = Objects.requireNonNullElseGet(builder.openAiClient,
				() -> OpenAiSetup.setupSyncClient(this.defaultOptions.getBaseUrl(), this.defaultOptions.getApiKey(),
						this.defaultOptions.getCredential(), this.defaultOptions.getMicrosoftDeploymentName(),
						this.defaultOptions.getMicrosoftFoundryServiceVersion(),
						this.defaultOptions.getOrganizationId(), this.defaultOptions.isMicrosoftFoundry(),
						this.defaultOptions.isGitHubModels(), this.defaultOptions.getModel(),
						this.defaultOptions.getTimeout(), this.defaultOptions.getMaxRetries(),
						this.defaultOptions.getProxy(), this.defaultOptions.getCustomHeaders()));
	}

	/**
	 * Gets the transcription options for this model.
	 * @return the transcription options
	 */
	public OpenAiAudioTranscriptionOptions getOptions() {
		return this.defaultOptions;
	}

	@Override
	public AudioTranscriptionResponse call(AudioTranscriptionPrompt transcriptionPrompt) {
		OpenAiAudioTranscriptionOptions options = this.defaultOptions;
		if (transcriptionPrompt.getOptions() != null) {
			if (transcriptionPrompt.getOptions() instanceof OpenAiAudioTranscriptionOptions runtimeOptions) {
				options = merge(runtimeOptions, options);
			}
			else {
				throw new IllegalArgumentException("Prompt options are not of type OpenAiAudioTranscriptionOptions: "
						+ transcriptionPrompt.getOptions().getClass().getSimpleName());
			}
		}

		Resource audioResource = transcriptionPrompt.getInstructions();
		byte[] audioBytes = toBytes(audioResource);
		String filename = audioResource.getFilename();
		if (filename == null) {
			filename = "audio";
		}

		TranscriptionCreateParams params = buildParams(options, audioBytes, filename);
		if (logger.isTraceEnabled()) {
			logger.trace("OpenAiAudioTranscriptionModel call with model: {}", options.getModel());
		}

		TranscriptionCreateResponse response = this.openAiClient.audio().transcriptions().create(params);
		String text = extractText(response);
		AudioTranscription transcript = new AudioTranscription(text);
		return new AudioTranscriptionResponse(transcript, new AudioTranscriptionResponseMetadata());
	}

	private TranscriptionCreateParams buildParams(OpenAiAudioTranscriptionOptions options, byte[] audioBytes,
			String filename) {
		MultipartField<InputStream> fileField = MultipartField.<InputStream>builder()
			.value(new ByteArrayInputStream(audioBytes))
			.filename(filename)
			.build();
		String model;
		if (options.getDeploymentName() != null) {
			model = options.getDeploymentName();
		}
		else {
			model = options.getModel();
		}
		Assert.notNull(model, "Model must not be null");
		TranscriptionCreateParams.Builder builder = TranscriptionCreateParams.builder().file(fileField).model(model);

		if (options.getResponseFormat() != null) {
			builder.responseFormat(options.getResponseFormat());
		}
		if (options.getLanguage() != null) {
			builder.language(options.getLanguage());
		}
		if (options.getPrompt() != null) {
			builder.prompt(options.getPrompt());
		}
		if (options.getTemperature() != null) {
			builder.temperature(options.getTemperature().doubleValue());
		}
		if (options.getTimestampGranularities() != null && !options.getTimestampGranularities().isEmpty()) {
			builder.timestampGranularities(options.getTimestampGranularities());
		}
		return builder.build();
	}

	private static String extractText(TranscriptionCreateResponse response) {
		if (response.isTranscription()) {
			return response.asTranscription().text();
		}
		if (response.isVerbose()) {
			return response.asVerbose().text();
		}
		if (response.isDiarized()) {
			return response.asDiarized().text();
		}
		return "";
	}

	private static byte[] toBytes(Resource resource) {
		Assert.notNull(resource, "Resource must not be null");
		try {
			return resource.getInputStream().readAllBytes();
		}
		catch (IOException e) {
			throw new IllegalArgumentException("Failed to read resource: " + resource, e);
		}
	}

	private static OpenAiAudioTranscriptionOptions merge(OpenAiAudioTranscriptionOptions source,
			OpenAiAudioTranscriptionOptions target) {
		return OpenAiAudioTranscriptionOptions.builder().from(target).merge(source).build();
	}

	/**
	 * Builder for creating {@link OpenAiAudioTranscriptionModel} instances.
	 */
	public static final class Builder {

		private @Nullable OpenAIClient openAiClient;

		private @Nullable OpenAiAudioTranscriptionOptions options;

		private Builder() {
		}

		private Builder(OpenAiAudioTranscriptionModel model) {
			this.openAiClient = model.openAiClient;
			this.options = model.defaultOptions;
		}

		/**
		 * Sets the OpenAI client.
		 * @param openAiClient the OpenAI client
		 * @return this builder
		 */
		public Builder openAiClient(OpenAIClient openAiClient) {
			this.openAiClient = openAiClient;
			return this;
		}

		/**
		 * Sets the transcription options.
		 * @param options the transcription options
		 * @return this builder
		 */
		public Builder options(OpenAiAudioTranscriptionOptions options) {
			this.options = options;
			return this;
		}

		/**
		 * Builds a new {@link OpenAiAudioTranscriptionModel} instance.
		 * @return the configured transcription model
		 */
		public OpenAiAudioTranscriptionModel build() {
			return new OpenAiAudioTranscriptionModel(this);
		}

	}

}
