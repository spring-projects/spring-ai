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
package org.springframework.ai.bedrock.anthropic.api;

import java.time.Duration;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.publisher.Flux;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;

import org.springframework.ai.bedrock.anthropic.api.AnthropicChatBedrockApi.AnthropicChatRequest;
import org.springframework.ai.bedrock.anthropic.api.AnthropicChatBedrockApi.AnthropicChatResponse;
import org.springframework.ai.bedrock.api.AbstractBedrockApi;
import org.springframework.util.Assert;

/**
 * @author Christian Tzolov
 * @since 0.8.0
 */
// @formatter:off
public class AnthropicChatBedrockApi extends
		AbstractBedrockApi<AnthropicChatRequest, AnthropicChatResponse, AnthropicChatResponse> {

	public static final String PROMPT_TEMPLATE = "\n\nHuman:%s\n\nAssistant:";

	/**
	 * Default version of the Anthropic chat model.
	 */
	public static final String DEFAULT_ANTHROPIC_VERSION = "bedrock-2023-05-31";


	/**
	 * Create a new AnthropicChatBedrockApi instance using the default credentials provider chain, the default object.
	 * @param modelId The model id to use. See the {@link AnthropicChatModel} for the supported models.
	 * @param region The AWS region to use.
	 */
	public AnthropicChatBedrockApi(String modelId, String region) {
		super(modelId, region);
	}

	/**
	 * Create a new AnthropicChatBedrockApi instance using the default credentials provider chain, the default object.
	 * @param modelId The model id to use. See the {@link AnthropicChatModel} for the supported models.
	 * @param region The AWS region to use.
	 * @param timeout The timeout to use.
	 */
	public AnthropicChatBedrockApi(String modelId, String region, Duration timeout) {
		super(modelId, region, timeout);
	}

	/**
	 * Create a new AnthropicChatBedrockApi instance using the provided credentials provider, region and object mapper.
	 *
	 * @param modelId The model id to use. See the {@link AnthropicChatModel} for the supported models.
	 * @param credentialsProvider The credentials provider to connect to AWS.
	 * @param region The AWS region to use.
	 * @param objectMapper The object mapper to use for JSON serialization and deserialization.
	 */
	public AnthropicChatBedrockApi(String modelId, AwsCredentialsProvider credentialsProvider, String region,
			ObjectMapper objectMapper) {
		super(modelId, credentialsProvider, region, objectMapper);
	}

	/**
	 * Create a new AnthropicChatBedrockApi instance using the provided credentials provider, region and object mapper.
	 *
	 * @param modelId The model id to use. See the {@link AnthropicChatModel} for the supported models.
	 * @param credentialsProvider The credentials provider to connect to AWS.
	 * @param region The AWS region to use.
	 * @param objectMapper The object mapper to use for JSON serialization and deserialization.
	 * @param timeout The timeout to use.
	 */
	public AnthropicChatBedrockApi(String modelId, AwsCredentialsProvider credentialsProvider, String region,
			ObjectMapper objectMapper, Duration timeout) {
		super(modelId, credentialsProvider, region, objectMapper, timeout);
	}

	// https://github.com/build-on-aws/amazon-bedrock-java-examples/blob/main/example_code/bedrock-runtime/src/main/java/aws/community/examples/InvokeBedrockStreamingAsync.java

	// https://docs.anthropic.com/claude/reference/complete_post

	// https://docs.aws.amazon.com/bedrock/latest/userguide/br-product-ids.html

	// Anthropic Claude models: https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-claude.html

	/**
	 * AnthropicChatRequest encapsulates the request parameters for the Anthropic chat model.
	 * https://docs.anthropic.com/claude/reference/complete_post
	 *
	 * @param prompt The prompt to use for the chat.
	 * @param temperature (default 0.5) The temperature to use for the chat. You should either alter temperature or
	 * top_p, but not both.
	 * @param maxTokensToSample (default 200) Specify the maximum number of tokens to use in the generated response.
	 * Note that the models may stop before reaching this maximum. This parameter only specifies the absolute maximum
	 * number of tokens to generate. We recommend a limit of 4,000 tokens for optimal performance.
	 * @param topK (default 250) Specify the number of token choices the model uses to generate the next token.
	 * @param topP (default 1) Nucleus sampling to specify the cumulative probability of the next token in range [0,1].
	 * In nucleus sampling, we compute the cumulative distribution over all the options for each subsequent token in
	 * decreasing probability order and cut it off once it reaches a particular probability specified by top_p. You
	 * should either alter temperature or top_p, but not both.
	 * @param stopSequences (defaults to "\n\nHuman:") Configure up to four sequences that the model recognizes. After a
	 * stop sequence, the model stops generating further tokens. The returned text doesn't contain the stop sequence.
	 * @param anthropicVersion The version of the model to use. The default value is bedrock-2023-05-31.
	 */
	@JsonInclude(Include.NON_NULL)
	public record AnthropicChatRequest(
			@JsonProperty("prompt") String prompt,
			@JsonProperty("temperature") Float temperature,
			@JsonProperty("max_tokens_to_sample") Integer maxTokensToSample,
			@JsonProperty("top_k") Integer topK,
			@JsonProperty("top_p") Float topP,
			@JsonProperty("stop_sequences") List<String> stopSequences,
			@JsonProperty("anthropic_version") String anthropicVersion) {

		public static Builder builder(String prompt) {
			return new Builder(prompt);
		}

		public static class Builder {
			private final String prompt;
			private Float temperature;// = 0.7f;
			private Integer maxTokensToSample;// = 500;
			private Integer topK;// = 10;
			private Float topP;
			private List<String> stopSequences;
			private String anthropicVersion;

			private Builder(String prompt) {
				this.prompt = prompt;
			}

			public Builder withTemperature(Float temperature) {
				this.temperature = temperature;
				return this;
			}

			public Builder withMaxTokensToSample(Integer maxTokensToSample) {
				this.maxTokensToSample = maxTokensToSample;
				return this;
			}

			public Builder withTopK(Integer topK) {
				this.topK = topK;
				return this;
			}

			public Builder withTopP(Float tpoP) {
				this.topP = tpoP;
				return this;
			}

			public Builder withStopSequences(List<String> stopSequences) {
				this.stopSequences = stopSequences;
				return this;
			}

			public Builder withAnthropicVersion(String anthropicVersion) {
				this.anthropicVersion = anthropicVersion;
				return this;
			}

			public AnthropicChatRequest build() {
				return new AnthropicChatRequest(
						prompt,
						temperature,
						maxTokensToSample,
						topK,
						topP,
						stopSequences,
						anthropicVersion
				);
			}
		}
	}

	/**
	 * AnthropicChatResponse encapsulates the response parameters for the Anthropic chat model.
	 *
	 * @param completion The generated text.
	 * @param stopReason The reason the model stopped generating text.
	 * @param stop The stop sequence that caused the model to stop generating text.
	 * @param amazonBedrockInvocationMetrics Metrics about the model invocation.
	 */
	@JsonInclude(Include.NON_NULL)
	public record AnthropicChatResponse(
			@JsonProperty("completion") String completion,
			@JsonProperty("stop_reason") String stopReason,
			@JsonProperty("stop") String stop,
			@JsonProperty("amazon-bedrock-invocationMetrics") AmazonBedrockInvocationMetrics amazonBedrockInvocationMetrics) {
	}

	/**
	 * Anthropic models version.
	 */
	public enum AnthropicChatModel {
		/**
		 * anthropic.claude-instant-v1
		 */
		CLAUDE_INSTANT_V1("anthropic.claude-instant-v1"),
		/**
		 * anthropic.claude-v2
		 */
		CLAUDE_V2("anthropic.claude-v2"),
		/**
		 * anthropic.claude-v2:1
		 */
		CLAUDE_V21("anthropic.claude-v2:1");

		private final String id;

		/**
		 * @return The model id.
		 */
		public String id() {
			return id;
		}

		AnthropicChatModel(String value) {
			this.id = value;
		}
	}

	@Override
	public AnthropicChatResponse chatCompletion(AnthropicChatRequest anthropicRequest) {
		Assert.notNull(anthropicRequest, "'anthropicRequest' must not be null");
		return this.internalInvocation(anthropicRequest, AnthropicChatResponse.class);
	}

	@Override
	public Flux<AnthropicChatResponse> chatCompletionStream(AnthropicChatRequest anthropicRequest) {
		Assert.notNull(anthropicRequest, "'anthropicRequest' must not be null");
		return this.internalInvocationStream(anthropicRequest, AnthropicChatResponse.class);
	}

}
// @formatter:on