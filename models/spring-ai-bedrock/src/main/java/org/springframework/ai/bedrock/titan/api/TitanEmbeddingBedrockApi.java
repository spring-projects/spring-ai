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

package org.springframework.ai.bedrock.titan.api;

import java.time.Duration;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;

import org.springframework.ai.bedrock.api.AbstractBedrockApi;
import org.springframework.ai.bedrock.titan.api.TitanEmbeddingBedrockApi.TitanEmbeddingRequest;
import org.springframework.ai.bedrock.titan.api.TitanEmbeddingBedrockApi.TitanEmbeddingResponse;
import org.springframework.util.Assert;

/**
 * Java client for the Bedrock Titan Embedding model.
 * https://docs.aws.amazon.com/bedrock/latest/userguide/titan-multiemb-models.html
 *
 * @author Christian Tzolov
 * @author Wei Jiang
 * @since 0.8.0
 */
// @formatter:off
public class TitanEmbeddingBedrockApi extends
		AbstractBedrockApi<TitanEmbeddingRequest, TitanEmbeddingResponse, TitanEmbeddingResponse> {

	/**
	 * Create a new TitanEmbeddingBedrockApi instance using the default credentials provider and default object
	 * mapper.
	 * @param modelId The model id to use. See the {@link TitanEmbeddingModel} for the supported models.
	 * @param region The AWS region to use.
	 * @param timeout The timeout to use.
	 */
	public TitanEmbeddingBedrockApi(String modelId, String region, Duration timeout) {
		super(modelId, region, timeout);
	}

	/**
	 * Create a new TitanEmbeddingBedrockApi instance.
	 *
	 * @param modelId The model id to use. See the {@link TitanEmbeddingModel} for the supported models.
	 * @param credentialsProvider The credentials provider to connect to AWS.
	 * @param region The AWS region to use.
	 * @param objectMapper The object mapper to use for JSON serialization and deserialization.
	 * @param timeout The timeout to use.
	 */
	public TitanEmbeddingBedrockApi(String modelId, AwsCredentialsProvider credentialsProvider, String region,
			ObjectMapper objectMapper, Duration timeout) {
		super(modelId, credentialsProvider, region, objectMapper, timeout);
	}

	/**
	 * Create a new TitanEmbeddingBedrockApi instance.
	 *
	 * @param modelId The model id to use. See the {@link TitanEmbeddingModel} for the supported models.
	 * @param credentialsProvider The credentials provider to connect to AWS.
	 * @param region The AWS region to use.
	 * @param objectMapper The object mapper to use for JSON serialization and deserialization.
	 * @param timeout The timeout to use.
	 */
	public TitanEmbeddingBedrockApi(String modelId, AwsCredentialsProvider credentialsProvider, Region region,
			ObjectMapper objectMapper, Duration timeout) {
		super(modelId, credentialsProvider, region, objectMapper, timeout);
	}

	@Override
	public TitanEmbeddingResponse embedding(TitanEmbeddingRequest request) {
		return this.internalInvocation(request, TitanEmbeddingResponse.class);
	}

	/**
	 * Titan Embedding model ids.
	 */
	public enum TitanEmbeddingModel {
		/**
		 * amazon.titan-embed-image-v1
		 */
		TITAN_EMBED_IMAGE_V1("amazon.titan-embed-image-v1"),
		/**
		 * amazon.titan-embed-text-v1
		 */
		TITAN_EMBED_TEXT_V1("amazon.titan-embed-text-v1"),
		/**
		 * amazon.titan-embed-text-v2
		 */
		TITAN_EMBED_TEXT_V2("amazon.titan-embed-text-v2:0");

		private final String id;

		TitanEmbeddingModel(String value) {
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
	 * Titan Embedding request parameters.
	 *
	 * @param inputText The text to compute the embedding for.
	 * @param inputImage The image to compute the embedding for. Only applicable for the 'Titan Multimodal Embeddings
	 * G1' model.
	 */
	@JsonInclude(Include.NON_NULL)
	public record TitanEmbeddingRequest(
			@JsonProperty("inputText") String inputText,
			@JsonProperty("inputImage") String inputImage) {


		public static Builder builder() {
			return new Builder();
		}

		/**
		 * TitanEmbeddingRequest builder.
		 */
		public static class Builder {

			private String inputText;
			private String inputImage;

			public Builder inputText(String inputText) {
				this.inputText = inputText;
				return this;
			}

			public Builder inputImage(String inputImage) {
				this.inputImage = inputImage;
				return this;
			}

			public TitanEmbeddingRequest build() {
				Assert.isTrue(this.inputText != null || this.inputImage != null,
						"At least one of the inputText or inputImage parameters must be provided!");
				Assert.isTrue(!(this.inputText != null && this.inputImage != null),
						"Only one of the inputText or inputImage parameters must be provided!");

				return new TitanEmbeddingRequest(this.inputText, this.inputImage);
			}
		}

	}

	/**
	 * Titan Embedding response.
	 *
	 * @param embedding The embedding vector.
	 * @param inputTextTokenCount The number of tokens in the input text.
	 * @param embeddingsByType The embeddings by type.
	 * @param message No idea what this is.
	 */
	@JsonInclude(Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record TitanEmbeddingResponse(
			@JsonProperty("embedding") float[] embedding,
			@JsonProperty("inputTextTokenCount") Integer inputTextTokenCount,
			@JsonProperty("successCount") Integer successCount,
			@JsonProperty("failureCount") Integer failureCount,
			@JsonProperty("embeddingsByType") Map<String, Object> embeddingsByType,
			@JsonProperty("results") Object results,
			@JsonProperty("message") Object message) {


	}
}
// @formatter:on
