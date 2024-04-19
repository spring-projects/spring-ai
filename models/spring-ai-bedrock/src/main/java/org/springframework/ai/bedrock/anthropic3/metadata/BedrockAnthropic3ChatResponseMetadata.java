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
package org.springframework.ai.bedrock.anthropic3.metadata;

import org.springframework.ai.bedrock.BedrockUsage;
import org.springframework.ai.bedrock.anthropic3.api.Anthropic3ChatBedrockApi.AnthropicChatResponse;
import org.springframework.ai.bedrock.api.AbstractBedrockApi.AmazonBedrockInvocationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.PromptMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.util.Assert;

/**
 * {@link ChatResponseMetadata} implementation for
 * {@literal Amazon Bedrock Anthropic Chat Model}.
 *
 * @author Wei Jiang
 * @see ChatResponseMetadata
 * @since 0.8.1
 */
public class BedrockAnthropic3ChatResponseMetadata implements ChatResponseMetadata {

	protected static final String AI_METADATA_STRING = "{ @type: %1$s, id: %2$s, latency: %3$s, usage: %4$s, rateLimit: %5$s }";

	public static BedrockAnthropic3ChatResponseMetadata from(AnthropicChatResponse response,
			AmazonBedrockInvocationMetadata invocationMetadata) {
		Assert.notNull(invocationMetadata, "Bedrock invocation metadata must not be null");

		BedrockUsage usage = BedrockUsage.from(invocationMetadata);

		BedrockAnthropic3ChatResponseMetadata chatResponseMetadata = new BedrockAnthropic3ChatResponseMetadata(
				response.id(), invocationMetadata.invocationLatency(), usage);
		return chatResponseMetadata;
	}

	private final String id;

	private Long invocationLatency;

	private final Usage usage;

	protected BedrockAnthropic3ChatResponseMetadata(String id, Long invocationLatency, BedrockUsage usage) {
		this.id = id;
		this.invocationLatency = invocationLatency;
		this.usage = usage;
	}

	public String getId() {
		return this.id;
	}

	public Long getInvocationLatency() {
		return this.invocationLatency;
	}

	@Override
	public Usage getUsage() {
		return this.usage;
	}

	@Override
	public PromptMetadata getPromptMetadata() {
		return PromptMetadata.empty();
	}

	@Override
	public String toString() {
		return AI_METADATA_STRING.formatted(getClass().getTypeName(), getId(), getInvocationLatency(), getUsage(),
				getRateLimit());
	}

}
