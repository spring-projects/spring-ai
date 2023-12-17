/*
 * Copyright 2023-2023 the original author or authors.
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
// @formatter:off
package org.springframework.ai.bedrock.titan.api;

import java.io.IOException;
import java.util.Base64;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;

import org.springframework.ai.bedrock.api.AbstractBedrockApi;
import org.springframework.core.io.DefaultResourceLoader;

/**
 * Java client for the Bedrock Titan Embedding model.
 * https://docs.aws.amazon.com/bedrock/latest/userguide/titan-multiemb-models.html
 *
 * @author Christian Tzolov
 * @since 0.8.0
 */
public class TitanEmbeddingBedrockApi extends
		AbstractBedrockApi<TitanEmbeddingBedrockApi.TitanEmbeddingRequest, TitanEmbeddingBedrockApi.TitanEmbeddingResponse, TitanEmbeddingBedrockApi.TitanEmbeddingResponse> {

	/**
	 * Create a new TitanEmbeddingBedrockApi instance using the default credentials provider and default object
	 * mapper.
	 * @param modelId The model id to use. See the {@link TitanEmbeddingModel} for the supported models.
	 * @param region The AWS region to use.
	 */
	public TitanEmbeddingBedrockApi(String modelId, String region) {
		super(modelId, region);
	}

	/**
	 * Create a new TitanEmbeddingBedrockApi instance.
	 *
	 * @param modelId The model id to use. See the {@link TitanEmbeddingModel} for the supported models.
	 * @param credentialsProvider The credentials provider to connect to AWS.
	 * @param region The AWS region to use.
	 * @param objectMapper The object mapper to use for JSON serialization and deserialization.
	 */
	public TitanEmbeddingBedrockApi(String modelId, AwsCredentialsProvider credentialsProvider, String region,
			ObjectMapper objectMapper) {
		super(modelId, credentialsProvider, region, objectMapper);
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

		/**
		 * Shortcut constructor to create a TitanEmbeddingRequest with only the inputText parameter.
		 * @param inputText The text to compute the embedding for.
		 */
		TitanEmbeddingRequest(String inputText) {
			this(inputText, null);
		}
	}

	/**
	 * Titan Embedding response.
	 *
	 * @param embedding The embedding vector.
	 * @param inputTextTokenCount The number of tokens in the input text.
	 * @param message TODO
	 */
	@JsonInclude(Include.NON_NULL)
	public record TitanEmbeddingResponse(
			@JsonProperty("embedding") List<Double> embedding,
			@JsonProperty("inputTextTokenCount") Integer inputTextTokenCount,
			@JsonProperty("message") Object message) {
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
		TITAN_EMBED_TEXT_V1("amazon.titan-embed-text-v1");

		private final String id;

		/**
		 * @return The model id.
		 */
		public String id() {
			return id;
		}

		TitanEmbeddingModel(String value) {
			this.id = value;
		}
	}

	@Override
	public TitanEmbeddingResponse embedding(TitanEmbeddingRequest request) {
		return this.internalInvocation(request, TitanEmbeddingResponse.class);
	}

	/**
	 * TODO: to remove.
	 *
	 * @param args blank.
	 */
	public static void main(String[] args) throws IOException {
		TitanEmbeddingBedrockApi api = new TitanEmbeddingBedrockApi(
				TitanEmbeddingModel.TITAN_EMBED_TEXT_V1.id(),
				Region.US_EAST_1.id());

		TitanEmbeddingRequest request = new TitanEmbeddingRequest("I like to eat apples.");
		TitanEmbeddingResponse response = api.embedding(request);
		System.out.println(response);

		TitanEmbeddingBedrockApi api2 = new TitanEmbeddingBedrockApi(
				TitanEmbeddingModel.TITAN_EMBED_IMAGE_V1.id(),
				Region.US_EAST_1.id());

		byte[] image = new DefaultResourceLoader().getResource("classpath:/spring_framework.png")
				.getContentAsByteArray();
		String imageBase64 = Base64.getEncoder().encodeToString(image);
		System.out.println(imageBase64.length());
		TitanEmbeddingRequest request2 = new TitanEmbeddingRequest(null, imageBase64);
		System.out.println(api2.embedding(request2));

	}

}
// @formatter:on