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

import org.springframework.ai.metadata.GenerationMetadata;
import org.springframework.ai.metadata.Usage;
import org.springframework.util.Assert;

/**
 * {@link GenerationMetadata} implementation for
 * {@literal Microsoft Azure OpenAI Service}.
 *
 * @author John Blum
 * @see org.springframework.ai.metadata.GenerationMetadata
 * @since 0.7.1
 */
public class AzureOpenAiGenerationMetadata implements GenerationMetadata {

	protected static final String AI_METADATA_STRING = "{ @type: %1$s, id: %2$s, usage: %3$s, rateLimit: %4$s }";

	@SuppressWarnings("all")
	public static AzureOpenAiGenerationMetadata from(ChatCompletions chatCompletions) {
		Assert.notNull(chatCompletions, "Azure OpenAI ChatCompletions must not be null");
		String id = chatCompletions.getId();
		AzureOpenAiUsage usage = AzureOpenAiUsage.from(chatCompletions);
		AzureOpenAiGenerationMetadata generationMetadata = new AzureOpenAiGenerationMetadata(id, usage);
		return generationMetadata;
	}

	private final String id;

	private final Usage usage;

	protected AzureOpenAiGenerationMetadata(String id, AzureOpenAiUsage usage) {
		this.id = id;
		this.usage = usage;
	}

	public String getId() {
		return this.id;
	}

	@Override
	public Usage getUsage() {
		return this.usage;
	}

	@Override
	public String toString() {
		return AI_METADATA_STRING.formatted(getClass().getTypeName(), getId(), getUsage(), getRateLimit());
	}

}
