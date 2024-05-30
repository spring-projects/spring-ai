/*
 * Copyright 2024 - 2024 the original author or authors.
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
package org.springframework.ai.yi.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.ai.model.ModelDescription;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

/**
 * Single class implementation of the YiAI Chat Completion API:
 * https://platform.lingyiwanwu.com/docs#create-chat-completion
 *
 * @author Geng Rong
 * @since 1.0.0 M1
 */
public class YiAiApi {

	public static final String DEFAULT_CHAT_MODEL = ChatModel.YI_LARGE.getValue();

	private static final Predicate<String> SSE_DONE_PREDICATE = "[DONE]"::equals;

	private final RestClient restClient;

	private final WebClient webClient;

	/**
	 * Create a new chat completion api with default base URL.
	 * @param apiKey YiAI apiKey.
	 */
	public YiAiApi(String apiKey) {
		this(ApiUtils.DEFAULT_BASE_URL, apiKey);
	}

	/**
	 * Create a new chat completion api.
	 * @param baseUrl api base URL.
	 * @param apiKey YiAI apiKey.
	 */
	public YiAiApi(String baseUrl, String apiKey) {
		this(baseUrl, apiKey, RestClient.builder());
	}

	/**
	 * Create a new chat completion api.
	 * @param baseUrl api base URL.
	 * @param apiKey YiAI apiKey.
	 * @param restClientBuilder RestClient builder.
	 */
	public YiAiApi(String baseUrl, String apiKey, RestClient.Builder restClientBuilder) {
		this(baseUrl, apiKey, restClientBuilder, RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER);
	}

	/**
	 * Create a new chat completion api.
	 * @param baseUrl api base URL.
	 * @param apiKey YiAI apiKey.
	 * @param restClientBuilder RestClient builder.
	 * @param responseErrorHandler Response error handler.
	 */
	public YiAiApi(String baseUrl, String apiKey, RestClient.Builder restClientBuilder,
			ResponseErrorHandler responseErrorHandler) {

		this.restClient = restClientBuilder.baseUrl(baseUrl)
			.defaultHeaders(ApiUtils.getJsonContentHeaders(apiKey))
			.defaultStatusHandler(responseErrorHandler)
			.build();

		this.webClient = WebClient.builder()
			.baseUrl(baseUrl)
			.defaultHeaders(ApiUtils.getJsonContentHeaders(apiKey))
			.build();
	}

	/**
	 * Yi Chat Completion Models:
	 * <a href="https://platform.lingyiwanwu.com/docs#%E6%A8%A1%E5%9E%8B">YiAI Model</a>.
	 */
	public enum ChatModel implements ModelDescription {

		YI_LARGE("yi-large"), YI_MEDIUM("yi-medium"), YI_VISION("yi-vision"), YI_MEDIUM_200K("yi-medium-200k"),
		YI_SPARK("yi-spark"), YI_LARGE_RAG("yi-large-rag"), YI_LARGE_TURBO("yi-large-turbo");

		public final String value;

		ChatModel(String value) {
			this.value = value;
		}

		public String getValue() {
			return value;
		}

		@Override
		public String getModelName() {
			return this.value;
		}

	}

	/**
	 * Creates a model response for the given chat conversation.
	 *
	 * @param messages A list of messages comprising the conversation so far.
	 * @param model ID of the model to use.
	 * @param maxTokens The maximum number of tokens to generate in the chat completion.
	 * The total length of input tokens and generated tokens is limited by the model's
	 * context length.
	 * @param stream If set, partial message deltas will be sent.Tokens will be sent as
	 * data-only server-sent events as they become available, with the stream terminated
	 * by a data: [DONE] message.
	 * @param temperature What sampling temperature to use, between 0 and 1. Higher values
	 * like 0.8 will make the output more random, while lower values like 0.2 will make it
	 * more focused and deterministic. We generally recommend altering this or top_p but
	 * not both.
	 * @param topP An alternative to sampling with temperature, called nucleus sampling,
	 * where the model considers the results of the tokens with top_p probability mass. So
	 * 0.1 means only the tokens comprising the top 10% probability mass are considered.
	 * We generally recommend altering this or temperature but not both.
	 */
	@JsonInclude(Include.NON_NULL)
	public record ChatCompletionRequest(@JsonProperty("messages") List<ChatCompletionMessage> messages,
			@JsonProperty("model") String model, @JsonProperty("max_tokens") Integer maxTokens,
			@JsonProperty("stream") Boolean stream, @JsonProperty("temperature") Float temperature,
			@JsonProperty("top_p") Float topP) {

		/**
		 * Shortcut constructor for a chat completion request with the given messages and
		 * model.
		 * @param messages A list of messages comprising the conversation so far.
		 * @param model ID of the model to use.
		 * @param temperature What sampling temperature to use, between 0 and 1.
		 */
		public ChatCompletionRequest(List<ChatCompletionMessage> messages, String model, Float temperature) {
			this(messages, model, null, false, temperature, null);
		}

		/**
		 * Shortcut constructor for a chat completion request with the given messages,
		 * model and control for streaming.
		 * @param messages A list of messages comprising the conversation so far.
		 * @param model ID of the model to use.
		 * @param temperature What sampling temperature to use, between 0 and 1.
		 * @param stream If set, partial message deltas will be sent.Tokens will be sent
		 * as data-only server-sent events as they become available, with the stream
		 * terminated by a data: [DONE] message.
		 */
		public ChatCompletionRequest(List<ChatCompletionMessage> messages, String model, Float temperature,
				boolean stream) {
			this(messages, model, null, stream, temperature, null);
		}

		/**
		 * Shortcut constructor for a chat completion request with the given messages,
		 * model, tools and tool choice. Streaming is set to false, temperature to 0.8 and
		 * all other parameters are null.
		 * @param messages A list of messages comprising the conversation so far.
		 * @param stream If set, partial message deltas will be sent.Tokens will be sent
		 * as data-only server-sent events as they become available, with the stream
		 * terminated by a data: [DONE] message.
		 */
		public ChatCompletionRequest(List<ChatCompletionMessage> messages, Boolean stream) {
			this(messages, null, null, stream, null, null);
		}
	}

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
	public record ChatCompletionMessage(@JsonProperty("content") Object rawContent, @JsonProperty("role") Role role,
			@JsonProperty("name") String name, @JsonProperty("tool_call_id") String toolCallId,
			@JsonProperty("tool_calls") List<ToolCall> toolCalls) {

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
			if (this.rawContent instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof MediaContent) {
				return ((List<MediaContent>) list).stream()
					.filter(c -> false)
					.map(ChatCompletionMessage.MediaContent::text)
					.reduce("", (a, b) -> a + b);
			}
			throw new IllegalStateException("The content is not a string or a image format!");
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
		 * using the glm-4v model.
		 */
		@JsonInclude(Include.NON_NULL)
		public record MediaContent(@JsonProperty("type") String type, @JsonProperty("text") String text,
				@JsonProperty("image_url") ImageUrl imageUrl) {

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
	public record ChatCompletion(@JsonProperty("id") String id, @JsonProperty("choices") List<Choice> choices,
			@JsonProperty("created") Long created, @JsonProperty("model") String model,
			@JsonProperty("system_fingerprint") String systemFingerprint, @JsonProperty("object") String object,
			@JsonProperty("usage") Usage usage) {

		/**
		 * Chat completion choice.
		 *
		 * @param finishReason The reason the model stopped generating tokens.
		 * @param index The index of the choice in the list of choices.
		 * @param message A chat completion message generated by the model.
		 * @param logprobs Log probability information for the choice.
		 */
		@JsonInclude(Include.NON_NULL)
		public record Choice(@JsonProperty("finish_reason") ChatCompletionFinishReason finishReason,
				@JsonProperty("index") Integer index, @JsonProperty("message") ChatCompletionMessage message,
				@JsonProperty("logprobs") LogProbs logprobs) {

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
		public record Content(@JsonProperty("token") String token, @JsonProperty("logprob") Float logprob,
				@JsonProperty("bytes") List<Integer> probBytes,
				@JsonProperty("top_logprobs") List<TopLogProbs> topLogprobs) {

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
			public record TopLogProbs(@JsonProperty("token") String token, @JsonProperty("logprob") Float logprob,
					@JsonProperty("bytes") List<Integer> probBytes) {
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
	public record Usage(@JsonProperty("completion_tokens") Integer completionTokens,
			@JsonProperty("prompt_tokens") Integer promptTokens, @JsonProperty("total_tokens") Integer totalTokens) {

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
	 */
	@JsonInclude(Include.NON_NULL)
	public record ChatCompletionChunk(@JsonProperty("id") String id, @JsonProperty("choices") List<ChunkChoice> choices,
			@JsonProperty("created") Long created, @JsonProperty("model") String model,
			@JsonProperty("system_fingerprint") String systemFingerprint, @JsonProperty("object") String object) {

		/**
		 * Chat completion choice.
		 *
		 * @param finishReason The reason the model stopped generating tokens.
		 * @param index The index of the choice in the list of choices.
		 * @param delta A chat completion delta generated by streamed model responses.
		 * @param logprobs Log probability information for the choice.
		 */
		@JsonInclude(Include.NON_NULL)
		public record ChunkChoice(@JsonProperty("finish_reason") ChatCompletionFinishReason finishReason,
				@JsonProperty("index") Integer index, @JsonProperty("delta") ChatCompletionMessage delta,
				@JsonProperty("logprobs") LogProbs logprobs) {
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
		Assert.isTrue(!chatRequest.stream(), "Request must set the steam property to false.");

		return this.restClient.post()
			.uri("/v1/chat/completions")
			.body(chatRequest)
			.retrieve()
			.toEntity(ChatCompletion.class);
	}

	private final YiAiStreamFunctionCallingHelper chunkMerger = new YiAiStreamFunctionCallingHelper();

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
			.takeUntil(SSE_DONE_PREDICATE)
			.filter(SSE_DONE_PREDICATE.negate())
			.map(content -> ModelOptionsUtils.jsonToObject(content, ChatCompletionChunk.class))
			.map(chunk -> {
				if (this.chunkMerger.isStreamingToolFunctionCall(chunk)) {
					isInsideTool.set(true);
				}
				return chunk;
			})
			.windowUntil(chunk -> {
				if (isInsideTool.get() && this.chunkMerger.isStreamingToolFunctionCallFinish(chunk)) {
					isInsideTool.set(false);
					return true;
				}
				return !isInsideTool.get();
			})
			.concatMapIterable(window -> {
				Mono<ChatCompletionChunk> monoChunk = window
					.reduce(new ChatCompletionChunk(null, null, null, null, null, null), this.chunkMerger::merge);
				return List.of(monoChunk);
			})
			.flatMap(mono -> mono);
	}

}
