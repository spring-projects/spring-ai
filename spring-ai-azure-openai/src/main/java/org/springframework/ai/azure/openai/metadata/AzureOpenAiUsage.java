/*
 * Copyright 2023 the original author or authors.
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

package org.springframework.ai.azure.openai.metadata;

import com.azure.ai.openai.models.ChatCompletions;
import com.azure.ai.openai.models.CompletionsUsage;

import org.springframework.ai.metadata.Usage;
import org.springframework.util.Assert;

/**
 * {@link Usage} implementation for {@literal Microsoft Azure OpenAI Service}.
 *
 * @author John Blum
 * @see com.azure.ai.openai.models.CompletionsUsage
 * @since 0.7.0
 */
public class AzureOpenAiUsage implements Usage {

	public static AzureOpenAiUsage from(ChatCompletions chatCompletions) {
		Assert.notNull(chatCompletions, "ChatCompletions must not be null");
		return from(chatCompletions.getUsage());
	}

	public static AzureOpenAiUsage from(CompletionsUsage usage) {
		return new AzureOpenAiUsage(usage);
	}

	private final CompletionsUsage usage;

	public AzureOpenAiUsage(CompletionsUsage usage) {
		Assert.notNull(usage, "CompletionsUsage must not be null");
		this.usage = usage;
	}

	protected CompletionsUsage getUsage() {
		return this.usage;
	}

	@Override
	public Long getPromptTokens() {
		return Integer.valueOf(getUsage().getPromptTokens()).longValue();
	}

	@Override
	public Long getGenerationTokens() {
		return Integer.valueOf(getUsage().getCompletionTokens()).longValue();
	}

	@Override
	public Long getTotalTokens() {
		return Integer.valueOf(getUsage().getTotalTokens()).longValue();
	}

	@Override
	public String toString() {
		return getUsage().toString();
	}

}
