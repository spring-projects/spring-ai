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

package org.springframework.ai.ollama.api;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.ollama.api.common.OllamaApiConstants;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Java Client for the Ollama API. <a href="https://ollama.ai/">https://ollama.ai</a>
 *
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Jonghoon Park
 * @since 0.8.0
 */
// @formatter:off
public class OllamaApi {

	public static Builder builder() { return new Builder(); }

	public static final String REQUEST_BODY_NULL_ERROR = "The request body can not be null.";

	private static final Log logger = LogFactory.getLog(OllamaApi.class);

	private final RestClient restClient;

	private final WebClient webClient;

	/**
	 * Create a new OllamaApi instance
	 * @param baseUrl The base url of the Ollama server.
	 * @param restClientBuilder The {@link RestClient.Builder} to use.
     * @param webClientBuilder The {@link WebClient.Builder} to use.
	 * @param responseErrorHandler Response error handler.
	 */
	private OllamaApi(String baseUrl, RestClient.Builder restClientBuilder, WebClient.Builder webClientBuilder, ResponseErrorHandler responseErrorHandler) {

		Consumer<HttpHeaders> defaultHeaders = headers -> {
			headers.setContentType(MediaType.APPLICATION_JSON);
			headers.setAccept(List.of(MediaType.APPLICATION_JSON));
		};

		this.restClient = restClientBuilder
				.clone()
				.baseUrl(baseUrl)
				.defaultHeaders(defaultHeaders)
				.defaultStatusHandler(responseErrorHandler)
				.build();

		this.webClient = webClientBuilder
				.clone()
				.baseUrl(baseUrl)
				.defaultHeaders(defaultHeaders)
				.build();
	}

	/**
	 * Generate the next message in a chat with a provided model.
	 * This is a streaming endpoint (controlled by the 'stream' request property), so
	 * there will be a series of responses. The final response object will include
	 * statistics and additional data from the request.
	 * @param chatRequest Chat request.
	 * @return Chat response.
	 */
	public ChatResponse chat(ChatRequest chatRequest) {
		Assert.notNull(chatRequest, REQUEST_BODY_NULL_ERROR);
		Assert.isTrue(!chatRequest.stream(), "Stream mode must be disabled.");

		return this.restClient.post()
			.uri("/api/chat")
			.body(chatRequest)
			.retrieve()
			.body(ChatResponse.class);
	}

	/**
	 * Streaming response for the chat completion request.
	 * @param chatRequest Chat request. The request must set the stream property to true.
	 * @return Chat response as a {@link Flux} stream.
	 */
	public Flux<ChatResponse> streamingChat(ChatRequest chatRequest) {
		Assert.notNull(chatRequest, REQUEST_BODY_NULL_ERROR);
		Assert.isTrue(chatRequest.stream(), "Request must set the stream property to true.");

		AtomicBoolean isInsideTool = new AtomicBoolean(false);

		return this.webClient.post()
			.uri("/api/chat")
			.body(Mono.just(chatRequest), ChatRequest.class)
			.retrieve()
			.bodyToFlux(ChatResponse.class)
			.map(chunk -> {
				if (OllamaApiHelper.isStreamingToolCall(chunk)) {
					isInsideTool.set(true);
				}
				return chunk;
			})
			// Group all chunks belonging to the same function call.
			// Flux<ChatChatResponse> -> Flux<Flux<ChatChatResponse>>
			.windowUntil(chunk -> {
				if (isInsideTool.get() && OllamaApiHelper.isStreamingDone(chunk)) {
					isInsideTool.set(false);
					return true;
				}
				return !isInsideTool.get();
			})
			// Merging the window chunks into a single chunk.
			// Reduce the inner Flux<ChatChatResponse> window into a single
			// Mono<ChatChatResponse>,
			// Flux<Flux<ChatChatResponse>> -> Flux<Mono<ChatChatResponse>>
			.concatMapIterable(window -> {
				Mono<ChatResponse> monoChunk = window.reduce(
						new ChatResponse(),
						(previous, current) -> OllamaApiHelper.merge(previous, current));
				return List.of(monoChunk);
			})
			// Flux<Mono<ChatChatResponse>> -> Flux<ChatChatResponse>
			.flatMap(mono -> mono)
			.handle((data, sink) -> {
				if (logger.isTraceEnabled()) {
					logger.trace(data);
				}
				sink.next(data);
			});
	}

	/**
	 * Generate embeddings from a model.
	 * @param embeddingsRequest Embedding request.
	 * @return Embeddings response.
	 */
	public EmbeddingsResponse embed(EmbeddingsRequest embeddingsRequest) {
		Assert.notNull(embeddingsRequest, REQUEST_BODY_NULL_ERROR);

		return this.restClient.post()
			.uri("/api/embed")
			.body(embeddingsRequest)
			.retrieve()
			.body(EmbeddingsResponse.class);
	}

	/**
	 * List models that are available locally on the machine where Ollama is running.
	 */
	public ListModelResponse listModels() {
		return this.restClient.get()
				.uri("/api/tags")
				.retrieve()
				.body(ListModelResponse.class);
	}

	/**
	 * Show information about a model available locally on the machine where Ollama is running.
	 */
	public ShowModelResponse showModel(ShowModelRequest showModelRequest) {
		Assert.notNull(showModelRequest, "showModelRequest must not be null");
		return this.restClient.post()
				.uri("/api/show")
				.body(showModelRequest)
				.retrieve()
				.body(ShowModelResponse.class);
	}

	/**
     * Copy a model. Creates a model with another name from an existing model.
     */
	public ResponseEntity<Void> copyModel(CopyModelRequest copyModelRequest) {
		Assert.notNull(copyModelRequest, "copyModelRequest must not be null");
		return this.restClient.post()
				.uri("/api/copy")
				.body(copyModelRequest)
				.retrieve()
				.toBodilessEntity();
	}

	/**
	 * Delete a model and its data.
	 */
	public ResponseEntity<Void> deleteModel(DeleteModelRequest deleteModelRequest) {
		Assert.notNull(deleteModelRequest, "deleteModelRequest must not be null");
		return this.restClient.method(HttpMethod.DELETE)
				.uri("/api/delete")
				.body(deleteModelRequest)
				.retrieve()
				.toBodilessEntity();
	}

	// --------------------------------------------------------------------------
	// Embeddings
	// --------------------------------------------------------------------------

	/**
	 * Download a model from the Ollama library. Cancelled pulls are resumed from where they left off,
	 * and multiple calls will share the same download progress.
	 */
	public Flux<ProgressResponse> pullModel(PullModelRequest pullModelRequest) {
		Assert.notNull(pullModelRequest, "pullModelRequest must not be null");
		Assert.isTrue(pullModelRequest.stream(), "Request must set the stream property to true.");

		return this.webClient.post()
				.uri("/api/pull")
				.bodyValue(pullModelRequest)
				.retrieve()
				.bodyToFlux(ProgressResponse.class);
	}

	/**
	 * Chat message object.
	 *
	 * @param role The role of the message of type {@link Role}.
	 * @param content The content of the message.
	 * @param images The list of base64-encoded images to send with the message.
	 * 				 Requires multimodal models such as llava or bakllava.
	 * @param toolCalls The relevant tool call.
	 */
	@JsonInclude(Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Message(
			@JsonProperty("role") Role role,
			@JsonProperty("content") String content,
			@JsonProperty("images") List<String> images,
			@JsonProperty("tool_calls") List<ToolCall> toolCalls) {

		public static Builder builder(Role role) {
			return new Builder(role);
		}

		/**
		 * The role of the message in the conversation.
		 */
		public enum Role {

			/**
			 * System message type used as instructions to the model.
			 */
			@JsonProperty("system")
			SYSTEM,
			/**
			 * User message type.
			 */
			@JsonProperty("user")
			USER,
			/**
			 * Assistant message type. Usually the response from the model.
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
		 * The relevant tool call.
		 *
		 * @param function The function definition.
		 */
		@JsonInclude(Include.NON_NULL)
		public record ToolCall(
			@JsonProperty("function") ToolCallFunction function) {
		}

		/**
		 * The function definition.
		 *
		 * @param name The name of the function.
		 * @param arguments The arguments that the model expects you to pass to the function.
		 */
		@JsonInclude(Include.NON_NULL)
		public record ToolCallFunction(
			@JsonProperty("name") String name,
			@JsonProperty("arguments") Map<String, Object> arguments) {
		}

		public static class Builder {

			private final Role role;
			private String content;
			private List<String> images;
			private List<ToolCall> toolCalls;

			public Builder(Role role) {
				this.role = role;
			}

			public Builder content(String content) {
				this.content = content;
				return this;
			}

			public Builder images(List<String> images) {
				this.images = images;
				return this;
			}

			public Builder toolCalls(List<ToolCall> toolCalls) {
				this.toolCalls = toolCalls;
				return this;
			}

			public Message build() {
				return new Message(this.role, this.content, this.images, this.toolCalls);
			}
		}
	}

	/**
	 * Chat request object.
	 *
	 * @param model The model to use for completion. It should be a name familiar to Ollama from the <a href="https://ollama.com/library">Library</a>.
	 * @param messages The list of messages in the chat. This can be used to keep a chat memory.
	 * @param stream Whether to stream the response. If false, the response will be returned as a single response object rather than a stream of objects.
	 * @param format The format to return the response in. It can either be the String "json" or a Map containing a JSON Schema definition.
	 * @param keepAlive Controls how long the model will stay loaded into memory following this request (default: 5m).
	 * @param tools List of tools the model has access to.
	 * @param options Model-specific options. For example, "temperature" can be set through this field, if the model supports it.
	 * You can use the {@link OllamaOptions} builder to create the options then {@link OllamaOptions#toMap()} to convert the options into a map.
	 *
	 * @see <a href=
	 * "https://github.com/ollama/ollama/blob/main/docs/api.md#generate-a-chat-completion">Chat
	 * Completion API</a>
	 * @see <a href="https://github.com/ollama/ollama/blob/main/api/types.go">Ollama
	 * Types</a>
	 */
	@JsonInclude(Include.NON_NULL)
	public record ChatRequest(
			@JsonProperty("model") String model,
			@JsonProperty("messages") List<Message> messages,
			@JsonProperty("stream") Boolean stream,
			@JsonProperty("format") Object format,
			@JsonProperty("keep_alive") String keepAlive,
			@JsonProperty("tools") List<Tool> tools,
			@JsonProperty("options") Map<String, Object> options
	) {

		public static Builder builder(String model) {
			return new Builder(model);
		}

		/**
		 * Represents a tool the model may call. Currently, only functions are supported as a tool.
		 *
		 * @param type The type of the tool. Currently, only 'function' is supported.
		 * @param function The function definition.
		 */
		@JsonInclude(Include.NON_NULL)
		public record Tool(
				@JsonProperty("type") Type type,
				@JsonProperty("function") Function function) {

			/**
			 * Create a tool of type 'function' and the given function definition.
			 * @param function function definition.
			 */
			public Tool(Function function) {
				this(Type.FUNCTION, function);
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
			 *
			 * @param name The name of the function to be called. Must be a-z, A-Z, 0-9, or contain underscores and dashes.
			 * @param description A description of what the function does, used by the model to choose when and how to call
			 * the function.
			 * @param parameters The parameters the functions accepts, described as a JSON Schema object. To describe a
			 * function that accepts no parameters, provide the value {"type": "object", "properties": {}}.
			 */
			public record Function(
				@JsonProperty("name") String name,
				@JsonProperty("description") String description,
				@JsonProperty("parameters") Map<String, Object> parameters) {

				/**
				 * Create tool function definition.
				 *
				 * @param description tool function description.
				 * @param name tool function name.
				 * @param jsonSchema tool function schema as json.
				 */
				public Function(String description, String name, String jsonSchema) {
					this(description, name, ModelOptionsUtils.jsonToMap(jsonSchema));
				}
			}
		}

		public static class Builder {

			private final String model;
			private List<Message> messages = List.of();
			private boolean stream = false;
			private Object format;
			private String keepAlive;
			private List<Tool> tools = List.of();
			private Map<String, Object> options = Map.of();

			public Builder(String model) {
				Assert.notNull(model, "The model can not be null.");
				this.model = model;
			}

			public Builder messages(List<Message> messages) {
				this.messages = messages;
				return this;
			}

			public Builder stream(boolean stream) {
				this.stream = stream;
				return this;
			}

			public Builder format(Object format) {
				this.format = format;
				return this;
			}

			public Builder keepAlive(String keepAlive) {
				this.keepAlive = keepAlive;
				return this;
			}

			public Builder tools(List<Tool> tools) {
				this.tools = tools;
				return this;
			}

			public Builder options(Map<String, Object> options) {
				Objects.requireNonNull(options, "The options can not be null.");

				this.options = OllamaOptions.filterNonSupportedFields(options);
				return this;
			}

			public Builder options(OllamaOptions options) {
				Objects.requireNonNull(options, "The options can not be null.");
				this.options = OllamaOptions.filterNonSupportedFields(options.toMap());
				return this;
			}

			public ChatRequest build() {
				return new ChatRequest(this.model, this.messages, this.stream, this.format, this.keepAlive, this.tools, this.options);
			}
		}
	}

	// --------------------------------------------------------------------------
	// Models
	// --------------------------------------------------------------------------

	/**
	 * Ollama chat response object.
	 *
	 * @param model The model used for generating the response.
	 * @param createdAt The timestamp of the response generation.
	 * @param message The response {@link Message} with {@link Message.Role#ASSISTANT}.
	 * @param doneReason The reason the model stopped generating text.
	 * @param done Whether this is the final response. For streaming response only the
	 * last message is marked as done. If true, this response may be followed by another
	 * response with the following, additional fields: context, prompt_eval_count,
	 * prompt_eval_duration, eval_count, eval_duration.
	 * @param totalDuration Time spent generating the response.
	 * @param loadDuration Time spent loading the model.
	 * @param promptEvalCount Number of tokens in the prompt.
	 * @param promptEvalDuration Time spent evaluating the prompt.
	 * @param evalCount Number of tokens in the response.
	 * @param evalDuration Time spent generating the response.
	 *
	 * @see <a href=
	 * "https://github.com/ollama/ollama/blob/main/docs/api.md#generate-a-chat-completion">Chat
	 * Completion API</a>
	 * @see <a href="https://github.com/ollama/ollama/blob/main/api/types.go">Ollama
	 * Types</a>
	 */
	@JsonInclude(Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ChatResponse(
			@JsonProperty("model") String model,
			@JsonProperty("created_at") Instant createdAt,
			@JsonProperty("message") Message message,
			@JsonProperty("done_reason") String doneReason,
			@JsonProperty("done") Boolean done,
			@JsonProperty("total_duration") Long totalDuration,
			@JsonProperty("load_duration") Long loadDuration,
			@JsonProperty("prompt_eval_count") Integer promptEvalCount,
			@JsonProperty("prompt_eval_duration") Long promptEvalDuration,
			@JsonProperty("eval_count") Integer evalCount,
			@JsonProperty("eval_duration") Long evalDuration
	) {
		ChatResponse() {
			this(null, null, null, null, null, null, null, null, null, null, null);
		}

		public Duration getTotalDuration() {
			return (this.totalDuration() != null) ? Duration.ofNanos(this.totalDuration()) : null;
		}

		public Duration getLoadDuration() {
			return (this.loadDuration() != null) ? Duration.ofNanos(this.loadDuration()) : null;
		}

		public Duration getPromptEvalDuration() {
			return (this.promptEvalDuration() != null) ? Duration.ofNanos(this.promptEvalDuration()) : null;
		}

		public Duration getEvalDuration() {
			if (this.evalDuration() == null) {
				return null;
			}
			return Duration.ofNanos(this.evalDuration());
			// return (this.evalDuration() != null)? Duration.ofNanos(this.evalDuration()) : null;
		}
	}

	/**
	 * Generate embeddings from a model.
	 *
	 * @param model The name of model to generate embeddings from.
	 * @param input The text or list of text to generate embeddings for.
	 * @param keepAlive Controls how long the model will stay loaded into memory following the request (default: 5m).
	 * @param options Additional model parameters listed in the documentation for the
	 * @param truncate Truncates the end of each input to fit within context length.
	 *  Returns error if false and context length is exceeded. Defaults to true.
	 */
	@JsonInclude(Include.NON_NULL)
	public record EmbeddingsRequest(
			@JsonProperty("model") String model,
			@JsonProperty("input") List<String> input,
			@JsonProperty("keep_alive") Duration keepAlive,
			@JsonProperty("options") Map<String, Object> options,
			@JsonProperty("truncate") Boolean truncate) {

		/**
		 * Shortcut constructor to create a EmbeddingRequest without options.
		 * @param model The name of model to generate embeddings from.
		 * @param input The text or list of text to generate embeddings for.
		 */
		public EmbeddingsRequest(String model, String input) {
			this(model, List.of(input), null, null, null);
		}
	}

	/**
	 * The response object returned from the /embedding endpoint.
	 * @param model The model used for generating the embeddings.
	 * @param embeddings The list of embeddings generated from the model.
	 * Each embedding (list of doubles) corresponds to a single input text.
	 * @param totalDuration The total time spent generating the embeddings.
	 * @param loadDuration The time spent loading the model.
	 * @param promptEvalCount The number of tokens in the prompt.
	 */
	@JsonInclude(Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record EmbeddingsResponse(
			@JsonProperty("model") String model,
			@JsonProperty("embeddings") List<float[]> embeddings,
			@JsonProperty("total_duration") Long totalDuration,
			@JsonProperty("load_duration") Long loadDuration,
			@JsonProperty("prompt_eval_count") Integer promptEvalCount) {

	}

	@JsonInclude(Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Model(
			@JsonProperty("name") String name,
			@JsonProperty("model") String model,
			@JsonProperty("modified_at") Instant modifiedAt,
			@JsonProperty("size") Long size,
			@JsonProperty("digest") String digest,
			@JsonProperty("details") Details details
	) {
		@JsonInclude(Include.NON_NULL)
		@JsonIgnoreProperties(ignoreUnknown = true)
		public record Details(
				@JsonProperty("parent_model") String parentModel,
				@JsonProperty("format") String format,
				@JsonProperty("family") String family,
				@JsonProperty("families") List<String> families,
				@JsonProperty("parameter_size") String parameterSize,
				@JsonProperty("quantization_level") String quantizationLevel
		) { }
	}

	@JsonInclude(Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ListModelResponse(
			@JsonProperty("models") List<Model> models
	) { }

	@JsonInclude(Include.NON_NULL)
	public record ShowModelRequest(
			@JsonProperty("model") String model,
			@JsonProperty("system") String system,
			@JsonProperty("verbose") Boolean verbose,
			@JsonProperty("options") Map<String, Object> options
	) {
		public ShowModelRequest(String model) {
			this(model, null, null, null);
		}
	}

	@JsonInclude(Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ShowModelResponse(
			@JsonProperty("license") String license,
			@JsonProperty("modelfile") String modelfile,
			@JsonProperty("parameters") String parameters,
			@JsonProperty("template") String template,
			@JsonProperty("system") String system,
			@JsonProperty("details") Model.Details details,
			@JsonProperty("messages") List<Message> messages,
			@JsonProperty("model_info") Map<String, Object> modelInfo,
			@JsonProperty("projector_info") Map<String, Object> projectorInfo,
			@JsonProperty("capabilities") List<String> capabilities,
			@JsonProperty("modified_at") Instant modifiedAt
	) { }

	@JsonInclude(Include.NON_NULL)
	public record CopyModelRequest(
			@JsonProperty("source") String source,
			@JsonProperty("destination") String destination
	) { }

	@JsonInclude(Include.NON_NULL)
	public record DeleteModelRequest(
			@JsonProperty("model") String model
	) { }

	@JsonInclude(Include.NON_NULL)
	public record PullModelRequest(
			@JsonProperty("model") String model,
			@JsonProperty("insecure") boolean insecure,
			@JsonProperty("username") String username,
			@JsonProperty("password") String password,
			@JsonProperty("stream") boolean stream
	) {
		public PullModelRequest {
			if (!stream) {
				logger.warn("Enforcing streaming of the model pull request");
			}
			stream = true;
		}

		public PullModelRequest(String model) {
			this(model, false, null, null, true);
		}
	}

	@JsonInclude(Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ProgressResponse(
			@JsonProperty("status") String status,
			@JsonProperty("digest") String digest,
			@JsonProperty("total") Long total,
			@JsonProperty("completed") Long completed
	) { }

	public static class Builder {

		private String baseUrl = OllamaApiConstants.DEFAULT_BASE_URL;

		private RestClient.Builder restClientBuilder = RestClient.builder();

		private WebClient.Builder webClientBuilder = WebClient.builder();

		private ResponseErrorHandler responseErrorHandler = RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER;

		public Builder baseUrl(String baseUrl) {
			Assert.hasText(baseUrl, "baseUrl cannot be null or empty");
			this.baseUrl = baseUrl;
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

		public OllamaApi build() {
			return new OllamaApi(this.baseUrl, this.restClientBuilder, this.webClientBuilder, this.responseErrorHandler);
		}

	}
}
// @formatter:on
