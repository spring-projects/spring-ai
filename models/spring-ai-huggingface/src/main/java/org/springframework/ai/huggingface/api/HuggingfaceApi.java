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

package org.springframework.ai.huggingface.api;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.ai.huggingface.api.common.HuggingfaceApiConstants;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;

/**
 * Java Client for the HuggingFace Inference API. Supports both Chat and Embedding
 * endpoints. <a href= "https://huggingface.co/docs/inference-providers/index">HuggingFace
 * Inference API</a>
 *
 * @author Myeongdeok Kang
 */
public final class HuggingfaceApi {

	// API Endpoint Paths
	public static final String CHAT_COMPLETIONS_PATH = "/chat/completions";

	public static final String EMBEDDING_PATH_TEMPLATE = "/%s/pipeline/feature-extraction";

	// Default Models
	public static final String DEFAULT_CHAT_MODEL = "meta-llama/Llama-3.2-3B-Instruct";

	public static final String DEFAULT_EMBEDDING_MODEL = "sentence-transformers/all-MiniLM-L6-v2";

	private static final String REQUEST_BODY_NULL_ERROR = "The request body cannot be null.";

	private final RestClient restClient;

	/**
	 * Create a new HuggingfaceApi instance.
	 * @param baseUrl The base URL of the HuggingFace Inference API.
	 * @param apiKey The HuggingFace API key for authentication.
	 * @param restClientBuilder The {@link RestClient.Builder} to use.
	 * @param responseErrorHandler Response error handler.
	 */
	private HuggingfaceApi(String baseUrl, String apiKey, RestClient.Builder restClientBuilder,
			ResponseErrorHandler responseErrorHandler) {
		Assert.hasText(baseUrl, "baseUrl must not be empty");
		Assert.hasText(apiKey, "apiKey must not be empty");

		Consumer<HttpHeaders> defaultHeaders = headers -> {
			headers.setContentType(MediaType.APPLICATION_JSON);
			headers.setAccept(List.of(MediaType.APPLICATION_JSON));
			headers.setBearerAuth(apiKey);
		};

		RestClient.Builder builder = restClientBuilder.clone().baseUrl(baseUrl).defaultHeaders(defaultHeaders);

		if (responseErrorHandler != null) {
			builder.defaultStatusHandler(responseErrorHandler);
		}

		this.restClient = builder.build();
	}

	/**
	 * Generate chat completion using the specified model (OpenAI-compatible endpoint).
	 * Supports parameters from the Chat Completion API specification:
	 * https://huggingface.co/docs/inference-providers/tasks/chat-completion
	 * @param chatRequest Chat request containing the model, messages, and optional
	 * parameters (temperature, max_tokens, top_p, frequency_penalty, presence_penalty,
	 * stop, seed, response_format, tool_prompt, logprobs, top_logprobs, etc.)
	 * @return Chat response containing the generated text.
	 */
	public ChatResponse chat(ChatRequest chatRequest) {
		Assert.notNull(chatRequest, REQUEST_BODY_NULL_ERROR);
		Assert.hasText(chatRequest.model(), "Model must not be empty");
		Assert.notEmpty(chatRequest.messages(), "Messages must not be empty");

		// OpenAI-compatible chat completions endpoint
		ResponseEntity<ChatResponse> responseEntity = this.restClient.post()
			.uri(CHAT_COMPLETIONS_PATH)
			.body(chatRequest)
			.retrieve()
			.toEntity(ChatResponse.class);

		ChatResponse response = responseEntity.getBody();
		if (response == null) {
			throw new IllegalStateException("No response returned from HuggingFace API");
		}

		return response;
	}

	/**
	 * Generate embeddings from a model using the Feature Extraction pipeline.
	 * @param embeddingsRequest Embedding request containing the model and inputs.
	 * @return Embeddings response containing the generated embeddings.
	 */
	public EmbeddingsResponse embeddings(EmbeddingsRequest embeddingsRequest) {
		Assert.notNull(embeddingsRequest, REQUEST_BODY_NULL_ERROR);
		Assert.hasText(embeddingsRequest.model(), "Model must not be empty");
		Assert.notEmpty(embeddingsRequest.inputs(), "Inputs must not be empty");

		// HuggingFace Inference API endpoint for feature extraction
		String uri = String.format(EMBEDDING_PATH_TEMPLATE, embeddingsRequest.model());

		ResponseEntity<float[][]> responseEntity = this.restClient.post()
			.uri(uri)
			.body(new EmbeddingsRequestBody(embeddingsRequest.inputs(), embeddingsRequest.options()))
			.retrieve()
			.toEntity(float[][].class);

		float[][] embeddings = responseEntity.getBody();
		if (embeddings == null || embeddings.length == 0) {
			throw new IllegalStateException("No embeddings returned from HuggingFace API");
		}

		// Convert float[][] to List<float[]> for consistency with other implementations
		return new EmbeddingsResponse(embeddingsRequest.model(), Arrays.asList(embeddings));
	}

	/**
	 * Create a new builder for HuggingfaceApi.
	 * @return A new builder instance.
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builder for creating HuggingfaceApi instances.
	 */
	public static final class Builder {

		private String baseUrl = HuggingfaceApiConstants.DEFAULT_CHAT_BASE_URL;

		private String apiKey;

		private RestClient.Builder restClientBuilder = RestClient.builder();

		private ResponseErrorHandler responseErrorHandler;

		private Builder() {
		}

		/**
		 * Set the base URL for the HuggingFace Inference API.
		 * @param baseUrl The base URL.
		 * @return This builder.
		 */
		public Builder baseUrl(String baseUrl) {
			this.baseUrl = baseUrl;
			return this;
		}

		/**
		 * Set the API key for authentication.
		 * @param apiKey The HuggingFace API key.
		 * @return This builder.
		 */
		public Builder apiKey(String apiKey) {
			this.apiKey = apiKey;
			return this;
		}

		/**
		 * Set the RestClient.Builder to use.
		 * @param restClientBuilder The RestClient.Builder.
		 * @return This builder.
		 */
		public Builder restClientBuilder(RestClient.Builder restClientBuilder) {
			this.restClientBuilder = restClientBuilder;
			return this;
		}

		/**
		 * Set the response error handler.
		 * @param responseErrorHandler The error handler.
		 * @return This builder.
		 */
		public Builder responseErrorHandler(ResponseErrorHandler responseErrorHandler) {
			this.responseErrorHandler = responseErrorHandler;
			return this;
		}

		/**
		 * Build the HuggingfaceApi instance.
		 * @return A new HuggingfaceApi instance.
		 */
		public HuggingfaceApi build() {
			return new HuggingfaceApi(this.baseUrl, this.apiKey, this.restClientBuilder, this.responseErrorHandler);
		}

	}

	/**
	 * Chat request for HuggingFace Inference API.
	 *
	 * @param model The name of the model to use for chat.
	 * @param messages The list of messages in the conversation.
	 * @param tools A list of tools the model may call. Currently, only functions are
	 * supported as a tool.
	 * @param toolChoice Controls which (if any) function is called by the model.
	 * @param options Additional options for the chat request (optional).
	 */
	@JsonInclude(Include.NON_NULL)
	public record ChatRequest(@JsonProperty("model") String model, @JsonProperty("messages") List<Message> messages,
			@JsonProperty("tools") List<FunctionTool> tools, @JsonProperty("tool_choice") Object toolChoice,
			@JsonProperty("options") Map<String, Object> options) {

		/**
		 * Shortcut constructor without options.
		 * @param model The model name.
		 * @param messages The messages.
		 */
		public ChatRequest(String model, List<Message> messages) {
			this(model, messages, null, null, null);
		}

		/**
		 * Constructor with options but no tools.
		 * @param model The model name.
		 * @param messages The messages.
		 * @param options Additional options.
		 */
		public ChatRequest(String model, List<Message> messages, Map<String, Object> options) {
			this(model, messages, null, null, options);
		}

		/**
		 * Constructor with tools and tool choice.
		 * @param model The model name.
		 * @param messages The messages.
		 * @param tools The list of tools.
		 * @param toolChoice Controls which function is called.
		 */
		public ChatRequest(String model, List<Message> messages, List<FunctionTool> tools, Object toolChoice) {
			this(model, messages, tools, toolChoice, null);
		}

		/**
		 * Constructor with tools, tool choice, and additional options.
		 * @param model The model name.
		 * @param messages The messages.
		 * @param tools The list of tools.
		 * @param toolChoice Controls which function is called.
		 * @param options Additional options.
		 */
		public ChatRequest(String model, List<Message> messages, List<FunctionTool> tools, Object toolChoice,
				Map<String, Object> options) {
			this.model = model;
			this.messages = messages;
			this.tools = tools;
			this.toolChoice = toolChoice;
			this.options = options;
		}

	}

	/**
	 * Chat message.
	 *
	 * @param role The role of the message sender (system, user, assistant, tool).
	 * @param content The content of the message.
	 * @param name An optional name for the participant. In case of function calling, the
	 * name is the function name that the message is responding to.
	 * @param toolCallId Tool call that this message is responding to. Only applicable for
	 * the tool role.
	 * @param toolCalls The tool calls generated by the model, such as function calls.
	 * Applicable only for assistant role.
	 */
	@JsonInclude(Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Message(@JsonProperty("role") String role, @JsonProperty("content") String content,
			@JsonProperty("name") String name, @JsonProperty("tool_call_id") String toolCallId,
			@JsonProperty("tool_calls") @JsonFormat(
					with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY) List<ToolCall> toolCalls) {

		/**
		 * Create a simple message with role and content.
		 * @param role The role of the message sender.
		 * @param content The content of the message.
		 */
		public Message(String role, String content) {
			this(role, content, null, null, null);
		}

		/**
		 * Create a tool response message.
		 * @param content The content of the tool response.
		 * @param role The role (should be "tool").
		 * @param name The function name.
		 * @param toolCallId The tool call ID this message responds to.
		 */
		public Message(String content, String role, String name, String toolCallId) {
			this(role, content, name, toolCallId, null);
		}

		/**
		 * Create an assistant message with tool calls.
		 * @param role The role (should be "assistant").
		 * @param content The content of the message.
		 * @param toolCalls The tool calls generated by the model.
		 */
		public Message(String role, String content, List<ToolCall> toolCalls) {
			this(role, content, null, null, toolCalls);
		}

	}

	/**
	 * Chat response from HuggingFace Inference API (OpenAI-compatible).
	 *
	 * @param id Unique identifier for the chat completion.
	 * @param object Object type, always "chat.completion".
	 * @param created Unix timestamp of when the chat completion was created.
	 * @param model The model used for generating the response.
	 * @param choices The list of generated choices.
	 * @param usage Token usage information (optional).
	 * @param systemFingerprint System fingerprint (optional).
	 */
	@JsonInclude(Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ChatResponse(@JsonProperty("id") String id, @JsonProperty("object") String object,
			@JsonProperty("created") Long created, @JsonProperty("model") String model,
			@JsonProperty("choices") List<Choice> choices, @JsonProperty("usage") Usage usage,
			@JsonProperty("system_fingerprint") String systemFingerprint) {
	}

	/**
	 * A chat completion choice.
	 *
	 * @param index The index of the choice.
	 * @param message The generated message.
	 * @param finishReason The reason the generation stopped.
	 */
	@JsonInclude(Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Choice(@JsonProperty("index") Integer index, @JsonProperty("message") Message message,
			@JsonProperty("finish_reason") String finishReason) {
	}

	/**
	 * Token usage information.
	 *
	 * @param promptTokens Number of tokens in the prompt.
	 * @param completionTokens Number of tokens in the completion.
	 * @param totalTokens Total number of tokens.
	 */
	@JsonInclude(Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Usage(@JsonProperty("prompt_tokens") Integer promptTokens,
			@JsonProperty("completion_tokens") Integer completionTokens,
			@JsonProperty("total_tokens") Integer totalTokens) {
	}

	/**
	 * Embedding request for HuggingFace Inference API.
	 *
	 * @param model The name of the model to use for embeddings (e.g.,
	 * "sentence-transformers/all-MiniLM-L6-v2").
	 * @param inputs The list of text inputs to generate embeddings for.
	 * @param options Additional options for the embedding request (optional).
	 */
	@JsonInclude(Include.NON_NULL)
	public record EmbeddingsRequest(@JsonProperty("model") String model, @JsonProperty("inputs") List<String> inputs,
			@JsonProperty("options") Map<String, Object> options) {

		/**
		 * Shortcut constructor without options.
		 * @param model The model name.
		 * @param inputs The text inputs.
		 */
		public EmbeddingsRequest(String model, List<String> inputs) {
			this(model, inputs, null);
		}

	}

	/**
	 * Internal request body sent to the HuggingFace API for embeddings. The API doesn't
	 * expect a "model" field in the body since it's in the URL path.
	 * <p>
	 * Options are flattened at the top level alongside inputs, as per the HuggingFace API
	 * specification. Example request body: <pre>
	 * {
	 *   "inputs": ["text1", "text2"],
	 *   "normalize": true,
	 *   "dimensions": 256,
	 *   "prompt_name": "query"
	 * }
	 * </pre>
	 *
	 * @param inputs The text inputs.
	 * @param options Additional options (dimensions, normalize, prompt_name, etc.) that
	 * get flattened at the top level.
	 */
	@JsonInclude(Include.NON_NULL)
	record EmbeddingsRequestBody(@JsonProperty("inputs") List<String> inputs,
			@com.fasterxml.jackson.annotation.JsonUnwrapped Map<String, Object> options) {
	}

	/**
	 * Embedding response from HuggingFace Inference API.
	 *
	 * @param model The model used for generating embeddings.
	 * @param embeddings The generated embeddings as a list of float arrays. Each array
	 * represents one input's embedding vector.
	 */
	@JsonInclude(Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record EmbeddingsResponse(@JsonProperty("model") String model,
			@JsonProperty("embeddings") List<float[]> embeddings) {
	}

	/**
	 * Represents a tool the model may call. Currently, only functions are supported as a
	 * tool.
	 */
	@JsonInclude(Include.NON_NULL)
	public static class FunctionTool {

		/**
		 * The type of the tool. Currently, only 'function' is supported.
		 */
		@JsonProperty("type")
		private Type type = Type.FUNCTION;

		/**
		 * The function definition.
		 */
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
		@JsonInclude(Include.NON_NULL)
		public static class Function {

			@JsonProperty("description")
			private String description;

			@JsonProperty("name")
			private String name;

			@JsonProperty("parameters")
			private Map<String, Object> parameters;

			/**
			 * NOTE: Required by Jackson, JSON deserialization!
			 */
			@SuppressWarnings("unused")
			private Function() {
			}

			/**
			 * Create tool function definition.
			 * @param description A description of what the function does, used by the
			 * model to choose when and how to call the function.
			 * @param name The name of the function to be called. Must be a-z, A-Z, 0-9,
			 * or contain underscores and dashes, with a maximum length of 64.
			 * @param parameters The parameters the functions accepts, described as a JSON
			 * Schema object.
			 */
			public Function(String description, String name, Map<String, Object> parameters) {
				this.description = description;
				this.name = name;
				this.parameters = parameters;
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

		}

	}

	/**
	 * The relevant tool call.
	 *
	 * @param index The index of the tool call.
	 * @param id The ID of the tool call.
	 * @param type The type of tool call the output is required for. For now, this is
	 * always function.
	 * @param function The function definition.
	 */
	@JsonInclude(Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ToolCall(@JsonProperty("index") Integer index, @JsonProperty("id") String id,
			@JsonProperty("type") String type, @JsonProperty("function") ChatCompletionFunction function) {

		public ToolCall(String id, String type, ChatCompletionFunction function) {
			this(null, id, type, function);
		}

	}

	/**
	 * The function definition.
	 *
	 * @param name The name of the function.
	 * @param arguments The arguments that the model expects you to pass to the function.
	 */
	@JsonInclude(Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ChatCompletionFunction(@JsonProperty("name") String name,
			@JsonProperty("arguments") String arguments) {
	}

}
