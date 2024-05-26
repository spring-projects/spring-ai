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
package org.springframework.ai.qwen.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class QWenAiApi {

    public static final String DEFAULT_CHAT_MODEL = ChatModel.QWEN_TURBO.getValue();
    public static final String DEFAULT_EMBEDDING_MODEL = EmbeddingModel.EMBEDDING_V2.getValue();

    private static final Predicate<String> SSE_DONE_PREDICATE = "[DONE]"::equals;

    private final RestClient restClient;

    private WebClient webClient;

    /**
     * Create a new client api with DEFAULT_BASE_URL
     *
     * @param qwenAiApiKey Mistral api Key.
     */
    public QWenAiApi(String qwenAiApiKey) {
        this(ApiUtils.DEFAULT_BASE_URL, qwenAiApiKey);
    }

    /**
     * Create a new client api.
     *
     * @param baseUrl      api base URL.
     * @param qwenAiApiKey Mistral api Key.
     */
    public QWenAiApi(String baseUrl, String qwenAiApiKey) {
        this(baseUrl, qwenAiApiKey, RestClient.builder(), RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER);
    }

    /**
     * Create a new client api.
     *
     * @param baseUrl              api base URL.
     * @param qwenAiApiKey         Mistral api Key.
     * @param restClientBuilder    RestClient builder.
     * @param responseErrorHandler Response error handler.
     */
    public QWenAiApi(String baseUrl, String qwenAiApiKey, RestClient.Builder restClientBuilder, ResponseErrorHandler responseErrorHandler) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).defaultHeaders(ApiUtils.getJsonContentHeaders(qwenAiApiKey)).defaultStatusHandler(responseErrorHandler).build();
        this.webClient = WebClient.builder().baseUrl(baseUrl).defaultHeaders(ApiUtils.getJsonContentHeaders(qwenAiApiKey)).build();
    }


    public enum ChatModel {

        QWEN_TURBO("qwen-turbo"), QWEN_PLUS("qwen-plus"), QWEN_MAX("qwen-max"), QWEN_MAX_LONGCONTEXT("qwen-max-longcontext");

        private final String value;

        ChatModel(String value) {
            this.value = value;
        }

        public String getValue() {
            return this.value;
        }

    }

    /**
     * Usage statistics.
     *
     * @param inputTokens  Number of tokens in the prompt.
     * @param outputTokens Number of tokens in the generated completion. Only applicable for completion requests.
     * @param totalTokens  Total number of tokens used in the request (prompt + completion).
     */
    @JsonInclude(Include.NON_NULL)
    public record Usage(@JsonProperty("input_tokens") Integer inputTokens,
                        @JsonProperty("output_tokens") Integer outputTokens,
                        @JsonProperty("total_tokens") Integer totalTokens) {
    }

    public enum EmbeddingModel {
        @JsonProperty("text-embedding-V1") EMBEDDING_V1("text-embedding-V1"),
        @JsonProperty("text-embedding-v2") EMBEDDING_V2("text-embedding-v2");

        private final String value;

        EmbeddingModel(String value) {
            this.value = value;
        }

        public String getValue() {
            return this.value;
        }
    }

    /**
     * Represents an embedding vector returned by embedding endpoint.
     *
     * @param index     The index of the embedding in the list of embeddings.
     * @param embedding The embedding vector, which is a list of floats. The length of
     *                  vector depends on the model.
     */
    @JsonInclude(Include.NON_NULL)
    public record Embedding(@JsonProperty("text_index") Integer index,
                            @JsonProperty("embedding") List<Double> embedding) {
    }

    /**
     * Creates an embedding vector representing the input text.
     *
     * @param encodingFormat The format to return the embeddings in. Can be either float
     *                       or base64.
     * @param input          Input text to embed, encoded as a string or array of tokens
     */
    @JsonInclude(Include.NON_NULL)
    public record EmbeddingRequest(@JsonProperty("input") EmbeddingReqInput input, @JsonProperty("model") String model,
                                   @JsonProperty("parameters") EmbeddingReqParameters encodingFormat) {
    }

    public record EmbeddingReqInput(@JsonProperty("texts") List<String> texts) {
    }

    public record EmbeddingReqParameters(@JsonProperty("text_type") String textType) {
    }

    /**
     * List of multiple embedding responses.
     *
     * @param output    本次请求的算法输出内容是一个由结构组成的数组，每一个数组中包含一个对应的输入text的算法输出内容。
     * @param usage     本次请求输入内容的token数目，算法的计量是根据用户输入字符串被模型tokenizer解析之后对应的token数目来进行。
     * @param requestId 本次请求的系统唯一码。
     */
    @JsonInclude(Include.NON_NULL)
    public record EmbeddingList(@JsonProperty("output") EmbeddingResponseOutput output,
                                @JsonProperty("usage") Usage usage, @JsonProperty("request_id") String requestId) {
    }

    public record EmbeddingResponseOutput(@JsonProperty("embeddings") List<Embedding> embeddings) {
    }

    /**
     * Creates an embedding vector representing the input text or token array.
     *
     * @param embeddingRequest The embedding request.
     * @param <T>              Type of the entity in the data list. Can be a {@link String} or
     *                         {@link List} of tokens (e.g. Integers). For embedding multiple inputs in a single
     *                         request, You can pass a {@link List} of {@link String} or {@link List} of
     *                         {@link List} of tokens. For example:
     *
     *                         <pre>{@code List.of("text1", "text2", "text3") or List.of(List.of(1, 2, 3), List.of(3, 4, 5))} </pre>
     * @return Returns list of {@link Embedding} wrapped in {@link EmbeddingList}.
     */
    public <T> ResponseEntity<EmbeddingList> embeddings(EmbeddingRequest embeddingRequest) {
        Assert.notNull(embeddingRequest, "The request body can not be null.");
        return this.restClient.post().uri("/api/v1/services/embeddings/text-embedding/text-embedding").body(embeddingRequest).retrieve().toEntity(new ParameterizedTypeReference<>() {
        });
    }

}
