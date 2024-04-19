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
// @formatter:off
package org.springframework.ai.bedrock.cohere.api;

import java.time.Duration;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.publisher.Flux;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;

import org.springframework.ai.bedrock.api.AbstractBedrockApi;
import org.springframework.ai.bedrock.cohere.api.CohereChatBedrockApi.CohereChatRequest;
import org.springframework.ai.bedrock.cohere.api.CohereChatBedrockApi.CohereChatResponse;
import org.springframework.util.Assert;

/**
 * Java client for the Bedrock Cohere chat model.
 * https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-cohere.html
 *
 * @author Christian Tzolov
 * @since 0.8.0
 */
public class CohereChatBedrockApi extends
		AbstractBedrockApi<CohereChatRequest, CohereChatResponse, CohereChatResponse.Generation> {

	/**
	 * Create a new CohereChatBedrockApi instance using the default credentials provider chain, the default object
	 * mapper, default temperature and topP values.
	 *
	 * @param modelId The model id to use. See the {@link CohereChatModel} for the supported models.
	 * @param region The AWS region to use.
	 */
	public CohereChatBedrockApi(String modelId, String region) {
		super(modelId, region);
	}

	/**
	 * Create a new CohereChatBedrockApi instance using the provided credentials provider, region and object mapper.
	 *
	 * @param modelId The model id to use. See the {@link CohereChatModel} for the supported models.
	 * @param credentialsProvider The credentials provider to connect to AWS.
	 * @param region The AWS region to use.
	 * @param objectMapper The object mapper to use for JSON serialization and deserialization.
	 */
	public CohereChatBedrockApi(String modelId, AwsCredentialsProvider credentialsProvider, String region,
			ObjectMapper objectMapper) {
		super(modelId, credentialsProvider, region, objectMapper);
	}

	/**
	 * Create a new CohereChatBedrockApi instance using the default credentials provider chain, the default object
	 * mapper, default temperature and topP values.
	 *
	 * @param modelId The model id to use. See the {@link CohereChatModel} for the supported models.
	 * @param region The AWS region to use.
	 * @param timeout The timeout to use.
	 */
	public CohereChatBedrockApi(String modelId, String region, Duration timeout) {
		super(modelId, region, timeout);
	}

	/**
	 * Create a new CohereChatBedrockApi instance using the provided credentials provider, region and object mapper.
	 *
	 * @param modelId The model id to use. See the {@link CohereChatModel} for the supported models.
	 * @param credentialsProvider The credentials provider to connect to AWS.
	 * @param region The AWS region to use.
	 * @param objectMapper The object mapper to use for JSON serialization and deserialization.
	 * @param timeout The timeout to use.
	 */
	public CohereChatBedrockApi(String modelId, AwsCredentialsProvider credentialsProvider, String region,
			ObjectMapper objectMapper, Duration timeout) {
		super(modelId, credentialsProvider, region, objectMapper, timeout);
	}

	/**
	 * CohereChatRequest encapsulates the request parameters for the Cohere command model.
	 *
	 * @param prompt The input prompt to generate the response from.
	 * @param temperature (optional) Use a lower value to decrease randomness in the response.
	 * @param topP (optional) Use a lower value to ignore less probable options. Set to 0 or 1.0 to disable.
	 * @param topK (optional) Specify the number of token choices the model uses to generate the next token.
	 * @param maxTokens (optional) Specify the maximum number of tokens to use in the generated response.
	 * @param stopSequences (optional) Configure up to four sequences that the model recognizes. After a stop sequence,
	 * the model stops generating further tokens. The returned text doesn't contain the stop sequence.
	 * @param returnLikelihoods (optional) Specify how and if the token likelihoods are returned with the response.
	 * @param stream (optional) Specify true to return the response piece-by-piece in real-time and false to return the
	 * complete response after the process finishes.
	 * @param numGenerations (optional) The maximum number of generations that the model should return.
	 * @param logitBias (optional) prevents the model from generating unwanted tokens or incentivize the model to
	 * include desired tokens. The format is {token_id: bias} where bias is a float between -10 and 10. Tokens can be
	 * obtained from text using any tokenization service, such as Cohere’s Tokenize endpoint.
	 * @param truncate (optional) Specifies how the API handles inputs longer than the maximum token length.
	 */
	@JsonInclude(Include.NON_NULL)
	public record CohereChatRequest(
			@JsonProperty("prompt") String prompt,
			@JsonProperty("temperature") Float temperature,
			@JsonProperty("p") Float topP,
			@JsonProperty("k") Integer topK,
			@JsonProperty("max_tokens") Integer maxTokens,
			@JsonProperty("stop_sequences") List<String> stopSequences,
			@JsonProperty("return_likelihoods") ReturnLikelihoods returnLikelihoods,
			@JsonProperty("stream") boolean stream,
			@JsonProperty("num_generations") Integer numGenerations,
			@JsonProperty("logit_bias") LogitBias logitBias,
			@JsonProperty("truncate") Truncate truncate) {

		/**
		 * Prevents the model from generating unwanted tokens or incentivize the model to include desired tokens.
		 *
		 * @param token The token likelihoods.
		 * @param bias A float between -10 and 10.
		 */
		@JsonInclude(Include.NON_NULL)
		public record LogitBias(
				@JsonProperty("token") String token,
				@JsonProperty("bias") Float bias) {
		}

		/**
		 * (optional) Specify how and if the token likelihoods are returned with the response.
		 */
		public enum ReturnLikelihoods {
			/**
			 * Only return likelihoods for generated tokens.
			 */
			GENERATION,
			/**
			 * Return likelihoods for all tokens.
			 */
			ALL,
			/**
			 * (Default) Don't return any likelihoods.
			 */
			NONE
		}

		/**
		 * Specifies how the API handles inputs longer than the maximum token length. If you specify START or END, the
		 * model discards the input until the remaining input is exactly the maximum input token length for the model.
		 */
		public enum Truncate {
			/**
			 * Returns an error when the input exceeds the maximum input token length.
			 */
			NONE,
			/**
			 * Discard the start of the input.
			 */
			START,
			/**
			 * (Default) Discards the end of the input.
			 */
			END
		}

		/**
		 * Get CohereChatRequest builder.
		 * @param prompt compulsory request prompt parameter.
		 * @return CohereChatRequest builder.
		 */
		public static Builder builder(String prompt) {
			return new Builder(prompt);
		}

		/**
		 * Builder for the CohereChatRequest.
		 */
		public static class Builder {
			private final String prompt;
			private Float temperature;
			private Float topP;
			private Integer topK;
			private Integer maxTokens;
			private List<String> stopSequences;
			private ReturnLikelihoods returnLikelihoods;
			private boolean stream;
			private Integer numGenerations;
			private LogitBias logitBias;
			private Truncate truncate;

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

			public Builder withReturnLikelihoods(ReturnLikelihoods returnLikelihoods) {
				this.returnLikelihoods = returnLikelihoods;
				return this;
			}

			public Builder withStream(boolean stream) {
				this.stream = stream;
				return this;
			}

			public Builder withNumGenerations(Integer numGenerations) {
				this.numGenerations = numGenerations;
				return this;
			}

			public Builder withLogitBias(LogitBias logitBias) {
				this.logitBias = logitBias;
				return this;
			}

			public Builder withTruncate(Truncate truncate) {
				this.truncate = truncate;
				return this;
			}

			public CohereChatRequest build() {
				return new CohereChatRequest(
						prompt,
						temperature,
						topP,
						topK,
						maxTokens,
						stopSequences,
						returnLikelihoods,
						stream,
						numGenerations,
						logitBias,
						truncate
				);
			}
		}
	}

	/**
	 * CohereChatResponse encapsulates the response parameters for the Cohere command model.
	 *
	 * @param id An identifier for the request (always returned).
	 * @param prompt The prompt from the input request. (Always returned).
	 * @param generations A list of generated results along with the likelihoods for tokens requested. (Always
	 * returned).
	 */
	@JsonInclude(Include.NON_NULL)
	public record CohereChatResponse(
			@JsonProperty("id") String id,
			@JsonProperty("prompt") String prompt,
			@JsonProperty("generations") List<Generation> generations) {

		/**
		 * Generated result along with the likelihoods for tokens requested.
		 *
		 * @param id An identifier for the generation. (Always returned).
		 * @param likelihood The likelihood of the output. The value is the average of the token likelihoods in
		 * token_likelihoods. Returned if you specify the return_likelihoods input parameter.
		 * @param tokenLikelihoods An array of per token likelihoods. Returned if you specify the return_likelihoods
		 * input parameter.
		 * @param finishReason states the reason why the model finished generating tokens.
		 * @param isFinished A boolean field used only when stream is true, signifying whether or not there are
		 * additional tokens that will be generated as part of the streaming response. (Not always returned).
		 * @param text The generated text.
		 * @param index In a streaming response, use to determine which generation a given token belongs to. When only
		 * one response is streamed, all tokens belong to the same generation and index is not returned. index therefore
		 * is only returned in a streaming request with a value for num_generations that is larger than one.
		 * @param amazonBedrockInvocationMetrics Encapsulates the metrics about the model invocation.
		 */
		@JsonInclude(Include.NON_NULL)
		public record Generation(
				@JsonProperty("id") String id,
				@JsonProperty("likelihood") Float likelihood,
				@JsonProperty("token_likelihoods") List<TokenLikelihood> tokenLikelihoods,
				@JsonProperty("finish_reason") FinishReason finishReason,
				@JsonProperty("is_finished") Boolean isFinished,
				@JsonProperty("text") String text,
				@JsonProperty("index") Integer index,
				@JsonProperty("amazon-bedrock-invocationMetrics") AmazonBedrockInvocationMetrics amazonBedrockInvocationMetrics) {

			/**
			 * @param token The token.
			 * @param likelihood The likelihood of the token.
			 */
			@JsonInclude(Include.NON_NULL)
			public record TokenLikelihood(
					@JsonProperty("token") String token,
					@JsonProperty("likelihood") Float likelihood) {
			}

			/**
			 * The reason the response finished being generated.
			 */
			public enum FinishReason {
				/**
				 * The model sent back a finished reply.
				 */
				COMPLETE,
				/**
				 * The reply was cut off because the model reached the maximum number of tokens for its context length.
				 */
				MAX_TOKENS,
				/**
				 * Something went wrong when generating the reply.
				 */
				ERROR,
				/**
				 * the model generated a reply that was deemed toxic. finish_reason is returned only when
				 * is_finished=true. (Not always returned).
				 */
				ERROR_TOXIC
			}
		}
	}

	/**
	 * Cohere models version.
	 */
	public enum CohereChatModel {

		/**
		 * cohere.command-light-text-v14
		 */
		COHERE_COMMAND_LIGHT_V14("cohere.command-light-text-v14"),

		/**
		 * cohere.command-text-v14
		 */
		COHERE_COMMAND_V14("cohere.command-text-v14");

		private final String id;

		/**
		 * @return The model id.
		 */
		public String id() {
			return id;
		}

		CohereChatModel(String value) {
			this.id = value;
		}
	}

	@Override
	public CohereChatResponse chatCompletion(CohereChatRequest request) {
		Assert.isTrue(!request.stream(), "The request must be configured to return the complete response!");
		return this.internalInvocation(request, CohereChatResponse.class);
	}

	@Override
	public Flux<CohereChatResponse.Generation> chatCompletionStream(CohereChatRequest request) {
		Assert.isTrue(request.stream(), "The request must be configured to stream the response!");
		return this.internalInvocationStream(request, CohereChatResponse.Generation.class);
	}
}
// @formatter:on