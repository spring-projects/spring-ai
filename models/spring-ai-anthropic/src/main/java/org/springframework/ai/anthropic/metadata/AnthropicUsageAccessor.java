/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.anthropic.metadata;

import java.util.Map;

import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.metadata.UsageUtils;
import org.springframework.util.Assert;

/**
 * Anthropic Usage accessor class which provides access to the usage metadata.
 *
 * @author Ilayaperumal Gopinathan
 */
public record AnthropicUsageAccessor(Map<String, Object> usage) implements Usage {

	public static final String INPUT_TOKENS = "input_tokens";

	public static final String OUTPUT_TOKENS = "output_tokens";

	public static final String CACHE_CREATION_INPUT_TOKENS = "cache_creation_input_tokens";

	public static final String CACHE_READ_INPUT_TOKENS = "cache_read_input_tokens";

	public AnthropicUsageAccessor {
		Assert.notNull(usage, "usage must not be null");
	}

	@Override
	public Long getPromptTokens() {
		return UsageUtils.parseLong(this.usage.get(INPUT_TOKENS));
	}

	@Override
	public Long getGenerationTokens() {
		return UsageUtils.parseLong(this.usage.get(OUTPUT_TOKENS));
	}

	public Long getCacheCreationInputTokens() {
		return UsageUtils.parseLong(this.usage.get(CACHE_CREATION_INPUT_TOKENS));
	}

	public Long getCacheReadInputTokens() {
		return UsageUtils.parseLong(this.usage.get(CACHE_READ_INPUT_TOKENS));
	}

	public Map<String, Object> getUsage() {
		return this.usage;
	}

	@Override
	public String toString() {
		return this.usage.toString();
	}
}
