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

package org.springframework.ai.bedrock.cohere.api;

import java.time.Duration;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;

import org.springframework.ai.bedrock.api.AbstractBedrockApi;
import org.springframework.ai.bedrock.cohere.api.CohereEmbeddingBedrockApi.CohereEmbeddingRequest;
import org.springframework.ai.bedrock.cohere.api.CohereEmbeddingBedrockApi.CohereEmbeddingResponse;

/**
 * Cohere Embedding API. <a href=
 * "https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-cohere.html#model-parameters-embed">AWS
 * Bedrock Cohere Embedding API</a> Based on the
 * <a href="https://docs.cohere.com/reference/embed">Cohere Embedding API</a>
 *
 * @author Christian Tzolov
 * @author Wei Jiang
 * @since 0.8.0
 */
public class CohereEmbeddingBedrockApi
		extends AbstractBedrockApi<CohereEmbeddingRequest, CohereEmbeddingResponse, CohereEmbeddingResponse> {

	/**
	 * Create a new CohereEmbeddingBedrockApi instance using the default credentials
	 * provider chain, the default object mapper, default temperature and topP values.
	 * @param modelId The model id to use. See the {@link CohereEmbeddingModel} for the
	 * supported models.
	 * @param region The AWS region to use.
	 */
	public CohereEmbeddingBedrockApi(String modelId, String region) {
		super(modelId, region);
	}

	/**
	 * Create a new CohereEmbeddingBedrockApi instance using the provided credentials
	 * provider, region and object mapper.
	 * @param modelId The model id to use. See the {@link CohereEmbeddingModel} for the
	 * supported models.
	 * @param credentialsProvider The credentials provider to connect to AWS.
	 * @param region The AWS region to use.
	 * @param objectMapper The object mapper to use for JSON serialization and
	 * deserialization.
	 */
	public CohereEmbeddingBedrockApi(String modelId, AwsCredentialsProvider credentialsProvider, String region,
			ObjectMapper objectMapper) {
		super(modelId, credentialsProvider, region, objectMapper);
	}

	/**
	 * Create a new CohereEmbeddingBedrockApi instance using the default credentials
	 * provider chain, the default object mapper, default temperature and topP values.
	 * @param modelId The model id to use. See the {@link CohereEmbeddingModel} for the
	 * supported models.
	 * @param region The AWS region to use.
	 * @param timeout The timeout to use.
	 */
	public CohereEmbeddingBedrockApi(String modelId, String region, Duration timeout) {
		super(modelId, region, timeout);
	}

	/**
	 * Create a new CohereEmbeddingBedrockApi instance using the provided credentials
	 * provider, region and object mapper.
	 * @param modelId The model id to use. See the {@link CohereEmbeddingModel} for the
	 * supported models.
	 * @param credentialsProvider The credentials provider to connect to AWS.
	 * @param region The AWS region to use.
	 * @param objectMapper The object mapper to use for JSON serialization and
	 * deserialization.
	 * @param timeout The timeout to use.
	 */
	public CohereEmbeddingBedrockApi(String modelId, AwsCredentialsProvider credentialsProvider, String region,
			ObjectMapper objectMapper, Duration timeout) {
		super(modelId, credentialsProvider, region, objectMapper, timeout);
	}

	/**
	 * Create a new CohereEmbeddingBedrockApi instance using the provided credentials
	 * provider, region and object mapper.
	 * @param modelId The model id to use. See the {@link CohereEmbeddingModel} for the
	 * supported models.
	 * @param credentialsProvider The credentials provider to connect to AWS.
	 * @param region The AWS region to use.
	 * @param objectMapper The object mapper to use for JSON serialization and
	 * deserialization.
	 * @param timeout The timeout to use.
	 */
	public CohereEmbeddingBedrockApi(String modelId, AwsCredentialsProvider credentialsProvider, Region region,
			ObjectMapper objectMapper, Duration timeout) {
		super(modelId, credentialsProvider, region, objectMapper, timeout);
	}

	@Override
	public CohereEmbeddingResponse embedding(CohereEmbeddingRequest request) {
		return this.internalInvocation(request, CohereEmbeddingResponse.class);
	}

	/**
	 * Cohere Embedding model ids.
	 * https://docs.aws.amazon.com/bedrock/latest/userguide/model-ids-arns.html
	 */
	public enum CohereEmbeddingModel {

		/**
		 * cohere.embed-multilingual-v3
		 */
		COHERE_EMBED_MULTILINGUAL_V3("cohere.embed-multilingual-v3"),
		/**
		 * cohere.embed-english-v3
		 */
		COHERE_EMBED_ENGLISH_V3("cohere.embed-english-v3");

		private final String id;

		CohereEmbeddingModel(String value) {
			this.id = value;
		}

		/**
		 * @return The model id.
		 */
		public String id() {
			return this.id;
		}

	}

	/**
	 * The Cohere Embed model request.
	 *
	 * @param texts An array of strings for the model to embed. For optimal performance,
	 * we recommend reducing the length of each text to less than 512 tokens. 1 token is
	 * about 4 characters.
	 * @param inputType Prepends special tokens to differentiate each type from one
	 * another. You should not mix different types together, except when mixing types for
	 * search and retrieval. In this case, embed your corpus with the search_document type
	 * and embedded queries with type search_query type.
	 * @param truncate Specifies how the API handles inputs longer than the maximum token
	 * length. If you specify LEFT or RIGHT, the model discards the input until the
	 * remaining input is exactly the maximum input token length for the model.
	 */
	@JsonInclude(Include.NON_NULL)
	public record CohereEmbeddingRequest(@JsonProperty("texts") List<String> texts,
			@JsonProperty("input_type") InputType inputType, @JsonProperty("truncate") Truncate truncate) {

		/**
		 * Cohere Embedding API input types.
		 */
		public enum InputType {

			/**
			 * In search use-cases, use search_document when you encode documents for
			 * embeddings that you store in a vector database.
			 */
			@JsonProperty("search_document")
			SEARCH_DOCUMENT,
			/**
			 * Use search_query when querying your vector DB to find relevant documents.
			 */
			@JsonProperty("search_query")
			SEARCH_QUERY,
			/**
			 * Use classification when using embeddings as an input to a text classifier.
			 */
			@JsonProperty("classification")
			CLASSIFICATION,
			/**
			 * Use clustering to cluster the embeddings.
			 */
			@JsonProperty("clustering")
			CLUSTERING

		}

		/**
		 * Specifies how the API handles inputs longer than the maximum token length.
		 * Passing START will discard the start of the input. END will discard the end of
		 * the input. In both cases, input is discarded until the remaining input is
		 * exactly the maximum input token length for the model.
		 */
		public enum Truncate {

			/**
			 * Returns an error when the input exceeds the maximum input token length.
			 */
			NONE,
			/**
			 * Discards the start of the input.
			 */
			START,
			/**
			 * (default) Discards the end of the input.
			 */
			END

		}
	}

	/**
	 * Cohere Embedding response.
	 *
	 * @param id An identifier for the response.
	 * @param embeddings An array of embeddings, where each embedding is an array of
	 * floats with 1024 elements. The length of the embeddings array will be the same as
	 * the length of the original texts array.
	 * @param texts An array containing the text entries for which embeddings were
	 * returned.
	 * @param responseType The type of the response. The value is always embeddings.
	 * @param amazonBedrockInvocationMetrics Bedrock invocation metrics. Currently bedrock
	 * doesn't return invocationMetrics for the cohere embedding model.
	 */
	@JsonInclude(Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record CohereEmbeddingResponse(@JsonProperty("id") String id,
			@JsonProperty("embeddings") List<float[]> embeddings, @JsonProperty("texts") List<String> texts,
			@JsonProperty("response_type") String responseType,
			// For future use: Currently bedrock doesn't return invocationMetrics for the
			// cohere embedding model.
			@JsonProperty("amazon-bedrock-invocationMetrics") AmazonBedrockInvocationMetrics amazonBedrockInvocationMetrics) {
	}

}
