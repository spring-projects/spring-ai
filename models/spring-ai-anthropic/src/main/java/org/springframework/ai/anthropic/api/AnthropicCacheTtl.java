/*
 * Copyright 2023-2025 the original author or authors.
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

/**
 * Anthropic cache TTL (time-to-live) options for specifying how long cached prompts See
 * the Anthropic documentation for more details: <a href=
 * "https://docs.anthropic.com/en/docs/build-with-claude/prompt-caching#1-hour-cache-duration">Anthropic
 * Prompt Caching</a>
 *
 * @author Austin Dase
 * @since 1.1.0
 **/
public enum AnthropicCacheTtl {

	FIVE_MINUTES("5m"), ONE_HOUR("1h");

	private final String value;

	AnthropicCacheTtl(String value) {
		this.value = value;
	}

	public String getValue() {
		return this.value;
	}

}
