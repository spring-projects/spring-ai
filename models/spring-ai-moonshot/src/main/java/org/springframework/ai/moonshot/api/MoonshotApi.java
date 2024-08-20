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
package org.springframework.ai.moonshot.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.ai.model.ChatModelDescription;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static org.springframework.ai.moonshot.api.MoonshotConstants.DEFAULT_BASE_URL;

/**
 * Single-class, Java Client library for Moonshot platform. Provides implementation for
 * the <a href="https://platform.moonshot.cn/docs/api-reference">Chat Completion</a> APIs.
 * <p>
 * Implements <b>Synchronous</b> and <b>Streaming</b> chat completion.
 * </p>
 *
 * @author Geng Rong
 */
public class MoonshotApi {

	public static final String DEFAULT_CHAT_MODEL = ChatModel.MOONSHOT_V1_32K.getValue();

	private static final Predicate<String> SSE_DONE_PREDICATE = "[DONE]"::equals;

	private final RestClient restClient;

	private final WebClient webClient;

	/**
	 * Create a new client api with DEFAULT_BASE_URL
	 * @param moonshotApiKey Moonshot api Key.
	 */
	public MoonshotApi(String moonshotApiKey) {
		this(DEFAULT_BASE_URL, moonshotApiKey);
	}

	/**
	 * Create a new client api.
	 * @param baseUrl api base URL.
	 * @param moonshotApiKey Moonshot api Key.
	 */
	public MoonshotApi(String baseUrl, String moonshotApiKey) {
		this(baseUrl, moonshotApiKey, RestClient.builder(), RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER);
	}

	/**
	 * Create a new client api.
	 * @param baseUrl api base URL.
	 * @param moonshotApiKey Moonshot api Key.
	 * @param restClientBuilder RestClient builder.
	 * @param responseErrorHandler Response error handler.
	 */
	public MoonshotApi(String baseUrl, String moonshotApiKey, RestClient.Builder restClientBuilder,
			ResponseErrorHandler responseErrorHandler) {

		Consumer<HttpHeaders> jsonContentHeaders = headers -> {
			headers.setBearerAuth(moonshotApiKey);
			headers.setContentType(MediaType.APPLICATION_JSON);
		};

		this.restClient = restClientBuilder.baseUrl(baseUrl)
			.defaultHeaders(jsonContentHeaders)
			.defaultStatusHandler(responseErrorHandler)
			.build();

		this.webClient = WebClient.builder().baseUrl(baseUrl).defaultHeaders(jsonContentHeaders).build();
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
	 * Creates a model response for the given chat conversation.
	 *
	 * @param model ID of the model to use.
	 * @param messages A list of messages comprising the conversation so far.
	 * @param maxTokens The maximum number of tokens to generate in the chat completion.
	 * The total length of input tokens and generated tokens is limited by the model's
	 * context length.
	 * @param temperature What sampling temperature to use, between 0 and 1. Higher values
	 * like 0.8 will make the output more random, while lower values like 0.2 will make it
	 * more focused and deterministic. We generally recommend altering this or top_p but
	 * not both.
	 * @param topP An alternative to sampling with temperature, called nucleus sampling,
	 * where the model considers the results of the tokens with top_p probability mass. So
	 * 0.1 means only the tokens comprising the top 10% probability mass are considered.
	 * We generally recommend altering this or temperature but not both.
	 * @param n How many chat completion choices to generate for each input message. Note
	 * that you will be charged based on the number of generated tokens across all the
	 * choices. Keep n as 1 to minimize costs.
	 * @param presencePenalty Number between -2.0 and 2.0. Positive values penalize new
	 * tokens based on whether they appear in the text so far, increasing the model's
	 * likelihood to talk about new topics.
	 * @param frequencyPenalty Number between -2.0 and 2.0. Positive values penalize new
	 * tokens based on their existing frequency in the text so far, decreasing the model's
	 * likelihood to repeat the same line verbatim.
	 * @param stop Up to 5 sequences where the API will stop generating further tokens.
	 * @param stream If set, partial message deltas will be sent.Tokens will be sent as
	 * data-only server-sent events as they become available, with the stream terminated
	 * by a data: [DONE] message.
	 */
	@JsonInclude(Include.NON_NULL)
	public record ChatCompletionRequest(
	// @formatter:off
            @JsonProperty("messages") List<ChatCompletionMessage> messages,
            @JsonProperty("model") String model,
            @JsonProperty("max_tokens") Integer maxTokens,
            @JsonProperty("temperature") Float temperature,
            @JsonProperty("top_p") Float topP,
            @JsonProperty("n") Integer n,
            @JsonProperty("frequency_penalty") Float frequencyPenalty,
            @JsonProperty("presence_penalty") Float presencePenalty,
            @JsonProperty("stop") List<String> stop,
            @JsonProperty("stream") Boolean stream,
			@JsonProperty("tools") List<FunctionTool> tools,
			@JsonProperty("tool_choice") Object toolChoice) {
		 // @formatter:on

		/**
		 * Shortcut constructor for a chat completion request with the given messages and
		 * model.
		 * @param messages The prompt(s) to generate completions for, encoded as a list of
		 * dict with role and content. The first prompt role should be user or system.
		 * @param model ID of the model to use.
		 */
		public ChatCompletionRequest(List<ChatCompletionMessage> messages, String model) {
			this(messages, model, null, 0.3f, 1f, null, null, null, null, false, null, null);
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
			this(messages, model, null, temperature, 1f, null, null, null, null, stream, null, null);
		}

		/**
		 * Shortcut constructor for a chat completion request with the given messages,
		 * model and temperature.
		 * @param messages The prompt(s) to generate completions for, encoded as a list of
		 * dict with role and content. The first prompt role should be user or system.
		 * @param model ID of the model to use.
		 * @param temperature What sampling temperature to use, between 0.0 and 1.0.
		 */
		public ChatCompletionRequest(List<ChatCompletionMessage> messages, String model, Float temperature) {
			this(messages, model, null, temperature, 1f, null, null, null, null, false, null, null);
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
			this(messages, model, null, null, 1f, null, null, null, null, false, tools, toolChoice);
		}

		/**
		 * Shortcut constructor for a chat completion request with the given messages and
		 * stream.
		 */
		public ChatCompletionRequest(List<ChatCompletionMessage> messages, Boolean stream) {
			this(messages, DEFAULT_CHAT_MODEL, null, 0.7f, 1F, null, null, null, null, stream, null, null);
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
	}

	/**
	 * Message comprising the conversation.
	 *
	 * @param rawContent The raw contents of the message.
	 * @param role The role of the message's author. Could be one of the {@link Role}
	 * types.
	 * @param name The name of the message's author.
	 * @param toolCallId The ID of the tool call associated with the message.
	 * @param toolCalls The list of tool calls associated with the message.
	 */
	@JsonInclude(Include.NON_NULL)
	public record ChatCompletionMessage(
	// @formatter:off
		@JsonProperty("content") Object rawContent,
		@JsonProperty("role") Role role,
		@JsonProperty("name") String name,
		@JsonProperty("tool_call_id") String toolCallId,
		@JsonProperty("tool_calls") List<ToolCall> toolCalls
	// @formatter:on
	) {

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
		 * Create a chat completion message with the given content and role. All other
		 * fields are null.
		 * @param content The contents of the message.
		 * @param role The role of the author of this message.
		 */
		public ChatCompletionMessage(Object content, Role role) {
			this(content, role, null, null, null);
		}

		/**
		 * The role of the author of this message. NOTE: Moonshot expects the system
		 * message to be before the user message or will fail with 400 error.
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
		@JsonInclude(Include.NON_NULL)
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
		@JsonInclude(Include.NON_NULL)
		public record ChatCompletionFunction(@JsonProperty("name") String name,
				@JsonProperty("arguments") String arguments) {
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
		 * (deprecated) The model called a function.
		 */
		@JsonProperty("function_call")
		FUNCTION_CALL,
		/**
		 * Only for compatibility with Mistral AI API.
		 */
		@JsonProperty("tool_call")
		TOOL_CALL

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
			@JsonProperty("finish_reason") ChatCompletionFinishReason finishReason,
			@JsonProperty("usage") Usage usage
		// @formatter:on
		) {
		}
	}

	/**
	 * Moonshot Chat Completion Models:
	 *
	 * <ul>
	 * <li><b>MOONSHOT_V1_8K</b> - moonshot-v1-8k</li>
	 * <li><b>MOONSHOT_V1_32K</b> - moonshot-v1-32k</li>
	 * <li><b>MOONSHOT_V1_128K</b> - moonshot-v1-128k</li>
	 * </ul>
	 */
	public enum ChatModel implements ChatModelDescription {

		// @formatter:off
        MOONSHOT_V1_8K("moonshot-v1-8k"),
        MOONSHOT_V1_32K("moonshot-v1-32k"),
        MOONSHOT_V1_128K("moonshot-v1-128k");
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
	 * Represents a tool the model may call. Currently, only functions are supported as a
	 * tool.
	 *
	 * @param type The type of the tool. Currently, only 'function' is supported.
	 * @param function The function definition.
	 */
	@JsonInclude(Include.NON_NULL)
	public record FunctionTool(@JsonProperty("type") Type type, @JsonProperty("function") Function function) {

		/**
		 * Create a tool of type 'function' and the given function definition.
		 * @param function function definition.
		 */
		@ConstructorBinding
		public FunctionTool(Function function) {
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
	 * Creates a model response for the given chat conversation.
	 * @param chatRequest The chat completion request.
	 * @return Entity response with {@link ChatCompletion} as a body and HTTP status code
	 * and headers.
	 */
	public ResponseEntity<ChatCompletion> chatCompletionEntity(ChatCompletionRequest chatRequest) {

		Assert.notNull(chatRequest, "The request body can not be null.");
		Assert.isTrue(!chatRequest.stream(), "Request must set the stream property to false.");

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
		Assert.isTrue(chatRequest.stream(), "Request must set the stream property to true.");

		return this.webClient.post()
			.uri("/v1/chat/completions")
			.body(Mono.just(chatRequest), ChatCompletionRequest.class)
			.retrieve()
			.bodyToFlux(String.class)
			.takeUntil(SSE_DONE_PREDICATE)
			.filter(SSE_DONE_PREDICATE.negate())
			.map(content -> ModelOptionsUtils.jsonToObject(content, ChatCompletionChunk.class));
	}

}
