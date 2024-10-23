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
package org.springframework.ai.wordlift.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.ai.model.ChatModelDescription;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.wordlift.api.common.WordLiftApiConstants;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

/**
 * Single class implementation of the WordLift API.
 * <p>
 * This code is an adaptation of the OpenAIApi code.
 *
 * @author David Riccitelli
 */
public class WordLiftApi {

  public static final ChatModel DEFAULT_CHAT_MODEL = ChatModel.GPT_4_O;

  private static final Predicate<String> SSE_DONE_PREDICATE = "[DONE]"::equals;

  private final String completionsPath;

  private final RestClient restClient;

  private final WebClient webClient;

  /**
   * Create a new chat completion api with base URL set to https://api.wordlift.io
   *
   * @param apiKey WordLift apiKey.
   */
  public WordLiftApi(String apiKey) {
    this(WordLiftApiConstants.DEFAULT_BASE_URL, apiKey);
  }

  /**
   * Create a new chat completion api.
   *
   * @param baseUrl api base URL.
   * @param apiKey  WordLift API key.
   */
  public WordLiftApi(String baseUrl, String apiKey) {
    this(baseUrl, apiKey, RestClient.builder(), WebClient.builder());
  }

  /**
   * Create a new chat completion api.
   *
   * @param baseUrl           api base URL.
   * @param apiKey            WordLift API key.
   * @param restClientBuilder RestClient builder.
   */
  public WordLiftApi(
    String baseUrl,
    String apiKey,
    RestClient.Builder restClientBuilder,
    WebClient.Builder webClientBuilder
  ) {
    this(
      baseUrl,
      apiKey,
      restClientBuilder,
      webClientBuilder,
      RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER
    );
  }

  /**
   * Create a new chat completion api.
   *
   * @param baseUrl              api base URL.
   * @param apiKey               WordLift API key.
   * @param restClientBuilder    RestClient builder.
   * @param responseErrorHandler Response error handler.
   */
  public WordLiftApi(
    String baseUrl,
    String apiKey,
    RestClient.Builder restClientBuilder,
    WebClient.Builder webClientBuilder,
    ResponseErrorHandler responseErrorHandler
  ) {
    this(
      baseUrl,
      apiKey,
      "/chat/completions",
      restClientBuilder,
      webClientBuilder,
      responseErrorHandler
    );
  }

  /**
   * Create a new chat completion api.
   *
   * @param baseUrl              api base URL.
   * @param apiKey               WordLift API key.
   * @param restClientBuilder    RestClient builder.
   * @param responseErrorHandler Response error handler.
   */
  public WordLiftApi(
    String baseUrl,
    String apiKey,
    String completionsPath,
    RestClient.Builder restClientBuilder,
    WebClient.Builder webClientBuilder,
    ResponseErrorHandler responseErrorHandler
  ) {
    this(
      baseUrl,
      apiKey,
      CollectionUtils.toMultiValueMap(Map.of()),
      completionsPath,
      restClientBuilder,
      webClientBuilder,
      responseErrorHandler
    );
  }

  /**
   * Create a new chat completion api.
   *
   * @param baseUrl              api base URL.
   * @param apiKey               WordLift API key.
   * @param headers              the http headers to use.
   * @param restClientBuilder    RestClient builder.
   * @param responseErrorHandler Response error handler.
   */
  public WordLiftApi(
    String baseUrl,
    String apiKey,
    MultiValueMap<String, String> headers,
    String completionsPath,
    RestClient.Builder restClientBuilder,
    WebClient.Builder webClientBuilder,
    ResponseErrorHandler responseErrorHandler
  ) {
    Assert.hasText(completionsPath, "Completions Path must not be null");
    Assert.notNull(headers, "Headers must not be null");

    this.completionsPath = completionsPath;

    this.restClient =
      restClientBuilder
        .baseUrl(baseUrl)
        .defaultHeaders(h -> {
          h.set("Authorization", "Key " + apiKey);
          h.setContentType(MediaType.APPLICATION_JSON);
          h.addAll(headers);
        })
        .defaultStatusHandler(responseErrorHandler)
        .build();

    this.webClient =
      webClientBuilder
        .baseUrl(baseUrl)
        .defaultHeaders(h -> {
          h.set("Authorization", "Key " + apiKey);
          h.setContentType(MediaType.APPLICATION_JSON);
          h.addAll(headers);
        })
        .build();
  }

  /**
   * Chat Completion Models: -
   */
  public enum ChatModel implements ChatModelDescription {
    /**
     * Mistral Large.
     */
    MISTRAL_LARGE("wl-mistral-large"),

    /**
     * Meta Llama 3.1 70b.
     */
    META_LLAMA_3_1_70B("wl-meta-llama-3-1-70b"),

    /**
     * Meta Llama 3.1 405b.
     */
    META_LLAMA_3_1_405B("wl-meta-llama-3-1-405b"),

    /**
     * Multimodal flagship model that’s cheaper and faster than GPT-4 Turbo.
     */
    GPT_4_O("gpt-4o"),

    /**
     * Affordable and intelligent small model for fast, lightweight tasks. GPT-4o mini
     * is cheaper and more capable than GPT-3.5 Turbo.
     */
    GPT_4_O_MINI("gpt-4o-mini"),

    /**
     * GPT-4.
     */
    GPT_4("gpt-4");

    public final String value;

    ChatModel(String value) {
      this.value = value;
    }

    public String getValue() {
      return value;
    }

    @Override
    public String getName() {
      return this.value;
    }
  }

  /**
   * Represents a tool the model may call. Currently, only functions are supported as a
   * tool.
   *
   * @param type     The type of the tool. Currently, only 'function' is supported.
   * @param function The function definition.
   */
  @JsonInclude(Include.NON_NULL)
  public record FunctionTool( // @formatter:off
    @JsonProperty("type") Type type,
			@JsonProperty("function") Function function
  ) {

		/**
		 * Create a tool of type 'function' and the given function definition.
		 * @param function function definition.
		 */
		@ConstructorBinding
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
			@ConstructorBinding
			public Function(String description, String name, String jsonSchema) {
				this(description, name, ModelOptionsUtils.jsonToMap(jsonSchema));
			}
		}
	} // @formatter:on

  /**
   * Creates a model response for the given chat conversation.
   *
   * @param messages          A list of messages comprising the conversation so far.
   * @param model             ID of the model to use.
   * @param frequencyPenalty  Number between -2.0 and 2.0. Positive values penalize new
   *                          tokens based on their existing frequency in the text so far, decreasing the model's
   *                          likelihood to repeat the same line verbatim.
   * @param logitBias         Modify the likelihood of specified tokens appearing in the
   *                          completion. Accepts a JSON object that maps tokens (specified by their token ID in
   *                          the tokenizer) to an associated bias value from -100 to 100. Mathematically, the
   *                          bias is added to the logits generated by the model prior to sampling. The exact
   *                          effect will vary per model, but values between -1 and 1 should decrease or increase
   *                          likelihood of selection; values like -100 or 100 should result in a ban or
   *                          exclusive selection of the relevant token.
   * @param logprobs          Whether to return log probabilities of the output tokens or not. If
   *                          true, returns the log probabilities of each output token returned in the 'content'
   *                          of 'message'.
   * @param topLogprobs       An integer between 0 and 5 specifying the number of most likely
   *                          tokens to return at each token position, each with an associated log probability.
   *                          'logprobs' must be set to 'true' if this parameter is used.
   * @param maxTokens         The maximum number of tokens to generate in the chat completion.
   *                          The total length of input tokens and generated tokens is limited by the model's
   *                          context length.
   * @param n                 How many chat completion choices to generate for each input message. Note
   *                          that you will be charged based on the number of generated tokens across all the
   *                          choices. Keep n as 1 to minimize costs.
   * @param presencePenalty   Number between -2.0 and 2.0. Positive values penalize new
   *                          tokens based on whether they appear in the text so far, increasing the model's
   *                          likelihood to talk about new topics.
   * @param responseFormat    An object specifying the format that the model must output.
   *                          Setting to { "type": "json_object" } enables JSON mode, which guarantees the
   *                          message the model generates is valid JSON.
   * @param seed              This feature is in Beta. If specified, our system will make a best
   *                          effort to sample deterministically, such that repeated requests with the same seed
   *                          and parameters should return the same result. Determinism is not guaranteed, and
   *                          you should refer to the system_fingerprint response parameter to monitor changes in
   *                          the backend.
   * @param stop              Up to 4 sequences where the API will stop generating further tokens.
   * @param stream            If set, partial message deltas will be sent.Tokens will be sent as
   *                          data-only server-sent events as they become available, with the stream terminated
   *                          by a data: [DONE] message.
   * @param streamOptions     Options for streaming response. Only set this when you set.
   * @param temperature       What sampling temperature to use, between 0 and 1. Higher values
   *                          like 0.8 will make the output more random, while lower values like 0.2 will make it
   *                          more focused and deterministic. We generally recommend altering this or top_p but
   *                          not both.
   * @param topP              An alternative to sampling with temperature, called nucleus sampling,
   *                          where the model considers the results of the tokens with top_p probability mass. So
   *                          0.1 means only the tokens comprising the top 10% probability mass are considered.
   *                          We generally recommend altering this or temperature but not both.
   * @param tools             A list of tools the model may call. Currently, only functions are
   *                          supported as a tool. Use this to provide a list of functions the model may generate
   *                          JSON inputs for.
   * @param toolChoice        Controls which (if any) function is called by the model. none
   *                          means the model will not call a function and instead generates a message. auto
   *                          means the model can pick between generating a message or calling a function.
   *                          Specifying a particular function via {"type: "function", "function": {"name":
   *                          "my_function"}} forces the model to call that function. none is the default when no
   *                          functions are present. auto is the default if functions are present. Use the
   *                          {@link ToolChoiceBuilder} to create the tool choice value.
   * @param user              A unique identifier representing your end-user, which can help OpenAI
   *                          to monitor and detect abuse.
   * @param parallelToolCalls If set to true, the model will call all functions in the
   *                          tools list in parallel. Otherwise, the model will call the functions in the tools
   *                          list in the order they are provided.
   */
  @JsonInclude(Include.NON_NULL)
  public record ChatCompletionRequest( // @formatter:off
    @JsonProperty("messages") List<ChatCompletionMessage> messages,
			// While we keep the `model` here, we don't send it to the API request. The `model` is desumed by the path.
			@JsonProperty("model") String model,
			@JsonProperty("frequency_penalty") Float frequencyPenalty,
			@JsonProperty("logit_bias") Map<String, Integer> logitBias,
			@JsonProperty("logprobs") Boolean logprobs,
			@JsonProperty("top_logprobs") Integer topLogprobs,
			@JsonProperty("max_tokens") Integer maxTokens,
			@JsonProperty("n") Integer n,
			@JsonProperty("presence_penalty") Float presencePenalty,
			@JsonProperty("response_format") ResponseFormat responseFormat,
			@JsonProperty("seed") Integer seed,
			@JsonProperty("stop") List<String> stop,
			@JsonProperty("stream") Boolean stream,
			@JsonProperty("stream_options") StreamOptions streamOptions,
			@JsonProperty("temperature") Float temperature,
			@JsonProperty("top_p") Float topP,
			@JsonProperty("tools") List<FunctionTool> tools,
			@JsonProperty("tool_choice") Object toolChoice,
			@JsonProperty("parallel_tool_calls") Boolean parallelToolCalls,
			@JsonProperty("user") String user
  ) {

		/**
		 * Shortcut constructor for a chat completion request with the given messages, model and temperature.
		 *
		 * @param messages A list of messages comprising the conversation so far.
		 * @param model ID of the model to use.
		 * @param temperature What sampling temperature to use, between 0 and 1.
		 */
		public ChatCompletionRequest(List<ChatCompletionMessage> messages, String model, Float temperature) {
			this(messages, model, null, null, null, null, null, null, null,
					null, null, null, false, null, temperature, null,
					null, null, null, null);
		}

		/**
		 * Shortcut constructor for a chat completion request with the given messages, model, temperature and control for streaming.
		 *
		 * @param messages A list of messages comprising the conversation so far.
		 * @param model ID of the model to use.
		 * @param temperature What sampling temperature to use, between 0 and 1.
		 * @param stream If set, partial message deltas will be sent.Tokens will be sent as data-only server-sent events
		 * as they become available, with the stream terminated by a data: [DONE] message.
		 */
		public ChatCompletionRequest(List<ChatCompletionMessage> messages, String model, Float temperature, boolean stream) {
			this(messages, model, null, null, null, null, null, null, null,
					null, null, null, stream, null, temperature, null,
					null, null, null,  null);
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
			this(messages, model, null, null, null, null, null, null, null,
					null, null, null, false, null, 0.8f, null,
					tools, toolChoice, null, null);
		}

		/**
		 * Shortcut constructor for a chat completion request with the given messages for streaming.
		 *
		 * @param messages A list of messages comprising the conversation so far.
		 * @param stream If set, partial message deltas will be sent.Tokens will be sent as data-only server-sent events
		 * as they become available, with the stream terminated by a data: [DONE] message.
		 */
		public ChatCompletionRequest(List<ChatCompletionMessage> messages, Boolean stream) {
			this(messages, null, null, null, null, null, null, null, null,
					null, null, null, stream, null, null, null,
					null, null, null, null);
		}

		/**
		 * Sets the {@link StreamOptions} for this request.
		 *
		 * @param streamOptions The new stream options to use.
		 * @return A new {@link ChatCompletionRequest} with the specified stream options.
		 */
		public ChatCompletionRequest withStreamOptions(StreamOptions streamOptions) {
			return new ChatCompletionRequest(messages, model, frequencyPenalty, logitBias, logprobs, topLogprobs, maxTokens, n, presencePenalty,
					responseFormat, seed, stop, stream, streamOptions, temperature, topP,
					tools, toolChoice, parallelToolCalls, user);
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
			public static Object FUNCTION(String functionName) {
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

		/**
		 * @param includeUsage If set, an additional chunk will be streamed
		 * before the data: [DONE] message. The usage field on this chunk
		 * shows the token usage statistics for the entire request, and
		 * the choices field will always be an empty array. All other chunks
		 * will also include a usage field, but with a null value.
		 */
		@JsonInclude(Include.NON_NULL)
		public record StreamOptions(
				@JsonProperty("include_usage") Boolean includeUsage) {

			public static StreamOptions INCLUDE_USAGE = new StreamOptions(true);
		}
	} // @formatter:on

  /**
   * Message comprising the conversation.
   *
   * @param rawContent The contents of the message. Can be either a {@link MediaContent}
   *                   or a {@link String}. The response message content is always a {@link String}.
   * @param role       The role of the messages author. Could be one of the {@link Role}
   *                   types.
   * @param name       An optional name for the participant. Provides the model information to
   *                   differentiate between participants of the same role. In case of Function calling,
   *                   the name is the function name that the message is responding to.
   * @param toolCallId Tool call that this message is responding to. Only applicable for
   *                   the {@link Role#TOOL} role and null otherwise.
   * @param toolCalls  The tool calls generated by the model, such as function calls.
   *                   Applicable only for {@link Role#ASSISTANT} role and null otherwise.
   */
  @JsonInclude(Include.NON_NULL)
  public record ChatCompletionMessage( // @formatter:off
    @JsonProperty("content") Object rawContent,
			@JsonProperty("role") Role role,
			@JsonProperty("name") String name,
			@JsonProperty("tool_call_id") String toolCallId,
			@JsonProperty("tool_calls") List<ToolCall> toolCalls
  ) { // @formatter:on
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
     * Create a chat completion message with the given content and role. All other
     * fields are null.
     *
     * @param content The contents of the message.
     * @param role    The role of the author of this message.
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
      TOOL,
    }

    /**
     * An array of content parts with a defined type. Each MediaContent can be of
     * either "text" or "image_url" type. Not both.
     *
     * @param type     Content type, each can be of type text or image_url.
     * @param text     The text content of the message.
     * @param imageUrl The image content of the message. You can pass multiple images
     *                 by adding multiple image_url content parts. Image input is only supported when
     *                 using the gpt-4-visual-preview model.
     */
    @JsonInclude(Include.NON_NULL)
    public record MediaContent( // @formatter:off
      @JsonProperty("type") String type,
			@JsonProperty("text") String text,
			@JsonProperty("image_url") ImageUrl imageUrl
    ) {
      // @formatter:on

      /**
       * @param url    Either a URL of the image or the base64 encoded image data. The
       *               base64 encoded image data must have a special prefix in the following
       *               format: "data:{mimetype};base64,{base64-encoded-image-data}".
       * @param detail Specifies the detail level of the image.
       */
      @JsonInclude(Include.NON_NULL)
      public record ImageUrl(
        @JsonProperty("url") String url,
        @JsonProperty("detail") String detail
      ) {
        public ImageUrl(String url) {
          this(url, null);
        }
      }

      /**
       * Shortcut constructor for a text content.
       *
       * @param text The text content of the message.
       */
      public MediaContent(String text) {
        this("text", text, null);
      }

      /**
       * Shortcut constructor for an image content.
       *
       * @param imageUrl The image content of the message.
       */
      public MediaContent(ImageUrl imageUrl) {
        this("image_url", null, imageUrl);
      }
    }

    /**
     * The relevant tool call.
     *
     * @param id       The ID of the tool call. This ID must be referenced when you submit
     *                 the tool outputs in using the Submit tool outputs to run endpoint.
     * @param type     The type of tool call the output is required for. For now, this is
     *                 always function.
     * @param function The function definition.
     */
    @JsonInclude(Include.NON_NULL)
    public record ToolCall( // @formatter:off
      @JsonProperty("id") String id,
				@JsonProperty("type") String type,
				@JsonProperty("function") ChatCompletionFunction function
    ) {
        } // @formatter:on

    /**
     * The function definition.
     *
     * @param name      The name of the function.
     * @param arguments The arguments that the model expects you to pass to the
     *                  function.
     */
    @JsonInclude(Include.NON_NULL)
    public record ChatCompletionFunction( // @formatter:off
      @JsonProperty("name") String name,
				@JsonProperty("arguments") String arguments
    ) {
        } // @formatter:on
  }

  public static String getTextContent(
    List<ChatCompletionMessage.MediaContent> content
  ) {
    return content
      .stream()
      .filter(c -> "text".equals(c.type()))
      .map(ChatCompletionMessage.MediaContent::text)
      .reduce("", (a, b) -> a + b);
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
    TOOL_CALL,
  }

  /**
   * Represents a chat completion response returned by model, based on the provided
   * input.
   *
   * @param id                A unique identifier for the chat completion.
   * @param choices           A list of chat completion choices. Can be more than one if n is
   *                          greater than 1.
   * @param created           The Unix timestamp (in seconds) of when the chat completion was
   *                          created.
   * @param model             The model used for the chat completion.
   * @param systemFingerprint This fingerprint represents the backend configuration that
   *                          the model runs with. Can be used in conjunction with the seed request parameter to
   *                          understand when backend changes have been made that might impact determinism.
   * @param object            The object type, which is always chat.completion.
   * @param usage             Usage statistics for the completion request.
   */
  @JsonInclude(Include.NON_NULL)
  public record ChatCompletion( // @formatter:off
    @JsonProperty("id") String id,
			@JsonProperty("choices") List<Choice> choices,
			@JsonProperty("created") Long created,
			@JsonProperty("model") String model,
			@JsonProperty("system_fingerprint") String systemFingerprint,
			@JsonProperty("object") String object,
			@JsonProperty("usage") Usage usage
  ) { // @formatter:on
    /**
     * Chat completion choice.
     *
     * @param finishReason The reason the model stopped generating tokens.
     * @param index        The index of the choice in the list of choices.
     * @param message      A chat completion message generated by the model.
     * @param logprobs     Log probability information for the choice.
     */
    @JsonInclude(Include.NON_NULL)
    public record Choice( // @formatter:off
      @JsonProperty("finish_reason") ChatCompletionFinishReason finishReason,
				@JsonProperty("index") Integer index,
				@JsonProperty("message") ChatCompletionMessage message,
				@JsonProperty("logprobs") LogProbs logprobs
    ) {
        } // @formatter:on
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
     * @param token       The token.
     * @param logprob     The log probability of the token.
     * @param probBytes   A list of integers representing the UTF-8 bytes representation
     *                    of the token. Useful in instances where characters are represented by multiple
     *                    tokens and their byte representations must be combined to generate the correct
     *                    text representation. Can be null if there is no bytes representation for the
     *                    token.
     * @param topLogprobs List of the most likely tokens and their log probability, at
     *                    this token position. In rare cases, there may be fewer than the number of
     *                    requested top_logprobs returned.
     */
    @JsonInclude(Include.NON_NULL)
    public record Content( // @formatter:off
      @JsonProperty("token") String token,
				@JsonProperty("logprob") Float logprob,
				@JsonProperty("bytes") List<Integer> probBytes,
				@JsonProperty("top_logprobs") List<TopLogProbs> topLogprobs
    ) { // @formatter:on
      /**
       * The most likely tokens and their log probability, at this token position.
       *
       * @param token     The token.
       * @param logprob   The log probability of the token.
       * @param probBytes A list of integers representing the UTF-8 bytes
       *                  representation of the token. Useful in instances where characters are
       *                  represented by multiple tokens and their byte representations must be
       *                  combined to generate the correct text representation. Can be null if there
       *                  is no bytes representation for the token.
       */
      @JsonInclude(Include.NON_NULL)
      public record TopLogProbs( // @formatter:off
        @JsonProperty("token") String token,
					@JsonProperty("logprob") Float logprob,
					@JsonProperty("bytes") List<Integer> probBytes
      ) {
            } // @formatter:on
    }
  }

  /**
   * Usage statistics for the completion request.
   *
   * @param completionTokens Number of tokens in the generated completion. Only
   *                         applicable for completion requests.
   * @param promptTokens     Number of tokens in the prompt.
   * @param totalTokens      Total number of tokens used in the request (prompt +
   *                         completion).
   */
  @JsonInclude(Include.NON_NULL)
  public record Usage( // @formatter:off
    @JsonProperty("completion_tokens") Integer completionTokens,
			@JsonProperty("prompt_tokens") Integer promptTokens,
			@JsonProperty("total_tokens") Integer totalTokens
  ) {
    } // @formatter:on

  /**
   * Represents a streamed chunk of a chat completion response returned by model, based
   * on the provided input.
   *
   * @param id                A unique identifier for the chat completion. Each chunk has the same ID.
   * @param choices           A list of chat completion choices. Can be more than one if n is
   *                          greater than 1.
   * @param created           The Unix timestamp (in seconds) of when the chat completion was
   *                          created. Each chunk has the same timestamp.
   * @param model             The model used for the chat completion.
   * @param systemFingerprint This fingerprint represents the backend configuration that
   *                          the model runs with. Can be used in conjunction with the seed request parameter to
   *                          understand when backend changes have been made that might impact determinism.
   * @param object            The object type, which is always 'chat.completion.chunk'.
   */
  @JsonInclude(Include.NON_NULL)
  public record ChatCompletionChunk( // @formatter:off
    @JsonProperty("id") String id,
			@JsonProperty("choices") List<ChunkChoice> choices,
			@JsonProperty("created") Long created,
			@JsonProperty("model") String model,
			@JsonProperty("system_fingerprint") String systemFingerprint,
			@JsonProperty("object") String object,
			@JsonProperty("usage") Usage usage
  ) { // @formatter:on
    /**
     * Chat completion choice.
     *
     * @param finishReason The reason the model stopped generating tokens.
     * @param index        The index of the choice in the list of choices.
     * @param delta        A chat completion delta generated by streamed model responses.
     * @param logprobs     Log probability information for the choice.
     */
    @JsonInclude(Include.NON_NULL)
    public record ChunkChoice( // @formatter:off
      @JsonProperty("finish_reason") ChatCompletionFinishReason finishReason,
				@JsonProperty("index") Integer index,
				@JsonProperty("delta") ChatCompletionMessage delta,
				@JsonProperty("logprobs") LogProbs logprobs
    ) {
        } // @formatter:on
  }

  /**
   * Creates a model response for the given chat conversation.
   *
   * @param chatRequest The chat completion request.
   * @return Entity response with {@link ChatCompletion} as a body and HTTP status code
   * and headers.
   */
  public ResponseEntity<ChatCompletion> chatCompletionEntity(
    ChatCompletionRequest chatRequest
  ) {
    return chatCompletionEntity(chatRequest, new LinkedMultiValueMap<>());
  }

  /**
   * Creates a model response for the given chat conversation.
   *
   * @param chatRequest          The chat completion request.
   * @param additionalHttpHeader Optional, additional HTTP headers to be added to the
   *                             request.
   * @return Entity response with {@link ChatCompletion} as a body and HTTP status code
   * and headers.
   */
  public ResponseEntity<ChatCompletion> chatCompletionEntity(
    ChatCompletionRequest chatRequest,
    MultiValueMap<String, String> additionalHttpHeader
  ) {
    Assert.notNull(chatRequest, "The request body can not be null.");
    Assert.isTrue(
      !chatRequest.stream(),
      "Request must set the steam property to false."
    );
    Assert.notNull(
      additionalHttpHeader,
      "The additional HTTP headers can not be null."
    );

    return this.restClient.post()
      .uri(this.completionsPath, Map.of("model", chatRequest.model))
      .headers(headers -> headers.addAll(additionalHttpHeader))
      .body(chatRequest)
      .retrieve()
      .toEntity(ChatCompletion.class);
  }

  private WordLiftStreamFunctionCallingHelper chunkMerger =
    new WordLiftStreamFunctionCallingHelper();

  /**
   * Creates a streaming chat response for the given chat conversation.
   *
   * @param chatRequest The chat completion request. Must have the stream property set
   *                    to true.
   * @return Returns a {@link Flux} stream from chat completion chunks.
   */
  public Flux<ChatCompletionChunk> chatCompletionStream(
    ChatCompletionRequest chatRequest
  ) {
    return chatCompletionStream(chatRequest, new LinkedMultiValueMap<>());
  }

  /**
   * Creates a streaming chat response for the given chat conversation.
   *
   * @param chatRequest          The chat completion request. Must have the stream property set
   *                             to true.
   * @param additionalHttpHeader Optional, additional HTTP headers to be added to the
   *                             request.
   * @return Returns a {@link Flux} stream from chat completion chunks.
   */
  public Flux<ChatCompletionChunk> chatCompletionStream(
    ChatCompletionRequest chatRequest,
    MultiValueMap<String, String> additionalHttpHeader
  ) {
    Assert.notNull(chatRequest, "The request body can not be null.");
    Assert.isTrue(
      chatRequest.stream(),
      "Request must set the steam property to true."
    );

    AtomicBoolean isInsideTool = new AtomicBoolean(false);

    return this.webClient.post()
      .uri(this.completionsPath, Map.of("model", chatRequest.model))
      .headers(headers -> headers.addAll(additionalHttpHeader))
      .body(Mono.just(chatRequest), ChatCompletionRequest.class)
      .retrieve()
      .bodyToFlux(String.class)
      // cancels the flux stream after the "[DONE]" is received.
      .takeUntil(SSE_DONE_PREDICATE)
      // filters out the "[DONE]" message.
      .filter(SSE_DONE_PREDICATE.negate())
      .map(content ->
        ModelOptionsUtils.jsonToObject(content, ChatCompletionChunk.class)
      )
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
        if (
          isInsideTool.get() &&
          this.chunkMerger.isStreamingToolFunctionCallFinish(chunk)
        ) {
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
          new ChatCompletionChunk(null, null, null, null, null, null, null),
          (previous, current) -> this.chunkMerger.merge(previous, current)
        );
        return List.of(monoChunk);
      })
      // Flux<Mono<ChatCompletionChunk>> -> Flux<ChatCompletionChunk>
      .flatMap(mono -> mono);
  }
}
