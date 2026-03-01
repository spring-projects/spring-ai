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

package org.springframework.ai.cohere.api;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.ai.model.ChatModelDescription;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Java Client library for Cohere Platform. Provides implementation for the
 * <a href="https://docs.cohere.com/reference/chat"> Chat and
 * <a href="https://docs.cohere.com/reference/chat-stream"> Chat Stream
 * <a href="https://docs.cohere.com/reference/embed">Embedding API</a>.
 * <p>
 * Implements <b>Synchronous</b> and <b>Streaming</b> chat completion and supports latest
 * <b>Function Calling</b> features.
 * </p>
 *
 * @author Ricken Bazolo
 */
public class CohereApi {

	public static final String PROVIDER_NAME = AiProvider.COHERE.value();

	private static final String DEFAULT_BASE_URL = "https://api.cohere.com";

	private static final Predicate<String> SSE_DONE_PREDICATE = "[DONE]"::equals;

	private final RestClient restClient;

	private final WebClient webClient;

	private final CohereStreamFunctionCallingHelper chunkMerger = new CohereStreamFunctionCallingHelper();

	/**
	 * Create a new client api with DEFAULT_BASE_URL
	 * @param cohereApiKey Cohere api Key.
	 */
	public CohereApi(String cohereApiKey) {
		this(DEFAULT_BASE_URL, cohereApiKey);
	}

	/**
	 * Create a new client api.
	 * @param baseUrl api base URL.
	 * @param cohereApiKey Cohere api Key.
	 */
	public CohereApi(String baseUrl, String cohereApiKey) {
		this(baseUrl, cohereApiKey, RestClient.builder(), WebClient.builder(),
				RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER);
	}

	/**
	 * Create a new client api.
	 * @param baseUrl api base URL.
	 * @param cohereApiKey Cohere api Key.
	 * @param restClientBuilder RestClient builder.
	 * @param responseErrorHandler Response error handler.
	 */
	public CohereApi(String baseUrl, String cohereApiKey, RestClient.Builder restClientBuilder,
			WebClient.Builder webClientBuilder, ResponseErrorHandler responseErrorHandler) {

		Consumer<HttpHeaders> jsonContentHeaders = headers -> {
			headers.setBearerAuth(cohereApiKey);
			headers.setContentType(MediaType.APPLICATION_JSON);
		};

		this.restClient = restClientBuilder.baseUrl(baseUrl)
			.defaultHeaders(jsonContentHeaders)
			.defaultStatusHandler(responseErrorHandler)
			.build();

		this.webClient = webClientBuilder.clone().baseUrl(baseUrl).defaultHeaders(jsonContentHeaders).build();
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

		return this.restClient.post().uri("/v2/chat/").body(chatRequest).retrieve().toEntity(ChatCompletion.class);
	}

	/**
	 * Creates an embedding vector representing the input text, token array, or images.
	 * @param embeddingRequest The embedding request.
	 * @return Returns {@link EmbeddingResponse} with embeddings data.
	 * @param <T> Type of the entity in the data list. Can be a {@link String} or
	 * {@link List} of tokens (e.g. Integers). For embedding multiple inputs in a single
	 * request, You can pass a {@link List} of {@link String} or {@link List} of
	 * {@link List} of tokens. For example:
	 *
	 * <pre>{@code List.of("text1", "text2", "text3")} </pre>
	 */
	public <T> ResponseEntity<EmbeddingResponse> embeddings(EmbeddingRequest<T> embeddingRequest) {

		Assert.notNull(embeddingRequest, "The request body can not be null.");

		boolean hasTexts = !CollectionUtils.isEmpty(embeddingRequest.texts);
		boolean hasImages = !CollectionUtils.isEmpty(embeddingRequest.images);

		Assert.isTrue(hasTexts || hasImages, "Either texts or images must be provided");
		Assert.isTrue(!(hasTexts && hasImages), "Cannot provide both texts and images in the same request");

		if (hasTexts) {
			Assert.isTrue(embeddingRequest.texts.size() <= 96, "The texts list must be 96 items or less");
		}

		if (hasImages) {
			Assert.isTrue(embeddingRequest.images.size() <= 1, "Only one image per request is supported");
		}

		return this.restClient.post()
			.uri("/v2/embed")
			.body(embeddingRequest)
			.retrieve()
			.toEntity(new ParameterizedTypeReference<>() {

			});
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
			.uri("v2/chat")
			.body(Mono.just(chatRequest), ChatCompletionRequest.class)
			.retrieve()
			.bodyToFlux(String.class)
			.takeUntil(SSE_DONE_PREDICATE)
			.filter(SSE_DONE_PREDICATE.negate())
			.map(content -> ModelOptionsUtils.jsonToObject(content, ChatCompletionChunk.class))
			.groupBy(chunk -> chunk.id() != null ? chunk.id() : "no-id")
			.flatMap(group -> group.reduce(new ChatCompletionChunk(null, null, null, null), this.chunkMerger::merge)
				.filter(chunk -> EventType.MESSAGE_END.value.equals(chunk.type())
						|| (chunk.delta() != null && chunk.delta().finishReason() != null)))
			.map(this.chunkMerger::sanitizeToolCalls)
			.filter(this.chunkMerger::hasValidToolCallsOnly)
			.filter(Objects::nonNull);
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builder for creating CohereApi instances.
	 */
	public static class Builder {

		private String baseUrl = DEFAULT_BASE_URL;

		private String apiKey;

		private RestClient.Builder restClientBuilder = RestClient.builder();

		private WebClient.Builder webClientBuilder = WebClient.builder();

		private ResponseErrorHandler responseErrorHandler = RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER;

		public Builder baseUrl(String baseUrl) {
			this.baseUrl = baseUrl;
			return this;
		}

		public Builder apiKey(String apiKey) {
			this.apiKey = apiKey;
			return this;
		}

		public Builder restClientBuilder(RestClient.Builder restClientBuilder) {
			this.restClientBuilder = restClientBuilder;
			return this;
		}

		public Builder webClientBuilder(WebClient.Builder webClientBuilder) {
			this.webClientBuilder = webClientBuilder;
			return this;
		}

		public Builder responseErrorHandler(ResponseErrorHandler responseErrorHandler) {
			this.responseErrorHandler = responseErrorHandler;
			return this;
		}

		public CohereApi build() {
			Assert.hasText(this.apiKey, "Cohere API key must be set");
			Assert.hasText(this.baseUrl, "Cohere base URL must be set");
			Assert.notNull(this.restClientBuilder, "RestClient.Builder must not be null");
			Assert.notNull(this.webClientBuilder, "WebClient.Builder must not be null");
			Assert.notNull(this.responseErrorHandler, "ResponseErrorHandler must not be null");

			return new CohereApi(this.baseUrl, this.apiKey, this.restClientBuilder, this.webClientBuilder,
					this.responseErrorHandler);
		}

	}

	/**
	 * List of well-known Cohere chat models.
	 *
	 * @see <a href="https://docs.cohere.com/docs/models">Cohere Models Overview</a>
	 */
	public enum ChatModel implements ChatModelDescription {

		COMMAND_A("command-a-03-2025"),

		COMMAND_A_REASONING("command-a-reasoning-08-2025"),

		COMMAND_A_TRANSLATE("command-a-translate-08-2025"),

		COMMAND_A_VISION("command-a-vision-07-2025"),

		COMMAND_A_R7B("command-r7b-12-2024"),

		COMMAND_R_PLUS("command-r-plus-08-2024"),

		COMMAND_R("command-r-08-2024");

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
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Usage(@JsonProperty("billedUnits") BilledUnits billedUnits, @JsonProperty("tokens") Tokens tokens,
			@JsonProperty("cached_tokens") Integer cachedTokens) {
		/**
		 * Bille units
		 *
		 * @param inputTokens The number of billed input tokens.
		 * @param outputTokens The number of billed output tokens.
		 * @param searchUnits The number of billed search units.
		 * @param classifications The number of billed classifications units.
		 */
		public record BilledUnits(@JsonProperty("input_tokens") Integer inputTokens,
				@JsonProperty("output_tokens") Integer outputTokens, @JsonProperty("search_units") Double searchUnits,
				@JsonProperty("classifications") Double classifications) {
		}

		/**
		 * The Tokens
		 *
		 * @param inputTokens The number of tokens used as input to the model.
		 * @param outputTokens The number of tokens produced by the model.
		 */
		public record Tokens(@JsonProperty("input_tokens") Integer inputTokens,
				@JsonProperty("output_tokens") Integer outputTokens) {
		}
	}

	/**
	 * Creates a model request for chat conversation.
	 *
	 * @param model The name of a compatible Cohere model or the ID of a fine-tuned model.
	 * @param messages The prompt(s) to generate completions for, encoded as a list of
	 * dict with role and rawContent. The first prompt role should be user or system.
	 * @param tools A list of tools the model may call. Currently, only functions are
	 * supported as a tool. Use this to provide a list of functions the model may generate
	 * JSON inputs for.
	 * @param documents A list of relevant documents that the model can cite to generate a
	 * more accurate reply. Each document is either a string or document object with
	 * rawContent and metadata.
	 * @param citationOptions Options for controlling citation generation.
	 * @param responseFormat An object specifying the format or schema that the model must
	 * output. Setting to { "type": "json_object" } enables JSON mode, which guarantees
	 * the message the model generates is valid JSON. Setting to { "type": "json_object" ,
	 * "json_schema": schema} allows you to ensure the model provides an answer in a very
	 * specific JSON format by supplying a clear JSON schema.
	 * @param safetyMode Safety modes are not yet configurable in combination with tools,
	 * tool_results and documents parameters.
	 * @param maxTokens The maximum number of tokens to generate in the completion. The
	 * token count of your prompt plus max_tokens cannot exceed the model's context
	 * length.
	 * @param stopSequences A list of tokens that the model should stop generating after.
	 * If set,
	 * @param temperature What sampling temperature to use, between 0.0 and 1.0. Higher
	 * values like 0.8 will make the output more random, while lower values like 0.2 will
	 * make it more focused and deterministic. We generally recommend altering this or p
	 * but not both.
	 * @param seed If specified, the backend will make a best effort to sample tokens
	 * deterministically, such that repeated requests with the same seed and parameters
	 * should return the same result. However, determinism cannot be totally guaranteed.
	 * @param frequencyPenalty Number between 0.0 and 1.0. Used to reduce repetitiveness
	 * of generated tokens. The higher the value, the stronger a penalty is applied to
	 * previously present tokens, proportional to how many times they have already
	 * appeared in the prompt or prior generation.
	 * @param presencePenalty min value of 0.0, max value of 1.0. Used to reduce
	 * repetitiveness of generated tokens. Similar to frequency_penalty, except that this
	 * penalty is applied equally to all tokens that have already appeared, regardless of
	 * their exact frequencies.
	 * @param stream When true, the response will be a SSE stream of events. The final
	 * event will contain the complete response, and will have an event_type of
	 * "stream-end".
	 * @param k Ensures that only the top k most likely tokens are considered for
	 * generation at each step. When k is set to 0, k-sampling is disabled. Defaults to 0,
	 * min value of 0, max value of 500.
	 * @param p Ensures that only the most likely tokens, with total probability mass of
	 * p, are considered for generation at each step. If both k and p are enabled, p acts
	 * after k. Defaults to 0.75. min value of 0.01, max value of 0.99.
	 * @param logprobs Defaults to false. When set to true, the log probabilities of the
	 * generated tokens will be included in the response.
	 * @param toolChoice Used to control whether or not the model will be forced to use a
	 * tool when answering. When REQUIRED is specified, the model will be forced to use at
	 * least one of the user-defined tools, and the tools parameter must be passed in the
	 * request. When NONE is specified, the model will be forced not to use one of the
	 * specified tools, and give a direct response. If tool_choice isn’t specified, then
	 * the model is free to choose whether to use the specified tools or not.
	 * @param strictTools When set to true, tool calls in the Assistant message will be
	 * forced to follow the tool definition strictly.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ChatCompletionRequest(@JsonProperty("model") String model,
			@JsonProperty("messages") List<ChatCompletionMessage> messages,
			@JsonProperty("tools") List<FunctionTool> tools, @JsonProperty("documents") List<Document> documents,
			@JsonProperty("citation_options") CitationOptions citationOptions,
			@JsonProperty("response_format") ResponseFormat responseFormat,
			@JsonProperty("safety_mode") SafetyMode safetyMode, @JsonProperty("max_tokens") Integer maxTokens,
			@JsonProperty("stop_sequences") List<String> stopSequences, @JsonProperty("temperature") Double temperature,
			@JsonProperty("seed") Integer seed, @JsonProperty("frequency_penalty") Double frequencyPenalty,
			@JsonProperty("stream") Boolean stream, @JsonProperty("k") Integer k, @JsonProperty("p") Double p,
			@JsonProperty("logprobs") Boolean logprobs, @JsonProperty("tool_choice") ToolChoice toolChoice,
			@JsonProperty("strict_tools") Boolean strictTools,
			@JsonProperty("presence_penalty") Double presencePenalty) {

		/**
		 * Shortcut constructor for a chat completion request with the given messages and
		 * model.
		 * @param messages The prompt(s) to generate completions for, encoded as a list of
		 * dict with role and rawContent. The first prompt role should be user or system.
		 * @param model ID or name of the model to use.
		 */
		public ChatCompletionRequest(List<ChatCompletionMessage> messages, String model) {
			this(model, messages, null, null, new CitationOptions(CitationMode.FAST), null, SafetyMode.CONTEXTUAL, null,
					null, 0.3, null, null, false, 0, 0.75, false, null, false, null);
		}

		/**
		 * Shortcut constructor for a chat completion request with the given messages,
		 * model and temperature.
		 * @param messages The prompt(s) to generate completions for, encoded as a list of
		 * dict with role and rawContent. The first prompt role should be user or system.
		 * @param model ID or model of the model to use.
		 * @param temperature What sampling temperature to use, between 0.0 and 1.0.
		 * @param stream Whether to stream back partial progress. If set, tokens will be
		 * sent
		 */
		public ChatCompletionRequest(List<ChatCompletionMessage> messages, String model, Double temperature,
				boolean stream) {
			this(model, messages, null, null, new CitationOptions(CitationMode.FAST), null, SafetyMode.CONTEXTUAL, null,
					null, temperature, null, null, stream, 0, 0.75, false, null, false, null);
		}

		/**
		 * Shortcut constructor for a chat completion request with the given messages,
		 * model and temperature.
		 * @param messages The prompt(s) to generate completions for, encoded as a list of
		 * dict with role and rawContent. The first prompt role should be user or system.
		 * @param model ID of the model to use.
		 * @param temperature What sampling temperature to use, between 0.0 and 1.0.
		 *
		 */
		public ChatCompletionRequest(List<ChatCompletionMessage> messages, String model, Double temperature) {
			this(model, messages, null, null, new CitationOptions(CitationMode.FAST), null, SafetyMode.CONTEXTUAL, null,
					null, temperature, null, null, false, 0, 0.75, false, null, false, null);
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
				ToolChoice toolChoice) {
			this(model, messages, tools, null, new CitationOptions(CitationMode.FAST), null, SafetyMode.CONTEXTUAL,
					null, null, 0.75, null, null, false, 0, 0.75, false, toolChoice, false, null);
		}

		/**
		 * Shortcut constructor for a chat completion request with the given messages and
		 * stream.
		 */
		public ChatCompletionRequest(List<ChatCompletionMessage> messages, Boolean stream) {
			this(null, messages, null, null, new CitationOptions(CitationMode.FAST), null, SafetyMode.CONTEXTUAL, null,
					null, 0.75, null, null, stream, 0, 0.75, false, null, false, null);
		}

		/**
		 * An object specifying the format that the model must output.
		 *
		 * @param type Must be one of 'text' or 'json_object'.
		 * @param jsonSchema A specific JSON schema to match, if 'type' is 'json_object'.
		 */
		@JsonInclude(JsonInclude.Include.NON_NULL)
		public record ResponseFormat(@JsonProperty("type") String type,
				@JsonProperty("json_schema") Map<String, Object> jsonSchema) {
		}

		/**
		 * Specifies a tool the model should use
		 */
		public enum ToolChoice {

			REQUIRED, NONE

		}

	}

	/**
	 * Message comprising the conversation. A message from the assistant role can contain
	 * text and tool call information.
	 *
	 * @param role The role of the messages author. Could be one of the {@link Role} types
	 * "assistant".
	 * @param toolCalls The tool calls generated by the model, such as function calls.
	 * Applicable only for {@link Role#ASSISTANT} role and null otherwise.
	 * @param toolPlan A chain-of-thought style reflection and plan that the model
	 * generates when working with Tools.
	 * @param rawContent The contents of the message. Can be either a {@link MediaContent}
	 * or a {@link MessageContent}.
	 * @param citations Tool call that this message is responding to. Only applicable for
	 * the {@link ChatCompletionFinishReason#TOOL_CALL} role and null otherwise.
	 */
	public record ChatCompletionMessage(@JsonProperty("content") Object rawContent, @JsonProperty("role") Role role,
			@JsonProperty("tool_plan") String toolPlan,
			@JsonFormat(
					with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY) @JsonProperty("tool_calls") List<ToolCall> toolCalls,
			@JsonFormat(
					with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY) @JsonProperty("citations") List<ChatCompletionCitation> citations,
			@JsonProperty("tool_call_id") String toolCallId) {

		public ChatCompletionMessage(Object content, Role role) {
			this(content, role, null, null, null, null);
		}

		public ChatCompletionMessage(Object content, Role role, List<ToolCall> toolCalls) {
			this(content, role, null, toolCalls, null, null);
		}

		public ChatCompletionMessage(Object content, Role role, List<ToolCall> toolCalls, String toolPlan) {
			this(content, role, toolPlan, toolCalls, null, null);
		}

		public ChatCompletionMessage(Object content, Role role, String toolCallId) {
			this(content, role, null, null, null, toolCallId);
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
		 * An array of rawContent parts with a defined type. Each MediaContent can be of
		 * either "text" or "image_url" type. Only one option allowed.
		 *
		 * @param type Content type, each can be of type text or image_url.
		 * @param text The text rawContent of the message.
		 * @param imageUrl The image rawContent of the message.
		 */
		@JsonInclude(JsonInclude.Include.NON_NULL)
		public record MediaContent(@JsonProperty("type") String type, @JsonProperty("text") String text,
				@JsonProperty("image_url") ImageUrl imageUrl) {

			/**
			 * Shortcut constructor for a text rawContent.
			 * @param text The text rawContent of the message.
			 */
			public MediaContent(String text) {
				this("text", text, null);
			}

			/**
			 * Shortcut constructor for an image rawContent.
			 * @param imageUrl The image rawContent of the message.
			 */
			public MediaContent(ImageUrl imageUrl) {
				this("image_url", null, imageUrl);
			}

			/**
			 * The level of detail for processing the image.
			 */
			public enum DetailLevel {

				@JsonProperty("low")
				LOW,

				@JsonProperty("high")
				HIGH,

				@JsonProperty("auto")
				AUTO

			}

			/**
			 * Shortcut constructor for an image rawContent.
			 *
			 * @param url Either a URL of the image or the base64 encoded image data. The
			 * base64 encoded image data must have a special prefix in the following
			 * format: "data:{mimetype};base64,{base64-encoded-image-data}".
			 * @param detail The level of detail for processing the image. Can be "low",
			 * "high", or "auto". Defaults to "auto" if not specified.
			 */
			@JsonInclude(JsonInclude.Include.NON_NULL)
			public record ImageUrl(@JsonProperty("url") String url, @JsonProperty("detail") DetailLevel detail) {

				public ImageUrl(String url) {
					this(url, DetailLevel.AUTO);
				}

			}
		}

		/**
		 * Message rawContent that can be either a text or a value.
		 *
		 * @param type The type of the message rawContent, such as "text" or "thinking".
		 * @param text The text rawContent of the message.
		 * @param value The value of the thinking, which can be any object.
		 */
		public record MessageContent(@JsonProperty("type") String type, @JsonProperty("text") String text,
				@JsonProperty("value") Object value) {
		}

		/**
		 * The role of the author of this message.
		 */
		public enum Role {

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
			 * System message.
			 */
			@JsonProperty("system")
			SYSTEM,
			/**
			 * Tool message.
			 */
			@JsonProperty("tool")
			TOOL

		}

		/**
		 * The relevant tool call.
		 *
		 * @param id The ID of the tool call. This ID must be referenced when you submit
		 * the tool outputs in using the Submit tool outputs to run endpoint.
		 * @param type The type of tool call the output is required for. For now, this is
		 * always function.
		 * @param function The function definition.
		 * @param index The index of the tool call in the list of tool calls.
		 */
		@JsonInclude(JsonInclude.Include.NON_NULL)
		public record ToolCall(@JsonProperty("id") String id, @JsonProperty("type") String type,
				@JsonProperty("function") ChatCompletionFunction function, @JsonProperty("index") Integer index) {
		}

		/**
		 * The function definition.
		 *
		 * @param name The name of the function.
		 * @param arguments The arguments that the model expects you to pass to the
		 * function.
		 */
		@JsonInclude(JsonInclude.Include.NON_NULL)
		public record ChatCompletionFunction(@JsonProperty("name") String name,
				@JsonProperty("arguments") String arguments) {
		}

		public record ChatCompletionCitation(
				/**
				 * Start index of the cited snippet in the original source text.
				 */
				@JsonProperty("start") Integer start,
				/**
				 * End index of the cited snippet in the original source text.
				 */
				@JsonProperty("end") Integer end,
				/**
				 * Text snippet that is being cited.
				 */
				@JsonProperty("text") String text, @JsonProperty("sources") List<Source> sources,
				@JsonProperty("type") Type type) {
			/**
			 * The type of citation which indicates what part of the response the citation
			 * is for.
			 */
			public enum Type {

				TEXT_CONTENT, PLAN

			}

			/**
			 * @param type Tool or A document source object containing the unique
			 * identifier of the document and the document itself.
			 * @param id The unique identifier of the document
			 * @param toolOutput map from strings to any Optional if type == tool
			 * @param document map from strings to any Optional if type == document
			 */
			public record Source(@JsonProperty("type") String type, @JsonProperty("id") String id,
					@JsonProperty("tool_output") Map<String, Object> toolOutput,
					@JsonProperty("document") Map<String, Object> document) {
			}
		}

		public record Provider(@JsonProperty("content") List<MessageContent> content, @JsonProperty("role") Role role,
				@JsonProperty("tool_plan") String toolPlan, @JsonProperty("tool_calls") List<ToolCall> toolCalls,
				@JsonProperty("citations") List<ChatCompletionCitation> citations) {
		}
	}

	/**
	 * Used to select the safety instruction inserted into the prompt. Defaults to
	 * CONTEXTUAL. When OFF is specified, the safety instruction will be omitted. Safety
	 * modes are not yet configurable in combination with tools, tool_results and
	 * documents parameters. Note: This parameter is only compatible newer Cohere models,
	 * starting with Command R 08-2024 and Command R+ 08-2024. Note: command-r7b-12-2024
	 * and newer models only support "CONTEXTUAL" and "STRICT" modes.
	 */
	public enum SafetyMode {

		CONTEXTUAL, STRICT, OFF

	}

	/**
	 * Options for controlling citation generation. Defaults to "accurate". Dictates the
	 * approach taken to generating citations as part of the RAG flow by allowing the user
	 * to specify whether they want "accurate" results, "fast" results or no results.
	 * Note: command-r7b-12-2024 and command-a-03-2025 only support "fast" and "off"
	 * modes. The default is "fast".
	 */
	public record CitationOptions(@JsonProperty("mode") CitationMode mode) {
	}

	/**
	 * Options for controlling citation generation. Defaults to "accurate". Dictates the
	 * approach taken to generating citations as part of the RAG flow by allowing the user
	 * to specify whether they want "accurate" results, "fast" results or no results.
	 * Note: command-r7b-12-2024 and command-a-03-2025 only support "fast" and "off"
	 * modes. The default is "fast".
	 */
	public enum CitationMode {

		FAST, ACCURATE, OFF

	}

	/**
	 * relevant documents that the model can cite to generate a more accurate reply. Each
	 * document is either a string or document object with rawContent and metadata.
	 *
	 * @param id An optional Unique identifier for this document which will be referenced
	 * in citations. If not provided an ID will be automatically generated.
	 * @param data A relevant document that the model can cite to generate a more accurate
	 * reply. Each document is a string-any dictionary.
	 */
	public record Document(@JsonProperty("id") String id, @JsonProperty("data") String data) {
	}

	/**
	 * Represents a tool the model may call. Currently, only functions are supported as a
	 * tool.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public static class FunctionTool {

		// The type of the tool. Currently, only 'function' is supported.
		@JsonProperty("type")
		Type type = Type.FUNCTION;

		// The function definition.
		@JsonProperty("function")
		Function function;

		public FunctionTool() {

		}

		/**
		 * Create a tool of type 'function' and the given function definition.
		 * @param function function definition.
		 */
		public FunctionTool(Function function) {
			this(Type.FUNCTION, function);
		}

		public FunctionTool(Type type, Function function) {
			this.type = type;
			this.function = function;
		}

		public Type getType() {
			return this.type;
		}

		public Function getFunction() {
			return this.function;
		}

		public void setType(Type type) {
			this.type = type;
		}

		public void setFunction(Function function) {
			this.function = function;
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
		 */
		public static class Function {

			@JsonProperty("description")
			private String description;

			@JsonProperty("name")
			private String name;

			@JsonProperty("parameters")
			private Map<String, Object> parameters;

			@JsonIgnore
			private String jsonSchema;

			private Function() {

			}

			/**
			 * Create tool function definition.
			 * @param description A description of what the function does, used by the
			 * model to choose when and how to call the function.
			 * @param name The name of the function to be called. Must be a-z, A-Z, 0-9,
			 * or contain underscores and dashes, with a maximum length of 64.
			 * @param parameters The parameters the functions accepts, described as a JSON
			 * Schema object. To describe a function that accepts no parameters, provide
			 * the value {"type": "object", "properties": {}}.
			 */
			public Function(String description, String name, Map<String, Object> parameters) {
				this.description = description;
				this.name = name;
				this.parameters = parameters;
			}

			/**
			 * Create tool function definition.
			 * @param description tool function description.
			 * @param name tool function name.
			 * @param jsonSchema tool function schema as json.
			 */
			public Function(String description, String name, String jsonSchema) {
				this(description, name, ModelOptionsUtils.jsonToMap(jsonSchema));
			}

			public String getDescription() {
				return this.description;
			}

			public String getName() {
				return this.name;
			}

			public Map<String, Object> getParameters() {
				return this.parameters;
			}

			public void setDescription(String description) {
				this.description = description;
			}

			public void setName(String name) {
				this.name = name;
			}

			public void setParameters(Map<String, Object> parameters) {
				this.parameters = parameters;
			}

			public String getJsonSchema() {
				return this.jsonSchema;
			}

			public void setJsonSchema(String jsonSchema) {
				this.jsonSchema = jsonSchema;
				if (jsonSchema != null) {
					this.parameters = ModelOptionsUtils.jsonToMap(jsonSchema);
				}
			}

		}

	}

	/**
	 * Represents a chat completion response returned by model, based on the provided
	 * input.
	 *
	 * @param id A unique identifier for the chat completion.
	 * @param finishReason The reason the model stopped generating tokens.
	 * @param message A chat completion message generated by streamed model responses.
	 * @param logprobs Log probability information for the choice.
	 * @param usage Usage statistics for the completion request.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ChatCompletion(@JsonProperty("id") String id,
			@JsonProperty("finish_reason") ChatCompletionFinishReason finishReason,
			@JsonProperty("message") ChatCompletionMessage.Provider message,
			@JsonProperty("logprobs") LogProbs logprobs, @JsonProperty("usage") Usage usage) {
	}

	/**
	 * The reason the model stopped generating tokens.
	 */
	public enum ChatCompletionFinishReason {

		/**
		 * The model finished sending a complete message.
		 */
		COMPLETE,

		/**
		 * One of the provided stop_sequence entries was reached in the model’s
		 * generation.
		 */
		STOP_SEQUENCE,

		/**
		 * The number of generated tokens exceeded the model’s context length or the value
		 * specified via the max_tokens parameter.
		 */
		MAX_TOKENS,

		/**
		 * The model generated a Tool Call and is expecting a Tool Message in return
		 */
		TOOL_CALL,

		/**
		 * The model called a tool.
		 */
		@JsonProperty("tool_calls")
		TOOL_CALLS,

		/**
		 * The generation failed due to an internal error
		 */
		ERROR

	}

	/**
	 * Log probability information
	 *
	 * @param tokenIds The token ids of each token used to construct the text chunk.
	 * @param text The text chunk for which the log probabilities was calculated.
	 * @param logprobs The log probability of each token used to construct the text chunk.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record LogProbs(@JsonProperty("token_ids") List<Integer> tokenIds, @JsonProperty("text") String text,
			@JsonProperty("logprobs") List<Double> logprobs) {

	}

	/**
	 * Helper factory that creates a tool_choice of type 'REQUIRED', 'NONE' or selected
	 * function by name.
	 */
	public static class ToolChoiceBuilder {

		public static final String NONE = "NONE";

		public static final String REQUIRED = "REQUIRED";

		/**
		 * Specifying a particular function forces the model to call that function.
		 */
		public static Object FUNCTION(String functionName) {
			return Map.of("type", "function", "function", Map.of("name", functionName));
		}

	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ChatCompletionChunk(
	// @formatter:off
			@JsonProperty("id") String id,
			@JsonProperty("type") String type,
			@JsonProperty("index") Integer index,
			@JsonProperty("delta") ChunkDelta delta) {
		// @formatter:on

		@JsonInclude(JsonInclude.Include.NON_NULL)
		@JsonIgnoreProperties(ignoreUnknown = true)
		public record ChunkDelta(
		// @formatter:off
				@JsonProperty("message") ChatCompletionMessage message,
				@JsonProperty("finish_reason") ChatCompletionFinishReason finishReason,
				@JsonProperty("usage") Usage usage) {
			// @formatter:on
		}

	}

	/**
	 * List of well-known Cohere embedding models.
	 *
	 * @see <a href="https://docs.cohere.com/docs/models">Cohere Models Overview</a>
	 */
	public enum EmbeddingModel {

		// @formatter:off

		/**
		 * A model that allows for text and images to be classified or turned into embeddings
		 * dimensional - [256, 512, 1024, 1536 (default)]
		 */
		EMBED_V4("embed-v4.0"),
		/**
		 * Embed v3 Multilingual model for text embeddings.
		 * Produces 1024-dimensional embeddings suitable for multilingual semantic search,
		 * clustering, and other text similarity tasks.
		 */
		EMBED_MULTILINGUAL_V3("embed-multilingual-v3.0"),

		/**
		 * Embed v3 English model for text embeddings.
		 * Produces 1024-dimensional embeddings optimized for English text.
		 */
		EMBED_ENGLISH_V3("embed-english-v3.0"),

		/**
		 * Embed v3 Multilingual Light model.
		 * Smaller and faster variant with 1024 dimensions.
		 */
		EMBED_MULTILINGUAL_LIGHT_V3("embed-multilingual-light-v3.0"),

		/**
		 * Embed v3 English Light model.
		 * Smaller and faster English-only variant with 1024 dimensions.
		 */
		EMBED_ENGLISH_LIGHT_V3("embed-english-light-v3.0");
		// @formatter:on

		private final String value;

		EmbeddingModel(String value) {
			this.value = value;
		}

		public String getValue() {
			return this.value;
		}

	}

	/**
	 * Embedding type
	 */
	public enum EmbeddingType {

		/**
		 * Use this when you want to get back the default float embeddings. Supported with
		 * all Embed models.
		 */
		@JsonProperty("float")
		FLOAT,

		/**
		 * Use this when you want to get back signed int8 embeddings. Supported with Embed
		 * v3.0 and newer Embed models.
		 */
		@JsonProperty("int8")
		INT8,

		/**
		 * Use this when you want to get back unsigned int8 embeddings. Supported with
		 * Embed v3.0 and newer Embed models.
		 */
		@JsonProperty("uint8")
		UINT8,

		/**
		 * Use this when you want to get back signed binary embeddings. Supported with
		 * Embed v3.0 and newer Embed models.
		 */
		@JsonProperty("binary")
		BINARY,

		/**
		 * Use this when you want to get back unsigned binary embeddings. Supported with
		 * Embed v3.0 and newer Embed models.
		 */
		@JsonProperty("ubinary")
		UBINARY,

		/**
		 * Use this when you want to get back base64 embeddings. Supported with Embed v3.0
		 * and newer Embed models.
		 */
		@JsonProperty("base64")
		BASE64

	}

	/**
	 * Embedding request.
	 *
	 * @param texts An array of strings to embed.
	 * @param images An array of images to embed as data URIs.
	 * @param model The model to use for embedding.
	 * @param inputType The type of input (search_document, search_query, classification,
	 * clustering, image).
	 * @param embeddingTypes The types of embeddings to return (float, int8, uint8,
	 * binary, ubinary).
	 * @param truncate How to handle inputs longer than the maximum token length (NONE,
	 * START, END).
	 * @param <T> Type of the input (String or List of tokens).
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record EmbeddingRequest<T>(
	// @formatter:off
			@JsonProperty("texts") List<T> texts,
			@JsonProperty("images") List<String> images,
			@JsonProperty("model") String model,
			@JsonProperty("input_type") InputType inputType,
			@JsonProperty("embedding_types") List<EmbeddingType> embeddingTypes,
			@JsonProperty("truncate") Truncate truncate) {
		// @formatter:on

		public static <T> Builder<T> builder() {
			return new Builder<>();
		}

		public static final class Builder<T> {

			private String model = EmbeddingModel.EMBED_V4.getValue();

			private List<T> texts;

			private List<String> images;

			private InputType inputType = InputType.SEARCH_DOCUMENT;

			private List<EmbeddingType> embeddingTypes = List.of(EmbeddingType.FLOAT);

			private Truncate truncate = Truncate.END;

			public Builder<T> model(String model) {
				this.model = model;
				return this;
			}

			public Builder<T> texts(Object raw) {
				if (raw == null) {
					this.texts = null;
					return this;
				}

				if (raw instanceof List<?> list) {
					this.texts = (List<T>) list;
				}
				else {
					this.texts = List.of((T) raw);
				}
				return this;
			}

			public Builder<T> images(List<String> images) {
				this.images = images;
				return this;
			}

			public Builder<T> inputType(InputType inputType) {
				this.inputType = inputType;
				return this;
			}

			public Builder<T> embeddingTypes(List<EmbeddingType> embeddingTypes) {
				this.embeddingTypes = embeddingTypes;
				return this;
			}

			public Builder<T> truncate(Truncate truncate) {
				this.truncate = truncate;
				return this;
			}

			public EmbeddingRequest<T> build() {
				return new EmbeddingRequest<>(this.texts, this.images, this.model, this.inputType, this.embeddingTypes,
						this.truncate);
			}

		}

		/**
		 * Input type for embeddings.
		 */
		public enum InputType {

			// @formatter:off
			@JsonProperty("search_document")
			SEARCH_DOCUMENT,
			@JsonProperty("search_query")
			SEARCH_QUERY,
			@JsonProperty("classification")
			CLASSIFICATION,
			@JsonProperty("clustering")
			CLUSTERING,
			@JsonProperty("image")
			IMAGE
			// @formatter:on

		}

		/**
		 * Truncation strategy for inputs longer than maximum token length.
		 */
		public enum Truncate {

			// @formatter:off
			@JsonProperty("NONE")
			NONE,
			@JsonProperty("START")
			START,
			@JsonProperty("END")
			END
			// @formatter:on

		}

	}

	/**
	 * Embedding response.
	 *
	 * @param id Unique identifier for the embedding request.
	 * @param embeddings The embeddings
	 * @param texts The texts that were embedded.
	 * @param responseType The type of response ("embeddings_floats" or
	 * "embeddings_by_type").
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record EmbeddingResponse(
	// @formatter:off
			@JsonProperty("id") String id,
			@JsonProperty("embeddings") Object embeddings,
			@JsonProperty("texts") List<String> texts,
			@JsonProperty("response_type") String responseType) {
		// @formatter:on

		/**
		 * Extracts float embeddings from the response. Handles both response formats: -
		 * "embeddings_floats": embeddings is List&lt;List&lt;Double&gt;&gt; -
		 * "embeddings_by_type": embeddings is Map with "float" key containing
		 * List&lt;List&lt;Double&gt;&gt;
		 * @return List of float arrays representing the embeddings
		 */
		@JsonIgnore
		@SuppressWarnings("unchecked")
		public List<float[]> getFloatEmbeddings() {
			if (this.embeddings == null) {
				return List.of();
			}

			// Handle "embeddings_floats" format: embeddings is directly
			// List<List<Double>>
			if (this.embeddings instanceof List<?> embeddingsList) {
				return embeddingsList.stream().map(embedding -> {
					if (embedding instanceof List<?> embeddingVector) {
						float[] floatArray = new float[embeddingVector.size()];
						for (int i = 0; i < embeddingVector.size(); i++) {
							Object value = embeddingVector.get(i);
							floatArray[i] = (value instanceof Number number) ? number.floatValue() : 0f;
						}
						return floatArray;
					}
					return new float[0];
				}).toList();
			}

			// Handle "embeddings_by_type" format: embeddings is Map<String, Object>
			if (this.embeddings instanceof Map<?, ?> embeddingsMap) {
				Object floatEmbeddings = embeddingsMap.get("float");
				if (floatEmbeddings instanceof List<?> embeddingsList) {
					return embeddingsList.stream().map(embedding -> {
						if (embedding instanceof List<?> embeddingVector) {
							float[] floatArray = new float[embeddingVector.size()];
							for (int i = 0; i < embeddingVector.size(); i++) {
								Object value = embeddingVector.get(i);
								floatArray[i] = (value instanceof Number number) ? number.floatValue() : 0f;
							}
							return floatArray;
						}
						return new float[0];
					}).toList();
				}
			}

			return List.of();
		}

	}

	public enum EventType {

		MESSAGE_END("message-end"), CONTENT_START("content-start"), CONTENT_DELTA("content-delta"),
		CONTENT_END("content-end"), TOOL_PLAN_DELTA("tool-plan-delta"), TOOL_CALL_START("tool-call-start"),
		TOOL_CALL_DELTA("tool-call-delta"), CITATION_START("citation-start");

		public final String value;

		EventType(String value) {
			this.value = value;
		}

		public String getValue() {
			return this.value;
		}

	}

}
