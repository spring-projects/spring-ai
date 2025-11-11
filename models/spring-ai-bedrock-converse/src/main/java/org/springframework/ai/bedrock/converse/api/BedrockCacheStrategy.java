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

package org.springframework.ai.bedrock.converse.api;

/**
 * Defines the caching strategy for AWS Bedrock prompt caching. Bedrock allows up to 4
 * cache breakpoints per request, and the cache hierarchy follows the order: tools →
 * system → messages.
 *
 * <p>
 * Prompt caching reduces latency and costs by reusing previously processed prompt
 * content. Cached content has a 5-minute Time To Live (TTL) that resets with each cache
 * hit.
 *
 * @author Soby Chacko
 * @since 1.1.0
 * @see <a href=
 * "https://docs.aws.amazon.com/bedrock/latest/userguide/prompt-caching.html">AWS Bedrock
 * Prompt Caching</a>
 */
public enum BedrockCacheStrategy {

	/**
	 * No caching (default behavior). All content is processed fresh on each request.
	 * <p>
	 * Use this when:
	 * <ul>
	 * <li>Requests are one-off or highly variable</li>
	 * <li>Content doesn't meet minimum token requirements (1024+ tokens for most
	 * models)</li>
	 * <li>You want to avoid caching overhead</li>
	 * </ul>
	 */
	NONE,

	/**
	 * Cache system instructions only. Places a cache breakpoint on the system message
	 * content. Tools are cached implicitly via Bedrock's automatic ~20-block lookback
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
	 * <p>
	 * This is the recommended starting point for most use cases as it provides the best
	 * balance of simplicity and effectiveness.
	 */
	SYSTEM_ONLY,

	/**
	 * Cache tool definitions only. Places a cache breakpoint after the last tool
	 * definition. System messages and conversation history are not cached.
	 * <p>
	 * Use this when:
	 * <ul>
	 * <li>You have many tool definitions (20+ tools, 1024+ tokens total)</li>
	 * <li>Tools are stable but system prompts change frequently</li>
	 * <li>You want to cache tool schemas without caching system instructions</li>
	 * </ul>
	 * <p>
	 * <strong>Important Model Compatibility:</strong>
	 * <ul>
	 * <li><strong>Supported:</strong> Claude 3.x and Claude 4.x models (all
	 * variants)</li>
	 * <li><strong>Not Supported:</strong> Amazon Nova models (Nova Micro, Lite, Pro,
	 * Premier) - these models only support caching for system and messages, not
	 * tools</li>
	 * </ul>
	 * <p>
	 * If you use this strategy with an unsupported model, AWS will return a
	 * ValidationException. Use {@link #SYSTEM_ONLY} instead for Amazon Nova models.
	 * <p>
	 * <strong>Note:</strong> If no tools are present in the request, this strategy is
	 * equivalent to NONE (no caching occurs).
	 */
	TOOLS_ONLY,

	/**
	 * Cache both tool definitions and system instructions. Places two cache breakpoints:
	 * one after the last tool definition, and one after the last system message.
	 * <p>
	 * Use this when:
	 * <ul>
	 * <li>Both tools and system prompts are large and stable (1024+ tokens each)</li>
	 * <li>You want maximum cache coverage</li>
	 * <li>You're willing to use 2 of your 4 available cache breakpoints</li>
	 * </ul>
	 * <p>
	 * <strong>Important Model Compatibility:</strong>
	 * <ul>
	 * <li><strong>Supported:</strong> Claude 3.x and Claude 4.x models (all
	 * variants)</li>
	 * <li><strong>Not Supported:</strong> Amazon Nova models (Nova Micro, Lite, Pro,
	 * Premier) - these models only support caching for system and messages, not
	 * tools</li>
	 * </ul>
	 * <p>
	 * If you use this strategy with an unsupported model, AWS will return a
	 * ValidationException. Use {@link #SYSTEM_ONLY} instead for Amazon Nova models.
	 * <p>
	 * <strong>Cache Invalidation:</strong>
	 * <ul>
	 * <li>Changing tools invalidates both cache breakpoints (tools are the prefix)</li>
	 * <li>Changing system prompts only invalidates the system cache (tools remain
	 * cached)</li>
	 * </ul>
	 * <p>
	 * This provides the most comprehensive caching but uses more cache breakpoints.
	 */
	SYSTEM_AND_TOOLS,

	/**
	 * Cache the entire conversation history up to and including the current user
	 * question. This is ideal for multi-turn conversations where you want to reuse the
	 * conversation context while asking new questions.
	 * <p>
	 * A cache breakpoint is placed on the last user message in the conversation. This
	 * enables incremental caching where each conversation turn builds on the previous
	 * cached prefix, providing significant cost savings and performance improvements.
	 * <p>
	 * Use this when:
	 * <ul>
	 * <li>Building multi-turn conversational applications (chatbots, assistants)</li>
	 * <li>Conversation history is substantial (1024+ tokens)</li>
	 * <li>Users are asking follow-up questions that require context from earlier
	 * messages</li>
	 * <li>You want to reduce latency and costs for ongoing conversations</li>
	 * </ul>
	 * <p>
	 * <strong>Model Compatibility:</strong>
	 * <ul>
	 * <li><strong>Verified:</strong> Claude 3.x and Claude 4.x models (all variants)</li>
	 * <li><strong>Note:</strong> Amazon Nova models theoretically support conversation
	 * caching, but have not been verified in integration tests</li>
	 * </ul>
	 * <p>
	 * <strong>How it works:</strong>
	 * <ol>
	 * <li>Identifies the last user message in the conversation</li>
	 * <li>Places cache breakpoint as the last content block on that message</li>
	 * <li>All messages up to and including the last user message are cached (system,
	 * previous user/assistant turns, and current user question)</li>
	 * <li>On the next turn, the cached context is reused and a new cache is created
	 * including the assistant response and new user question</li>
	 * </ol>
	 * <p>
	 * <strong>Example conversation flow:</strong>
	 *
	 * <pre>
	 * Turn 1: "My name is Alice" → Response cached
	 * Turn 2: "I work as a data scientist" → Response cached
	 * Turn 3: "What career advice would you give me?" ← Cache applies here
	 *         (Turns 1-2 are read from cache, Turn 3 question is fresh)
	 * </pre>
	 * <p>
	 * <strong>Cache behavior:</strong>
	 * <ul>
	 * <li>First request: Creates cache (cacheWriteInputTokens &gt; 0)</li>
	 * <li>Subsequent requests: Reads from cache (cacheReadInputTokens &gt; 0)</li>
	 * <li>Cache TTL: 5 minutes (resets on each cache hit)</li>
	 * <li>Minimum content: 1024+ tokens required for caching to activate</li>
	 * </ul>
	 * <p>
	 */
	CONVERSATION_HISTORY

}
