/*
 * Copyright 2024-2024 the original author or authors.
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

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.ai.anthropic.api.AnthropicApi.StreamChatCompletion;
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
 *
 * @author Christian Tzolov
 * @sine 1.0.0
 */
public class AnthropicApi {

	private static final String DEFAULT_BASE_URL = "https://api.anthropic.com";

	private static final String DEFAULT_ANTHROPIC_VERSION = "2023-06-01";

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
			RestClient.Builder restClientBuilder,
			ResponseErrorHandler responseErrorHandler) {

		Consumer<HttpHeaders> jsonContentHeaders = headers -> {
			// headers.setBearerAuth(anthropicApiKey);
			headers.add("x-api-key", anthropicApiKey);
			headers.add("anthropic-version", anthropicVersion);
			headers.setContentType(MediaType.APPLICATION_JSON);
		};

		this.restClient = restClientBuilder.baseUrl(baseUrl)
				.defaultHeaders(jsonContentHeaders)
				.defaultStatusHandler(responseErrorHandler)
				.build();

		this.webClient = WebClient.builder().baseUrl(baseUrl).defaultHeaders(jsonContentHeaders).build();
	}

	/**
	 * @param model The model that will complete your prompt. See the list of
	 * <a href="https://docs.anthropic.com/claude/docs/models-overview">models</a> for additional details and options.
	 * @param messages Input messages.
	 * @param system System prompt. A system prompt is a way of providing context and instructions to Claude, such as
	 * specifying a particular goal or role. See our
	 * <a href="https://docs.anthropic.com/claude/docs/system-prompts">guide</a> to system prompts.
	 * @param maxTokens The maximum number of tokens to generate before stopping. Note that our models may stop before
	 * reaching this maximum. This parameter only specifies the absolute maximum number of tokens to generate. Different
	 * models have different maximum values for this parameter.
	 * @param metadata An object describing metadata about the request.
	 * @param stopSequences Custom text sequences that will cause the model to stop generating. Our models will normally
	 * stop when they have naturally completed their turn, which will result in a response stop_reason of "end_turn". If
	 * you want the model to stop generating when it encounters custom strings of text, you can use the stop_sequences
	 * parameter. If the model encounters one of the custom sequences, the response stop_reason value will be
	 * "stop_sequence" and the response stop_sequence value will contain the matched stop sequence.
	 * @param stream Whether to incrementally stream the response using server-sent events.
	 * @param temperature Amount of randomness injected into the response.Defaults to 1.0. Ranges from 0.0 to 1.0. Use
	 * temperature closer to 0.0 for analytical / multiple choice, and closer to 1.0 for creative and generative tasks.
	 * Note that even with temperature of 0.0, the results will not be fully deterministic.
	 * @param topP Use nucleus sampling. In nucleus sampling, we compute the cumulative distribution over all the
	 * options for each subsequent token in decreasing probability order and cut it off once it reaches a particular
	 * probability specified by top_p. You should either alter temperature or top_p, but not both. Recommended for
	 * advanced use cases only. You usually only need to use temperature.
	 * @param topK Only sample from the top K options for each subsequent token. Used to remove "long tail" low
	 * probability responses. Learn more technical details here. Recommended for advanced use cases only. You usually
	 * only need to use temperature.
	 */
	@JsonInclude(Include.NON_NULL)
	public record ChatCompletionRequest(
	// @formatter:off
	@JsonProperty("model") String model,
	@JsonProperty("messages") List<ChatCompletionMessage> messages,
	@JsonProperty("system") String system,
	@JsonProperty("max_tokens") Integer maxTokens,
	@JsonProperty("metadata") Metadata metadata,
	@JsonProperty("stop_sequences") List<String> stopSequences,
	@JsonProperty("stream") Boolean stream,
	@JsonProperty("temperature") Float temperature,
	@JsonProperty("top_p") Float topP,
	@JsonProperty("top_k") Float topK) {
	// @formatter:on

		ChatCompletionRequest(String model, List<ChatCompletionMessage> messages, String system, Integer maxTokens,
				Float temperature,
				Boolean stream) {
			this(model, messages, system, maxTokens, null, null, stream, temperature, null, null);
		}

		/**
		 * @param uerId An external identifier for the user who is associated with the request. This should be a uuid,
		 * hash value, or other opaque identifier. Anthropic may use this id to help detect abuse. Do not include any
		 * identifying information such as name, email address, or phone number.
		 */
		@JsonInclude(Include.NON_NULL)
		record Metadata(
				@JsonProperty("user_id") String userId) {
		}
	}

	/**
	 * Input messages.
	 *
	 * Our models are trained to operate on alternating user and assistant conversational turns. When creating a new
	 * Message, you specify the prior conversational turns with the messages parameter, and the model then generates the
	 * next Message in the conversation. Each input message must be an object with a role and content. You can specify a
	 * single user-role message, or you can include multiple user and assistant messages. The first message must always
	 * use the user role. If the final message uses the assistant role, the response content will continue immediately
	 * from the content in that message. This can be used to constrain part of the model's response.
	 *
	 * @param content The contents of the message. Can be of one of String or MultiModalContent.
	 * @param role The role of the messages author. Could be one of the {@link Role} types.
	 */
	@JsonInclude(Include.NON_NULL)
	public record ChatCompletionMessage(
	// @formatter:off
		 @JsonProperty("content") Object content,
		 @JsonProperty("role") Role role) {
		 // @formatter:on

		/**
		 * The role of the author of this message.
		 */
		public enum Role {
			// @formatter:off
			 @JsonProperty("user") USER,
			 @JsonProperty("assistant") ASSISTANT,
			 // @formatter:on
		}

		/**
		 * @param type
		 */
		@JsonInclude(Include.NON_NULL)
		public record MultiModalContent(
				@JsonProperty("type") String type,
				@JsonProperty("source") Source source) {

			public MultiModalContent(Source source) {
				this("image", source);
			}

			@JsonInclude(Include.NON_NULL)
			public record Source(
					@JsonProperty("type") String type,
					@JsonProperty("media_type") String mediaType,
					@JsonProperty("data") String data) {

				public Source(String mediaType, String data) {
					this("base64", mediaType, data);
				}
			}
		}
	}

	/**
	 * @param id Unique object identifier. The format and length of IDs may change over time.
	 * @param type Object type. For Messages, this is always "message".
	 * @param role Conversational role of the generated message. This will always be "assistant".
	 * @param content Content generated by the model. This is an array of content blocks.
	 * @param model The model that handled the request.
	 * @param stopReason The reason the model stopped generating tokens. This will be one of "end_turn", "max_tokens",
	 * "stop_sequence", or "timeout".
	 * @param stopSequence Which custom stop sequence was generated, if any.
	 * @param usageBilling and rate-limit usage.
	 */
	@JsonInclude(Include.NON_NULL)
	public record ChatCompletion(
			@JsonProperty("id") String id,
			@JsonProperty("type") String type,
			@JsonProperty("role") ChatCompletionMessage.Role role,
			@JsonProperty("content") List<Content> content,
			@JsonProperty("model") String model,
			@JsonProperty("stop_reason") String stopReason,
			@JsonProperty("stop_sequence") String stopSequence,
			@JsonProperty("usage") Usage usage) {

		/**
		 * @param type determines content's shape. Currently, the only type in responses is "text".
		 * @param text The text of the message.
		 */
		@JsonInclude(Include.NON_NULL)
		public record Content(
				@JsonProperty("type") String type,
				@JsonProperty("text") String text) {

		}
	}

	/**
	 * Usage statistics.
	 *
	 * @param inputTokens The number of input tokens which were used.
	 * @param outputTokens The number of output tokens which were used. completion).
	 */
	@JsonInclude(Include.NON_NULL)
	public record Usage(
	// @formatter:off
		 @JsonProperty("input_tokens") Integer inputTokens,
		 @JsonProperty("output_tokens") Integer outputTokens) {
		 // @formatter:off
	}


	/**
	 * Streaming chat completion response.
	 * @param type The server event type of the stream response. Each stream uses the following event flow:
	 * (1) 'message_start': contains a Message object with empty content; (2) A series of content blocks,
	 * each of which have a 'content_block_start', one or more 'content_block_delta events', and a 'content_block_stop' event.
	 * Each content block will have an 'index' that corresponds to its index in the final Message content array.
	 */
	@JsonInclude(Include.NON_NULL)
	public record StreamChatCompletion(
		// @formatter:off
		@JsonProperty("type") String type,
		@JsonProperty("index") String index,

		@JsonProperty("message") ChatCompletion message,
		@JsonProperty("content_block") ChatCompletion.Content contentBlock,
		@JsonProperty("delta") ChatCompletion.Content delta,
		){}
		// @formatter:off

	/**
	 * Creates a model response for the given chat conversation.
	 * @param chatRequest The chat completion request.
	 * @return Entity response with {@link ChatCompletion} as a body and HTTP status code and headers.
	 */
	public ResponseEntity<ChatCompletion> chatCompletionEntity(ChatCompletionRequest chatRequest) {

		Assert.notNull(chatRequest, "The request body can not be null.");
		Assert.isTrue(!chatRequest.stream(), "Request must set the steam property to false.");

		return this.restClient.post()
				.uri("/v1/messages")
				.body(chatRequest)
				.retrieve()
				.toEntity(ChatCompletion.class);
	}

	/**
	 * Creates a streaming chat response for the given chat conversation.
	 * @param chatRequest The chat completion request. Must have the stream property set to true.
	 * @return Returns a {@link Flux} stream from chat completion chunks.
	 */
	public Flux<StreamChatCompletion> chatCompletionStream(ChatCompletionRequest chatRequest) {

		Assert.notNull(chatRequest, "The request body can not be null.");
		Assert.isTrue(chatRequest.stream(), "Request must set the steam property to true.");

		return this.webClient.post()
				.uri("/v1/messages")
				.body(Mono.just(chatRequest), ChatCompletionRequest.class)
				.retrieve()
				.bodyToFlux(String.class)
				.takeUntil(SSE_DONE_PREDICATE)
				.filter(SSE_DONE_PREDICATE.negate())
				.map(content -> ModelOptionsUtils.jsonToObject(content, StreamChatCompletion.class));
	}
}
