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
 * @author Soby Chacko
 * @since 1.1.0
 */
public enum AnthropicCacheStrategy {

	/**
	 * No caching (default behavior). All content is processed fresh on each request.
	 * <p>
	 * Use this when:
	 * <ul>
	 * <li>Requests are one-off or highly variable</li>
	 * <li>Content doesn't meet minimum token requirements (1024+ tokens)</li>
	 * <li>You want to avoid caching overhead</li>
	 * </ul>
	 */
	NONE,

	/**
	 * Cache tool definitions only. Places a cache breakpoint on the last tool, while
	 * system messages and conversation history remain uncached and are processed fresh on
	 * each request.
	 * <p>
	 * Use this when:
	 * <ul>
	 * <li>Tool definitions are large and stable (5000+ tokens)</li>
	 * <li>System prompts change frequently or are small (&lt;500 tokens)</li>
	 * <li>You want to share cached tools across different system contexts (e.g.,
	 * multi-tenant applications, A/B testing system prompts)</li>
	 * <li>Tool definitions rarely change</li>
	 * </ul>
	 * <p>
	 * <strong>Important:</strong> Changing any tool definition will invalidate this cache
	 * entry. Due to Anthropic's cascade invalidation, tool changes will also invalidate
	 * any downstream cache breakpoints (system, messages) if used in combination with
	 * other strategies.
	 */
	TOOLS_ONLY,

	/**
	 * Cache system instructions only. Places a cache breakpoint on the system message
	 * content. Tools are cached implicitly via Anthropic's automatic ~20-block lookback
	 * mechanism (content before the cache breakpoint is included in the cache).
	 * <p>
	 * Use this when:
	 * <ul>
	 * <li>System prompts are large and stable (1024+ tokens)</li>
	 * <li>Tool definitions are relatively small (&lt;20 tools)</li>
	 * <li>You want simple, single-breakpoint caching</li>
	 * </ul>
	 * <p>
	 * <strong>Note:</strong> Changing tools will invalidate the cache since tools are
	 * part of the cache prefix (they appear before system in the request hierarchy).
	 */
	SYSTEM_ONLY,

	/**
	 * Cache system instructions and tool definitions. Places cache breakpoints on the
	 * last tool (breakpoint 1) and system message content (breakpoint 2).
	 * <p>
	 * Use this when:
	 * <ul>
	 * <li>Both tools and system prompts are large and stable</li>
	 * <li>You have many tools (20+ tools, beyond the automatic lookback window)</li>
	 * <li>You want deterministic, explicit caching of both components</li>
	 * <li>System prompts may change independently of tools</li>
	 * </ul>
	 * <p>
	 * <strong>Behavior:</strong>
	 * <ul>
	 * <li>If only tools change: Both caches invalidated (tools + system)</li>
	 * <li>If only system changes: Tools cache remains valid, system cache
	 * invalidated</li>
	 * </ul>
	 * This allows efficient reuse of tool cache when only system prompts are updated.
	 */
	SYSTEM_AND_TOOLS,

	/**
	 * Cache the entire conversation history up to (but not including) the current user
	 * question. Places a cache breakpoint on the last user message in the conversation
	 * history, enabling incremental caching as the conversation grows.
	 * <p>
	 * Use this when:
	 * <ul>
	 * <li>Building multi-turn conversational applications (chatbots, assistants)</li>
	 * <li>Conversation history is large and grows over time</li>
	 * <li>You want to reuse conversation context while asking new questions</li>
	 * <li>Using chat memory advisors or conversation persistence</li>
	 * </ul>
	 * <p>
	 * <strong>Behavior:</strong> Each turn builds on the previous cached prefix. The
	 * cache grows incrementally: Request 1 caches [Message1], Request 2 caches [Message1
	 * + Message2], etc. This provides significant cost savings (90%+) and performance
	 * improvements for long conversations.
	 * <p>
	 * <strong>Important:</strong> Changing tools or system prompts will invalidate the
	 * entire conversation cache due to cascade invalidation. Tool and system stability is
	 * critical for this strategy.
	 */
	CONVERSATION_HISTORY

}
