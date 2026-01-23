/*
 * Copyright 2024-2025 the original author or authors.
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

package org.springframework.ai.chat.client.advisor.api;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.util.Assert;

/**
 * Base interface for chat memory advisors.
 *
 * @author Mark Pollack
 * @author Thomas Vitale
 * @since 1.0
 */
public interface BaseChatMemoryAdvisor extends BaseAdvisor {

	/**
	 * Retrieve the conversation ID from the given context or return the default
	 * conversation ID when not found.
	 */
	default String getConversationId(Map<String, @Nullable Object> context, String defaultConversationId) {
		Assert.notNull(context, "context cannot be null");
		Assert.noNullElements(context.keySet().toArray(), "context cannot contain null keys");
		Assert.hasText(defaultConversationId, "defaultConversationId cannot be null or empty");
		return context.containsKey(ChatMemory.CONVERSATION_ID) ? context.get(ChatMemory.CONVERSATION_ID).toString()
				: defaultConversationId;
	}

}
