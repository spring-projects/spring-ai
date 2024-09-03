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
package org.springframework.ai.bedrock.llama.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.publisher.Flux;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;

import org.springframework.ai.bedrock.api.AbstractBedrockApi;
import org.springframework.ai.bedrock.llama.api.LlamaChatBedrockApi.LlamaChatRequest;
import org.springframework.ai.bedrock.llama.api.LlamaChatBedrockApi.LlamaChatResponse;
import org.springframework.ai.model.ChatModelDescription;

import java.time.Duration;

// @formatter:off
/**
 * Java client for the Bedrock Llama chat model.
 * https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-meta.html
 *
 * @author Christian Tzolov
 * @author Wei Jiang
 * @since 0.8.0
 */
public class LlamaChatBedrockApi extends
		AbstractBedrockApi<LlamaChatRequest, LlamaChatResponse, LlamaChatResponse> {

	/**
	 * Create a new LlamaChatBedrockApi instance using the default credentials provider chain, the default object
	 * mapper, default temperature and topP values.
	 *
	 * @param modelId The model id to use. See the {@link LlamaChatModel} for the supported models.
	 * @param region The AWS region to use.
	 */
	public LlamaChatBedrockApi(String modelId, String region) {
		super(modelId, region);
	}

	/**
	 * Create a new LlamaChatBedrockApi instance using the provided credentials provider, region and object mapper.
	 *
	 * @param modelId The model id to use. See the {@link LlamaChatModel} for the supported models.
	 * @param credentialsProvider The credentials provider to connect to AWS.
	 * @param region The AWS region to use.
	 * @param objectMapper The object mapper to use for JSON serialization and deserialization.
	 */
	public LlamaChatBedrockApi(String modelId, AwsCredentialsProvider credentialsProvider, String region,
			ObjectMapper objectMapper) {
		super(modelId, credentialsProvider, region, objectMapper);
	}

	/**
	 * Create a new LlamaChatBedrockApi instance using the default credentials provider chain, the default object
	 * mapper, default temperature and topP values.
	 *
	 * @param modelId The model id to use. See the {@link LlamaChatModel} for the supported models.
	 * @param region The AWS region to use.
	 * @param timeout The timeout to use.
	 */
	public LlamaChatBedrockApi(String modelId, String region, Duration timeout) {
		super(modelId, region, timeout);
	}

	/**
	 * Create a new LlamaChatBedrockApi instance using the provided credentials provider, region and object mapper.
	 *
	 * @param modelId The model id to use. See the {@link LlamaChatModel} for the supported models.
	 * @param credentialsProvider The credentials provider to connect to AWS.
	 * @param region The AWS region to use.
	 * @param objectMapper The object mapper to use for JSON serialization and deserialization.
	 * @param timeout The timeout to use.
	 */
	public LlamaChatBedrockApi(String modelId, AwsCredentialsProvider credentialsProvider, String region,
			ObjectMapper objectMapper, Duration timeout) {
		super(modelId, credentialsProvider, region, objectMapper, timeout);
	}

	/**
	 * Create a new LlamaChatBedrockApi instance using the provided credentials provider, region and object mapper.
	 *
	 * @param modelId The model id to use. See the {@link LlamaChatModel} for the supported models.
	 * @param credentialsProvider The credentials provider to connect to AWS.
	 * @param region The AWS region to use.
	 * @param objectMapper The object mapper to use for JSON serialization and deserialization.
	 * @param timeout The timeout to use.
	 */
	public LlamaChatBedrockApi(String modelId, AwsCredentialsProvider credentialsProvider, Region region,
			ObjectMapper objectMapper, Duration timeout) {
		super(modelId, credentialsProvider, region, objectMapper, timeout);
	}

	/**
	 * LlamaChatRequest encapsulates the request parameters for the Meta Llama chat model.
	 *
	 * @param prompt The prompt to use for the chat.
	 * @param temperature The temperature value controls the randomness of the generated text. Use a lower value to
	 * decrease randomness in the response.
	 * @param topP The topP value controls the diversity of the generated text. Use a lower value to ignore less
	 * probable options. Set to 0 or 1.0 to disable.
	 * @param maxGenLen The maximum length of the generated text.
	 */
	@JsonInclude(Include.NON_NULL)
	public record LlamaChatRequest(
			@JsonProperty("prompt") String prompt,
			@JsonProperty("temperature") Float temperature,
			@JsonProperty("top_p") Float topP,
			@JsonProperty("max_gen_len") Integer maxGenLen) {

			/**
			 * Create a new LlamaChatRequest builder.
			 * @param prompt compulsory prompt parameter.
			 * @return a new LlamaChatRequest builder.
			 */
			public static Builder builder(String prompt) {
				return new Builder(prompt);
			}

			public static class Builder {
				private String prompt;
				private Float temperature;
				private Float topP;
				private Integer maxGenLen;

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

				public Builder withMaxGenLen(Integer maxGenLen) {
					this.maxGenLen = maxGenLen;
					return this;
				}

				public LlamaChatRequest build() {
					return new LlamaChatRequest(
							prompt,
							temperature,
							topP,
							maxGenLen
					);
				}
			}
	}

	/**
	 * LlamaChatResponse encapsulates the response parameters for the Meta Llama chat model.
	 *
	 * @param generation The generated text.
	 * @param promptTokenCount The number of tokens in the prompt.
	 * @param generationTokenCount The number of tokens in the response.
	 * @param stopReason The reason why the response stopped generating text. Possible values are: (1) stop – The model
	 * has finished generating text for the input prompt. (2) length – The length of the tokens for the generated text
	 * exceeds the value of max_gen_len in the call. The response is truncated to max_gen_len tokens. Consider
	 * increasing the value of max_gen_len and trying again.
	 */
	@JsonInclude(Include.NON_NULL)
	public record LlamaChatResponse(
			@JsonProperty("generation") String generation,
			@JsonProperty("prompt_token_count") Integer promptTokenCount,
			@JsonProperty("generation_token_count") Integer generationTokenCount,
			@JsonProperty("stop_reason") StopReason stopReason,
			@JsonProperty("amazon-bedrock-invocationMetrics") AmazonBedrockInvocationMetrics amazonBedrockInvocationMetrics) {

		/**
		 * The reason the response finished being generated.
		 */
		public enum StopReason {
			/**
			 * The model has finished generating text for the input prompt.
			 */
			@JsonProperty("stop") STOP,
			/**
			 * The response was truncated because of the response length you set.
			 */
			@JsonProperty("length") LENGTH
		}
	}

	/**
	 * Llama models version.
	 */
	public enum LlamaChatModel implements ChatModelDescription {

		/**
		 * meta.llama2-13b-chat-v1
		 */
		LLAMA2_13B_CHAT_V1("meta.llama2-13b-chat-v1"),

		/**
		 * meta.llama2-70b-chat-v1
		 */
		LLAMA2_70B_CHAT_V1("meta.llama2-70b-chat-v1"),

		/**
		 * meta.llama3-8b-instruct-v1:0
		 */
		LLAMA3_8B_INSTRUCT_V1("meta.llama3-8b-instruct-v1:0"),

		/**
		 * meta.llama3-70b-instruct-v1:0
		 */
		LLAMA3_70B_INSTRUCT_V1("meta.llama3-70b-instruct-v1:0");

		private final String id;

		/**
		 * @return The model id.
		 */
		public String id() {
			return id;
		}

		LlamaChatModel(String value) {
			this.id = value;
		}

		@Override
		public String getName() {
			return this.id;
		}
	}

	@Override
	public LlamaChatResponse chatCompletion(LlamaChatRequest request) {
		return this.internalInvocation(request, LlamaChatResponse.class);
	}

	@Override
	public Flux<LlamaChatResponse> chatCompletionStream(LlamaChatRequest request) {
		return this.internalInvocationStream(request, LlamaChatResponse.class);
	}
}
// @formatter:on