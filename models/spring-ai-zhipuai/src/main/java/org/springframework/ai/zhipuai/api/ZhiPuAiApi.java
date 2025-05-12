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

package org.springframework.ai.zhipuai.api;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.ai.model.ChatModelDescription;
import org.springframework.ai.model.ModelOptionsUtils;
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

// @formatter:off
/**
 * Single class implementation of the <a href="https://open.bigmodel.cn/dev/api#http">ZhiPuAI Chat Completion API</a> and
 * <a href="https://open.bigmodel.cn/dev/api#text_embedding">ZhiPuAI Embedding API</a>.
 *
 * @author Geng Rong
 * @author Thomas Vitale
 * @since 1.0.0
 */
public class ZhiPuAiApi {

	public static final String DEFAULT_CHAT_MODEL = ChatModel.GLM_4_Air.getValue();
	public static final String DEFAULT_EMBEDDING_MODEL = EmbeddingModel.Embedding_2.getValue();
	private static final Predicate<String> SSE_DONE_PREDICATE = "[DONE]"::equals;

	private final RestClient restClient;

	private final WebClient webClient;

	private final ZhiPuAiStreamFunctionCallingHelper chunkMerger = new ZhiPuAiStreamFunctionCallingHelper();

	/**
	 * Create a new chat completion api with default base URL.
	 *
	 * @param zhiPuAiToken ZhiPuAI apiKey.
	 */
	public ZhiPuAiApi(String zhiPuAiToken) {
		this(ZhiPuApiConstants.DEFAULT_BASE_URL, zhiPuAiToken);
	}

	/**
	 * Create a new chat completion api.
	 *
	 * @param baseUrl api base URL.
	 * @param zhiPuAiToken ZhiPuAI apiKey.
	 */
	public ZhiPuAiApi(String baseUrl, String zhiPuAiToken) {
		this(baseUrl, zhiPuAiToken, RestClient.builder());
	}

	/**
	 * Create a new chat completion api.
	 *
	 * @param baseUrl api base URL.
	 * @param zhiPuAiToken ZhiPuAI apiKey.
	 * @param restClientBuilder RestClient builder.
	 */
	public ZhiPuAiApi(String baseUrl, String zhiPuAiToken, RestClient.Builder restClientBuilder) {
		this(baseUrl, zhiPuAiToken, restClientBuilder, RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER);
	}

	/**
	 * Create a new chat completion api.
	 *
	 * @param baseUrl api base URL.
	 * @param zhiPuAiToken ZhiPuAI apiKey.
	 * @param restClientBuilder RestClient builder.
	 * @param responseErrorHandler Response error handler.
	 */
	public ZhiPuAiApi(String baseUrl, String zhiPuAiToken, RestClient.Builder restClientBuilder, ResponseErrorHandler responseErrorHandler) {

		Consumer<HttpHeaders> authHeaders = h -> {
			h.setBearerAuth(zhiPuAiToken);
			h.setContentType(MediaType.APPLICATION_JSON);
		};

		this.restClient = restClientBuilder
				.baseUrl(baseUrl)
				.defaultHeaders(authHeaders)
				.defaultStatusHandler(responseErrorHandler)
				.build();

		this.webClient = WebClient.builder() // FIXME: use a builder instead
				.baseUrl(baseUrl)
				.defaultHeaders(authHeaders)
				.build();
	}

	public static  String getTextContent(List<ChatCompletionMessage.MediaContent> content) {
		return content.stream()
				.filter(c -> "text".equals(c.type()))
				.map(ChatCompletionMessage.MediaContent::text)
				.reduce("", (a, b) -> a + b);
	}

	/**
	 * Creates a model response for the given chat conversation.
	 *
	 * @param chatRequest The chat completion request.
	 * @return Entity response with {@link ChatCompletion} as a body and HTTP status code and headers.
	 */
	public ResponseEntity<ChatCompletion> chatCompletionEntity(ChatCompletionRequest chatRequest) {

		Assert.notNull(chatRequest, "The request body can not be null.");
		Assert.isTrue(!chatRequest.stream(), "Request must set the stream property to false.");

		return this.restClient.post()
				.uri("/v4/chat/completions")
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
		Assert.isTrue(chatRequest.stream(), "Request must set the stream property to true.");

		AtomicBoolean isInsideTool = new AtomicBoolean(false);

		return this.webClient.post()
				.uri("/v4/chat/completions")
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
					Mono<ChatCompletionChunk> monoChunk = window.reduce(
							new ChatCompletionChunk(null, null, null, null, null, null),
							this.chunkMerger::merge);
					return List.of(monoChunk);
				})
				.flatMap(mono -> mono);
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

		if (embeddingRequest.input() instanceof List list) {
			Assert.isTrue(!CollectionUtils.isEmpty(list), "The input list can not be empty.");
			Assert.isTrue(list.size() <= 512, "The list must be 512 dimensions or less");
			Assert.isTrue(list.get(0) instanceof String || list.get(0) instanceof Integer
					|| list.get(0) instanceof List,
					"The input must be either a String, or a List of Strings or list of list of integers.");
		}

		return this.restClient.post()
				.uri("/v4/embeddings")
				.body(embeddingRequest)
				.retrieve()
				.toEntity(new ParameterizedTypeReference<>() {
		});
	}

	/**
	 * ZhiPuAI Chat Completion Models:
	 * <a href="https://open.bigmodel.cn/dev/howuse/model">ZhiPuAI Model</a>.
	 */
	public enum ChatModel implements ChatModelDescription {
		GLM_4("GLM-4"),
		GLM_4V("glm-4v"),
		GLM_4_Air("glm-4-air"),
		GLM_4_AirX("glm-4-airx"),
		GLM_4_Flash("glm-4-flash"),
		GLM_3_Turbo("GLM-3-Turbo");

		public final String  value;

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
	 * ZhiPuAI Embeddings Models:
	 * <a href="https://open.bigmodel.cn/dev/api#text_embedding">Embeddings</a>.
	 */
	public enum EmbeddingModel {

		/**
		 * DIMENSION: 1024
		 */
		Embedding_2("Embedding-2"),

		/**
		 * DIMENSION: up to 2048
		 */
		Embedding_3("Embedding-3");

		public final String  value;

		EmbeddingModel(String value) {
			this.value = value;
		}

		public String getValue() {
			return this.value;
		}
	}

	public class Foo {

		String foo;

		public Foo() {

		}
		public Foo(String foo) {
			this.foo = foo;
		}
	}


	/**
	 * Represents a tool the model may call. Currently, only functions are supported as a tool.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public static class FunctionTool {

		// The type of the tool. Currently, only 'function' is supported.
		@JsonProperty("type")
		private Type type = Type.FUNCTION;

		//	The function definition.
		@JsonProperty("function")
		private Function function;

		public FunctionTool() {

		}

		/**
		 * Create a tool of type 'function' and the given function definition.
		 * @param type the tool type
		 * @param function function definition
		 */
		public FunctionTool(
				Type type,
				Function function) {
			this.type = type;
			this.function = function;
		}

		/**
		 * Create a tool of type 'function' and the given function definition.
		 * @param function function definition.
		 */
		public FunctionTool(Function function) {
			this(Type.FUNCTION, function);
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
			 *
			 * @param description A description of what the function does, used by the model to choose when and how to call
			 * the function.
			 * @param name The name of the function to be called. Must be a-z, A-Z, 0-9, or contain underscores and dashes,
			 * with a maximum length of 64.
			 * @param parameters The parameters the functions accepts, described as a JSON Schema object. To describe a
			 * function that accepts no parameters, provide the value {"type": "object", "properties": {}}.
			 */
			public Function(
					String description,
					String name,
					Map<String, Object> parameters) {
				this.description = description;
				this.name = name;
				this.parameters = parameters;
			}

			/**
			 * Create tool function definition.
			 *
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
	 * Creates a model response for the given chat conversation.
	 *
	 * @param messages A list of messages comprising the conversation so far.
	 * @param model ID of the model to use.
	 * @param maxTokens The maximum number of tokens to generate in the chat completion. The total length of input
	 * tokens and generated tokens is limited by the model's context length.
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
	 * functions are present. Use the {@link ToolChoiceBuilder} to create the tool choice value.
	 * @param user A unique identifier representing your end-user, which can help ZhiPuAI to monitor and detect abuse.
	 * @param requestId A unique identifier for the request. If set, the request will be logged and can be used for
	 * debugging purposes.
	 * @param doSample If set, the model will use sampling to generate the next token. If not set, the model will use
	 * greedy decoding to generate the next token.
	 */
	@JsonInclude(Include.NON_NULL)
	public record ChatCompletionRequest(
			@JsonProperty("messages") List<ChatCompletionMessage> messages,
			@JsonProperty("model") String model,
			@JsonProperty("max_tokens") Integer maxTokens,
			@JsonProperty("stop") List<String> stop,
			@JsonProperty("stream") Boolean stream,
			@JsonProperty("temperature") Double temperature,
			@JsonProperty("top_p") Double topP,
			@JsonProperty("tools") List<FunctionTool> tools,
			@JsonProperty("tool_choice") Object toolChoice,
			@JsonProperty("user") String user,
			@JsonProperty("request_id") String requestId,
			@JsonProperty("do_sample") Boolean doSample) {

		/**
		 * Shortcut constructor for a chat completion request with the given messages and model.
		 *
		 * @param messages A list of messages comprising the conversation so far.
		 * @param model ID of the model to use.
		 * @param temperature What sampling temperature to use, between 0 and 1.
		 */
		public ChatCompletionRequest(List<ChatCompletionMessage> messages, String model, Double temperature) {
			this(messages, model, null,  null, false, temperature, null,
					null, null, null, null, null);
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
		public ChatCompletionRequest(List<ChatCompletionMessage> messages, String model, Double temperature, boolean stream) {
			this(messages, model, null,  null,  stream, temperature, null,
					null, null, null, null, null);
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
			this(messages, model, null, null,  false, 0.8, null,
					tools, toolChoice, null, null, null);
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
			this(messages, null, null,  null,  stream, null, null,
					null, null, null, null, null);
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
			public static Object function(String functionName) {
				return Map.of("type", "function", "function", Map.of("name", functionName));
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
	 * @param rawContent The contents of the message. Can be either a {@link MediaContent} or a {@link String}.
	 * The response message content is always a {@link String}.
	 * @param role The role of the messages author. Could be one of the {@link Role} types.
	 * @param name An optional name for the participant. Provides the model information to differentiate between
	 * participants of the same role. In case of Function calling, the name is the function name that the message is
	 * responding to.
	 * @param toolCallId Tool call that this message is responding to. Only applicable for the {@link Role#TOOL} role
	 * and null otherwise.
	 * @param toolCalls The tool calls generated by the model, such as function calls. Applicable only for
	 * {@link Role#ASSISTANT} role and null otherwise.
	 */
	@JsonInclude(Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ChatCompletionMessage(
			@JsonProperty("content") Object rawContent,
			@JsonProperty("role") Role role,
			@JsonProperty("name") String name,
			@JsonProperty("tool_call_id") String toolCallId,
			@JsonProperty("tool_calls") List<ToolCall> toolCalls) {

		/**
		 * Create a chat completion message with the given content and role. All other fields are null.
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
		 * An array of content parts with a defined type.
		 * Each MediaContent can be of either "text" or "image_url" type. Not both.
		 *
		 * @param type Content  type, each can be of type text or image_url.
		 * @param text The text content of the message.
		 * @param imageUrl The image content of the message. You can pass multiple
		 * images by adding multiple image_url content parts. Image input is only
		 * supported when using the glm-4v model.
		 */
		@JsonInclude(Include.NON_NULL)
		@JsonIgnoreProperties(ignoreUnknown = true)
		public record MediaContent(
			@JsonProperty("type") String type,
			@JsonProperty("text") String text,
			@JsonProperty("image_url") ImageUrl imageUrl) {

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

			/**
			 * The image content of the message.
			 * @param url Either a URL of the image or the base64 encoded image data.
			 * The base64 encoded image data must have a special prefix in the following format:
			 * "data:{mimetype};base64,{base64-encoded-image-data}".
			 * @param detail Specifies the detail level of the image.
			 */
			@JsonInclude(Include.NON_NULL)
			@JsonIgnoreProperties(ignoreUnknown = true)
			public record ImageUrl(
				@JsonProperty("url") String url,
				@JsonProperty("detail") String detail) {

				public ImageUrl(String url) {
					this(url, null);
				}
			}
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
		@JsonIgnoreProperties(ignoreUnknown = true)
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
		@JsonIgnoreProperties(ignoreUnknown = true)
		public record ChatCompletionFunction(
				@JsonProperty("name") String name,
				@JsonProperty("arguments") String arguments) {
		}
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
	@JsonIgnoreProperties(ignoreUnknown = true)
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
		@JsonIgnoreProperties(ignoreUnknown = true)
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
	@JsonIgnoreProperties(ignoreUnknown = true)
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
		@JsonIgnoreProperties(ignoreUnknown = true)
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
			@JsonIgnoreProperties(ignoreUnknown = true)
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
	@JsonIgnoreProperties(ignoreUnknown = true)
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
	@JsonIgnoreProperties(ignoreUnknown = true)
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
		@JsonIgnoreProperties(ignoreUnknown = true)
		public record ChunkChoice(
				@JsonProperty("finish_reason") ChatCompletionFinishReason finishReason,
				@JsonProperty("index") Integer index,
				@JsonProperty("delta") ChatCompletionMessage delta,
				@JsonProperty("logprobs") LogProbs logprobs) {
		}
	}

	/**
	 * Represents an embedding vector returned by embedding endpoint.
	 *
	 * @param index The index of the embedding in the list of embeddings.
	 * @param embedding The embedding vector, which is a list of floats. The length of vector depends on the model.
	 * @param object The object type, which is always 'embedding'.
	 */
	@JsonInclude(Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Embedding(
			@JsonProperty("index") Integer index,
			@JsonProperty("embedding") float[] embedding,
			@JsonProperty("object") String object) {

		/**
		 * Create an embedding with the given index, embedding and object type set to 'embedding'.
		 *
		 * @param index The index of the embedding in the list of embeddings.
		 * @param embedding The embedding vector, which is a list of floats. The length of vector depends on the model.
		 */
		public Embedding(Integer index, float[] embedding) {
			this(index, embedding, "embedding");
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (!(o instanceof Embedding embedding1)) {
				return false;
			}
			return Objects.equals(this.index, embedding1.index) && Arrays.equals(this.embedding, embedding1.embedding) && Objects.equals(this.object, embedding1.object);
		}

		@Override
		public int hashCode() {
			int result = Objects.hash(this.index, this.object);
			result = 31 * result + Arrays.hashCode(this.embedding);
			return result;
		}

		@Override
		public String toString() {
			return "Embedding{" +
					"index=" + this.index +
					", embedding=" + Arrays.toString(this.embedding) +
					", object='" + this.object + '\'' +
					'}';
		}
	}

	/**
	 * Creates an embedding vector representing the input text.
	 *
	 * @param <T> Type of the input.
	 * @param input Input text to embed, encoded as a string or array of tokens.
	 * @param model ID of the model to use.
	 */
	@JsonInclude(Include.NON_NULL)
	public record EmbeddingRequest<T>(
			@JsonProperty("input") T input,
			@JsonProperty("model") String model,
			@JsonProperty("dimensions") Integer dimensions) {


		/**
         * Create an embedding request with the given input. Encoding model is set to 'embedding-2'.
         *
         * @param input Input text to embed.
         */
		public EmbeddingRequest(T input) {
			this(input, DEFAULT_EMBEDDING_MODEL, null);
		}

		/**
		 * Create an embedding request with the given input and model.
		 *
		 * @param input
		 * @param model
		 */
		public EmbeddingRequest(T input, String model) {
			this(input, model, null);
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
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record EmbeddingList<T>(
			@JsonProperty("object") String object,
			@JsonProperty("data") List<T> data,
			@JsonProperty("model") String model,
			@JsonProperty("usage") Usage usage) {
	}

}
// @formatter:on
