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

package org.springframework.ai.elevenlabs.api;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

import org.springframework.ai.model.ApiKey;
import org.springframework.ai.model.NoopApiKey;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;

/**
 * Client for the ElevenLabs Speech-to-Text API.
 *
 * @author Alexandros Pappas
 * @since 1.0.0
 */
public final class ElevenLabsSpeechToTextApi {

	public static final String DEFAULT_BASE_URL = "https://api.elevenlabs.io";

	private final RestClient restClient;

	private ElevenLabsSpeechToTextApi(String baseUrl, ApiKey apiKey, HttpHeaders headers,
			RestClient.Builder restClientBuilder, ResponseErrorHandler responseErrorHandler) {

		Consumer<HttpHeaders> jsonContentHeaders = h -> {
			if (!(apiKey instanceof NoopApiKey)) {
				h.set("xi-api-key", apiKey.getValue());
			}
			h.addAll(HttpHeaders.readOnlyHttpHeaders(headers));
		};

		this.restClient = restClientBuilder.baseUrl(baseUrl)
			.defaultHeaders(jsonContentHeaders)
			.defaultStatusHandler(responseErrorHandler)
			.build();
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Create a transcription from an audio file. POST /v1/speech-to-text
	 * @param request The transcription request with file and options.
	 * @return ResponseEntity containing the transcription response.
	 */
	public ResponseEntity<SpeechToTextResponse> createTranscription(TranscriptionRequest request) {
		Assert.notNull(request, "request must not be null");
		Assert.isTrue(request.file() != null || request.cloudStorageUrl() != null,
				"Either file or cloudStorageUrl must be provided");

		MultiValueMap<String, Object> multipartBody = new LinkedMultiValueMap<>();

		if (request.file() != null) {
			multipartBody.add("file", new ByteArrayResource(request.file()) {
				@Override
				public String getFilename() {
					return request.fileName() != null ? request.fileName() : "audio.mp3";
				}
			});
		}

		if (request.modelId() != null) {
			multipartBody.add("model_id", request.modelId());
		}
		if (request.languageCode() != null) {
			multipartBody.add("language_code", request.languageCode());
		}
		if (request.tagAudioEvents() != null) {
			multipartBody.add("tag_audio_events", request.tagAudioEvents().toString());
		}
		if (request.numSpeakers() != null) {
			multipartBody.add("num_speakers", request.numSpeakers().toString());
		}
		if (request.timestampsGranularity() != null) {
			multipartBody.add("timestamps_granularity", request.timestampsGranularity().getValue());
		}
		if (request.diarize() != null) {
			multipartBody.add("diarize", request.diarize().toString());
		}
		if (request.diarizationThreshold() != null) {
			multipartBody.add("diarization_threshold", request.diarizationThreshold().toString());
		}
		if (request.fileFormat() != null) {
			multipartBody.add("file_format", request.fileFormat().getValue());
		}
		if (request.cloudStorageUrl() != null) {
			multipartBody.add("cloud_storage_url", request.cloudStorageUrl());
		}
		if (request.webhook() != null) {
			multipartBody.add("webhook", request.webhook().toString());
		}
		if (request.webhookId() != null) {
			multipartBody.add("webhook_id", request.webhookId());
		}
		if (request.temperature() != null) {
			multipartBody.add("temperature", request.temperature().toString());
		}
		if (request.seed() != null) {
			multipartBody.add("seed", request.seed().toString());
		}
		if (request.enableLogging() != null) {
			multipartBody.add("enable_logging", request.enableLogging().toString());
		}

		return this.restClient.post()
			.uri("/v1/speech-to-text")
			.contentType(MediaType.MULTIPART_FORM_DATA)
			.body(multipartBody)
			.retrieve()
			.toEntity(SpeechToTextResponse.class);
	}

	/**
	 * Retrieve a transcription by ID. GET
	 * /v1/speech-to-text/transcripts/{transcription_id}
	 * @param transcriptionId The ID of the transcription to retrieve.
	 * @return ResponseEntity containing the transcription response.
	 */
	public ResponseEntity<SpeechToTextResponse> getTranscription(String transcriptionId) {
		Assert.hasText(transcriptionId, "transcriptionId must not be empty");

		return this.restClient.get()
			.uri("/v1/speech-to-text/{transcription_id}", transcriptionId)
			.retrieve()
			.toEntity(SpeechToTextResponse.class);
	}

	/**
	 * Timestamps granularity for transcription.
	 */
	public enum TimestampsGranularity {

		@JsonProperty("none")
		NONE("none"),

		@JsonProperty("word")
		WORD("word"),

		@JsonProperty("character")
		CHARACTER("character");

		private final String value;

		TimestampsGranularity(String value) {
			this.value = value;
		}

		@JsonValue
		public String getValue() {
			return this.value;
		}

	}

	/**
	 * File format hint for transcription.
	 */
	public enum FileFormat {

		@JsonProperty("pcm_s16le_16000")
		PCM_S16LE_16000("pcm_s16le_16000"),

		@JsonProperty("other")
		OTHER("other");

		private final String value;

		FileFormat(String value) {
			this.value = value;
		}

		@JsonValue
		public String getValue() {
			return this.value;
		}

	}

	/**
	 * Word type in transcription response.
	 */
	public enum WordType {

		@JsonProperty("word")
		WORD("word"),

		@JsonProperty("spacing")
		SPACING("spacing"),

		@JsonProperty("audio_event")
		AUDIO_EVENT("audio_event");

		private final String value;

		WordType(String value) {
			this.value = value;
		}

		@JsonValue
		public String getValue() {
			return this.value;
		}

	}

	/**
	 * Character timing information.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Character(@JsonProperty("character") String character, @JsonProperty("start_time") Double startTime,
			@JsonProperty("end_time") Double endTime) {
	}

	/**
	 * Word information with timing and speaker data.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Word(@JsonProperty("text") String text, @JsonProperty("start") Double start,
			@JsonProperty("end") Double end, @JsonProperty("type") WordType type,
			@JsonProperty("speaker_id") String speakerId, @JsonProperty("characters") List<Character> characters) {
	}

	/**
	 * Speech-to-text response from ElevenLabs API.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record SpeechToTextResponse(@JsonProperty("text") String text,
			@JsonProperty("language_code") String languageCode,
			@JsonProperty("language_probability") Double languageProbability, @JsonProperty("words") List<Word> words,
			@JsonProperty("transcription_id") String transcriptionId) {
	}

	/**
	 * Transcription request parameters.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record TranscriptionRequest(byte[] file, String fileName, String modelId, String languageCode,
			Boolean tagAudioEvents, Integer numSpeakers, TimestampsGranularity timestampsGranularity, Boolean diarize,
			Float diarizationThreshold, FileFormat fileFormat, String cloudStorageUrl, Boolean webhook,
			String webhookId, Map<String, Object> webhookMetadata, Float temperature, Integer seed,
			Boolean enableLogging) {

		public static Builder builder() {
			return new Builder();
		}

		public static final class Builder {

			private byte[] file;

			private String fileName;

			private String modelId = "scribe_v1";

			private String languageCode;

			private Boolean tagAudioEvents;

			private Integer numSpeakers;

			private TimestampsGranularity timestampsGranularity;

			private Boolean diarize;

			private Float diarizationThreshold;

			private FileFormat fileFormat;

			private String cloudStorageUrl;

			private Boolean webhook;

			private String webhookId;

			private Map<String, Object> webhookMetadata;

			private Float temperature;

			private Integer seed;

			private Boolean enableLogging;

			/**
			 * Sets the audio file content to transcribe.
			 * <p>
			 * This method is mutually exclusive with {@link #cloudStorageUrl(String)}.
			 * You must provide either a file or a cloud storage URL, but not both.
			 * Supported audio formats include: mp3, mp4, mpeg, mpga, m4a, wav, webm,
			 * flac, ogg, and opus.
			 * @param file the audio file content as a byte array
			 * @return this builder
			 */
			public Builder file(byte[] file) {
				this.file = file;
				return this;
			}

			/**
			 * Sets the filename hint for the audio file.
			 * <p>
			 * This is used as a hint for file type detection and does not need to match
			 * the actual source filename. If not provided, defaults to "audio.mp3".
			 * @param fileName the filename to use for the audio file
			 * @return this builder
			 */
			public Builder fileName(String fileName) {
				this.fileName = fileName;
				return this;
			}

			/**
			 * Sets the transcription model to use.
			 * <p>
			 * Defaults to "scribe_v1" if not specified. The model determines the
			 * accuracy, speed, and features available for transcription.
			 * @param modelId the ID of the model to use for transcription
			 * @return this builder
			 */
			public Builder modelId(String modelId) {
				this.modelId = modelId;
				return this;
			}

			/**
			 * Sets the language code hint for auto-detection.
			 * <p>
			 * Provide the expected language of the audio in ISO 639-1 format (e.g., "en"
			 * for English, "es" for Spanish). This helps improve transcription accuracy
			 * and speed by narrowing the language detection scope. Note that while the
			 * input uses ISO 639-1 (2-letter codes), the API response will return ISO
			 * 639-3 (3-letter codes, e.g., "eng" for English).
			 * @param languageCode the ISO 639-1 language code
			 * @return this builder
			 */
			public Builder languageCode(String languageCode) {
				this.languageCode = languageCode;
				return this;
			}

			/**
			 * Enables tagging of non-speech audio events in the transcription.
			 * <p>
			 * When enabled, the transcription will identify and tag audio events such as
			 * laughter, applause, music, and other non-speech sounds in the output. These
			 * events will be marked with special tags in the transcription text.
			 * @param tagAudioEvents true to enable audio event tagging, false to disable
			 * @return this builder
			 */
			public Builder tagAudioEvents(Boolean tagAudioEvents) {
				this.tagAudioEvents = tagAudioEvents;
				return this;
			}

			/**
			 * Sets the expected number of speakers for diarization.
			 * <p>
			 * This parameter is only used when {@link #diarize(Boolean)} is set to true.
			 * Providing an accurate speaker count helps improve the quality of speaker
			 * separation in the transcription. If not specified, the model will attempt
			 * to automatically detect the number of speakers.
			 * @param numSpeakers the expected number of distinct speakers in the audio
			 * @return this builder
			 */
			public Builder numSpeakers(Integer numSpeakers) {
				this.numSpeakers = numSpeakers;
				return this;
			}

			/**
			 * Sets the granularity level for timestamp information in the transcription.
			 * <p>
			 * Controls the detail level of timing information returned:
			 * <ul>
			 * <li>{@link TimestampsGranularity#NONE NONE} - No timestamp information is
			 * included in the response. Use this for simple text-only
			 * transcriptions.</li>
			 * <li>{@link TimestampsGranularity#WORD WORD} - Timestamps are provided at
			 * the word level, indicating when each word starts and ends. Useful for
			 * subtitle generation and word-level analysis.</li>
			 * <li>{@link TimestampsGranularity#CHARACTER CHARACTER} - Timestamps are
			 * provided at the character level, offering the most granular timing
			 * information. Ideal for precise synchronization and detailed analysis.</li>
			 * </ul>
			 * @param timestampsGranularity the desired timestamp granularity level
			 * @return this builder
			 */
			public Builder timestampsGranularity(TimestampsGranularity timestampsGranularity) {
				this.timestampsGranularity = timestampsGranularity;
				return this;
			}

			/**
			 * Enables speaker diarization (speaker separation) in the transcription.
			 * <p>
			 * When enabled, the transcription will attempt to identify different speakers
			 * and assign speaker IDs to each segment of speech. This is useful for
			 * multi-speaker recordings such as meetings, interviews, or conversations.
			 * Use {@link #numSpeakers(Integer)} to provide a hint about the expected
			 * number of speakers for improved accuracy.
			 * @param diarize true to enable speaker diarization, false to disable
			 * @return this builder
			 */
			public Builder diarize(Boolean diarize) {
				this.diarize = diarize;
				return this;
			}

			/**
			 * Sets the clustering threshold for speaker diarization.
			 * <p>
			 * This parameter controls the sensitivity of speaker separation. Lower values
			 * result in more conservative clustering (fewer speakers identified, reducing
			 * false positives), while higher values result in more aggressive clustering
			 * (more speakers identified, potentially increasing false positives). Only
			 * applicable when {@link #diarize(Boolean)} is set to true.
			 * @param diarizationThreshold the threshold value for speaker clustering
			 * @return this builder
			 */
			public Builder diarizationThreshold(Float diarizationThreshold) {
				this.diarizationThreshold = diarizationThreshold;
				return this;
			}

			/**
			 * Sets the file format hint for audio processing optimization.
			 * <p>
			 * Providing the correct format can improve processing efficiency and
			 * accuracy. Use {@link FileFormat#PCM_S16LE_16000} for raw PCM audio at 16kHz
			 * sample rate, or {@link FileFormat#OTHER} for other formats. The API will
			 * attempt to auto-detect the format if not specified.
			 * @param fileFormat the audio file format hint
			 * @return this builder
			 */
			public Builder fileFormat(FileFormat fileFormat) {
				this.fileFormat = fileFormat;
				return this;
			}

			/**
			 * Sets a cloud storage URL as an alternative to uploading a file directly.
			 * <p>
			 * This method is mutually exclusive with {@link #file(byte[])}. The provided
			 * URL must point to a publicly accessible audio file in one of the supported
			 * formats. This is useful for processing large audio files or files already
			 * stored in cloud storage without needing to download and re-upload them.
			 * @param cloudStorageUrl the publicly accessible URL of the audio file
			 * @return this builder
			 */
			public Builder cloudStorageUrl(String cloudStorageUrl) {
				this.cloudStorageUrl = cloudStorageUrl;
				return this;
			}

			/**
			 * Enables asynchronous webhook-based processing for the transcription.
			 * <p>
			 * When enabled, the API will process the transcription asynchronously and
			 * send the results to a configured webhook endpoint instead of returning them
			 * immediately. This is useful for processing large files or when integrating
			 * with event-driven architectures. Use {@link #webhookId(String)} and
			 * {@link #webhookMetadata(Map)} to provide additional webhook context.
			 * @param webhook true to enable webhook processing, false for synchronous
			 * processing
			 * @return this builder
			 */
			public Builder webhook(Boolean webhook) {
				this.webhook = webhook;
				return this;
			}

			/**
			 * Sets an identifier for the webhook callback.
			 * <p>
			 * This identifier will be included in the webhook payload, allowing you to
			 * correlate webhook callbacks with specific transcription requests. Only used
			 * when {@link #webhook(Boolean)} is set to true.
			 * @param webhookId the webhook identifier
			 * @return this builder
			 */
			public Builder webhookId(String webhookId) {
				this.webhookId = webhookId;
				return this;
			}

			/**
			 * Sets custom metadata to be included in the webhook callback.
			 * <p>
			 * This metadata will be included in the webhook payload and can contain any
			 * additional context needed to process the transcription result in your
			 * webhook handler. Only used when {@link #webhook(Boolean)} is set to true.
			 * @param webhookMetadata a map of custom metadata key-value pairs
			 * @return this builder
			 */
			public Builder webhookMetadata(Map<String, Object> webhookMetadata) {
				this.webhookMetadata = webhookMetadata;
				return this;
			}

			/**
			 * Sets the sampling temperature for transcription.
			 * <p>
			 * The temperature controls the randomness of the model's output. Valid values
			 * range from 0.0 to 1.0. Lower values (e.g., 0.0-0.3) make the output more
			 * deterministic and focused, which is generally preferred for transcription
			 * accuracy. Higher values (e.g., 0.7-1.0) increase randomness and creativity,
			 * which may be useful in specific scenarios but can reduce transcription
			 * accuracy.
			 * @param temperature the sampling temperature (0.0-1.0)
			 * @return this builder
			 */
			public Builder temperature(Float temperature) {
				this.temperature = temperature;
				return this;
			}

			/**
			 * Sets a random seed for reproducible transcription results.
			 * <p>
			 * Providing a seed value ensures that multiple transcriptions of the same
			 * audio with the same parameters will produce identical results. This is
			 * useful for testing, debugging, and scenarios where consistency across
			 * multiple runs is required.
			 * @param seed the random seed value
			 * @return this builder
			 */
			public Builder seed(Integer seed) {
				this.seed = seed;
				return this;
			}

			/**
			 * Enables server-side logging for this transcription request.
			 * <p>
			 * When enabled, the API provider may log the audio and transcription for
			 * quality improvement, debugging, or compliance purposes. Consider privacy
			 * implications before enabling this for sensitive audio content.
			 * @param enableLogging true to enable server-side logging, false to disable
			 * @return this builder
			 */
			public Builder enableLogging(Boolean enableLogging) {
				this.enableLogging = enableLogging;
				return this;
			}

			public TranscriptionRequest build() {
				return new TranscriptionRequest(this.file, this.fileName, this.modelId, this.languageCode,
						this.tagAudioEvents, this.numSpeakers, this.timestampsGranularity, this.diarize,
						this.diarizationThreshold, this.fileFormat, this.cloudStorageUrl, this.webhook, this.webhookId,
						this.webhookMetadata, this.temperature, this.seed, this.enableLogging);
			}

		}

	}

	/**
	 * Builder to construct {@link ElevenLabsSpeechToTextApi} instance.
	 */
	public static final class Builder {

		private String baseUrl = DEFAULT_BASE_URL;

		private ApiKey apiKey;

		private HttpHeaders headers = new HttpHeaders();

		private RestClient.Builder restClientBuilder = RestClient.builder();

		private ResponseErrorHandler responseErrorHandler = RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER;

		public Builder baseUrl(String baseUrl) {
			Assert.hasText(baseUrl, "baseUrl cannot be null or empty");
			this.baseUrl = baseUrl;
			return this;
		}

		public Builder apiKey(ApiKey apiKey) {
			Assert.notNull(apiKey, "apiKey cannot be null");
			this.apiKey = apiKey;
			return this;
		}

		public Builder apiKey(String simpleApiKey) {
			Assert.notNull(simpleApiKey, "simpleApiKey cannot be null");
			this.apiKey = new SimpleApiKey(simpleApiKey);
			return this;
		}

		public Builder headers(HttpHeaders headers) {
			Assert.notNull(headers, "headers cannot be null");
			this.headers = headers;
			return this;
		}

		public Builder restClientBuilder(RestClient.Builder restClientBuilder) {
			Assert.notNull(restClientBuilder, "restClientBuilder cannot be null");
			this.restClientBuilder = restClientBuilder;
			return this;
		}

		public Builder responseErrorHandler(ResponseErrorHandler responseErrorHandler) {
			Assert.notNull(responseErrorHandler, "responseErrorHandler cannot be null");
			this.responseErrorHandler = responseErrorHandler;
			return this;
		}

		public ElevenLabsSpeechToTextApi build() {
			Assert.notNull(this.apiKey, "apiKey must be set");
			return new ElevenLabsSpeechToTextApi(this.baseUrl, this.apiKey, this.headers, this.restClientBuilder,
					this.responseErrorHandler);
		}

	}

}
