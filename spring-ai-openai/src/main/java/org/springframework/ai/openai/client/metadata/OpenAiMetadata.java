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

package org.springframework.ai.openai.client.metadata;

import com.theokanning.openai.completion.chat.ChatCompletionResult;

import org.springframework.ai.client.metadata.AiMetadata;
import org.springframework.ai.client.metadata.RateLimit;
import org.springframework.ai.client.metadata.Usage;
import org.springframework.ai.openai.client.metadata.support.OpenAiHttpResponseHeadersInterceptor;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * {@link AiMetadata} implementation for {@literal OpenAI}.
 *
 * @author John Blum
 * @see org.springframework.ai.client.metadata.AiMetadata
 * @see org.springframework.ai.client.metadata.RateLimit
 * @see org.springframework.ai.client.metadata.Usage
 * @since 0.7.0
 */
public class OpenAiMetadata implements AiMetadata {

	protected static final String AI_METADATA_STRING = "{ @type: %1$s, id: %2$s, usage: %3$s, rateLimit: %4$s }";

	public static OpenAiMetadata from(ChatCompletionResult result) {
		Assert.notNull(result, "OpenAI ChatCompletionResult must not be null");
		OpenAiMetadata metadata = new OpenAiMetadata(result.getId(), OpenAiUsage.from(result.getUsage()));
		OpenAiHttpResponseHeadersInterceptor.applyTo(metadata);
		return metadata;
	}

	private final String id;

	@Nullable
	private RateLimit rateLimit;

	private final Usage usage;

	protected OpenAiMetadata(String id, OpenAiUsage usage) {
		this(id, usage, null);
	}

	protected OpenAiMetadata(String id, OpenAiUsage usage, @Nullable OpenAiRateLimit rateLimit) {
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
		Assert.state(rateLimit != null, "OpenAI rate limit metadata was not provided");
		return rateLimit;
	}

	@Override
	public Usage getUsage() {
		Usage usage = this.usage;
		Assert.state(usage != null, "OpenAI usage metadata was not provided");
		return usage;
	}

	public OpenAiMetadata withRateLimit(RateLimit rateLimit) {
		this.rateLimit = rateLimit;
		return this;
	}

	@Override
	public String toString() {
		return AI_METADATA_STRING.formatted(getClass().getName(), getId(), getUsage(), getRateLimit());
	}

}
