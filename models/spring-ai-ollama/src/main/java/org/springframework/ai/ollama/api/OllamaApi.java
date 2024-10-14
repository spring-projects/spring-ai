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
package org.springframework.ai.ollama.api;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.Assert;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Java Client for the Ollama API. <a href="https://ollama.ai/">https://ollama.ai</a>
 *
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @since 0.8.0
 */
// @formatter:off
public class OllamaApi {

	private static final Log logger = LogFactory.getLog(OllamaApi.class);

	private static final String DEFAULT_BASE_URL = "http://localhost:11434";

	public static final String PROVIDER_NAME = AiProvider.OLLAMA.value();

	public static final String REQUEST_BODY_NULL_ERROR = "The request body can not be null.";

	private final ResponseErrorHandler responseErrorHandler;

	private final RestClient restClient;

	private final WebClient webClient;

	private static class OllamaResponseErrorHandler implements ResponseErrorHandler {

		@Override
		public boolean hasError(ClientHttpResponse response) throws IOException {
			return response.getStatusCode().isError();
		}

		@Override
		public void handleError(ClientHttpResponse response) throws IOException {
			if (response.getStatusCode().isError()) {
				int statusCode = response.getStatusCode().value();
				String statusText = response.getStatusText();
				String message = StreamUtils.copyToString(response.getBody(), java.nio.charset.StandardCharsets.UTF_8);
				logger.warn(String.format("[%s] %s - %s", statusCode, statusText, message));
				throw new RuntimeException(String.format("[%s] %s - %s", statusCode, statusText, message));
			}
		}

	}

	/**
	 * Default constructor that uses the default localhost url.
	 */
	public OllamaApi() {
		this(DEFAULT_BASE_URL);
	}

	/**
	 * Crate a new OllamaApi instance with the given base url.
	 * @param baseUrl The base url of the Ollama server.
	 */
	public OllamaApi(String baseUrl) {
		this(baseUrl, RestClient.builder(), WebClient.builder());
	}

	/**
	 * Crate a new OllamaApi instance with the given base url and
	 * {@link RestClient.Builder}.
	 * @param baseUrl The base url of the Ollama server.
	 * @param restClientBuilder The {@link RestClient.Builder} to use.
	 */
	public OllamaApi(String baseUrl, RestClient.Builder restClientBuilder, WebClient.Builder webClientBuilder) {

		this.responseErrorHandler = new OllamaResponseErrorHandler();

		Consumer<HttpHeaders> defaultHeaders = headers -> {
			headers.setContentType(MediaType.APPLICATION_JSON);
			headers.setAccept(List.of(MediaType.APPLICATION_JSON));
		};

		this.restClient = restClientBuilder.baseUrl(baseUrl).defaultHeaders(defaultHeaders).build();

		this.webClient = webClientBuilder.baseUrl(baseUrl).defaultHeaders(defaultHeaders).build();
	}

	// --------------------------------------------------------------------------
	// Generate & Streaming Generate
	// --------------------------------------------------------------------------
	/**
	 * The request object sent to the /generate endpoint.
	 *
	 * @param model (required) The model to use for completion.
	 * @param prompt (required) The prompt(s) to generate completions for.
	 * @param format (optional) The format to return the response in. Currently, the only
	 * accepted value is "json".
	 * @param options (optional) additional model parameters listed in the documentation
	 * for the Model file such as temperature.
	 * @param system (optional) system prompt to (overrides what is defined in the Model file).
	 * @param template (optional) the full prompt or prompt template (overrides what is
	 *  defined in the Model file).
	 * @param context the context parameter returned from a previous request to /generate,
	 * this can be used to keep a short conversational memory.
	 * @param stream (optional) if false the response will be returned as a single
	 * response object, rather than a stream of objects.
	 * @param raw (optional) if true no formatting will be applied to the prompt and no
	 * context will be returned. You may choose to use the raw parameter if you are
	 * specifying a full templated prompt in your request to the API, and are managing
	 * history yourself.
	 * @param images (optional) a list of base64-encoded images (for multimodal models such as llava).
	 * @param keepAlive (optional) controls how long the model will stay loaded into memory following the request (default: 5m).
	 */
	@Deprecated(since = "1.0.0-M2", forRemoval = true)
	@JsonInclude(Include.NON_NULL)
	public record GenerateRequest(
			@JsonProperty("model") String model,
			@JsonProperty("prompt") String prompt,
			@JsonProperty("format") String format,
			@JsonProperty("options") Map<String, Object> options,
			@JsonProperty("system") String system,
			@JsonProperty("template") String template,
			@JsonProperty("context") List<Integer> context,
			@JsonProperty("stream") Boolean stream,
			@JsonProperty("raw") Boolean raw,
			@JsonProperty("images") List<String> images,
			@JsonProperty("keep_alive") String keepAlive) {

		/**
		 * Shortcut constructor to create a CompletionRequest without options.
		 * @param model The model used for completion.
		 * @param prompt The prompt(s) to generate completions for.
		 * @param stream Whether to stream the response.
		 */
		public GenerateRequest(String model, String prompt, Boolean stream) {
			this(model, prompt, null, null, null, null, null, stream, null, null, null);
		}

		/**
		 * Shortcut constructor to create a CompletionRequest without options.
		 * @param model The model used for completion.
		 * @param prompt The prompt(s) to generate completions for.
		 * @param enableJsonFormat Whether to return the response in json format.
		 * @param stream Whether to stream the response.
		 */
		public GenerateRequest(String model, String prompt, boolean enableJsonFormat, Boolean stream) {
			this(model, prompt, (enableJsonFormat) ? "json" : null, null, null, null, null, stream, null, null, null);
		}

		/**
		 * Create a CompletionRequest builder.
		 * @param prompt The prompt(s) to generate completions for.
		 */
		public static Builder builder(String prompt) {
			return new Builder(prompt);
		}

		public static class Builder {

			private String model;
			private final String prompt;
			private String format;
			private Map<String, Object> options;
			private String system;
			private String template;
			private List<Integer> context;
			private Boolean stream;
			private Boolean raw;
			private List<String> images;
			private String keepAlive;

			public Builder(String prompt) {
				this.prompt = prompt;
			}

			public Builder withModel(String model) {
				this.model = model;
				return this;
			}

			public Builder withFormat(String format) {
				this.format = format;
				return this;
			}

			public Builder withOptions(Map<String, Object> options) {
				this.options = options;
				return this;
			}

			public Builder withOptions(OllamaOptions options) {
				this.options = options.toMap();
				return this;
			}

			public Builder withSystem(String system) {
				this.system = system;
				return this;
			}

			public Builder withTemplate(String template) {
				this.template = template;
				return this;
			}

			public Builder withContext(List<Integer> context) {
				this.context = context;
				return this;
			}

			public Builder withStream(Boolean stream) {
				this.stream = stream;
				return this;
			}

			public Builder withRaw(Boolean raw) {
				this.raw = raw;
				return this;
			}

			public Builder withImages(List<String> images) {
				this.images = images;
				return this;
			}

			public Builder withKeepAlive(String keepAlive) {
				this.keepAlive = keepAlive;
				return this;
			}

			public GenerateRequest build() {
				return new GenerateRequest(model, prompt, format, options, system, template, context, stream, raw, images, keepAlive);
			}

		}
	}

	/**
	 * The response object returned from the /generate endpoint. To calculate how fast the
	 * response is generated in tokens per second (token/s), divide eval_count /
	 * eval_duration.
	 *
	 * @param model The model used for completion.
	 * @param createdAt When the request was made.
	 * @param response The completion response. Empty if the response was streamed, if not
	 * streamed, this will contain the full response
	 * @param done Whether this is the final response. If true, this response may be
	 * followed by another response with the following, additional fields: context,
	 * prompt_eval_count, prompt_eval_duration, eval_count, eval_duration.
	 * @param context Encoding of the conversation used in this response, this can be sent
	 * in the next request to keep a conversational memory.
	 * @param totalDuration Time spent generating the response.
	 * @param loadDuration Time spent loading the model.
	 * @param promptEvalCount Number of times the prompt was evaluated.
	 * @param promptEvalDuration Time spent evaluating the prompt.
	 * @param evalCount Number of tokens in the response.
	 * @param evalDuration Time spent generating the response.
	 */
	@Deprecated(since = "1.0.0-M2", forRemoval = true)
	@JsonInclude(Include.NON_NULL)
	public record GenerateResponse(
			@JsonProperty("model") String model,
			@JsonProperty("created_at") Instant createdAt,
			@JsonProperty("response") String response,
			@JsonProperty("done") Boolean done,
			@JsonProperty("context") List<Integer> context,
			@JsonProperty("total_duration") Duration totalDuration,
			@JsonProperty("load_duration") Duration loadDuration,
			@JsonProperty("prompt_eval_count") Integer promptEvalCount,
			@JsonProperty("prompt_eval_duration") Duration promptEvalDuration,
			@JsonProperty("eval_count") Integer evalCount,
			@JsonProperty("eval_duration") Duration evalDuration) {
	}

	/**
	 * Generate a completion for the given prompt.
	 * @param completionRequest Completion request.
	 * @return Completion response.
	 * @deprecated Use {@link #chat(ChatRequest)} instead.
	 */
	@Deprecated(since = "1.0.0-M2", forRemoval = true)
	public GenerateResponse generate(GenerateRequest completionRequest) {
		Assert.notNull(completionRequest, REQUEST_BODY_NULL_ERROR);
		Assert.isTrue(completionRequest.stream() == false, "Stream mode must be disabled.");

		return this.restClient.post()
			.uri("/api/generate")
			.body(completionRequest)
			.retrieve()
			.onStatus(this.responseErrorHandler)
			.body(GenerateResponse.class);
	}

	/**
	 * Generate a streaming completion for the given prompt.
	 * @param completionRequest Completion request. The request must set the stream
	 * property to true.
	 * @return Completion response as a {@link Flux} stream.
	 * @deprecated Use {@link #streamingChat(ChatRequest)} instead.
	 */
	@Deprecated(since = "1.0.0-M2", forRemoval = true)
	public Flux<GenerateResponse> generateStreaming(GenerateRequest completionRequest) {
		Assert.notNull(completionRequest, REQUEST_BODY_NULL_ERROR);
		Assert.isTrue(completionRequest.stream(), "Request must set the stream property to true.");

		return webClient.post()
			.uri("/api/generate")
			.body(Mono.just(completionRequest), GenerateRequest.class)
			.retrieve()
			.bodyToFlux(GenerateResponse.class)
			.handle((data, sink) -> {
				if (logger.isTraceEnabled()) {
					logger.trace(data);
				}
				sink.next(data);
			});
	}

	// --------------------------------------------------------------------------
	// Chat & Streaming Chat
	// --------------------------------------------------------------------------
	/**
	 * Chat message object.
	 *
	 * @param role The role of the message of type {@link Role}.
	 * @param content The content of the message.
	 * @param images The list of base64-encoded images to send with the message.
	 * 				 Requires multimodal models such as llava or bakllava.
	 */
	@JsonInclude(Include.NON_NULL)
	public record Message(
			@JsonProperty("role") Role role,
			@JsonProperty("content") String content,
			@JsonProperty("images") List<String> images,
			@JsonProperty("tool_calls") List<ToolCall> toolCalls) {

		/**
		 * The role of the message in the conversation.
		 */
		public enum Role {

			/**
			 * System message type used as instructions to the model.
			 */
			@JsonProperty("system") SYSTEM,
			/**
			 * User message type.
			 */
			@JsonProperty("user") USER,
			/**
			 * Assistant message type. Usually the response from the model.
			 */
			@JsonProperty("assistant") ASSISTANT,
			/**
			 * Tool message.
			 */
			@JsonProperty("tool") TOOL

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

		public static Builder builder(Role role) {
			return new Builder(role);
		}

		public static class Builder {

			private final Role role;
			private String content;
			private List<String> images;
			private List<ToolCall> toolCalls;

			public Builder(Role role) {
				this.role = role;
			}

			public Builder withContent(String content) {
				this.content = content;
				return this;
			}

			public Builder withImages(List<String> images) {
				this.images = images;
				return this;
			}

			public Builder withToolCalls(List<ToolCall> toolCalls) {
				this.toolCalls = toolCalls;
				return this;
			}

			public Message build() {
				return new Message(role, content, images, toolCalls);
			}

		}
	}

	/**
	 * Chat request object.
	 *
	 * @param model The model to use for completion. It should be a name familiar to Ollama from the <a href="https://ollama.com/library">Library</a>.
	 * @param messages The list of messages in the chat. This can be used to keep a chat memory.
	 * @param stream Whether to stream the response. If false, the response will be returned as a single response object rather than a stream of objects.
	 * @param format The format to return the response in. Currently, the only accepted value is "json".
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
			@JsonProperty("format") String format,
			@JsonProperty("keep_alive") String keepAlive,
			@JsonProperty("tools") List<Tool> tools,
			@JsonProperty("options") Map<String, Object> options
	) {

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
			@ConstructorBinding
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
				@JsonProperty("function") FUNCTION
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
				@ConstructorBinding
				public Function(String description, String name, String jsonSchema) {
					this(description, name, ModelOptionsUtils.jsonToMap(jsonSchema));
				}
			}
		}
		
		public static Builder builder(String model) {
			return new Builder(model);
		}

		public static class Builder {

			private final String model;
			private List<Message> messages = List.of();
			private boolean stream = false;
			private String format;
			private String keepAlive;
			private List<Tool> tools = List.of();
			private Map<String, Object> options = Map.of();

			public Builder(String model) {
				Assert.notNull(model, "The model can not be null.");
				this.model = model;
			}

			public Builder withMessages(List<Message> messages) {
				this.messages = messages;
				return this;
			}

			public Builder withStream(boolean stream) {
				this.stream = stream;
				return this;
			}

			public Builder withFormat(String format) {
				this.format = format;
				return this;
			}

			public Builder withKeepAlive(String keepAlive) {
				this.keepAlive = keepAlive;
				return this;
			}

			public Builder withTools(List<Tool> tools) {
				this.tools = tools;
				return this;
			}

			public Builder withOptions(Map<String, Object> options) {
				Objects.requireNonNull(options, "The options can not be null.");

				this.options = OllamaOptions.filterNonSupportedFields(options);
				return this;
			}

			public Builder withOptions(OllamaOptions options) {
				Objects.requireNonNull(options, "The options can not be null.");
				this.options = OllamaOptions.filterNonSupportedFields(options.toMap());
				return this;
			}

			public ChatRequest build() {
				return new ChatRequest(model, messages, stream, format, keepAlive, tools, options);
			}
		}
	}

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
	public record ChatResponse(
			@JsonProperty("model") String model,
			@JsonProperty("created_at") Instant createdAt,
			@JsonProperty("message") Message message,
			@JsonProperty("done_reason") String doneReason,
			@JsonProperty("done") Boolean done,
			@JsonProperty("total_duration") Duration totalDuration,
			@JsonProperty("load_duration") Duration loadDuration,
			@JsonProperty("prompt_eval_count") Integer promptEvalCount,
			@JsonProperty("prompt_eval_duration") Duration promptEvalDuration,
			@JsonProperty("eval_count") Integer evalCount,
			@JsonProperty("eval_duration") Duration evalDuration
	) {
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
			.onStatus(this.responseErrorHandler)
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

		return webClient.post()
			.uri("/api/chat")
			.body(Mono.just(chatRequest), GenerateRequest.class)
			.retrieve()
			.bodyToFlux(ChatResponse.class)
			.handle((data, sink) -> {
				if (logger.isTraceEnabled()) {
					logger.trace(data);
				}
				sink.next(data);
			});
	}

	// --------------------------------------------------------------------------
	// Embeddings
	// --------------------------------------------------------------------------
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
	 * Generate embeddings from a model.
	 *
	 * @param model The name of model to generate embeddings from.
	 * @param prompt The text generate embeddings for
	 * @param keepAlive Controls how long the model will stay loaded into memory following the request (default: 5m).
	 * @param options Additional model parameters listed in the documentation for the
	 * @deprecated Use {@link EmbeddingsRequest} instead.
	 */
	@Deprecated(since = "1.0.0-M2", forRemoval = true)
	@JsonInclude(Include.NON_NULL)
	public record EmbeddingRequest(
			@JsonProperty("model") String model,
			@JsonProperty("prompt") String prompt,
			@JsonProperty("keep_alive") Duration keepAlive,
			@JsonProperty("options") Map<String, Object> options) {

		/**
		 * Shortcut constructor to create a EmbeddingRequest without options.
		 * @param model The name of model to generate embeddings from.
		 * @param prompt The text to generate embeddings for.
		 */
		public EmbeddingRequest(String model, String prompt) {
			this(model, prompt, null, null);
		}
	}

	/**
	 * The response object returned from the /embedding endpoint.
	 *
	 * @param embedding The embedding generated from the model.
	 * @deprecated Use {@link EmbeddingsResponse} instead.
	 */
	@Deprecated(since = "1.0.0-M2", forRemoval = true)
	@JsonInclude(Include.NON_NULL)
	public record EmbeddingResponse(
			@JsonProperty("embedding") List<Float> embedding) {
	}


	/**
	 * The response object returned from the /embedding endpoint.
	 * @param model The model used for generating the embeddings.
	 * @param embeddings The list of embeddings generated from the model. 
	 * Each embedding (list of doubles) corresponds to a single input text.
	 */
	@JsonInclude(Include.NON_NULL)
	public record EmbeddingsResponse(
			@JsonProperty("model") String model,
			@JsonProperty("embeddings") List<float[]> embeddings,
			@JsonProperty("total_duration") Long totalDuration,
			@JsonProperty("load_duration") Long loadDuration,
			@JsonProperty("prompt_eval_count") Integer promptEvalCount) {
		
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
			.onStatus(this.responseErrorHandler)
			.body(EmbeddingsResponse.class);
	}
	/**
	 * Generate embeddings from a model.
	 * @param embeddingRequest Embedding request.
	 * @return Embedding response.
	 * @deprecated Use {@link #embed(EmbeddingsRequest)} instead.
	 */
	@Deprecated(since = "1.0.0-M2", forRemoval = true)
	public EmbeddingResponse embeddings(EmbeddingRequest embeddingRequest) {
		Assert.notNull(embeddingRequest, REQUEST_BODY_NULL_ERROR);

		return this.restClient.post()
			.uri("/api/embeddings")
			.body(embeddingRequest)
			.retrieve()
			.onStatus(this.responseErrorHandler)
			.body(EmbeddingResponse.class);
	}

	// --------------------------------------------------------------------------
	// Models
	// --------------------------------------------------------------------------

	@JsonInclude(Include.NON_NULL)
	public record Model(
			@JsonProperty("name") String name,
			@JsonProperty("model") String model,
			@JsonProperty("modified_at") Instant modifiedAt,
			@JsonProperty("size") Long size,
			@JsonProperty("digest") String digest,
			@JsonProperty("details") Details details
	) {
		@JsonInclude(Include.NON_NULL)
		public record Details(
				@JsonProperty("parent_model") String parentModel,
				@JsonProperty("format") String format,
				@JsonProperty("family") String family,
				@JsonProperty("families") List<String> families,
				@JsonProperty("parameter_size") String parameterSize,
				@JsonProperty("quantization_level") String quantizationLevel
		) {}
	}

	@JsonInclude(Include.NON_NULL)
	public record ListModelResponse(
			@JsonProperty("models") List<Model> models
	) {}

	/**
	 * List models that are available locally on the machine where Ollama is running.
	 */
	public ListModelResponse listModels() {
		return this.restClient.get()
				.uri("/api/tags")
				.retrieve()
				.onStatus(this.responseErrorHandler)
				.body(ListModelResponse.class);
	}

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
			@JsonProperty("modified_at") Instant modifiedAt
	) {}

	/**
	 * Show information about a model available locally on the machine where Ollama is running.
	 */
	public ShowModelResponse showModel(ShowModelRequest showModelRequest) {
		return this.restClient.post()
				.uri("/api/show")
				.body(showModelRequest)
				.retrieve()
				.onStatus(this.responseErrorHandler)
				.body(ShowModelResponse.class);
	}

	@JsonInclude(Include.NON_NULL)
	public record CopyModelRequest(
			@JsonProperty("source") String source,
			@JsonProperty("destination") String destination
	) {}

	/**
     * Copy a model. Creates a model with another name from an existing model.
     */
	public ResponseEntity<Void> copyModel(CopyModelRequest copyModelRequest) {
		return this.restClient.post()
				.uri("/api/copy")
				.body(copyModelRequest)
				.retrieve()
				.onStatus(this.responseErrorHandler)
				.toBodilessEntity();
	}

	@JsonInclude(Include.NON_NULL)
	public record DeleteModelRequest(
			@JsonProperty("model") String model
	) {}

	/**
	 * Delete a model and its data.
	 */
	public ResponseEntity<Void> deleteModel(DeleteModelRequest deleteModelRequest) {
		return this.restClient.method(HttpMethod.DELETE)
				.uri("/api/delete")
				.body(deleteModelRequest)
				.retrieve()
				.onStatus(this.responseErrorHandler)
				.toBodilessEntity();
	}

	@JsonInclude(Include.NON_NULL)
	public record PullModelRequest(
			@JsonProperty("model") String model,
			@JsonProperty("insecure") Boolean insecure,
			@JsonProperty("username") String username,
			@JsonProperty("password") String password,
			@JsonProperty("stream") Boolean stream
	) {
		public PullModelRequest {
			if (stream != null && stream) {
				logger.warn("Streaming when pulling models is not supported yet");
			}
			stream = false;
		}

		public PullModelRequest(String model) {
			this(model, null, null, null, null);
		}
	}

	@JsonInclude(Include.NON_NULL)
	public record ProgressResponse(
			@JsonProperty("status") String status,
			@JsonProperty("digest") String digest,
			@JsonProperty("total") Long total,
			@JsonProperty("completed") Long completed
	) {}

	/**
	 * Download a model from the Ollama library. Cancelled pulls are resumed from where they left off,
	 * and multiple calls will share the same download progress.
	 */
	public ProgressResponse pullModel(PullModelRequest pullModelRequest) {
		return this.restClient.post()
				.uri("/api/pull")
				.body(pullModelRequest)
				.retrieve()
				.onStatus(this.responseErrorHandler)
				.body(ProgressResponse.class);
	}

}
// @formatter:on