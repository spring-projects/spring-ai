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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.Assert;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Java Client for the Ollama API. https://ollama.ai/
 *
 * @author Christian Tzolov
 * @since 0.8.0
 */
// @formatter:off
public class OllamaApi {

	private static final Log logger = LogFactory.getLog(OllamaApi.class);

	private final static String DEFAULT_BASE_URL = "http://localhost:11434";

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
		this(baseUrl, RestClient.builder());
	}

	/**
	 * Crate a new OllamaApi instance with the given base url and
	 * {@link RestClient.Builder}.
	 * @param baseUrl The base url of the Ollama server.
	 * @param restClientBuilder The {@link RestClient.Builder} to use.
	 */
	public OllamaApi(String baseUrl, RestClient.Builder restClientBuilder) {

		this.responseErrorHandler = new OllamaResponseErrorHandler();

		Consumer<HttpHeaders> defaultHeaders = headers -> {
			headers.setContentType(MediaType.APPLICATION_JSON);
			headers.setAccept(List.of(MediaType.APPLICATION_JSON));
		};

		this.restClient = restClientBuilder.baseUrl(baseUrl).defaultHeaders(defaultHeaders).build();

		this.webClient = WebClient.builder().baseUrl(baseUrl).defaultHeaders(defaultHeaders).build();
	}

	// --------------------------------------------------------------------------
	// Generate & Streaming Generate
	// --------------------------------------------------------------------------
	/**
	 * The request object sent to the /generate endpoint.
	 *
	 * @param model (required) The model to use for completion.
	 * @param prompt (required) The prompt(s) to generate completions for.
	 * @param format (optional) The format to return the response in. Currently the only
	 * accepted value is "json".
	 * @param options (optional) additional model parameters listed in the documentation
	 * for the Modelfile such as temperature.
	 * @param system (optional) system prompt to (overrides what is defined in the Modelfile).
	 * @param template (optional) the full prompt or prompt template (overrides what is
	 *  defined in the Modelfile).
	 * @param context the context parameter returned from a previous request to /generate,
	 * this can be used to keep a short conversational memory.
	 * @param stream (optional) if false the response will be returned as a single
	 * response object, rather than a stream of objects.
	 * @param raw (optional) if true no formatting will be applied to the prompt and no
	 * context will be returned. You may choose to use the raw parameter if you are
	 * specifying a full templated prompt in your request to the API, and are managing
	 * history yourself.
	 */
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
			@JsonProperty("raw") Boolean raw) {

		/**
		 * Short cut constructor to create a CompletionRequest without options.
		 * @param model The model used for completion.
		 * @param prompt The prompt(s) to generate completions for.
		 * @param stream Whether to stream the response.
		 */
		public GenerateRequest(String model, String prompt, Boolean stream) {
			this(model, prompt, null, null, null, null, null, stream, null);
		}

		/**
		 * Short cut constructor to create a CompletionRequest without options.
		 * @param model The model used for completion.
		 * @param prompt The prompt(s) to generate completions for.
		 * @param enableJsonFormat Whether to return the response in json format.
		 * @param stream Whether to stream the response.
		 */
		public GenerateRequest(String model, String prompt, boolean enableJsonFormat, Boolean stream) {
			this(model, prompt, (enableJsonFormat) ? "json" : null, null, null, null, null, stream, null);
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

			public GenerateRequest build() {
				return new GenerateRequest(model, prompt, format, options, system, template, context, stream, raw);
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
	 */
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
	 */
	public Flux<GenerateResponse> generateStreaming(GenerateRequest completionRequest) {
		Assert.notNull(completionRequest, REQUEST_BODY_NULL_ERROR);
		Assert.isTrue(completionRequest.stream(), "Request must set the steam property to true.");

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
	 * @param images The list of images to send with the message.
	 */
	@JsonInclude(Include.NON_NULL)
	public record Message(
			@JsonProperty("role") Role role,
			@JsonProperty("content") String content,
			@JsonProperty("images") List<byte[]> images) {

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
			@JsonProperty("assistant") ASSISTANT;

		}

		public static Builder builder(Role role) {
			return new Builder(role);
		}

		public static class Builder {

			private final Role role;
			private String content;
			private List<byte[]> images;

			public Builder(Role role) {
				this.role = role;
			}

			public Builder withContent(String content) {
				this.content = content;
				return this;
			}

			public Builder withImages(List<byte[]> images) {
				this.images = images;
				return this;
			}

			public Message build() {
				return new Message(role, content, images);
			}

		}
	}

	/**
	 * Chat request object.
	 *
	 * @param model The model to use for completion.
	 * @param messages The list of messages to chat with.
	 * @param stream Whether to stream the response.
	 * @param format The format to return the response in. Currently the only accepted
	 * value is "json".
	 * @param options Additional model parameters. You can use the {@link OllamaOptions} builder
	 * to create the options then {@link OllamaOptions#toMap()} to convert the options into a
	 * map.
	 */
	@JsonInclude(Include.NON_NULL)
	public record ChatRequest(
			@JsonProperty("model") String model,
			@JsonProperty("messages") List<Message> messages,
			@JsonProperty("stream") Boolean stream,
			@JsonProperty("format") String format,
			@JsonProperty("options") Map<String, Object> options) {

		public static Builder builder(String model) {
			return new Builder(model);
		}

		public static class Builder {

			private final String model;
			private List<Message> messages = List.of();
			private boolean stream = false;
			private String format;
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

			public Builder withOptions(Map<String, Object> options) {
				Objects.requireNonNullElse(options, "The options can not be null.");

				this.options = OllamaOptions.filterNonSupportedFields(options);
				return this;
			}

			public Builder withOptions(OllamaOptions options) {
				Objects.requireNonNullElse(options, "The options can not be null.");
				this.options = OllamaOptions.filterNonSupportedFields(options.toMap());
				return this;
			}

			public ChatRequest build() {
				return new ChatRequest(model, messages, stream, format, options);
			}
		}
	}

	/**
	 * Ollama chat response object.
	 *
	 * @param model The model name used for completion.
	 * @param createdAt When the request was made.
	 * @param message The response {@link Message} with {@link Message.Role#ASSISTANT}.
	 * @param done Whether this is the final response. For streaming response only the
	 * last message is marked as done. If true, this response may be followed by another
	 * response with the following, additional fields: context, prompt_eval_count,
	 * prompt_eval_duration, eval_count, eval_duration.
	 * @param totalDuration Time spent generating the response.
	 * @param loadDuration Time spent loading the model.
	 * @param promptEvalCount number of tokens in the prompt.(*)
	 * @param promptEvalDuration time spent evaluating the prompt.
	 * @param evalCount number of tokens in the response.
	 * @param evalDuration time spent generating the response.
	 * @see <a href=
	 * "https://github.com/jmorganca/ollama/blob/main/docs/api.md#generate-a-chat-completion">Chat
	 * Completion API</a>
	 * @see <a href="https://github.com/jmorganca/ollama/blob/main/api/types.go">Ollama
	 * Types</a>
	 */
	@JsonInclude(Include.NON_NULL)
	public record ChatResponse(
			@JsonProperty("model") String model,
			@JsonProperty("created_at") Instant createdAt,
			@JsonProperty("message") Message message,
			@JsonProperty("done") Boolean done,
			@JsonProperty("total_duration") Duration totalDuration,
			@JsonProperty("load_duration") Duration loadDuration,
			@JsonProperty("prompt_eval_count") Integer promptEvalCount,
			@JsonProperty("prompt_eval_duration") Duration promptEvalDuration,
			@JsonProperty("eval_count") Integer evalCount,
			@JsonProperty("eval_duration") Duration evalDuration) {
	}

	/**
	 * Generate the next message in a chat with a provided model.
	 *
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
		Assert.isTrue(chatRequest.stream(), "Request must set the steam property to true.");

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
	 * @param prompt The text to generate embeddings for.
	 * @param options Additional model parameters listed in the documentation for the
	 * Modelfile such as temperature.
	 */
	@JsonInclude(Include.NON_NULL)
	public record EmbeddingRequest(
			@JsonProperty("model") String model,
			@JsonProperty("prompt") String prompt,
			@JsonProperty("options") Map<String, Object> options) {

		/**
		 * short cut constructor to create a EmbeddingRequest without options.
		 * @param model The name of model to generate embeddings from.
		 * @param prompt The text to generate embeddings for.
		 */
		public EmbeddingRequest(String model, String prompt) {
			this(model, prompt, null);
		}
	}

	/**
	 * The response object returned from the /embedding endpoint.
	 *
	 * @param embedding The embedding generated from the model.
	 */
	@JsonInclude(Include.NON_NULL)
	public record EmbeddingResponse(
			@JsonProperty("embedding") List<Double> embedding) {
	}

	/**
	 * Generate embeddings from a model.
	 * @param embeddingRequest Embedding request.
	 * @return Embedding response.
	 */
	public EmbeddingResponse embeddings(EmbeddingRequest embeddingRequest) {
		Assert.notNull(embeddingRequest, REQUEST_BODY_NULL_ERROR);

		return this.restClient.post()
			.uri("/api/embeddings")
			.body(embeddingRequest)
			.retrieve()
			.onStatus(this.responseErrorHandler)
			.body(EmbeddingResponse.class);
	}

}
// @formatter:on