package org.springframework.ai.zhipuai.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * @author Ricken Bazolo
 */
public class ZhipuAiApi {

	private static final String DEFAULT_BASE_URL = "https://open.bigmodel.cn";

	private static final Predicate<String> SSE_DONE_PREDICATE = "[DONE]"::equals;

	private final RestClient restClient;

	private WebClient webClient;

	/**
	 * Create a new client api with DEFAULT_BASE_URL
	 * @param zhipuAiApiKey Zhipu api Key.
	 */
	public ZhipuAiApi(String zhipuAiApiKey) {
		this(DEFAULT_BASE_URL, zhipuAiApiKey);
	}

	/**
	 * Create a new client api.
	 * @param baseUrl api base URL.
	 * @param zhipuAiApiKey Mistral api Key.
	 */
	public ZhipuAiApi(String baseUrl, String zhipuAiApiKey) {
		this(baseUrl, zhipuAiApiKey, RestClient.builder(), RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER);
	}

	/**
	 * Create a new client api.
	 * @param baseUrl api base URL.
	 * @param zhipuAiApiKey Mistral api Key.
	 * @param restClientBuilder RestClient builder.
	 * @param responseErrorHandler Response error handler.
	 */
	public ZhipuAiApi(String baseUrl, String zhipuAiApiKey, RestClient.Builder restClientBuilder,
			ResponseErrorHandler responseErrorHandler) {

		Consumer<HttpHeaders> jsonContentHeaders = headers -> {
			headers.setBearerAuth(zhipuAiApiKey);
			headers.setContentType(MediaType.APPLICATION_JSON);
		};

		this.restClient = restClientBuilder.baseUrl(baseUrl)
			.defaultHeaders(jsonContentHeaders)
			.defaultStatusHandler(responseErrorHandler)
			.build();

		this.webClient = WebClient.builder().baseUrl(baseUrl).defaultHeaders(jsonContentHeaders).build();
	}

	/**
	 * Represents a tool the model may call. Currently, only functions are supported as a
	 * tool.
	 *
	 * @param type The type of the tool. Currently, only 'function' is supported.
	 * @param function The function definition.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record FunctionTool(@JsonProperty("type") Type type, @JsonProperty("function") Function function,
			@JsonProperty("retrieval") Retrieval retrieval, @JsonProperty("web_search") WebSearch webSearch) {

		/**
		 * Create a tool of type 'function' and the given function definition.
		 * @param function function definition.
		 */
		@ConstructorBinding
		public FunctionTool(Function function) {
			this(Type.FUNCTION, function, null, null);
		}

		/**
		 * Create a tool of type and the given function definition.
		 * @param function function definition.
		 */
		@ConstructorBinding
		public FunctionTool(Type type, Function function) {
			this(type, function, null, null);
		}

		/**
		 * Create a tool of type 'function' and the given function definition.
		 */
		public enum Type {

			/**
			 * Function tool type.
			 */
			@JsonProperty("function")
			FUNCTION

		}

		/**
		 * Retrieval definition.
		 *
		 * @param knowledgeId
		 * @param promptTemplate
		 */
		public record Retrieval(@JsonProperty("knowledge_id") String knowledgeId,
				@JsonProperty("prompt_template") String promptTemplate) {
		}

		/**
		 * Web search definition.
		 *
		 * @param enable For enabling the search for custom key content.
		 * @param searchQuery Force Search for Custom Key Content At this point, the model
		 * will respond to the user-initiated conversation based on the results returned
		 * by the custom search key content as background knowledge.
		 */
		public record WebSearch(@JsonProperty("enable") Boolean enable,
				@JsonProperty("search_query") String searchQuery) {
		}

		/**
		 * Function definition.
		 *
		 * @param description A description of what the function does, used by the model
		 * to choose when and how to call the function.
		 * @param name The name of the function to be called. Must be a-z, A-Z, 0-9, or
		 * contain underscores and dashes, with a maximum length of 64.
		 * @param parameters The parameters the functions accepts, described as a JSON
		 * Schema object. To describe a function that accepts no parameters, provide the
		 * value {"type": "object", "properties": {}}.
		 */
		public record Function(@JsonProperty("description") String description, @JsonProperty("name") String name,
				@JsonProperty("parameters") Map<String, Object> parameters) {

			/**
			 * Create tool function definition.
			 * @param description tool function description.
			 * @param name tool function name.
			 * @param jsonSchema tool function schema as json.
			 */
			@ConstructorBinding
			public Function(String description, String name, String jsonSchema) {
				this(description, name, ModelOptionsUtils.jsonToMap(jsonSchema));
			}
		}
	}

	/**
	 * The role of the author of this message.
	 */
	public enum Role {

		// @formatter:off
        @JsonProperty("system") SYSTEM,
        @JsonProperty("user") USER,
        @JsonProperty("assistant") ASSISTANT,
        @JsonProperty("tool") TOOL
        // @formatter:on

	}

	/**
	 * Represents a chat completion response returned by model, based on the provided
	 * input.
	 *
	 * @param id A unique identifier for the chat completion.
	 * @param created The Unix timestamp (in seconds) of when the chat completion was
	 * created.
	 * @param model The model used for the chat completion.
	 * @param choices A list of chat completion choices.
	 * @param usage Usage statistics for the completion request.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ChatCompletion(
	// @formatter:off
            @JsonProperty("id") String id,
            @JsonProperty("choices") List<Choice> choices,
            @JsonProperty("created") Long created,
            @JsonProperty("model") String model,
            @JsonProperty("request_id") String requestId,
            @JsonProperty("usage") Usage usage) {
        // @formatter:on

		/**
		 * Chat completion choice.
		 *
		 * @param index The index of the choice in the list of choices.
		 * @param message A chat completion message generated by the model.
		 * @param finishReason The reason the model stopped generating tokens.
		 */
		@JsonInclude(JsonInclude.Include.NON_NULL)
		public record Choice(
		// @formatter:off
                @JsonProperty("index") Integer index,
                @JsonProperty("message") ChatCompletionMessage message,
                @JsonProperty("finish_reason") ChatCompletionFinishReason finishReason) {
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
        @JsonProperty("model_length") MODEL_LENGTH,
        /**
         * The model called a tool.
         */
        @JsonProperty("tool_call") TOOL_CALL,

        @JsonProperty("tool_calls") TOOL_CALLS
        // @formatter:on

	}

	/**
	 * Message comprising the conversation.
	 *
	 * @param content The contents of the message.
	 * @param role The role of the messages author. Could be one of the {@link Role}
	 * types.
	 * @param toolCalls The tool calls generated by the model, such as function calls.
	 * Applicable only for {@link Role#ASSISTANT} role and null otherwise.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ChatCompletionMessage(
	// @formatter:off
            @JsonProperty("content") String content,
            @JsonProperty("role") Role role,
            @JsonProperty("name") String name,
            @JsonProperty("tool_calls") List<ToolCall> toolCalls) {
        // @formatter:on

		/**
		 * Create a chat completion message with the given content and role. All other
		 * fields are null.
		 * @param content The contents of the message.
		 * @param role The role of the author of this message.
		 */
		public ChatCompletionMessage(String content, Role role) {
			this(content, role, null, null);
		}

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
            @JsonProperty("assistant") ASSISTANT,
            @JsonProperty("tool") TOOL
            // @formatter:on

		}

		/**
		 * The relevant tool call.
		 *
		 * @param id The ID of the tool call. This ID must be referenced when you submit
		 * the tool outputs in using the Submit tool outputs to run endpoint.
		 * @param type The type of tool call the output is required for. For now, this is
		 * always function.
		 * @param function The function definition.
		 */
		@JsonInclude(JsonInclude.Include.NON_NULL)
		public record ToolCall(@JsonProperty("id") String id, @JsonProperty("type") String type,
				@JsonProperty("function") ChatCompletionFunction function) {
		}

		/**
		 * The function definition.
		 *
		 * @param name The name of the function.
		 * @param arguments The arguments that the model expects you to pass to the
		 * function.
		 */
		@JsonInclude(JsonInclude.Include.NON_NULL)
		public record ChatCompletionFunction(@JsonProperty("name") String name,
				@JsonProperty("arguments") String arguments) {
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
	@JsonInclude(JsonInclude.Include.NON_NULL)
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
	@JsonInclude(JsonInclude.Include.NON_NULL)
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

		// If the input is a list, then the list must be joined, and the string must not
		// be empty.
		var joinInput = "";

		// The input must not an empty string, and any array must be 1024 dimensions or
		// less.
		if (embeddingRequest.input() instanceof List list) {
			Assert.isTrue(!CollectionUtils.isEmpty(list), "The input list can not be empty.");
			Assert.isTrue(list.size() <= 1024, "The list must be 1024 dimensions or less");
			Assert.isTrue(
					list.get(0) instanceof String || list.get(0) instanceof Integer || list.get(0) instanceof List,
					"The input must be either a String, or a List of Strings or list of list of integers.");

			joinInput = list.stream().map(Object::toString).collect(Collectors.joining(", ")).toString();
		}

		var newEmbeddingRequest = new EmbeddingRequest<>(joinInput.isEmpty() ? embeddingRequest.input : joinInput,
				embeddingRequest.model());

		return this.restClient.post()
			.uri("/api/paas/v4/embeddings")
			.body(newEmbeddingRequest)
			.retrieve()
			.toEntity(new ParameterizedTypeReference<>() {
			});
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
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record EmbeddingList<T>(
	// @formatter:off
            @JsonProperty("object") String object,
            @JsonProperty("data") List<T> data,
            @JsonProperty("model") String model,
            @JsonProperty("usage") Usage usage) {
        // @formatter:on
	}

	/**
	 * List of well-known Zhipu embedding models.
	 * <a href="https://open.bigmodel.cn/dev/api#text_embedding">...</a>
	 */
	public enum EmbeddingModel {

		// @formatter:off
        @JsonProperty("mistral-embed") EMBED("embedding-2");
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
	 * Creates an embedding vector representing the input text.
	 *
	 * @param input Input text to embed, encoded as a string or array of tokens
	 * @param model ID of the model to use. or base64.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record EmbeddingRequest<T>(
	// @formatter:off
            @JsonProperty("input") T input,
            @JsonProperty("model") String model) {
        // @formatter:on

		/**
		 * Create an embedding request with the given input. The model is set to
		 * 'embedding-2'.
		 * @param input Input text to embed.
		 */
		public EmbeddingRequest(T input) {
			this(input, EmbeddingModel.EMBED.getValue());
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
			.uri("/api/paas/v4/chat/completions")
			.body(chatRequest)
			.retrieve()
			.toEntity(ChatCompletion.class);
	}

	/**
	 * List of well-known Zhipu chat models. <a
	 * href="https://open.bigmodel.cn/dev/api#language>Zhipu Large general model</a>
	 */
	public enum ChatModel {

		// @formatter:off
        GLM_4("glm-4"),
        GLM_4_V("glm-4v"),
        GLM_3_TURBO("glm-3-turbo");
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
	 * Creates a model request for chat conversation.
	 *
	 * @param model ID of the model to use.
	 * @param messages The prompt(s) to generate completions for, encoded as a list of
	 * dict with role and content. The first prompt role should be user or system.
	 * @param tools A list of tools the model may call. Currently, only functions are
	 * supported as a tool. Use this to provide a list of functions the model may generate
	 * JSON inputs for.
	 * @param toolChoice Controls which (if any) function is called by the model. none
	 * means the model will not call a function and instead generates a message. auto
	 * means the model can pick between generating a message or calling a function. Any
	 * means the model must call a function.
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
	 * @param requestId The parameters transmitted by the client must be unique; a unique
	 * identifier allowing each request to be distinguished will be generated by default
	 * by the platform if the client does not transmit it.
	 * @param doSample When do_sample is true, the sampling strategy is enabled. When
	 * do_sample is false, the temperature and top_p sampling strategy will not take
	 * effect. The default is true.
	 * @param userId The unique end-user identifier helps the platform intervene in
	 * end-user violations, generation of illegal and harmful information or other abusive
	 * behavior. ID length requirements: minimum 6 characters, maximum 128 characters.
	 * @param responseFormat An object specifying the format that the model must output.
	 * Setting to { "type": "json_object" } enables JSON mode, which guarantees the
	 * message the model generates is valid JSON.
	 * @param stop The model will stop generating when it encounters the character
	 * specified by stop. Currently it only supports a single stop word in the format:
	 * ["stop_word1"]
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ChatCompletionRequest(
	// @formatter:off
            @JsonProperty("model") String model,
            @JsonProperty("messages") List<ChatCompletionMessage> messages,
            @JsonProperty("request_id") String requestId,
            @JsonProperty("do_sample") Boolean doSample,
            @JsonProperty("tools") List<FunctionTool> tools,
            @JsonProperty("tool_choice") ToolChoice toolChoice,
            @JsonProperty("temperature") Float temperature,
            @JsonProperty("top_p") Float topP,
            @JsonProperty("max_tokens") Integer maxTokens,
            @JsonProperty("stream") Boolean stream,
            @JsonProperty("user_id") String userId,
            @JsonProperty("stop") List<String> stop,
            @JsonProperty("response_format") ResponseFormat responseFormat) {
        // @formatter:on

		/**
		 * Shortcut constructor for a chat completion request with the given messages and
		 * model.
		 * @param messages The prompt(s) to generate completions for, encoded as a list of
		 * dict with role and content. The first prompt role should be user or system.
		 * @param model ID of the model to use.
		 */
		public ChatCompletionRequest(List<ChatCompletionMessage> messages, String model) {
			this(model, messages, null, true, null, null, 0.95f, 0.7f, null, false, null, null, null);
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
			this(model, messages, null, true, null, null, temperature, 0.7f, null, stream, null, null, null);
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
			this(model, messages, null, true, null, null, temperature, 0.7f, null, false, null, null, null);
		}

		/**
		 * Shortcut constructor for a chat completion request with the given messages,
		 * model, tools and tool choice. Streaming is set to false, temperature to 0.95
		 * and all other parameters are null.
		 * @param messages A list of messages comprising the conversation so far.
		 * @param model ID of the model to use.
		 * @param tools A list of tools the model may call. Currently, only functions are
		 * supported as a tool.
		 * @param toolChoice Controls which (if any) function is called by the model.
		 */
		public ChatCompletionRequest(List<ChatCompletionMessage> messages, String model, List<FunctionTool> tools,
				ToolChoice toolChoice) {
			this(model, messages, null, true, tools, toolChoice, 0.95f, 0.7f, null, false, null, null, null);
		}

		/**
		 * Shortcut constructor for a chat completion request with the given messages and
		 * stream.
		 */
		public ChatCompletionRequest(List<ChatCompletionMessage> messages, Boolean stream) {
			this(null, messages, null, true, null, null, 0.95f, 0.7f, null, stream, null, null, null);
		}

		/**
		 * Specifies a tool the model should use. Use to force the model to call a
		 * specific function.
		 *
		 */
		public enum ToolChoice {

			// @formatter:off
            @JsonProperty("auto") AUTO,
            @JsonProperty("any") ANY,
            @JsonProperty("none") NONE
            // @formatter:on

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

	private ZhipuAiStreamFunctionCallingHelper chunkMerger = new ZhipuAiStreamFunctionCallingHelper();

	/**
	 * Creates a streaming chat response for the given chat conversation.
	 * @param chatRequest The chat completion request. Must have the stream property set
	 * to true.
	 * @return Returns a {@link Flux} stream from chat completion chunks.
	 */
	public Flux<ChatCompletionChunk> chatCompletionStream(ChatCompletionRequest chatRequest) {

		Assert.notNull(chatRequest, "The request body can not be null.");
		Assert.isTrue(chatRequest.stream(), "Request must set the steam property to true.");

		AtomicBoolean isInsideTool = new AtomicBoolean(false);

		return this.webClient.post()
			.uri("/api/paas/v4/chat/completions")
			.body(Mono.just(chatRequest), ChatCompletionRequest.class)
			.retrieve()
			.bodyToFlux(String.class)
			.takeUntil(SSE_DONE_PREDICATE)
			.filter(SSE_DONE_PREDICATE.negate())
			.map(content -> ModelOptionsUtils.jsonToObject(content, ChatCompletionChunk.class))
			.map(chunk -> {
				if (this.chunkMerger.isStreamingToolFunctionCall(chunk)) {
					isInsideTool.set(true);
				}
				return chunk;
			})
			.windowUntil(chunk -> {
				if (isInsideTool.get() && this.chunkMerger.isStreamingToolFunctionCallFinish(chunk)) {
					isInsideTool.set(false);
					return true;
				}
				return !isInsideTool.get();
			})
			.concatMapIterable(window -> {
				Mono<ChatCompletionChunk> mono1 = window.reduce(new ChatCompletionChunk(null, null, null, null),
						(previous, current) -> this.chunkMerger.merge(previous, current));
				return List.of(mono1);
			})
			.flatMap(mono -> mono);
	}

	/**
	 * Represents a streamed chunk of a chat completion response returned by model, based
	 * on the provided input.
	 *
	 * @param id A unique identifier for the chat completion. Each chunk has the same ID.
	 * @param created The Unix timestamp (in seconds) of when the chat completion was
	 * created. Each chunk has the same timestamp.
	 * @param model The model used for the chat completion.
	 * @param choices A list of chat completion choices. Can be more than one if n is
	 * greater than 1.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ChatCompletionChunk(
	// @formatter:off
            @JsonProperty("id") String id,
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
		@JsonInclude(JsonInclude.Include.NON_NULL)
		public record ChunkChoice(
		// @formatter:off
                @JsonProperty("index") Integer index,
                @JsonProperty("delta") ChatCompletionMessage delta,
                @JsonProperty("finish_reason") ChatCompletionFinishReason finishReason) {
            // @formatter:on
		}
	}

}
