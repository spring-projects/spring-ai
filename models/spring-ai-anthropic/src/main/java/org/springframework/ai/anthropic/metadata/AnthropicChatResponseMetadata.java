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
package org.springframework.ai.anthropic.metadata;

import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.EmptyRateLimit;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.RateLimit;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import java.util.HashMap;

/**
 * {@link ChatResponseMetadata} implementation for {@literal AnthropicApi}.
 *
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @see ChatResponseMetadata
 * @see RateLimit
 * @see Usage
 * @since 1.0.0
 */
public class AnthropicChatResponseMetadata extends HashMap<String, Object> implements ChatResponseMetadata {

	protected static final String AI_METADATA_STRING = "{ @type: %1$s, id: %2$s, model: %3$s, usage: %4$s, rateLimit: %5$s }";

	public static AnthropicChatResponseMetadata from(AnthropicApi.ChatCompletionResponse result) {
		Assert.notNull(result, "Anthropic ChatCompletionResult must not be null");
		AnthropicUsage usage = AnthropicUsage.from(result.usage());
		return new AnthropicChatResponseMetadata(result.id(), result.model(), usage);
	}

	private final String id;

	private final String model;

	@Nullable
	private RateLimit rateLimit;

	private final Usage usage;

	protected AnthropicChatResponseMetadata(String id, String model, AnthropicUsage usage) {
		this(id, model, usage, null);
	}

	protected AnthropicChatResponseMetadata(String id, String model, AnthropicUsage usage,
			@Nullable AnthropicRateLimit rateLimit) {
		this.id = id;
		this.model = model;
		this.usage = usage;
		this.rateLimit = rateLimit;
	}

	@Override
	public String getId() {
		return this.id;
	}

	@Override
	public String getModel() {
		return this.model;
	}

	@Override
	@Nullable
	public RateLimit getRateLimit() {
		RateLimit rl = this.rateLimit;
		return rl != null ? rl : new EmptyRateLimit();
	}

	@Override
	public Usage getUsage() {
		Usage usage = this.usage;
		return usage != null ? usage : new EmptyUsage();
	}

	public AnthropicChatResponseMetadata withRateLimit(RateLimit rateLimit) {
		this.rateLimit = rateLimit;
		return this;
	}

	@Override
	public String toString() {
		return AI_METADATA_STRING.formatted(getClass().getName(), getId(), getModel(), getUsage(), getRateLimit());
	}

}
