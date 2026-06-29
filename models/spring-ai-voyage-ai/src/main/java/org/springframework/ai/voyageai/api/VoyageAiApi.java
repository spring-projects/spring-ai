/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.voyageai.api;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;

/**
 * Single-class, Java Client library for the Voyage AI platform. Provides an
 * implementation for the
 * <a href="https://docs.voyageai.com/reference/embeddings-api">Text Embeddings</a> API.
 *
 * @author Spring AI
 * @since 2.0.0
 */
public class VoyageAiApi {

	public static final String PROVIDER_NAME = AiProvider.VOYAGE_AI.value();

	public static final String DEFAULT_BASE_URL = "https://api.voyageai.com";

	private final RestClient restClient;

	/**
	 * Create a new client API.
	 * @param baseUrl API base URL.
	 * @param apiKey Voyage AI api Key.
	 * @param restClientBuilder RestClient builder.
	 * @param responseErrorHandler Response error handler.
	 */
	public VoyageAiApi(String baseUrl, String apiKey, RestClient.Builder restClientBuilder,
			ResponseErrorHandler responseErrorHandler) {

		Consumer<HttpHeaders> defaultHeaders = headers -> {
			headers.setBearerAuth(apiKey);
			headers.setContentType(MediaType.APPLICATION_JSON);
		};

		this.restClient = restClientBuilder.clone()
			.baseUrl(baseUrl)
			.defaultHeaders(defaultHeaders)
			.defaultStatusHandler(responseErrorHandler)
			.build();
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Creates an embedding vector representing the input text or list of texts.
	 * @param embeddingRequest The embedding request.
	 * @return Returns the {@link EmbeddingList} wrapped in a {@link ResponseEntity}.
	 */
	public ResponseEntity<EmbeddingList> embeddings(EmbeddingRequest embeddingRequest) {

		Assert.notNull(embeddingRequest, "The request body can not be null.");
		Assert.notNull(embeddingRequest.input(), "The input can not be null.");
		Assert.isTrue(!CollectionUtils.isEmpty(embeddingRequest.input()), "The input list can not be empty.");
		Assert.isTrue(embeddingRequest.input().size() <= 1000, "The list must be 1000 entries or less.");

		return this.restClient.post()
			.uri("/v1/embeddings")
			.body(embeddingRequest)
			.retrieve()
			.toEntity(EmbeddingList.class);
	}

	/**
	 * Type of the input text, used by Voyage AI to tailor embeddings for retrieval/search
	 * tasks.
	 */
	public enum InputType {

		// @formatter:off
		QUERY("query"),
		DOCUMENT("document");
		// @formatter:on

		private final String value;

		InputType(String value) {
			this.value = value;
		}

		public String getValue() {
			return this.value;
		}

	}

	/**
	 * Creates an embedding vector representing the input text.
	 *
	 * @param input List of input texts to embed.
	 * @param model ID of the model to use.
	 * @param inputType Type of the input text, either {@code query} or {@code document}.
	 * Defaults to {@code null}.
	 * @param outputDimension The number of dimensions for the resulting output
	 * embeddings. Defaults to {@code null} (model default).
	 * @param truncation Whether to truncate the input texts to fit within the context
	 * length. Defaults to {@code null} (provider default of {@code true}).
	 */
	@JsonInclude(Include.NON_NULL)
	public record EmbeddingRequest(
	// @formatter:off
		@JsonProperty("input") List<String> input,
		@JsonProperty("model") String model,
		@JsonProperty("input_type") @Nullable String inputType,
		@JsonProperty("output_dimension") @Nullable Integer outputDimension,
		@JsonProperty("truncation") @Nullable Boolean truncation) {
		 // @formatter:on

		/**
		 * Create an embedding request with the given input and model.
		 * @param input List of input texts to embed.
		 * @param model ID of the model to use.
		 */
		public EmbeddingRequest(List<String> input, String model) {
			this(input, model, null, null, null);
		}

	}

	/**
	 * Represents an embedding vector returned by the embedding endpoint.
	 *
	 * @param index The index of the embedding in the list of embeddings.
	 * @param embedding The embedding vector, which is a list of floats. The length of the
	 * vector depends on the model.
	 * @param object The object type, which is always 'embedding'.
	 */
	@JsonInclude(Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Embedding(
	// @formatter:off
		@JsonProperty("index") Integer index,
		@JsonProperty("embedding") float[] embedding,
		@JsonProperty("object") String object) {
		 // @formatter:on

		/**
		 * Create an embedding with the given index and embedding and the object type set
		 * to 'embedding'.
		 * @param index The index of the embedding in the list of embeddings.
		 * @param embedding The embedding vector.
		 */
		public Embedding(Integer index, float[] embedding) {
			this(index, embedding, "embedding");
		}

		@Override
		public boolean equals(@Nullable Object o) {
			if (this == o) {
				return true;
			}
			if (!(o instanceof Embedding other)) {
				return false;
			}
			return Objects.equals(this.index, other.index) && Arrays.equals(this.embedding, other.embedding)
					&& Objects.equals(this.object, other.object);
		}

		@Override
		public int hashCode() {
			int result = Objects.hash(this.index, this.object);
			result = 31 * result + Arrays.hashCode(this.embedding);
			return result;
		}

		@Override
		public String toString() {
			return "Embedding{" + "index=" + this.index + ", embedding=" + Arrays.toString(this.embedding)
					+ ", object='" + this.object + '\'' + '}';
		}

	}

	/**
	 * List of multiple embedding responses.
	 *
	 * @param object Must have value "list".
	 * @param data List of entities.
	 * @param model ID of the model used.
	 * @param usage Usage statistics for the request.
	 */
	@JsonInclude(Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record EmbeddingList(
	// @formatter:off
		@JsonProperty("object") String object,
		@JsonProperty("data") List<Embedding> data,
		@JsonProperty("model") String model,
		@JsonProperty("usage") Usage usage) {
		 // @formatter:on
	}

	/**
	 * Usage statistics for the embedding request.
	 *
	 * @param totalTokens The total number of tokens used for computing the embeddings.
	 */
	@JsonInclude(Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Usage(
	// @formatter:off
		@JsonProperty("total_tokens") Integer totalTokens) {
		 // @formatter:on
	}

	public static final class Builder {

		private String baseUrl = DEFAULT_BASE_URL;

		private @Nullable String apiKey;

		private RestClient.Builder restClientBuilder = RestClient.builder();

		private ResponseErrorHandler responseErrorHandler = RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER;

		public Builder baseUrl(String baseUrl) {
			Assert.hasText(baseUrl, "baseUrl cannot be null or empty");
			this.baseUrl = baseUrl;
			return this;
		}

		public Builder apiKey(String apiKey) {
			Assert.hasText(apiKey, "apiKey cannot be null or empty");
			this.apiKey = apiKey;
			return this;
		}

		public Builder restClientBuilder(RestClient.Builder restClientBuilder) {
			Assert.notNull(restClientBuilder, "restClientBuilder cannot be null");
			this.restClientBuilder = restClientBuilder;
			return this;
		}

		public Builder responseErrorHandler(ResponseErrorHandler responseErrorHandler) {
			Assert.notNull(responseErrorHandler, "responseErrorHandler cannot be null");
			this.responseErrorHandler = responseErrorHandler;
			return this;
		}

		public VoyageAiApi build() {
			Assert.state(this.apiKey != null, "The API key must not be null");
			return new VoyageAiApi(this.baseUrl, this.apiKey, this.restClientBuilder, this.responseErrorHandler);
		}

	}

}
