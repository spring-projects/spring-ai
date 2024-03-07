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
package org.springframework.ai.bedrock.anthropic3.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.bedrock.anthropic3.api.AnthropicChatBedrockApi.AnthropicChatRequest;
import org.springframework.ai.bedrock.anthropic3.api.AnthropicChatBedrockApi.AnthropicChatResponse;
import org.springframework.ai.bedrock.anthropic3.api.AnthropicChatBedrockApi.AnthropicChatStreamingResponse;
import org.springframework.ai.bedrock.api.AbstractBedrockApi;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;

import java.util.List;

/**
 * @author Christian Tzolov
 * @since 0.8.0
 */
// @formatter:off
public class AnthropicChatBedrockApi extends
		AbstractBedrockApi<AnthropicChatRequest, AnthropicChatResponse, AnthropicChatStreamingResponse> {


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

	// https://github.com/build-on-aws/amazon-bedrock-java-examples/blob/main/example_code/bedrock-runtime/src/main/java/aws/community/examples/InvokeBedrockStreamingAsync.java

	// https://docs.anthropic.com/claude/reference/complete_post

	// https://docs.aws.amazon.com/bedrock/latest/userguide/br-product-ids.html

	// Anthropic Claude models: https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-claude.html

	/**
	 * AnthropicChatRequest encapsulates the request parameters for the Anthropic messages model.
	 * https://docs.anthropic.com/claude/reference/messages_post
	 *
	 * @param messages A list of messages comprising the conversation so far.
	 * @param system A system prompt, providing context and instructions to Claude, such as specifying a particular goal
	 * or role.
	 * @param temperature (default 0.5) The temperature to use for the chat. You should either alter temperature or
	 * top_p, but not both.
	 * @param maxTokens (default 200) Specify the maximum number of tokens to use in the generated response.
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
			@JsonProperty("messages") List<ChatCompletionMessage> messages,
			@JsonProperty("system") String system,
			@JsonProperty("temperature") Float temperature,
			@JsonProperty("max_tokens") Integer maxTokens,
			@JsonProperty("top_k") Integer topK,
			@JsonProperty("top_p") Float topP,
			@JsonProperty("stop_sequences") List<String> stopSequences,
			@JsonProperty("anthropic_version") String anthropicVersion) {

		public static Builder builder(List<ChatCompletionMessage> messages) {
			return new Builder(messages);
		}

		public static class Builder {
			private final List<ChatCompletionMessage> messages;
			private String system;
			private Float temperature;// = 0.7f;
			private Integer maxTokens;// = 500;
			private Integer topK;// = 10;
			private Float topP;
			private List<String> stopSequences;
			// private String anthropicVersion = DEFAULT_ANTHROPIC_VERSION;
			private String anthropicVersion;

			private Builder(List<ChatCompletionMessage> messages) {
				this.messages = messages;
			}

			public Builder withSystem(String system) {
				this.system = system;
				return this;
			}
			public Builder withTemperature(Float temperature) {
				this.temperature = temperature;
				return this;
			}

			public Builder withMaxTokens(Integer maxTokens) {
				this.maxTokens = maxTokens;
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
						messages,
						system,
						temperature,
						maxTokens,
						topK,
						topP,
						stopSequences,
						anthropicVersion
				);
			}
		}
	}

	/**
	 * Encapsulates an input message.
	 * TODO: Add support for images.
	 * https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-anthropic-claude-messages.html#model-parameters-anthropic-claude-messages-request-response
	 *
	 * @param type The type of the message.
	 * @param text The text message.
	 */
	@JsonInclude(Include.NON_NULL)
	public record AnthropicMessage(
			@JsonProperty("type") Type type,
			@JsonProperty("text") String text) {

		/**
		 * The type of this message.
		 */
		public enum Type {
			/**
			 * Text message.
			 */
			@JsonProperty("text") TEXT,
			/**
			 * Image message.
			 */
			@JsonProperty("image") IMAGE
		}
	}

	/**
	 * Message comprising the conversation.
	 *
	 * @param content The contents of the message.
	 * @param role The role of the messages author. Could be one of the {@link Role} types.
	 */
	@JsonInclude(Include.NON_NULL)
	public record ChatCompletionMessage(
			@JsonProperty("content") List<AnthropicMessage> content,
			@JsonProperty("role") Role role) {

		/**
		 * The role of the author of this message.
		 */
		public enum Role {
				/**
			 * User message.
			 */
			@JsonProperty("user") USER,
			/**
			 * Assistant message.
			 */
			@JsonProperty("assistant") ASSISTANT
		}
	}

	/**
	 * Encapsulates the metrics about the model invocation.
	 * https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-anthropic-claude-messages.html#model-parameters-anthropic-claude-messages-request-response
	 *
	 * @param inputTokens The number of tokens in the input prompt.
	 * @param outputTokens The number of tokens in the generated text.
	 */
	@JsonInclude(Include.NON_NULL)
	public record AnthropicUsage(
			@JsonProperty("input_tokens") Integer inputTokens,
			@JsonProperty("output_tokens") Integer outputTokens) {
	}

	/**
	 * AnthropicChatResponse encapsulates the response parameters for the Anthropic messages model.
	 *
	 * @param id The unique response identifier.
	 * @param model  The ID for the Anthropic Claude model that made the request.
	 * @param content The generated text.
	 * @param stopReason The reason the model stopped generating text:
	 *        end_turn – The model reached a natural stopping point.
	 *        max_tokens – The generated text exceeded the value of the max_tokens input field or exceeded the maximum
	 *        number of tokens that the model supports.
	 *        stop_sequence – The model generated one of the stop sequences that you specified in the stop_sequences
	 *        input field.
	 * @param stopSequence The stop sequence that caused the model to stop generating text.
	 * @param usage Metrics about the model invocation.
	 */
	@JsonInclude(Include.NON_NULL)
	public record AnthropicChatResponse(
			@JsonProperty("id") String id,
			@JsonProperty("model") String model,
			@JsonProperty("type") String type,
			@JsonProperty("role") String role,
			@JsonProperty("content") List<AnthropicMessage> content,
			@JsonProperty("stop_reason") String stopReason,
			@JsonProperty("stop_sequence") String stopSequence,
			@JsonProperty("usage") AnthropicUsage usage) {
	}

	/**
	 * AnthropicChatStreamingResponse encapsulates the streaming response parameters for the Anthropic messages model.
	 * https://docs.anthropic.com/claude/reference/messages-streaming
	 *
	 * @param type The streaming type.
	 * @param message The message details that made the request.
	 * @param index The delta index.
	 * @param content_block The generated text.
	 * @param delta The delta.
	 */
	@JsonInclude(Include.NON_NULL)
	public record AnthropicChatStreamingResponse(
			@JsonProperty("type") StreamingType type,
			@JsonProperty("message") AnthropicChatResponse message,
			@JsonProperty("index") Integer index,
			@JsonProperty("content_block") AnthropicMessage content_block,
			@JsonProperty("delta") Delta delta,
			@JsonProperty("usage") AnthropicUsage usage) {

		/**
		 * The streaming type of this message.
		 */
		public enum StreamingType {
			/**
			 * Message start.
			 */
			@JsonProperty("message_start") MESSAGE_START,
			/**
			 * Content block start.
			 */
			@JsonProperty("content_block_start") CONTENT_BLOCK_START,
			/**
			 * Ping.
			 */
			@JsonProperty("ping") PING,
			/**
			 * Content block delta.
			 */
			@JsonProperty("content_block_delta") CONTENT_BLOCK_DELTA,
			/**
			 * Content block stop.
			 */
			@JsonProperty("content_block_stop") CONTENT_BLOCK_STOP,
			/**
			 * Message delta.
			 */
			@JsonProperty("message_delta") MESSAGE_DELTA,
			/**
			 * Message stop.
			 */
			@JsonProperty("message_stop") MESSAGE_STOP
		}


		/**
		 * Encapsulates a delta.
		 * https://docs.anthropic.com/claude/reference/messages-streaming		 *
		 * @param type The type of the message.
		 * @param text The text message.
		 */
		@JsonInclude(Include.NON_NULL)
		public record Delta(
				@JsonProperty("type") String type,
				@JsonProperty("text") String text,
				@JsonProperty("stop_reason") String stopReason,
				@JsonProperty("stop_sequence") String stopSequence
		) {
		}

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
		CLAUDE_V21("anthropic.claude-v2:1"),
		/**
		 * anthropic.claude-3-sonnet-20240229-v1:0
		 */
		CLAUDE_V3_SONNET("anthropic.claude-3-sonnet-20240229-v1:0");

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
	public Flux<AnthropicChatStreamingResponse> chatCompletionStream(AnthropicChatRequest anthropicRequest) {
		Assert.notNull(anthropicRequest, "'anthropicRequest' must not be null");
		return this.internalInvocationStream(anthropicRequest, AnthropicChatStreamingResponse.class);
	}

}
// @formatter:on
