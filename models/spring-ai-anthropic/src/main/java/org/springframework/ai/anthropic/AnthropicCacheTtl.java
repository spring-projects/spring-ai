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

package org.springframework.ai.anthropic;

import com.anthropic.models.messages.CacheControlEphemeral;

/**
 * Anthropic cache TTL (time-to-live) options for specifying how long cached prompts
 * remain valid. Wraps the SDK's {@link CacheControlEphemeral.Ttl} enum values.
 *
 * @author Austin Dase
 * @author Soby Chacko
 * @since 1.1.0
 * @deprecated since 2.0.0-M5, use
 * {@link org.springframework.ai.chat.prompt.PromptCacheChatOptions#getPromptCacheTtl()}
 * or
 * {@link org.springframework.ai.chat.prompt.PromptCacheChatOptions#getPromptCacheMessageTypeTtl()}
 * instead.
 * @see <a href=
 * "https://docs.anthropic.com/en/docs/build-with-claude/prompt-caching#1-hour-cache-duration">Anthropic
 * Prompt Caching</a>
 */
@Deprecated(since = "2.0.0-M5", forRemoval = true)
public enum AnthropicCacheTtl {

	FIVE_MINUTES(CacheControlEphemeral.Ttl.TTL_5M),

	ONE_HOUR(CacheControlEphemeral.Ttl.TTL_1H);

	private final CacheControlEphemeral.Ttl sdkTtl;

	AnthropicCacheTtl(CacheControlEphemeral.Ttl sdkTtl) {
		this.sdkTtl = sdkTtl;
	}

	public CacheControlEphemeral.Ttl getSdkTtl() {
		return this.sdkTtl;
	}

}
