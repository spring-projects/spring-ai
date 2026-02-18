/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.modelslab.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Single class implementation of the ModelsLab API.
 * 
 * @author Patcher
 * @since 2.0.0
 */
public class ModelsLabApi {

	private static final Logger logger = LoggerFactory.getLogger(ModelsLabApi.class);

	/**
	 * ModelsLab API key for authentication
	 */
	private final String apiKey;

	/**
	 * Base URLs for different API categories
	 */
	private final String chatBaseUrl;
	private final String imageBaseUrl;
	private final String videoBaseUrl;
	private final String audioBaseUrl;

	/**
	 * RestClient for synchronous HTTP requests
	 */
	private final RestClient restClient;

	/**
	 * WebClient for reactive HTTP requests
	 */
	private final WebClient webClient;

	/**
	 * Creates a ModelsLabApi instance with default base URLs.
	 *
	 * @param apiKey The ModelsLab API key
	 */
	public ModelsLabApi(String apiKey) {
		this(apiKey, ModelsLabApiConstants.DEFAULT_CHAT_BASE_URL, 
			 ModelsLabApiConstants.DEFAULT_IMAGE_BASE_URL,
			 ModelsLabApiConstants.DEFAULT_VIDEO_BASE_URL,
			 ModelsLabApiConstants.DEFAULT_AUDIO_BASE_URL);
	}

	/**
	 * Creates a ModelsLabApi instance with custom base URLs.
	 *
	 * @param apiKey The ModelsLab API key
	 * @param chatBaseUrl Base URL for chat API
	 * @param imageBaseUrl Base URL for image API
	 * @param videoBaseUrl Base URL for video API
	 * @param audioBaseUrl Base URL for audio API
	 */
	public ModelsLabApi(String apiKey, String chatBaseUrl, String imageBaseUrl, String videoBaseUrl, String audioBaseUrl) {
		this(apiKey, chatBaseUrl, imageBaseUrl, videoBaseUrl, audioBaseUrl, RestClient.builder(), WebClient.builder());
	}

	/**
	 * Creates a ModelsLabApi instance with custom clients.
	 *
	 * @param apiKey The ModelsLab API key
	 * @param chatBaseUrl Base URL for chat API
	 * @param imageBaseUrl Base URL for image API
	 * @param videoBaseUrl Base URL for video API
	 * @param audioBaseUrl Base URL for audio API
	 * @param restClientBuilder RestClient builder for customization
	 * @param webClientBuilder WebClient builder for customization
	 */
	public ModelsLabApi(String apiKey, String chatBaseUrl, String imageBaseUrl, String videoBaseUrl, String audioBaseUrl,
						RestClient.Builder restClientBuilder, WebClient.Builder webClientBuilder) {
		Assert.hasText(apiKey, "ModelsLab API key must not be empty");
		Assert.hasText(chatBaseUrl, "Chat base URL must not be empty");
		Assert.hasText(imageBaseUrl, "Image base URL must not be empty");
		Assert.hasText(videoBaseUrl, "Video base URL must not be empty");
		Assert.hasText(audioBaseUrl, "Audio base URL must not be empty");

		this.apiKey = apiKey;
		this.chatBaseUrl = chatBaseUrl;
		this.imageBaseUrl = imageBaseUrl;
		this.videoBaseUrl = videoBaseUrl;
		this.audioBaseUrl = audioBaseUrl;

		Consumer<HttpHeaders> defaultHeaders = headers -> {
			headers.setContentType(MediaType.APPLICATION_JSON);
			headers.setBearerAuth(apiKey); // For OpenAI-compatible chat API
		};

		this.restClient = restClientBuilder
			.defaultHeaders(defaultHeaders)
			.build();

		this.webClient = webClientBuilder
			.defaultHeaders(defaultHeaders)
			.build();
	}

	// ====================================
	// Chat Completion API (OpenAI-compatible)
	// ====================================

	/**
	 * Executes a chat completion request.
	 *
	 * @param request The chat completion request
	 * @return The chat completion response
	 */
	public ResponseEntity<ChatCompletion> chatCompletionEntity(ChatCompletionRequest request) {
		return chatCompletionEntity(request, null);
	}

	/**
	 * Executes a chat completion request with additional headers.
	 *
	 * @param request The chat completion request
	 * @param additionalHeaders Additional HTTP headers
	 * @return The chat completion response
	 */
	public ResponseEntity<ChatCompletion> chatCompletionEntity(ChatCompletionRequest request, HttpHeaders additionalHeaders) {
		Assert.notNull(request, "ChatCompletionRequest must not be null");

		return this.restClient.post()
			.uri(chatBaseUrl + ModelsLabApiConstants.CHAT_COMPLETIONS_PATH)
			.headers(headers -> {
				if (additionalHeaders != null) {
					headers.addAll(additionalHeaders);
				}
			})
			.body(request)
			.retrieve()
			.toEntity(ChatCompletion.class);
	}

	/**
	 * Executes a streaming chat completion request.
	 *
	 * @param request The chat completion request
	 * @return A flux of chat completion chunks
	 */
	public Flux<ChatCompletionChunk> chatCompletionStream(ChatCompletionRequest request) {
		return chatCompletionStream(request, null);
	}

	/**
	 * Executes a streaming chat completion request with additional headers.
	 *
	 * @param request The chat completion request
	 * @param additionalHeaders Additional HTTP headers
	 * @return A flux of chat completion chunks
	 */
	public Flux<ChatCompletionChunk> chatCompletionStream(ChatCompletionRequest request, HttpHeaders additionalHeaders) {
		Assert.notNull(request, "ChatCompletionRequest must not be null");

		AtomicBoolean isInsideTool = new AtomicBoolean(false);

		return this.webClient.post()
			.uri(chatBaseUrl + ModelsLabApiConstants.CHAT_COMPLETIONS_PATH)
			.headers(headers -> {
				if (additionalHeaders != null) {
					headers.addAll(additionalHeaders);
				}
			})
			.body(Mono.just(request), ChatCompletionRequest.class)
			.retrieve()
			.bodyToFlux(String.class)
			.takeUntil(line -> "[DONE]".equals(line.trim()))
			.filter(line -> StringUtils.hasText(line) && !line.trim().equals("data: [DONE]"))
			.map(line -> line.replaceFirst("^data: ", ""))
			.filter(StringUtils::hasText)
			.map(line -> ModelMapperUtils.fromJson(line, ChatCompletionChunk.class))
			.filter(chunk -> chunk != null);
	}

	// ====================================
	// Image Generation API
	// ====================================

	/**
	 * Executes a text-to-image generation request.
	 *
	 * @param request The text-to-image request
	 * @return The image generation response
	 */
	public ResponseEntity<ImageGenerationResponse> textToImageEntity(TextToImageRequest request) {
		Assert.notNull(request, "TextToImageRequest must not be null");

		return this.restClient.post()
			.uri(imageBaseUrl + ModelsLabApiConstants.TEXT_TO_IMAGE_PATH)
			.body(request.withKey(apiKey)) // Add API key to request body
			.retrieve()
			.toEntity(ImageGenerationResponse.class);
	}

	/**
	 * Executes an image-to-image transformation request.
	 *
	 * @param request The image-to-image request
	 * @return The image generation response
	 */
	public ResponseEntity<ImageGenerationResponse> imageToImageEntity(ImageToImageRequest request) {
		Assert.notNull(request, "ImageToImageRequest must not be null");

		return this.restClient.post()
			.uri(imageBaseUrl + ModelsLabApiConstants.IMAGE_TO_IMAGE_PATH)
			.body(request.withKey(apiKey)) // Add API key to request body
			.retrieve()
			.toEntity(ImageGenerationResponse.class);
	}

	/**
	 * Fetches the result of an async image generation operation.
	 *
	 * @param requestId The async request ID
	 * @return The image generation response
	 */
	public ResponseEntity<ImageGenerationResponse> fetchImageResult(String requestId) {
		Assert.hasText(requestId, "Request ID must not be empty");

		var fetchRequest = Map.of("key", apiKey);
		
		return this.restClient.post()
			.uri(imageBaseUrl + "/images/fetch/{id}", requestId)
			.body(fetchRequest)
			.retrieve()
			.toEntity(ImageGenerationResponse.class);
	}

	// ====================================
	// Audio API
	// ====================================

	/**
	 * Executes a text-to-speech request.
	 *
	 * @param request The text-to-speech request
	 * @return The audio generation response
	 */
	public ResponseEntity<AudioGenerationResponse> textToSpeechEntity(TextToSpeechRequest request) {
		Assert.notNull(request, "TextToSpeechRequest must not be null");

		return this.restClient.post()
			.uri(audioBaseUrl + ModelsLabApiConstants.TEXT_TO_SPEECH_PATH)
			.body(request.withKey(apiKey)) // Add API key to request body
			.retrieve()
			.toEntity(AudioGenerationResponse.class);
	}

	/**
	 * Executes a speech-to-text transcription request.
	 *
	 * @param request The speech-to-text request
	 * @return The transcription response
	 */
	public ResponseEntity<TranscriptionResponse> speechToTextEntity(SpeechToTextRequest request) {
		Assert.notNull(request, "SpeechToTextRequest must not be null");

		return this.restClient.post()
			.uri(audioBaseUrl + ModelsLabApiConstants.SPEECH_TO_TEXT_PATH)
			.body(request.withKey(apiKey)) // Add API key to request body
			.retrieve()
			.toEntity(TranscriptionResponse.class);
	}

	// ====================================
	// Data Transfer Objects
	// ====================================

	/**
	 * Chat completion request (OpenAI-compatible)
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ChatCompletionRequest(
		@JsonProperty("model") String model,
		@JsonProperty("messages") List<ChatMessage> messages,
		@JsonProperty("temperature") Float temperature,
		@JsonProperty("max_tokens") Integer maxTokens,
		@JsonProperty("stream") Boolean stream,
		@JsonProperty("presence_penalty") Float presencePenalty,
		@JsonProperty("frequency_penalty") Float frequencyPenalty,
		@JsonProperty("stop") List<String> stop
	) {
		public ChatCompletionRequest withStream(boolean stream) {
			return new ChatCompletionRequest(model, messages, temperature, maxTokens, stream, presencePenalty, frequencyPenalty, stop);
		}
	}

	/**
	 * Chat message
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ChatMessage(
		@JsonProperty("role") String role,
		@JsonProperty("content") String content
	) {
	}

	/**
	 * Chat completion response
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ChatCompletion(
		@JsonProperty("id") String id,
		@JsonProperty("object") String object,
		@JsonProperty("created") Long created,
		@JsonProperty("model") String model,
		@JsonProperty("choices") List<Choice> choices,
		@JsonProperty("usage") Usage usage
	) {
	}

	/**
	 * Chat completion chunk for streaming
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ChatCompletionChunk(
		@JsonProperty("id") String id,
		@JsonProperty("object") String object,
		@JsonProperty("created") Long created,
		@JsonProperty("model") String model,
		@JsonProperty("choices") List<ChunkChoice> choices
	) {
	}

	/**
	 * Chat completion choice
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Choice(
		@JsonProperty("index") Integer index,
		@JsonProperty("message") ChatMessage message,
		@JsonProperty("finish_reason") String finishReason
	) {
	}

	/**
	 * Chat completion chunk choice
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ChunkChoice(
		@JsonProperty("index") Integer index,
		@JsonProperty("delta") ChatMessage delta,
		@JsonProperty("finish_reason") String finishReason
	) {
	}

	/**
	 * Token usage information
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Usage(
		@JsonProperty("prompt_tokens") Integer promptTokens,
		@JsonProperty("completion_tokens") Integer completionTokens,
		@JsonProperty("total_tokens") Integer totalTokens
	) {
	}

	/**
	 * Text-to-image request
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record TextToImageRequest(
		@JsonProperty("key") String key,
		@JsonProperty("model_id") String modelId,
		@JsonProperty("prompt") String prompt,
		@JsonProperty("negative_prompt") String negativePrompt,
		@JsonProperty("width") Integer width,
		@JsonProperty("height") Integer height,
		@JsonProperty("samples") Integer samples,
		@JsonProperty("num_inference_steps") Integer numInferenceSteps,
		@JsonProperty("guidance_scale") Double guidanceScale,
		@JsonProperty("seed") Long seed,
		@JsonProperty("safety_checker") String safetyChecker,
		@JsonProperty("webhook") String webhook,
		@JsonProperty("track_id") String trackId
	) {
		public TextToImageRequest withKey(String apiKey) {
			return new TextToImageRequest(apiKey, modelId, prompt, negativePrompt, width, height, samples, numInferenceSteps, guidanceScale, seed, safetyChecker, webhook, trackId);
		}
	}

	/**
	 * Image-to-image request
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ImageToImageRequest(
		@JsonProperty("key") String key,
		@JsonProperty("model_id") String modelId,
		@JsonProperty("prompt") String prompt,
		@JsonProperty("negative_prompt") String negativePrompt,
		@JsonProperty("init_image") String initImage,
		@JsonProperty("width") Integer width,
		@JsonProperty("height") Integer height,
		@JsonProperty("samples") Integer samples,
		@JsonProperty("num_inference_steps") Integer numInferenceSteps,
		@JsonProperty("guidance_scale") Double guidanceScale,
		@JsonProperty("strength") Double strength,
		@JsonProperty("seed") Long seed,
		@JsonProperty("safety_checker") String safetyChecker,
		@JsonProperty("webhook") String webhook,
		@JsonProperty("track_id") String trackId
	) {
		public ImageToImageRequest withKey(String apiKey) {
			return new ImageToImageRequest(apiKey, modelId, prompt, negativePrompt, initImage, width, height, samples, numInferenceSteps, guidanceScale, strength, seed, safetyChecker, webhook, trackId);
		}
	}

	/**
	 * Image generation response
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ImageGenerationResponse(
		@JsonProperty("status") String status,
		@JsonProperty("generationTime") Double generationTime,
		@JsonProperty("id") Long id,
		@JsonProperty("output") List<String> output,
		@JsonProperty("meta") Map<String, Object> meta,
		@JsonProperty("message") String message
	) {
	}

	/**
	 * Text-to-speech request
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record TextToSpeechRequest(
		@JsonProperty("key") String key,
		@JsonProperty("prompt") String prompt,
		@JsonProperty("voice_id") String voiceId,
		@JsonProperty("language") String language,
		@JsonProperty("speed") Double speed,
		@JsonProperty("emotion") String emotion,
		@JsonProperty("webhook") String webhook,
		@JsonProperty("track_id") String trackId
	) {
		public TextToSpeechRequest withKey(String apiKey) {
			return new TextToSpeechRequest(apiKey, prompt, voiceId, language, speed, emotion, webhook, trackId);
		}
	}

	/**
	 * Speech-to-text request
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record SpeechToTextRequest(
		@JsonProperty("key") String key,
		@JsonProperty("audio") String audio,
		@JsonProperty("language") String language,
		@JsonProperty("webhook") String webhook,
		@JsonProperty("track_id") String trackId
	) {
		public SpeechToTextRequest withKey(String apiKey) {
			return new SpeechToTextRequest(apiKey, audio, language, webhook, trackId);
		}
	}

	/**
	 * Audio generation response
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record AudioGenerationResponse(
		@JsonProperty("status") String status,
		@JsonProperty("generationTime") Double generationTime,
		@JsonProperty("id") Long id,
		@JsonProperty("output") List<String> output,
		@JsonProperty("meta") Map<String, Object> meta,
		@JsonProperty("message") String message
	) {
	}

	/**
	 * Transcription response
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record TranscriptionResponse(
		@JsonProperty("status") String status,
		@JsonProperty("generationTime") Double generationTime,
		@JsonProperty("id") Long id,
		@JsonProperty("output") String output,
		@JsonProperty("meta") Map<String, Object> meta,
		@JsonProperty("message") String message
	) {
	}

	// ====================================
	// Utility Classes
	// ====================================

	/**
	 * JSON mapper utility
	 */
	private static final class ModelMapperUtils {
		private static final com.fasterxml.jackson.databind.ObjectMapper objectMapper = 
			new com.fasterxml.jackson.databind.ObjectMapper();

		public static <T> T fromJson(String json, Class<T> clazz) {
			try {
				return objectMapper.readValue(json, clazz);
			} catch (Exception e) {
				logger.warn("Failed to parse JSON response: {}", json, e);
				return null;
			}
		}
	}

}