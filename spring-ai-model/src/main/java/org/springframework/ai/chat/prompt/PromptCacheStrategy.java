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

package org.springframework.ai.chat.prompt;

/**
 * Strategies for prompt caching that are portable across providers supporting explicit
 * cache control (e.g., Anthropic, AWS Bedrock). Providers that use automatic caching
 * (e.g., OpenAI) or external cache lifecycle management (e.g., Google Gemini) will ignore
 * these strategies.
 *
 * @author Soby Chacko
 * @since 2.0.0
 * @see PromptCacheChatOptions
 */
public enum PromptCacheStrategy {

	/**
	 * No prompt caching. This is the default.
	 */
	NONE,

	/**
	 * Cache system instructions only. Places a cache breakpoint on the system message
	 * content. Best for applications with large, stable system prompts and varying user
	 * queries.
	 */
	SYSTEM_ONLY,

	/**
	 * Cache tool definitions only. Places a cache breakpoint on the last tool definition.
	 * Best for applications with many stable tools but dynamic system prompts (e.g.,
	 * multi-tenant scenarios).
	 */
	TOOLS_ONLY,

	/**
	 * Cache both system instructions and tool definitions. Places cache breakpoints on
	 * both the system message and the last tool definition. Best for applications with
	 * large, stable system prompts and many stable tools.
	 */
	SYSTEM_AND_TOOLS,

	/**
	 * Cache conversation history up to the last user message. Enables incremental caching
	 * as the conversation grows. Best for multi-turn conversations with stable system
	 * prompts and tools.
	 */
	CONVERSATION_HISTORY

}
