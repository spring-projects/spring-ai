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
package org.springframework.ai.anthropic.api;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * @author Christian Tzolov
 * @since 1.0.0
 */
public class AnthropicApi {

	private static final String HEADER_X_API_KEY = "x-api-key";

	private static final String HEADER_ANTHROPIC_VERSION = "anthropic-version";

	private static final String HEADER_ANTHROPIC_BETA = "anthropic-beta";

	public static final String DEFAULT_BASE_URL = "https://api.anthropic.com";

	public static final String DEFAULT_ANTHROPIC_VERSION = "2023-06-01";

	public static final String DEFAULT_ANTHROPIC_BETA_VERSION = "tools-2024-04-04";

	private static final Predicate<String> SSE_DONE_PREDICATE = "[DONE]"::equals;

	private final RestClient restClient;

	private WebClient webClient;

	/**
	 * Create a new client api with DEFAULT_BASE_URL
	 * @param anthropicApiKey Anthropic api Key.
	 */
	public AnthropicApi(String anthropicApiKey) {
		this(DEFAULT_BASE_URL, anthropicApiKey);
	}

	/**
	 * Create a new client api.
	 * @param baseUrl api base URL.
	 * @param anthropicApiKey Anthropic api Key.
	 */
	public AnthropicApi(String baseUrl, String anthropicApiKey) {
		this(baseUrl, anthropicApiKey, DEFAULT_ANTHROPIC_VERSION, RestClient.builder(),
				RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER);
	}

	/**
	 * Create a new client api.
	 * @param baseUrl api base URL.
	 * @param anthropicApiKey Anthropic api Key.
	 * @param restClientBuilder RestClient builder.
	 * @param responseErrorHandler Response error handler.
	 */
	public AnthropicApi(String baseUrl, String anthropicApiKey, String anthropicVersion,
			RestClient.Builder restClientBuilder, ResponseErrorHandler responseErrorHandler) {

		Consumer<HttpHeaders> jsonContentHeaders = headers -> {
			headers.add(HEADER_X_API_KEY, anthropicApiKey);
			headers.add(HEADER_ANTHROPIC_VERSION, anthropicVersion);
			headers.add(HEADER_ANTHROPIC_BETA, DEFAULT_ANTHROPIC_BETA_VERSION);
			headers.setContentType(MediaType.APPLICATION_JSON);
		};

		this.restClient = restClientBuilder.baseUrl(baseUrl)
			.defaultHeaders(jsonContentHeaders)
			.defaultStatusHandler(responseErrorHandler)
			.build();

		this.webClient = WebClient.builder()
			.baseUrl(baseUrl)
			.defaultHeaders(jsonContentHeaders)
			.defaultStatusHandler(HttpStatusCode::isError,
					resp -> Mono.just(new RuntimeException("Response exception, Status: [" + resp.statusCode()
							+ "], Body:[" + resp.bodyToMono(java.lang.String.class) + "]")))
			.build();
	}

	/**
	 * Check the <a href="https://docs.anthropic.com/claude/docs/models-overview">Models
	 * overview</a> and <a href=
	 * "https://docs.anthropic.com/claude/docs/models-overview#model-comparison">model
	 * comparison</a> for additional details and options.
	 */
	public enum ChatModel {

		// @formatter:off
		CLAUDE_3_OPUS("claude-3-opus-20240229"),
		CLAUDE_3_SONNET("claude-3-sonnet-20240229"),
		CLAUDE_3_HAIKU("claude-3-haiku-20240307"),

		// Legacy models
		CLAUDE_2_1("claude-2.1"),
		CLAUDE_2("claude-2.0"),

		CLAUDE_INSTANT_1_2("claude-instant-1.2");
		// @formatter:on

		public final String value;

		ChatModel(String value) {
			this.value = value;
		}

		public String getValue() {
			return this.value;
		}

	}

	/**
	 * @param model The model that will complete your prompt. See the list of
	 * <a href="https://docs.anthropic.com/claude/docs/models-overview">models</a> for
	 * additional details and options.
	 * @param messages Input messages.
	 * @param system System prompt. A system prompt is a way of providing context and
	 * instructions to Claude, such as specifying a particular goal or role. See our
	 * <a href="https://docs.anthropic.com/claude/docs/system-prompts">guide</a> to system
	 * prompts.
	 * @param maxTokens The maximum number of tokens to generate before stopping. Note
	 * that our models may stop before reaching this maximum. This parameter only
	 * specifies the absolute maximum number of tokens to generate. Different models have
	 * different maximum values for this parameter.
	 * @param metadata An object describing metadata about the request.
	 * @param stopSequences Custom text sequences that will cause the model to stop
	 * generating. Our models will normally stop when they have naturally completed their
	 * turn, which will result in a response stop_reason of "end_turn". If you want the
	 * model to stop generating when it encounters custom strings of text, you can use the
	 * stop_sequences parameter. If the model encounters one of the custom sequences, the
	 * response stop_reason value will be "stop_sequence" and the response stop_sequence
	 * value will contain the matched stop sequence.
	 * @param stream Whether to incrementally stream the response using server-sent
	 * events.
	 * @param temperature Amount of randomness injected into the response.Defaults to 1.0.
	 * Ranges from 0.0 to 1.0. Use temperature closer to 0.0 for analytical / multiple
	 * choice, and closer to 1.0 for creative and generative tasks. Note that even with
	 * temperature of 0.0, the results will not be fully deterministic.
	 * @param topP Use nucleus sampling. In nucleus sampling, we compute the cumulative
	 * distribution over all the options for each subsequent token in decreasing
	 * probability order and cut it off once it reaches a particular probability specified
	 * by top_p. You should either alter temperature or top_p, but not both. Recommended
	 * for advanced use cases only. You usually only need to use temperature.
	 * @param topK Only sample from the top K options for each subsequent token. Used to
	 * remove "long tail" low probability responses. Learn more technical details here.
	 * Recommended for advanced use cases only. You usually only need to use temperature.
	 * @param tools Definitions of tools that the model may use. If provided the model may
	 * return tool_use content blocks that represent the model's use of those tools. You
	 * can then run those tools using the tool input generated by the model and then
	 * optionally return results back to the model using tool_result content blocks.
	 */
	@JsonInclude(Include.NON_NULL)
	public record ChatCompletionRequest( // @formatter:off
		@JsonProperty("model") String model,
		@JsonProperty("messages") List<RequestMessage> messages,
		@JsonProperty("system") String system,
		@JsonProperty("max_tokens") Integer maxTokens,
		@JsonProperty("metadata") Metadata metadata,
		@JsonProperty("stop_sequences") List<String> stopSequences,
		@JsonProperty("stream") Boolean stream,
		@JsonProperty("temperature") Float temperature,
		@JsonProperty("top_p") Float topP,
		@JsonProperty("top_k") Integer topK,
		@JsonProperty("tools") List<Tool> tools) {
		// @formatter:on

		public ChatCompletionRequest(String model, List<RequestMessage> messages, String system, Integer maxTokens,
				Float temperature, Boolean stream) {
			this(model, messages, system, maxTokens, null, null, stream, temperature, null, null, null);
		}

		public ChatCompletionRequest(String model, List<RequestMessage> messages, String system, Integer maxTokens,
				List<String> stopSequences, Float temperature, Boolean stream) {
			this(model, messages, system, maxTokens, null, stopSequences, stream, temperature, null, null, null);
		}

		/**
		 * @param userId An external identifier for the user who is associated with the
		 * request. This should be a uuid, hash value, or other opaque identifier.
		 * Anthropic may use this id to help detect abuse. Do not include any identifying
		 * information such as name, email address, or phone number.
		 */
		@JsonInclude(Include.NON_NULL)
		public record Metadata(@JsonProperty("user_id") String userId) {
		}

		public static ChatCompletionRequestBuilder builder() {
			return new ChatCompletionRequestBuilder();
		}

		public static ChatCompletionRequestBuilder from(ChatCompletionRequest request) {
			return new ChatCompletionRequestBuilder(request);
		}
	}

	public static class ChatCompletionRequestBuilder {

		private String model;

		private List<RequestMessage> messages;

		private String system;

		private Integer maxTokens;

		private ChatCompletionRequest.Metadata metadata;

		private List<String> stopSequences;

		private Boolean stream = false;

		private Float temperature;

		private Float topP;

		private Integer topK;

		private List<Tool> tools;

		private ChatCompletionRequestBuilder() {
		}

		private ChatCompletionRequestBuilder(ChatCompletionRequest request) {
			this.model = request.model;
			this.messages = request.messages;
			this.system = request.system;
			this.maxTokens = request.maxTokens;
			this.metadata = request.metadata;
			this.stopSequences = request.stopSequences;
			this.stream = request.stream;
			this.temperature = request.temperature;
			this.topP = request.topP;
			this.topK = request.topK;
			this.tools = request.tools;
		}

		public ChatCompletionRequestBuilder withModel(ChatModel model) {
			this.model = model.getValue();
			return this;
		}

		public ChatCompletionRequestBuilder withModel(String model) {
			this.model = model;
			return this;
		}

		public ChatCompletionRequestBuilder withMessages(List<RequestMessage> messages) {
			this.messages = messages;
			return this;
		}

		public ChatCompletionRequestBuilder withSystem(String system) {
			this.system = system;
			return this;
		}

		public ChatCompletionRequestBuilder withMaxTokens(Integer maxTokens) {
			this.maxTokens = maxTokens;
			return this;
		}

		public ChatCompletionRequestBuilder withMetadata(ChatCompletionRequest.Metadata metadata) {
			this.metadata = metadata;
			return this;
		}

		public ChatCompletionRequestBuilder withStopSequences(List<String> stopSequences) {
			this.stopSequences = stopSequences;
			return this;
		}

		public ChatCompletionRequestBuilder withStream(Boolean stream) {
			this.stream = stream;
			return this;
		}

		public ChatCompletionRequestBuilder withTemperature(Float temperature) {
			this.temperature = temperature;
			return this;
		}

		public ChatCompletionRequestBuilder withTopP(Float topP) {
			this.topP = topP;
			return this;
		}

		public ChatCompletionRequestBuilder withTopK(Integer topK) {
			this.topK = topK;
			return this;
		}

		public ChatCompletionRequestBuilder withTools(List<Tool> tools) {
			this.tools = tools;
			return this;
		}

		public ChatCompletionRequest build() {
			return new ChatCompletionRequest(model, messages, system, maxTokens, metadata, stopSequences, stream,
					temperature, topP, topK, tools);
		}

	}

	/**
	 * Input messages.
	 *
	 * Our models are trained to operate on alternating user and assistant conversational
	 * turns. When creating a new Message, you specify the prior conversational turns with
	 * the messages parameter, and the model then generates the next Message in the
	 * conversation. Each input message must be an object with a role and content. You can
	 * specify a single user-role message, or you can include multiple user and assistant
	 * messages. The first message must always use the user role. If the final message
	 * uses the assistant role, the response content will continue immediately from the
	 * content in that message. This can be used to constrain part of the model's
	 * response.
	 *
	 * @param content The contents of the message. Can be of one of String or
	 * MultiModalContent.
	 * @param role The role of the messages author. Could be one of the {@link Role}
	 * types.
	 */
	@JsonInclude(Include.NON_NULL)
	public record RequestMessage( // @formatter:off
		 @JsonProperty("content") List<MediaContent> content,
		 @JsonProperty("role") Role role) {
		 // @formatter:on
	}

	/**
	 * @param type the content type can be "text" or "image".
	 * @param source The source of the media content. Applicable for "image" types only.
	 * @param text The text of the message. Applicable for "text" types only.
	 * @param index The index of the content block. Applicable only for streaming
	 * responses.
	 */
	@JsonInclude(Include.NON_NULL)
	public record MediaContent( // @formatter:off
		@JsonProperty("type") Type type,
		@JsonProperty("source") Source source,
		@JsonProperty("text") String text,

		// applicable only for streaming responses.
		@JsonProperty("index") Integer index,

		// tool_use response only
		@JsonProperty("id") String id,
		@JsonProperty("name") String name,
		@JsonProperty("input") Map<String, Object> input,

		// tool_result response only
		@JsonProperty("tool_use_id") String toolUseId,
		@JsonProperty("content") String content
		) {
		// @formatter:on

		public MediaContent(String mediaType, String data) {
			this(new Source(mediaType, data));
		}

		public MediaContent(Source source) {
			this(Type.IMAGE, source, null, null, null, null, null, null, null);
		}

		public MediaContent(String text) {
			this(Type.TEXT, null, text, null, null, null, null, null, null);
		}

		// Tool result
		public MediaContent(Type type, String toolUseId, String content) {
			this(type, null, null, null, null, null, null, toolUseId, content);
		}

		public MediaContent(Type type, Source source, String text, Integer index) {
			this(type, source, text, index, null, null, null, null, null);
		}

		/**
		 * The type of this message.
		 */
		public enum Type {

			/**
			 * Tool request
			 */
			@JsonProperty("tool_use")
			TOOL_USE,

			/**
			 * Send tool result back to LLM.
			 */
			@JsonProperty("tool_result")
			TOOL_RESULT,

			/**
			 * Text message.
			 */
			@JsonProperty("text")
			TEXT,

			/**
			 * Text delta message. Returned from the streaming response.
			 */
			@JsonProperty("text_delta")
			TEXT_DELTA,

			/**
			 * Image message.
			 */
			@JsonProperty("image")
			IMAGE;

		}

		/**
		 * The source of the media content. (Applicable for "image" types only)
		 *
		 * @param type The type of the media content. Only "base64" is supported at the
		 * moment.
		 * @param mediaType The media type of the content. For example, "image/png" or
		 * "image/jpeg".
		 * @param data The base64-encoded data of the content.
		 */
		@JsonInclude(Include.NON_NULL)
		public record Source( // @formatter:off
			@JsonProperty("type") String type,
			@JsonProperty("media_type") String mediaType,
			@JsonProperty("data") String data) {
			// @formatter:on

			public Source(String mediaType, String data) {
				this("base64", mediaType, data);
			}
		}
	}

	@JsonInclude(Include.NON_NULL)
	public record Tool(// @formatter:off
		@JsonProperty("name") String name,
		@JsonProperty("description") String description,
		@JsonProperty("input_schema") Map<String, Object> inputSchema) {
		// @formatter:on
	}

	/**
	 * @param id Unique object identifier. The format and length of IDs may change over
	 * time.
	 * @param type Object type. For Messages, this is always "message".
	 * @param role Conversational role of the generated message. This will always be
	 * "assistant".
	 * @param content Content generated by the model. This is an array of content blocks.
	 * @param model The model that handled the request.
	 * @param stopReason The reason the model stopped generating tokens. This will be one
	 * of "end_turn", "max_tokens", "stop_sequence", or "timeout".
	 * @param stopSequence Which custom stop sequence was generated, if any.
	 * @param usage Input and output token usage.
	 */
	@JsonInclude(Include.NON_NULL)
	public record ChatCompletion( // @formatter:off
		@JsonProperty("id") String id,
		@JsonProperty("type") String type,
		@JsonProperty("role") Role role,
		@JsonProperty("content") List<MediaContent> content,
		@JsonProperty("model") String model,
		@JsonProperty("stop_reason") String stopReason,
		@JsonProperty("stop_sequence") String stopSequence,
		@JsonProperty("usage") Usage usage) {
		// @formatter:on
	}

	/**
	 * Usage statistics.
	 *
	 * @param inputTokens The number of input tokens which were used.
	 * @param outputTokens The number of output tokens which were used. completion).
	 */
	@JsonInclude(Include.NON_NULL)
	public record Usage( // @formatter:off
		 @JsonProperty("input_tokens") Integer inputTokens,
		 @JsonProperty("output_tokens") Integer outputTokens) {
		 // @formatter:off
	}

	/**
	 * The role of the author of this message.
	 */
	public enum Role { // @formatter:off
		 @JsonProperty("user") USER,
		 @JsonProperty("assistant") ASSISTANT
		 // @formatter:on

	}

	/**
	 * Streaming chat completion response. Provides partial information for either the
	 * ResponseMessage or its MediaContent. The event type defines what partial
	 * information is provided.
	 *
	 * @param type The server event type of the stream response. Each stream uses the
	 * following event flow: (1) 'message_start': contains a Message object with empty
	 * content; (2) A series of content blocks, each of which have a
	 * 'content_block_start', one or more 'content_block_delta events', and a
	 * 'content_block_stop' event. Each content block will have an 'index' that
	 * corresponds to its index in the final Message content array.
	 * @param index The index of the content block. Applicable only for "content_block"
	 * type.
	 * @param message The message object. Applicable only for "message_start" type.
	 * @param contentBlock The content block object. Applicable only for "content_block"
	 * type.
	 * @param delta The delta object. Applicable only for "content_block_delta" and
	 * "message_delta" types.
	 *
	 */
	@JsonInclude(Include.NON_NULL)
	public record StreamResponse( // @formatter:off
		@JsonProperty("type") String type,
		@JsonProperty("index") Integer index,
		@JsonProperty("message") ChatCompletion message,
		@JsonProperty("content_block") MediaContent contentBlock,
		@JsonProperty("delta") Map<String, Object> delta) {
		// @formatter:on
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

		return this.restClient.post().uri("/v1/messages").body(chatRequest).retrieve().toEntity(ChatCompletion.class);
	}

	/**
	 * Creates a streaming chat response for the given chat conversation.
	 * @param chatRequest The chat completion request. Must have the stream property set
	 * to true.
	 * @return Returns a {@link Flux} stream from chat completion chunks.
	 */
	public Flux<StreamResponse> chatCompletionStream(ChatCompletionRequest chatRequest) {

		Assert.notNull(chatRequest, "The request body can not be null.");
		Assert.isTrue(chatRequest.stream(), "Request must set the steam property to true.");

		return this.webClient.post()
			.uri("/v1/messages")
			.body(Mono.just(chatRequest), ChatCompletionRequest.class)
			.retrieve()
			.bodyToFlux(String.class)
			.takeUntil(SSE_DONE_PREDICATE)
			.filter(SSE_DONE_PREDICATE.negate())
			.map(content -> ModelOptionsUtils.jsonToObject(content, StreamResponse.class));
	}

}
