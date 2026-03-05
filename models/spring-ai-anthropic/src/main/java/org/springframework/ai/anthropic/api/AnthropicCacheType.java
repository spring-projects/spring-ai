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

package org.springframework.ai.anthropic.api;

import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.anthropic.api.AnthropicApi.ChatCompletionRequest.CacheControl;

/**
 * Cache types supported by Anthropic's prompt caching feature.
 *
 * <p>
 * Prompt caching allows reusing frequently used prompts to reduce costs and improve
 * response times for repeated interactions.
 *
 * @see <a href=
 * "https://docs.anthropic.com/en/docs/build-with-claude/prompt-caching">Anthropic Prompt
 * Caching</a>
 * @author Claudio Silva Junior
 * @author Soby Chacko
 * @author Austin Dase
 */
public enum AnthropicCacheType {

	/**
	 * Ephemeral cache with 5-minute lifetime, refreshed on each use.
	 */
	EPHEMERAL(ttl -> new CacheControl("ephemeral", ttl));

	private final Function<@Nullable String, CacheControl> value;

	AnthropicCacheType(Function<@Nullable String, CacheControl> value) {
		this.value = value;
	}

	/**
	 * Returns a new CacheControl instance for this cache type.
	 * @return a CacheControl instance configured for this cache type
	 */
	public CacheControl cacheControl() {
		return this.value.apply(null);
	}

	/**
	 * Returns a new CacheControl instance for this cache type with the specified TTL.
	 * @param ttl the time-to-live for the cache entry (e.g., "5m" for 5 minutes, "1h" for
	 * 1 hour)
	 * @return a CacheControl instance configured for this cache type and TTL
	 */
	public CacheControl cacheControl(String ttl) {
		return this.value.apply(ttl);
	}

}
