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
 * Defines the caching strategy for Anthropic prompt caching. Anthropic allows up to 4
 * cache breakpoints per request, and the cache hierarchy follows the order: tools →
 * system → messages.
 *
 * @author Mark Pollack
 * @since 1.1.0
 */
public enum AnthropicCacheStrategy {

	/**
	 * No caching (default behavior).
	 */
	NONE,

	/**
	 * Cache system instructions only. Places a cache breakpoint on the system message
	 * content.
	 */
	SYSTEM_ONLY,

	/**
	 * Cache system instructions and tool definitions. Places cache breakpoints on the
	 * last tool and system message content.
	 */
	SYSTEM_AND_TOOLS,

	/**
	 * Cache the entire conversation history up to (but not including) the current user
	 * question. This is ideal for multi-turn conversations where you want to reuse the
	 * conversation context while asking new questions.
	 */
	CONVERSATION_HISTORY

}
