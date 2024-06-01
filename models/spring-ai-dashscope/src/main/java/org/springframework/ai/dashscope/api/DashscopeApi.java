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
package org.springframework.ai.dashscope.api;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.dashscope.record.TokenUsage;
import org.springframework.ai.dashscope.record.chat.ChatCompletion;
import org.springframework.ai.dashscope.record.chat.ChatCompletionRequest;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

// @formatter:off
/**
 * Single class implementation of the Dashscope Chat Completion API:
 * https://help.aliyun.com/zh/dashscope/developer-reference/api-details and Dashscope Embedding API:
 * https://help.aliyun.com/zh/dashscope/developer-reference/text-embedding-api-details.
 *
 * @author Nottyjay Ji
 */
public class DashscopeApi {

  private static final Logger logger = LoggerFactory.getLogger(DashscopeApi.class);

  /** Default chat model */
  public static final String DEFAULT_CHAT_MODEL = ChatModel.QWEN_PLUS.getModel();

  /** Default embedding model */
  public static final String DEFAULT_EMBEDDING_MODEL = EmbeddingModel.TEXT_EMBEDDING_V1.getModel();

  private static final Predicate<String> SSE_DONE_PREDICATE = "event:result"::equals;

  private final RestClient restClient;
  private final WebClient webClient;
  private final String apiKey;

  public DashscopeApi(String apiKey) {
    this(DashscopeApiUtils.DEFAULT_BASE_URL, apiKey);
  }

  public DashscopeApi(String baseUrl, String apiKey) {
    this(baseUrl, apiKey, RestClient.builder());
  }

  public DashscopeApi(String baseUrl, String apiKey, RestClient.Builder restClientBuilder) {
    this(baseUrl, apiKey, restClientBuilder, RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER);
  }

  public DashscopeApi(
      String baseUrl,
      String apiKey,
      RestClient.Builder restClientBuilder,
      ResponseErrorHandler responseErrorHandler) {
    this.apiKey = apiKey;
    this.restClient =
        restClientBuilder
            .baseUrl(baseUrl)
            .defaultHeaders(DashscopeApiUtils.getJsonContentHeaders(apiKey))
            .defaultStatusHandler(responseErrorHandler)
            .build();

    this.webClient =
        WebClient.builder()
            .baseUrl(baseUrl)
            .defaultHeaders(DashscopeApiUtils.getJsonContentHeaders(apiKey))
            .build();
  }

  public ResponseEntity embeddings(EmbeddingRequest embeddingRequest) {
    Assert.notNull(embeddingRequest, "The request body can not be null.");

    // Input text to embed, encoded as a string or array of tokens. To embed multiple inputs in a
    // single
    // request, pass an array of strings.
    Assert.notNull(embeddingRequest.input(), "The input can not be null.");

    return this.restClient
        .post()
        .uri("/api/v1/services/embeddings/text-embedding/text-embedding")
        .body(embeddingRequest)
        .retrieve()
        .toEntity(DashscopeEmbeddingResponse.class);
  }

  public ResponseEntity<ChatCompletion> chatCompletionEntity(ChatCompletionRequest chatRequest) {
    Assert.notNull(chatRequest, "The request body can not be null.");
    Assert.isTrue(!chatRequest.stream(), "Request must set the steam property to false.");
    return this.restClient
        .post()
        .uri("/api/v1/services/aigc/text-generation/generation")
        //            .header("Accept", chatRequest.stream() ? "text/event-stream" : "*/*")
        .body(chatRequest)
        .retrieve()
        .toEntity(ChatCompletion.class);
  }

  public static DashScopeApiBuilder builder() {
    return new DashScopeApiBuilder();
  }

  public Flux<ChatCompletion> chatCompletionStream(ChatCompletionRequest chatRequest) {
    Assert.notNull(chatRequest, "The request body can not be null.");
    Assert.isTrue(chatRequest.stream(), "Request must set the steam property to false.");

    AtomicBoolean isInsideTool = new AtomicBoolean(false);
    return this.webClient
        .post()
        .uri("/api/v1/services/aigc/text-generation/generation")
        .header("Accept", "text/event-stream")
        .body(Mono.just(chatRequest), ChatCompletionRequest.class)
        .retrieve()
        .bodyToFlux(String.class)
        .takeUntil(SSE_DONE_PREDICATE)
        .filter(SSE_DONE_PREDICATE.negate())
        .map(content -> ModelOptionsUtils.jsonToObject(content, ChatCompletion.class));
  }

  public static class DashScopeApiBuilder {

    private String apiKey;

    private DashScopeApiBuilder() {}

    public DashScopeApiBuilder withApiKey(String apiKey) {
      this.apiKey = apiKey;
      return this;
    }

    public DashscopeApi build() {
      return new DashscopeApi(apiKey);
    }
  }

  /*
   * Dashscope Chat Completion Models:
   * <a href="https://help.aliyun.com/zh/dashscope/developer-reference/api-details">Dashscope Chat API</a>
   */
  public enum ChatModel {

    /** 模型支持8k tokens上下文，为了保证正常的使用和输出，API限定用户输入为6k tokens。 */
    QWEN_PLUS("qwen-plus"),

    /** 模型支持32k tokens上下文，为了保证正常的使用和输出，API限定用户输入为30k tokens。 */
    QWEN_TURBO("qwen-turbo"),

    /** 模型支持8k tokens上下文，为了保证正常的使用和输出，API限定用户输入为6k tokens。 */
    QWEN_MAX("qwen-max"),

    /** 模型支持30k tokens上下文，为了保证正常的使用和输出，API限定用户输入为28k tokens。 */
    QWEN_MAX_LONGCONTEXT("qwen-max-longcontext");

    private final String model;

    ChatModel(String model) {
      this.model = model;
    }

    public String getModel() {
      return this.model;
    }
  }

  public enum EmbeddingModel {
    TEXT_EMBEDDING_V1("text-embedding-v1"),
    TEXT_EMBEDDING_V2("text-embedding-v2");

    private String model;

    EmbeddingModel(String model) {
      this.model = model;
    }

    public String getModel() {
      return model;
    }
  }

  /**
   * Creates an embedding vector representing the input text.
   *
   * @param input Input text to embed, encoded as a string or array of tokens. To embed multiple
   *     inputs in a single * request, pass an array of strings or array of token arrays. The input
   *     cannot be an empty string, and any array must be 2048 * dimensions or less.
   * @param model ID of the model to use.
   */
  @JsonInclude(Include.NON_NULL)
  public record EmbeddingRequest(
      @JsonProperty("input") EmbeddingTextList input, @JsonProperty("model") String model) {
    /**
     * Create an embedding request with the given input, model and encoding format set to float.
     *
     * @param input Input text to embed.
     * @param model ID of the model to use.
     */
    public EmbeddingRequest(EmbeddingTextList input, String model) {
      this.input = input;
      this.model = model;
    }

    /**
     * Create an embedding request with the given input. Encoding format is set to float and user is
     * null and the model is set to 'text-embedding-v1'.
     *
     * @param input Input text to embed.
     */
    public EmbeddingRequest(EmbeddingTextList input) {
      this(input, DEFAULT_EMBEDDING_MODEL);
    }
  }

  /**
   * Embedding content to embed.
   *
   * @param texts
   */
  @JsonInclude(Include.NON_NULL)
  public record EmbeddingTextList(@JsonProperty("texts") List<String> texts) {
    public EmbeddingTextList(List<String> texts) {
      this.texts = texts;
    }
  }

  /**
   * Dashscope 文字向量请求返回值
   *
   * @param output 输出内容
   * @param usage 本次请求所使用的token量
   * @param requestId 本次请求id
   */
  public record DashscopeEmbeddingResponse(
      @JsonProperty("output") EmbeddingResponse output,
      @JsonProperty("usage") TokenUsage usage,
      @JsonProperty("request_id") String requestId) {}

  /**
   * Dashscope请求返回embedding
   *
   * @param embeddings embedding列表
   */
  public record EmbeddingResponse(
      @JsonProperty("embeddings") List<DashscopeEmbedding> embeddings) {}

  /**
   * Dashscope请求返回embedding
   *
   * @param index 输入文本在embedding列表中的索引
   * @param embedding embedding向量
   */
  public record DashscopeEmbedding(
      @JsonProperty("text_index") Integer index,
      @JsonProperty("embedding") List<Double> embedding) {}

  /**
   * Represents a tool the model may call. Currently, only functions are supported as a tool.
   *
   * @param type The type of the tool. Currently, only 'function' is supported.
   * @param function The function definition.
   */
  @JsonInclude(Include.NON_NULL)
  public record FunctionTool(
      @JsonProperty("type") Type type, @JsonProperty("function") Function function) {

    /**
     * Create a tool of type 'function' and the given function definition.
     *
     * @param function function definition.
     */
    @ConstructorBinding
    public FunctionTool(Function function) {
      this(Type.FUNCTION, function);
    }

    /** Create a tool of type 'function' and the given function definition. */
    public enum Type {
      /** Function tool type. */
      @JsonProperty("function")
      FUNCTION
    }

    /**
     * Function definition.
     *
     * @param description A description of what the function does, used by the model to choose when
     *     and how to call the function.
     * @param name The name of the function to be called. Must be a-z, A-Z, 0-9, or contain
     *     underscores and dashes, with a maximum length of 64.
     * @param parameters The parameters the functions accepts, described as a JSON Schema object. To
     *     describe a function that accepts no parameters, provide the value {"type": "object",
     *     "properties": {}}.
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
  }
}
