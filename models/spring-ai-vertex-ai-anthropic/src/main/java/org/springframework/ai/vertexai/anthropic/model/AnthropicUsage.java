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
package org.springframework.ai.vertexai.anthropic.model;

import org.springframework.ai.chat.metadata.Usage;
import org.springframework.util.Assert;

/**
 * {@link ApiUsage} implementation for {@literal AnthropicApi}.
 *
 * @author Alessio Bertazzo
 * @since 1.0.0
 */
public class AnthropicUsage implements Usage {

	public static AnthropicUsage from(ApiUsage usage) {
		return new AnthropicUsage(usage);
	}

	private final ApiUsage usage;

	protected AnthropicUsage(ApiUsage usage) {
		Assert.notNull(usage, "AnthropicApi Usage must not be null");
		this.usage = usage;
	}

	protected ApiUsage getUsage() {
		return this.usage;
	}

	@Override
	public Long getPromptTokens() {
		return getUsage().inputTokens().longValue();
	}

	@Override
	public Long getGenerationTokens() {
		return getUsage().outputTokens().longValue();
	}

	@Override
	public Long getTotalTokens() {
		return this.getPromptTokens() + this.getGenerationTokens();
	}

	@Override
	public String toString() {
		return getUsage().toString();
	}

}
