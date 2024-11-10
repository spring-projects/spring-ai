package org.springframework.ai.solar.api;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.solar.api.common.SolarConstants;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
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

	private static final Predicate<ChatCompletionChunk> SSE_DONE_PREDICATE = ChatCompletionChunk::end;

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
	 * Creates an embedding vector representing the input text or token array.
	 * @param embeddingRequest The embedding request.
	 * @return Returns list of {@link Embedding} wrapped in {@link EmbeddingList}.
	 */
	public ResponseEntity<EmbeddingList> embeddings(EmbeddingRequest embeddingRequest) {

		Assert.notNull(embeddingRequest, "The request body can not be null.");

		// Input text to embed, encoded as a string or array of tokens. To embed multiple
		// inputs in a single
		// request, pass an array of strings or array of token arrays.
		Assert.notNull(embeddingRequest.texts(), "The input can not be null.");

		// The input must not an empty string, and any array must be 16 dimensions or
		// less.
		Assert.isTrue(!CollectionUtils.isEmpty(embeddingRequest.texts()), "The input list can not be empty.");
		Assert.isTrue(embeddingRequest.texts().size() <= 16, "The list must be 16 dimensions or less");

		return this.restClient.post()
			.uri("/v1/solar/embeddings")
			.body(embeddingRequest)
			.retrieve()
			.toEntity(new ParameterizedTypeReference<>() {
			});
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
	 * @param model ID of the model to use.
	 * @param frequencyPenalty Number between -2.0 and 2.0. Positive values penalize new
	 * tokens based on their existing frequency in the text so far, decreasing the model's
	 * likelihood to repeat the same line verbatim.
	 * @param maxTokens The maximum number of tokens to generate in the chat completion.
	 * The total length of input tokens and generated tokens is limited by the model's
	 * context length. appear in the text so far, increasing the model's likelihood to
	 * talk about new topics.
	 * @param responseFormat An object specifying the format that the model must output.
	 * Setting to { "type": "json_object" } enables JSON mode, which guarantees the
	 * message the model generates is valid JSON.
	 * @param stop Up to 4 sequences where the API will stop generating further tokens.
	 * @param stream If set, partial message deltas will be sent.Tokens will be sent as
	 * data-only server-sent events as they become available, with the stream terminated
	 * by a data: [DONE] message.
	 * @param temperature What sampling temperature to use, between 0 and 1. Higher values
	 * like 0.8 will make the output more random, while lower values like 0.2 will make it
	 * more focused and deterministic. We generally recommend altering this or top_p but
	 * not both.
	 * @param topP An alternative to sampling with temperature, called nucleus sampling,
	 * where the model considers the results of the tokens with top_p probability mass. So
	 * 0.1 means only the tokens comprising the top 10% probability mass are considered.
	 * We generally recommend altering this or temperature but not both.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ChatCompletionRequest(@JsonProperty("messages") List<ChatCompletionMessage> messages,
			@JsonProperty("system") String system, @JsonProperty("model") String model,
			@JsonProperty("frequency_penalty") Double frequencyPenalty,
			@JsonProperty("max_output_tokens") Integer maxTokens,
			@JsonProperty("presence_penalty") Double presencePenalty,
			@JsonProperty("response_format") ResponseFormat responseFormat, @JsonProperty("stop") List<String> stop,
			@JsonProperty("stream") Boolean stream, @JsonProperty("temperature") Double temperature,
			@JsonProperty("top_p") Double topP) {

		/**
		 * Shortcut constructor for a chat completion request with the given messages and
		 * model.
		 * @param messages A list of messages comprising the conversation so far.
		 * @param model ID of the model to use.
		 * @param temperature What sampling temperature to use, between 0 and 1.
		 */
		public ChatCompletionRequest(List<ChatCompletionMessage> messages, String system, String model,
				Double temperature) {
			this(messages, system, model, null, null, null, null, null, false, temperature, null);
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
			this(messages, null, model, null, null, null, null, null, stream, null, null);
		}

		/**
		 * An object specifying the format that the model must output.
		 *
		 * @param type Must be one of 'text' or 'json_object'.
		 */
		@JsonInclude(JsonInclude.Include.NON_NULL)
		public record ResponseFormat(@JsonProperty("type") String type) {
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
			ASSISTANT

		}
	}

	/**
	 * Represents a chat completion response returned by model, based on the provided
	 * input.
	 *
	 * @param id A unique identifier for the chat completion.
	 * @param created The Unix timestamp (in seconds) of when the chat completion was
	 * created. used in conjunction with the seed request parameter to understand when
	 * backend changes have been made that might impact determinism.
	 * @param object The object type, which is always chat.completion.
	 * @param usage Usage statistics for the completion request.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ChatCompletion(@JsonProperty("id") String id, @JsonProperty("object") String object,
			@JsonProperty("created") Long created, @JsonProperty("model") String model,
			@JsonProperty("choices") List<Choice> choices, @JsonProperty("usage") Usage usage,
			@JsonProperty("system_fingerprint") String systemFingerprint) {
		public record Choice(@JsonProperty("index") int index, @JsonProperty("message") Message message,
				@JsonProperty("logprobs") Object logprobs, @JsonProperty("finish_reason") String finishReason) {
		}

		public record Message(@JsonProperty("role") String role, @JsonProperty("content") String content) {
		}
	}

	/**
	 * Usage statistics for the completion request.
	 *
	 * @param promptTokens Number of tokens in the prompt.
	 * @param totalTokens Total number of tokens used in the request (prompt +
	 * completion).
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Usage(@JsonProperty("completion_tokens") Integer completionTokens,
			@JsonProperty("prompt_tokens") Integer promptTokens, @JsonProperty("total_tokens") Integer totalTokens) {
	}

	/**
	 * Represents a streamed chunk of a chat completion response returned by model, based
	 * on the provided input.
	 *
	 * @param id A unique identifier for the chat completion. Each chunk has the same ID.
	 * @param object The object type, which is always 'chat.completion.chunk'.
	 * @param created The Unix timestamp (in seconds) of when the chat completion was
	 * created. Each chunk has the same timestamp.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ChatCompletionChunk(@JsonProperty("id") String id, @JsonProperty("object") String object,
			@JsonProperty("created") Long created, @JsonProperty("choices") List<Choice> choices,
			@JsonProperty("finish_reason") String finishReason, @JsonProperty("is_end") Boolean end,
			@JsonProperty("usage") Usage usage, @JsonProperty("system_finger_print") String systemFingerPrint) {
		public record Choice(@JsonProperty("index") int index, @JsonProperty("delta") Delta delta,
				@JsonProperty("logprobs") Object logprobs, @JsonProperty("finish_reason") String finishReason) {
		}

		public record Delta(@JsonProperty("role") String role, @JsonProperty("content") String content) {
		}
	}

	/**
	 * Creates an embedding vector representing the input text.
	 *
	 * @param texts Input text to embed, encoded as a string or array of tokens.
	 * @param user A unique identifier representing your end-user, which can help Solar to
	 * monitor and detect abuse.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record EmbeddingRequest(@JsonProperty("input") List<String> texts, @JsonProperty("model") String model,
			@JsonProperty("user_id") String user) {
		/**
		 * Create an embedding request with the given input. Embedding model is set to
		 * 'bge_large_zh'.
		 * @param text Input text to embed.
		 */
		public EmbeddingRequest(String text) {
			this(List.of(text), DEFAULT_EMBEDDING_MODEL, null);
		}

		/**
		 * Create an embedding request with the given input.
		 * @param text Input text to embed.
		 * @param model ID of the model to use.
		 * @param userId A unique identifier representing your end-user, which can help
		 * Solar to monitor and detect abuse.
		 */
		public EmbeddingRequest(String text, String model, String userId) {
			this(List.of(text), model, userId);
		}

		/**
		 * Create an embedding request with the given input. Embedding model is set to
		 * 'bge_large_zh'.
		 * @param texts Input text to embed.
		 */
		public EmbeddingRequest(List<String> texts) {
			this(texts, DEFAULT_EMBEDDING_MODEL, null);
		}

		/**
		 * Create an embedding request with the given input.
		 * @param texts Input text to embed.
		 * @param model ID of the model to use.
		 */
		public EmbeddingRequest(List<String> texts, String model) {
			this(texts, model, null);
		}
	}

	/**
	 * Represents an embedding vector returned by embedding endpoint.
	 *
	 * @param index The index of the embedding in the list of embeddings.
	 * @param embedding The embedding vector, which is a list of floats. The length of
	 * vector depends on the model.
	 * @param object The object type, which is always 'embedding'.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Embedding(
	// @formatter:off
			@JsonProperty("index") Integer index,
			@JsonProperty("embedding") float[] embedding,
			@JsonProperty("object") String object) {
		// @formatter:on

		/**
		 * Create an embedding with the given index, embedding and object type set to
		 * 'embedding'.
		 * @param index The index of the embedding in the list of embeddings.
		 * @param embedding The embedding vector, which is a list of floats. The length of
		 * vector depends on the model.
		 */
		public Embedding(Integer index, float[] embedding) {
			this(index, embedding, "embedding");
		}
	}

	/**
	 * List of multiple embedding responses.
	 *
	 * @param object Must have value "embedding_list".
	 * @param data List of entities.
	 * @param model ID of the model to use.
	 * @param usage Usage statistics for the completion request.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record EmbeddingList(
	// @formatter:off
			@JsonProperty("object") String object,
			@JsonProperty("data") List<Embedding> data,
			@JsonProperty("model") String model,
			@JsonProperty("error_code") String errorCode,
			@JsonProperty("error_msg") String errorNsg,
			@JsonProperty("usage") Usage usage) {
		// @formatter:on
	}

}
