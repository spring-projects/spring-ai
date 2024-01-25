/*
 * Copyright 2023-2023 the original author or authors.
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

package org.springframework.ai.openai.api;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

// @formatter:off
/**
 * Single class implementation of the OpenAI Chat Completion API: https://beta.openai.com/docs/api-reference/chat and
 * OpenAI Embedding API: https://beta.openai.com/docs/api-reference/embeddings.
 *
 * @author Christian Tzolov
 */
public class OpenAiApi {

	private static final String DEFAULT_BASE_URL = "https://api.openai.com";
	private static final String DEFAULT_EMBEDDING_MODEL = "text-embedding-ada-002";
	private static final Predicate<String> SSE_DONE_PREDICATE = "[DONE]"::equals;

	private final RestClient restClient;
	private final WebClient webClient;
	private final ObjectMapper objectMapper;

	/**
	 * Create an new chat completion api with base URL set to https://api.openai.com
	 *
	 * @param openAiToken OpenAI apiKey.
	 */
	public OpenAiApi(String openAiToken) {
		this(DEFAULT_BASE_URL, openAiToken, RestClient.builder());
	}

	/**
	 * Create a new chat completion api.
	 *
	 * @param baseUrl api base URL.
	 * @param openAiToken OpenAI apiKey.
	 * @param restClientBuilder RestClient builder.
	 */
	public OpenAiApi(String baseUrl, String openAiToken, RestClient.Builder restClientBuilder) {

		this.objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

		Consumer<HttpHeaders> jsonContentHeaders = headers -> {
			headers.setBearerAuth(openAiToken);
			headers.setContentType(MediaType.APPLICATION_JSON);
		};

		var responseErrorHandler = new ResponseErrorHandler() {

			@Override
			public boolean hasError(ClientHttpResponse response) throws IOException {
				return response.getStatusCode().isError();
			}

			@Override
			public void handleError(ClientHttpResponse response) throws IOException {
				if (response.getStatusCode().isError()) {
					if (response.getStatusCode().is4xxClientError()) {
						throw new OpenAiApiClientErrorException(String.format("%s - %s", response.getStatusCode().value(),
							OpenAiApi.this.objectMapper.readValue(response.getBody(), ResponseError.class)));
					}
					throw new OpenAiApiException(String.format("%s - %s", response.getStatusCode().value(),
							OpenAiApi.this.objectMapper.readValue(response.getBody(), ResponseError.class)));
				}
			}
		};

		this.restClient = restClientBuilder
				.baseUrl(baseUrl)
				.defaultHeaders(jsonContentHeaders)
				.defaultStatusHandler(responseErrorHandler)
				.build();

		this.webClient = WebClient.builder()
				.baseUrl(baseUrl)
				.defaultHeaders(jsonContentHeaders)
				.build();
	}


	public static class OpenAiApiException extends RuntimeException {

		public OpenAiApiException(String message) {
			super(message);
		}

		public OpenAiApiException(String message, Throwable cause) {
			super(message, cause);
		}
	}

	/**
	 * Thrown on 4xx client errors, such as 401 - Incorrect API key provided,
	 * 401 - You must be a member of an organization to use the API,
	 * 429 - Rate limit reached for requests, 429 - You exceeded your current quota
	 * , please check your plan and billing details.
	 */
	public static class OpenAiApiClientErrorException extends RuntimeException {

		public OpenAiApiClientErrorException(String message) {
			super(message);
		}

		public OpenAiApiClientErrorException(String message, Throwable cause) {
			super(message, cause);
		}
	}

	/**
	 * API error response.
	 * @param error Error details.
	 */
	@JsonInclude(Include.NON_NULL)
	public record ResponseError(@JsonProperty("error") Error error) {

		/**
		 * Error details.
		 * @param message Error message.
		 * @param type Error type.
		 * @param param Error parameter.
		 * @param code Error code.
		 */
		@JsonInclude(Include.NON_NULL)
		public record Error(
				@JsonProperty("message") String message,
				@JsonProperty("type") String type,
				@JsonProperty("param") String param,
				@JsonProperty("code") String code) {
		}
	}

	/**
	 * Represents a tool the model may call. Currently, only functions are supported as a tool.
	 *
	 * @param type The type of the tool. Currently, only 'function' is supported.
	 * @param function The function definition.
	 */
	@JsonInclude(Include.NON_NULL)
	public record FunctionTool(
			@JsonProperty("type") Type type,
			@JsonProperty("function") Function function) {

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
			public Function(String description, String name, String jsonSchema) {
				this(description, name, parseJson(jsonSchema));
			}
		}
	}

	/**
	 * Creates a model response for the given chat conversation.
	 *
	 * @param messages A list of messages comprising the conversation so far.
	 * @param model ID of the model to use.
	 * @param frequencyPenalty Number between -2.0 and 2.0. Positive values penalize new tokens based on their existing
	 * frequency in the text so far, decreasing the model's likelihood to repeat the same line verbatim.
	 * @param logitBias Modify the likelihood of specified tokens appearing in the completion. Accepts a JSON object
	 * that maps tokens (specified by their token ID in the tokenizer) to an associated bias value from -100 to 100.
	 * Mathematically, the bias is added to the logits generated by the model prior to sampling. The exact effect will
	 * vary per model, but values between -1 and 1 should decrease or increase likelihood of selection; values like -100
	 * or 100 should result in a ban or exclusive selection of the relevant token.
	 * @param maxTokens The maximum number of tokens to generate in the chat completion. The total length of input
	 * tokens and generated tokens is limited by the model's context length.
	 * @param n How many chat completion choices to generate for each input message. Note that you will be charged based
	 * on the number of generated tokens across all of the choices. Keep n as 1 to minimize costs.
	 * @param presencePenalty Number between -2.0 and 2.0. Positive values penalize new tokens based on whether they
	 * appear in the text so far, increasing the model's likelihood to talk about new topics.
	 * @param responseFormat An object specifying the format that the model must output. Setting to { "type":
	 * "json_object" } enables JSON mode, which guarantees the message the model generates is valid JSON.
	 * @param seed This feature is in Beta. If specified, our system will make a best effort to sample
	 * deterministically, such that repeated requests with the same seed and parameters should return the same result.
	 * Determinism is not guaranteed, and you should refer to the system_fingerprint response parameter to monitor
	 * changes in the backend.
	 * @param stop Up to 4 sequences where the API will stop generating further tokens.
	 * @param stream If set, partial message deltas will be sent.Tokens will be sent as data-only server-sent events as
	 * they become available, with the stream terminated by a data: [DONE] message.
	 * @param temperature What sampling temperature to use, between 0 and 1. Higher values like 0.8 will make the output
	 * more random, while lower values like 0.2 will make it more focused and deterministic. We generally recommend
	 * altering this or top_p but not both.
	 * @param topP An alternative to sampling with temperature, called nucleus sampling, where the model considers the
	 * results of the tokens with top_p probability mass. So 0.1 means only the tokens comprising the top 10%
	 * probability mass are considered. We generally recommend altering this or temperature but not both.
	 * @param tools A list of tools the model may call. Currently, only functions are supported as a tool. Use this to
	 * provide a list of functions the model may generate JSON inputs for.
	 * @param toolChoice Controls which (if any) function is called by the model. none means the model will not call a
	 * function and instead generates a message. auto means the model can pick between generating a message or calling a
	 * function. Specifying a particular function via {"type: "function", "function": {"name": "my_function"}} forces
	 * the model to call that function. none is the default when no functions are present. auto is the default if
	 * functions are present.
	 * @param user A unique identifier representing your end-user, which can help OpenAI to monitor and detect abuse.
	 *
	 */
	@JsonInclude(Include.NON_NULL)
	public record ChatCompletionRequest(
			@JsonProperty("messages") List<ChatCompletionMessage> messages,
			@JsonProperty("model") String model,
			@JsonProperty("frequency_penalty") Float frequencyPenalty,
			@JsonProperty("logit_bias") Map<String, Object> logitBias,
			@JsonProperty("max_tokens") Integer maxTokens,
			@JsonProperty("n") Integer n,
			@JsonProperty("presence_penalty") Float presencePenalty,
			@JsonProperty("response_format") ResponseFormat responseFormat,
			@JsonProperty("seed") Integer seed,
			@JsonProperty("stop") String stop,
			@JsonProperty("stream") Boolean stream,
			@JsonProperty("temperature") Float temperature,
			@JsonProperty("top_p") Float topP,
			@JsonProperty("tools") List<FunctionTool> tools,
			@JsonProperty("tool_choice") ToolChoice toolChoice,
			@JsonProperty("user") String user) {

		/**
		 * Shortcut constructor for a chat completion request with the given messages and model.
		 *
		 * @param messages A list of messages comprising the conversation so far.
		 * @param model ID of the model to use.
		 * @param temperature What sampling temperature to use, between 0 and 1.
		 */
		public ChatCompletionRequest(List<ChatCompletionMessage> messages, String model, Float temperature) {
			this(messages, model, 0.0f, null, null, 1, 0.0f,
					null, null, null, false, temperature, null,
					null, null, null);
		}

		/**
		 * Shortcut constructor for a chat completion request with the given messages, model and control for streaming.
		 *
		 * @param messages A list of messages comprising the conversation so far.
		 * @param model ID of the model to use.
		 * @param temperature What sampling temperature to use, between 0 and 1.
		 * @param stream If set, partial message deltas will be sent.Tokens will be sent as data-only server-sent events
		 * as they become available, with the stream terminated by a data: [DONE] message.
		 */
		public ChatCompletionRequest(List<ChatCompletionMessage> messages, String model, Float temperature, boolean stream) {
			this(messages, model, 0.0f, null, null, 1, 0.0f,
					null, null, null, stream, temperature, null,
					null, null, null);
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
				List<FunctionTool> tools, ToolChoice toolChoice) {
			this(messages, model, 0.0f, null, null, 1, 0.0f,
					null, null, null, false, 0.8f, null,
					tools, toolChoice, null);
		}

		/**
		 * Specifies a tool the model should use. Use to force the model to call a specific function.
		 *
		 * @param type The type of the tool. Currently, only 'function' is supported.
		 * @param function single field map for type 'name':'your function name'.
		 */
		@JsonInclude(Include.NON_NULL)
		public record ToolChoice(
				@JsonProperty("type") String type,
				@JsonProperty("function") Map<String, String> function) {

			/**
			 * Create a tool choice of type 'function' and name 'functionName'.
			 * @param functionName Function name of the tool.
			 */
			public ToolChoice(String functionName) {
				this("function", Map.of("name", functionName));
			}
		}

		/**
		 * An object specifying the format that the model must output.
		 * @param type Must be one of 'text' or 'json_object'.
		 */
		@JsonInclude(Include.NON_NULL)
		public record ResponseFormat(
				@JsonProperty("type") String type) {
		}
	}

	/**
	 * Message comprising the conversation.
	 *
	 * @param content The contents of the message.
	 * @param role The role of the messages author. Could be one of the {@link Role} types.
	 * @param name An optional name for the participant. Provides the model information to differentiate between
	 * participants of the same role.
	 * @param toolCallId Tool call that this message is responding to. Only applicable for the {@link Role#TOOL} role
	 * and null otherwise.
	 * @param toolCalls The tool calls generated by the model, such as function calls. Applicable only for
	 * {@link Role#ASSISTANT} role and null otherwise.
	 * @param functionCall Deprecated and replaced by tool_calls. The name and arguments of a function that should be
	 * called, as generated by the model.
	 */
	@JsonInclude(Include.NON_NULL)
	public record ChatCompletionMessage(
			@JsonProperty("content") String content,
			@JsonProperty("role") Role role,
			@JsonProperty("name") String name,
			@JsonProperty("tool_call_id") String toolCallId,
			@JsonProperty("tool_calls") List<ToolCall> toolCalls,
			@JsonProperty("function_call") ChatCompletionFunction functionCall) {

		/**
		 * Create a chat completion message with the given content and role. All other fields are null.
		 * @param content The contents of the message.
		 * @param role The role of the author of this message.
		 */
		public ChatCompletionMessage(String content, Role role) {
			this(content, role, null, null, null, null);
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
			@JsonProperty("assistant") ASSISTANT,
			/**
			 * Tool message.
			 */
			@JsonProperty("tool") TOOL
		}

		/**
		 * The relevant tool call.
		 *
		 * @param id The ID of the tool call. This ID must be referenced when you submit the tool outputs in using the
		 * Submit tool outputs to run endpoint.
		 * @param type The type of tool call the output is required for. For now, this is always function.
		 * @param function The function definition.
		 */
		@JsonInclude(Include.NON_NULL)
		public record ToolCall(
				@JsonProperty("id") String id,
				@JsonProperty("type") String type,
				@JsonProperty("function") ChatCompletionFunction function) {
		}

		/**
		 * The function definition.
		 *
		 * @param name The name of the function.
		 * @param arguments The arguments that the model expects you to pass to the function.
		 */
		@JsonInclude(Include.NON_NULL)
		public record ChatCompletionFunction(
				@JsonProperty("name") String name,
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
		@JsonProperty("stop") STOP,
		/**
		 * The maximum number of tokens specified in the request was reached.
		 */
		@JsonProperty("length") LENGTH,
		/**
		 * The content was omitted due to a flag from our content filters.
		 */
		@JsonProperty("content_filter") CONTENT_FILTER,
		/**
		 * The model called a tool.
		 */
		@JsonProperty("tool_calls") TOOL_CALLS,
		/**
		 * (deprecated) The model called a function.
		 */
		@JsonProperty("function_call") FUNCTION_CALL
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
		 * @param probBytes A list of integers representing the UTF-8 bytes representation
		 * of the token. Useful in instances where characters are represented by multiple
		 * tokens and their byte representations must be combined to generate the correct
		 * text representation. Can be null if there is no bytes representation for the token.
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
			 * @param logprob The log probability of the token.
			 * @param probBytes A list of integers representing the UTF-8 bytes representation
			 * of the token. Useful in instances where characters are represented by multiple
			 * tokens and their byte representations must be combined to generate the correct
			 * text representation. Can be null if there is no bytes representation for the token.
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
	 * @param systemFingerprint This fingerprint represents the backend configuration that the model runs with. Can be
	 * used in conjunction with the seed request parameter to understand when backend changes have been made that might
	 * impact determinism.
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
				.uri("/v1/chat/completions")
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
				.uri("/v1/chat/completions")
				.body(Mono.just(chatRequest), ChatCompletionRequest.class)
				.retrieve()
				.bodyToFlux(String.class)
				// cancels the flux stream after the "[DONE]" is received.
				.takeUntil(SSE_DONE_PREDICATE)
				// filters out the "[DONE]" message.
				.filter(SSE_DONE_PREDICATE.negate())
				.map(content -> parseJson(content, ChatCompletionChunk.class));
	}

	/**
	 * Represents an embedding vector returned by embedding endpoint.
	 *
	 * @param index The index of the embedding in the list of embeddings.
	 * @param embedding The embedding vector, which is a list of floats. The length of vector depends on the model.
	 * @param object The object type, which is always 'embedding'.
	 */
	@JsonInclude(Include.NON_NULL)
	public record Embedding(
			@JsonProperty("index") Integer index,
			@JsonProperty("embedding") List<Double> embedding,
			@JsonProperty("object") String object) {

		/**
		 * Create an embedding with the given index, embedding and object type set to 'embedding'.
		 *
		 * @param index The index of the embedding in the list of embeddings.
		 * @param embedding The embedding vector, which is a list of floats. The length of vector depends on the model.
		 */
		public Embedding(Integer index, List<Double> embedding) {
			this(index, embedding, "embedding");
		}
	}

	/**
	 * Creates an embedding vector representing the input text.
	 *
	 * @param input Input text to embed, encoded as a string or array of tokens. To embed multiple inputs in a single
	 * request, pass an array of strings or array of token arrays. The input must not exceed the max input tokens for
	 * the model (8192 tokens for text-embedding-ada-002), cannot be an empty string, and any array must be 2048
	 * dimensions or less.
	 * @param model ID of the model to use.
	 * @param encodingFormat The format to return the embeddings in. Can be either float or base64.
	 * @param user A unique identifier representing your end-user, which can help OpenAI to monitor and detect abuse.
	 */
	@JsonInclude(Include.NON_NULL)
	public record EmbeddingRequest<T>(
			@JsonProperty("input") T input,
			@JsonProperty("model") String model,
			@JsonProperty("encoding_format") String encodingFormat,
			@JsonProperty("user") String user) {

		/**
		 * Create an embedding request with the given input, model and encoding format set to float.
		 * @param input Input text to embed.
		 * @param model ID of the model to use.
		 */
		public EmbeddingRequest(T input, String model) {
			this(input, model, "float", null);
		}

		/**
		 * Create an embedding request with the given input. Encoding format is set to float and user is null and the
		 * model is set to 'text-embedding-ada-002'.
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
	public record EmbeddingList<T>(
			@JsonProperty("object") String object,
			@JsonProperty("data") List<T> data,
			@JsonProperty("model") String model,
			@JsonProperty("usage") Usage usage) {
	}

	/**
	 * Creates an embedding vector representing the input text or token array.
	 *
	 * @param embeddingRequest The embedding request.
	 * @return Returns list of {@link Embedding} wrapped in {@link EmbeddingList}.
	 * @param <T> Type of the entity in the data list. Can be a {@link String} or {@link List} of tokens (e.g.
	 * Integers). For embedding multiple inputs in a single request, You can pass a {@link List} of {@link String} or
	 * {@link List} of {@link List} of tokens. For example:
	 *
	 * <pre>{@code List.of("text1", "text2", "text3") or List.of(List.of(1, 2, 3), List.of(3, 4, 5))} </pre>
	 */
	public <T> ResponseEntity<EmbeddingList<Embedding>> embeddings(EmbeddingRequest<T> embeddingRequest) {

		Assert.notNull(embeddingRequest, "The request body can not be null.");

		// Input text to embed, encoded as a string or array of tokens. To embed multiple inputs in a single
		// request, pass an array of strings or array of token arrays.
		Assert.notNull(embeddingRequest.input(), "The input can not be null.");
		Assert.isTrue(embeddingRequest.input() instanceof String || embeddingRequest.input() instanceof List,
				"The input must be either a String, or a List of Strings or List of List of integers.");

		// The input must not exceed the max input tokens for the model (8192 tokens for text-embedding-ada-002), cannot
		// be an empty string, and any array must be 2048 dimensions or less.
		if (embeddingRequest.input() instanceof List list) {
			Assert.isTrue(!CollectionUtils.isEmpty(list), "The input list can not be empty.");
			Assert.isTrue(list.size() <= 2048, "The list must be 2048 dimensions or less");
			Assert.isTrue(list.get(0) instanceof String || list.get(0) instanceof Integer
					|| list.get(0) instanceof List,
					"The input must be either a String, or a List of Strings or list of list of integers.");
		}

		return this.restClient.post()
				.uri("/v1/embeddings")
				.body(embeddingRequest)
				.retrieve()
				.toEntity(new ParameterizedTypeReference<>() {
				});
	}

	private static Map<String, Object> parseJson(String jsonSchema) {
		try {
			return new ObjectMapper().readValue(jsonSchema,
					new TypeReference<Map<String, Object>>() {
					});
		}
		catch (Exception e) {
			throw new OpenAiApiException("Failed to parse schema: " + jsonSchema, e);
		}
	}

	private <T> T parseJson(String json, Class<T> type) {
		try {
			return this.objectMapper.readValue(json, type);
		}
		catch (Exception e) {
			throw new OpenAiApiException("Failed to parse schema: " + json, e);
		}
	}
}
// @formatter:on
