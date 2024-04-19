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
package org.springframework.ai.bedrock;

import org.springframework.ai.bedrock.api.AbstractBedrockApi.AmazonBedrockInvocationMetadata;
import org.springframework.ai.bedrock.api.AbstractBedrockApi.AmazonBedrockInvocationMetrics;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.util.Assert;

/**
 * {@link Usage} implementation for Bedrock API.
 *
 * @author Christian Tzolov
 * @author Wei Jiang
 * @since 0.8.0
 */
public class BedrockUsage implements Usage {

	public static BedrockUsage from(AmazonBedrockInvocationMetrics usage) {
		Assert.notNull(usage, "Amazon Bedrock Usage must not be null");

		return new BedrockUsage(usage.inputTokenCount().longValue(), usage.outputTokenCount().longValue());
	}

	public static BedrockUsage from(AmazonBedrockInvocationMetadata metadata) {
		Assert.notNull(metadata, "Amazon Bedrock Invocation Metadata must not be null");

		return new BedrockUsage(metadata.inputTokenCount(), metadata.outputTokenCount());
	}

	private Long promptTokens;

	private Long generationTokens;

	protected BedrockUsage(Long promptTokens, Long generationTokens) {
		this.promptTokens = promptTokens;
		this.generationTokens = generationTokens;
	}

	@Override
	public Long getPromptTokens() {
		return this.promptTokens;
	}

	@Override
	public Long getGenerationTokens() {
		return this.generationTokens;
	}

	@Override
	public String toString() {
		return "BedrockUsage [promptTokens=" + promptTokens + ", generationTokens=" + generationTokens + "]";
	}

}
