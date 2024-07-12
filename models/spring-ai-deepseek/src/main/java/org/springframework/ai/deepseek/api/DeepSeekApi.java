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
package org.springframework.ai.deepseek.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.ai.model.ModelDescription;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.util.api.ApiUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.function.Predicate;

import static org.springframework.ai.deepseek.api.DeepSeekApiConstants.DEFAULT_BASE_URL;

// @formatter:off
/**
 * Single class implementation of the DeepSeek Chat Completion API: https://platform.deepseek.com/api-docs/api/create-chat-completion
 *
 * @author Geng Rong
 */
public class DeepSeekApi {

	public static final String DEFAULT_CHAT_MODEL = ChatModel.DEEPSEEK_CHAT.getValue();

	private static final Predicate<String> SSE_DONE_PREDICATE = "[DONE]"::equals;

	private final RestClient restClient;

	private final WebClient webClient;

	/**
	 * Create an new chat completion api with base URL set to https://api.deepseek.com
	 *
	 * @param deepseekToken DeepSeek apiKey.
	 */
	public DeepSeekApi(String deepseekToken) {
		this(DEFAULT_BASE_URL, deepseekToken);
	}

	/**
	 * Create a new chat completion api.
	 *
	 * @param baseUrl api base URL.
	 * @param deepseekToken DeepSeek apiKey.
	 */
	public DeepSeekApi(String baseUrl, String deepseekToken) {
		this(baseUrl, deepseekToken, RestClient.builder(), WebClient.builder());
	}

	/**
	 * Create a new chat completion api.
	 *
	 * @param baseUrl api base URL.
	 * @param deepseekToken DeepSeek apiKey.
	 * @param restClientBuilder RestClient builder.
	 */
	public DeepSeekApi(String baseUrl, String deepseekToken, RestClient.Builder restClientBuilder, WebClient.Builder webClientBuilder) {
		this(baseUrl, deepseekToken, restClientBuilder, webClientBuilder, RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER);
	}

	/**
	 * Create a new chat completion api.
	 *
	 * @param baseUrl api base URL.
	 * @param deepseekToken DeepSeek apiKey.
	 * @param restClientBuilder RestClient builder.
	 * @param responseErrorHandler Response error handler.
	 */
	public DeepSeekApi(String baseUrl, String deepseekToken, RestClient.Builder restClientBuilder, WebClient.Builder webClientBuilder, ResponseErrorHandler responseErrorHandler) {

		this.restClient = restClientBuilder
				.baseUrl(baseUrl)
				.defaultHeaders(ApiUtils.getJsonContentHeaders(deepseekToken))
				.defaultStatusHandler(responseErrorHandler)
				.build();

		this.webClient = webClientBuilder
				.baseUrl(baseUrl)
				.defaultHeaders(ApiUtils.getJsonContentHeaders(deepseekToken))
				.build();
	}

	/**
	 * DeepSeek Chat Completion <a href="https://platform.deepseek.com/api-docs/api/list-models">Models</a>
	 */
	public enum ChatModel implements ModelDescription {
		/**
		 * The DeepSeek V2 Chat and DeepSeek Coder V2 models have been merged
		 * and upgraded into the new model, DeepSeek V2.5. The new model significantly surpasses
		 * the previous versions in both general capabilities and code abilities.
		 */
		DEEPSEEK_CHAT("deepseek-chat");

		public final String  value;

		ChatModel(String value) {
			this.value = value;
		}

		public String getValue() {
			return value;
		}

		@Override public String getName() {
			return value;
		}
	}

	/**
	 * Creates a model response for the given chat conversation.
	 *
	 * @param messages A list of messages comprising the conversation so far.
	 * @param model ID of the model to use. You can use either usedeepseek-coder or deepseek-chat.
	 * @param frequencyPenalty Number between -2.0 and 2.0. Positive values penalize new tokens based on their existing
	 * frequency in the text so far, decreasing the model's likelihood to repeat the same line verbatim.
	 * @param maxTokens The maximum number of tokens that can be generated in the chat completion.
	 * The total length of input tokens and generated tokens is limited by the model's context length.
	 * @param presencePenalty Number between -2.0 and 2.0. Positive values penalize new tokens based on whether they
	 * appear in the text so far, increasing the model's likelihood to talk about new topics.
	 * @param stop A string or a list containing up to 4 strings, upon encountering these words,
	 * the API will cease generating more tokens.
	 * @param stream If set, partial message deltas will be sent.
	 * Tokens will be sent as data-only server-sent events (SSE) as they become available,
	 * with the stream terminated by a data: [DONE] message.
	 * @param temperature What sampling temperature to use, between 0 and 2.
	 * Higher values like 0.8 will make the output more random,
	 * while lower values like 0.2 will make it more focused and deterministic.
	 * We generally recommend altering this or top_p but not both.
	 * @param topP An alternative to sampling with temperature, called nucleus sampling,
	 * where the model considers the results of the tokens with top_p probability mass.
	 * So 0.1 means only the tokens comprising the top 10% probability mass are considered.
	 * We generally recommend altering this or temperature but not both.
	 * @param logprobs Whether to return log probabilities of the output tokens or not.
	 * If true, returns the log probabilities of each output token returned in the content of message.
	 * @param topLogprobs An integer between 0 and 20 specifying the number of most likely tokens to return at each token position,
	 * each with an associated log probability. logprobs must be set to true if this parameter is used.
	 */
	@JsonInclude(Include.NON_NULL)
	public record ChatCompletionRequest (
			@JsonProperty("messages") List<ChatCompletionMessage> messages,
			@JsonProperty("model") String model,
			@JsonProperty("frequency_penalty") Float frequencyPenalty,
			@JsonProperty("max_tokens") Integer maxTokens,
			@JsonProperty("presence_penalty") Float presencePenalty,
			@JsonProperty("stop") List<String> stop,
			@JsonProperty("stream") Boolean stream,
			@JsonProperty("temperature") Float temperature,
			@JsonProperty("top_p") Float topP,
			@JsonProperty("logprobs") Boolean logprobs,
			@JsonProperty("top_logprobs") Integer topLogprobs) {

		/**
		 * Shortcut constructor for a chat completion request with the given messages and model.
		 *
		 * @param messages A list of messages comprising the conversation so far.
		 * @param model ID of the model to use. You can use either usedeepseek-coder or deepseek-chat.
		 * @param temperature What sampling temperature to use, between 0 and 1.
		 */
		public ChatCompletionRequest(List<ChatCompletionMessage> messages, String model, Float temperature) {
			this(messages, model, null, null, null, null, false, temperature, null,
					null, null);
		}

		/**
		 * Shortcut constructor for a chat completion request with the given messages, model and control for streaming.
		 *
		 * @param messages A list of messages comprising the conversation so far.
		 * @param model ID of the model to use. You can use either usedeepseek-coder or deepseek-chat.
		 * @param temperature What sampling temperature to use, between 0 and 1.
		 * @param stream If set, partial message deltas will be sent.
		 * 	 * Tokens will be sent as data-only server-sent events (SSE) as they become available,
		 * 	 * with the stream terminated by a data: [DONE] message.
		 */
		public ChatCompletionRequest(List<ChatCompletionMessage> messages, String model, Float temperature, boolean stream) {
			this(messages, model, null, null, null, null, stream, temperature, null,
					null, null);
		}

		/**
		 * Shortcut constructor for a chat completion request with the given messages, model
		 * Streaming is set to false, temperature to 1 and all other parameters are null.
		 *
		 * @param messages A list of messages comprising the conversation so far.
		 * @param model ID of the model to use. You can use either usedeepseek-coder or deepseek-chat.
		 */
		public ChatCompletionRequest(List<ChatCompletionMessage> messages, String model) {
			this(messages, model, null, null, null, null, false, 1F, null,
					null, null);
		}

		/**
		 * Shortcut constructor for a chat completion request with the given messages, model, tools and tool choice.
		 * Streaming is set to false, temperature to 0.8 and all other parameters are null.
		 *
		 * @param messages A list of messages comprising the conversation so far.
		 * @param stream If set, partial message deltas will be sent.Tokens will be sent as data-only server-sent events
		 * as they become available, with the stream terminated by a data: [DONE] message.
		 */
		public ChatCompletionRequest(List<ChatCompletionMessage> messages, Boolean stream) {
			this(messages, null, null, null, null, null, stream, null, null,
					null, null);
		}
	}

	/**
	 * Message comprising the conversation.
	 *
	 * @param rawContent The contents of the message. Can be either a {@link String}.
	 * The response message content is always a {@link String}.
	 * @param role The role of the messages author. Could be one of the {@link Role} types.
	 */
	@JsonInclude(Include.NON_NULL)
	public record ChatCompletionMessage(
			@JsonProperty("content") Object rawContent,
			@JsonProperty("role") Role role) {

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
		 * The role of the author of this message.
		 */
		public enum Role {
			/**
			 * System message.
			 */
			@JsonProperty("system") SYSTEM,
			/**
			 * User message.
			 */
			@JsonProperty("user") USER,
			/**
			 * Assistant message.
			 */
			@JsonProperty("assistant") ASSISTANT
		}
	}

	/**
	 * The reason the model stopped generating tokens.
	 */
	public enum ChatCompletionFinishReason {
		/**
		 * The model hit a natural stop point or a provided stop sequence.
		 */
		@JsonProperty("stop") STOP,
		/**
		 * The maximum number of tokens specified in the request was reached.
		 */
		@JsonProperty("length") LENGTH,
		/**
		 * The content was omitted due to a flag from our content filters.
		 */
		@JsonProperty("content_filter") CONTENT_FILTER
	}

	/**
	 * Represents a chat completion response returned by model, based on the provided input.
	 *
	 * @param id A unique identifier for the chat completion.
	 * @param choices A list of chat completion choices. Can be more than one if n is greater than 1.
	 * @param created The Unix timestamp (in seconds) of when the chat completion was created.
	 * @param model The model used for the chat completion.
	 * @param systemFingerprint This fingerprint represents the backend configuration that the model runs with. Can be
	 * used in conjunction with the seed request parameter to understand when backend changes have been made that might
	 * impact determinism.
	 * @param object The object type, which is always chat.completion.
	 * @param usage Usage statistics for the completion request.
	 */
	@JsonInclude(Include.NON_NULL)
	public record ChatCompletion(
			@JsonProperty("id") String id,
			@JsonProperty("choices") List<Choice> choices,
			@JsonProperty("created") Long created,
			@JsonProperty("model") String model,
			@JsonProperty("system_fingerprint") String systemFingerprint,
			@JsonProperty("object") String object,
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
		public record Choice(
				@JsonProperty("finish_reason") ChatCompletionFinishReason finishReason,
				@JsonProperty("index") Integer index,
				@JsonProperty("message") ChatCompletionMessage message,
				@JsonProperty("logprobs") LogProbs logprobs) {

		}
	}

	/**
	 * Log probability information for the choice.
	 *
	 * @param content A list of message content tokens with log probability information.
	 */
	@JsonInclude(Include.NON_NULL)
	public record LogProbs(
			@JsonProperty("content") List<Content> content) {

		/**
		 * Message content tokens with log probability information.
		 *
		 * @param token The token.
		 * @param logprob The log probability of the token.
		 * @param probBytes A list of integers representing the UTF-8 bytes representation of the token.
		 * Useful in instances where characters are represented by multiple tokens and their byte
		 * representations must be combined to generate the correct text representation.
		 * Can be null if there is no bytes representation for the token.
		 * @param topLogprobs List of the most likely tokens and their log probability,
		 * at this token position. In rare cases, there may be fewer than the number of
		 * requested top_logprobs returned.
		 */
		@JsonInclude(Include.NON_NULL)
		public record Content(
				@JsonProperty("token") String token,
				@JsonProperty("logprob") Float logprob,
				@JsonProperty("bytes") List<Integer> probBytes,
				@JsonProperty("top_logprobs") List<TopLogProbs> topLogprobs) {

			/**
			 * The most likely tokens and their log probability, at this token position.
			 *
			 * @param token The token.
			 * @param logprob TThe log probability of this token,
			 * if it is within the top 20 most likely tokens. Otherwise,
			 * the value -9999.0 is used to signify that the token is very unlikely.
			 * @param probBytes A list of integers representing the UTF-8 bytes representation of the token.
			 * Useful in instances where characters are represented by multiple tokens and their byte
			 * representations must be combined to generate the correct text representation.
			 * Can be null if there is no bytes representation for the token.
			 */
			@JsonInclude(Include.NON_NULL)
			public record TopLogProbs(
					@JsonProperty("token") String token,
					@JsonProperty("logprob") Float logprob,
					@JsonProperty("bytes") List<Integer> probBytes) {
			}
		}
	}

	/**
	 * Usage statistics for the completion request.
	 *
	 * @param completionTokens Number of tokens in the generated completion. Only applicable for completion requests.
	 * @param promptTokens Number of tokens in the prompt.
	 * @param totalTokens Total number of tokens used in the request (prompt + completion).
	 */
	@JsonInclude(Include.NON_NULL)
	public record Usage(
			@JsonProperty("completion_tokens") Integer completionTokens,
			@JsonProperty("prompt_tokens") Integer promptTokens,
			@JsonProperty("prompt_cache_hit_tokens") Integer promptCacheHitTokens,
			@JsonProperty("prompt_cache_miss_tokens") Integer promptCacheMissTokens,
			@JsonProperty("total_tokens") Integer totalTokens) {

	}

	/**
	 * Represents a streamed chunk of a chat completion response returned by model, based on the provided input.
	 *
	 * @param id A unique identifier for the chat completion. Each chunk has the same ID.
	 * @param choices A list of chat completion choices. Can be more than one if n is greater than 1.
	 * @param created The Unix timestamp (in seconds) of when the chat completion was created. Each chunk has the same
	 * timestamp.
	 * @param model The model used for the chat completion.
	 * @param object The object type, which is always 'chat.completion.chunk'.
	 */
	@JsonInclude(Include.NON_NULL)
	public record ChatCompletionChunk(
			@JsonProperty("id") String id,
			@JsonProperty("choices") List<ChunkChoice> choices,
			@JsonProperty("created") Long created,
			@JsonProperty("model") String model,
			@JsonProperty("system_fingerprint") String systemFingerprint,
			@JsonProperty("object") String object) {

		/**
		 * Chat completion choice.
		 *
		 * @param finishReason The reason the model stopped generating tokens.
		 * @param index The index of the choice in the list of choices.
		 * @param delta A chat completion delta generated by streamed model responses.
		 * @param logprobs Log probability information for the choice.
		 */
		@JsonInclude(Include.NON_NULL)
		public record ChunkChoice(
				@JsonProperty("finish_reason") ChatCompletionFinishReason finishReason,
				@JsonProperty("index") Integer index,
				@JsonProperty("delta") ChatCompletionMessage delta,
				@JsonProperty("logprobs") LogProbs logprobs) {
		}
	}

	/**
	 * Creates a model response for the given chat conversation.
	 *
	 * @param chatRequest The chat completion request.
	 * @return Entity response with {@link ChatCompletion} as a body and HTTP status code and headers.
	 */
	public ResponseEntity<ChatCompletion> chatCompletionEntity(ChatCompletionRequest chatRequest) {

		Assert.notNull(chatRequest, "The request body can not be null.");
		Assert.isTrue(!chatRequest.stream(), "Request must set the steam property to false.");

		return this.restClient.post()
				.uri("/chat/completions")
				.body(chatRequest)
				.retrieve()
				.toEntity(ChatCompletion.class);
	}

	/**
	 * Creates a streaming chat response for the given chat conversation.
	 *
	 * @param chatRequest The chat completion request. Must have the stream property set to true.
	 * @return Returns a {@link Flux} stream from chat completion chunks.
	 */
	public Flux<ChatCompletionChunk> chatCompletionStream(ChatCompletionRequest chatRequest) {
		Assert.notNull(chatRequest, "The request body can not be null.");
		Assert.isTrue(chatRequest.stream(), "Request must set the steam property to true.");

		return this.webClient.post()
				.uri("/chat/completions")
				.body(Mono.just(chatRequest), ChatCompletionRequest.class)
				.retrieve()
				.bodyToFlux(String.class)
				// cancels the flux stream after the "[DONE]" is received.
				.takeUntil(SSE_DONE_PREDICATE)
				// filters out the "[DONE]" message.
				.filter(SSE_DONE_PREDICATE.negate())
				.map(content -> ModelOptionsUtils.jsonToObject(content, ChatCompletionChunk.class));
	}
}
// @formatter:on
