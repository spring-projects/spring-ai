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
package org.springframework.ai.openai.api;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import org.springframework.ai.model.ChatModelDescription;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.openai.api.common.OpenAiApiConstants;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Single class implementation of the
 * <a href="https://platform.openai.com/docs/api-reference/chat">OpenAI Chat Completion
 * API</a> and <a href="https://platform.openai.com/docs/api-reference/embeddings">OpenAI
 * Embedding API</a>.
 *
 * @author Christian Tzolov
 * @author Michael Lavelle
 * @author Mariusz Bernacki
 * @author Thomas Vitale
 */
public class OpenAiApi {

	public static final OpenAiApi.ChatModel DEFAULT_CHAT_MODEL = ChatModel.GPT_4_O;

	public static final String DEFAULT_EMBEDDING_MODEL = EmbeddingModel.TEXT_EMBEDDING_ADA_002.getValue();

	private static final Predicate<String> SSE_DONE_PREDICATE = "[DONE]"::equals;

	private final String completionsPath;

	private final String embeddingsPath;

	private final RestClient restClient;

	private final WebClient webClient;

	/**
	 * Create a new chat completion api with base URL set to https://api.openai.com
	 * @param apiKey OpenAI apiKey.
	 */
	public OpenAiApi(String apiKey) {
		this(OpenAiApiConstants.DEFAULT_BASE_URL, apiKey);
	}

	/**
	 * Create a new chat completion api.
	 * @param baseUrl api base URL.
	 * @param apiKey OpenAI apiKey.
	 */
	public OpenAiApi(String baseUrl, String apiKey) {
		this(baseUrl, apiKey, RestClient.builder(), WebClient.builder());
	}

	/**
	 * Create a new chat completion api.
	 * @param baseUrl api base URL.
	 * @param apiKey OpenAI apiKey.
	 * @param restClientBuilder RestClient builder.
	 */
	public OpenAiApi(String baseUrl, String apiKey, RestClient.Builder restClientBuilder,
			WebClient.Builder webClientBuilder) {
		this(baseUrl, apiKey, restClientBuilder, webClientBuilder, RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER);
	}

	/**
	 * Create a new chat completion api.
	 * @param baseUrl api base URL.
	 * @param apiKey OpenAI apiKey.
	 * @param restClientBuilder RestClient builder.
	 * @param responseErrorHandler Response error handler.
	 */
	public OpenAiApi(String baseUrl, String apiKey, RestClient.Builder restClientBuilder,
			WebClient.Builder webClientBuilder, ResponseErrorHandler responseErrorHandler) {
		this(baseUrl, apiKey, "/v1/chat/completions", "/v1/embeddings", restClientBuilder, webClientBuilder,
				responseErrorHandler);
	}

	/**
	 * Create a new chat completion api.
	 * @param baseUrl api base URL.
	 * @param apiKey OpenAI apiKey.
	 * @param restClientBuilder RestClient builder.
	 * @param responseErrorHandler Response error handler.
	 */
	public OpenAiApi(String baseUrl, String apiKey, String completionsPath, String embeddingsPath,
			RestClient.Builder restClientBuilder, WebClient.Builder webClientBuilder,
			ResponseErrorHandler responseErrorHandler) {

		this(baseUrl, apiKey, CollectionUtils.toMultiValueMap(Map.of()), completionsPath, embeddingsPath,
				restClientBuilder, webClientBuilder, responseErrorHandler);
	}

	/**
	 * Create a new chat completion api.
	 * @param baseUrl api base URL.
	 * @param apiKey OpenAI apiKey.
	 * @param headers the http headers to use.
	 * @param restClientBuilder RestClient builder.
	 * @param responseErrorHandler Response error handler.
	 */
	public OpenAiApi(String baseUrl, String apiKey, MultiValueMap<String, String> headers, String completionsPath,
			String embeddingsPath, RestClient.Builder restClientBuilder, WebClient.Builder webClientBuilder,
			ResponseErrorHandler responseErrorHandler) {

		Assert.hasText(completionsPath, "Completions Path must not be null");
		Assert.hasText(embeddingsPath, "Embeddings Path must not be null");
		Assert.notNull(headers, "Headers must not be null");

		this.completionsPath = completionsPath;
		this.embeddingsPath = embeddingsPath;
		// @formatter:off
		this.restClient = restClientBuilder.baseUrl(baseUrl)
			.defaultHeaders(h -> {
				h.setBearerAuth(apiKey);
				h.setContentType(MediaType.APPLICATION_JSON);
				h.addAll(headers);
			})
			.defaultStatusHandler(responseErrorHandler)
			.build();

		this.webClient = webClientBuilder
			.baseUrl(baseUrl)
			.defaultHeaders(h -> {
				h.setBearerAuth(apiKey);
				h.setContentType(MediaType.APPLICATION_JSON);
				h.addAll(headers);
			})
			.build();// @formatter:on
	}

	/**
	 * OpenAI Chat Completion Models: -
	 * <a href="https://platform.openai.com/docs/models/gpt-4o">GPT-4o</a> -
	 * <a href="https://platform.openai.com/docs/models/gpt-4o-mini">GPT-4o mini</a> -
	 * <a href="https://platform.openai.com/docs/models/gpt-4-and-gpt-4-turbo">GPT-4 and
	 * GPT-4 Turbo</a> -
	 * <a href="https://platform.openai.com/docs/models/gpt-3-5-turbo">GPT-3.5 Turbo</a>.
	 */
	public enum ChatModel implements ChatModelDescription {

		/**
		 * Multimodal flagship model that’s cheaper and faster than GPT-4 Turbo. Currently
		 * points to gpt-4o-2024-05-13.
		 */
		GPT_4_O("gpt-4o"),

		/**
		 * Affordable and intelligent small model for fast, lightweight tasks. GPT-4o mini
		 * is cheaper and more capable than GPT-3.5 Turbo. Currently points to
		 * gpt-4o-mini-2024-07-18.
		 */
		GPT_4_O_MINI("gpt-4o-mini"),

		/**
		 * GPT-4 Turbo with Vision The latest GPT-4 Turbo model with vision capabilities.
		 * Vision requests can now use JSON mode and function calling. Currently points to
		 * gpt-4-turbo-2024-04-09.
		 */
		GPT_4_TURBO("gpt-4-turbo"),

		/**
		 * GPT-4 Turbo with Vision model. Vision requests can now use JSON mode and
		 * function calling.
		 */
		GPT_4_TURBO_2204_04_09("gpt-4-turbo-2024-04-09"),

		/**
		 * (New) GPT-4 Turbo - latest GPT-4 model intended to reduce cases of “laziness”
		 * where the model doesn’t complete a task. Returns a maximum of 4,096 output
		 * tokens. Context window: 128k tokens
		 */
		GPT_4_0125_PREVIEW("gpt-4-0125-preview"),

		/**
		 * Currently points to gpt-4-0125-preview - model featuring improved instruction
		 * following, JSON mode, reproducible outputs, parallel function calling, and
		 * more. Returns a maximum of 4,096 output tokens Context window: 128k tokens
		 */
		GPT_4_TURBO_PREVIEW("gpt-4-turbo-preview"),

		/**
		 * GPT-4 with the ability to understand images, in addition to all other GPT-4
		 * Turbo capabilities. Currently points to gpt-4-1106-vision-preview. Returns a
		 * maximum of 4,096 output tokens Context window: 128k tokens
		 */
		@Deprecated(since = "1.0.0-M2", forRemoval = true) // Replaced by GPT_4_O
		GPT_4_VISION_PREVIEW("gpt-4-vision-preview"),

		/**
		 * Currently points to gpt-4-0613. Snapshot of gpt-4 from June 13th 2023 with
		 * improved function calling support. Context window: 8k tokens
		 */
		GPT_4("gpt-4"),

		/**
		 * Currently points to gpt-4-32k-0613. Snapshot of gpt-4-32k from June 13th 2023
		 * with improved function calling support. Context window: 32k tokens
		 */
		@Deprecated(since = "1.0.0-M2", forRemoval = true) // Replaced by GPT_4_O
		GPT_4_32K("gpt-4-32k"),

		/**
		 * Currently points to gpt-3.5-turbo-0125. model with higher accuracy at
		 * responding in requested formats and a fix for a bug which caused a text
		 * encoding issue for non-English language function calls. Returns a maximum of
		 * 4,096 Context window: 16k tokens
		 */
		GPT_3_5_TURBO("gpt-3.5-turbo"),

		/**
		 * (new) The latest GPT-3.5 Turbo model with higher accuracy at responding in
		 * requested formats and a fix for a bug which caused a text encoding issue for
		 * non-English language function calls. Returns a maximum of 4,096 Context window:
		 * 16k tokens
		 */
		GPT_3_5_TURBO_0125("gpt-3.5-turbo-0125"),

		/**
		 * GPT-3.5 Turbo model with improved instruction following, JSON mode,
		 * reproducible outputs, parallel function calling, and more. Returns a maximum of
		 * 4,096 output tokens. Context window: 16k tokens.
		 */
		GPT_3_5_TURBO_1106("gpt-3.5-turbo-1106");

		public final String value;

		ChatModel(String value) {
			this.value = value;
		}

		public String getValue() {
			return value;
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
	public record FunctionTool(// @formatter:off
			@JsonProperty("type") Type type,
			@JsonProperty("function") Function function) {

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
			@JsonProperty("function") FUNCTION
		}

		/**
		 * Function definition.
		 *
		 * @param description A description of what the function does, used by the model to choose when and how to call
		 * the function.
		 * @param name The name of the function to be called. Must be a-z, A-Z, 0-9, or contain underscores and dashes,
		 * with a maximum length of 64.
		 * @param parameters The parameters the functions accepts, described as a JSON Schema object. To describe a
		 * function that accepts no parameters, provide the value {"type": "object", "properties": {}}.
		 */
		public record Function(
				@JsonProperty("description") String description,
				@JsonProperty("name") String name,
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
	}// @formatter:on

	/**
	 * Creates a model response for the given chat conversation.
	 *
	 * @param messages A list of messages comprising the conversation so far.
	 * @param model ID of the model to use.
	 * @param frequencyPenalty Number between -2.0 and 2.0. Positive values penalize new
	 * tokens based on their existing frequency in the text so far, decreasing the model's
	 * likelihood to repeat the same line verbatim.
	 * @param logitBias Modify the likelihood of specified tokens appearing in the
	 * completion. Accepts a JSON object that maps tokens (specified by their token ID in
	 * the tokenizer) to an associated bias value from -100 to 100. Mathematically, the
	 * bias is added to the logits generated by the model prior to sampling. The exact
	 * effect will vary per model, but values between -1 and 1 should decrease or increase
	 * likelihood of selection; values like -100 or 100 should result in a ban or
	 * exclusive selection of the relevant token.
	 * @param logprobs Whether to return log probabilities of the output tokens or not. If
	 * true, returns the log probabilities of each output token returned in the 'content'
	 * of 'message'.
	 * @param topLogprobs An integer between 0 and 5 specifying the number of most likely
	 * tokens to return at each token position, each with an associated log probability.
	 * 'logprobs' must be set to 'true' if this parameter is used.
	 * @param maxTokens The maximum number of tokens to generate in the chat completion.
	 * The total length of input tokens and generated tokens is limited by the model's
	 * context length.
	 * @param n How many chat completion choices to generate for each input message. Note
	 * that you will be charged based on the number of generated tokens across all the
	 * choices. Keep n as 1 to minimize costs.
	 * @param presencePenalty Number between -2.0 and 2.0. Positive values penalize new
	 * tokens based on whether they appear in the text so far, increasing the model's
	 * likelihood to talk about new topics.
	 * @param responseFormat An object specifying the format that the model must output.
	 * Setting to { "type": "json_object" } enables JSON mode, which guarantees the
	 * message the model generates is valid JSON.
	 * @param seed This feature is in Beta. If specified, our system will make a best
	 * effort to sample deterministically, such that repeated requests with the same seed
	 * and parameters should return the same result. Determinism is not guaranteed, and
	 * you should refer to the system_fingerprint response parameter to monitor changes in
	 * the backend.
	 * @param stop Up to 4 sequences where the API will stop generating further tokens.
	 * @param stream If set, partial message deltas will be sent.Tokens will be sent as
	 * data-only server-sent events as they become available, with the stream terminated
	 * by a data: [DONE] message.
	 * @param streamOptions Options for streaming response. Only set this when you set.
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
	 * @param user A unique identifier representing your end-user, which can help OpenAI
	 * to monitor and detect abuse.
	 * @param parallelToolCalls If set to true, the model will call all functions in the
	 * tools list in parallel. Otherwise, the model will call the functions in the tools
	 * list in the order they are provided.
	 */
	@JsonInclude(Include.NON_NULL)
	public record ChatCompletionRequest(// @formatter:off
			@JsonProperty("messages") List<ChatCompletionMessage> messages,
			@JsonProperty("model") String model,
			@JsonProperty("frequency_penalty") Float frequencyPenalty,
			@JsonProperty("logit_bias") Map<String, Integer> logitBias,
			@JsonProperty("logprobs") Boolean logprobs,
			@JsonProperty("top_logprobs") Integer topLogprobs,
			@JsonProperty("max_tokens") Integer maxTokens,
			@JsonProperty("n") Integer n,
			@JsonProperty("presence_penalty") Float presencePenalty,
			@JsonProperty("response_format") ResponseFormat responseFormat,
			@JsonProperty("seed") Integer seed,
			@JsonProperty("stop") List<String> stop,
			@JsonProperty("stream") Boolean stream,
			@JsonProperty("stream_options") StreamOptions streamOptions,
			@JsonProperty("temperature") Float temperature,
			@JsonProperty("top_p") Float topP,
			@JsonProperty("tools") List<FunctionTool> tools,
			@JsonProperty("tool_choice") Object toolChoice,
			@JsonProperty("parallel_tool_calls") Boolean parallelToolCalls,
			@JsonProperty("user") String user) {

		/**
		 * Shortcut constructor for a chat completion request with the given messages, model and temperature.
		 *
		 * @param messages A list of messages comprising the conversation so far.
		 * @param model ID of the model to use.
		 * @param temperature What sampling temperature to use, between 0 and 1.
		 */
		public ChatCompletionRequest(List<ChatCompletionMessage> messages, String model, Float temperature) {
			this(messages, model, null, null, null, null, null, null, null,
					null, null, null, false, null, temperature, null,
					null, null, null, null);
		}

		/**
		 * Shortcut constructor for a chat completion request with the given messages, model, temperature and control for streaming.
		 *
		 * @param messages A list of messages comprising the conversation so far.
		 * @param model ID of the model to use.
		 * @param temperature What sampling temperature to use, between 0 and 1.
		 * @param stream If set, partial message deltas will be sent.Tokens will be sent as data-only server-sent events
		 * as they become available, with the stream terminated by a data: [DONE] message.
		 */
		public ChatCompletionRequest(List<ChatCompletionMessage> messages, String model, Float temperature, boolean stream) {
			this(messages, model, null, null, null, null, null, null, null,
					null, null, null, stream, null, temperature, null,
					null, null, null,  null);
		}

		/**
		 * Shortcut constructor for a chat completion request with the given messages, model, tools and tool choice.
		 * Streaming is set to false, temperature to 0.8 and all other parameters are null.
		 *
		 * @param messages A list of messages comprising the conversation so far.
		 * @param model ID of the model to use.
		 * @param tools A list of tools the model may call. Currently, only functions are supported as a tool.
		 * @param toolChoice Controls which (if any) function is called by the model.
		 */
		public ChatCompletionRequest(List<ChatCompletionMessage> messages, String model,
				List<FunctionTool> tools, Object toolChoice) {
			this(messages, model, null, null, null, null, null, null, null,
					null, null, null, false, null, 0.8f, null,
					tools, toolChoice, null, null);
		}

		/**
		 * Shortcut constructor for a chat completion request with the given messages for streaming.
		 *
		 * @param messages A list of messages comprising the conversation so far.
		 * @param stream If set, partial message deltas will be sent.Tokens will be sent as data-only server-sent events
		 * as they become available, with the stream terminated by a data: [DONE] message.
		 */
		public ChatCompletionRequest(List<ChatCompletionMessage> messages, Boolean stream) {
			this(messages, null, null, null, null, null, null, null, null,
					null, null, null, stream, null, null, null,
					null, null, null, null);
		}

		/**
		 * Sets the {@link StreamOptions} for this request.
		 *
		 * @param streamOptions The new stream options to use.
		 * @return A new {@link ChatCompletionRequest} with the specified stream options.
		 */
		public ChatCompletionRequest withStreamOptions(StreamOptions streamOptions) {
			return new ChatCompletionRequest(messages, model, frequencyPenalty, logitBias, logprobs, topLogprobs, maxTokens, n, presencePenalty,
					responseFormat, seed, stop, stream, streamOptions, temperature, topP,
					tools, toolChoice, parallelToolCalls, user);
		}

		/**
		 * Helper factory that creates a tool_choice of type 'none', 'auto' or selected function by name.
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
			public static Object FUNCTION(String functionName) {
				return Map.of("type", "function", "function", Map.of("name", functionName));
			}
		}

		/**
		 * An object specifying the format that the model must output.
		 * @param type Must be one of 'text' or 'json_object'.
		 * @param jsonSchema JSON schema object that describes the format of the JSON object.
		 * Only applicable when type is 'json_schema'.
		 */
		@JsonInclude(Include.NON_NULL)
		public record ResponseFormat(
				@JsonProperty("type") Type type,
				@JsonProperty("json_schema") JsonSchema jsonSchema ) {
			
			public enum Type {
				/**
				 * Generates a text response. (default)
				 */
				@JsonProperty("text")
				TEXT,

				/**
				 * Enables JSON mode, which guarantees the message
				 * the model generates is valid JSON.
				 */
				@JsonProperty("json_object")
				JSON_OBJECT,

				/**
				 * Enables Structured Outputs which guarantees the model
				 * will match your supplied JSON schema.
				 */
				@JsonProperty("json_schema")
				JSON_SCHEMA
			}

			/**
			 * JSON schema object that describes the format of the JSON object.
			 * Applicable for the 'json_schema' type only.
			 * @param name The name of the schema.
			 * @param schema The JSON schema object that describes the format of the JSON object.
			 * @param strict If true, the model will only generate outputs that match the schema.
			 */
			@JsonInclude(Include.NON_NULL)
			public record JsonSchema(
				@JsonProperty("name") String name,
				@JsonProperty("schema") Map<String, Object> schema,
				@JsonProperty("strict") Boolean strict) {

				public JsonSchema(String name, String schema) {
					this(name, ModelOptionsUtils.jsonToMap(schema), true);
				}

				public JsonSchema(String name, String schema, Boolean strict) {
					this(StringUtils.hasText(name)? name : "custom_schema", ModelOptionsUtils.jsonToMap(schema), strict);
				}
			}

			public ResponseFormat(Type type) {
				this(type, (JsonSchema) null);
			}

			public ResponseFormat(Type type, String schema) {
				this(type, "custom_schema", schema, true);
			}

			@ConstructorBinding
			public ResponseFormat(Type type, String name, String schema, Boolean strict) {
				this(type, StringUtils.hasText(schema)? new JsonSchema(name, schema, strict): null);
			}

		}

		/**
		 * @param includeUsage If set, an additional chunk will be streamed
		 * before the data: [DONE] message. The usage field on this chunk
		 * shows the token usage statistics for the entire request, and
		 * the choices field will always be an empty array. All other chunks
		 * will also include a usage field, but with a null value.
		 */
		@JsonInclude(Include.NON_NULL)
		public record StreamOptions(
				@JsonProperty("include_usage") Boolean includeUsage) {

			public static StreamOptions INCLUDE_USAGE = new StreamOptions(true);
		}
	}// @formatter:on

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
	public record ChatCompletionMessage(// @formatter:off
			@JsonProperty("content") Object rawContent,
			@JsonProperty("role") Role role,
			@JsonProperty("name") String name,
			@JsonProperty("tool_call_id") String toolCallId,
			@JsonProperty("tool_calls") List<ToolCall> toolCalls,
			@JsonProperty("refusal") String refusal) {// @formatter:on

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
			this(content, role, null, null, null, null);
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
		 * using the gpt-4-visual-preview model.
		 */
		@JsonInclude(Include.NON_NULL)
		public record MediaContent(// @formatter:off
			@JsonProperty("type") String type,
			@JsonProperty("text") String text,
			@JsonProperty("image_url") ImageUrl imageUrl) {
// @formatter:on
			/**
			 * @param url Either a URL of the image or the base64 encoded image data. The
			 * base64 encoded image data must have a special prefix in the following
			 * format: "data:{mimetype};base64,{base64-encoded-image-data}".
			 * @param detail Specifies the detail level of the image.
			 */
			@JsonInclude(Include.NON_NULL)
			public record ImageUrl(@JsonProperty("url") String url, @JsonProperty("detail") String detail) {

				public ImageUrl(String url) {
					this(url, null);
				}
			}

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
		public record ToolCall(// @formatter:off
				@JsonProperty("id") String id,
				@JsonProperty("type") String type,
				@JsonProperty("function") ChatCompletionFunction function) {// @formatter:on
		}

		/**
		 * The function definition.
		 *
		 * @param name The name of the function.
		 * @param arguments The arguments that the model expects you to pass to the
		 * function.
		 */
		@JsonInclude(Include.NON_NULL)
		public record ChatCompletionFunction(// @formatter:off
				@JsonProperty("name") String name,
				@JsonProperty("arguments") String arguments) {// @formatter:on
		}
	}

	public static String getTextContent(List<ChatCompletionMessage.MediaContent> content) {
		return content.stream()
			.filter(c -> "text".equals(c.type()))
			.map(ChatCompletionMessage.MediaContent::text)
			.reduce("", (a, b) -> a + b);
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
	public record ChatCompletion(// @formatter:off
			@JsonProperty("id") String id,
			@JsonProperty("choices") List<Choice> choices,
			@JsonProperty("created") Long created,
			@JsonProperty("model") String model,
			@JsonProperty("system_fingerprint") String systemFingerprint,
			@JsonProperty("object") String object,
			@JsonProperty("usage") Usage usage) {// @formatter:on

		/**
		 * Chat completion choice.
		 *
		 * @param finishReason The reason the model stopped generating tokens.
		 * @param index The index of the choice in the list of choices.
		 * @param message A chat completion message generated by the model.
		 * @param logprobs Log probability information for the choice.
		 */
		@JsonInclude(Include.NON_NULL)
		public record Choice(// @formatter:off
				@JsonProperty("finish_reason") ChatCompletionFinishReason finishReason,
				@JsonProperty("index") Integer index,
				@JsonProperty("message") ChatCompletionMessage message,
				@JsonProperty("logprobs") LogProbs logprobs) {// @formatter:on

		}
	}

	/**
	 * Log probability information for the choice.
	 *
	 * @param content A list of message content tokens with log probability information.
	 */
	@JsonInclude(Include.NON_NULL)
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
		public record Content(// @formatter:off
				@JsonProperty("token") String token,
				@JsonProperty("logprob") Float logprob,
				@JsonProperty("bytes") List<Integer> probBytes,
				@JsonProperty("top_logprobs") List<TopLogProbs> topLogprobs) {// @formatter:on

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
			public record TopLogProbs(// @formatter:off
					@JsonProperty("token") String token,
					@JsonProperty("logprob") Float logprob,
					@JsonProperty("bytes") List<Integer> probBytes) {// @formatter:on
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
	public record Usage(// @formatter:off
			@JsonProperty("completion_tokens") Integer completionTokens,
			@JsonProperty("prompt_tokens") Integer promptTokens,
			@JsonProperty("total_tokens") Integer totalTokens) {// @formatter:on

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
	 * @param usage Usage statistics for the completion request. Present in the last chunk
	 * only if the StreamOptions.includeUsage is set to true.
	 */
	@JsonInclude(Include.NON_NULL)
	public record ChatCompletionChunk(// @formatter:off
			@JsonProperty("id") String id,
			@JsonProperty("choices") List<ChunkChoice> choices,
			@JsonProperty("created") Long created,
			@JsonProperty("model") String model,
			@JsonProperty("system_fingerprint") String systemFingerprint,
			@JsonProperty("object") String object,
			@JsonProperty("usage") Usage usage) {// @formatter:on

		/**
		 * Chat completion choice.
		 *
		 * @param finishReason The reason the model stopped generating tokens.
		 * @param index The index of the choice in the list of choices.
		 * @param delta A chat completion delta generated by streamed model responses.
		 * @param logprobs Log probability information for the choice.
		 */
		@JsonInclude(Include.NON_NULL)
		public record ChunkChoice(// @formatter:off
				@JsonProperty("finish_reason") ChatCompletionFinishReason finishReason,
				@JsonProperty("index") Integer index,
				@JsonProperty("delta") ChatCompletionMessage delta,
				@JsonProperty("logprobs") LogProbs logprobs) {// @formatter:on
		}
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
	 * @param additionalHttpHeader Optional, additional HTTP headers to be added to the
	 * request.
	 * @return Entity response with {@link ChatCompletion} as a body and HTTP status code
	 * and headers.
	 */
	public ResponseEntity<ChatCompletion> chatCompletionEntity(ChatCompletionRequest chatRequest,
			MultiValueMap<String, String> additionalHttpHeader) {

		Assert.notNull(chatRequest, "The request body can not be null.");
		Assert.isTrue(!chatRequest.stream(), "Request must set the steam property to false.");
		Assert.notNull(additionalHttpHeader, "The additional HTTP headers can not be null.");

		return this.restClient.post()
			.uri(this.completionsPath)
			.headers(headers -> headers.addAll(additionalHttpHeader))
			.body(chatRequest)
			.retrieve()
			.toEntity(ChatCompletion.class);
	}

	private OpenAiStreamFunctionCallingHelper chunkMerger = new OpenAiStreamFunctionCallingHelper();

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
	 * @param additionalHttpHeader Optional, additional HTTP headers to be added to the
	 * request.
	 * @return Returns a {@link Flux} stream from chat completion chunks.
	 */
	public Flux<ChatCompletionChunk> chatCompletionStream(ChatCompletionRequest chatRequest,
			MultiValueMap<String, String> additionalHttpHeader) {

		Assert.notNull(chatRequest, "The request body can not be null.");
		Assert.isTrue(chatRequest.stream(), "Request must set the steam property to true.");

		AtomicBoolean isInsideTool = new AtomicBoolean(false);

		return this.webClient.post()
			.uri(this.completionsPath)
			.headers(headers -> headers.addAll(additionalHttpHeader))
			.body(Mono.just(chatRequest), ChatCompletionRequest.class)
			.retrieve()
			.bodyToFlux(String.class)
			// cancels the flux stream after the "[DONE]" is received.
			.takeUntil(SSE_DONE_PREDICATE)
			// filters out the "[DONE]" message.
			.filter(SSE_DONE_PREDICATE.negate())
			.map(content -> ModelOptionsUtils.jsonToObject(content, ChatCompletionChunk.class))
			// Detect is the chunk is part of a streaming function call.
			.map(chunk -> {
				if (this.chunkMerger.isStreamingToolFunctionCall(chunk)) {
					isInsideTool.set(true);
				}
				return chunk;
			})
			// Group all chunks belonging to the same function call.
			// Flux<ChatCompletionChunk> -> Flux<Flux<ChatCompletionChunk>>
			.windowUntil(chunk -> {
				if (isInsideTool.get() && this.chunkMerger.isStreamingToolFunctionCallFinish(chunk)) {
					isInsideTool.set(false);
					return true;
				}
				return !isInsideTool.get();
			})
			// Merging the window chunks into a single chunk.
			// Reduce the inner Flux<ChatCompletionChunk> window into a single
			// Mono<ChatCompletionChunk>,
			// Flux<Flux<ChatCompletionChunk>> -> Flux<Mono<ChatCompletionChunk>>
			.concatMapIterable(window -> {
				Mono<ChatCompletionChunk> monoChunk = window.reduce(
						new ChatCompletionChunk(null, null, null, null, null, null, null),
						(previous, current) -> this.chunkMerger.merge(previous, current));
				return List.of(monoChunk);
			})
			// Flux<Mono<ChatCompletionChunk>> -> Flux<ChatCompletionChunk>
			.flatMap(mono -> mono);
	}

	// Embeddings API

	/**
	 * OpenAI Embeddings Models:
	 * <a href="https://platform.openai.com/docs/models/embeddings">Embeddings</a>.
	 */
	public enum EmbeddingModel {

		/**
		 * Most capable embedding model for both english and non-english tasks. DIMENSION:
		 * 3072
		 */
		TEXT_EMBEDDING_3_LARGE("text-embedding-3-large"),

		/**
		 * Increased performance over 2nd generation ada embedding model. DIMENSION: 1536
		 */
		TEXT_EMBEDDING_3_SMALL("text-embedding-3-small"),

		/**
		 * Most capable 2nd generation embedding model, replacing 16 first generation
		 * models. DIMENSION: 1536
		 */
		TEXT_EMBEDDING_ADA_002("text-embedding-ada-002");

		public final String value;

		EmbeddingModel(String value) {
			this.value = value;
		}

		public String getValue() {
			return value;
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
	public record Embedding(// @formatter:off
			@JsonProperty("index") Integer index,
			@JsonProperty("embedding") List<Double> embedding,
			@JsonProperty("object") String object) {// @formatter:on

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
	 * Creates an embedding vector representing the input text.
	 *
	 * @param input Input text to embed, encoded as a string or array of tokens. To embed
	 * multiple inputs in a single request, pass an array of strings or array of token
	 * arrays. The input must not exceed the max input tokens for the model (8192 tokens
	 * for text-embedding-ada-002), cannot be an empty string, and any array must be 2048
	 * dimensions or less.
	 * @param model ID of the model to use.
	 * @param encodingFormat The format to return the embeddings in. Can be either float
	 * or base64.
	 * @param dimensions The number of dimensions the resulting output embeddings should
	 * have. Only supported in text-embedding-3 and later models.
	 * @param user A unique identifier representing your end-user, which can help OpenAI
	 * to monitor and detect abuse.
	 */
	@JsonInclude(Include.NON_NULL)
	public record EmbeddingRequest<T>(// @formatter:off
			@JsonProperty("input") T input,
			@JsonProperty("model") String model,
			@JsonProperty("encoding_format") String encodingFormat,
			@JsonProperty("dimensions") Integer dimensions,
			@JsonProperty("user") String user) {// @formatter:on

		/**
		 * Create an embedding request with the given input, model and encoding format set
		 * to float.
		 * @param input Input text to embed.
		 * @param model ID of the model to use.
		 */
		public EmbeddingRequest(T input, String model) {
			this(input, model, "float", null, null);
		}

		/**
		 * Create an embedding request with the given input. Encoding format is set to
		 * float and user is null and the model is set to 'text-embedding-ada-002'.
		 * @param input Input text to embed.
		 */
		public EmbeddingRequest(T input) {
			this(input, DEFAULT_EMBEDDING_MODEL);
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
	public record EmbeddingList<T>(// @formatter:off
			@JsonProperty("object") String object,
			@JsonProperty("data") List<T> data,
			@JsonProperty("model") String model,
			@JsonProperty("usage") Usage usage) {// @formatter:on
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

		// The input must not exceed the max input tokens for the model (8192 tokens for
		// text-embedding-ada-002), cannot
		// be an empty string, and any array must be 2048 dimensions or less.
		if (embeddingRequest.input() instanceof List list) {
			Assert.isTrue(!CollectionUtils.isEmpty(list), "The input list can not be empty.");
			Assert.isTrue(list.size() <= 2048, "The list must be 2048 dimensions or less");
			Assert.isTrue(
					list.get(0) instanceof String || list.get(0) instanceof Integer || list.get(0) instanceof List,
					"The input must be either a String, or a List of Strings or list of list of integers.");
		}

		return this.restClient.post()
			.uri(this.embeddingsPath)
			.body(embeddingRequest)
			.retrieve()
			.toEntity(new ParameterizedTypeReference<>() {
			});
	}

}
