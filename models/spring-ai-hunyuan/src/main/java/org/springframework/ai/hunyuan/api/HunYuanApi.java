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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.hunyuan.api.auth.HunYuanAuthApi;
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
 * Single-class, Java Client library for HunYuan platform. Provides implementation for
 * the <a href="https://cloud.tencent.com/document/api/1729/105701">Chat Completion</a> APIs.
 * <p>
 * Implements <b>Synchronous</b> and <b>Streaming</b> chat completion.
 * </p>
 *
 * @author Guo Junyu
 */
public class HunYuanApi {

	private static final Logger logger = LoggerFactory.getLogger(HunYuanApi.class);

	public static final String DEFAULT_CHAT_MODEL = ChatModel.HUNYUAN_PRO.getValue();

	private static final Predicate<String> SSE_DONE_PREDICATE = "[DONE]"::equals;

	private final RestClient restClient;

	private final WebClient webClient;

	private final HunYuanAuthApi hunyuanAuthApi;

	private final HunYuanStreamFunctionCallingHelper chunkMerger = new HunYuanStreamFunctionCallingHelper();

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
		// Compatible Return Position text/plain
		logger.info("Response body: {}", retrieve.getBody());
		ChatCompletionResponse chatCompletionResponse = ModelOptionsUtils.jsonToObject(retrieve.getBody(), ChatCompletionResponse.class);
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
		String service = HunYuanConstants.DEFAULT_SERVICE;
		String host = HunYuanConstants.DEFAULT_CHAT_HOST;
//		String region = "ap-guangzhou";
		String action = HunYuanConstants.DEFAULT_CHAT_ACTION;
		MultiValueMap<String, String> jsonContentHeaders = hunyuanAuthApi.getHttpHeadersConsumer(host, action, service, chatRequest);
		return this.webClient.post()
			.uri("/")
			.headers(headers -> {
				headers.addAll(jsonContentHeaders);
			})
			.body(Mono.just(chatRequest), ChatCompletionRequest.class)
			.retrieve()
			.bodyToFlux(String.class)
			// cancels the flux stream after the "[DONE]" is received.
			.takeUntil(SSE_DONE_PREDICATE)
			// filters out the "[DONE]" message.
			.filter(SSE_DONE_PREDICATE.negate())
			.map(content ->{
//				logger.info(content);
				return ModelOptionsUtils.jsonToObject(content, ChatCompletionChunk.class);
			})
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
						new ChatCompletionChunk(null, null,null,null,null,null,null,null, null, null, null),
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
		 * The model called a tool.
		 */
		@JsonProperty("tool_calls")
		TOOL_CALLS;

		private final String jsonValue;

		ChatCompletionFinishReason() {
			this.jsonValue = this.name().toLowerCase();
		}
		public String getJsonValue() {
			return this.jsonValue;
		}
	}

	/**
	 * HunYuan Chat Completion Models:
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
	 * @param temperature What sampling temperature to use, between 0 and 1. Higher values
	 * like 0.8 will make the output more random, while lower values like 0.2 will make it
	 * more focused and deterministic. We generally recommend altering this or top_p but
	 * not both.
	 * @param enableEnhancement Enables or disables feature enhancements such as search. This parameter does not affect the security review capability.
	 * For hunyuan-lite, this parameter is ineffective.
	 * If not specified, the switch is turned on by default.
	 * Turning off this switch can reduce response latency, especially for the first character in stream mode, but may slightly degrade the response quality in some scenarios.
	 * Example: true
	 * @param topP An alternative to sampling with temperature, called nucleus sampling,
	 * where the model considers the results of the tokens with top_p probability mass. So
	 * 0.1 means only the tokens comprising the top 10% probability mass are considered.
	 * We generally recommend altering this or temperature but not both.
	 * @param stop Up to 5 sequences where the API will stop generating further tokens.
	 * @param stream If set, partial message deltas will be sent.Tokens will be sent as
	 * data-only server-sent events as they become available, with the stream terminated
	 * by a data: [DONE] message.
	 * @param streamModeration Controls whether the output is reviewed in real-time during streaming.
	 * This field is effective only when Stream is set to true.
	 * If true, the output is reviewed in real-time, and segments that fail the review will have their FinishReason set to sensitive.
	 * If false, the entire output is reviewed before being returned.
	 * If real-time text display is required in your application, you should handle the case where FinishReason is sensitive by撤回已显示的内容 and providing a custom message.
	 * Example: false
	 * @param tools A list of tools the model may call. Currently, only functions are
	 * supported as a tool.
	 * @param toolChoice Controls which (if any) function is called by the model. Possible values are none, auto, and custom.
	 *  If not specified, the default is auto.
	 *  Example: auto
	 * @param customTool Forces the model to call a specific tool. This parameter is required when ToolChoice is set to custom.
	 * @param searchInfo If true, the interface will return SearchInfo when a search hit occurs. Example: false
	 * @param citation Enables or disables citation markers in the response.
	 * This parameter works in conjunction with EnableEnhancement and SearchInfo.
	 * If true, search results in the response will be marked with a citation marker corresponding to links in the SearchInfo list.
	 * If not specified, the default is false.
	 * Example: false
	 * @param enableSpeedSearch Enables or disables the fast version of search.
	 * If true and a search hit occurs, the fast version of search will be used, which can reduce the latency of the first character in the stream.
	 * Example: false
	 * @param enableMultimedia  Enables or disables multimedia capabilities.
	 * This parameter is effective only for whitelisted users and when EnableEnhancement is true and EnableSpeedSearch is false.
	 * For hunyuan-lite, this parameter is ineffective.
	 * If not specified, the default is false.
	 * When enabled and a multimedia hit occurs, the corresponding multimedia address will be output.
	 * Example: false
	 * @param enableDeepSearch Enables or disables deep research on the question.
	 *  If true and a deep research hit occurs, information about the deep research will be returned.
	 *  Example: false
	 * @param seed Ensures the model's output is reproducible.
	 * The value should be a non-zero positive integer, with a maximum value of 10000.
	 * It is not recommended to use this parameter unless necessary, as improper values can affect the output quality.
	 * Example: 1
	 * @param forceSearchEnhancement Forces the use of AI search.
	 * If true, AI search will be used, and if the AI search result is empty, the large model will provide a fallback response.
	 * Example: false
	 * @param enableRecommendedQuestions Enables or disables the recommendation of additional questions.
	 * If true, the response will include a RecommendedQuestions field with up to 3 recommended questions in the last package.
	 * Example: false
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
			@JsonProperty("Seed") Integer seed,
			@JsonProperty("ForceSearchEnhancement") Boolean forceSearchEnhancement,
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
		 *  Shortcut constructor for a chat completion request with the given messages,
		 *  model, stream, streamModeration, enableEnhancement, searchInfo, citation, enableSpeedSearch.
		 * @param messages A list of messages comprising the conversation so far.
		 * @param model	  ID of the model to use.
		 * @param stream  Whether to stream back partial progress.
		 * @param streamModeration Whether to stream back partial progress.
		 * @param enableEnhancement Enables or disables the enhancement feature.
		 * @param searchInfo Enables or disables the search information feature.
		 * @param citation Enables or disables the citation feature.
		 * @param enableSpeedSearch Enables or disables the speed search feature.
		 */
		public ChatCompletionRequest(List<ChatCompletionMessage> messages, String model,
									 Boolean stream,
									 Boolean streamModeration,
									 Boolean enableEnhancement,
									 Boolean searchInfo,
									 Boolean citation,
									 Boolean enableSpeedSearch) {
			this(model,messages, null, enableEnhancement, null, null, stream, streamModeration, null, null, null, searchInfo, citation, null, null, enableSpeedSearch, null, null, null);
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
	 * @param chatContents The name of the message's author.
	 * @param toolCallId The ID of the tool call associated with the message.
	 * @param toolCalls The list of tool calls associated with the message.
	 */
	@JsonInclude(Include.NON_NULL)
	public record ChatCompletionMessage(
	// @formatter:off
		@JsonProperty("Content") Object rawContent,
		@JsonProperty("Role") Role role,
		@JsonProperty("Contents") List<ChatContent> chatContents,
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
		public ChatCompletionMessage(Object content, Role role,List<ToolCall> toolCalls) {
			this(content, role, null, null, toolCalls);
		}
		public ChatCompletionMessage(Role role,List<ChatContent> chatContent) {
			this(null, role, chatContent, null, null);
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
		public record ToolCall(@JsonProperty("Id") String id, @JsonProperty("Type") String type, @JsonProperty("Index") Integer index,
				@JsonProperty("Function") ChatCompletionFunction function) {

		}

		@JsonInclude(Include.NON_NULL)
		public record ChatContent(@JsonProperty("Type") String type, @JsonProperty("Text") String text,
							   @JsonProperty("ImageUrl") ImageUrl imageUrl) {
			public ChatContent(String type, String text) {
				this(type, text, null);
			}
			public ChatContent(String text) {
				this("text", text, null);
			}
			public ChatContent(String type, ImageUrl imageUrl) {
				this(type, null, imageUrl);
			}
			public ChatContent(ImageUrl imageUrl) {
				this("image_url", null, imageUrl);
			}

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
	 * @param response The response object containing the generated chat completion.
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
			@JsonProperty("FinishReason") String finishReason,
			@JsonProperty("Delta") ChatCompletionDelta delta
		) {
			 // @formatter:on
		}

		@JsonInclude(Include.NON_NULL)
		public record ChatCompletionDelta(
				// @formatter:off
				@JsonProperty("Role") ChatCompletionMessage.Role role,
				@JsonProperty("Content") String content,
				@JsonProperty("ToolCalls") List<ChatCompletionMessage.ToolCall> toolCalls
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
	 * @param errorMsg The error message, if any.
	 * @param created The Unix timestamp (in seconds) of when the chat completion was
	 * created. Each chunk has the same timestamp.
	 * @param note A note about the generated content. Each chunk has the same note.
	 * @param choices A list of chat completion choices. Can be more than one if n is
	 * greater than 1.
	 * @param usage The usage statistics for the chat completion.
	 */
	@JsonInclude(Include.NON_NULL)
	public record ChatCompletionChunk(
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
	@JsonProperty("RequestId") String requestId) {
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
