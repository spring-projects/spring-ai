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

package org.springframework.ai.bedrock.converse.api;

import software.amazon.awssdk.services.bedrockruntime.model.TokenUsage;

import org.springframework.ai.chat.metadata.Usage;
import org.springframework.util.Assert;

/**
 * {@link Usage} implementation for Bedrock Converse API.
 *
 * @author Christian Tzolov
 * @author Wei Jiang
 * @since 1.0.0
 */
public class BedrockUsage implements Usage {

	public static BedrockUsage from(TokenUsage usage) {
		Assert.notNull(usage, "'TokenUsage' must not be null.");

		return new BedrockUsage(usage.inputTokens().longValue(), usage.outputTokens().longValue());
	}

	private final Long inputTokens;

	private final Long outputTokens;

	protected BedrockUsage(Long inputTokens, Long outputTokens) {
		this.inputTokens = inputTokens;
		this.outputTokens = outputTokens;
	}

	@Override
	public Long getPromptTokens() {
		return this.inputTokens;
	}

	@Override
	public Long getGenerationTokens() {
		return this.outputTokens;
	}

	@Override
	public String toString() {
		return "BedrockUsage [inputTokens=" + this.inputTokens + ", outputTokens=" + this.outputTokens + "]";
	}

}
