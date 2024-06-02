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
package org.springframework.ai.bedrock.mistral.api;

import java.time.Duration;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Flux;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;

import org.springframework.ai.bedrock.api.AbstractBedrockApi;
import org.springframework.ai.bedrock.mistral.api.MistralChatBedrockApi.MistralChatRequest;
import org.springframework.ai.bedrock.mistral.api.MistralChatBedrockApi.MistralChatResponse;
import org.springframework.ai.model.ModelDescription;
import org.springframework.util.Assert;

/**
 * Java client for the Bedrock Mistral chat model.
 * https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-mistral-text-completion.html
 *
 * @author Wei Jiang
 * @since 1.0.0
 */
// @formatter:off
public class MistralChatBedrockApi extends AbstractBedrockApi<MistralChatRequest, MistralChatResponse, MistralChatResponse> {

	/**
	 * Create a new MistralChatBedrockApi instance using the default credentials provider chain, the default object
	 * mapper, default temperature and topP values.
	 *
	 * @param modelId The model id to use. See the {@link MistralChatModel} for the supported models.
	 * @param region The AWS region to use.
	 */
	public MistralChatBedrockApi(String modelId, String region) {
		super(modelId, region);
	}

	/**
	 * Create a new MistralChatBedrockApi instance using the provided credentials provider, region and object mapper.
	 *
	 * @param modelId The model id to use. See the {@link MistralChatModel} for the supported models.
	 * @param credentialsProvider The credentials provider to connect to AWS.
	 * @param region The AWS region to use.
	 * @param objectMapper The object mapper to use for JSON serialization and deserialization.
	 */
	public MistralChatBedrockApi(String modelId, AwsCredentialsProvider credentialsProvider, String region,
			ObjectMapper objectMapper) {
		super(modelId, credentialsProvider, region, objectMapper);
	}

	/**
	 * Create a new MistralChatBedrockApi instance using the default credentials provider chain, the default object
	 * mapper, default temperature and topP values.
	 *
	 * @param modelId The model id to use. See the {@link MistralChatModel} for the supported models.
	 * @param region The AWS region to use.
	 * @param timeout The timeout to use.
	 */
	public MistralChatBedrockApi(String modelId, String region, Duration timeout) {
		super(modelId, region, timeout);
	}

	/**
	 * Create a new MistralChatBedrockApi instance using the provided credentials provider, region and object mapper.
	 *
	 * @param modelId The model id to use. See the {@link MistralChatModel} for the supported models.
	 * @param credentialsProvider The credentials provider to connect to AWS.
	 * @param region The AWS region to use.
	 * @param objectMapper The object mapper to use for JSON serialization and deserialization.
	 * @param timeout The timeout to use.
	 */
	public MistralChatBedrockApi(String modelId, AwsCredentialsProvider credentialsProvider, String region,
			ObjectMapper objectMapper, Duration timeout) {
		super(modelId, credentialsProvider, region, objectMapper, timeout);
	}

	/**
	 * Create a new MistralChatBedrockApi instance using the provided credentials provider, region and object mapper.
	 *
	 * @param modelId The model id to use. See the {@link MistralChatModel} for the supported models.
	 * @param credentialsProvider The credentials provider to connect to AWS.
	 * @param region The AWS region to use.
	 * @param objectMapper The object mapper to use for JSON serialization and deserialization.
	 * @param timeout The timeout to use.
	 */
	public MistralChatBedrockApi(String modelId, AwsCredentialsProvider credentialsProvider, Region region,
			ObjectMapper objectMapper, Duration timeout) {
		super(modelId, credentialsProvider, region, objectMapper, timeout);
	}

	/**
	 * MistralChatRequest encapsulates the request parameters for the Mistral model.
	 *
	 * @param prompt The input prompt to generate the response from.
	 * @param temperature (optional) Use a lower value to decrease randomness in the response.
	 * @param topP (optional) Use a lower value to ignore less probable options. Set to 0 or 1.0 to disable.
	 * @param topK (optional) Specify the number of token choices the model uses to generate the next token.
	 * @param maxTokens (optional) Specify the maximum number of tokens to use in the generated response.
	 * @param stopSequences (optional) Configure up to four sequences that the model recognizes. After a stop sequence,
	 * the model stops generating further tokens. The returned text doesn't contain the stop sequence.
	 */
	@JsonInclude(Include.NON_NULL)
	public record MistralChatRequest(
			@JsonProperty("prompt") String prompt,
			@JsonProperty("temperature") Float temperature,
			@JsonProperty("top_p") Float topP,
			@JsonProperty("top_k") Integer topK,
			@JsonProperty("max_tokens") Integer maxTokens,
			@JsonProperty("stop") List<String> stopSequences) {

		/**
		 * Get MistralChatRequest builder.
		 * @param prompt compulsory request prompt parameter.
		 * @return MistralChatRequest builder.
		 */
		public static Builder builder(String prompt) {
			return new Builder(prompt);
		}

		/**
		 * Builder for the MistralChatRequest.
		 */
		public static class Builder {
			private final String prompt;
			private Float temperature;
			private Float topP;
			private Integer topK;
			private Integer maxTokens;
			private List<String> stopSequences;

			public Builder(String prompt) {
				this.prompt = prompt;
			}

			public Builder withTemperature(Float temperature) {
				this.temperature = temperature;
				return this;
			}

			public Builder withTopP(Float topP) {
				this.topP = topP;
				return this;
			}

			public Builder withTopK(Integer topK) {
				this.topK = topK;
				return this;
			}

			public Builder withMaxTokens(Integer maxTokens) {
				this.maxTokens = maxTokens;
				return this;
			}

			public Builder withStopSequences(List<String> stopSequences) {
				this.stopSequences = stopSequences;
				return this;
			}

			public MistralChatRequest build() {
				return new MistralChatRequest(
						prompt,
						temperature,
						topP,
						topK,
						maxTokens,
						stopSequences
				);
			}
		}
	}

	/**
	 * MistralChatResponse encapsulates the response parameters for the Mistral model.
	 *
	 * @param A list of outputs from the model. Each output has the following fields.
	 */
	@JsonInclude(Include.NON_NULL)
	public record MistralChatResponse(
			@JsonProperty("outputs") List<Generation> outputs,
			@JsonProperty("amazon-bedrock-invocationMetrics") AmazonBedrockInvocationMetrics amazonBedrockInvocationMetrics) {

		/**
		 * Generated result along with the likelihoods for tokens requested.
		 *
		 * @param text The text that the model generated.
		 * @param stopReason The reason why the response stopped generating text.
		 */
		public record Generation(
				@JsonProperty("text") String text,
				@JsonProperty("stop_reason") String stopReason) {
		}

	}

	/**
	 * Mistral models version.
	 */
	public enum MistralChatModel implements ModelDescription {

		/**
		 * mistral.mistral-7b-instruct-v0:2
		 */
		MISTRAL_7B_INSTRUCT("mistral.mistral-7b-instruct-v0:2"),

		/**
		 * mistral.mixtral-8x7b-instruct-v0:1
		 */
		MISTRAL_8X7B_INSTRUCT("mistral.mixtral-8x7b-instruct-v0:1"),

		/**
		 * mistral.mistral-large-2402-v1:0
		 */
		MISTRAL_LARGE("mistral.mistral-large-2402-v1:0"),

		/**
		 * mistral.mistral-small-2402-v1:0
		 */
		MISTRAL_SMALL("mistral.mistral-small-2402-v1:0");

		private final String id;

		/**
		 * @return The model id.
		 */
		public String id() {
			return id;
		}

		MistralChatModel(String value) {
			this.id = value;
		}

		@Override
		public String getModelName() {
			return this.id;
		}
	}

	@Override
	public MistralChatResponse chatCompletion(MistralChatRequest mistralRequest) {
		Assert.notNull(mistralRequest, "'MistralChatRequest' must not be null");
		return this.internalInvocation(mistralRequest, MistralChatResponse.class);
	}

	@Override
	public Flux<MistralChatResponse> chatCompletionStream(MistralChatRequest mistralRequest) {
		Assert.notNull(mistralRequest, "'MistralChatRequest' must not be null");
		return this.internalInvocationStream(mistralRequest, MistralChatResponse.class);
	}

}
//@formatter:on