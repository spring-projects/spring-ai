/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.model.anthropic.autoconfigure;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.anthropic.AnthropicCacheOptions;
import org.springframework.ai.anthropic.AnthropicCacheStrategy;
import org.springframework.ai.anthropic.AnthropicCacheTtl;
import org.springframework.ai.chat.messages.MessageType;

public class AnthropicCacheProperties {

	private @Nullable AnthropicCacheStrategy strategy;

	private @Nullable Map<MessageType, AnthropicCacheTtl> messageTypeTtl;

	private @Nullable Map<MessageType, Integer> messageTypeMinContentLengths;

	private @Nullable Boolean multiBlockSystemCaching;

	public @Nullable AnthropicCacheStrategy getStrategy() {
		return this.strategy;
	}

	public void setStrategy(@Nullable AnthropicCacheStrategy strategy) {
		this.strategy = strategy;
	}

	public @Nullable Map<MessageType, AnthropicCacheTtl> getMessageTypeTtl() {
		return this.messageTypeTtl;
	}

	public void setMessageTypeTtl(@Nullable Map<MessageType, AnthropicCacheTtl> messageTypeTtl) {
		this.messageTypeTtl = messageTypeTtl;
	}

	public @Nullable Map<MessageType, Integer> getMessageTypeMinContentLengths() {
		return this.messageTypeMinContentLengths;
	}

	public void setMessageTypeMinContentLengths(@Nullable Map<MessageType, Integer> messageTypeMinContentLengths) {
		this.messageTypeMinContentLengths = messageTypeMinContentLengths;
	}

	public @Nullable Boolean getMultiBlockSystemCaching() {
		return this.multiBlockSystemCaching;
	}

	public void setMultiBlockSystemCaching(@Nullable Boolean multiBlockSystemCaching) {
		this.multiBlockSystemCaching = multiBlockSystemCaching;
	}

	public AnthropicCacheOptions toOptions() {
		return AnthropicCacheOptions.builder()
			.strategy(this.strategy)
			.messageTypeTtl(this.messageTypeTtl)
			.messageTypeMinContentLengths(this.messageTypeMinContentLengths)
			.multiBlockSystemCaching(this.multiBlockSystemCaching)
			.build();
	}

}
