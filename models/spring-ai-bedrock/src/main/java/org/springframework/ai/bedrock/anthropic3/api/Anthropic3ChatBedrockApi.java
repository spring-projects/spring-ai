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

package org.springframework.ai.bedrock.anthropic3.api;

import java.time.Duration;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.publisher.Flux;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;

import org.springframework.ai.bedrock.anthropic3.api.Anthropic3ChatBedrockApi.AnthropicChatRequest;
import org.springframework.ai.bedrock.anthropic3.api.Anthropic3ChatBedrockApi.AnthropicChatResponse;
import org.springframework.ai.bedrock.anthropic3.api.Anthropic3ChatBedrockApi.AnthropicChatStreamingResponse;
import org.springframework.ai.bedrock.api.AbstractBedrockApi;
import org.springframework.ai.model.ChatModelDescription;
import org.springframework.util.Assert;

/**
 * Based on Bedrock's <a href=
 * "https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-anthropic-claude-messages.html">Anthropic
 * Claude Messages API</a>.
 *
 * It is meant to replace the previous Chat API, which is now deprecated.
 *
 * @author Ben Middleton
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Wei Jiang
 * @since 1.0.0
 */
// @formatter:off
public class Anthropic3ChatBedrockApi extends
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
	public Anthropic3ChatBedrockApi(String modelId, String region) {
		super(modelId, region);
	}

	/**
	 * Create a new AnthropicChatBedrockApi instance using the default credentials provider chain, the default object.
	 * @param modelId The model id to use. See the {@link AnthropicChatModel} for the supported models.
	 * @param region The AWS region to use.
	 * @param timeout The timeout to use.
	 */
	public Anthropic3ChatBedrockApi(String modelId, String region, Duration timeout) {
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
	public Anthropic3ChatBedrockApi(String modelId, AwsCredentialsProvider credentialsProvider, String region,
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
	public Anthropic3ChatBedrockApi(String modelId, AwsCredentialsProvider credentialsProvider, String region,
			ObjectMapper objectMapper, Duration timeout) {
		super(modelId, credentialsProvider, region, objectMapper, timeout);
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
	public Anthropic3ChatBedrockApi(String modelId, AwsCredentialsProvider credentialsProvider, Region region,
			ObjectMapper objectMapper, Duration timeout) {
		super(modelId, credentialsProvider, region, objectMapper, timeout);
	}

	// https://github.com/build-on-aws/amazon-bedrock-java-examples/blob/main/example_code/bedrock-runtime/src/main/java/aws/community/examples/InvokeBedrockStreamingAsync.java

	// https://docs.anthropic.com/claude/reference/complete_post

	// https://docs.aws.amazon.com/bedrock/latest/userguide/br-product-ids.html

	// Anthropic Claude models: https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-claude.html

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

	/**
	 * Anthropic models version.
	 */
	public enum AnthropicChatModel implements ChatModelDescription {

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
		CLAUDE_V3_SONNET("anthropic.claude-3-sonnet-20240229-v1:0"),
		/**
		 * anthropic.claude-3-haiku-20240307-v1:0
		 */
		CLAUDE_V3_HAIKU("anthropic.claude-3-haiku-20240307-v1:0"),
		/**
		 * anthropic.claude-3-opus-20240229-v1:0
		 */
		CLAUDE_V3_OPUS("anthropic.claude-3-opus-20240229-v1:0"),
		/**
		 * anthropic.claude-3-5-sonnet-20240620-v1:0
		 */
		CLAUDE_V3_5_SONNET("anthropic.claude-3-5-sonnet-20240620-v1:0"),
		/**
		 * anthropic.claude-3-5-sonnet-20241022-v2:0
		 */
		CLAUDE_V3_5_SONNET_V2("anthropic.claude-3-5-sonnet-20241022-v2:0");

		private final String id;

		AnthropicChatModel(String value) {
			this.id = value;
		}

		/**
		 * Get the model id.
		 * @return The model id.
		 */
		public String id() {
			return this.id;
		}

		@Override
		public String getName() {
			return this.id;
		}

	}

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
			@JsonProperty("temperature") Double temperature,
			@JsonProperty("max_tokens") Integer maxTokens,
			@JsonProperty("top_k") Integer topK,
			@JsonProperty("top_p") Double topP,
			@JsonProperty("stop_sequences") List<String> stopSequences,
			@JsonProperty("anthropic_version") String anthropicVersion) {

		/**
		 * Create a new {@link AnthropicChatRequest} instance.
		 * @param messages A list of messages comprising the conversation so far.
		 * @return the {@link AnthropicChatRequest}
		 */
		public static Builder builder(List<ChatCompletionMessage> messages) {
			return new Builder(messages);
		}

		/**
		 * Builder for {@link AnthropicChatRequest}.
		 */
		public static final class Builder {
			private final List<ChatCompletionMessage> messages;
			private String system;
			private Double temperature; // = 0.7;
			private Integer maxTokens; // = 500;
			private Integer topK; // = 10;
			private Double topP;
			private List<String> stopSequences;
			private String anthropicVersion;
			
			private Builder(List<ChatCompletionMessage> messages) {
				this.messages = messages;
			}

			/**
			 * Set the system prompt.
			 * @param system A system prompt
			 * @return this {@link Builder} instance
			 */
			public Builder withSystem(String system) {
				this.system = system;
				return this;
			}

			/**
			 * Set the temperature.
			 * @param temperature The temperature
			 * @return this {@link Builder} instance
			 */
			public Builder withTemperature(Double temperature) {
				this.temperature = temperature;
				return this;
			}

			/**
			 * Set the max tokens.
			 * @param maxTokens The max tokens
			 * @return this {@link Builder} instance
			 */
			public Builder withMaxTokens(Integer maxTokens) {
				this.maxTokens = maxTokens;
				return this;
			}

			/**
			 * Set the top k.
			 * @param topK The top k
			 * @return this {@link Builder} instance
			 */
			public Builder withTopK(Integer topK) {
				this.topK = topK;
				return this;
			}

			/**
			 * Set the top p.
			 * @param tpoP The top p
			 * @return this {@link Builder} instance
			 */
			public Builder withTopP(Double tpoP) {
				this.topP = tpoP;
				return this;
			}

			/**
			 * Set the stop sequences.
			 * @param stopSequences The stop sequences
			 * @return this {@link Builder} instance
			 */
			public Builder withStopSequences(List<String> stopSequences) {
				this.stopSequences = stopSequences;
				return this;
			}

			/**
			 * Set the anthropic version.
			 * @param anthropicVersion The anthropic version
			 * @return this {@link Builder} instance
			 */
			public Builder withAnthropicVersion(String anthropicVersion) {
				this.anthropicVersion = anthropicVersion;
				return this;
			}

			/**
			 * Build the {@link AnthropicChatRequest}.
			 * @return the {@link AnthropicChatRequest}
			 */
			public AnthropicChatRequest build() {
				return new AnthropicChatRequest(
						this.messages,
						this.system,
						this.temperature,
						this.maxTokens,
						this.topK,
						this.topP,
						this.stopSequences,
						this.anthropicVersion
				);
			}
		}
	}

	/**
	 * Encapsulates the Amazon Bedrock invocation metrics.
	 * @param type the content type can be "text" or "image".
	 * @param source The source of the media content. Applicable for "image" types only.
	 * @param text The text of the message. Applicable for "text" types only.
	 * @param index The index of the content block. Applicable only for streaming
	 * responses.
	 */
	@JsonInclude(Include.NON_NULL)
	public record MediaContent(
			// @formatter:off
		@JsonProperty("type") Type type,
		@JsonProperty("source") Source source,
		@JsonProperty("text") String text,
		@JsonProperty("index") Integer index // applicable only for streaming responses.
		) {
		// @formatter:on

		/**
		 * Create a new media content.
		 * @param mediaType The media type of the content.
		 * @param data The base64-encoded data of the content.
		 */
		public MediaContent(String mediaType, String data) {
			this(new Source(mediaType, data));
		}

		/**
		 * Create a new media content.
		 * @param source The source of the media content.
		 */
		public MediaContent(Source source) {
			this(Type.IMAGE, source, null, null);
		}

		/**
		 * Create a new media content.
		 * @param text The text of the message.
		 */
		public MediaContent(String text) {
			this(Type.TEXT, null, text, null);
		}

		/**
		 * The type of this message.
		 */
		public enum Type {

			/**
			 * Text message.
			 */
			@JsonProperty("text")
			TEXT,
			/**
			 * Image message.
			 */
			@JsonProperty("image")
			IMAGE

		}

		/**
		 * The source of the media content. (Applicable for "image" types only)
		 *
		 * @param type The type of the media content. Only "base64" is supported at the
		 * moment.
		 * @param mediaType The media type of the content. For example, "image/png" or
		 * "image/jpeg".
		 * @param data The base64-encoded data of the content.
		 */
		@JsonInclude(Include.NON_NULL)
		public record Source(
		// @formatter:off
			@JsonProperty("type") String type,
			@JsonProperty("media_type") String mediaType,
			@JsonProperty("data") String data) {
			// @formatter:on

			/**
			 * Create a new source.
			 * @param mediaType The media type of the content.
			 * @param data The base64-encoded data of the content.
			 */
			public Source(String mediaType, String data) {
				this("base64", mediaType, data);
			}

		}

	}

	/**
	 * Message comprising the conversation.
	 *
	 * @param content The contents of the message.
	 * @param role The role of the messages author. Could be one of the {@link Role}
	 * types.
	 */
	@JsonInclude(Include.NON_NULL)
	public record ChatCompletionMessage(@JsonProperty("content") List<MediaContent> content,
			@JsonProperty("role") Role role) {

		/**
		 * The role of the author of this message.
		 */
		public enum Role {

			/**
			 * User message.
			 */
			@JsonProperty("user")
			USER,
			/**
			 * Assistant message.
			 */
			@JsonProperty("assistant")
			ASSISTANT

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
	public record AnthropicUsage(@JsonProperty("input_tokens") Integer inputTokens,
			@JsonProperty("output_tokens") Integer outputTokens) {

	}

	/**
	 * AnthropicChatResponse encapsulates the response parameters for the Anthropic
	 * messages model.
	 *
	 * @param id The unique response identifier.
	 * @param model The ID for the Anthropic Claude model that made the request.
	 * @param type The type of the response.
	 * @param role The role of the response.
	 * @param content The list of generated text.
	 * @param stopReason The reason the model stopped generating text: end_turn – The
	 * model reached a natural stopping point. max_tokens – The generated text exceeded
	 * the value of the max_tokens input field or exceeded the maximum number of tokens
	 * that the model supports. stop_sequence – The model generated one of the stop
	 * sequences that you specified in the stop_sequences input field.
	 * @param stopSequence The stop sequence that caused the model to stop generating
	 * text.
	 * @param usage Metrics about the model invocation.
	 * @param amazonBedrockInvocationMetrics The metrics about the model invocation.
	 */
	@JsonInclude(Include.NON_NULL)
	public record AnthropicChatResponse(// formatter:off
			@JsonProperty("id") String id, @JsonProperty("model") String model, @JsonProperty("type") String type,
			@JsonProperty("role") String role, @JsonProperty("content") List<MediaContent> content,
			@JsonProperty("stop_reason") String stopReason, @JsonProperty("stop_sequence") String stopSequence,
			@JsonProperty("usage") AnthropicUsage usage,
			@JsonProperty("amazon-bedrock-invocationMetrics") AmazonBedrockInvocationMetrics amazonBedrockInvocationMetrics) { // formatter:on

	}

	/**
	 * AnthropicChatStreamingResponse encapsulates the streaming response parameters for
	 * the Anthropic messages model.
	 * https://docs.anthropic.com/claude/reference/messages-streaming
	 *
	 * @param type The streaming type.
	 * @param message The message details that made the request.
	 * @param index The delta index.
	 * @param contentBlock The generated text.
	 * @param delta The delta.
	 * @param usage The usage data.
	 * @param amazonBedrockInvocationMetrics The metrics about the model invocation.
	 */
	@JsonInclude(Include.NON_NULL)
	public record AnthropicChatStreamingResponse(// formatter:off
			@JsonProperty("type") StreamingType type, @JsonProperty("message") AnthropicChatResponse message,
			@JsonProperty("index") Integer index, @JsonProperty("content_block") MediaContent contentBlock,
			@JsonProperty("delta") Delta delta, @JsonProperty("usage") AnthropicUsage usage,
			@JsonProperty("amazon-bedrock-invocationMetrics") AmazonBedrockInvocationMetrics amazonBedrockInvocationMetrics) { // formatter:on

		/**
		 * The streaming type of this message.
		 */
		public enum StreamingType {

			/**
			 * Message start.
			 */
			@JsonProperty("message_start")
			MESSAGE_START,
			/**
			 * Content block start.
			 */
			@JsonProperty("content_block_start")
			CONTENT_BLOCK_START,
			/**
			 * Ping.
			 */
			@JsonProperty("ping")
			PING,
			/**
			 * Content block delta.
			 */
			@JsonProperty("content_block_delta")
			CONTENT_BLOCK_DELTA,
			/**
			 * Content block stop.
			 */
			@JsonProperty("content_block_stop")
			CONTENT_BLOCK_STOP,
			/**
			 * Message delta.
			 */
			@JsonProperty("message_delta")
			MESSAGE_DELTA,
			/**
			 * Message stop.
			 */
			@JsonProperty("message_stop")
			MESSAGE_STOP

		}

		/**
		 * Encapsulates a delta.
		 * https://docs.anthropic.com/claude/reference/messages-streaming *
		 *
		 * @param type The type of the message.
		 * @param text The text message.
		 * @param stopReason The stop reason.
		 * @param stopSequence The stop sequence.
		 */
		@JsonInclude(Include.NON_NULL)
		public record Delta(@JsonProperty("type") String type, @JsonProperty("text") String text,
				@JsonProperty("stop_reason") String stopReason, @JsonProperty("stop_sequence") String stopSequence) {

		}

	}

}
// @formatter:on
