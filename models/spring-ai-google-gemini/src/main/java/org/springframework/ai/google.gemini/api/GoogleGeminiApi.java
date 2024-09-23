/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.google.gemini.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.gemini.GoogleGeminiChatOptions;
import org.springframework.ai.google.gemini.ResponseSchema;
import org.springframework.ai.model.ModelDescription;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static org.springframework.ai.google.gemini.api.GoogleGeminiApiConstants.DEFAULT_BASE_URL;

/**
 * Single class implementation of the Google Gemini Chat Completion API:
 * <a href="https://ai.google.dev/gemini-api/docs/text-generation?lang=rest">Docs</a>
 */
public class GoogleGeminiApi {

	public static final String DEFAULT_CHAT_MODEL = ChatModel.GEMINI_1_5_FLASH.value;

	private static final Predicate<String> SSE_DONE_PREDICATE = "[DONE]"::equals;

	private final RestClient restClient;

	private final WebClient webClient;

	private final String chatModel;

	private final String apiKey;

	/**
	 * Create a new chat completion api with default base URL
	 * @param apiKey Google Gemini apiKey.
	 */
	public GoogleGeminiApi(String apiKey) {
		this(DEFAULT_BASE_URL, DEFAULT_CHAT_MODEL, apiKey);
	}

	/**
	 * Create a new chat completion api with default base URL
	 * @param apiKey Google Gemini apiKey.
	 */
	public GoogleGeminiApi(String chatModel, String apiKey) {
		this(DEFAULT_BASE_URL, chatModel, apiKey);
	}

	/**
	 * Create a new chat completion api.
	 * @param baseUrl api base URL.
	 * @param apiKey Google Gemini apiKey.
	 */
	public GoogleGeminiApi(String baseUrl, String chatModel, String apiKey) {
		this(baseUrl, chatModel, apiKey, RestClient.builder(), WebClient.builder());
	}

	/**
	 * Create a new chat completion api.
	 * @param baseUrl api base URL.
	 * @param apiKey Google Gemini apiKey.
	 * @param restClientBuilder RestClient builder.
	 */
	public GoogleGeminiApi(String baseUrl, String chatModel, String apiKey, RestClient.Builder restClientBuilder,
			WebClient.Builder webClientBuilder) {
		this(baseUrl, chatModel, apiKey, restClientBuilder, webClientBuilder,
				RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER);
	}

	/**
	 * Create a new chat completion api.
	 * @param baseUrl api base URL.
	 * @param apiKey Google Gemini apiKey.
	 * @param restClientBuilder RestClient builder.
	 * @param responseErrorHandler Response error handler.
	 */
	public GoogleGeminiApi(String baseUrl, String chatModel, String apiKey, RestClient.Builder restClientBuilder,
			WebClient.Builder webClientBuilder, ResponseErrorHandler responseErrorHandler) {

		this.chatModel = chatModel;

		this.apiKey = apiKey;

		Consumer<HttpHeaders> jsonContentHeaders = headers -> {
			headers.setContentType(MediaType.APPLICATION_JSON);
		};

		this.restClient = restClientBuilder.baseUrl(baseUrl)
			.defaultHeaders(jsonContentHeaders)
			.defaultStatusHandler(responseErrorHandler)
			.build();

		this.webClient = webClientBuilder.baseUrl(baseUrl).defaultHeaders(jsonContentHeaders).build();
	}

	/**
	 * Google Gemini Chat Completion
	 * <a href="https://platform.google.gemini.com/api-docs/api/list-models">Models</a>
	 */
	public enum ChatModel implements ModelDescription {

		GEMINI_1_5_FLASH("gemini-1.5-flash"), GEMINI_1_5_PRO("gemini-1.5-pro"), GEMINI_1_0_PRO("gemini-1.0-pro");

		public final String value;

		ChatModel(String value) {
			this.value = value;
		}

		public String getValue() {
			return value;
		}

		@Override
		public String getName() {
			return value;
		}

	}

	@JsonInclude(Include.NON_NULL)
	public record Part(@JsonProperty("text") String text, @JsonProperty("thought") Boolean thought) {
		Part(String text) {
			this(text, false);
		}
	}

	@JsonInclude(Include.NON_NULL)
	public record ChatCompletionMessage(@JsonProperty("role") Role role, @JsonProperty("parts") List<Part> parts) {

		public ChatCompletionMessage(Message instruction) {
			this(Role.of(instruction.getMessageType()), List.of(new Part(instruction.getText())));
		}

		public ChatCompletionMessage(Role role, String content) {
			this(role, List.of(new Part(content)));
		}

		public ChatCompletionMessage(String content) {
			this(null, content);
		}

		public static ChatCompletionMessage getSystemInstruction(Prompt prompt) {
			return prompt.getInstructions()
				.stream()
				.filter(instruction -> instruction.getMessageType() == MessageType.SYSTEM)
				.map(instruction -> new ChatCompletionMessage(instruction.getText()))
				.findFirst()
				.orElse(null);
		}

		/**
		 * The role of the author of this message.
		 */
		public enum Role {

			/**
			 * User message.
			 */
			@JsonProperty("user")
			USER,
			/**
			 * Assistant message.
			 */
			@JsonProperty("model")
			ASSISTANT;

			public static Role of(MessageType messageType) {
				if (messageType == MessageType.USER) {
					return USER;
				}
				else if (messageType == MessageType.ASSISTANT) {
					return ASSISTANT;
				}
				else {
					throw new IllegalArgumentException("Only USER and ASSISTANT roles are allowed.");
				}
			}

		}
	}

	@JsonInclude(Include.NON_NULL)
	public record ThinkingConfig(@JsonProperty("thinkingBudget") Integer thinkingBudget,
			@JsonProperty("includeThoughts") Boolean includeThoughts) {
	}

	/**
	 * <a href=
	 * "https://ai.google.dev/api/generate-content#v1beta.GenerationConfig">Docs</a>
	 */
	@JsonInclude(Include.NON_NULL)
	public record GenerationConfig(@JsonProperty("temperature") Double temperature,
			@JsonProperty("maxOutputTokens") Integer maxOutputTokens,
			@JsonProperty("thinkingConfig") ThinkingConfig thinkingConfig,
			@JsonProperty("responseSchema") ResponseSchema responseSchema,
			@JsonProperty("responseMimeType") String responseMimeType) {
		private GenerationConfig(GoogleGeminiChatOptions options) {
			this(options.getTemperature(), options.getMaxTokens(),
					options.getThinkingBudget() == null ? null : new ThinkingConfig(options.getThinkingBudget(), false),
					options.getResponseSchema(), options.getResponseSchema() == null ? null : "application/json");
		}

		public static GenerationConfig of(GoogleGeminiChatOptions options) {
			return new GenerationConfig(options);
		}
	}

	/**
	 * Creates a model response for the given chat conversation.
	 */
	@JsonInclude(Include.NON_NULL)
	public record ChatCompletionRequest(@JsonProperty("contents") List<ChatCompletionMessage> contents,
			@JsonProperty("systemInstruction") ChatCompletionMessage systemInstruction,
			@JsonProperty("generationConfig") GenerationConfig generationConfig) {
		public ChatCompletionRequest(Prompt prompt, GoogleGeminiChatOptions options) {
			this(prompt.getInstructions()
				.stream()
				.filter(instruction -> instruction.getMessageType() != MessageType.SYSTEM)
				.map(ChatCompletionMessage::new)
				.toList(), ChatCompletionMessage.getSystemInstruction(prompt), GenerationConfig.of(options));
			Assert.isTrue(prompt.getInstructions()
				.stream()
				.filter(instruction -> instruction.getMessageType() == MessageType.SYSTEM)
				.count() <= 1, "Only one system message is allowed in the prompt.");
		}
	}

	@JsonInclude(Include.NON_NULL)
	public record Candidate(@JsonProperty("content") ChatCompletionMessage content,
			@JsonProperty("finishReason") String finishReason,
			@JsonProperty("safetyRatings") List<SafetyRating> safetyRatings,
			@JsonProperty("tokenCount") Integer tokenCount) {
		public Candidate(ChatCompletionMessage content) {
			this(content, null, null, null);
		}
	}

	@JsonInclude(Include.NON_NULL)
	public record SafetyRating(@JsonProperty("category") String category,
			@JsonProperty("probability") String probability, @JsonProperty("blocked") Boolean blocked) {
	}

	@JsonInclude(Include.NON_NULL)
	public record PromptFeedback(@JsonProperty("blockReason") String blockReason,
			@JsonProperty("safetyRatings") List<SafetyRating> safetyRatings) {
	}

	/**
	 * Represents a chat completion response returned by model, based on the provided
	 * input.
	 *
	 * @param choices A list of chat completion choices. Can be more than one if n is
	 * greater than 1.
	 * @param usage Usage statistics for the completion request.
	 */
	@JsonInclude(Include.NON_NULL)
	public record ChatCompletion(@JsonProperty("candidates") List<Candidate> choices,
			@JsonProperty("promptFeedback") PromptFeedback promptFeedback, @JsonProperty("usageMetadata") Usage usage) {
	}

	/**
	 * Usage statistics for the completion request.
	 */
	@JsonInclude(Include.NON_NULL)
	public record Usage(@JsonProperty("promptTokenCount") Integer promptTokenCount,
			@JsonProperty("cachedContentTokenCount") Integer cachedContentTokenCount,
			@JsonProperty("candidatesTokenCount") Integer candidatesTokenCount,
			@JsonProperty("toolUsePromptTokenCount") Integer toolUsePromptTokenCount,
			@JsonProperty("thoughtsTokenCount") Integer thoughtsTokenCount,
			@JsonProperty("totalTokenCount") Integer totalTokenCount) {

	}

	private String getCompletionUrl(boolean stream) {
		return "/models/" + chatModel + (stream ? ":streamGenerateContent?alt=sse&" : ":generateContent?") + "key="
				+ apiKey;
	}

	/**
	 * Creates a model response for the given chat conversation. <a href=
	 * "https://ai.google.dev/api/generate-content#v1beta.models.generateContent">Docs</a>
	 * @param chatRequest The chat completion request.
	 * @return Entity response with {@link ChatCompletion} as a body and HTTP status code
	 * and headers.
	 */
	public ResponseEntity<ChatCompletion> chatCompletionEntity(ChatCompletionRequest chatRequest) {

		Assert.notNull(chatRequest, "The request body can not be null.");

		return this.restClient.post()
			.uri(getCompletionUrl(false))
			.body(chatRequest)
			.retrieve()
			.toEntity(ChatCompletion.class);
	}

	/**
	 * Creates a streaming chat response for the given chat conversation. <a href=
	 * "https://ai.google.dev/api/generate-content#method:-models.streamgeneratecontent">Docs</a>
	 * @param chatRequest The chat completion request. Must have the stream property set
	 * to true.
	 * @return Returns a {@link Flux} stream from chat completion chunks.
	 */
	public Flux<ChatCompletion> chatCompletionStream(ChatCompletionRequest chatRequest) {
		Assert.notNull(chatRequest, "The request body can not be null.");

		return this.webClient.post()
			.uri(getCompletionUrl(true))
			.body(Mono.just(chatRequest), ChatCompletionRequest.class)
			.retrieve()
			.bodyToFlux(String.class)
			// cancels the flux stream after the "[DONE]" is received.
			.takeUntil(SSE_DONE_PREDICATE)
			// filters out the "[DONE]" message.
			.filter(SSE_DONE_PREDICATE.negate())
			.map(content -> ModelOptionsUtils.jsonToObject(content, ChatCompletion.class));
	}

}
