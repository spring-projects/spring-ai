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

package org.springframework.ai.anthropicsdk;

/**
 * Cache strategy options for Anthropic prompt caching. Prompt caching allows reusing
 * computed context across multiple API calls, reducing latency and cost for repeated
 * content.
 *
 * <p>
 * Anthropic supports caching for:
 * <ul>
 * <li>System prompts</li>
 * <li>Tool definitions</li>
 * <li>User message content (for conversation history)</li>
 * </ul>
 *
 * <p>
 * Note: Anthropic limits cache breakpoints to a maximum of 4 per request.
 *
 * @author Soby Chacko
 * @since 1.0.0
 * @see <a href=
 * "https://docs.anthropic.com/en/docs/build-with-claude/prompt-caching">Anthropic Prompt
 * Caching</a>
 */
public enum AnthropicSdkCacheStrategy {

	/**
	 * Caching is disabled. No cache control headers will be added to the request.
	 */
	DISABLED,

	/**
	 * Cache the system prompt only. Useful for applications with a constant system prompt
	 * across multiple requests.
	 */
	SYSTEM_PROMPT,

	/**
	 * Cache tool definitions only. Useful when using the same set of tools across
	 * multiple requests.
	 */
	TOOLS,

	/**
	 * Cache both system prompt and tool definitions.
	 */
	SYSTEM_AND_TOOLS,

	/**
	 * Cache the last user message in the conversation. Useful for multi-turn
	 * conversations where the history is reused.
	 */
	LAST_USER_MESSAGE,

	/**
	 * Cache system prompt, tools, and the last user message. This is the most aggressive
	 * caching strategy and uses 3 of the 4 available cache breakpoints.
	 */
	ALL

}
