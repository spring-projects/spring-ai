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

package org.springframework.ai.jina.api;

import java.util.List;
import java.util.function.Consumer;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;

/**
 * Jina AI Reranker API client.
 *
 * @author Wongi Kim
 * @since 2.0.0
 */
public class JinaScoringApi {

	/** Default base URL. */
	private static final String DEFAULT_BASE_URL = "https://api.jina.ai";

	/** Rest client. */
	private final RestClient restClient;

	/**
	 * Create a new API client.
	 * @param baseUrl base URL
	 * @param apiKey API key
	 * @param restClientBuilder builder
	 * @param responseErrorHandler error handler
	 */
	public JinaScoringApi(final String baseUrl, final String apiKey, final RestClient.Builder restClientBuilder,
			final ResponseErrorHandler responseErrorHandler) {

		Consumer<HttpHeaders> jsonHeaders = headers -> {
			headers.setBearerAuth(apiKey);
			headers.setContentType(MediaType.APPLICATION_JSON);
		};

		this.restClient = restClientBuilder.clone()
			.baseUrl(baseUrl)
			.defaultHeaders(jsonHeaders)
			.defaultStatusHandler(responseErrorHandler)
			.build();
	}

	/**
	 * Rerank documents.
	 * @param rerankRequest request
	 * @return response
	 */
	public ResponseEntity<RerankResponse> rerank(final RerankRequest rerankRequest) {
		Assert.notNull(rerankRequest, "Rerank request cannot be null.");

		return this.restClient.post().uri("/v1/rerank").body(rerankRequest).retrieve().toEntity(RerankResponse.class);
	}

	/**
	 * Create a builder.
	 * @return builder
	 */
	public static Builder builder() {
		return new Builder();
	}

	/** Builder for JinaScoringApi. */
	public static final class Builder {

		/** Base URL. */
		private String baseUrlValue = DEFAULT_BASE_URL;

		/** API key. */
		private @Nullable String apiKeyValue;

		/** Rest client builder. */
		private RestClient.Builder restClientBuilderValue = RestClient.builder();

		/** Error handler. */
		private ResponseErrorHandler errorHandlerValue = RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER;

		/**
		 * Set base URL.
		 * @param baseUrl URL
		 * @return this
		 */
		public Builder baseUrl(final String baseUrl) {
			this.baseUrlValue = baseUrl;
			return this;
		}

		/**
		 * Set API key.
		 * @param apiKey key
		 * @return this
		 */
		public Builder apiKey(final String apiKey) {
			this.apiKeyValue = apiKey;
			return this;
		}

		/**
		 * Set rest client builder.
		 * @param builder builder
		 * @return this
		 */
		public Builder restClientBuilder(final RestClient.Builder builder) {
			this.restClientBuilderValue = builder;
			return this;
		}

		/**
		 * Set error handler.
		 * @param handler handler
		 * @return this
		 */
		public Builder responseErrorHandler(final ResponseErrorHandler handler) {
			this.errorHandlerValue = handler;
			return this;
		}

		/**
		 * Build client.
		 * @return client
		 */
		public JinaScoringApi build() {
			Assert.state(this.apiKeyValue != null, "API key null");
			return new JinaScoringApi(this.baseUrlValue, this.apiKeyValue, this.restClientBuilderValue,
					this.errorHandlerValue);
		}

	}

	/** Jina AI Reranker models. */
	public enum Model {

		/** V2 Base Multilingual. */
		JINA_RERANKER_V2_BASE_MULTILINGUAL("jina-reranker-v2-base-multilingual"),
		/** V1 Tiny EN. */
		JINA_RERANKER_V1_TINY_EN("jina-reranker-v1-tiny-en"),
		/** V1 Turbo EN. */
		JINA_RERANKER_V1_TURBO_EN("jina-reranker-v1-turbo-en"),
		/** V1 Base EN. */
		JINA_RERANKER_V1_BASE_EN("jina-reranker-v1-base-en"),
		/** ColBERT V1 EN. */
		JINA_COLBERT_V1_EN("jina-colbert-v1-en"),
		/** ColBERT V2. */
		JINA_COLBERT_V2("jina-colbert-v2"),
		/** M0. */
		JINA_RERANKER_M0("jina-reranker-m0"),
		/** V3. */
		JINA_RERANKER_V3("jina-reranker-v3");

		/** Model value. */
		private final String modelValue;

		Model(final String modelValue) {
			this.modelValue = modelValue;
		}

		/**
		 * Get value.
		 * @return model name
		 */
		public String getValue() {
			return this.modelValue;
		}

	}

	/**
	 * Rerank request.
	 *
	 * @param model model name
	 * @param query query string
	 * @param documents list of doc texts
	 * @param topN top N results
	 * @param returnDocs return docs flag
	 * @param truncation truncation flag
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record RerankRequest(@JsonProperty("model") String model, @JsonProperty("query") String query,
			@JsonProperty("documents") List<String> documents, @JsonProperty("top_n") @Nullable Integer topN,
			@JsonProperty("return_documents") @Nullable Boolean returnDocs,
			@JsonProperty("truncation") @Nullable Boolean truncation) {

		/**
		 * Initial constructor.
		 * @param modelName model name
		 * @param queryText query text
		 * @param docList list of documents
		 */
		public RerankRequest(final String modelName, final String queryText, final List<String> docList) {
			this(modelName, queryText, docList, null, null, null);
		}
	}

	/**
	 * Rerank response.
	 *
	 * @param model model used
	 * @param results ranked results
	 * @param usage token usage
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record RerankResponse(@JsonProperty("model") String model,
			@JsonProperty("results") List<RerankResult> results, @JsonProperty("usage") @Nullable Usage usage) {
	}

	/**
	 * Rerank result.
	 *
	 * @param index doc index
	 * @param relevanceScore score
	 * @param document doc content
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record RerankResult(@JsonProperty("index") int index, @JsonProperty("relevance_score") double relevanceScore,
			@JsonProperty("document") @Nullable Document document) {
	}

	/**
	 * Document wrapper.
	 *
	 * @param text document text
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Document(@JsonProperty("text") String text) {

		/**
		 * Create from string.
		 * @param text doc text
		 * @return document
		 */
		@com.fasterxml.jackson.annotation.JsonCreator(
				mode = com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING)
		public static Document fromString(final String text) {
			return new Document(text);
		}
	}

	/**
	 * Token usage.
	 *
	 * @param totalTokens tokens
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Usage(@JsonProperty("total_tokens") int totalTokens) {
	}

}
