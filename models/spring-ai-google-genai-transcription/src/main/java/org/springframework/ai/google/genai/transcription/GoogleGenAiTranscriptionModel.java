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

package org.springframework.ai.google.genai.transcription;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import io.micrometer.observation.ObservationRegistry;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;

import org.springframework.ai.audio.transcription.AudioTranscription;
import org.springframework.ai.audio.transcription.AudioTranscriptionOptions;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.audio.transcription.observation.AudioTranscriptionModelObservationContext;
import org.springframework.ai.audio.transcription.observation.AudioTranscriptionModelObservationConvention;
import org.springframework.ai.audio.transcription.observation.AudioTranscriptionModelObservationDocumentation;
import org.springframework.ai.audio.transcription.observation.DefaultAudioTranscriptionModelObservationConvention;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * A {@link TranscriptionModel} implementation that transcribes audio with Gemini
 * transcription models (e.g. {@code gemini-3.5-transcribe}) through the Google Gen AI
 * SDK. The audio is sent inline in a {@code generateContent} request and the model
 * replies with the transcription text.
 *
 * @author Olivier Le Quellec
 * @since 2.0.1
 */
public class GoogleGenAiTranscriptionModel implements TranscriptionModel {

	private static final AudioTranscriptionModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultAudioTranscriptionModelObservationConvention();

	private static final String DEFAULT_AUDIO_MIME_TYPE = "audio/wav";

	private static final String USER_ROLE = "user";

	private final GoogleGenAiAudioTranscriptionOptions options;

	private final GoogleGenAiTranscriptionConnectionDetails connectionDetails;

	private final RetryTemplate retryTemplate;

	private final ObservationRegistry observationRegistry;

	private AudioTranscriptionModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	private final Client genAiClient;

	public GoogleGenAiTranscriptionModel(GoogleGenAiTranscriptionConnectionDetails connectionDetails,
			GoogleGenAiAudioTranscriptionOptions defaultOptions) {
		this(connectionDetails, defaultOptions, RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	public GoogleGenAiTranscriptionModel(GoogleGenAiTranscriptionConnectionDetails connectionDetails,
			GoogleGenAiAudioTranscriptionOptions defaultOptions, RetryTemplate retryTemplate) {
		this(connectionDetails, defaultOptions, retryTemplate, ObservationRegistry.NOOP);
	}

	public GoogleGenAiTranscriptionModel(GoogleGenAiTranscriptionConnectionDetails connectionDetails,
			GoogleGenAiAudioTranscriptionOptions defaultTranscriptionOptions, RetryTemplate retryTemplate,
			ObservationRegistry observationRegistry) {
		Assert.notNull(connectionDetails, "GoogleGenAiTranscriptionConnectionDetails must not be null");
		Assert.notNull(defaultTranscriptionOptions, "GoogleGenAiAudioTranscriptionOptions must not be null");
		Assert.notNull(retryTemplate, "retryTemplate must not be null");
		Assert.notNull(observationRegistry, "observationRegistry must not be null");

		this.options = defaultTranscriptionOptions;
		this.connectionDetails = connectionDetails;
		this.genAiClient = connectionDetails.getGenAiClient();
		this.retryTemplate = retryTemplate;
		this.observationRegistry = observationRegistry;
	}

	/**
	 * Creates a new builder for {@link GoogleGenAiTranscriptionModel}.
	 * @return a new builder instance
	 */
	public static Builder builder() {
		return new Builder();
	}

	@Override
	public AudioTranscriptionResponse call(AudioTranscriptionPrompt prompt) {
		final AudioTranscriptionPrompt transcriptionPrompt = buildTranscriptionPrompt(prompt);

		final AudioTranscriptionModelObservationContext observationContext = AudioTranscriptionModelObservationContext
			.builder()
			.transcriptionPrompt(transcriptionPrompt)
			.provider(AiProvider.GOOGLE_GENAI_AI.value())
			.build();

		return AudioTranscriptionModelObservationDocumentation.AUDIO_TRANSCRIPTION_MODEL_OPERATION
			.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(() -> {
				final GoogleGenAiAudioTranscriptionOptions options = (GoogleGenAiAudioTranscriptionOptions) transcriptionPrompt
					.getOptions();
				Assert.notNull(options, "Transcription options must not be null");

				final Resource audioResource = transcriptionPrompt.getInstructions();
				Assert.notNull(audioResource, "Audio resource must not be null");

				final Content content = Content.builder()
					.role(USER_ROLE)
					.parts(Part.fromBytes(readAudioBytes(audioResource), detectMimeType(audioResource)),
							Part.fromText(buildTranscriptionInstruction(options)))
					.build();

				final GenerateContentConfig config = buildGenerateContentConfig(options);

				final GenerateContentResponse generateContentResponse = RetryUtils.execute(this.retryTemplate,
						() -> this.genAiClient.models.generateContent(options.getModel(), content, config));

				final AudioTranscriptionResponse response = new AudioTranscriptionResponse(new AudioTranscription(
						Optional.ofNullable(generateContentResponse).map(GenerateContentResponse::text).orElse("")),
						new GoogleGenAiAudioTranscriptionMetadata(
								Optional.ofNullable(generateContentResponse.modelVersion())
									.orElseGet(Optional::empty)
									.orElse(null),
								toUsage(generateContentResponse)));

				observationContext.setResponse(response);

				return response;
			});
	}

	public void setObservationConvention(@Nullable AudioTranscriptionModelObservationConvention observationConvention) {
		Assert.notNull(observationConvention, "observationConvention cannot be null");
		this.observationConvention = observationConvention;
	}

	@Override
	public Flux<AudioTranscriptionResponse> stream(AudioTranscriptionPrompt prompt) {
		return Flux.just(call(prompt));
	}

	// Private methods

	private static @Nullable Usage toUsage(GenerateContentResponse response) {
		return response.usageMetadata()
			.map(usageMetadata -> new DefaultUsage(usageMetadata.promptTokenCount().orElse(null),
					usageMetadata.candidatesTokenCount().orElse(null), usageMetadata.totalTokenCount().orElse(null),
					usageMetadata))
			.orElse(null);
	}

	private AudioTranscriptionPrompt buildTranscriptionPrompt(AudioTranscriptionPrompt prompt) {
		final AudioTranscriptionOptions runtimeOptions = prompt.getOptions();
		if (Objects.isNull(runtimeOptions)) {
			return new AudioTranscriptionPrompt(prompt.getInstructions(), this.options);
		}

		final GoogleGenAiAudioTranscriptionOptions mergedOptions = GoogleGenAiAudioTranscriptionOptions.builder()
			.from(this.options)
			.merge(runtimeOptions)
			.build();

		return new AudioTranscriptionPrompt(prompt.getInstructions(), mergedOptions);
	}

	private static String buildTranscriptionInstruction(GoogleGenAiAudioTranscriptionOptions options) {
		final StringBuilder instruction = new StringBuilder(StringUtils.hasText(options.getPrompt())
				? options.getPrompt() : GoogleGenAiAudioTranscriptionOptions.DEFAULT_TRANSCRIPTION_PROMPT);

		if (StringUtils.hasText(options.getLanguage())) {
			instruction.append(" The audio language is ").append(options.getLanguage()).append('.');
		}

		return instruction.toString();
	}

	private static @Nullable GenerateContentConfig buildGenerateContentConfig(
			GoogleGenAiAudioTranscriptionOptions options) {
		if (Objects.isNull(options.getTemperature()) && Objects.isNull(options.getTopP())
				&& Objects.isNull(options.getTopK()) && Objects.isNull(options.getCandidateCount())
				&& Objects.isNull(options.getMaxOutputTokens()) && Objects.isNull(options.getStopSequences())
				&& Objects.isNull(options.getResponseMimeType()) && Objects.isNull(options.getPresencePenalty())
				&& Objects.isNull(options.getFrequencyPenalty()) && Objects.isNull(options.getSeed())) {
			return null;
		}
		final GenerateContentConfig.Builder builder = GenerateContentConfig.builder();
		if (Objects.nonNull(options.getTemperature())) {
			builder.temperature(options.getTemperature());
		}
		if (Objects.nonNull(options.getTopP())) {
			builder.topP(options.getTopP());
		}
		if (Objects.nonNull(options.getTopK())) {
			builder.topK(options.getTopK());
		}
		if (Objects.nonNull(options.getCandidateCount())) {
			builder.candidateCount(options.getCandidateCount());
		}
		if (Objects.nonNull(options.getMaxOutputTokens())) {
			builder.maxOutputTokens(options.getMaxOutputTokens());
		}
		if (Objects.nonNull(options.getStopSequences())) {
			builder.stopSequences(options.getStopSequences());
		}
		if (Objects.nonNull(options.getResponseMimeType())) {
			builder.responseMimeType(options.getResponseMimeType());
		}
		if (Objects.nonNull(options.getPresencePenalty())) {
			builder.presencePenalty(options.getPresencePenalty());
		}
		if (Objects.nonNull(options.getFrequencyPenalty())) {
			builder.frequencyPenalty(options.getFrequencyPenalty());
		}
		if (Objects.nonNull(options.getSeed())) {
			builder.seed(options.getSeed());
		}
		return builder.build();
	}

	private static byte[] readAudioBytes(Resource audioResource) {
		try {
			return audioResource.getContentAsByteArray();
		}
		catch (IOException exception) {
			throw new UncheckedIOException("Failed to read audio resource: " + audioResource, exception);
		}
	}

	private static String detectMimeType(Resource audioResource) {
		final String filename = audioResource.getFilename();
		if (StringUtils.hasText(filename)) {
			final int dotIndex = filename.lastIndexOf('.');
			if (dotIndex >= 0 && dotIndex < filename.length() - 1) {
				final String extension = filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
				return "audio/" + extension;
			}
		}
		return DEFAULT_AUDIO_MIME_TYPE;
	}

	/**
	 * Builder for creating {@link GoogleGenAiTranscriptionModel} instances.
	 */
	public static final class Builder {

		private @Nullable GoogleGenAiTranscriptionConnectionDetails connectionDetails;

		private @Nullable GoogleGenAiAudioTranscriptionOptions options;

		private @Nullable RetryTemplate retryTemplate;

		private @Nullable ObservationRegistry observationRegistry;

		private Builder() {
		}

		/**
		 * Sets the connection details used to access the Google Gen AI API.
		 * @param connectionDetails the connection details
		 * @return this builder
		 */
		public Builder connectionDetails(GoogleGenAiTranscriptionConnectionDetails connectionDetails) {
			this.connectionDetails = connectionDetails;
			return this;
		}

		/**
		 * Sets the default transcription options.
		 * @param options the default transcription options
		 * @return this builder
		 */
		public Builder options(GoogleGenAiAudioTranscriptionOptions options) {
			this.options = options;
			return this;
		}

		/**
		 * Sets the retry template. Defaults to {@link RetryUtils#DEFAULT_RETRY_TEMPLATE}
		 * when unset.
		 * @param retryTemplate the retry template
		 * @return this builder
		 */
		public Builder retryTemplate(RetryTemplate retryTemplate) {
			this.retryTemplate = retryTemplate;
			return this;
		}

		/**
		 * Sets the observation registry. Defaults to {@link ObservationRegistry#NOOP}
		 * when unset.
		 * @param observationRegistry the observation registry
		 * @return this builder
		 */
		public Builder observationRegistry(ObservationRegistry observationRegistry) {
			this.observationRegistry = observationRegistry;
			return this;
		}

		/**
		 * Builds a new {@link GoogleGenAiTranscriptionModel} instance.
		 * @return the configured transcription model
		 */
		public GoogleGenAiTranscriptionModel build() {
			Assert.notNull(this.connectionDetails, "GoogleGenAiTranscriptionConnectionDetails must not be null");
			Assert.notNull(this.options, "GoogleGenAiAudioTranscriptionOptions must not be null");

			return new GoogleGenAiTranscriptionModel(this.connectionDetails, this.options,
					Objects.requireNonNullElse(this.retryTemplate, RetryUtils.DEFAULT_RETRY_TEMPLATE),
					Objects.requireNonNullElse(this.observationRegistry, ObservationRegistry.NOOP));
		}

	}

}
