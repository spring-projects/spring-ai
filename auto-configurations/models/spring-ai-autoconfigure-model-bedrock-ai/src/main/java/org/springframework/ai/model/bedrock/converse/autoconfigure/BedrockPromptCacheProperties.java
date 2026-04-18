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

package org.springframework.ai.model.bedrock.converse.autoconfigure;

import java.time.Duration;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.bedrock.converse.BedrockChatOptions;
import org.springframework.ai.chat.prompt.PromptCacheStrategy;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for portable prompt caching. These properties configure the
 * default prompt caching behavior across providers that support explicit cache control.
 *
 * <p>
 * Example configuration:
 *
 * <pre>
 * spring.ai.prompt-cache.strategy=system-and-tools
 * spring.ai.prompt-cache.ttl=5m
 * spring.ai.prompt-cache.min-content-length=1024
 * </pre>
 *
 * @author Soby Chacko
 * @since 2.0.0
 */
@ConfigurationProperties("spring.ai.prompt-cache")
public class BedrockPromptCacheProperties {

	private PromptCacheStrategy strategy = PromptCacheStrategy.NONE;

	private @Nullable Duration ttl;

	private @Nullable Integer minContentLength;

	public PromptCacheStrategy getStrategy() {
		return this.strategy;
	}

	public void setStrategy(PromptCacheStrategy strategy) {
		this.strategy = strategy;
	}

	public @Nullable Duration getTtl() {
		return this.ttl;
	}

	public void setTtl(@Nullable Duration ttl) {
		this.ttl = ttl;
	}

	public @Nullable Integer getMinContentLength() {
		return this.minContentLength;
	}

	public void setMinContentLength(@Nullable Integer minContentLength) {
		this.minContentLength = minContentLength;
	}

	/**
	 * Applies these cache properties to the given {@link BedrockChatOptions} instance.
	 * Does nothing if strategy is {@code NONE}.
	 * @param options the options to configure
	 */
	public void applyTo(BedrockChatOptions options) {
		if (this.strategy != PromptCacheStrategy.NONE) {
			options.setPromptCacheStrategy(this.strategy);
			if (this.ttl != null) {
				options.setPromptCacheTtl(this.ttl);
			}
			if (this.minContentLength != null) {
				options.setPromptCacheMinContentLength(this.minContentLength);
			}
		}
	}

}
