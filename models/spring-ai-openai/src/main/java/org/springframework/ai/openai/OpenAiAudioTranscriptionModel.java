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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.core.MultipartField;
import com.openai.models.audio.transcriptions.TranscriptionCreateParams;
import com.openai.models.audio.transcriptions.TranscriptionCreateResponse;
import com.openai.models.audio.transcriptions.TranscriptionStreamEvent;
import io.micrometer.observation.ObservationRegistry;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;

import org.springframework.ai.audio.transcription.AudioTranscription;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponseMetadata;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.openai.http.okhttp.OpenAiHttpClientBuilderCustomizer;
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
 * @author Sebastien Deleuze
 * @author guan xu
 */
public final class OpenAiAudioTranscriptionModel implements TranscriptionModel {

	private static final Log logger = LogFactory.getLog(OpenAiAudioTranscriptionModel.class);

	private final OpenAIClient openAiClient;

	private final OpenAIClientAsync openAiClientAsync;

	private final OpenAiAudioTranscriptionOptions options;

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
		this.options = builder.options != null ? builder.options : OpenAiAudioTranscriptionOptions.builder().build();
		this.openAiClient = Objects.requireNonNullElseGet(builder.openAiClient,
				() -> OpenAiSetup.setupSyncClient(this.options.getBaseUrl(), this.options.getApiKey(),
						this.options.getCredential(), this.options.getMicrosoftDeploymentName(),
						this.options.getMicrosoftFoundryServiceVersion(), this.options.getOrganizationId(),
						this.options.isMicrosoftFoundry(), this.options.isGitHubModels(), this.options.getModel(),
						this.options.getTimeout(), this.options.getMaxRetries(), this.options.getProxy(),
						this.options.getCustomHeaders(), ObservationRegistry.NOOP, null,
						builder.httpClientCustomizers));
		this.openAiClientAsync = Objects.requireNonNullElseGet(builder.openAiClientAsync,
				() -> OpenAiSetup.setupAsyncClient(this.options.getBaseUrl(), this.options.getApiKey(),
						this.options.getCredential(), this.options.getMicrosoftDeploymentName(),
						this.options.getMicrosoftFoundryServiceVersion(), this.options.getOrganizationId(),
						this.options.isMicrosoftFoundry(), this.options.isGitHubModels(), this.options.getModel(),
						this.options.getTimeout(), this.options.getMaxRetries(), this.options.getProxy(),
						this.options.getCustomHeaders(), ObservationRegistry.NOOP, null,
						builder.httpClientCustomizers));
	}

	/**
	 * Gets the transcription options for this model.
	 * @return the transcription options
	 */
	public OpenAiAudioTranscriptionOptions getOptions() {
		return this.options;
	}

	@Override
	public AudioTranscriptionResponse call(AudioTranscriptionPrompt transcriptionPrompt) {
		// Merge request options with default options
		OpenAiAudioTranscriptionOptions mergedOptions = OpenAiAudioTranscriptionOptions.builder()
			.from(this.options)
			.merge(transcriptionPrompt.getOptions())
			.build();

		Resource audioResource = transcriptionPrompt.getInstructions();
		byte[] audioBytes = toBytes(audioResource);
		String filename = getFilename(audioResource);

		TranscriptionCreateParams params = buildParams(mergedOptions, audioBytes, filename);
		if (logger.isTraceEnabled()) {
			logger.trace("OpenAiAudioTranscriptionModel call with model: " + mergedOptions.getModel());
		}

		TranscriptionCreateResponse response = this.openAiClient.audio().transcriptions().create(params);
		String text = extractText(response);
		AudioTranscription transcript = new AudioTranscription(text);
		return new AudioTranscriptionResponse(transcript, new AudioTranscriptionResponseMetadata());
	}

	@Override
	public Flux<AudioTranscriptionResponse> stream(AudioTranscriptionPrompt transcriptionPrompt) {
		// Merge request options with default options
		OpenAiAudioTranscriptionOptions mergedOptions = OpenAiAudioTranscriptionOptions.builder()
			.from(this.options)
			.merge(transcriptionPrompt.getOptions())
			.build();

		Resource audioResource = transcriptionPrompt.getInstructions();
		byte[] audioBytes = toBytes(audioResource);
		String filename = getFilename(audioResource);

		TranscriptionCreateParams params = buildParams(mergedOptions, audioBytes, filename);
		if (logger.isTraceEnabled()) {
			logger.trace("OpenAiAudioTranscriptionModel stream with model: " + mergedOptions.getModel());
		}

		Flux<TranscriptionStreamEvent> chunk = Flux.create(sink -> {
			this.openAiClientAsync.audio()
				.transcriptions()
				.createStreaming(params)
				.subscribe(sink::next)
				.onCompleteFuture()
				.whenComplete((unused, throwable) -> {
					if (throwable != null) {
						sink.error(throwable);
					}
					else {
						sink.complete();
					}
				});
		});

		return chunk.map(event -> {
			String text = extractStreamEventText(event);
			AudioTranscription transcript = new AudioTranscription(text);
			return new AudioTranscriptionResponse(transcript, new AudioTranscriptionResponseMetadata());
		});
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

	private static String extractStreamEventText(TranscriptionStreamEvent event) {
		if (event.isTranscriptTextDelta()) {
			return event.asTranscriptTextDelta().delta();
		}
		if (event.isTranscriptTextSegment()) {
			return event.asTranscriptTextSegment().text();
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

	private static String getFilename(Resource audioResource) {
		String filename = audioResource.getFilename();
		if (filename == null) {
			filename = "audio";
		}
		return filename;
	}

	/**
	 * Builder for creating {@link OpenAiAudioTranscriptionModel} instances.
	 */
	public static final class Builder {

		private @Nullable OpenAIClient openAiClient;

		private @Nullable OpenAIClientAsync openAiClientAsync;

		private @Nullable OpenAiAudioTranscriptionOptions options;

		private List<OpenAiHttpClientBuilderCustomizer> httpClientCustomizers = new ArrayList<>();

		private Builder() {
		}

		private Builder(OpenAiAudioTranscriptionModel model) {
			this.openAiClient = model.openAiClient;
			this.openAiClientAsync = model.openAiClientAsync;
			this.options = model.options;
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
		 * Sets the OpenAI client async.
		 * @param openAiClientAsync the OpenAI client async
		 * @return this builder
		 */
		public Builder openAiClientAsync(OpenAIClientAsync openAiClientAsync) {
			this.openAiClientAsync = openAiClientAsync;
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
		 * Registers an {@link OpenAiHttpClientBuilderCustomizer} that mutates the
		 * underlying OkHttp client builder before the OpenAI clients are constructed. Use
		 * this to attach OkHttp interceptors (e.g. OAuth2 bearer-token injection), swap
		 * the dispatcher executor, or tweak any other OkHttp setting. Customizers are
		 * applied in the order they are registered, after Spring AI's own defaults, so
		 * user code wins.
		 */
		public Builder httpClientBuilderCustomizer(OpenAiHttpClientBuilderCustomizer customizer) {
			Assert.notNull(customizer, "customizer cannot be null");
			this.httpClientCustomizers.add(customizer);
			return this;
		}

		/**
		 * Sets the full list of {@link OpenAiHttpClientBuilderCustomizer customizers} to
		 * apply, replacing any customizers registered earlier on this builder. The order
		 * of the list is preserved when invoking the customizers.
		 */
		public Builder httpClientBuilderCustomizers(List<OpenAiHttpClientBuilderCustomizer> customizers) {
			Assert.notNull(customizers, "customizers cannot be null");
			this.httpClientCustomizers = new ArrayList<>(customizers);
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
