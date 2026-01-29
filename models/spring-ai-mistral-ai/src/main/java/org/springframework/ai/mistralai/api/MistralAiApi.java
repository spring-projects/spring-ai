/*
 * Copyright 2023-2026 the original author or authors.
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

package org.springframework.ai.mistralai.api;

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
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.ai.model.ChatModelDescription;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Single-class, Java Client library for Mistral AI platform. Provides implementation for
 * the <a href=
 * "https://docs.mistral.ai/api/#tag/embeddings/operation/embeddings_v1_embeddings_post">Embeddings</a>
 * and the <a href=
 * "https://docs.mistral.ai/api/#tag/chat/operation/chat_completion_v1_chat_completions_post">Chat
 * Completion</a> APIs.
 * <p>
 * Implements <b>Synchronous</b> and <b>Streaming</b> chat completion and supports latest
 * <b>Function Calling</b> features.
 * </p>
 *
 * @author Ricken Bazolo
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Jason Smith
 * @author Nicolas Krier
 * @since 1.0.0
 */
public class MistralAiApi {

	public static Builder builder() {
		return new Builder();
	}

	public static final String PROVIDER_NAME = AiProvider.MISTRAL_AI.value();

	private static final String DEFAULT_BASE_URL = "https://api.mistral.ai";

	private static final Predicate<String> SSE_DONE_PREDICATE = "[DONE]"::equals;

	private final RestClient restClient;

	private final WebClient webClient;

	private final MistralAiStreamFunctionCallingHelper chunkMerger = new MistralAiStreamFunctionCallingHelper();

	/**
	 * Create a new client api.
	 * @param baseUrl api base URL.
	 * @param apiKey Mistral api Key.
	 * @param restClientBuilder RestClient builder.
	 * @param responseErrorHandler Response error handler.
	 */
	public MistralAiApi(String baseUrl, String apiKey, RestClient.Builder restClientBuilder,
			WebClient.Builder webClientBuilder, ResponseErrorHandler responseErrorHandler) {

		Consumer<HttpHeaders> jsonContentHeaders = headers -> {
			headers.setBearerAuth(apiKey);
			headers.setContentType(MediaType.APPLICATION_JSON);
		};

		this.restClient = restClientBuilder.clone()
			.baseUrl(baseUrl)
			.defaultHeaders(jsonContentHeaders)
			.defaultStatusHandler(responseErrorHandler)
			.build();

		this.webClient = webClientBuilder.clone().baseUrl(baseUrl).defaultHeaders(jsonContentHeaders).build();
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
		if (embeddingRequest.input() instanceof List<?> list) {
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
	 * Creates a model response for the given chat conversation.
	 * @param chatRequest The chat completion request.
	 * @return Entity response with {@link ChatCompletion} as a body and HTTP status code
	 * and headers.
	 */
	public ResponseEntity<ChatCompletion> chatCompletionEntity(ChatCompletionRequest chatRequest) {

		Assert.notNull(chatRequest, "The request body can not be null.");
		Assert.isTrue(Boolean.FALSE.equals(chatRequest.stream()), "Request must set the stream property to false.");

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
		Assert.isTrue(Boolean.TRUE.equals(chatRequest.stream()), "Request must set the stream property to true.");

		AtomicBoolean isInsideTool = new AtomicBoolean(false);

		return this.webClient.post()
			.uri("/v1/chat/completions")
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
				Mono<ChatCompletionChunk> mono1 = window.reduce(this.chunkMerger::merge);
				return List.of(mono1);
			})
			.flatMap(mono -> mono);
	}

	/**
	 * The reason the model stopped generating tokens.
	 */
	public enum ChatCompletionFinishReason {

		// @formatter:off
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
		@JsonProperty("model_length")
		MODEL_LENGTH,

		@JsonProperty("error")
		ERROR,

		/**
		* The model requested a tool call.
		*/
		@JsonProperty("tool_calls")
		TOOL_CALLS
		 // @formatter:on

	}

	/**
	 * List of well-known Mistral chat models.
	 *
	 * @see <a href="https://docs.mistral.ai/getting-started/models">Mistral AI Models</a>
	 */
	public enum ChatModel implements ChatModelDescription {

		// @formatter:off
		// Premier Models
		MAGISTRAL_MEDIUM("magistral-medium-latest"),
		MISTRAL_MEDIUM("mistral-medium-latest"),
		CODESTRAL("codestral-latest"),
		DEVSTRAL_MEDIUM("devstral-medium-latest"),
		MISTRAL_LARGE("mistral-large-latest"),
		PIXTRAL_LARGE("pixtral-large-latest"),
		// Free Models
		MINISTRAL_3B("ministral-3b-latest"),
		MINISTRAL_8B("ministral-8b-latest"),
		MINISTRAL_14B("ministral-14b-latest"),
		MAGISTRAL_SMALL("magistral-small-latest"),
		DEVSTRAL_SMALL("devstral-small-latest"),
		MISTRAL_SMALL("mistral-small-latest"),
		PIXTRAL_12B("pixtral-12b-latest"),
		// Free Models - Research
		OPEN_MISTRAL_NEMO("open-mistral-nemo");
		// @formatter:on

		private final String value;

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
	 * List of well-known Mistral embedding models.
	 *
	 * @see <a href="https://docs.mistral.ai/getting-started/models">Mistral AI Models</a>
	 */
	public enum EmbeddingModel {

		// @formatter:off
		/**
		 * Mistral Embed model for general text embeddings.
		 * Produces 1024-dimensional embeddings suitable for semantic search,
		 * clustering, and other text similarity tasks.
		 */
		EMBED("mistral-embed"),

		/**
		 * Codestral Embed model optimized for code embeddings.
		 * Produces 1536-dimensional embeddings specifically designed for
		 * code similarity, code search, and retrieval-augmented generation (RAG)
		 * with code repositories.
		 */
		CODESTRAL_EMBED("codestral-embed");
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
	 * Represents a tool the model may call. Currently, only functions are supported as a
	 * tool.
	 */
	@JsonInclude(Include.NON_NULL)
	public static class FunctionTool {

		// The type of the tool. Currently, only 'function' is supported.
		@JsonProperty("type")
		Type type = Type.FUNCTION;

		// The function definition.
		@JsonProperty("function")
		@SuppressWarnings("NullAway.Init")
		Function function;

		public FunctionTool() {

		}

		/**
		 * Create a tool of type 'function' and the given function definition.
		 * @param function function definition.
		 */
		public FunctionTool(Function function) {
			this(Type.FUNCTION, function);
		}

		public FunctionTool(Type type, Function function) {
			this.type = type;
			this.function = function;
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
			@SuppressWarnings("NullAway.Init")
			private String description;

			@JsonProperty("name")
			@SuppressWarnings("NullAway.Init")
			private String name;

			@JsonProperty("parameters")
			@SuppressWarnings("NullAway.Init")
			private Map<String, Object> parameters;

			@JsonIgnore
			private @Nullable String jsonSchema;

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

			public @Nullable String getJsonSchema() {
				return this.jsonSchema;
			}

			public void setJsonSchema(@Nullable String jsonSchema) {
				this.jsonSchema = jsonSchema;
				if (jsonSchema != null) {
					this.parameters = ModelOptionsUtils.jsonToMap(jsonSchema);
				}
			}

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
	@JsonIgnoreProperties(ignoreUnknown = true)
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
	@JsonIgnoreProperties(ignoreUnknown = true)
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

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (!(o instanceof Embedding embedding1)) {
				return false;
			}
			return Objects.equals(this.index, embedding1.index) && Arrays.equals(this.embedding, embedding1.embedding)
					&& Objects.equals(this.object, embedding1.object);
		}

		@Override
		public int hashCode() {
			int result = Objects.hash(this.index, this.object);
			result = 31 * result + Arrays.hashCode(this.embedding);
			return result;
		}

		@Override
		public String toString() {
			return "Embedding{" + "index=" + this.index + ", embedding=" + Arrays.toString(this.embedding)
					+ ", object='" + this.object + '\'' + '}';
		}

	}

	/**
	 * Creates an embedding vector representing the input text.
	 *
	 * @param <T> Type of the input.
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
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record EmbeddingList<T>(
	// @formatter:off
			@JsonProperty("object") String object,
			@JsonProperty("data") List<T> data,
			@JsonProperty("model") String model,
			@JsonProperty("usage") Usage usage) {
		 // @formatter:on
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
	 * @param safePrompt Whether to inject a safety prompt before all conversations.
	 * @param stop A list of tokens that the model should stop generating after. If set,
	 * @param randomSeed The seed to use for random sampling. If set, different calls will
	 * generate deterministic results.
	 * @param responseFormat An object specifying the format or schema that the model must
	 * output. Setting to { "type": "json_object" } enables JSON mode, which guarantees
	 * the message the model generates is valid JSON. Setting to { "type": "json_object" ,
	 * "json_schema": schema} allows you to ensure the model provides an answer in a very
	 * specific JSON format by supplying a clear JSON schema.
	 */
	@JsonInclude(Include.NON_NULL)
	public record ChatCompletionRequest(
	// @formatter:off
			@JsonProperty("model") @Nullable String model,
			@JsonProperty("messages") List<ChatCompletionMessage> messages,
			@JsonProperty("tools") @Nullable List<FunctionTool> tools,
			@JsonProperty("tool_choice") @Nullable ToolChoice toolChoice,
			@JsonProperty("temperature") @Nullable Double temperature,
			@JsonProperty("top_p") @Nullable Double topP,
			@JsonProperty("max_tokens") @Nullable Integer maxTokens,
			@JsonProperty("stream") @Nullable Boolean stream,
			@JsonProperty("safe_prompt") @Nullable Boolean safePrompt,
			@JsonProperty("stop") @Nullable List<String> stop,
			@JsonProperty("random_seed") @Nullable Integer randomSeed,
			@JsonProperty("response_format") @Nullable ResponseFormat responseFormat) {
		 // @formatter:on

		/**
		 * Shortcut constructor for a chat completion request with the given messages and
		 * model.
		 * @param messages The prompt(s) to generate completions for, encoded as a list of
		 * dict with role and content. The first prompt role should be user or system.
		 * @param model ID of the model to use.
		 */
		public ChatCompletionRequest(List<ChatCompletionMessage> messages, String model) {
			this(model, messages, null, null, 0.7, 1.0, null, false, false, null, null, null);
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
		public ChatCompletionRequest(List<ChatCompletionMessage> messages, String model, Double temperature,
				boolean stream) {
			this(model, messages, null, null, temperature, 1.0, null, stream, false, null, null, null);
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
		public ChatCompletionRequest(List<ChatCompletionMessage> messages, String model, Double temperature) {
			this(model, messages, null, null, temperature, 1.0, null, false, false, null, null, null);
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
				ToolChoice toolChoice) {
			this(model, messages, tools, toolChoice, null, 1.0, null, false, false, null, null, null);
		}

		/**
		 * Shortcut constructor for a chat completion request with the given messages and
		 * stream.
		 */
		public ChatCompletionRequest(List<ChatCompletionMessage> messages, Boolean stream) {
			this(null, messages, null, null, 0.7, 1.0, null, stream, false, null, null, null);
		}

		/**
		 * Specifies a tool the model should use. Use to force the model to call a
		 * specific function.
		 *
		 */
		public enum ToolChoice {

			// @formatter:off
			@JsonProperty("auto")
			AUTO,
			@JsonProperty("any")
			ANY,
			@JsonProperty("none")
			NONE
			 // @formatter:on

		}

		/**
		 * An object specifying the format that the model must output.
		 *
		 * <p>
		 * Setting the type to JSON_SCHEMA enables Structured Outputs which ensures the
		 * model will match your supplied JSON schema.
		 * </p>
		 *
		 * @author Ricken Bazolo
		 * @author Christian Tzolov
		 * @see <a href= "https://docs.mistral.ai/capabilities/structured-output/">Mistral
		 * AI Structured Output</a>
		 */
		@JsonInclude(Include.NON_NULL)
		public static class ResponseFormat {

			/**
			 * Type Must be one of 'text', 'json_object' or 'json_schema'.
			 */
			@JsonProperty("type")
			private Type type;

			/**
			 * JSON schema object that describes the format of the JSON object. Only
			 * applicable when type is 'json_schema'.
			 */
			@JsonProperty("json_schema")
			private @Nullable JsonSchema jsonSchema;

			@JsonIgnore
			private @Nullable String schema;

			@SuppressWarnings("NullAway") // Constructor designed for Jackson databinding
			public ResponseFormat() {
			}

			/**
			 * @deprecated Use {@link #builder()} or factory methods instead.
			 */
			@Deprecated
			public ResponseFormat(String type) {
				this(Type.fromValue(type), (JsonSchema) null);
			}

			/**
			 * @deprecated Use {@link #builder()} or factory methods instead.
			 */
			@Deprecated
			public ResponseFormat(String type, @Nullable Map<String, Object> jsonSchema) {
				this(Type.fromValue(type),
						jsonSchema != null ? JsonSchema.builder().schema(jsonSchema).strict(true).build() : null);
			}

			private ResponseFormat(Type type, @Nullable JsonSchema jsonSchema) {
				this.type = type;
				this.jsonSchema = jsonSchema;
			}

			public ResponseFormat(Type type, String schema) {
				this(type, org.springframework.util.StringUtils.hasText(schema)
						? JsonSchema.builder().schema(schema).strict(true).build() : null);
			}

			public Type getType() {
				return this.type;
			}

			public void setType(Type type) {
				this.type = type;
			}

			public @Nullable JsonSchema getJsonSchema() {
				return this.jsonSchema;
			}

			public void setJsonSchema(JsonSchema jsonSchema) {
				this.jsonSchema = jsonSchema;
			}

			public @Nullable String getSchema() {
				return this.schema;
			}

			public void setSchema(@Nullable String schema) {
				this.schema = schema;
				if (schema != null) {
					this.jsonSchema = JsonSchema.builder().schema(schema).strict(true).build();
				}
			}

			// Factory methods

			/**
			 * Creates a ResponseFormat for text output.
			 * @return ResponseFormat configured for text output
			 */
			public static ResponseFormat text() {
				return new ResponseFormat(Type.TEXT, (JsonSchema) null);
			}

			/**
			 * Creates a ResponseFormat for JSON object output (JSON mode).
			 * @return ResponseFormat configured for JSON object output
			 */
			public static ResponseFormat jsonObject() {
				return new ResponseFormat(Type.JSON_OBJECT, (JsonSchema) null);
			}

			/**
			 * Creates a ResponseFormat for JSON schema output with automatic schema
			 * generation from a class.
			 * @param clazz the class to generate the JSON schema from
			 * @return ResponseFormat configured with the generated JSON schema
			 */
			public static ResponseFormat jsonSchema(Class<?> clazz) {
				String schemaJson = org.springframework.ai.util.json.schema.JsonSchemaGenerator.generateForType(clazz);
				return jsonSchema(schemaJson);
			}

			/**
			 * Creates a ResponseFormat for JSON schema output with a JSON schema string.
			 * @param schema the JSON schema as a string
			 * @return ResponseFormat configured with the provided JSON schema
			 */
			public static ResponseFormat jsonSchema(String schema) {
				return new ResponseFormat(Type.JSON_SCHEMA, JsonSchema.builder().schema(schema).strict(true).build());
			}

			/**
			 * Creates a ResponseFormat for JSON schema output with a JSON schema map.
			 * @param schema the JSON schema as a map
			 * @return ResponseFormat configured with the provided JSON schema
			 */
			public static ResponseFormat jsonSchema(Map<String, Object> schema) {
				return new ResponseFormat(Type.JSON_SCHEMA, JsonSchema.builder().schema(schema).strict(true).build());
			}

			public static Builder builder() {
				return new Builder();
			}

			@Override
			public boolean equals(Object o) {
				if (this == o) {
					return true;
				}
				if (o == null || getClass() != o.getClass()) {
					return false;
				}
				ResponseFormat that = (ResponseFormat) o;
				return this.type == that.type && Objects.equals(this.jsonSchema, that.jsonSchema);
			}

			@Override
			public int hashCode() {
				return Objects.hash(this.type, this.jsonSchema);
			}

			@Override
			public String toString() {
				return "ResponseFormat{" + "type=" + this.type + ", jsonSchema=" + this.jsonSchema + '}';
			}

			public static final class Builder {

				private @Nullable Type type;

				private @Nullable JsonSchema jsonSchema;

				private Builder() {
				}

				public Builder type(Type type) {
					this.type = type;
					return this;
				}

				public Builder jsonSchema(JsonSchema jsonSchema) {
					this.jsonSchema = jsonSchema;
					return this;
				}

				public Builder jsonSchema(String jsonSchema) {
					this.jsonSchema = JsonSchema.builder().schema(jsonSchema).build();
					return this;
				}

				public ResponseFormat build() {
					Assert.state(this.type != null, "The ype ");
					return new ResponseFormat(this.type, this.jsonSchema);
				}

			}

			public enum Type {

				/**
				 * Generates a text response. (default)
				 */
				@JsonProperty("text")
				TEXT("text"),

				/**
				 * Enables JSON mode, which guarantees the message the model generates is
				 * valid JSON.
				 */
				@JsonProperty("json_object")
				JSON_OBJECT("json_object"),

				/**
				 * Enables Structured Outputs which guarantees the model will match your
				 * supplied JSON schema.
				 */
				@JsonProperty("json_schema")
				JSON_SCHEMA("json_schema");

				private final String value;

				Type(String value) {
					this.value = value;
				}

				public String getValue() {
					return this.value;
				}

				public static Type fromValue(String value) {
					for (Type type : Type.values()) {
						if (type.value.equals(value)) {
							return type;
						}
					}
					throw new IllegalArgumentException("Unknown ResponseFormat type: " + value);
				}

			}

			/**
			 * JSON schema object that describes the format of the JSON object. Applicable
			 * for the 'json_schema' type only.
			 */
			@JsonInclude(Include.NON_NULL)
			public static class JsonSchema {

				@JsonProperty("name")
				private String name;

				@JsonProperty("schema")
				private Map<String, Object> schema;

				@JsonProperty("strict")
				private Boolean strict;

				@SuppressWarnings("NullAway") // Constructor designed for Jackson
												// databinding
				public JsonSchema() {
				}

				public String getName() {
					return this.name;
				}

				public Map<String, Object> getSchema() {
					return this.schema;
				}

				public Boolean getStrict() {
					return this.strict;
				}

				private JsonSchema(String name, Map<String, Object> schema, Boolean strict) {
					this.name = name;
					this.schema = schema;
					this.strict = strict;
				}

				public static Builder builder() {
					return new Builder();
				}

				@Override
				public int hashCode() {
					return Objects.hash(this.name, this.schema, this.strict);
				}

				@Override
				public boolean equals(@Nullable Object o) {
					if (this == o) {
						return true;
					}
					if (o == null || getClass() != o.getClass()) {
						return false;
					}
					JsonSchema that = (JsonSchema) o;
					return Objects.equals(this.name, that.name) && Objects.equals(this.schema, that.schema)
							&& Objects.equals(this.strict, that.strict);
				}

				@Override
				public String toString() {
					return "JsonSchema{" + "name='" + this.name + '\'' + ", schema=" + this.schema + ", strict="
							+ this.strict + '}';
				}

				public static final class Builder {

					private String name = "custom_schema";

					private @Nullable Map<String, Object> schema;

					private Boolean strict = true;

					private Builder() {
					}

					public Builder name(String name) {
						this.name = name;
						return this;
					}

					public Builder schema(Map<String, Object> schema) {
						this.schema = schema;
						return this;
					}

					public Builder schema(String schema) {
						this.schema = ModelOptionsUtils.jsonToMap(schema);
						return this;
					}

					public Builder strict(Boolean strict) {
						this.strict = strict;
						return this;
					}

					public JsonSchema build() {
						Assert.state(this.schema != null, "The schema must be defined");
						return new JsonSchema(this.name, this.schema, this.strict);
					}

				}

			}

		}

	}

	/**
	 * Message comprising the conversation.
	 *
	 * @param rawContent The contents of the message. Can be either a {@link MediaContent}
	 * or a {@link String}. The response message content is always a {@link String}.
	 * @param role The role of the messages author. Could be one of the {@link Role}
	 * types.
	 * @param name The name of the author of the message.
	 * @param toolCalls The tool calls generated by the model, such as function calls.
	 * Applicable only for {@link Role#ASSISTANT} role and null otherwise.
	 * @param toolCallId Tool call that this message is responding to. Only applicable for
	 * the {@link Role#TOOL} role and null otherwise.
	 */
	@JsonInclude(Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ChatCompletionMessage(
	// @formatter:off
		@JsonProperty("content") @Nullable Object rawContent,
		@JsonProperty("role") Role role,
		@JsonProperty("name") @Nullable String name,
		@JsonProperty("tool_calls") @Nullable List<ToolCall> toolCalls,
		@JsonProperty("tool_call_id") @Nullable String toolCallId) {
		// @formatter:on

		/**
		 * Message comprising the conversation.
		 * @param content The contents of the message.
		 * @param role The role of the messages author. Could be one of the {@link Role}
		 * types.
		 * @param toolCalls The tool calls generated by the model, such as function calls.
		 * Applicable only for {@link Role#ASSISTANT} role and null otherwise.
		 */
		public ChatCompletionMessage(@Nullable Object content, Role role, @Nullable String name,
				List<ToolCall> toolCalls) {
			this(content, role, name, toolCalls, null);
		}

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
		public @Nullable String content() {
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
		 * <p>
		 * NOTE: Mistral expects the system message to be before the user message or will
		 * fail with 400 error.
		 * </p>
		 */
		public enum Role {

			// @formatter:off
			@JsonProperty("system")
			SYSTEM,
			@JsonProperty("user")
			USER,
			@JsonProperty("assistant")
			ASSISTANT,
			@JsonProperty("tool")
			TOOL
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
		 * @param index The index of the tool call in the list of tool calls.
		 */
		@JsonInclude(Include.NON_NULL)
		@JsonIgnoreProperties(ignoreUnknown = true)
		public record ToolCall(@JsonProperty("id") String id, @JsonProperty("type") String type,
				@JsonProperty("function") ChatCompletionFunction function,
				@JsonProperty("index") @Nullable Integer index) {

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
		public record ChatCompletionFunction(@JsonProperty("name") String name,
				@JsonProperty("arguments") String arguments) {

		}

		/**
		 * An array of content parts with a defined type. Each MediaContent can be of
		 * either "text" or "image_url" type. Only one option allowed.
		 *
		 * @param type Content type, each can be of type text or image_url.
		 * @param text The text content of the message.
		 * @param imageUrl The image content of the message.
		 */
		@JsonInclude(Include.NON_NULL)
		@JsonIgnoreProperties(ignoreUnknown = true)
		public record MediaContent(
		// @formatter:off
		   		@JsonProperty("type") String type,
		   		@JsonProperty("text") @Nullable String text,
		   		@JsonProperty("image_url") @Nullable ImageUrl imageUrl
				// @formatter:on
		) {

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
			 * Shortcut constructor for an image content.
			 *
			 * @param url Either a URL of the image or the base64 encoded image data. The
			 * base64 encoded image data must have a special prefix in the following
			 * format: "data:{mimetype};base64,{base64-encoded-image-data}".
			 * @param detail Specifies the detail level of the image.
			 */
			@JsonInclude(Include.NON_NULL)
			public record ImageUrl(
			// @formatter:off
					@JsonProperty("url") String url,
					@JsonProperty("detail") @Nullable String detail
					// @formatter:on
			) {

				public ImageUrl(String url) {
					this(url, null);
				}

			}

		}

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
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ChatCompletion(
	// @formatter:off
		@JsonProperty("id") String id,
		@JsonProperty("object") String object,
		@JsonProperty("created") Long created,
		@JsonProperty("model") String model,
		@JsonProperty("choices") List<Choice> choices,
		@JsonProperty("usage") @Nullable Usage usage) {
		 // @formatter:on

		/**
		 * Chat completion choice.
		 *
		 * @param index The index of the choice in the list of choices.
		 * @param message A chat completion message generated by the model.
		 * @param finishReason The reason the model stopped generating tokens.
		 * @param logprobs Log probability information for the choice.
		 */
		@JsonInclude(Include.NON_NULL)
		@JsonIgnoreProperties(ignoreUnknown = true)
		public record Choice(
		// @formatter:off
			@JsonProperty("index") Integer index,
			@JsonProperty("message") ChatCompletionMessage message,
			@JsonProperty("finish_reason") ChatCompletionFinishReason finishReason,
			@JsonProperty("logprobs") @Nullable LogProbs logprobs) {
			 // @formatter:on
		}

	}

	/**
	 *
	 * Log probability information for the choice. anticipation of future changes.
	 *
	 * @param content A list of message content tokens with log probability information.
	 */
	@JsonInclude(Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record LogProbs(@JsonProperty("content") List<Content> content) {

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
		public record Content(@JsonProperty("token") String token, @JsonProperty("logprob") Float logprob,
				@JsonProperty("bytes") List<Integer> probBytes,
				@JsonProperty("top_logprobs") List<TopLogProbs> topLogprobs) {

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
			public record TopLogProbs(@JsonProperty("token") String token, @JsonProperty("logprob") Float logprob,
					@JsonProperty("bytes") List<Integer> probBytes) {

			}

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
	 * @param usage usage metrics for the chat completion.
	 */
	@JsonInclude(Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ChatCompletionChunk(
	// @formatter:off
		@JsonProperty("id") String id,
		@JsonProperty("object") @Nullable String object,
		@JsonProperty("created") @Nullable Long created,
		@JsonProperty("model") String model,
		@JsonProperty("choices") List<ChunkChoice> choices,
		@JsonProperty("usage") @Nullable Usage usage) {
		 // @formatter:on

		/**
		 * Chat completion choice.
		 *
		 * @param index The index of the choice in the list of choices.
		 * @param delta A chat completion delta generated by streamed model responses.
		 * @param finishReason The reason the model stopped generating tokens.
		 * @param logprobs Log probability information for the choice.
		 */
		@JsonInclude(Include.NON_NULL)
		@JsonIgnoreProperties(ignoreUnknown = true)
		public record ChunkChoice(
		// @formatter:off
			@SuppressWarnings("NullAway.Init") @JsonProperty("index") Integer index,
			@SuppressWarnings("NullAway.Init") @JsonProperty("delta") ChatCompletionMessage delta,
			@SuppressWarnings("NullAway.Init") @JsonProperty("finish_reason") ChatCompletionFinishReason finishReason,
			@JsonProperty("logprobs") @Nullable LogProbs logprobs) {
			 // @formatter:on
		}

	}

	public static final class Builder {

		private String baseUrl = DEFAULT_BASE_URL;

		private @Nullable String apiKey;

		private RestClient.Builder restClientBuilder = RestClient.builder();

		private WebClient.Builder webClientBuilder = WebClient.builder();

		private ResponseErrorHandler responseErrorHandler = RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER;

		public Builder baseUrl(String baseUrl) {
			Assert.hasText(baseUrl, "baseUrl cannot be null or empty");
			this.baseUrl = baseUrl;
			return this;
		}

		public Builder apiKey(String apiKey) {
			Assert.hasText(apiKey, "apiKey cannot be null or empty");
			this.apiKey = apiKey;
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

		public MistralAiApi build() {
			Assert.state(this.apiKey != null, "The API key must not be null");
			return new MistralAiApi(this.baseUrl, this.apiKey, this.restClientBuilder, this.webClientBuilder,
					this.responseErrorHandler);
		}

	}

}
