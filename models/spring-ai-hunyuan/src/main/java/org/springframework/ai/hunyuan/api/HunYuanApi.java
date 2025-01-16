/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.hunyuan.api;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.alibaba.fastjson2.JSONObject;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.hunyuan.api.auth.HunYuanAuthApi;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.ai.model.ChatModelDescription;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Single-class, Java Client library for Hunyuan platform. Provides implementation for
 * the <a href="https://cloud.tencent.com/document/api/1729/105701">Chat Completion</a> APIs.
 * <p>
 * Implements <b>Synchronous</b> and <b>Streaming</b> chat completion.
 * </p>
 *
 * @author Guo Junyu
 */
public class HunYuanApi {

	public static final String DEFAULT_CHAT_MODEL = ChatModel.HUNYUAN_PRO.getValue();

	private static final Predicate<String> SSE_DONE_PREDICATE = "[DONE]"::equals;

	private final RestClient restClient;

	private final WebClient webClient;

	private final HunYuanAuthApi hunyuanAuthApi;

	private final HunYuanStreamFunctionCallingHelper chunkMerger = new HunYuanStreamFunctionCallingHelper();

	private final ObjectMapper objectMapper;
	/**
	 * Create a new client api with DEFAULT_BASE_URL
	 * @param secretId Hunyuan SecretId.
	 * @param secretKey Hunyuan SecretKey.
	 */
	public HunYuanApi(String secretId, String secretKey) {
		this(HunYuanConstants.DEFAULT_BASE_URL, secretId, secretKey);
	}

	/**
	 * Create a new client api.
	 * @param baseUrl api base URL.
	 * @param secretId Hunyuan SecretId.
	 * @param secretKey Hunyuan SecretKey.
	 */
	public HunYuanApi(String baseUrl, String secretId, String secretKey) {
		this(baseUrl, secretId, secretKey, RestClient.builder(), RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER);
	}

	/**
	 * Create a new client api.
	 * @param baseUrl api base URL.
	 * @param secretKey Hunyuan api Key.
	 * @param restClientBuilder RestClient builder.
	 * @param responseErrorHandler Response error handler.
	 */
	public HunYuanApi(String baseUrl, String secretId, String secretKey, RestClient.Builder restClientBuilder,
					  ResponseErrorHandler responseErrorHandler) {

		Consumer<HttpHeaders> jsonContentHeaders = headers -> {
			headers.setContentType(MediaType.APPLICATION_JSON);
		};
		hunyuanAuthApi = new HunYuanAuthApi(secretId, secretKey);
		this.objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		this.restClient = restClientBuilder.baseUrl(baseUrl)
			.defaultHeaders(jsonContentHeaders)
			.defaultStatusHandler(responseErrorHandler)
			.build();

		this.webClient = WebClient.builder().baseUrl(baseUrl).defaultHeaders(jsonContentHeaders).build();
	}

	/**
	 * Creates a model response for the given chat conversation.
	 * @param chatRequest The chat completion request.
	 * @return Entity response with {@link ChatCompletion} as a body and HTTP status code
	 * and headers.
	 */
	public ResponseEntity<ChatCompletionResponse> chatCompletionEntity(ChatCompletionRequest chatRequest) {

		Assert.notNull(chatRequest, "The request body can not be null.");
//		Assert.isTrue(!chatRequest.stream(), "Request must set the stream property to false.");
		String service = HunYuanConstants.DEFAULT_SERVICE;
		String host = HunYuanConstants.DEFAULT_CHAT_HOST;
//		String region = "ap-guangzhou";
		String action = HunYuanConstants.DEFAULT_CHAT_ACTION;
		MultiValueMap<String, String> jsonContentHeaders = hunyuanAuthApi.getHttpHeadersConsumer(host, action, service, chatRequest);
		ResponseEntity<String> retrieve = this.restClient.post()
				.uri("/")
				.headers(headers -> {
					headers.addAll(jsonContentHeaders);
				})
				.body(chatRequest)
				.retrieve()
				.toEntity(String.class);
		// 使用 ObjectMapper 将响应体字符串转换为 ChatCompletionResponse 对象
		ChatCompletionResponse chatCompletionResponse = null;
		try {
			chatCompletionResponse = objectMapper.readValue(retrieve.getBody(), ChatCompletionResponse.class);
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
		return ResponseEntity.ok(chatCompletionResponse);
	}

	/**
	 * Creates a streaming chat response for the given chat conversation.
	 * @param chatRequest The chat completion request. Must have the stream property set
	 * to true.
	 * @return Returns a {@link Flux} stream from chat completion chunks.
	 */
	public Flux<ChatCompletionChunk> chatCompletionStream(ChatCompletionRequest chatRequest) {
		Assert.notNull(chatRequest, "The request body can not be null.");
		Assert.isTrue(chatRequest.stream(), "Request must set the steam property to true.");
		AtomicBoolean isInsideTool = new AtomicBoolean(false);

		return this.webClient.post()
			.uri("/v1/chat/completions")
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
						new ChatCompletionChunk(null, null, null, null, null),
						(previous, current) -> this.chunkMerger.merge(previous, current));
				return List.of(monoChunk);
			})
			// Flux<Mono<ChatCompletionChunk>> -> Flux<ChatCompletionChunk>
			.flatMap(mono -> mono);
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
	 * Hunyuan Chat Completion Models:
	 *
	 * <ul>
	 * <li><b>HUNYUAN_LITE</b> - hunyuan-lite</li>
	 * <li><b>HUNYUAN_STANDARD</b> - hunyuan-standard</li>
	 * <li><b>HUNYUAN_STANDARD_256K</b> - hunyuan-standard-256K</li>
	 * <li><b>HUNYUAN_PRO</b> - hunyuan-pro</li>
	 * <li><b>HUNYUAN_CODE</b> - hunyuan-code</li>
	 * <li><b>HUNYUAN_ROLE</b> - hunyuan-role</li>
	 * <li><b>HUNYUAN_FUNCTIONCALL</b> - hunyuan-functioncall</li>
	 * <li><b>HUNYUAN_VISION</b> - hunyuan-vision</li>
	 * <li><b>HUNYUAN_TURBO</b> - hunyuan-turbo</li>
	 * <li><b>HUNYUAN_TURBO_LATEST</b> - hunyuan-turbo-latest</li>
	 * <li><b>HUNYUAN_LARGE</b> - hunyuan-large</li>
	 * <li><b>HUNYUAN_LARGE_LONGCONTEXT</b> - hunyuan-large-longcontext</li>
	 * <li><b>HUNYUAN_TURBO_VISION</b> - hunyuan-turbo-vision</li>
	 * <li><b>HUNYUAN_STANDARD_VISION</b> - hunyuan-standard-vision</li>
	 * </ul>
	 */
	public enum ChatModel implements ChatModelDescription {

		// @formatter:off
		HUNYUAN_LITE("hunyuan-lite"),
		HUNYUAN_STANDARD("hunyuan-standard"),
		HUNYUAN_STANDARD_256K("hunyuan-standard-256K"),
		HUNYUAN_PRO("hunyuan-pro"),
		HUNYUAN_CODE("hunyuan-code"),
		HUNYUAN_ROLE("hunyuan-role"),
		HUNYUAN_FUNCTIONCALL("hunyuan-functioncall"),
		HUNYUAN_VISION("hunyuan-vision"),
		HUNYUAN_TURBO("hunyuan-turbo"),
		HUNYUAN_TURBO_LATEST("hunyuan-turbo-latest"),
		HUNYUAN_LARGE("hunyuan-large"),
		HUNYUAN_LARGE_LONGCONTEXT("hunyuan-large-longcontext"),
		HUNYUAN_TURBO_VISION("hunyuan-turbo-vision"),
		HUNYUAN_STANDARD_VISION("hunyuan-standard-vision");
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
		@JsonProperty("PromptTokens") Integer promptTokens,
		@JsonProperty("TotalTokens") Integer totalTokens,
		@JsonProperty("CompletionTokens") Integer completionTokens) {
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
	 * @param tools A list of tools the model may call. Currently, only functions are
	 * supported as a tool.
	 * @param toolChoice Controls which (if any) function is called by the model.
	 */
	@JsonInclude(Include.NON_NULL)
	public record ChatCompletionRequest(
	// @formatter:off
			@JsonProperty("Model") String model,
			@JsonProperty("Messages") List<ChatCompletionMessage> messages,
			@JsonProperty("Temperature") Double temperature,
			@JsonProperty("EnableEnhancement") Boolean enableEnhancement,
			@JsonProperty("TopP") Double topP,
			@JsonProperty("Stop") List<String> stop,
			@JsonProperty("Stream") Boolean stream,
			@JsonProperty("StreamModeration") Boolean streamModeration,
			@JsonProperty("Tools") List<FunctionTool> tools,
			@JsonProperty("ToolChoice") String toolChoice,
			@JsonProperty("CustomTool") FunctionTool customTool,
			@JsonProperty("SearchInfo") Boolean searchInfo,
			@JsonProperty("Citation") Boolean citation,
			@JsonProperty("EnableSpeedSearch") Boolean enableSpeedSearch,
			@JsonProperty("EnableMultimedia") Boolean enableMultimedia,
			@JsonProperty("EnableDeepSearch") Boolean enableDeepSearch,
			@JsonProperty("Seed") Integer customToolOutputSchemaExampleDescription,
			@JsonProperty("ForceSearchEnhancement") Boolean ForceSearchEnhancement,
			@JsonProperty("EnableRecommendedQuestions") Boolean enableRecommendedQuestions
	) {
		 // @formatter:on

		/**
		 * Shortcut constructor for a chat completion request with the given messages and
		 * model.
		 * @param messages The prompt(s) to generate completions for, encoded as a list of
		 * dict with role and content. The first prompt role should be user or system.
		 * @param model ID of the model to use.
		 */
		public ChatCompletionRequest(List<ChatCompletionMessage> messages, String model) {
			this(model,messages,null, null, null, null, null, null, null, null, null,null,null,null,null,null,null,null,null);
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
				Boolean stream) {
			this(model,messages, temperature, null, null, null, stream, null, null, null, null, null, null, null, null, null, null, null, null);
		}

		/**
		 * Shortcut constructor for a chat completion request with the given messages,
		 * model and temperature.
		 * @param messages The prompt(s) to generate completions for, encoded as a list of
		 * dict with role and content. The first prompt role should be user or system.
		 * @param model ID of the model to use.
		 * @param temperature What sampling temperature to use, between 0.0 and 1.0.
		 */
		public ChatCompletionRequest(List<ChatCompletionMessage> messages, String model, Double temperature) {
			this(model,messages, temperature, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
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
									 String toolChoice) {
			this(model,messages, null, null, null, null, null, null, tools, toolChoice, null, null, null, null, null, null, null, null, null);
		}

		/**
		 * Shortcut constructor for a chat completion request with the given messages and
		 * stream.
		 */
		public ChatCompletionRequest(List<ChatCompletionMessage> messages, Boolean stream) {
			this(DEFAULT_CHAT_MODEL, messages, null, null, null, null, stream, null, null, null, null, null, null, null, null, null, null, null, null);
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
		@JsonProperty("Content") Object rawContent,
		@JsonProperty("Role") Role role,
		@JsonProperty("Contents") List<ChatContent> name,
		@JsonProperty("ToolCallId") String toolCallId,
		@JsonProperty("ToolCalls") List<ToolCall> toolCalls
	// @formatter:on
	) {

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
		 * The role of the author of this message. NOTE: Hunyuan expects the system
		 * message to be before the user message or will fail with 400 error.
		 */
		public enum Role {

			/**
			 * System message.
			 */
			@JsonProperty("system")
			system,
			/**
			 * User message.
			 */
			@JsonProperty("user")
			user,
			/**
			 * Assistant message.
			 */
			@JsonProperty("assistant")
			assistant,
			/**
			 * Tool message.
			 */
			@JsonProperty("tool")
			tool
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
		public record ToolCall(@JsonProperty("id") String id, @JsonProperty("type") String type, @JsonProperty("Index") Integer index,
				@JsonProperty("function") ChatCompletionFunction function) {

		}

		@JsonInclude(Include.NON_NULL)
		public record ChatContent(@JsonProperty("Type") String type, @JsonProperty("Text") String text,
							   @JsonProperty("ImageUrl") ImageUrl imageUrl) {

		}
		@JsonInclude(Include.NON_NULL)
		public record ImageUrl(@JsonProperty("Url") String url) {

		}

		/**
		 * The function definition.
		 *
		 * @param name The name of the function.
		 * @param arguments The arguments that the model expects you to pass to the
		 * function.
		 */
		@JsonInclude(Include.NON_NULL)
		public record ChatCompletionFunction(@JsonProperty("Name") String name,
				@JsonProperty("Arguments") String arguments) {

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
	public record ChatCompletionResponse(
			// @formatter:off
			@JsonProperty("Response") ChatCompletion response
	) {
		// @formatter:on
	}
	@JsonInclude(Include.NON_NULL)
	public record ChatCompletion(
	// @formatter:off
	@JsonProperty("Id") String id,
	@JsonProperty("Error") ChatCompletion.ErrorMsg errorMsg,
	@JsonProperty("Created") Long created,
	@JsonProperty("Note") String note,
	@JsonProperty("Choices") List<ChatCompletion.Choice> choices,
	@JsonProperty("Usage") Usage usage,
	@JsonProperty("ModerationLevel") String moderationLevel,
	@JsonProperty("SearchInfo") ChatCompletion.SearchInfo searchInfo,
	@JsonProperty("Replaces") List<ChatCompletion.Replace>  replaces,
	@JsonProperty("RecommendedQuestions") List<String> recommendedQuestions,
	@JsonProperty("RequestId") String requestId
	) {




		@JsonInclude(Include.NON_NULL)
		public record Replace(
				// @formatter:off
				@JsonProperty("Id") String id,
				@JsonProperty("Multimedia") List<Multimedia> multimedias
		) {
			// @formatter:on
		}
		@JsonInclude(Include.NON_NULL)
		public record Multimedia(
				// @formatter:off
				@JsonProperty("Type") String type,
				@JsonProperty("Url") String url,
				@JsonProperty("JumpUrl") String jumpUrl,
				@JsonProperty("Title") String title,
				@JsonProperty("Desc") String desc,
				@JsonProperty("Singer") String singer,
				@JsonProperty("Ext") SongExt ext
		) {
			// @formatter:on
		}
		@JsonInclude(Include.NON_NULL)
		public record SongExt(
				// @formatter:off
				@JsonProperty("SongId") Integer songId,
				@JsonProperty("SongMid") String SongMid,
				@JsonProperty("Vip") Integer Vip
		) {
			// @formatter:on
		}
		 // @formatter:on
		 @JsonInclude(Include.NON_NULL)
		 public record SearchInfo(
				 // @formatter:off
				 @JsonProperty("SearchResults") List<SearchResults> searchResults,
				 @JsonProperty("Mindmap") Mindmap mindmap,
				 @JsonProperty("RelevantEvents") List<RelevantEvent> relevantEvents,
				 @JsonProperty("RelevantEntities") List<RelevantEntity> relevantEntities,
				 @JsonProperty("Timeline") List<Timeline>  timelines,
				 @JsonProperty("SupportDeepSearch") Boolean supportDeepSearch,
				 @JsonProperty("Outline") List<String> outlines
		 ) {
			 // @formatter:on
		 }
		@JsonInclude(Include.NON_NULL)
		public record Timeline(
				// @formatter:off
				@JsonProperty("Title") String title,
				@JsonProperty("Datetime") String datetime,
				@JsonProperty("Url") String  url
		) {
			// @formatter:on
		}
		@JsonInclude(Include.NON_NULL)
		public record RelevantEntity(
				// @formatter:off
				@JsonProperty("Name") String name,
				@JsonProperty("Content") String content,
				@JsonProperty("Reference") List<Integer>  reference
		) {
			// @formatter:on
		}
		@JsonInclude(Include.NON_NULL)
		public record RelevantEvent(
				// @formatter:off
				@JsonProperty("Title") String title,
				@JsonProperty("Content") String content,
				@JsonProperty("Datetime") String datetime,
				@JsonProperty("Reference") List<Integer>  reference
		) {
			// @formatter:on
		}

		@JsonInclude(Include.NON_NULL)
		public record Mindmap(
				// @formatter:off
				@JsonProperty("ThumbUrl") String thumbUrl,
				@JsonProperty("Url") String url
		) {
			// @formatter:on
		}
		@JsonInclude(Include.NON_NULL)
		public record SearchResults(
				// @formatter:off
				@JsonProperty("Index") Integer index,
				@JsonProperty("Title") String title,
				@JsonProperty("Url") String url
		) {
			// @formatter:on
		}

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
			@JsonProperty("Index") Integer index,
			@JsonProperty("Message") ChatCompletionMessage message,
			@JsonProperty("FinishReason") ChatCompletionFinishReason finishReason,
			@JsonProperty("Delta") ChatCompletionDelta chatCompletionDelta
		) {
			 // @formatter:on
		}

		@JsonInclude(Include.NON_NULL)
		public record ChatCompletionDelta(
				// @formatter:off
				@JsonProperty("Role") String role,
				@JsonProperty("Content") String content,
				@JsonProperty("ToolCalls") ChatCompletionToolCall chatCompletionToolCall
		) {
			// @formatter:on
		}

		@JsonInclude(Include.NON_NULL)
		public record ChatCompletionToolCall(
				// @formatter:off
				@JsonProperty("Id") String role,
				@JsonProperty("Type") String content,
				@JsonProperty("Function") ChatCompletionMessage.ChatCompletionFunction chatCompletionToolCall,
				@JsonProperty("Index") Integer index
		) {
			// @formatter:on
		}

		@JsonInclude(Include.NON_NULL)
		public record ErrorMsg(
				// @formatter:off
				@JsonProperty("Code") String index,
				@JsonProperty("Message") String message) {
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
		 * @param usage Usage statistics for the completion request.
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
	 * Represents a tool the model may call. Currently, only functions are supported as a
	 * tool.
	 *
	 * @param type The type of the tool. Currently, only 'function' is supported.
	 * @param function The function definition.
	 */
	@JsonInclude(Include.NON_NULL)
	public record FunctionTool(@JsonProperty("Type") Type type, @JsonProperty("Function") Function function) {

		/**
		 * Create a tool of type 'function' and the given function definition.
		 * @param function function definition.
		 */
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
		public record Function(@JsonProperty("Description") String description, @JsonProperty("Name") String name,
				@JsonProperty("Parameters") String parameters) {
		}
	}

}
