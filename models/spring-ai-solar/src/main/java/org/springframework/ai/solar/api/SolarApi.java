package org.springframework.ai.solar.api;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class SolarApi {

	public static final String DEFAULT_CHAT_MODEL = ChatModel.SOLAR_PRO.getValue();

	public static final String DEFAULT_EMBEDDING_MODEL = EmbeddingModel.EMBEDDING_QUERY.getValue();

	private static final Predicate<String> SSE_DONE_PREDICATE = "[DONE]"::equals;

	private final RestClient restClient;

	private final WebClient webClient;

	/**
	 * Create a new chat completion api with default base URL.
	 * @param apiKey Solar api key.
	 */
	public SolarApi(String apiKey) {
		this(SolarConstants.DEFAULT_BASE_URL, apiKey);
	}

	/**
	 * Create a new chat completion api.
	 * @param baseUrl api base URL.
	 * @param apiKey Solar api key.
	 */
	public SolarApi(String baseUrl, String apiKey) {
		this(baseUrl, apiKey, RestClient.builder());
	}

	/**
	 * Create a new chat completion api.
	 * @param baseUrl api base URL.
	 * @param apiKey Solar api key.
	 * @param restClientBuilder RestClient builder.
	 */
	public SolarApi(String baseUrl, String apiKey, RestClient.Builder restClientBuilder) {
		this(baseUrl, apiKey, restClientBuilder, RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER);
	}

	/**
	 * Create a new chat completion api.
	 * @param baseUrl api base URL.
	 * @param apiKey Solar api key.
	 * @param restClientBuilder RestClient builder.
	 * @param responseErrorHandler Response error handler.
	 */
	public SolarApi(String baseUrl, String apiKey, RestClient.Builder restClientBuilder,
			ResponseErrorHandler responseErrorHandler) {
		this(baseUrl, apiKey, restClientBuilder, WebClient.builder(), responseErrorHandler);
	}

	/**
	 * Create a new chat completion api.
	 * @param baseUrl api base URL.
	 * @param apiKey Solar api key.
	 * @param restClientBuilder RestClient builder.
	 * @param webClientBuilder WebClient builder.
	 * @param responseErrorHandler Response error handler.
	 */
	public SolarApi(String baseUrl, String apiKey, RestClient.Builder restClientBuilder,
			WebClient.Builder webClientBuilder, ResponseErrorHandler responseErrorHandler) {
		Consumer<HttpHeaders> finalHeaders = h -> {
			h.setBearerAuth(apiKey);
			h.setContentType(MediaType.APPLICATION_JSON);
		};
		this.restClient = restClientBuilder.baseUrl(baseUrl)
				.defaultHeaders(finalHeaders)
				.defaultStatusHandler(responseErrorHandler)
				.build();
		this.webClient = webClientBuilder.baseUrl(baseUrl).defaultHeaders(finalHeaders).build();
	}

	/**
	 * Creates a model response for the given chat conversation.
	 * @param chatRequest The chat completion request.
	 * @return Entity response with {@link ChatCompletion} as a body and HTTP status code
	 * and headers.
	 */
	public ResponseEntity<ChatCompletion> chatCompletionEntity(ChatCompletionRequest chatRequest) {
		Assert.notNull(chatRequest, "The request body can not be null.");
		Assert.isTrue(!chatRequest.stream(), "Request must set the stream property to false.");

		return this.restClient.post()
				.uri("/v1/solar/chat/completions")
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
		Assert.isTrue(chatRequest.stream(), "Request must set the stream property to true.");

		return this.webClient.post()
				.uri("/v1/solar/chat/completions", chatRequest.model)
				.body(Mono.just(chatRequest), ChatCompletionRequest.class)
				.retrieve()
				.bodyToFlux(String.class)
				.takeUntil(SSE_DONE_PREDICATE)
				.filter(SSE_DONE_PREDICATE.negate())
				.map(content -> ModelOptionsUtils.jsonToObject(content, ChatCompletionChunk.class));
	}

	/**
	 * Solar Chat Completion Models:
	 * <a href="https://console.upstage.ai/docs/capabilities/chat">Solar Model</a>.
	 */
	public enum ChatModel {

		SOLAR_PRO("solar-pro"), SOLAR_MINI("solar-mini"), SOLAR_MINI_JA("solar-mini-ja");

		public final String value;

		ChatModel(String value) {
			this.value = value;
		}

		public String getValue() {
			return this.value;
		}

	}

	/**
	 * Solar Embeddings Models:
	 * <a href="https://console.upstage.ai/docs/capabilities/embeddings">Embeddings</a>.
	 */
	public enum EmbeddingModel {

		EMBEDDING_QUERY("embedding-query"), EMBEDDING_PASSAGE("embedding-passage");

		public final String value;

		EmbeddingModel(String value) {
			this.value = value;
		}

		public String getValue() {
			return this.value;
		}

	}

	/**
	 * Creates a model response for the given chat conversation.
	 *
	 * @param messages A list of messages comprising the conversation so far.
	 * @param model The model name to generate the completion.
	 * Value in: "solar-pro" | "solar-mini" | "solar-mini-ja"
	 * @param maxTokens An optional parameter that limits the maximum number of tokens to generate.
	 * If max_tokens is set, sum of input tokens and max_tokens should be
	 * lower than or equal to context length of model. Default value is inf.
	 * @param stream An optional parameter that specifies whether a response should be sent as a stream. If set true,
	 * partial message deltas will be sent. Tokens will be sent as data-only server-sent events. Default value is false.
	 * @param temperature An optional parameter to set the sampling temperature.
	 * The value should lie between 0 and 2. Higher values like 0.8 result in a more random output,
	 * whereas lower values such as 0.2 enhance focus and determinism in the output. Default value is 0.7.
	 * not both.
	 * @param topP An optional parameter to trigger nucleus sampling.
	 * The tokens with top_p probability mass will be considered, which means, setting this value to 0.1 will consider
	 * tokens comprising the top 10% probability.
	 * @param responseFormat An object specifying the format that the model must generate.
	 * To generate JSON object without providing schema (JSON Mode), set response_format: {\"type\": \"json_object\"}.
	 * To generate JSON object with your own schema (Structured Outputs),
	 * set response_format: {“type”: “json_schema”, “json_schema”: { … your json schema … }}.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ChatCompletionRequest(@JsonProperty("messages") List<ChatCompletionMessage> messages,
										@JsonProperty("model") String model,
										@JsonProperty("max_tokens") Integer maxTokens,
										@JsonProperty("stream") Boolean stream,
										@JsonProperty("temperature") Double temperature,
										@JsonProperty("top_p") Double topP,
										@JsonProperty("response_format") ResponseFormat responseFormat
	) {
		/**
		 * Shortcut constructor for a chat completion request with the given messages and
		 * model.
		 * @param messages A list of messages comprising the conversation so far.
		 * @param model ID of the model to use.
		 * @param temperature An optional parameter to set the sampling temperature.
		 * The value should lie between 0 and 2. Higher values like 0.8 result in a more random output,
		 * whereas lower values such as 0.2 enhance focus and determinism in the output. Default value is 0.7.
		 */
		public ChatCompletionRequest(List<ChatCompletionMessage> messages, String model, Double temperature) {
			this(messages, model, null, null, temperature, null, null);
		}

		/**
		 * Shortcut constructor for a chat completion request with the given messages,
		 * model and control for streaming.
		 * @param messages A list of messages comprising the conversation so far.
		 * @param model ID of the model to use.
		 * @param stream If set, partial message deltas will be sent.Tokens will be sent
		 * as data-only server-sent events as they become available, with the stream
		 * terminated by a data: [DONE] message.
		 */
		public ChatCompletionRequest(List<ChatCompletionMessage> messages, String model, boolean stream) {
			this(messages, model, null, stream, null, null, null);
		}

		/**
		 * Shortcut constructor for a chat completion request with the given messages,
		 * model and control for streaming.
		 * @param messages A list of messages comprising the conversation so far.
		 * @param model ID of the model to use.
		 * @param temperature An optional parameter to set the sampling temperature.
		 * The value should lie between 0 and 2. Higher values like 0.8 result in a more random output,
		 * whereas lower values such as 0.2 enhance focus and determinism in the output. Default value is 0.7.
		 * @param stream If set, partial message deltas will be sent.Tokens will be sent
		 * as data-only server-sent events as they become available, with the stream
		 * terminated by a data: [DONE] message.
		 */
		public ChatCompletionRequest(List<ChatCompletionMessage> messages, String model, Double temperature, boolean stream) {
			this(messages, model, null, stream, temperature, null, null);
		}

		/**
		 * An object specifying the format that the model must output.
		 *
		 * @param type Must be one of 'json_object' or 'json_schema'.
		 * @param jsonSchema The JSON schema to be used for structured output.
		 */
		@JsonInclude(JsonInclude.Include.NON_NULL)
		public record ResponseFormat(@JsonProperty("type") String type, @JsonProperty("json_schema") String jsonSchema) {
		}
	}

	/**
	 * Message comprising the conversation.
	 *
	 * @param rawContent The contents of the message. Can be a {@link String}. The
	 * response message content is always a {@link String}.
	 * @param role The role of the messages author. Could be one of the {@link Role}
	 * types.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ChatCompletionMessage(@JsonProperty("content") Object rawContent, @JsonProperty("role") Role role) {

		/**
		 * Get message content as String.
		 */
		public String content() {
			if (this.rawContent == null) {
				return null;
			}
			if (this.rawContent instanceof String text) {
				return text;
			}
			throw new IllegalStateException("The content is not a string!");
		}

		/**
		 * The role of the author of this message.
		 */
		public enum Role {

			/**
			 * System message.
			 */
			@JsonProperty("system")
			SYSTEM,
			/**
			 * User message.
			 */
			@JsonProperty("user")
			USER,
			/**
			 * Assistant message.
			 */
			@JsonProperty("assistant")
			ASSISTANT,
			/**
			 * Tool message.
			 */
			@JsonProperty("tool")
			TOOL

		}
	}

	/**
	 * Represents a chat completion response returned by model, based on the provided input.
	 *
	 * @param id A unique identifier for the chat completion. Each chunk has the same ID.
	 * @param object The object type, which is always 'chat.completion'.
	 * @param created The Unix timestamp (in seconds) of when the chat completion was created.
	 * Each chunk has the same timestamp.
	 * @param model A string representing the version of the model being used.
	 * @param systemFingerprint This field is not yet available.
	 * @param choices A list of chat completion choices.
	 * @param usage Usage statistics for the completion request.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ChatCompletion(@JsonProperty("id") String id,
								 @JsonProperty("object") String object,
								 @JsonProperty("created") Long created,
								 @JsonProperty("model") String model,
								 @JsonProperty("system_fingerprint") Object systemFingerprint,
								 @JsonProperty("choices") List<Choice> choices,
								 @JsonProperty("usage") Usage usage) {
		/**
		 * Choice statistics for the completion request.
		 *
		 * @param finishReason A unique identifier for the chat completion. Each chunk has the same ID.
		 * @param index The index of the choice in the list of choices.
		 * @param message A chat completion message generated by the model.
		 * @param logprobs This field is not yet available.
		 * @param usage Usage statistics for the completion request.
		 */
		public record Choice(
				@JsonProperty("finish_reason") String finishReason,
				@JsonProperty("index") int index,
				@JsonProperty("message") Message message,
				@JsonProperty("logprobs") Object logprobs,
				@JsonProperty("usage") Usage usage
		) {
		}

		/**
		 * A chat completion message generated by the model.
		 *
		 * @param content The contents of the message.
		 * @param role The role of the author of this message.
		 * @param toolCalls A list of tools selected by model to call.
		 */
		public record Message(
				@JsonProperty("content") String content,
				@JsonProperty("role") String role,
				@JsonProperty("tool_calls") ToolCalls toolCalls
		) {
		}

		/**
		 * A list of tools selected by model to call.
		 *
		 * @param id The ID of tool calls.
		 * @param type The type of tool.
		 * @param function A function object to call.
		 */
		public record ToolCalls(
				@JsonProperty("id") String id,
				@JsonProperty("type") String type,
				@JsonProperty("function") Function function
		) {
		}

		/**
		 * A function object to call.
		 *
		 * @param name The name of function to call.
		 * @param arguments A JSON input to function.
		 */
		public record Function(
				@JsonProperty("name") String name,
				@JsonProperty("arguments") String arguments
		) {
		}

		/**
		 * Usage statistics for the completion request.
		 *
		 * @param completionTokens Number of tokens in the generated completion.
		 * @param promptTokens Number of tokens in the prompt.
		 * @param totalTokens Total number of tokens used in the request (prompt + completion).
		 */
		@JsonInclude(JsonInclude.Include.NON_NULL)
		public record Usage(
				@JsonProperty("completion_tokens") Integer completionTokens,
				@JsonProperty("prompt_tokens") Integer promptTokens,
				@JsonProperty("total_tokens") Integer totalTokens
		) {
		}
	}

	/**
	 * Represents a streamed chunk of a chat completion response returned by model, based
	 * on the provided input.
	 *
	 * @param id A unique identifier for the chat completion. Each chunk has the same ID.
	 * @param object The object type, which is always 'chat.completion.chunk'.
	 * @param created The Unix timestamp (in seconds) of when the chat completion was created.
	 * Each chunk has the same timestamp.
	 * @param model A string representing the version of the model being used.
	 * @param systemFingerprint This field is not yet available.
	 * @param choices A list of chat completion choices.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ChatCompletionChunk(
		@JsonProperty("id") String id,
		@JsonProperty("object") String object,
	  	@JsonProperty("created") Long created,
	  	@JsonProperty("model") String model,
		@JsonProperty("system_fingerprint") Object systemFingerprint,
		@JsonProperty("choices") List<Choice> choices
	) {
		/**
		 * A list of chat completion choices.
		 *
		 * @param finishReason The reason the model stopped generating tokens.
		 * This will be stop if the model hit a natural stop point or a provided stop sequence,
		 * length if the maximum number of tokens specified in the request was reached.
		 * @param index The index of the choice in the list of choices.
		 * @param delta A chat completion message generated by the model.
		 * @param logprobs This field is not yet available.
		 */
		public record Choice(
			@JsonProperty("finish_reason") String finishReason,
			@JsonProperty("index") int index,
			@JsonProperty("delta") Delta delta,
		 	@JsonProperty("logprobs") Object logprobs) {
		}

		/**
		 * A chat completion message generated by the model.
		 *
		 * @param content The contents of the message.
		 * @param role The role of the author of this message.
		 * @param toolCalls A list of tools selected by model to call.
		 */
		public record Delta(
			@JsonProperty("content") String content,
			@JsonProperty("role") String role,
			@JsonProperty("tool_calls") ToolCalls toolCalls
		) {
		}

		/**
		 * A list of tools selected by model to call.
		 *
		 * @param id The ID of tool calls.
		 * @param type The type of tool.
		 * @param function A function object to call.
		 */
		public record ToolCalls(
			@JsonProperty("id") String id,
			@JsonProperty("type") String type,
			@JsonProperty("function") Function function
		) {
		}

		/**
		 * A function object to call.
		 *
		 * @param name The name of function to call.
		 * @param arguments A JSON input to function.
		 */
		public record Function(
			@JsonProperty("name") String name,
			@JsonProperty("arguments") String arguments
		) {
		}

	}

}
