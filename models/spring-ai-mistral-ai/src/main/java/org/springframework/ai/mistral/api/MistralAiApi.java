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

package org.springframework.ai.mistral.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

/**
 * Implementation of the MistralAI Embedding API:
 * https://docs.mistral.ai/api/#operation/createEmbedding
 *
 * @author Ricken Bazolo
 */
public class MistralAiApi {

	private static final String DEFAULT_BASE_URL = "https://api.mistral.ai";

	public static final String DEFAULT_EMBEDDING_MODEL = "mistral-embed";

	private final RestClient restClient;

	private final ObjectMapper objectMapper;

	/**
	 * Create a new client api with DEFAULT_BASE_URL
	 * @param mistralAiApiKey Mistral api Key.
	 */
	public MistralAiApi(String mistralAiApiKey) {
		this(DEFAULT_BASE_URL, mistralAiApiKey);
	}

	/**
	 * Create a new client api.
	 * @param baseUrl api base URL.
	 * @param mistralAiApiKey Mistral api Key.
	 */
	public MistralAiApi(String baseUrl, String mistralAiApiKey) {
		this(baseUrl, mistralAiApiKey, RestClient.builder());
	}

	/**
	 * Create a new client api.
	 * @param baseUrl api base URL.
	 * @param mistralAiApiKey Mistral api Key.
	 * @param restClientBuilder RestClient builder.
	 */
	public MistralAiApi(String baseUrl, String mistralAiApiKey, RestClient.Builder restClientBuilder) {

		this.objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

		Consumer<HttpHeaders> jsonContentHeaders = headers -> {
			headers.setBearerAuth(mistralAiApiKey);
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
						throw new MistralAiApiClientErrorException(String.format("%s - %s",
								response.getStatusCode().value(),
								MistralAiApi.this.objectMapper.readValue(response.getBody(), ResponseError.class)));
					}
					throw new MistralAiApiException(String.format("%s - %s", response.getStatusCode().value(),
							MistralAiApi.this.objectMapper.readValue(response.getBody(), ResponseError.class)));
				}
			}
		};

		this.restClient = restClientBuilder.baseUrl(baseUrl)
			.defaultHeaders(jsonContentHeaders)
			.defaultStatusHandler(responseErrorHandler)
			.build();
	}

	public static class MistralAiApiException extends RuntimeException {

		public MistralAiApiException(String message) {
			super(message);
		}

	}

	/**
	 * Thrown on 4xx client errors, such as 401 - Incorrect API key provided, 401 - You
	 * must be a member of an organization to use the API, 429 - Rate limit reached for
	 * requests, 429 - You exceeded your current quota , please check your plan and
	 * billing details.
	 */
	public static class MistralAiApiClientErrorException extends RuntimeException {

		public MistralAiApiClientErrorException(String message) {
			super(message);
		}

	}

	/**
	 * API error response.
	 *
	 * @param error Error details.
	 */
	@JsonInclude(Include.NON_NULL)
	public record ResponseError(@JsonProperty("error") Error error) {

		/**
		 * Error details.
		 *
		 * @param message Error message.
		 * @param type Error type.
		 * @param param Error parameter.
		 * @param code Error code.
		 */
		@JsonInclude(Include.NON_NULL)
		public record Error(@JsonProperty("message") String message, @JsonProperty("type") String type,
				@JsonProperty("param") String param, @JsonProperty("code") String code) {
		}
	}

	/**
	 * Usage statistics.
	 *
	 * @param promptTokens Number of tokens in the prompt.
	 * @param totalTokens Total number of tokens used in the request (prompt +
	 * completion).
	 */
	@JsonInclude(Include.NON_NULL)
	public record Usage(@JsonProperty("prompt_tokens") Integer promptTokens,
			@JsonProperty("total_tokens") Integer totalTokens) {
	}

	/**
	 * Represents an embedding vector returned by embedding endpoint.
	 *
	 * @param index The index of the embedding in the list of embeddings.
	 * @param embedding The embedding vector, which is a list of floats. The length of
	 * vector depends on the model.
	 * @param object The object type, which is always 'embedding'.
	 */
	@JsonInclude(Include.NON_NULL)
	public record Embedding(@JsonProperty("index") Integer index, @JsonProperty("embedding") List<Double> embedding,
			@JsonProperty("object") String object) {

		/**
		 * Create an embedding with the given index, embedding and object type set to
		 * 'embedding'.
		 * @param index The index of the embedding in the list of embeddings.
		 * @param embedding The embedding vector, which is a list of floats. The length of
		 * vector depends on the model.
		 */
		public Embedding(Integer index, List<Double> embedding) {
			this(index, embedding, "embedding");
		}
	}

	/**
	 * Creates an embedding vector representing the input text.
	 *
	 * @param input Input text to embed, encoded as a string or array of tokens
	 * @param model ID of the model to use.
	 * @param encodingFormat The format to return the embeddings in. Can be either float
	 * or base64.
	 */
	@JsonInclude(Include.NON_NULL)
	public record EmbeddingRequest<T>(@JsonProperty("input") T input, @JsonProperty("model") String model,
			@JsonProperty("encoding_format") String encodingFormat) {

		/**
		 * Create an embedding request with the given input, model and encoding format set
		 * to float.
		 * @param input Input text to embed.
		 * @param model ID of the model to use.
		 */
		public EmbeddingRequest(T input, String model) {
			this(input, model, "float");
		}

		/**
		 * Create an embedding request with the given input. Encoding format is set to
		 * float and user is null and the model is set to 'mistral-embed'.
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
	public record EmbeddingList<T>(@JsonProperty("object") String object, @JsonProperty("data") List<T> data,
			@JsonProperty("model") String model, @JsonProperty("usage") Usage usage) {
	}

	/**
	 * Creates an embedding vector representing the input text or token array.
	 * @param embeddingRequest The embedding request.
	 * @return Returns list of {@link Embedding} wrapped in {@link EmbeddingList}.
	 * @param <T> Type of the entity in the data list. Can be a {@link String} or
	 * {@link List} of tokens (e.g. Integers). For embedding multiple inputs in a single
	 * request, You can pass a {@link List} of {@link String} or {@link List} of
	 * {@link List} of tokens. For example:
	 *
	 * <pre>{@code List.of("text1", "text2", "text3") or List.of(List.of(1, 2, 3), List.of(3, 4, 5))} </pre>
	 */
	public <T> ResponseEntity<EmbeddingList<Embedding>> embeddings(EmbeddingRequest<T> embeddingRequest) {

		Assert.notNull(embeddingRequest, "The request body can not be null.");

		// Input text to embed, encoded as a string or array of tokens. To embed multiple
		// inputs in a single
		// request, pass an array of strings or array of token arrays.
		Assert.notNull(embeddingRequest.input(), "The input can not be null.");
		Assert.isTrue(embeddingRequest.input() instanceof String || embeddingRequest.input() instanceof List,
				"The input must be either a String, or a List of Strings or List of List of integers.");

		// The input must not an empty string, and any array must be 1024 dimensions or
		// less.
		if (embeddingRequest.input() instanceof List list) {
			Assert.isTrue(!CollectionUtils.isEmpty(list), "The input list can not be empty.");
			Assert.isTrue(list.size() <= 1024, "The list must be 1024 dimensions or less");
			Assert.isTrue(
					list.get(0) instanceof String || list.get(0) instanceof Integer || list.get(0) instanceof List,
					"The input must be either a String, or a List of Strings or list of list of integers.");
		}

		return this.restClient.post()
			.uri("/v1/embeddings")
			.body(embeddingRequest)
			.retrieve()
			.toEntity(new ParameterizedTypeReference<>() {
			});
	}

}
