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

package org.springframework.ai.mistralai.api;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;

import org.springframework.ai.retry.RetryUtils;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Mistral AI Audio API.
 *
 * @author Thomas Vitale
 * @see <a href= "https://docs.mistral.ai/studio/audio/speech-to-text/overview">Speech to
 * Text</a>
 * @see <a href=
 * "https://docs.mistral.ai/api/endpoint/audio/transcriptions">Transcriptions API</a>
 * @since 2.1.0
 */
public class MistralAiAudioApi {

	private static final String DEFAULT_BASE_URL = "https://api.mistral.ai";

	private static final String TRANSCRIPTIONS_PATH = "/v1/audio/transcriptions";

	private static final String DEFAULT_AUDIO_FILENAME = "audio";

	private final RestClient restClient;

	private final WebClient webClient;

	public MistralAiAudioApi(String baseUrl, String apiKey, RestClient.Builder restClientBuilder,
			WebClient.Builder webClientBuilder, ResponseErrorHandler responseErrorHandler) {
		Consumer<HttpHeaders> defaultHeaders = headers -> headers.setBearerAuth(apiKey);

		this.restClient = restClientBuilder.clone()
			.baseUrl(baseUrl)
			.defaultHeaders(defaultHeaders)
			.defaultStatusHandler(responseErrorHandler)
			.build();

		this.webClient = webClientBuilder.clone().baseUrl(baseUrl).defaultHeaders(defaultHeaders).build();
	}

	/**
	 * Transcribes audio into the input language.
	 * @param transcriptionRequest the transcription request, which must not ask for a
	 * streaming response
	 * @return the transcription response
	 */
	public ResponseEntity<TranscriptionResponse> createTranscription(TranscriptionRequest transcriptionRequest) {
		Assert.notNull(transcriptionRequest, "Transcription request cannot be null.");
		Assert.isTrue(!Boolean.TRUE.equals(transcriptionRequest.stream()),
				"Request must not set the stream property to true.");

		return this.restClient.post()
			.uri(TRANSCRIPTIONS_PATH)
			.contentType(MediaType.MULTIPART_FORM_DATA)
			.body(toMultipartBody(transcriptionRequest))
			.retrieve()
			.toEntity(TranscriptionResponse.class);
	}

	/**
	 * Transcribes audio into the input language, streaming the result as server-sent
	 * events.
	 * @param transcriptionRequest the transcription request, which must ask for a
	 * streaming response
	 * @return the stream of transcription events
	 */
	public Flux<TranscriptionStreamEvent> createTranscriptionStream(TranscriptionRequest transcriptionRequest) {
		Assert.notNull(transcriptionRequest, "Transcription request cannot be null.");
		Assert.isTrue(Boolean.TRUE.equals(transcriptionRequest.stream()),
				"Request must set the stream property to true.");

		return this.webClient.post()
			.uri(TRANSCRIPTIONS_PATH)
			.contentType(MediaType.MULTIPART_FORM_DATA)
			.accept(MediaType.TEXT_EVENT_STREAM)
			.body(BodyInserters.fromMultipartData(toMultipartBody(transcriptionRequest)))
			.retrieve()
			.bodyToFlux(TranscriptionStreamEvent.class);
	}

	private static MultiValueMap<String, Object> toMultipartBody(TranscriptionRequest request) {
		MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		body.add("model", request.model());
		if (request.file() != null) {
			body.add("file", withFilename(request.file()));
		}
		if (request.fileUrl() != null) {
			body.add("file_url", request.fileUrl());
		}
		if (request.fileId() != null) {
			body.add("file_id", request.fileId());
		}
		if (request.language() != null) {
			body.add("language", request.language());
		}
		if (request.temperature() != null) {
			body.add("temperature", String.valueOf(request.temperature()));
		}
		if (request.stream() != null) {
			body.add("stream", String.valueOf(request.stream()));
		}
		if (request.diarize() != null) {
			body.add("diarize", String.valueOf(request.diarize()));
		}
		if (request.contextBias() != null) {
			request.contextBias().forEach(contextBias -> body.add("context_bias", contextBias));
		}
		if (request.timestampGranularities() != null) {
			request.timestampGranularities()
				.forEach(granularity -> body.add("timestamp_granularities", granularity.getValue()));
		}
		return body;
	}

	/**
	 * A multipart file part is only written as a file when its resource has a filename.
	 */
	private static Resource withFilename(Resource resource) {
		if (resource.getFilename() != null) {
			return resource;
		}
		// Delegates instead of copying the content, so the audio is still streamed from
		// its source.
		return new AbstractResource() {

			@Override
			public String getFilename() {
				return DEFAULT_AUDIO_FILENAME;
			}

			@Override
			public InputStream getInputStream() throws IOException {
				return resource.getInputStream();
			}

			@Override
			public boolean exists() {
				return resource.exists();
			}

			@Override
			public boolean isOpen() {
				return resource.isOpen();
			}

			@Override
			public long contentLength() throws IOException {
				// The length of a read-once stream is unknown without consuming it.
				return (resource.isOpen() ? -1 : resource.contentLength());
			}

			@Override
			public String getDescription() {
				return resource.getDescription();
			}

		};
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private String baseUrl = DEFAULT_BASE_URL;

		private @Nullable String apiKey;

		private RestClient.Builder restClientBuilder = RestClient.builder();

		private WebClient.Builder webClientBuilder = WebClient.builder();

		private ResponseErrorHandler responseErrorHandler = RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER;

		public Builder baseUrl(String baseUrl) {
			Assert.hasText(baseUrl, "baseUrl cannot be null or empty");
			this.baseUrl = baseUrl;
			return this;
		}

		public Builder apiKey(String apiKey) {
			Assert.hasText(apiKey, "apiKey cannot be null or empty");
			this.apiKey = apiKey;
			return this;
		}

		public Builder restClientBuilder(RestClient.Builder restClientBuilder) {
			Assert.notNull(restClientBuilder, "restClientBuilder cannot be null");
			this.restClientBuilder = restClientBuilder;
			return this;
		}

		public Builder webClientBuilder(WebClient.Builder webClientBuilder) {
			Assert.notNull(webClientBuilder, "webClientBuilder cannot be null");
			this.webClientBuilder = webClientBuilder;
			return this;
		}

		public Builder responseErrorHandler(ResponseErrorHandler responseErrorHandler) {
			Assert.notNull(responseErrorHandler, "responseErrorHandler cannot be null");
			this.responseErrorHandler = responseErrorHandler;
			return this;
		}

		public MistralAiAudioApi build() {
			Assert.state(this.apiKey != null, "The API key must not be null");
			return new MistralAiAudioApi(this.baseUrl, this.apiKey, this.restClientBuilder, this.webClientBuilder,
					this.responseErrorHandler);
		}

	}

	/**
	 * List of well-known Mistral transcription models.
	 *
	 * @see <a href=
	 * "https://docs.mistral.ai/getting-started/models/models_overview/">Mistral AI Models
	 * Overview</a>
	 */
	public enum TranscriptionModel {

		VOXTRAL_MINI_LATEST("voxtral-mini-latest"),

		VOXTRAL_MINI_TRANSCRIBE_3("voxtral-mini-transcribe-3"),

		VOXTRAL_SMALL_TRANSCRIBE_3("voxtral-small-transcribe-3");

		private final String value;

		TranscriptionModel(String value) {
			this.value = value;
		}

		public String getValue() {
			return this.value;
		}

	}

	/**
	 * The granularities of the timestamps to include in a transcription.
	 */
	public enum TimestampGranularity {

		SEGMENT("segment"),

		WORD("word");

		private final String value;

		TimestampGranularity(String value) {
			this.value = value;
		}

		public String getValue() {
			return this.value;
		}

	}

	/**
	 * Request to transcribe audio, sent as multipart form data. Exactly one of
	 * {@code file}, {@code fileUrl} and {@code fileId} must be set.
	 *
	 * @param model ID of the model to use
	 * @param file the audio file to transcribe
	 * @param fileUrl URL of the audio file to transcribe
	 * @param fileId ID of an audio file uploaded to the Files API
	 * @param language the ISO 639-1 language of the audio, which can improve accuracy
	 * @param temperature the sampling temperature
	 * @param stream whether to stream the transcription as server-sent events
	 * @param diarize whether to identify the speaker of each segment, which requires
	 * segment timestamps
	 * @param contextBias words or phrases to bias the transcription towards
	 * @param timestampGranularities the granularities of the timestamps to include
	 */
	public record TranscriptionRequest(String model, @Nullable Resource file, @Nullable String fileUrl,
			@Nullable String fileId, @Nullable String language, @Nullable Double temperature, @Nullable Boolean stream,
			@Nullable Boolean diarize, @Nullable List<String> contextBias,
			@Nullable List<TimestampGranularity> timestampGranularities) {

		public TranscriptionRequest {
			Assert.hasText(model, "model cannot be null or empty");
			Assert.isTrue(Stream.of(file, fileUrl, fileId).filter(source -> source != null).count() == 1,
					"Exactly one of file, fileUrl and fileId must be set");
		}

		public static Builder builder() {
			return new Builder();
		}

		public static final class Builder {

			private @Nullable String model;

			private @Nullable Resource file;

			private @Nullable String fileUrl;

			private @Nullable String fileId;

			private @Nullable String language;

			private @Nullable Double temperature;

			private @Nullable Boolean stream;

			private @Nullable Boolean diarize;

			private @Nullable List<String> contextBias;

			private @Nullable List<TimestampGranularity> timestampGranularities;

			private Builder() {
			}

			public Builder model(String model) {
				this.model = model;
				return this;
			}

			public Builder file(@Nullable Resource file) {
				this.file = file;
				return this;
			}

			public Builder fileUrl(@Nullable String fileUrl) {
				this.fileUrl = fileUrl;
				return this;
			}

			public Builder fileId(@Nullable String fileId) {
				this.fileId = fileId;
				return this;
			}

			public Builder language(@Nullable String language) {
				this.language = language;
				return this;
			}

			public Builder temperature(@Nullable Double temperature) {
				this.temperature = temperature;
				return this;
			}

			public Builder stream(@Nullable Boolean stream) {
				this.stream = stream;
				return this;
			}

			public Builder diarize(@Nullable Boolean diarize) {
				this.diarize = diarize;
				return this;
			}

			public Builder contextBias(@Nullable List<String> contextBias) {
				this.contextBias = contextBias;
				return this;
			}

			public Builder timestampGranularities(@Nullable List<TimestampGranularity> timestampGranularities) {
				this.timestampGranularities = timestampGranularities;
				return this;
			}

			public TranscriptionRequest build() {
				Assert.state(this.model != null, "model cannot be null");
				return new TranscriptionRequest(this.model, this.file, this.fileUrl, this.fileId, this.language,
						this.temperature, this.stream, this.diarize, this.contextBias, this.timestampGranularities);
			}

		}

	}

	// @formatter:off
	/**
	 * Response of a transcription.
	 *
	 * @param model the model used for the transcription
	 * @param text the transcribed text
	 * @param language the language of the audio, if known
	 * @param segments the timestamped segments, when segment timestamps were requested
	 * @param usage the usage of the request
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record TranscriptionResponse(
			@JsonProperty("model") String model,
			@JsonProperty("text") String text,
			@JsonProperty("language") @Nullable String language,
			@JsonProperty("segments") @Nullable List<TranscriptionSegment> segments,
			@JsonProperty("usage") @Nullable Usage usage) {

	}

	/**
	 * A timestamped segment of a transcription.
	 *
	 * @param text the text of the segment
	 * @param start the start of the segment, in seconds
	 * @param end the end of the segment, in seconds
	 * @param score the confidence score of the segment
	 * @param speakerId the ID of the speaker of the segment, when diarization was
	 * requested
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record TranscriptionSegment(
			@JsonProperty("text") String text,
			@JsonProperty("start") @Nullable Double start,
			@JsonProperty("end") @Nullable Double end,
			@JsonProperty("score") @Nullable Double score,
			@JsonProperty("speaker_id") @Nullable String speakerId) {

	}

	/**
	 * Usage of a transcription request.
	 *
	 * @param promptTokens the number of tokens in the prompt
	 * @param completionTokens the number of tokens in the transcription
	 * @param totalTokens the total number of tokens
	 * @param promptAudioSeconds the duration of the audio, in seconds
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Usage(
			@JsonProperty("prompt_tokens") @Nullable Integer promptTokens,
			@JsonProperty("completion_tokens") @Nullable Integer completionTokens,
			@JsonProperty("total_tokens") @Nullable Integer totalTokens,
			@JsonProperty("prompt_audio_seconds") @Nullable Integer promptAudioSeconds) {

	}

	/**
	 * An event of a streamed transcription. Which fields are set depends on the event
	 * {@code type}:
	 * <ul>
	 * <li>{@value #TEXT_DELTA}: {@code text}</li>
	 * <li>{@value #LANGUAGE}: {@code audioLanguage}</li>
	 * <li>{@value #SEGMENT}: {@code text}, {@code start}, {@code end} and
	 * {@code speakerId}</li>
	 * <li>{@value #DONE}: {@code model}, {@code text}, {@code language},
	 * {@code segments} and {@code usage}, as in a {@link TranscriptionResponse}</li>
	 * </ul>
	 *
	 * @param type the type of the event
	 * @param text the transcribed text of the event
	 * @param audioLanguage the detected language of the audio
	 * @param start the start of the segment, in seconds
	 * @param end the end of the segment, in seconds
	 * @param speakerId the ID of the speaker of the segment
	 * @param model the model used for the transcription
	 * @param language the language of the audio, if known
	 * @param segments the timestamped segments of the whole transcription
	 * @param usage the usage of the request
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record TranscriptionStreamEvent(
			@JsonProperty("type") String type,
			@JsonProperty("text") @Nullable String text,
			@JsonProperty("audio_language") @Nullable String audioLanguage,
			@JsonProperty("start") @Nullable Double start,
			@JsonProperty("end") @Nullable Double end,
			@JsonProperty("speaker_id") @Nullable String speakerId,
			@JsonProperty("model") @Nullable String model,
			@JsonProperty("language") @Nullable String language,
			@JsonProperty("segments") @Nullable List<TranscriptionSegment> segments,
			@JsonProperty("usage") @Nullable Usage usage) {

		/**
		 * A chunk of the transcribed text.
		 */
		public static final String TEXT_DELTA = "transcription.text.delta";

		/**
		 * The detected language of the audio.
		 */
		public static final String LANGUAGE = "transcription.language";

		/**
		 * A timestamped segment of the transcription.
		 */
		public static final String SEGMENT = "transcription.segment";

		/**
		 * The end of the transcription, carrying the whole result.
		 */
		public static final String DONE = "transcription.done";

	}
	// @formatter:on

}
