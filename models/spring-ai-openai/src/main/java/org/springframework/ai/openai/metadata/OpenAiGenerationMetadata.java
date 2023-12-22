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

package org.springframework.ai.openai.metadata;

import org.springframework.ai.metadata.GenerationMetadata;
import org.springframework.ai.metadata.RateLimit;
import org.springframework.ai.metadata.Usage;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * {@link GenerationMetadata} implementation for {@literal OpenAI}.
 *
 * @author John Blum
 * @see org.springframework.ai.metadata.GenerationMetadata
 * @see org.springframework.ai.metadata.RateLimit
 * @see org.springframework.ai.metadata.Usage
 * @since 0.7.0
 */
public class OpenAiGenerationMetadata implements GenerationMetadata {

	protected static final String AI_METADATA_STRING = "{ @type: %1$s, id: %2$s, usage: %3$s, rateLimit: %4$s }";

	public static OpenAiGenerationMetadata from(OpenAiApi.ChatCompletion result) {
		Assert.notNull(result, "OpenAI ChatCompletionResult must not be null");
		OpenAiUsage usage = OpenAiUsage.from(result.usage());
		OpenAiGenerationMetadata generationMetadata = new OpenAiGenerationMetadata(result.id(), usage);
		return generationMetadata;
	}

	private final String id;

	@Nullable
	private RateLimit rateLimit;

	private final Usage usage;

	protected OpenAiGenerationMetadata(String id, OpenAiUsage usage) {
		this(id, usage, null);
	}

	protected OpenAiGenerationMetadata(String id, OpenAiUsage usage, @Nullable OpenAiRateLimit rateLimit) {
		this.id = id;
		this.usage = usage;
		this.rateLimit = rateLimit;
	}

	public String getId() {
		return this.id;
	}

	@Override
	@Nullable
	public RateLimit getRateLimit() {
		RateLimit rateLimit = this.rateLimit;
		return rateLimit != null ? rateLimit : RateLimit.NULL;
	}

	@Override
	public Usage getUsage() {
		Usage usage = this.usage;
		return usage != null ? usage : Usage.NULL;
	}

	public OpenAiGenerationMetadata withRateLimit(RateLimit rateLimit) {
		this.rateLimit = rateLimit;
		return this;
	}

	@Override
	public String toString() {
		return AI_METADATA_STRING.formatted(getClass().getName(), getId(), getUsage(), getRateLimit());
	}

}
