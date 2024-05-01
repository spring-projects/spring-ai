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
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.publisher.Flux;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;

import org.springframework.ai.bedrock.api.AbstractBedrockApi;
import org.springframework.ai.bedrock.cohere.api.CohereCommandRChatBedrockApi.CohereCommandRChatRequest;
import org.springframework.ai.bedrock.cohere.api.CohereCommandRChatBedrockApi.CohereCommandRChatResponse;
import org.springframework.ai.bedrock.cohere.api.CohereCommandRChatBedrockApi.CohereCommandRChatStreamingResponse;

/**
 * Java client for the Bedrock Cohere command R chat model.
 * https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-cohere-command-r-plus.html
 *
 * @author Wei Jiang
 * @since 1.0.0
 */
public class CohereCommandRChatBedrockApi
		extends AbstractBedrockApi<CohereCommandRChatRequest, CohereCommandRChatResponse, CohereCommandRChatStreamingResponse> {

	/**
	 * Create a new CohereCommandRChatBedrockApi instance using the default credentials provider chain, the default object
	 * mapper, default temperature and topP values.
	 *
	 * @param modelId The model id to use. See the {@link CohereChatModel} for the supported models.
	 * @param region The AWS region to use.
	 */
	public CohereCommandRChatBedrockApi(String modelId, String region) {
		super(modelId, region);
	}

	/**
	 * Create a new CohereCommandRChatBedrockApi instance using the provided credentials provider, region and object mapper.
	 *
	 * @param modelId The model id to use. See the {@link CohereChatModel} for the supported models.
	 * @param credentialsProvider The credentials provider to connect to AWS.
	 * @param region The AWS region to use.
	 * @param objectMapper The object mapper to use for JSON serialization and deserialization.
	 */
	public CohereCommandRChatBedrockApi(String modelId, AwsCredentialsProvider credentialsProvider, String region,
			ObjectMapper objectMapper) {
		super(modelId, credentialsProvider, region, objectMapper);
	}

	/**
	 * Create a new CohereCommandRChatBedrockApi instance using the default credentials provider chain, the default object
	 * mapper, default temperature and topP values.
	 *
	 * @param modelId The model id to use. See the {@link CohereChatModel} for the supported models.
	 * @param region The AWS region to use.
	 * @param timeout The timeout to use.
	 */
	public CohereCommandRChatBedrockApi(String modelId, String region, Duration timeout) {
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
	public CohereCommandRChatBedrockApi(String modelId, AwsCredentialsProvider credentialsProvider, String region,
			ObjectMapper objectMapper, Duration timeout) {
		super(modelId, credentialsProvider, region, objectMapper, timeout);
	}

	/**
	 * Create a new CohereCommandRChatBedrockApi instance using the provided credentials provider, region and object mapper.
	 *
	 * @param modelId The model id to use. See the {@link CohereCommandRChatModel} for the supported models.
	 * @param credentialsProvider The credentials provider to connect to AWS.
	 * @param region The AWS region to use.
	 * @param objectMapper The object mapper to use for JSON serialization and deserialization.
	 * @param timeout The timeout to use.
	 */
	public CohereCommandRChatBedrockApi(String modelId, AwsCredentialsProvider credentialsProvider, Region region,
			ObjectMapper objectMapper, Duration timeout) {
		super(modelId, credentialsProvider, region, objectMapper, timeout);
	}

	/**
	 * CohereCommandRChatRequest encapsulates the request parameters for the Cohere command R model.
	 *
	 * @param message Text input for the model to respond to.
	 * @param chatHistory (optional) A list of previous messages between the user and the model.
	 * @param documents (optional) A list of texts that the model can cite to generate a more accurate reply.
	 * @param searchQueriesOnly (optional) When enabled, it will only generate potential search queries without performing
	 * searches or providing a response.
	 * @param preamble (optional) Overrides the default preamble for search query generation.
	 * @param maxTokens (optional) Specify the maximum number of tokens to use in the generated response.
	 * @param temperature (optional) Use a lower value to decrease randomness in the response.
	 * @param topP (optional) Top P. Use a lower value to ignore less probable options. Set to 0 or 1.0 to disable.
	 * @param topK (optional) Top K. Specify the number of token choices the model uses to generate the next token.
	 * @param promptTruncation (optional) Dictates how the prompt is constructed.
	 * @param frequencyPenalty (optional) Used to reduce repetitiveness of generated tokens.
	 * @param presencePenalty (optional) Used to reduce repetitiveness of generated tokens.
	 * @param seed (optional) Specify the best effort to sample tokens deterministically.
	 * @param returnPrompt (optional) Specify true to return the full prompt that was sent to the model.
	 * @param stopSequences (optional) A list of stop sequences.
	 * @param rawPrompting (optional) Specify true, to send the user’s message to the model without any preprocessing.
	 */
	@JsonInclude(Include.NON_NULL)
	public record CohereCommandRChatRequest(
			@JsonProperty("message") String message,
			@JsonProperty("chat_history") List<ChatHistory> chatHistory,
			@JsonProperty("documents") List<Document> documents,
			@JsonProperty("search_queries_only") Boolean searchQueriesOnly,
			@JsonProperty("preamble") String preamble,
			@JsonProperty("max_tokens") Integer maxTokens,
			@JsonProperty("temperature") Float temperature,
			@JsonProperty("p") Float topP,
			@JsonProperty("k") Integer topK,
			@JsonProperty("prompt_truncation") PromptTruncation promptTruncation,
			@JsonProperty("frequency_penalty") Float frequencyPenalty,
			@JsonProperty("presence_penalty") Float presencePenalty,
			@JsonProperty("seed") Integer seed,
			@JsonProperty("return_prompt") Boolean returnPrompt,
			@JsonProperty("stop_sequences") List<String> stopSequences,
			@JsonProperty("raw_prompting") Boolean rawPrompting) {

		/**
		 * The text that the model can cite to generate a more accurate reply.
		 *
		 * @param title The title of the document.
		 * @param snippet The snippet of the document.
		 */
		@JsonInclude(Include.NON_NULL)
		public record Document(
				@JsonProperty("title") String title,
				@JsonProperty("snippet") String snippet) {}

		/**
		 * Specifies how the prompt is constructed.
		 */
		public enum PromptTruncation {
			/**
			 * Some elements from chat_history and documents will be dropped to construct a prompt
			 * that fits within the model's context length limit.
			 */
			AUTO_PRESERVE_ORDER,
			/**
			 * (Default) No elements will be dropped.
			 */
			OFF
		}

		/**
		 * Get CohereCommandRChatRequest builder.
		 *
		 * @param message Compulsory request prompt parameter.
		 * @return CohereCommandRChatRequest builder.
		 */
		public static Builder builder(String message) {
			return new Builder(message);
		}

		/**
		 * Builder for the CohereCommandRChatRequest.
		 */
		public static class Builder {
			private final String message;
			private List<ChatHistory> chatHistory;
			private List<Document> documents;
			private Boolean searchQueriesOnly;
			private String preamble;
			private Integer maxTokens;
			private Float temperature;
			private Float topP;
			private Integer topK;
			private PromptTruncation promptTruncation;
			private Float frequencyPenalty;
			private Float presencePenalty;
			private Integer seed;
			private Boolean returnPrompt;
			private List<String> stopSequences;
			private Boolean rawPrompting;

			public Builder(String message) {
				this.message = message;
			}

			public Builder withChatHistory(List<ChatHistory> chatHistory) {
				this.chatHistory = chatHistory;
				return this;
			}

			public Builder withDocuments(List<Document> documents) {
				this.documents = documents;
				return this;
			}

			public Builder withSearchQueriesOnly(Boolean searchQueriesOnly) {
				this.searchQueriesOnly = searchQueriesOnly;
				return this;
			}

			public Builder withPreamble(String preamble) {
				this.preamble = preamble;
				return this;
			}

			public Builder withMaxTokens(Integer maxTokens) {
				this.maxTokens = maxTokens;
				return this;
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

			public Builder withPromptTruncation(PromptTruncation promptTruncation) {
				this.promptTruncation = promptTruncation;
				return this;
			}

			public Builder withFrequencyPenalty(Float frequencyPenalty) {
				this.frequencyPenalty = frequencyPenalty;
				return this;
			}

			public Builder withPresencePenalty(Float presencePenalty) {
				this.presencePenalty = presencePenalty;
				return this;
			}

			public Builder withSeed(Integer seed) {
				this.seed = seed;
				return this;
			}

			public Builder withReturnPrompt(Boolean returnPrompt) {
				this.returnPrompt = returnPrompt;
				return this;
			}

			public Builder withStopSequences(List<String> stopSequences) {
				this.stopSequences = stopSequences;
				return this;
			}

			public Builder withRawPrompting(Boolean rawPrompting) {
				this.rawPrompting = rawPrompting;
				return this;
			}

			public CohereCommandRChatRequest build() {
				return new CohereCommandRChatRequest(
						message,
						chatHistory,
						documents,
						searchQueriesOnly,
						preamble,
						maxTokens,
						temperature,
						topP,
						topK,
						promptTruncation,
						frequencyPenalty,
						presencePenalty,
						seed,
						returnPrompt,
						stopSequences,
						rawPrompting
						
				);
			}
		}
	}

	/**
	 * CohereCommandRChatResponse encapsulates the response parameters for the Cohere command R model.
	 *
	 * @param id Unique identifier for chat completion.
	 * @param text The model’s response to chat message input.
	 * @param generationId Unique identifier for chat completion, used with Feedback endpoint on Cohere’s platform.
	 * @param finishReason The reason why the model stopped generating output.
	 * @param chatHistory A list of previous messages between the user and the model.
	 * @param metadata API usage data.
	 */
	@JsonInclude(Include.NON_NULL)
	public record CohereCommandRChatResponse(
			@JsonProperty("response_id") String id,
			@JsonProperty("text") String text,
			@JsonProperty("generation_id") String generationId,
			@JsonProperty("finish_reason") FinishReason finishReason,
			@JsonProperty("chat_history") List<ChatHistory> chatHistory,
			@JsonProperty("meta") Metadata metadata) {

		/**
		 * API usage data.
		 *
		 * @param apiVersion The API version.
		 * @param billedUnits The billed units.
		 * @param tokens The tokens units.
		 */
		@JsonInclude(Include.NON_NULL)
		public record Metadata(
				@JsonProperty("api_version") ApiVersion apiVersion,
				@JsonProperty("billed_units") BilledUnits billedUnits,
				@JsonProperty("tokens") Tokens tokens) {

			/**
			 * The API version.
			 *
			 * @param version The API version.
			 */
			@JsonInclude(Include.NON_NULL)
			public record ApiVersion(@JsonProperty("version") String version) {}

			/**
			 * The billed units.
			 *
			 * @param inputTokens The number of input tokens that were billed.
			 * @param outputTokens The number of output tokens that were billed.
			 */
			@JsonInclude(Include.NON_NULL)
			public record BilledUnits(
					@JsonProperty("input_tokens") Integer inputTokens,
					@JsonProperty("output_tokens") Integer outputTokens) {}

			/**
			 * The tokens units.
			 *
			 * @param inputTokens The number of input tokens.
			 * @param outputTokens The number of output tokens.
			 */
			@JsonInclude(Include.NON_NULL)
			public record Tokens(
					@JsonProperty("input_tokens") Integer inputTokens,
					@JsonProperty("output_tokens") Integer outputTokens) {}

		}

	}

	/**
	 * CohereCommandRChatStreamingResponse encapsulates the streaming response parameters for the Cohere command R model.
	 * https://docs.cohere.com/docs/streaming#stream-events
	 *
	 * @param eventType The event type of stream response.
	 * @param text The model’s response to chat message input.
	 * @param isFinished Specify whether the streaming session is finished
	 * @param finishReason The reason why the model stopped generating output.
	 * @param response The final response about this stream invocation.
	 * @param amazonBedrockInvocationMetrics Metrics about the model invocation.
	 */
	@JsonInclude(Include.NON_NULL)
	public record CohereCommandRChatStreamingResponse(
			@JsonProperty("event_type") String eventType,
			@JsonProperty("text") String text,
			@JsonProperty("is_finished") Boolean isFinished,
			@JsonProperty("finish_reason") FinishReason finishReason,
			@JsonProperty("response") CohereCommandRChatResponse response,
			@JsonProperty("amazon-bedrock-invocationMetrics") AmazonBedrockInvocationMetrics amazonBedrockInvocationMetrics) {}

	/**
	 * Previous messages between the user and the model.
	 *
	 * @param role The role for the message. Valid values are USER or CHATBOT. tokens.
	 * @param message Text contents of the message.
	 */
	@JsonInclude(Include.NON_NULL)
	public record ChatHistory(
			@JsonProperty("role") Role role,
			@JsonProperty("message") String message) {

		/**
		 * The role for the message.
		 */
		public enum Role {

			/**
			 * User message.
			 */
			USER,

			/**
			 * Chatbot message.
			 */
			CHATBOT

		}

	}

	/**
	 * The reason why the model stopped generating output.
	 */
	public enum FinishReason {

		/**
		 * The completion reached the end of generation token, ensure this is the finish reason for best performance.
		 */
		COMPLETE,

		/**
		 * The generation could not be completed due to our content filters.
		 */
		ERROR_TOXIC,

		/**
		 * The generation could not be completed because the model’s context limit was reached.
		 */
		ERROR_LIMIT,

		/**
		 * The generation could not be completed due to an error.
		 */
		ERROR,

		/**
		 * The generation could not be completed because it was stopped by the user.
		 */
		USER_CANCEL,

		/**
		 * The generation could not be completed because the user specified a max_tokens limit in the request and this limit was reached. May not result in best performance.
		 */
		MAX_TOKENS

	}

	/**
	 * Cohere command R models version.
	 */
	public enum CohereCommandRChatModel {

		/**
		 * cohere.command-r-v1:0
		 */
		COHERE_COMMAND_R_V1("cohere.command-r-v1:0"),

		/**
		 * cohere.command-r-plus-v1:0
		 */
		COHERE_COMMAND_R_PLUS_V1("cohere.command-r-plus-v1:0");

		private final String id;

		/**
		 * @return The model id.
		 */
		public String id() {
			return id;
		}

		CohereCommandRChatModel(String value) {
			this.id = value;
		}
	}

	@Override
	public CohereCommandRChatResponse chatCompletion(CohereCommandRChatRequest request) {
		return this.internalInvocation(request, CohereCommandRChatResponse.class);
	}

	@Override
	public Flux<CohereCommandRChatStreamingResponse> chatCompletionStream(CohereCommandRChatRequest request) {
		return this.internalInvocationStream(request, CohereCommandRChatStreamingResponse.class);
	}

}
//@formatter:on
