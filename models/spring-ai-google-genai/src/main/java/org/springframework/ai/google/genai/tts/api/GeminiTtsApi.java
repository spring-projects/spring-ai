/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.google.genai.tts.api;

import java.util.Base64;
import java.util.List;
import java.util.function.Consumer;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClient;

/**
 * Client for Google Gemini Text-to-Speech (TTS) API.
 *
 * <p>
 * This API provides text-to-speech capabilities using Gemini models. It supports both
 * single-speaker and multi-speaker audio generation.
 *
 * <p>
 * Example usage for single-speaker: <pre>{@code
 * GeminiTtsApi api = new GeminiTtsApi("https://generativelanguage.googleapis.com/v1beta", apiKey);
 *
 * var voiceConfig = new VoiceConfig(new PrebuiltVoiceConfig("Kore"));
 * var speechConfig = new SpeechConfig(voiceConfig, null);
 * var generationConfig = new GenerationConfig(List.of("AUDIO"), speechConfig);
 * var content = new Content(List.of(new Part("Say hello world!")));
 * var request = new GenerateContentRequest(List.of(content), generationConfig);
 *
 * ResponseEntity<GenerateContentResponse> response = api.generateContent("gemini-2.5-flash-preview-tts", request);
 * byte[] audioData = GeminiTtsApi.extractAudioData(response.getBody());
 * }</pre>
 *
 * @author Alexandros Pappas
 * @since 1.1.0
 */
public class GeminiTtsApi {

	private static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta";

	private final RestClient restClient;

	/**
	 * Create a new Gemini TTS API client with default base URL.
	 * @param apiKey The API key for authentication
	 */
	public GeminiTtsApi(String apiKey) {
		this(DEFAULT_BASE_URL, apiKey);
	}

	/**
	 * Create a new Gemini TTS API client.
	 * @param baseUrl The base URL for the API
	 * @param apiKey The API key for authentication
	 */
	public GeminiTtsApi(String baseUrl, String apiKey) {
		this(baseUrl, apiKey, RestClient.builder());
	}

	/**
	 * Create a new Gemini TTS API client with custom RestClient builder.
	 * @param baseUrl The base URL for the API
	 * @param apiKey The API key for authentication
	 * @param restClientBuilder RestClient builder for customization
	 */
	public GeminiTtsApi(String baseUrl, String apiKey, RestClient.Builder restClientBuilder) {
		this(baseUrl, apiKey, restClientBuilder, HttpHeaders::new);
	}

	/**
	 * Create a new Gemini TTS API client with custom headers.
	 * @param baseUrl The base URL for the API
	 * @param apiKey The API key for authentication
	 * @param restClientBuilder RestClient builder for customization
	 * @param defaultHeaders Consumer for adding default headers
	 */
	public GeminiTtsApi(String baseUrl, String apiKey, RestClient.Builder restClientBuilder,
			Consumer<HttpHeaders> defaultHeaders) {

		Assert.hasText(baseUrl, "Base URL must not be empty");
		Assert.hasText(apiKey, "API key must not be empty");
		Assert.notNull(restClientBuilder, "RestClient.Builder must not be null");

		this.restClient = restClientBuilder.baseUrl(baseUrl)
			.defaultHeaders(defaultHeaders)
			.defaultHeader("x-goog-api-key", apiKey)
			.build();
	}

	/**
	 * Generate speech from text using the specified model.
	 * @param model The model name (e.g., "gemini-2.5-flash-preview-tts")
	 * @param request The generation request
	 * @return Response containing base64-encoded audio data
	 */
	public ResponseEntity<GenerateContentResponse> generateContent(String model, GenerateContentRequest request) {
		Assert.hasText(model, "Model must not be empty");
		Assert.notNull(request, "Request must not be null");

		return this.restClient.post()
			.uri("/models/{model}:generateContent", model)
			.body(request)
			.retrieve()
			.toEntity(GenerateContentResponse.class);
	}

	/**
	 * Extract and decode the audio data from the response.
	 * @param response The API response
	 * @return Decoded PCM audio bytes
	 */
	public static byte[] extractAudioData(GenerateContentResponse response) {
		if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
			return new byte[0];
		}

		var candidate = response.candidates().get(0);
		if (candidate.content() == null || candidate.content().parts() == null
				|| candidate.content().parts().isEmpty()) {
			return new byte[0];
		}

		var part = candidate.content().parts().get(0);
		if (part.inlineData() == null || part.inlineData().data() == null) {
			return new byte[0];
		}

		return Base64.getDecoder().decode(part.inlineData().data());
	}

	// Request POJOs

	/**
	 * Request to generate content (audio from text).
	 *
	 * @param contents The input content containing text to convert to speech
	 * @param generationConfig Configuration for the generation including speech settings
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record GenerateContentRequest(@JsonProperty("contents") List<Content> contents,
			@JsonProperty("generationConfig") GenerationConfig generationConfig) {
	}

	/**
	 * Content containing parts to be converted to speech.
	 *
	 * @param parts List of parts (typically one text part)
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Content(@JsonProperty("parts") List<Part> parts) {
	}

	/**
	 * A part of the content (text to be spoken).
	 *
	 * @param text The text content to convert to speech
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Part(@JsonProperty("text") String text) {
	}

	/**
	 * Generation configuration specifying output modality and speech settings.
	 *
	 * @param responseModalities List of response modalities (e.g., ["AUDIO"])
	 * @param speechConfig Speech-specific configuration
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record GenerationConfig(@JsonProperty("responseModalities") List<String> responseModalities,
			@JsonProperty("speechConfig") SpeechConfig speechConfig) {
	}

	/**
	 * Speech configuration for single or multi-speaker audio generation.
	 *
	 * @param voiceConfig Configuration for single-speaker voice (mutually exclusive with
	 * multiSpeakerVoiceConfig)
	 * @param multiSpeakerVoiceConfig Configuration for multi-speaker voices (mutually
	 * exclusive with voiceConfig)
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record SpeechConfig(@JsonProperty("voiceConfig") VoiceConfig voiceConfig,
			@JsonProperty("multiSpeakerVoiceConfig") MultiSpeakerVoiceConfig multiSpeakerVoiceConfig) {
	}

	/**
	 * Voice configuration for a speaker.
	 *
	 * @param prebuiltVoiceConfig The prebuilt voice to use
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record VoiceConfig(@JsonProperty("prebuiltVoiceConfig") PrebuiltVoiceConfig prebuiltVoiceConfig) {

		/**
		 * Convenience factory method to create a VoiceConfig from a voice name.
		 * @param voiceName The name of the prebuilt voice (e.g., "Kore", "Puck")
		 * @return A VoiceConfig instance
		 */
		public static VoiceConfig of(String voiceName) {
			return new VoiceConfig(new PrebuiltVoiceConfig(voiceName));
		}

	}

	/**
	 * Prebuilt voice configuration specifying voice name.
	 *
	 * @param voiceName Name of the voice (e.g., "Kore", "Puck", "Zephyr")
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record PrebuiltVoiceConfig(@JsonProperty("voiceName") String voiceName) {
	}

	/**
	 * Multi-speaker voice configuration.
	 *
	 * @param speakerVoiceConfigs List of speaker configurations (up to 2 speakers)
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record MultiSpeakerVoiceConfig(
			@JsonProperty("speakerVoiceConfigs") List<SpeakerVoiceConfig> speakerVoiceConfigs) {
	}

	/**
	 * Configuration for a single speaker in multi-speaker mode.
	 *
	 * @param speaker The speaker name (must match the name used in the text prompt)
	 * @param voiceConfig The voice configuration for this speaker
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record SpeakerVoiceConfig(@JsonProperty("speaker") String speaker,
			@JsonProperty("voiceConfig") VoiceConfig voiceConfig) {

		/**
		 * Convenience factory method to create a SpeakerVoiceConfig from speaker and
		 * voice names.
		 * @param speaker The speaker name (e.g., "Joe", "Jane")
		 * @param voiceName The prebuilt voice name (e.g., "Kore", "Puck")
		 * @return A SpeakerVoiceConfig instance
		 */
		public static SpeakerVoiceConfig of(String speaker, String voiceName) {
			return new SpeakerVoiceConfig(speaker, VoiceConfig.of(voiceName));
		}

	}

	// Response POJOs

	/**
	 * Response containing generated content (audio).
	 *
	 * @param candidates List of candidate responses (typically one)
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record GenerateContentResponse(@JsonProperty("candidates") List<Candidate> candidates) {
	}

	/**
	 * A candidate response.
	 *
	 * @param content The content of the response
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Candidate(@JsonProperty("content") ContentResponse content) {
	}

	/**
	 * Response content containing parts.
	 *
	 * @param parts List of response parts (typically one audio part)
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ContentResponse(@JsonProperty("parts") List<PartResponse> parts) {
	}

	/**
	 * A part of the response content.
	 *
	 * @param inlineData The inline data (audio) in the response
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record PartResponse(@JsonProperty("inlineData") InlineData inlineData) {
	}

	/**
	 * Inline data in the response.
	 *
	 * @param mimeType The MIME type of the data (e.g., "audio/L16;codec=pcm;rate=24000")
	 * @param data The base64-encoded audio data
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record InlineData(@JsonProperty("mimeType") String mimeType, @JsonProperty("data") String data) {
	}

}
