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

package org.springframework.ai.zhipuai.api;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.ai.model.ApiKey;
import org.springframework.ai.model.ChatModelDescription;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.NoopApiKey;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Single class implementation of the
 * <a href="https://open.bigmodel.cn/dev/api#http">ZhiPuAI Chat Completion API</a> and
 * <a href="https://open.bigmodel.cn/dev/api#text_embedding">ZhiPuAI Embedding API</a>.
 *
 * @author Geng Rong
 * @author Thomas Vitale
 * @author YunKui Lu
 * @since 1.0.0
 */
public class ZhiPuAiApi {

	/**
	 * Returns a builder pre-populated with the current configuration for mutation.
	 */
	public Builder mutate() {
		return new Builder(this);
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final String DEFAULT_CHAT_MODEL = ChatModel.GLM_4_Air.getValue();

	public static final String DEFAULT_EMBEDDING_MODEL = EmbeddingModel.Embedding_2.getValue();

	public static final String DEFAULT_EMBEDDINGS_PATH = "/v4/embeddings";

	public static final String DEFAULT_COMPLETIONS_PATH = "/v4/chat/completions";

	private static final Predicate<String> SSE_DONE_PREDICATE = "[DONE]"::equals;

	private final String baseUrl;

	private final ApiKey apiKey;

	private final MultiValueMap<String, String> headers;

	private final String completionsPath;

	private final String embeddingsPath;

	private final ResponseErrorHandler responseErrorHandler;

	private final RestClient restClient;

	private final WebClient webClient;

	private final ZhiPuAiStreamFunctionCallingHelper chunkMerger = new ZhiPuAiStreamFunctionCallingHelper();

	/**
	 * Create a new chat completion api with default base URL.
	 * @param zhiPuAiToken ZhiPuAI apiKey.
	 * @deprecated Use {@link #builder()} instead.
	 */
	@Deprecated
	public ZhiPuAiApi(String zhiPuAiToken) {
		this(ZhiPuApiConstants.DEFAULT_BASE_URL, zhiPuAiToken);
	}

	/**
	 * Create a new chat completion api.
	 * @param baseUrl api base URL.
	 * @param zhiPuAiToken ZhiPuAI apiKey.
	 * @deprecated Use {@link #builder()} instead.
	 */
	@Deprecated
	public ZhiPuAiApi(String baseUrl, String zhiPuAiToken) {
		this(baseUrl, zhiPuAiToken, RestClient.builder());
	}

	/**
	 * Create a new chat completion api.
	 * @param baseUrl api base URL.
	 * @param zhiPuAiToken ZhiPuAI apiKey.
	 * @param restClientBuilder RestClient builder.
	 * @deprecated Use {@link #builder()} instead.
	 */
	@Deprecated
	public ZhiPuAiApi(String baseUrl, String zhiPuAiToken, RestClient.Builder restClientBuilder) {
		this(baseUrl, zhiPuAiToken, restClientBuilder, RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER);
	}

	/**
	 * Create a new chat completion api.
	 * @param baseUrl api base URL.
	 * @param zhiPuAiToken ZhiPuAI apiKey.
	 * @param restClientBuilder RestClient builder.
	 * @param responseErrorHandler Response error handler.
	 * @deprecated Use {@link #builder()} instead.
	 */
	@Deprecated
	public ZhiPuAiApi(String baseUrl, String zhiPuAiToken, RestClient.Builder restClientBuilder,
			ResponseErrorHandler responseErrorHandler) {
		this(baseUrl, new SimpleApiKey(zhiPuAiToken), new LinkedMultiValueMap<>(), DEFAULT_COMPLETIONS_PATH,
				DEFAULT_EMBEDDINGS_PATH, restClientBuilder, WebClient.builder(), responseErrorHandler);
	}

	/**
	 * Create a new chat completion api.
	 * @param baseUrl api base URL.
	 * @param apiKey ZhiPuAI apiKey.
	 * @param headers the http headers to use.
	 * @param completionsPath the path to the chat completions endpoint.
	 * @param embeddingsPath the path to the embeddings endpoint.
	 * @param restClientBuilder RestClient builder.
	 * @param webClientBuilder WebClient builder.
	 * @param responseErrorHandler Response error handler.
	 */
	private ZhiPuAiApi(String baseUrl, ApiKey apiKey, MultiValueMap<String, String> headers, String completionsPath,
			String embeddingsPath, RestClient.Builder restClientBuilder, WebClient.Builder webClientBuilder,
			ResponseErrorHandler responseErrorHandler) {
		Assert.hasText(completionsPath, "Completions Path must not be null");
		Assert.hasText(embeddingsPath, "Embeddings Path must not be null");
		Assert.notNull(headers, "Headers must not be null");

		this.baseUrl = baseUrl;
		this.apiKey = apiKey;
		this.headers = headers;
		this.completionsPath = completionsPath;
		this.embeddingsPath = embeddingsPath;
		this.responseErrorHandler = responseErrorHandler;

		Consumer<HttpHeaders> authHeaders = h -> {
			h.setContentType(MediaType.APPLICATION_JSON);
			h.addAll(headers);
		};

		this.restClient = restClientBuilder.clone()
			.baseUrl(baseUrl)
			.defaultHeaders(authHeaders)
			.defaultStatusHandler(responseErrorHandler)
			.build();

		// @formatter:off
		this.webClient = webClientBuilder.clone()
			.baseUrl(baseUrl)
			.defaultHeaders(authHeaders)
			.build(); // @formatter:on
	}

	public static String getTextContent(List<ChatCompletionMessage.MediaContent> content) {
		return content.stream()
			.filter(c -> "text".equals(c.type()))
			.map(ChatCompletionMessage.MediaContent::text)
			.reduce("", (a, b) -> a + b);
	}

	/**
	 * Creates a model response for the given chat conversation.
	 * @param chatRequest The chat completion request.
	 * @return Entity response with {@link ChatCompletion} as a body and HTTP status code
	 * and headers.
	 */
	public ResponseEntity<ChatCompletion> chatCompletionEntity(ChatCompletionRequest chatRequest) {
		return chatCompletionEntity(chatRequest, new LinkedMultiValueMap<>());
	}

	/**
	 * Creates a model response for the given chat conversation.
	 * @param chatRequest The chat completion request.
	 * @return Entity response with {@link ChatCompletion} as a body and HTTP status code
	 * and headers.
	 */
	public ResponseEntity<ChatCompletion> chatCompletionEntity(ChatCompletionRequest chatRequest,
			MultiValueMap<String, String> additionalHttpHeader) {

		Assert.notNull(chatRequest, "The request body can not be null.");
		Assert.isTrue(!chatRequest.stream(), "Request must set the stream property to false.");

		// @formatter:off
		return this.restClient.post()
			.uri(this.completionsPath)
			.headers(headers -> {
				headers.addAll(additionalHttpHeader);
				addDefaultHeadersIfMissing(headers);
			})
			.body(chatRequest)
			.retrieve()
			.toEntity(ChatCompletion.class);
		// @formatter:on
	}

	/**
	 * Creates a streaming chat response for the given chat conversation.
	 * @param chatRequest The chat completion request. Must have the stream property set
	 * to true.
	 * @return Returns a {@link Flux} stream from chat completion chunks.
	 */
	public Flux<ChatCompletionChunk> chatCompletionStream(ChatCompletionRequest chatRequest) {
		return chatCompletionStream(chatRequest, new LinkedMultiValueMap<>());
	}

	/**
	 * Creates a streaming chat response for the given chat conversation.
	 * @param chatRequest The chat completion request. Must have the stream property set
	 * to true.
	 * @return Returns a {@link Flux} stream from chat completion chunks.
	 */
	public Flux<ChatCompletionChunk> chatCompletionStream(ChatCompletionRequest chatRequest,
			MultiValueMap<String, String> additionalHttpHeader) {

		Assert.notNull(chatRequest, "The request body can not be null.");
		Assert.isTrue(chatRequest.stream(), "Request must set the stream property to true.");

		AtomicBoolean isInsideTool = new AtomicBoolean(false);

		// @formatter:off
		return this.webClient.post()
			.uri(this.completionsPath)
			.headers(headers -> {
				headers.addAll(additionalHttpHeader);
				addDefaultHeadersIfMissing(headers);
			}) // @formatter:on
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
				Mono<ChatCompletionChunk> monoChunk = window
					.reduce(new ChatCompletionChunk(null, null, null, null, null, null), this.chunkMerger::merge);
				return List.of(monoChunk);
			})
			.flatMap(mono -> mono);
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

		if (embeddingRequest.input() instanceof List list) {
			Assert.isTrue(!CollectionUtils.isEmpty(list), "The input list can not be empty.");
			Assert.isTrue(list.size() <= 512, "The list must be 512 dimensions or less");
			Assert.isTrue(
					list.get(0) instanceof String || list.get(0) instanceof Integer || list.get(0) instanceof List,
					"The input must be either a String, or a List of Strings or list of list of integers.");
		}

		return this.restClient.post()
			.uri(this.embeddingsPath)
			.headers(this::addDefaultHeadersIfMissing)
			.body(embeddingRequest)
			.retrieve()
			.toEntity(new ParameterizedTypeReference<>() {
			});
	}

	private void addDefaultHeadersIfMissing(HttpHeaders headers) {
		if (!headers.containsKey(HttpHeaders.AUTHORIZATION) && !(this.apiKey instanceof NoopApiKey)) {
			headers.setBearerAuth(this.apiKey.getValue());
		}
	}

	// Package-private getters for mutate/copy
	String getBaseUrl() {
		return this.baseUrl;
	}

	ApiKey getApiKey() {
		return this.apiKey;
	}

	MultiValueMap<String, String> getHeaders() {
		return this.headers;
	}

	String getCompletionsPath() {
		return this.completionsPath;
	}

	String getEmbeddingsPath() {
		return this.embeddingsPath;
	}

	ResponseErrorHandler getResponseErrorHandler() {
		return this.responseErrorHandler;
	}

	/**
	 * ZhiPuAI Chat Completion Models:
	 * <a href="https://open.bigmodel.cn/dev/howuse/model">ZhiPuAI Model</a>.
	 */
	public enum ChatModel implements ChatModelDescription {

		// @formatter:off
		GLM_4("GLM-4"),

		GLM_4V("glm-4v"),

		GLM_4_Air("glm-4-air"),

		GLM_4_AirX("glm-4-airx"),

		GLM_4_Flash("glm-4-flash"),

		GLM_3_Turbo("GLM-3-Turbo"); // @formatter:on

		public final String value;

		ChatModel(String value) {
			this.value = value;
		}

		public String getValue() {
			return this.value;
		}

		@Override
		public String getName() {
			return this.value;
		}

	}

	/**
	 * The reason the model stopped generating tokens.
	 */
	public enum ChatCompletionFinishReason {

		/**
		 * The model hit a natural stop point or a provided stop sequence.
		 */
		@JsonProperty("stop")
		STOP,
		/**
		 * The maximum number of tokens specified in the request was reached.
		 */
		@JsonProperty("length")
		LENGTH,
		/**
		 * The content was omitted due to a flag from our content filters.
		 */
		@JsonProperty("content_filter")
		CONTENT_FILTER,
		/**
		 * The model called a tool.
		 */
		@JsonProperty("tool_calls")
		TOOL_CALLS,
		/**
		 * Only for compatibility with Mistral AI API.
		 */
		@JsonProperty("tool_call")
		TOOL_CALL

	}

	/**
	 * ZhiPuAI Embeddings Models:
	 * <a href="https://open.bigmodel.cn/dev/api#text_embedding">Embeddings</a>.
	 */
	public enum EmbeddingModel {

		/**
		 * DIMENSION: 1024
		 */
		Embedding_2("Embedding-2"),

		/**
		 * DIMENSION: up to 2048
		 */
		Embedding_3("Embedding-3");

		public final String value;

		EmbeddingModel(String value) {
			this.value = value;
		}

		public String getValue() {
			return this.value;
		}

	}

	/**
	 * Represents a tool the model may call. Currently, only functions are supported as a
	 * tool.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public static class FunctionTool {

		// The type of the tool. Currently, only 'function' is supported.
		@JsonProperty("type")
		private Type type = Type.FUNCTION;

		// The function definition.
		@JsonProperty("function")
		private Function function;

		public FunctionTool() {

		}

		/**
		 * Create a tool of type 'function' and the given function definition.
		 * @param type the tool type
		 * @param function function definition
		 */
		public FunctionTool(Type type, Function function) {
			this.type = type;
			this.function = function;
		}

		/**
		 * Create a tool of type 'function' and the given function definition.
		 * @param function function definition.
		 */
		public FunctionTool(Function function) {
			this(Type.FUNCTION, function);
		}

		public Type getType() {
			return this.type;
		}

		public Function getFunction() {
			return this.function;
		}

		public void setType(Type type) {
			this.type = type;
		}

		public void setFunction(Function function) {
			this.function = function;
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
		 * Function definition.
		 */
		public static class Function {

			@JsonProperty("description")
			private String description;

			@JsonProperty("name")
			private String name;

			@JsonProperty("parameters")
			private Map<String, Object> parameters;

			@JsonIgnore
			private String jsonSchema;

			private Function() {

			}

			/**
			 * Create tool function definition.
			 * @param description A description of what the function does, used by the
			 * model to choose when and how to call the function.
			 * @param name The name of the function to be called. Must be a-z, A-Z, 0-9,
			 * or contain underscores and dashes, with a maximum length of 64.
			 * @param parameters The parameters the functions accepts, described as a JSON
			 * Schema object. To describe a function that accepts no parameters, provide
			 * the value {"type": "object", "properties": {}}.
			 */
			public Function(String description, String name, Map<String, Object> parameters) {
				this.description = description;
				this.name = name;
				this.parameters = parameters;
			}

			/**
			 * Create tool function definition.
			 * @param description tool function description.
			 * @param name tool function name.
			 * @param jsonSchema tool function schema as json.
			 */
			public Function(String description, String name, String jsonSchema) {
				this(description, name, ModelOptionsUtils.jsonToMap(jsonSchema));
			}

			public String getDescription() {
				return this.description;
			}

			public String getName() {
				return this.name;
			}

			public Map<String, Object> getParameters() {
				return this.parameters;
			}

			public void setDescription(String description) {
				this.description = description;
			}

			public void setName(String name) {
				this.name = name;
			}

			public void setParameters(Map<String, Object> parameters) {
				this.parameters = parameters;
			}

			public String getJsonSchema() {
				return this.jsonSchema;
			}

			public void setJsonSchema(String jsonSchema) {
				this.jsonSchema = jsonSchema;
				if (jsonSchema != null) {
					this.parameters = ModelOptionsUtils.jsonToMap(jsonSchema);
				}
			}

		}

	}

	/**
	 * Creates a model response for the given chat conversation.
	 *
	 * @param messages A list of messages comprising the conversation so far.
	 * @param model ID of the model to use.
	 * @param maxTokens The maximum number of tokens to generate in the chat completion.
	 * The total length of input tokens and generated tokens is limited by the model's
	 * context length.
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
	 * @param tools A list of tools the model may call. Currently, only functions are
	 * supported as a tool. Use this to provide a list of functions the model may generate
	 * JSON inputs for.
	 * @param toolChoice Controls which (if any) function is called by the model. none
	 * means the model will not call a function and instead generates a message. auto
	 * means the model can pick between generating a message or calling a function.
	 * Specifying a particular function via {"type: "function", "function": {"name":
	 * "my_function"}} forces the model to call that function. none is the default when no
	 * functions are present. auto is the default if functions are present. Use the
	 * {@link ToolChoiceBuilder} to create the tool choice value.
	 * @param user A unique identifier representing your end-user, which can help ZhiPuAI
	 * to monitor and detect abuse.
	 * @param requestId A unique identifier for the request. If set, the request will be
	 * logged and can be used for debugging purposes.
	 * @param doSample If set, the model will use sampling to generate the next token. If
	 * not set, the model will use greedy decoding to generate the next token.
	 */
	@JsonInclude(Include.NON_NULL)
	public record ChatCompletionRequest(// @formatter:off
			@JsonProperty("messages") List<ChatCompletionMessage> messages,
			@JsonProperty("model") String model,
			@JsonProperty("max_tokens") Integer maxTokens,
			@JsonProperty("stop") List<String> stop,
			@JsonProperty("stream") Boolean stream,
			@JsonProperty("temperature") Double temperature,
			@JsonProperty("top_p") Double topP,
			@JsonProperty("tools") List<FunctionTool> tools,
			@JsonProperty("tool_choice") Object toolChoice,
			@JsonProperty("user") String user,
			@JsonProperty("request_id") String requestId,
			@JsonProperty("do_sample") Boolean doSample) { // @formatter:on

		/**
		 * Shortcut constructor for a chat completion request with the given messages and
		 * model.
		 * @param messages A list of messages comprising the conversation so far.
		 * @param model ID of the model to use.
		 * @param temperature What sampling temperature to use, between 0 and 1.
		 */
		public ChatCompletionRequest(List<ChatCompletionMessage> messages, String model, Double temperature) {
			this(messages, model, null, null, false, temperature, null, null, null, null, null, null);
		}

		/**
		 * Shortcut constructor for a chat completion request with the given messages,
		 * model and control for streaming.
		 * @param messages A list of messages comprising the conversation so far.
		 * @param model ID of the model to use.
		 * @param temperature What sampling temperature to use, between 0 and 1.
		 * @param stream If set, partial message deltas will be sent.Tokens will be sent
		 * as data-only server-sent events as they become available, with the stream
		 * terminated by a data: [DONE] message.
		 */
		public ChatCompletionRequest(List<ChatCompletionMessage> messages, String model, Double temperature,
				boolean stream) {
			this(messages, model, null, null, stream, temperature, null, null, null, null, null, null);
		}

		/**
		 * Shortcut constructor for a chat completion request with the given messages,
		 * model, tools and tool choice. Streaming is set to false, temperature to 0.8 and
		 * all other parameters are null.
		 * @param messages A list of messages comprising the conversation so far.
		 * @param model ID of the model to use.
		 * @param tools A list of tools the model may call. Currently, only functions are
		 * supported as a tool.
		 * @param toolChoice Controls which (if any) function is called by the model.
		 */
		public ChatCompletionRequest(List<ChatCompletionMessage> messages, String model, List<FunctionTool> tools,
				Object toolChoice) {
			this(messages, model, null, null, false, 0.8, null, tools, toolChoice, null, null, null);
		}

		/**
		 * Shortcut constructor for a chat completion request with the given messages,
		 * model, tools and tool choice. Streaming is set to false, temperature to 0.8 and
		 * all other parameters are null.
		 * @param messages A list of messages comprising the conversation so far.
		 * @param stream If set, partial message deltas will be sent.Tokens will be sent
		 * as data-only server-sent events as they become available, with the stream
		 * terminated by a data: [DONE] message.
		 */
		public ChatCompletionRequest(List<ChatCompletionMessage> messages, Boolean stream) {
			this(messages, null, null, null, stream, null, null, null, null, null, null, null);
		}

		/**
		 * Helper factory that creates a tool_choice of type 'none', 'auto' or selected
		 * function by name.
		 */
		public static class ToolChoiceBuilder {

			/**
			 * Model can pick between generating a message or calling a function.
			 */
			public static final String AUTO = "auto";

			/**
			 * Model will not call a function and instead generates a message
			 */
			public static final String NONE = "none";

			/**
			 * Specifying a particular function forces the model to call that function.
			 */
			public static Object function(String functionName) {
				return Map.of("type", "function", "function", Map.of("name", functionName));
			}

		}

		/**
		 * An object specifying the format that the model must output.
		 *
		 * @param type Must be one of 'text' or 'json_object'.
		 */
		@JsonInclude(Include.NON_NULL)
		public record ResponseFormat(@JsonProperty("type") String type) {
		}
	}

	/**
	 * Message comprising the conversation.
	 *
	 * @param rawContent The contents of the message. Can be either a {@link MediaContent}
	 * or a {@link String}. The response message content is always a {@link String}.
	 * @param role The role of the messages author. Could be one of the {@link Role}
	 * types.
	 * @param name An optional name for the participant. Provides the model information to
	 * differentiate between participants of the same role. In case of Function calling,
	 * the name is the function name that the message is responding to.
	 * @param toolCallId Tool call that this message is responding to. Only applicable for
	 * the {@link Role#TOOL} role and null otherwise.
	 * @param toolCalls The tool calls generated by the model, such as function calls.
	 * Applicable only for {@link Role#ASSISTANT} role and null otherwise.
	 */
	@JsonInclude(Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ChatCompletionMessage(// @formatter:off
			@JsonProperty("content") Object rawContent,
			@JsonProperty("role") Role role,
			@JsonProperty("name") String name,
			@JsonProperty("tool_call_id") String toolCallId,
			@JsonProperty("tool_calls") List<ToolCall> toolCalls) { // @formatter:on

		/**
		 * Create a chat completion message with the given content and role. All other
		 * fields are null.
		 * @param content The contents of the message.
		 * @param role The role of the author of this message.
		 */
		public ChatCompletionMessage(Object content, Role role) {
			this(content, role, null, null, null);
		}

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

		/**
		 * An array of content parts with a defined type. Each MediaContent can be of
		 * either "text" or "image_url" type. Not both.
		 *
		 * @param type Content type, each can be of type text or image_url.
		 * @param text The text content of the message.
		 * @param imageUrl The image content of the message. You can pass multiple images
		 * by adding multiple image_url content parts. Image input is only supported when
		 * using the glm-4v model.
		 */
		@JsonInclude(Include.NON_NULL)
		@JsonIgnoreProperties(ignoreUnknown = true)
		public record MediaContent(// @formatter:off
				@JsonProperty("type") String type,
			    @JsonProperty("text") String text,
				@JsonProperty("image_url") ImageUrl imageUrl) { // @formatter:on

			/**
			 * Shortcut constructor for a text content.
			 * @param text The text content of the message.
			 */
			public MediaContent(String text) {
				this("text", text, null);
			}

			/**
			 * Shortcut constructor for an image content.
			 * @param imageUrl The image content of the message.
			 */
			public MediaContent(ImageUrl imageUrl) {
				this("image_url", null, imageUrl);
			}

			/**
			 * The image content of the message.
			 *
			 * @param url Either a URL of the image or the base64 encoded image data. The
			 * base64 encoded image data must have a special prefix in the following
			 * format: "data:{mimetype};base64,{base64-encoded-image-data}".
			 * @param detail Specifies the detail level of the image.
			 */
			@JsonInclude(Include.NON_NULL)
			@JsonIgnoreProperties(ignoreUnknown = true)
			public record ImageUrl(// @formatter:off
					@JsonProperty("url") String url,
				    @JsonProperty("detail") String detail) { // @formatter:on

				public ImageUrl(String url) {
					this(url, null);
				}
			}
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
		@JsonInclude(Include.NON_NULL)
		@JsonIgnoreProperties(ignoreUnknown = true)
		public record ToolCall(// @formatter:off
				@JsonProperty("id") String id,
			    @JsonProperty("type") String type,
				@JsonProperty("function") ChatCompletionFunction function) { // @formatter:on
		}

		/**
		 * The function definition.
		 *
		 * @param name The name of the function.
		 * @param arguments The arguments that the model expects you to pass to the
		 * function.
		 */
		@JsonInclude(Include.NON_NULL)
		@JsonIgnoreProperties(ignoreUnknown = true)
		public record ChatCompletionFunction(// @formatter:off
				@JsonProperty("name") String name,
				@JsonProperty("arguments") String arguments) { // @formatter:on
		}
	}

	/**
	 * Represents a chat completion response returned by model, based on the provided
	 * input.
	 *
	 * @param id A unique identifier for the chat completion.
	 * @param choices A list of chat completion choices. Can be more than one if n is
	 * greater than 1.
	 * @param created The Unix timestamp (in seconds) of when the chat completion was
	 * created.
	 * @param model The model used for the chat completion.
	 * @param systemFingerprint This fingerprint represents the backend configuration that
	 * the model runs with. Can be used in conjunction with the seed request parameter to
	 * understand when backend changes have been made that might impact determinism.
	 * @param object The object type, which is always chat.completion.
	 * @param usage Usage statistics for the completion request.
	 */
	@JsonInclude(Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ChatCompletion(// @formatter:off
			@JsonProperty("id") String id,
			@JsonProperty("choices") List<Choice> choices,
			@JsonProperty("created") Long created,
			@JsonProperty("model") String model,
			@JsonProperty("system_fingerprint") String systemFingerprint,
			@JsonProperty("object") String object,
			@JsonProperty("usage") Usage usage) { // @formatter:on

		/**
		 * Chat completion choice.
		 *
		 * @param finishReason The reason the model stopped generating tokens.
		 * @param index The index of the choice in the list of choices.
		 * @param message A chat completion message generated by the model.
		 * @param logprobs Log probability information for the choice.
		 */
		@JsonInclude(Include.NON_NULL)
		@JsonIgnoreProperties(ignoreUnknown = true)
		public record Choice(// @formatter:off
				@JsonProperty("finish_reason") ChatCompletionFinishReason finishReason,
				@JsonProperty("index") Integer index,
				@JsonProperty("message") ChatCompletionMessage message,
				@JsonProperty("logprobs") LogProbs logprobs) { // @formatter:on

		}
	}

	/**
	 * Log probability information for the choice.
	 *
	 * @param content A list of message content tokens with log probability information.
	 */
	@JsonInclude(Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record LogProbs(// @formatter:off
			@JsonProperty("content") List<Content> content) { // @formatter:on

		/**
		 * Message content tokens with log probability information.
		 *
		 * @param token The token.
		 * @param logprob The log probability of the token.
		 * @param probBytes A list of integers representing the UTF-8 bytes representation
		 * of the token. Useful in instances where characters are represented by multiple
		 * tokens and their byte representations must be combined to generate the correct
		 * text representation. Can be null if there is no bytes representation for the
		 * token.
		 * @param topLogprobs List of the most likely tokens and their log probability, at
		 * this token position. In rare cases, there may be fewer than the number of
		 * requested top_logprobs returned.
		 */
		@JsonInclude(Include.NON_NULL)
		@JsonIgnoreProperties(ignoreUnknown = true)
		public record Content(// @formatter:off
				@JsonProperty("token") String token,
				@JsonProperty("logprob") Float logprob,
				@JsonProperty("bytes") List<Integer> probBytes,
				@JsonProperty("top_logprobs") List<TopLogProbs> topLogprobs) { // @formatter:on

			/**
			 * The most likely tokens and their log probability, at this token position.
			 *
			 * @param token The token.
			 * @param logprob The log probability of the token.
			 * @param probBytes A list of integers representing the UTF-8 bytes
			 * representation of the token. Useful in instances where characters are
			 * represented by multiple tokens and their byte representations must be
			 * combined to generate the correct text representation. Can be null if there
			 * is no bytes representation for the token.
			 */
			@JsonInclude(Include.NON_NULL)
			@JsonIgnoreProperties(ignoreUnknown = true)
			public record TopLogProbs(// @formatter:off
					@JsonProperty("token") String token,
					@JsonProperty("logprob") Float logprob,
					@JsonProperty("bytes") List<Integer> probBytes) { // @formatter:on
			}
		}
	}

	/**
	 * Usage statistics for the completion request.
	 *
	 * @param completionTokens Number of tokens in the generated completion. Only
	 * applicable for completion requests.
	 * @param promptTokens Number of tokens in the prompt.
	 * @param totalTokens Total number of tokens used in the request (prompt +
	 * completion).
	 */
	@JsonInclude(Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Usage(// @formatter:off
			@JsonProperty("completion_tokens") Integer completionTokens,
			@JsonProperty("prompt_tokens") Integer promptTokens,
			@JsonProperty("total_tokens") Integer totalTokens) { // @formatter:on

	}

	/**
	 * Represents a streamed chunk of a chat completion response returned by model, based
	 * on the provided input.
	 *
	 * @param id A unique identifier for the chat completion. Each chunk has the same ID.
	 * @param choices A list of chat completion choices. Can be more than one if n is
	 * greater than 1.
	 * @param created The Unix timestamp (in seconds) of when the chat completion was
	 * created. Each chunk has the same timestamp.
	 * @param model The model used for the chat completion.
	 * @param systemFingerprint This fingerprint represents the backend configuration that
	 * the model runs with. Can be used in conjunction with the seed request parameter to
	 * understand when backend changes have been made that might impact determinism.
	 * @param object The object type, which is always 'chat.completion.chunk'.
	 */
	@JsonInclude(Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ChatCompletionChunk(// @formatter:off
			@JsonProperty("id") String id,
			@JsonProperty("choices") List<ChunkChoice> choices,
			@JsonProperty("created") Long created,
			@JsonProperty("model") String model,
			@JsonProperty("system_fingerprint") String systemFingerprint,
			@JsonProperty("object") String object) { // @formatter:on

		/**
		 * Chat completion choice.
		 *
		 * @param finishReason The reason the model stopped generating tokens.
		 * @param index The index of the choice in the list of choices.
		 * @param delta A chat completion delta generated by streamed model responses.
		 * @param logprobs Log probability information for the choice.
		 */
		@JsonInclude(Include.NON_NULL)
		@JsonIgnoreProperties(ignoreUnknown = true)
		public record ChunkChoice(// @formatter:off
				@JsonProperty("finish_reason") ChatCompletionFinishReason finishReason,
				@JsonProperty("index") Integer index,
				@JsonProperty("delta") ChatCompletionMessage delta,
				@JsonProperty("logprobs") LogProbs logprobs) { // @formatter:on
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
	@JsonInclude(Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Embedding(// @formatter:off
			@JsonProperty("index") Integer index,
			@JsonProperty("embedding") float[] embedding,
			@JsonProperty("object") String object) { // @formatter:on

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
	 * Creates an embedding vector representing the input text.
	 *
	 * @param <T> Type of the input.
	 * @param input Input text to embed, encoded as a string or array of tokens.
	 * @param model ID of the model to use.
	 */
	@JsonInclude(Include.NON_NULL)
	public record EmbeddingRequest<T>(// @formatter:off
			@JsonProperty("input") T input,
			@JsonProperty("model") String model,
			@JsonProperty("dimensions") Integer dimensions) { // @formatter:on

		/**
		 * Create an embedding request with the given input. Encoding model is set to
		 * 'embedding-2'.
		 * @param input Input text to embed.
		 */
		public EmbeddingRequest(T input) {
			this(input, DEFAULT_EMBEDDING_MODEL, null);
		}

		/**
		 * Create an embedding request with the given input and model.
		 * @param input
		 * @param model
		 */
		public EmbeddingRequest(T input, String model) {
			this(input, model, null);
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
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record EmbeddingList<T>(// @formatter:off
			@JsonProperty("object") String object,
			@JsonProperty("data") List<T> data,
			@JsonProperty("model") String model,
			@JsonProperty("usage") Usage usage) { // @formatter:on
	}

	public static class Builder {

		private Builder() {
		}

		public Builder(ZhiPuAiApi api) {
			this.baseUrl = api.getBaseUrl();
			this.apiKey = api.getApiKey();
			this.headers = new LinkedMultiValueMap<>(api.getHeaders());
			this.completionsPath = api.getCompletionsPath();
			this.embeddingsPath = api.getEmbeddingsPath();
			this.restClientBuilder = api.restClient != null ? api.restClient.mutate() : RestClient.builder();
			this.webClientBuilder = api.webClient != null ? api.webClient.mutate() : WebClient.builder();
			this.responseErrorHandler = api.getResponseErrorHandler();
		}

		private String baseUrl = ZhiPuApiConstants.DEFAULT_BASE_URL;

		private ApiKey apiKey;

		private MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();

		private String completionsPath = DEFAULT_COMPLETIONS_PATH;

		private String embeddingsPath = DEFAULT_EMBEDDINGS_PATH;

		private RestClient.Builder restClientBuilder = RestClient.builder();

		private WebClient.Builder webClientBuilder = WebClient.builder();

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
			this.apiKey = new SimpleApiKey(simpleApiKey);
			return this;
		}

		public Builder headers(MultiValueMap<String, String> headers) {
			Assert.notNull(headers, "headers cannot be null");
			this.headers = headers;
			return this;
		}

		public Builder completionsPath(String completionsPath) {
			Assert.hasText(completionsPath, "completionsPath cannot be null or empty");
			this.completionsPath = completionsPath;
			return this;
		}

		public Builder embeddingsPath(String embeddingsPath) {
			Assert.hasText(embeddingsPath, "embeddingsPath cannot be null or empty");
			this.embeddingsPath = embeddingsPath;
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

		public ZhiPuAiApi build() {
			Assert.notNull(this.apiKey, "apiKey must be set");
			return new ZhiPuAiApi(this.baseUrl, this.apiKey, this.headers, this.completionsPath, this.embeddingsPath,
					this.restClientBuilder, this.webClientBuilder, this.responseErrorHandler);
		}

	}

}
