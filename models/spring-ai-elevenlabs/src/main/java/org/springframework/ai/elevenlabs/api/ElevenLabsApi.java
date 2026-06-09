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

package org.springframework.ai.elevenlabs.api;

import java.util.List;
import java.util.function.Consumer;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.ai.model.ApiKey;
import org.springframework.ai.model.NoopApiKey;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Client for the ElevenLabs Text-to-Speech API.
 *
 * @author Alexandros Pappas
 */
public final class ElevenLabsApi {

	public static final String DEFAULT_BASE_URL = "https://api.elevenlabs.io";

	private final RestClient restClient;

	private final WebClient webClient;

	/**
	 * Create a new ElevenLabs API client.
	 * @param baseUrl The base URL for the ElevenLabs API.
	 * @param apiKey Your ElevenLabs API key.
	 * @param headers the http headers to use.
	 * @param restClientBuilder A builder for the Spring RestClient.
	 * @param webClientBuilder A builder for the Spring WebClient.
	 * @param responseErrorHandler A custom error handler for API responses.
	 */
	private ElevenLabsApi(String baseUrl, ApiKey apiKey, HttpHeaders headers, RestClient.Builder restClientBuilder,
			WebClient.Builder webClientBuilder, ResponseErrorHandler responseErrorHandler) {

		Consumer<HttpHeaders> jsonContentHeaders = h -> {
			if (!(apiKey instanceof NoopApiKey)) {
				h.set("xi-api-key", apiKey.getValue());
			}
			h.addAll(HttpHeaders.readOnlyHttpHeaders(headers));
			h.setContentType(MediaType.APPLICATION_JSON);
		};

		this.restClient = restClientBuilder.clone()
			.baseUrl(baseUrl)
			.defaultHeaders(jsonContentHeaders)
			.defaultStatusHandler(responseErrorHandler)
			.build();

		this.webClient = webClientBuilder.clone().baseUrl(baseUrl).defaultHeaders(jsonContentHeaders).build();
	}

	/**
	 * Create a new ElevenLabs API client.
	 * @param restClient Spring RestClient instance.
	 * @param webClient Spring WebClient instance.
	 */
	public ElevenLabsApi(RestClient restClient, WebClient webClient) {
		this.restClient = restClient;
		this.webClient = webClient;
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Convert text to speech using the specified voice and parameters.
	 * @param requestBody The request body containing text, model, and voice settings.
	 * @param voiceId The ID of the voice to use. Must not be null.
	 * @param queryParameters Additional query parameters for the API call.
	 * @return A ResponseEntity containing the generated audio as a byte array.
	 */
	public ResponseEntity<byte[]> textToSpeech(SpeechRequest requestBody, String voiceId,
			MultiValueMap<String, String> queryParameters) {

		Assert.notNull(voiceId, "voiceId must be provided. It cannot be null.");
		Assert.notNull(requestBody, "requestBody can not be null.");
		Assert.hasText(requestBody.text(), "requestBody.text must be provided. It cannot be null or empty.");

		UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromPath("/v1/text-to-speech/{voice_id}")
			.queryParams(queryParameters);

		return this.restClient.post()
			.uri(uriBuilder.buildAndExpand(voiceId).toUriString())
			.body(requestBody)
			.retrieve()
			.toEntity(byte[].class);
	}

	/**
	 * Convert text to speech using the specified voice and parameters, streaming the
	 * results.
	 * @param requestBody The request body containing text, model, and voice settings.
	 * @param voiceId The ID of the voice to use. Must not be null.
	 * @param queryParameters Additional query parameters for the API call.
	 * @return A Flux of ResponseEntity containing the generated audio chunks as byte
	 * arrays.
	 */
	public Flux<ResponseEntity<byte[]>> textToSpeechStream(SpeechRequest requestBody, String voiceId,
			MultiValueMap<String, String> queryParameters) {
		Assert.notNull(voiceId, "voiceId must be provided for streaming. It cannot be null.");
		Assert.notNull(requestBody, "requestBody can not be null.");
		Assert.hasText(requestBody.text(), "requestBody.text must be provided. It cannot be null or empty.");

		UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromPath("/v1/text-to-speech/{voice_id}/stream")
			.queryParams(queryParameters);

		return this.webClient.post()
			.uri(uriBuilder.buildAndExpand(voiceId).toUriString())
			.body(Mono.just(requestBody), SpeechRequest.class)
			.accept(MediaType.APPLICATION_OCTET_STREAM)
			.exchangeToFlux(clientResponse -> {
				HttpHeaders headers = clientResponse.headers().asHttpHeaders();
				return clientResponse.bodyToFlux(byte[].class)
					.map(bytes -> ResponseEntity.ok().headers(headers).body(bytes));
			});
	}

	/**
	 * The output format of the generated audio.
	 */
	public enum OutputFormat {

		MP3_22050_32("mp3_22050_32"), MP3_44100_32("mp3_44100_32"), MP3_44100_64("mp3_44100_64"),
		MP3_44100_96("mp3_44100_96"), MP3_44100_128("mp3_44100_128"), MP3_44100_192("mp3_44100_192"),
		PCM_8000("pcm_8000"), PCM_16000("pcm_16000"), PCM_22050("pcm_22050"), PCM_24000("pcm_24000"),
		PCM_44100("pcm_44100"), PCM_48000("pcm_48000"), ULAW_8000("ulaw_8000"), ALAW_8000("alaw_8000"),
		OPUS_48000_32("opus_48000_32"), OPUS_48000_64("opus_48000_64"), OPUS_48000_96("opus_48000_96"),
		OPUS_48000_128("opus_48000_128"), OPUS_48000_192("opus_48000_192");

		private final String value;

		OutputFormat(String value) {
			this.value = value;
		}

		public String getValue() {
			return this.value;
		}

	}

	/**
	 * Represents a request to the ElevenLabs Text-to-Speech API.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record SpeechRequest(@JsonProperty("text") String text, @JsonProperty("model_id") @Nullable String modelId,
			@JsonProperty("language_code") @Nullable String languageCode,
			@JsonProperty("voice_settings") @Nullable VoiceSettings voiceSettings,
			@JsonProperty("pronunciation_dictionary_locators") @Nullable List<PronunciationDictionaryLocator> pronunciationDictionaryLocators,
			@JsonProperty("seed") @Nullable Integer seed, @JsonProperty("previous_text") @Nullable String previousText,
			@JsonProperty("next_text") @Nullable String nextText,
			@JsonProperty("previous_request_ids") @Nullable List<String> previousRequestIds,
			@JsonProperty("next_request_ids") @Nullable List<String> nextRequestIds,
			@JsonProperty("apply_text_normalization") @Nullable TextNormalizationMode applyTextNormalization,
			@JsonProperty("apply_language_text_normalization") @Nullable Boolean applyLanguageTextNormalization) {

		public static Builder builder() {
			return new Builder();
		}

		/**
		 * Text normalization mode.
		 */
		public enum TextNormalizationMode {

			@JsonProperty("auto")
			AUTO("auto"), @JsonProperty("on")
			ON("on"), @JsonProperty("off")
			OFF("off");

			public final String value;

			TextNormalizationMode(String value) {
				this.value = value;
			}

			@JsonValue
			public String getValue() {
				return this.value;
			}

		}

		/**
		 * Voice settings to override defaults for the given voice.
		 */
		@JsonInclude(JsonInclude.Include.NON_NULL)
		public record VoiceSettings(@JsonProperty("stability") @Nullable Double stability,
				@JsonProperty("similarity_boost") @Nullable Double similarityBoost,
				@JsonProperty("style") @Nullable Double style,
				@JsonProperty("use_speaker_boost") @Nullable Boolean useSpeakerBoost,
				@JsonProperty("speed") @Nullable Double speed) {
		}

		/**
		 * Locator for a pronunciation dictionary.
		 */
		@JsonInclude(JsonInclude.Include.NON_NULL)
		public record PronunciationDictionaryLocator(
				@JsonProperty("pronunciation_dictionary_id") String pronunciationDictionaryId,
				@JsonProperty("version_id") String versionId) {
		}

		public static final class Builder {

			private @Nullable String text;

			private @Nullable String modelId;

			private @Nullable String languageCode;

			private @Nullable VoiceSettings voiceSettings;

			private @Nullable List<PronunciationDictionaryLocator> pronunciationDictionaryLocators;

			private @Nullable Integer seed;

			private @Nullable String previousText;

			private @Nullable String nextText;

			private @Nullable List<String> previousRequestIds;

			private @Nullable List<String> nextRequestIds;

			private @Nullable TextNormalizationMode applyTextNormalization;

			private @Nullable Boolean applyLanguageTextNormalization = false;

			public Builder text(String text) {
				this.text = text;
				return this;
			}

			public Builder modelId(@Nullable String modelId) {
				this.modelId = modelId;
				return this;
			}

			public Builder languageCode(@Nullable String languageCode) {
				this.languageCode = languageCode;
				return this;
			}

			public Builder voiceSettings(@Nullable VoiceSettings voiceSettings) {
				this.voiceSettings = voiceSettings;
				return this;
			}

			public Builder pronunciationDictionaryLocators(
					@Nullable List<PronunciationDictionaryLocator> pronunciationDictionaryLocators) {
				this.pronunciationDictionaryLocators = pronunciationDictionaryLocators;
				return this;
			}

			public Builder seed(@Nullable Integer seed) {
				this.seed = seed;
				return this;
			}

			public Builder previousText(@Nullable String previousText) {
				this.previousText = previousText;
				return this;
			}

			public Builder nextText(@Nullable String nextText) {
				this.nextText = nextText;
				return this;
			}

			public Builder previousRequestIds(@Nullable List<String> previousRequestIds) {
				this.previousRequestIds = previousRequestIds;
				return this;
			}

			public Builder nextRequestIds(@Nullable List<String> nextRequestIds) {
				this.nextRequestIds = nextRequestIds;
				return this;
			}

			public Builder applyTextNormalization(@Nullable TextNormalizationMode applyTextNormalization) {
				this.applyTextNormalization = applyTextNormalization;
				return this;
			}

			public Builder applyLanguageTextNormalization(@Nullable Boolean applyLanguageTextNormalization) {
				this.applyLanguageTextNormalization = applyLanguageTextNormalization;
				return this;
			}

			public SpeechRequest build() {
				Assert.hasText(this.text, "text must not be empty");
				return new SpeechRequest(this.text, this.modelId, this.languageCode, this.voiceSettings,
						this.pronunciationDictionaryLocators, this.seed, this.previousText, this.nextText,
						this.previousRequestIds, this.nextRequestIds, this.applyTextNormalization,
						this.applyLanguageTextNormalization);
			}

		}

	}

	/**
	 * Builder to construct {@link ElevenLabsApi} instance.
	 */
	public static final class Builder {

		private String baseUrl = DEFAULT_BASE_URL;

		private @Nullable ApiKey apiKey;

		private HttpHeaders headers = new HttpHeaders();

		private RestClient.Builder restClientBuilder = RestClient.builder();

		private WebClient.Builder webClientBuilder = WebClient.builder();

		private ResponseErrorHandler responseErrorHandler = RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER;

		public Builder baseUrl(String baseUrl) {
			Assert.hasText(baseUrl, "baseUrl cannot be null or empty");
			this.baseUrl = baseUrl;
			return this;
		}

		public Builder apiKey(@Nullable ApiKey apiKey) {
			this.apiKey = apiKey;
			return this;
		}

		public Builder apiKey(@Nullable String simpleApiKey) {
			this.apiKey = simpleApiKey != null ? new SimpleApiKey(simpleApiKey) : null;
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

		public ElevenLabsApi build() {
			Assert.notNull(this.apiKey, "apiKey must be set");
			return new ElevenLabsApi(this.baseUrl, this.apiKey, this.headers, this.restClientBuilder,
					this.webClientBuilder, this.responseErrorHandler);
		}

	}

}
