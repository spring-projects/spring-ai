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

package org.springframework.ai.anthropic.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.ai.anthropic.api.StreamHelper.ChatCompletionResponseBuilder;
import org.springframework.ai.model.ChatModelDescription;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * The Anthropic API client.
 *
 * @author Christian Tzolov
 * @author Mariusz Bernacki
 * @author Thomas Vitale
 * @author Jihoon Kim
 * @author Alexandros Pappas
 * @author Jonghoon Park
 * @author Claudio Silva Junior
 * @since 1.0.0
 */
public class AnthropicApi {

	public static Builder builder() {
		return new Builder();
	}

	public static final String PROVIDER_NAME = AiProvider.ANTHROPIC.value();

	public static final String DEFAULT_BASE_URL = "https://api.anthropic.com";

	public static final String DEFAULT_MESSAGE_COMPLETIONS_PATH = "/v1/messages";

	public static final String DEFAULT_ANTHROPIC_VERSION = "2023-06-01";

	public static final String DEFAULT_ANTHROPIC_BETA_VERSION = "tools-2024-04-04,pdfs-2024-09-25";

	public static final String BETA_MAX_TOKENS = "max-tokens-3-5-sonnet-2024-07-15";

	private static final String HEADER_X_API_KEY = "x-api-key";

	private static final String HEADER_ANTHROPIC_VERSION = "anthropic-version";

	private static final String HEADER_ANTHROPIC_BETA = "anthropic-beta";

	private static final Predicate<String> SSE_DONE_PREDICATE = "[DONE]"::equals;

	private final String completionsPath;

	private final RestClient restClient;

	private final StreamHelper streamHelper = new StreamHelper();

	private final WebClient webClient;

	/**
	 * Create a new client api.
	 * @param baseUrl api base URL.
	 * @param completionsPath path to append to the base URL.
	 * @param anthropicApiKey Anthropic api Key.
	 * @param anthropicVersion Anthropic version.
	 * @param restClientBuilder RestClient builder.
	 * @param webClientBuilder WebClient builder.
	 * @param responseErrorHandler Response error handler.
	 * @param anthropicBetaFeatures Anthropic beta features.
	 */
	private AnthropicApi(String baseUrl, String completionsPath, String anthropicApiKey, String anthropicVersion,
			RestClient.Builder restClientBuilder, WebClient.Builder webClientBuilder,
			ResponseErrorHandler responseErrorHandler, String anthropicBetaFeatures) {

		Consumer<HttpHeaders> jsonContentHeaders = headers -> {
			headers.add(HEADER_X_API_KEY, anthropicApiKey);
			headers.add(HEADER_ANTHROPIC_VERSION, anthropicVersion);
			headers.add(HEADER_ANTHROPIC_BETA, anthropicBetaFeatures);
			headers.setContentType(MediaType.APPLICATION_JSON);
		};

		this.completionsPath = completionsPath;

		this.restClient = restClientBuilder.clone()
			.baseUrl(baseUrl)
			.defaultHeaders(jsonContentHeaders)
			.defaultStatusHandler(responseErrorHandler)
			.build();

		this.webClient = webClientBuilder.clone()
			.baseUrl(baseUrl)
			.defaultHeaders(jsonContentHeaders)
			.defaultStatusHandler(HttpStatusCode::isError,
					resp -> resp.bodyToMono(String.class)
						.flatMap(it -> Mono.error(new RuntimeException(
								"Response exception, Status: [" + resp.statusCode() + "], Body:[" + it + "]"))))
			.build();
	}

	/**
	 * Creates a model response for the given chat conversation.
	 * @param chatRequest The chat completion request.
	 * @return Entity response with {@link ChatCompletionResponse} as a body and HTTP
	 * status code and headers.
	 */
	public ResponseEntity<ChatCompletionResponse> chatCompletionEntity(ChatCompletionRequest chatRequest) {
		return chatCompletionEntity(chatRequest, new LinkedMultiValueMap<>());
	}

	/**
	 * Creates a model response for the given chat conversation.
	 * @param chatRequest The chat completion request.
	 * @param additionalHttpHeader Additional HTTP headers.
	 * @return Entity response with {@link ChatCompletionResponse} as a body and HTTP
	 * status code and headers.
	 */
	public ResponseEntity<ChatCompletionResponse> chatCompletionEntity(ChatCompletionRequest chatRequest,
			MultiValueMap<String, String> additionalHttpHeader) {

		Assert.notNull(chatRequest, "The request body can not be null.");
		Assert.isTrue(!chatRequest.stream(), "Request must set the stream property to false.");
		Assert.notNull(additionalHttpHeader, "The additional HTTP headers can not be null.");

		return this.restClient.post()
			.uri(this.completionsPath)
			.headers(headers -> headers.addAll(additionalHttpHeader))
			.body(chatRequest)
			.retrieve()
			.toEntity(ChatCompletionResponse.class);
	}

	/**
	 * Creates a streaming chat response for the given chat conversation.
	 * @param chatRequest The chat completion request. Must have the stream property set
	 * to true.
	 * @return Returns a {@link Flux} stream from chat completion chunks.
	 */
	public Flux<ChatCompletionResponse> chatCompletionStream(ChatCompletionRequest chatRequest) {
		return chatCompletionStream(chatRequest, new LinkedMultiValueMap<>());
	}

	/**
	 * Creates a streaming chat response for the given chat conversation.
	 * @param chatRequest The chat completion request. Must have the stream property set
	 * to true.
	 * @param additionalHttpHeader Additional HTTP headers.
	 * @return Returns a {@link Flux} stream from chat completion chunks.
	 */
	public Flux<ChatCompletionResponse> chatCompletionStream(ChatCompletionRequest chatRequest,
			MultiValueMap<String, String> additionalHttpHeader) {

		Assert.notNull(chatRequest, "The request body can not be null.");
		Assert.isTrue(chatRequest.stream(), "Request must set the stream property to true.");
		Assert.notNull(additionalHttpHeader, "The additional HTTP headers can not be null.");

		AtomicBoolean isInsideTool = new AtomicBoolean(false);

		AtomicReference<ChatCompletionResponseBuilder> chatCompletionReference = new AtomicReference<>();

		return this.webClient.post()
			.uri(this.completionsPath)
			.headers(headers -> headers.addAll(additionalHttpHeader))
			.body(Mono.just(chatRequest), ChatCompletionRequest.class)
			.retrieve()
			.bodyToFlux(String.class)
			.takeUntil(SSE_DONE_PREDICATE)
			.filter(SSE_DONE_PREDICATE.negate())
			.map(content -> ModelOptionsUtils.jsonToObject(content, StreamEvent.class))
			.filter(event -> event.type() != EventType.PING)
			// Detect if the chunk is part of a streaming function call.
			.map(event -> {
				if (this.streamHelper.isToolUseStart(event)) {
					isInsideTool.set(true);
				}
				return event;
			})
			// Group all chunks belonging to the same function call.
			.windowUntil(event -> {
				if (isInsideTool.get() && this.streamHelper.isToolUseFinish(event)) {
					isInsideTool.set(false);
					return true;
				}
				return !isInsideTool.get();
			})
			// Merging the window chunks into a single chunk.
			.concatMapIterable(window -> {
				Mono<StreamEvent> monoChunk = window.reduce(new ToolUseAggregationEvent(),
						this.streamHelper::mergeToolUseEvents);
				return List.of(monoChunk);
			})
			.flatMap(mono -> mono)
			.map(event -> this.streamHelper.eventToChatCompletionResponse(event, chatCompletionReference))
			.filter(chatCompletionResponse -> chatCompletionResponse.type() != null);
	}

	/**
	 * Check the <a href="https://docs.anthropic.com/claude/docs/models-overview">Models
	 * overview</a> and <a href=
	 * "https://docs.anthropic.com/claude/docs/models-overview#model-comparison">model
	 * comparison</a> for additional details and options.
	 */
	public enum ChatModel implements ChatModelDescription {

		// @formatter:off
		/**
		 * The claude-3-7-sonnet-latest model.
		 */
		CLAUDE_3_7_SONNET("claude-3-7-sonnet-latest"),

		/**
		 * The claude-3-5-sonnet-latest model.
		 */
		CLAUDE_3_5_SONNET("claude-3-5-sonnet-latest"),

		/**
		 * The CLAUDE_3_OPUS
		 */
		CLAUDE_3_OPUS("claude-3-opus-latest"),

		/**
		 * The CLAUDE_3_SONNET (Deprecated. To be removed on July 21, 2025)
		 */
		CLAUDE_3_SONNET("claude-3-sonnet-20240229"),

		/**
		 * The CLAUDE 3.5 HAIKU
		 */
		CLAUDE_3_5_HAIKU("claude-3-5-haiku-latest"),

		/**
		 * The CLAUDE_3_HAIKU
		 */
		CLAUDE_3_HAIKU("claude-3-haiku-20240307"),

		// Legacy models
		/**
		 * The CLAUDE_2_1 (Deprecated. To be removed on July 21, 2025)
		 */
		CLAUDE_2_1("claude-2.1"),

		/**
		 * The CLAUDE_2_0 (Deprecated. To be removed on July 21, 2025)
		 */
		CLAUDE_2("claude-2.0");

		// @formatter:on

		private final String value;

		ChatModel(String value) {
			this.value = value;
		}

		/**
		 * Get the value of the model.
		 * @return The value of the model.
		 */
		public String getValue() {
			return this.value;
		}

		/**
		 * Get the name of the model.
		 * @return The name of the model.
		 */
		@Override
		public String getName() {
			return this.value;
		}

	}

	/**
	 * The role of the author of this message.
	 */
	public enum Role {

		// @formatter:off
		/**
		 * The user role.
		  */
		@JsonProperty("user")
		USER,

		/**
		 * The assistant role.
		 */
		@JsonProperty("assistant")
		ASSISTANT
		// @formatter:on

	}

	/**
	 * The thinking type.
	 */
	public enum ThinkingType {

		/**
		 * Enabled thinking type.
		 */
		@JsonProperty("enabled")
		ENABLED,

		/**
		 * Disabled thinking type.
		 */
		@JsonProperty("disabled")
		DISABLED

	}

	/**
	 * The event type of the streamed chunk.
	 */
	public enum EventType {

		/**
		 * Message start event. Contains a Message object with empty content.
		 */
		@JsonProperty("message_start")
		MESSAGE_START,

		/**
		 * Message delta event, indicating top-level changes to the final Message object.
		 */
		@JsonProperty("message_delta")
		MESSAGE_DELTA,

		/**
		 * A final message stop event.
		 */
		@JsonProperty("message_stop")
		MESSAGE_STOP,

		/**
		 * Content block start event.
		 */
		@JsonProperty("content_block_start")
		CONTENT_BLOCK_START,

		/**
		 * Content block delta event.
		 */
		@JsonProperty("content_block_delta")
		CONTENT_BLOCK_DELTA,

		/**
		 * A final content block stop event.
		 */
		@JsonProperty("content_block_stop")
		CONTENT_BLOCK_STOP,

		/**
		 * Error event.
		 */
		@JsonProperty("error")
		ERROR,

		/**
		 * Ping event.
		 */
		@JsonProperty("ping")
		PING,

		/**
		 * Artificially created event to aggregate tool use events.
		 */
		TOOL_USE_AGGREGATE

	}

	@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type",
			visible = true)
	@JsonSubTypes({ @JsonSubTypes.Type(value = ContentBlockStartEvent.class, name = "content_block_start"),
			@JsonSubTypes.Type(value = ContentBlockDeltaEvent.class, name = "content_block_delta"),
			@JsonSubTypes.Type(value = ContentBlockStopEvent.class, name = "content_block_stop"),
			@JsonSubTypes.Type(value = PingEvent.class, name = "ping"),
			@JsonSubTypes.Type(value = ErrorEvent.class, name = "error"),
			@JsonSubTypes.Type(value = MessageStartEvent.class, name = "message_start"),
			@JsonSubTypes.Type(value = MessageDeltaEvent.class, name = "message_delta"),
			@JsonSubTypes.Type(value = MessageStopEvent.class, name = "message_stop") })
	public interface StreamEvent {

		@JsonProperty("type")
		EventType type();

	}

	/**
	 * Chat completion request object.
	 *
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
	 * @param thinking Configuration for the model's thinking mode. When enabled, the
	 * model can perform more in-depth reasoning before responding to a query.
	 */
	@JsonInclude(Include.NON_NULL)
	public record ChatCompletionRequest(
	// @formatter:off
		@JsonProperty("model") String model,
		@JsonProperty("messages") List<AnthropicMessage> messages,
		@JsonProperty("system") String system,
		@JsonProperty("max_tokens") Integer maxTokens,
		@JsonProperty("metadata") Metadata metadata,
		@JsonProperty("stop_sequences") List<String> stopSequences,
		@JsonProperty("stream") Boolean stream,
		@JsonProperty("temperature") Double temperature,
		@JsonProperty("top_p") Double topP,
		@JsonProperty("top_k") Integer topK,
		@JsonProperty("tools") List<Tool> tools,
		@JsonProperty("thinking") ThinkingConfig thinking) {
		// @formatter:on

		public ChatCompletionRequest(String model, List<AnthropicMessage> messages, String system, Integer maxTokens,
				Double temperature, Boolean stream) {
			this(model, messages, system, maxTokens, null, null, stream, temperature, null, null, null, null);
		}

		public ChatCompletionRequest(String model, List<AnthropicMessage> messages, String system, Integer maxTokens,
				List<String> stopSequences, Double temperature, Boolean stream) {
			this(model, messages, system, maxTokens, null, stopSequences, stream, temperature, null, null, null, null);
		}

		public static ChatCompletionRequestBuilder builder() {
			return new ChatCompletionRequestBuilder();
		}

		public static ChatCompletionRequestBuilder from(ChatCompletionRequest request) {
			return new ChatCompletionRequestBuilder(request);
		}

		/**
		 * Metadata about the request.
		 *
		 * @param userId An external identifier for the user who is associated with the
		 * request. This should be a uuid, hash value, or other opaque identifier.
		 * Anthropic may use this id to help detect abuse. Do not include any identifying
		 * information such as name, email address, or phone number.
		 */
		@JsonInclude(Include.NON_NULL)
		public record Metadata(@JsonProperty("user_id") String userId) {

		}

		/**
		 * Configuration for the model's thinking mode.
		 *
		 * @param type The type of thinking mode. Currently, "enabled" is supported.
		 * @param budgetTokens The token budget available for the thinking process. Must
		 * be ≥1024 and less than max_tokens.
		 */
		@JsonInclude(Include.NON_NULL)
		public record ThinkingConfig(@JsonProperty("type") ThinkingType type,
				@JsonProperty("budget_tokens") Integer budgetTokens) {
		}

	}

	public static final class ChatCompletionRequestBuilder {

		private String model;

		private List<AnthropicMessage> messages;

		private String system;

		private Integer maxTokens;

		private ChatCompletionRequest.Metadata metadata;

		private List<String> stopSequences;

		private Boolean stream = false;

		private Double temperature;

		private Double topP;

		private Integer topK;

		private List<Tool> tools;

		private ChatCompletionRequest.ThinkingConfig thinking;

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
			this.thinking = request.thinking;
		}

		public ChatCompletionRequestBuilder model(ChatModel model) {
			this.model = model.getValue();
			return this;
		}

		public ChatCompletionRequestBuilder model(String model) {
			this.model = model;
			return this;
		}

		public ChatCompletionRequestBuilder messages(List<AnthropicMessage> messages) {
			this.messages = messages;
			return this;
		}

		public ChatCompletionRequestBuilder system(String system) {
			this.system = system;
			return this;
		}

		public ChatCompletionRequestBuilder maxTokens(Integer maxTokens) {
			this.maxTokens = maxTokens;
			return this;
		}

		public ChatCompletionRequestBuilder metadata(ChatCompletionRequest.Metadata metadata) {
			this.metadata = metadata;
			return this;
		}

		public ChatCompletionRequestBuilder stopSequences(List<String> stopSequences) {
			this.stopSequences = stopSequences;
			return this;
		}

		public ChatCompletionRequestBuilder stream(Boolean stream) {
			this.stream = stream;
			return this;
		}

		public ChatCompletionRequestBuilder temperature(Double temperature) {
			this.temperature = temperature;
			return this;
		}

		public ChatCompletionRequestBuilder topP(Double topP) {
			this.topP = topP;
			return this;
		}

		public ChatCompletionRequestBuilder topK(Integer topK) {
			this.topK = topK;
			return this;
		}

		public ChatCompletionRequestBuilder tools(List<Tool> tools) {
			this.tools = tools;
			return this;
		}

		public ChatCompletionRequestBuilder thinking(ChatCompletionRequest.ThinkingConfig thinking) {
			this.thinking = thinking;
			return this;
		}

		public ChatCompletionRequestBuilder thinking(ThinkingType type, Integer budgetTokens) {
			this.thinking = new ChatCompletionRequest.ThinkingConfig(type, budgetTokens);
			return this;
		}

		public ChatCompletionRequest build() {
			return new ChatCompletionRequest(this.model, this.messages, this.system, this.maxTokens, this.metadata,
					this.stopSequences, this.stream, this.temperature, this.topP, this.topK, this.tools, this.thinking);
		}

	}

	///////////////////////////////////////
	/// ERROR EVENT
	///////////////////////////////////////

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
	public record AnthropicMessage(
	// @formatter:off
		@JsonProperty("content") List<ContentBlock> content,
		@JsonProperty("role") Role role) {
		// @formatter:on
	}

	/**
	 * The content block of the message.
	 *
	 * @param type the content type can be "text", "image", "tool_use", "tool_result" or
	 * "text_delta".
	 * @param source The source of the media content. Applicable for "image" types only.
	 * @param text The text of the message. Applicable for "text" types only.
	 * @param index The index of the content block. Applicable only for streaming
	 * responses.
	 * @param id The id of the tool use. Applicable only for tool_use response.
	 * @param name The name of the tool use. Applicable only for tool_use response.
	 * @param input The input of the tool use. Applicable only for tool_use response.
	 * @param toolUseId The id of the tool use. Applicable only for tool_result response.
	 * @param content The content of the tool result. Applicable only for tool_result
	 * response.
	 */
	@JsonInclude(Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ContentBlock(
	// @formatter:off
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
		@JsonProperty("content") String content,

		// Thinking only
		@JsonProperty("signature") String signature,
		@JsonProperty("thinking") String thinking,

		// Redacted Thinking only
		@JsonProperty("data") String data
		) {
		// @formatter:on

		/**
		 * Create content block
		 * @param mediaType The media type of the content.
		 * @param data The content data.
		 */
		public ContentBlock(String mediaType, String data) {
			this(new Source(mediaType, data));
		}

		/**
		 * Create content block
		 * @param type The type of the content.
		 * @param source The source of the content.
		 */
		public ContentBlock(Type type, Source source) {
			this(type, source, null, null, null, null, null, null, null, null, null, null);
		}

		/**
		 * Create content block
		 * @param source The source of the content.
		 */
		public ContentBlock(Source source) {
			this(Type.IMAGE, source, null, null, null, null, null, null, null, null, null, null);
		}

		/**
		 * Create content block
		 * @param text The text of the content.
		 */
		public ContentBlock(String text) {
			this(Type.TEXT, null, text, null, null, null, null, null, null, null, null, null);
		}

		// Tool result
		/**
		 * Create content block
		 * @param type The type of the content.
		 * @param toolUseId The id of the tool use.
		 * @param content The content of the tool result.
		 */
		public ContentBlock(Type type, String toolUseId, String content) {
			this(type, null, null, null, null, null, null, toolUseId, content, null, null, null);
		}

		/**
		 * Create content block
		 * @param type The type of the content.
		 * @param source The source of the content.
		 * @param text The text of the content.
		 * @param index The index of the content block.
		 */
		public ContentBlock(Type type, Source source, String text, Integer index) {
			this(type, source, text, index, null, null, null, null, null, null, null, null);
		}

		// Tool use input JSON delta streaming
		/**
		 * Create content block
		 * @param type The type of the content.
		 * @param id The id of the tool use.
		 * @param name The name of the tool use.
		 * @param input The input of the tool use.
		 */
		public ContentBlock(Type type, String id, String name, Map<String, Object> input) {
			this(type, null, null, null, id, name, input, null, null, null, null, null);
		}

		/**
		 * The ContentBlock type.
		 */
		public enum Type {

			/**
			 * Tool request
			 */
			@JsonProperty("tool_use")
			TOOL_USE("tool_use"),

			/**
			 * Send tool result back to LLM.
			 */
			@JsonProperty("tool_result")
			TOOL_RESULT("tool_result"),

			/**
			 * Text message.
			 */
			@JsonProperty("text")
			TEXT("text"),

			/**
			 * Text delta message. Returned from the streaming response.
			 */
			@JsonProperty("text_delta")
			TEXT_DELTA("text_delta"),

			/**
			 * When using extended thinking with streaming enabled, you’ll receive
			 * thinking content via thinking_delta events. These deltas correspond to the
			 * thinking field of the thinking content blocks.
			 */
			@JsonProperty("thinking_delta")
			THINKING_DELTA("thinking_delta"),

			/**
			 * For thinking content, a special signature_delta event is sent just before
			 * the content_block_stop event. This signature is used to verify the
			 * integrity of the thinking block.
			 */
			@JsonProperty("signature_delta")
			SIGNATURE_DELTA("signature_delta"),

			/**
			 * Tool use input partial JSON delta streaming.
			 */
			@JsonProperty("input_json_delta")
			INPUT_JSON_DELTA("input_json_delta"),

			/**
			 * Image message.
			 */
			@JsonProperty("image")
			IMAGE("image"),

			/**
			 * Document message.
			 */
			@JsonProperty("document")
			DOCUMENT("document"),

			/**
			 * Thinking message.
			 */
			@JsonProperty("thinking")
			THINKING("thinking"),

			/**
			 * Redacted Thinking message.
			 */
			@JsonProperty("redacted_thinking")
			REDACTED_THINKING("redacted_thinking");

			public final String value;

			Type(String value) {
				this.value = value;
			}

			/**
			 * Get the value of the type.
			 * @return The value of the type.
			 */
			public String getValue() {
				return this.value;
			}

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
		public record Source(
		// @formatter:off
			@JsonProperty("type") String type,
			@JsonProperty("media_type") String mediaType,
			@JsonProperty("data") String data,
			@JsonProperty("url") String url) {
			// @formatter:on

			/**
			 * Create source
			 * @param mediaType The media type of the content.
			 * @param data The content data.
			 */
			public Source(String mediaType, String data) {
				this("base64", mediaType, data, null);
			}

			public Source(String url) {
				this("url", null, null, url);
			}

		}

	}

	///////////////////////////////////////
	/// CONTENT_BLOCK EVENTS
	///////////////////////////////////////

	/**
	 * Tool description.
	 *
	 * @param name The name of the tool.
	 * @param description A description of the tool.
	 * @param inputSchema The input schema of the tool.
	 */
	@JsonInclude(Include.NON_NULL)
	public record Tool(
	// @formatter:off
		@JsonProperty("name") String name,
		@JsonProperty("description") String description,
		@JsonProperty("input_schema") Map<String, Object> inputSchema) {
		// @formatter:on
	}

	// CB START EVENT

	/**
	 * Chat completion response object.
	 *
	 * @param id Unique object identifier. The format and length of IDs may change over
	 * time.
	 * @param type Object type. For Messages, this is always "message".
	 * @param role Conversational role of the generated message. This will always be
	 * "assistant".
	 * @param content Content generated by the model. This is an array of content blocks.
	 * @param model The model that handled the request.
	 * @param stopReason The reason the model stopped generating tokens. This will be one
	 * of "end_turn", "max_tokens", "stop_sequence", "tool_use", or "timeout".
	 * @param stopSequence Which custom stop sequence was generated, if any.
	 * @param usage Input and output token usage.
	 */
	@JsonInclude(Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ChatCompletionResponse(
	// @formatter:off
		@JsonProperty("id") String id,
		@JsonProperty("type") String type,
		@JsonProperty("role") Role role,
		@JsonProperty("content") List<ContentBlock> content,
		@JsonProperty("model") String model,
		@JsonProperty("stop_reason") String stopReason,
		@JsonProperty("stop_sequence") String stopSequence,
		@JsonProperty("usage") Usage usage) {
		// @formatter:on
	}

	// CB DELTA EVENT

	/**
	 * Usage statistics.
	 *
	 * @param inputTokens The number of input tokens which were used.
	 * @param outputTokens The number of output tokens which were used. completion).
	 */
	@JsonInclude(Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Usage(
	// @formatter:off
		@JsonProperty("input_tokens") Integer inputTokens,
		@JsonProperty("output_tokens") Integer outputTokens) {
		// @formatter:off
	}

	 /// ECB STOP

	/**
	 * Special event used to aggregate multiple tool use events into a single event with
	 * list of aggregated ContentBlockToolUse.
	*/
	public static class ToolUseAggregationEvent implements StreamEvent {

		private Integer index;

		private String id;

		private String name;

		private String partialJson = "";

		private List<ContentBlockStartEvent.ContentBlockToolUse> toolContentBlocks = new ArrayList<>();

		@Override
		public EventType type() {
			return EventType.TOOL_USE_AGGREGATE;
		}

		/**
		  * Get tool content blocks.
		  * @return The tool content blocks.
		*/
		public List<ContentBlockStartEvent.ContentBlockToolUse> getToolContentBlocks() {
			return this.toolContentBlocks;
		}

		/**
		  * Check if the event is empty.
		  * @return True if the event is empty, false otherwise.
		*/
		public boolean isEmpty() {
			return (this.index == null || this.id == null || this.name == null
					|| !StringUtils.hasText(this.partialJson));
		}

		ToolUseAggregationEvent withIndex(Integer index) {
			this.index = index;
			return this;
		}

		ToolUseAggregationEvent withId(String id) {
			this.id = id;
			return this;
		}

		ToolUseAggregationEvent withName(String name) {
			this.name = name;
			return this;
		}

		ToolUseAggregationEvent appendPartialJson(String partialJson) {
			this.partialJson = this.partialJson + partialJson;
			return this;
		}

		void squashIntoContentBlock() {
			Map<String, Object> map = (StringUtils.hasText(this.partialJson))
					? ModelOptionsUtils.jsonToMap(this.partialJson) : Map.of();
			this.toolContentBlocks.add(new ContentBlockStartEvent.ContentBlockToolUse("tool_use", this.id, this.name, map));
			this.index = null;
			this.id = null;
			this.name = null;
			this.partialJson = "";
		}

		@Override
		public String toString() {
			return "EventToolUseBuilder [index=" + this.index + ", id=" + this.id + ", name=" + this.name + ", partialJson="
					+ this.partialJson + ", toolUseMap=" + this.toolContentBlocks + "]";
		}

	}

	 ///////////////////////////////////////
	 /// MESSAGE EVENTS
	 ///////////////////////////////////////

	 // MESSAGE START EVENT

	/**
	 * Content block start event.
	 * @param type The event type.
	 * @param index The index of the content block.
	 * @param contentBlock The content block body.
	*/
	@JsonInclude(Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ContentBlockStartEvent(
			// @formatter:off
		@JsonProperty("type") EventType type,
		@JsonProperty("index") Integer index,
		@JsonProperty("content_block") ContentBlockBody contentBlock) implements StreamEvent {

		@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type",
				visible = true)
		@JsonSubTypes({ @JsonSubTypes.Type(value = ContentBlockToolUse.class, name = "tool_use"),
				@JsonSubTypes.Type(value = ContentBlockText.class, name = "text") })
		public interface ContentBlockBody {
			String type();
		}

		/**
		  * Tool use content block.
		  * @param type The content block type.
		  * @param id The tool use id.
		  * @param name The tool use name.
		  * @param input The tool use input.
		*/
		@JsonInclude(Include.NON_NULL)
		@JsonIgnoreProperties(ignoreUnknown = true)
		public record ContentBlockToolUse(
			@JsonProperty("type") String type,
			@JsonProperty("id") String id,
			@JsonProperty("name") String name,
			@JsonProperty("input") Map<String, Object> input) implements ContentBlockBody {
		}

		/**
		  * Text content block.
		  * @param type The content block type.
		  * @param text The text content.
		*/
		@JsonInclude(Include.NON_NULL)
		@JsonIgnoreProperties(ignoreUnknown = true)
		public record ContentBlockText(
			@JsonProperty("type") String type,
			@JsonProperty("text") String text) implements ContentBlockBody {
		}
	}
	// @formatter:on

	// MESSAGE DELTA EVENT

	/**
	 * Content block delta event.
	 *
	 * @param type The event type.
	 * @param index The index of the content block.
	 * @param delta The content block delta body.
	 */
	@JsonInclude(Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ContentBlockDeltaEvent(
	// @formatter:off
		@JsonProperty("type") EventType type,
		@JsonProperty("index") Integer index,
		@JsonProperty("delta") ContentBlockDeltaBody delta) implements StreamEvent {

		@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type",
				visible = true)
		@JsonSubTypes({ @JsonSubTypes.Type(value = ContentBlockDeltaText.class, name = "text_delta"),
				@JsonSubTypes.Type(value = ContentBlockDeltaJson.class, name = "input_json_delta"),
				@JsonSubTypes.Type(value = ContentBlockDeltaThinking.class, name = "thinking_delta"),
				@JsonSubTypes.Type(value = ContentBlockDeltaSignature.class, name = "signature_delta")
		})
		public interface ContentBlockDeltaBody {
			String type();
		}

		/**
		 * Text content block delta.
		 * @param type The content block type.
		 * @param text The text content.
		*/
		@JsonInclude(Include.NON_NULL)
		@JsonIgnoreProperties(ignoreUnknown = true)
		public record ContentBlockDeltaText(
			@JsonProperty("type") String type,
			@JsonProperty("text") String text) implements ContentBlockDeltaBody {
		}

		/**
		  * JSON content block delta.
		  * @param type The content block type.
		  * @param partialJson The partial JSON content.
		  */
		@JsonInclude(Include.NON_NULL)
		@JsonIgnoreProperties(ignoreUnknown = true)
		public record ContentBlockDeltaJson(
			@JsonProperty("type") String type,
			@JsonProperty("partial_json") String partialJson) implements ContentBlockDeltaBody {
		}

		/**
		 * Thinking content block delta.
		 * @param type The content block type.
		 * @param thinking The thinking content.
		 */
		@JsonInclude(Include.NON_NULL)
		@JsonIgnoreProperties(ignoreUnknown = true)
		public record ContentBlockDeltaThinking(
			@JsonProperty("type") String type,
			@JsonProperty("thinking") String thinking) implements ContentBlockDeltaBody {
		}

		/**
		 * Signature content block delta.
		 * @param type The content block type.
		 * @param signature The signature content.
		 */
		@JsonInclude(Include.NON_NULL)
		@JsonIgnoreProperties(ignoreUnknown = true)
		public record ContentBlockDeltaSignature(
			@JsonProperty("type") String type,
			@JsonProperty("signature") String signature) implements ContentBlockDeltaBody {
		}
	}
	// @formatter:on

	// MESSAGE STOP EVENT

	/**
	 * Content block stop event.
	 *
	 * @param type The event type.
	 * @param index The index of the content block.
	 */
	@JsonInclude(Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ContentBlockStopEvent(
	// @formatter:off
		@JsonProperty("type") EventType type,
		@JsonProperty("index") Integer index) implements StreamEvent {
	}
	// @formatter:on

	/**
	 * Message start event.
	 *
	 * @param type The event type.
	 * @param message The message body.
	 */
	@JsonInclude(Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record MessageStartEvent(// @formatter:off
		@JsonProperty("type") EventType type,
		@JsonProperty("message") ChatCompletionResponse message) implements StreamEvent {
	}
	// @formatter:on

	/**
	 * Message delta event.
	 *
	 * @param type The event type.
	 * @param delta The message delta body.
	 * @param usage The message delta usage.
	 */
	@JsonInclude(Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record MessageDeltaEvent(
	// @formatter:off
		@JsonProperty("type") EventType type,
		@JsonProperty("delta") MessageDelta delta,
		@JsonProperty("usage") MessageDeltaUsage usage) implements StreamEvent {

		/**
		  * @param stopReason The stop reason.
		  * @param stopSequence The stop sequence.
		  */
		@JsonInclude(Include.NON_NULL)
		@JsonIgnoreProperties(ignoreUnknown = true)
		public record MessageDelta(
			@JsonProperty("stop_reason") String stopReason,
			@JsonProperty("stop_sequence") String stopSequence) {
		}

		/**
		 * Message delta usage.
		 * @param outputTokens The output tokens.
		*/
		@JsonInclude(Include.NON_NULL)
		@JsonIgnoreProperties(ignoreUnknown = true)
		public record MessageDeltaUsage(
			@JsonProperty("output_tokens") Integer outputTokens) {
		}
	}
	// @formatter:on

	/**
	 * Message stop event.
	 *
	 * @param type The event type.
	 */
	@JsonInclude(Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record MessageStopEvent(
	//@formatter:off
		@JsonProperty("type") EventType type) implements StreamEvent {
	}
	// @formatter:on

	///////////////////////////////////////
	/// ERROR EVENT
	///////////////////////////////////////
	/**
	 * Error event.
	 *
	 * @param type The event type.
	 * @param error The error body.
	 */
	@JsonInclude(Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ErrorEvent(
	// @formatter:off
		@JsonProperty("type") EventType type,
		@JsonProperty("error") Error error) implements StreamEvent {

		/**
		 * Error body.
		 * @param type The error type.
		 * @param message The error message.
		*/
		@JsonInclude(Include.NON_NULL)
		@JsonIgnoreProperties(ignoreUnknown = true)
		public record Error(
			@JsonProperty("type") String type,
			@JsonProperty("message") String message) {
		}
	}
	// @formatter:on

	///////////////////////////////////////
	/// PING EVENT
	///////////////////////////////////////
	/**
	 * Ping event.
	 *
	 * @param type The event type.
	 */
	@JsonInclude(Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record PingEvent(
	// @formatter:off
		@JsonProperty("type") EventType type) implements StreamEvent {
	}
	// @formatter:on

	public static class Builder {

		private String baseUrl = DEFAULT_BASE_URL;

		private String completionsPath = DEFAULT_MESSAGE_COMPLETIONS_PATH;

		private String apiKey;

		private String anthropicVersion = DEFAULT_ANTHROPIC_VERSION;

		private RestClient.Builder restClientBuilder = RestClient.builder();

		private WebClient.Builder webClientBuilder = WebClient.builder();

		private ResponseErrorHandler responseErrorHandler = RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER;

		private String anthropicBetaFeatures = DEFAULT_ANTHROPIC_BETA_VERSION;

		public Builder baseUrl(String baseUrl) {
			Assert.hasText(baseUrl, "baseUrl cannot be null or empty");
			this.baseUrl = baseUrl;
			return this;
		}

		public Builder completionsPath(String completionsPath) {
			Assert.hasText(completionsPath, "completionsPath cannot be null or empty");
			this.completionsPath = completionsPath;
			return this;
		}

		public Builder apiKey(String apiKey) {
			Assert.notNull(apiKey, "apiKey cannot be null");
			this.apiKey = apiKey;
			return this;
		}

		public Builder anthropicVersion(String anthropicVersion) {
			Assert.notNull(anthropicVersion, "anthropicVersion cannot be null");
			this.anthropicVersion = anthropicVersion;
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

		public Builder anthropicBetaFeatures(String anthropicBetaFeatures) {
			Assert.notNull(anthropicBetaFeatures, "anthropicBetaFeatures cannot be null");
			this.anthropicBetaFeatures = anthropicBetaFeatures;
			return this;
		}

		public AnthropicApi build() {
			Assert.notNull(this.apiKey, "apiKey must be set");
			return new AnthropicApi(this.baseUrl, this.completionsPath, this.apiKey, this.anthropicVersion,
					this.restClientBuilder, this.webClientBuilder, this.responseErrorHandler,
					this.anthropicBetaFeatures);
		}

	}

}
