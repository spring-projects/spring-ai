/*
 * Copyright 2024-2024 the original author or authors.
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

package org.springframework.ai.mistral.api;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Implementation of the MistralAI Embedding API:
 * <a href="https://docs.mistral.ai/api/#operation/createEmbedding">...</a> and Chat
 * Completion API:
 * <a href="https://docs.mistral.ai/api/#operation/createChatCompletion">...</a>
 *
 * @author Ricken Bazolo
 * @author Christian Tzolov
 * @since 0.8.1
 */
public class MistralAiApi {

	private static final String DEFAULT_BASE_URL = "https://api.mistral.ai";

	private static final Predicate<String> SSE_DONE_PREDICATE = "[DONE]"::equals;

	private final RestClient restClient;

	private WebClient webClient;

	private final ObjectMapper objectMapper;

	/**
	 * Create a new client api with DEFAULT_BASE_URL
	 * @param mistralAiApiKey Mistral api Key.
	 */
	public MistralAiApi(String mistralAiApiKey) {
		this(DEFAULT_BASE_URL, mistralAiApiKey);
	}

	/**
	 * Create a new client api.
	 * @param baseUrl api base URL.
	 * @param mistralAiApiKey Mistral api Key.
	 */
	public MistralAiApi(String baseUrl, String mistralAiApiKey) {
		this(baseUrl, mistralAiApiKey, RestClient.builder());
	}

	/**
	 * Create a new client api.
	 * @param baseUrl api base URL.
	 * @param mistralAiApiKey Mistral api Key.
	 * @param restClientBuilder RestClient builder.
	 */
	public MistralAiApi(String baseUrl, String mistralAiApiKey, RestClient.Builder restClientBuilder) {

		this.objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

		Consumer<HttpHeaders> jsonContentHeaders = headers -> {
			headers.setBearerAuth(mistralAiApiKey);
			headers.setContentType(MediaType.APPLICATION_JSON);
		};

		var responseErrorHandler = new ResponseErrorHandler() {

			@Override
			public boolean hasError(ClientHttpResponse response) throws IOException {
				return response.getStatusCode().isError();
			}

			@Override
			public void handleError(ClientHttpResponse response) throws IOException {
				if (response.getStatusCode().isError()) {
					if (response.getStatusCode().is4xxClientError()) {
						throw new MistralAiApiClientErrorException(String.format("%s - %s",
								response.getStatusCode().value(),
								MistralAiApi.this.objectMapper.readValue(response.getBody(), ResponseError.class)));
					}
					throw new MistralAiApiException(String.format("%s - %s", response.getStatusCode().value(),
							MistralAiApi.this.objectMapper.readValue(response.getBody(), ResponseError.class)));
				}
			}
		};

		this.restClient = restClientBuilder.baseUrl(baseUrl)
			.defaultHeaders(jsonContentHeaders)
			.defaultStatusHandler(responseErrorHandler)
			.build();
		this.webClient = WebClient.builder().baseUrl(baseUrl).defaultHeaders(jsonContentHeaders).build();
	}

	public static class MistralAiApiException extends RuntimeException {

		public MistralAiApiException(String message) {
			super(message);
		}

		public MistralAiApiException(String message, Throwable t) {
			super(message, t);
		}

	}

	/**
	 * Thrown on 4xx client errors, such as 401 - Incorrect API key provided, 401 - You
	 * must be a member of an organization to use the API, 429 - Rate limit reached for
	 * requests, 429 - You exceeded your current quota , please check your plan and
	 * billing details.
	 */
	public static class MistralAiApiClientErrorException extends RuntimeException {

		public MistralAiApiClientErrorException(String message) {
			super(message);
		}

	}

	/**
	 * API error response.
	 *
	 * @param error Error details.
	 */
	@JsonInclude(Include.NON_NULL)
	public record ResponseError(@JsonProperty("error") Error error) {

		/**
		 * Error details.
		 *
		 * @param message Error message.
		 * @param type Error type.
		 * @param param Error parameter.
		 * @param code Error code.
		 */
		@JsonInclude(Include.NON_NULL)
		public record Error(
		// @formatter:off
			@JsonProperty("message") String message,
			@JsonProperty("type") String type,
			@JsonProperty("param") String param,
			@JsonProperty("code") String code) {
			// @formatter:on
		}
	}

	/**
	 * Usage statistics.
	 *
	 * @param promptTokens Number of tokens in the prompt.
	 * @param totalTokens Total number of tokens used in the request (prompt +
	 * completion).
	 * @param completionTokens Number of tokens in the generated completion. Only
	 * applicable for completion requests.
	 */
	@JsonInclude(Include.NON_NULL)
	public record Usage(
	// @formatter:off
		@JsonProperty("prompt_tokens") Integer promptTokens,
		@JsonProperty("total_tokens") Integer totalTokens,
		@JsonProperty("completion_tokens") Integer completionTokens) {
		// @formatter:on
	}

	/**
	 * Represents an embedding vector returned by embedding endpoint.
	 *
	 * @param index The index of the embedding in the list of embeddings.
	 * @param embedding The embedding vector, which is a list of floats. The length of
	 * vector depends on the model.
	 * @param object The object type, which is always 'embedding'.
	 */
	@JsonInclude(Include.NON_NULL)
	public record Embedding(
	// @formatter:off
		@JsonProperty("index") Integer index,
		@JsonProperty("embedding") List<Double> embedding,
		@JsonProperty("object") String object) {
		// @formatter:on

		/**
		 * Create an embedding with the given index, embedding and object type set to
		 * 'embedding'.
		 * @param index The index of the embedding in the list of embeddings.
		 * @param embedding The embedding vector, which is a list of floats. The length of
		 * vector depends on the model.
		 */
		public Embedding(Integer index, List<Double> embedding) {
			this(index, embedding, "embedding");
		}
	}

	/**
	 * Creates an embedding vector representing the input text.
	 *
	 * @param input Input text to embed, encoded as a string or array of tokens
	 * @param model ID of the model to use.
	 * @param encodingFormat The format to return the embeddings in. Can be either float
	 * or base64.
	 */
	@JsonInclude(Include.NON_NULL)
	public record EmbeddingRequest<T>(
	// @formatter:off
		@JsonProperty("input") T input,
		@JsonProperty("model") String model,
		@JsonProperty("encoding_format") String encodingFormat) {
		// @formatter:on

		/**
		 * Create an embedding request with the given input, model and encoding format set
		 * to float.
		 * @param input Input text to embed.
		 * @param model ID of the model to use.
		 */
		public EmbeddingRequest(T input, String model) {
			this(input, model, "float");
		}

		/**
		 * Create an embedding request with the given input. Encoding format is set to
		 * float and user is null and the model is set to 'mistral-embed'.
		 * @param input Input text to embed.
		 */
		public EmbeddingRequest(T input) {
			this(input, EmbeddingModel.EMBED.getValue());
		}
	}

	/**
	 * List of multiple embedding responses.
	 *
	 * @param <T> Type of the entities in the data list.
	 * @param object Must have value "list".
	 * @param data List of entities.
	 * @param model ID of the model to use.
	 * @param usage Usage statistics for the completion request.
	 */
	@JsonInclude(Include.NON_NULL)
	public record EmbeddingList<T>(
	// @formatter:off
			@JsonProperty("object") String object,
			@JsonProperty("data") List<T> data,
			@JsonProperty("model") String model,
			@JsonProperty("usage") Usage usage) {
		// @formatter:on
	}

	/**
	 * Creates an embedding vector representing the input text or token array.
	 * @param embeddingRequest The embedding request.
	 * @return Returns list of {@link Embedding} wrapped in {@link EmbeddingList}.
	 * @param <T> Type of the entity in the data list. Can be a {@link String} or
	 * {@link List} of tokens (e.g. Integers). For embedding multiple inputs in a single
	 * request, You can pass a {@link List} of {@link String} or {@link List} of
	 * {@link List} of tokens. For example:
	 *
	 * <pre>{@code List.of("text1", "text2", "text3") or List.of(List.of(1, 2, 3), List.of(3, 4, 5))} </pre>
	 */
	public <T> ResponseEntity<EmbeddingList<Embedding>> embeddings(EmbeddingRequest<T> embeddingRequest) {

		Assert.notNull(embeddingRequest, "The request body can not be null.");

		// Input text to embed, encoded as a string or array of tokens. To embed multiple
		// inputs in a single
		// request, pass an array of strings or array of token arrays.
		Assert.notNull(embeddingRequest.input(), "The input can not be null.");
		Assert.isTrue(embeddingRequest.input() instanceof String || embeddingRequest.input() instanceof List,
				"The input must be either a String, or a List of Strings or List of List of integers.");

		// The input must not an empty string, and any array must be 1024 dimensions or
		// less.
		if (embeddingRequest.input() instanceof List list) {
			Assert.isTrue(!CollectionUtils.isEmpty(list), "The input list can not be empty.");
			Assert.isTrue(list.size() <= 1024, "The list must be 1024 dimensions or less");
			Assert.isTrue(
					list.get(0) instanceof String || list.get(0) instanceof Integer || list.get(0) instanceof List,
					"The input must be either a String, or a List of Strings or list of list of integers.");
		}

		return this.restClient.post()
			.uri("/v1/embeddings")
			.body(embeddingRequest)
			.retrieve()
			.toEntity(new ParameterizedTypeReference<>() {
			});
	}

	/**
	 * Creates a model request for chat conversation.
	 *
	 * @param model ID of the model to use.
	 * @param messages The prompt(s) to generate completions for, encoded as a list of
	 * dict with role and content. The first prompt role should be user or system.
	 * @param temperature What sampling temperature to use, between 0.0 and 1.0. Higher
	 * values like 0.8 will make the output more random, while lower values like 0.2 will
	 * make it more focused and deterministic. We generally recommend altering this or
	 * top_p but not both.
	 * @param topP Nucleus sampling, where the model considers the results of the tokens
	 * with top_p probability mass. So 0.1 means only the tokens comprising the top 10%
	 * probability mass are considered. We generally recommend altering this or
	 * temperature but not both.
	 * @param maxTokens The maximum number of tokens to generate in the completion. The
	 * token count of your prompt plus max_tokens cannot exceed the model's context
	 * length.
	 * @param stream Whether to stream back partial progress. If set, tokens will be sent
	 * as data-only server-sent events as they become available, with the stream
	 * terminated by a data: [DONE] message. Otherwise, the server will hold the request
	 * open until the timeout or until completion, with the response containing the full
	 * result as JSON.
	 * @param safePrompt Whether to inject a safety prompt before all conversations.
	 * @param randomSeed The seed to use for random sampling. If set, different calls will
	 * generate deterministic results.
	 */
	@JsonInclude(Include.NON_NULL)
	public record ChatCompletionRequest(
	// @formatter:off
			@JsonProperty("model") String model,
			@JsonProperty("messages") List<ChatCompletionMessage> messages,
			@JsonProperty("temperature") Float temperature,
			@JsonProperty("top_p") Float topP,
			@JsonProperty("max_tokens") Integer maxTokens,
			@JsonProperty("stream") Boolean stream,
			@JsonProperty("safe_prompt") Boolean safePrompt,
			@JsonProperty("random_seed") Integer randomSeed) {
		// @formatter:on

		/**
		 * Shortcut constructor for a chat completion request with the given messages and
		 * model.
		 * @param messages The prompt(s) to generate completions for, encoded as a list of
		 * dict with role and content. The first prompt role should be user or system.
		 * @param model ID of the model to use.
		 */
		public ChatCompletionRequest(List<ChatCompletionMessage> messages, String model) {
			this(model, messages, 0.7f, 1f, null, false, false, null);
		}

		/**
		 * Shortcut constructor for a chat completion request with the given messages,
		 * model and temperature.
		 * @param messages The prompt(s) to generate completions for, encoded as a list of
		 * dict with role and content. The first prompt role should be user or system.
		 * @param model ID of the model to use.
		 * @param temperature What sampling temperature to use, between 0.0 and 1.0.
		 * @param stream Whether to stream back partial progress. If set, tokens will be
		 * sent
		 */
		public ChatCompletionRequest(List<ChatCompletionMessage> messages, String model, Float temperature,
				boolean stream) {
			this(model, messages, temperature, 1f, null, stream, false, null);
		}

		/**
		 * Shortcut constructor for a chat completion request with the given messages,
		 * model and temperature.
		 * @param messages The prompt(s) to generate completions for, encoded as a list of
		 * dict with role and content. The first prompt role should be user or system.
		 * @param model ID of the model to use.
		 * @param temperature What sampling temperature to use, between 0.0 and 1.0.
		 *
		 */
		public ChatCompletionRequest(List<ChatCompletionMessage> messages, String model, Float temperature) {
			this(model, messages, temperature, 1f, null, false, false, null);
		}

		/**
		 * Shortcut constructor for a chat completion request with the given messages and
		 * stream.
		 */
		public ChatCompletionRequest(List<ChatCompletionMessage> messages, Boolean stream) {
			this(null, messages, 0.7f, 1f, null, stream, false, null);
		}
	}

	/**
	 * Message comprising the conversation.
	 *
	 * @param content The contents of the message.
	 * @param role The role of the messages author. Could be one of the {@link Role}
	 * types.
	 */
	@JsonInclude(Include.NON_NULL)
	public record ChatCompletionMessage(
	// @formatter:off
		@JsonProperty("content") String content,
		@JsonProperty("role") Role role) {
		// @formatter:on

		/**
		 * The role of the author of this message.
		 *
		 * NOTE: Mistral expects the system message to be before the user message or will
		 * fail with 400 error.
		 */
		public enum Role {

			// @formatter:off
			@JsonProperty("system") SYSTEM,
			@JsonProperty("user") USER,
			@JsonProperty("assistant") ASSISTANT
			// @formatter:on

		}
	}

	/**
	 * The reason the model stopped generating tokens.
	 */
	public enum ChatCompletionFinishReason {

		// @formatter:off
		/**
		 * The model hit a natural stop point or a provided stop sequence.
		 */
		@JsonProperty("stop") STOP,
		/**
		 * The maximum number of tokens specified in the request was reached.
		 */
		@JsonProperty("length") LENGTH,
		/**
		 * The content was omitted due to a flag from our content filters.
		 */
		@JsonProperty("model_length") MODEL_LENGTH
		// @formatter:on

	}

	/**
	 * Represents a chat completion response returned by model, based on the provided
	 * input.
	 *
	 * @param id A unique identifier for the chat completion.
	 * @param object The object type, which is always chat.completion.
	 * @param created The Unix timestamp (in seconds) of when the chat completion was
	 * created.
	 * @param model The model used for the chat completion.
	 * @param choices A list of chat completion choices.
	 * @param usage Usage statistics for the completion request.
	 */
	@JsonInclude(Include.NON_NULL)
	public record ChatCompletion(
	// @formatter:off
		@JsonProperty("id") String id,
		@JsonProperty("object") String object,
		@JsonProperty("created") Long created,
		@JsonProperty("model") String model,
		@JsonProperty("choices") List<Choice> choices,
		@JsonProperty("usage") Usage usage) {
		// @formatter:on

		/**
		 * Chat completion choice.
		 *
		 * @param index The index of the choice in the list of choices.
		 * @param message A chat completion message generated by the model.
		 * @param finishReason The reason the model stopped generating tokens.
		 */
		@JsonInclude(Include.NON_NULL)
		public record Choice(
		// @formatter:off
			@JsonProperty("index") Integer index,
			@JsonProperty("message") ChatCompletionMessage message,
			@JsonProperty("finish_reason") ChatCompletionFinishReason finishReason) {
			// @formatter:on
		}
	}

	/**
	 * Represents a streamed chunk of a chat completion response returned by model, based
	 * on the provided input.
	 *
	 * @param id A unique identifier for the chat completion. Each chunk has the same ID.
	 * @param object The object type, which is always 'chat.completion.chunk'.
	 * @param created The Unix timestamp (in seconds) of when the chat completion was
	 * created. Each chunk has the same timestamp.
	 * @param model The model used for the chat completion.
	 * @param choices A list of chat completion choices. Can be more than one if n is
	 * greater than 1.
	 */
	@JsonInclude(Include.NON_NULL)
	public record ChatCompletionChunk(
	// @formatter:off
		@JsonProperty("id") String id,
		@JsonProperty("object") String object,
		@JsonProperty("created") Long created,
		@JsonProperty("model") String model,
		@JsonProperty("choices") List<ChunkChoice> choices) {
		// @formatter:on

		/**
		 * Chat completion choice.
		 *
		 * @param index The index of the choice in the list of choices.
		 * @param delta A chat completion delta generated by streamed model responses.
		 * @param finishReason The reason the model stopped generating tokens.
		 */
		@JsonInclude(Include.NON_NULL)
		public record ChunkChoice(
		// @formatter:off
			@JsonProperty("index") Integer index,
			@JsonProperty("delta") ChatCompletionMessage delta,
			@JsonProperty("finish_reason") ChatCompletionFinishReason finishReason) {
			// @formatter:on
		}
	}

	/**
	 * List of well-known Mistral chat models.
	 * https://docs.mistral.ai/platform/endpoints/#mistral-ai-generative-models
	 */
	public enum ChatModel {

		// @formatter:off
		@JsonProperty("mistral-tiny") TINY("mistral-tiny"),
		@JsonProperty("mistral-small") SMALL("mistral-small"),
		@JsonProperty("mistral-medium") MEDIUM("mistral-medium"),
		@JsonProperty("mistral-large") LARGE("mistral-large"),
		@JsonProperty("mistral-xlarge") XLARGE("mistral-xlarge");
		// @formatter:on

		private final String value;

		ChatModel(String value) {
			this.value = value;
		}

		public String getValue() {
			return this.value;
		}

	}

	/**
	 * List of well-known Mistral embedding models.
	 * https://docs.mistral.ai/platform/endpoints/#mistral-ai-embedding-model
	 */
	public enum EmbeddingModel {

		// @formatter:off
		@JsonProperty("mistral-embed") EMBED("mistral-embed");
		// @formatter:on

		private final String value;

		EmbeddingModel(String value) {
			this.value = value;
		}

		public String getValue() {
			return this.value;
		}

	}

	/**
	 * Creates a model response for the given chat conversation.
	 * @param chatRequest The chat completion request.
	 * @return Entity response with {@link ChatCompletion} as a body and HTTP status code
	 * and headers.
	 */
	public ResponseEntity<ChatCompletion> chatCompletionEntity(ChatCompletionRequest chatRequest) {

		Assert.notNull(chatRequest, "The request body can not be null.");
		Assert.isTrue(!chatRequest.stream(), "Request must set the steam property to false.");

		return this.restClient.post()
			.uri("/v1/chat/completions")
			.body(chatRequest)
			.retrieve()
			.toEntity(ChatCompletion.class);
	}

	/**
	 * Creates a streaming chat response for the given chat conversation.
	 * @param chatRequest The chat completion request. Must have the stream property set
	 * to true.
	 * @return Returns a {@link Flux} stream from chat completion chunks.
	 */
	public Flux<ChatCompletionChunk> chatCompletionStream(ChatCompletionRequest chatRequest) {

		Assert.notNull(chatRequest, "The request body can not be null.");
		Assert.isTrue(chatRequest.stream(), "Request must set the steam property to true.");

		return this.webClient.post()
			.uri("/v1/chat/completions")
			.body(Mono.just(chatRequest), ChatCompletionRequest.class)
			.retrieve()
			.bodyToFlux(String.class)
			.takeUntil(SSE_DONE_PREDICATE)
			.filter(SSE_DONE_PREDICATE.negate())
			.map(content -> parseJson(content, ChatCompletionChunk.class));
	}

	public static Map<String, Object> parseJson(String jsonSchema) {
		try {
			return new ObjectMapper().readValue(jsonSchema, new TypeReference<Map<String, Object>>() {
			});
		}
		catch (Exception e) {
			throw new MistralAiApiException("Failed to parse schema: " + jsonSchema, e);
		}
	}

	private <T> T parseJson(String json, Class<T> type) {
		try {
			return this.objectMapper.readValue(json, type);
		}
		catch (Exception e) {
			throw new MistralAiApiException("Failed to parse schema: " + json, e);
		}
	}

}
