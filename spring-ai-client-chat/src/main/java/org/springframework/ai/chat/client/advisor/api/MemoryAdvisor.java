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

package org.springframework.ai.chat.client.advisor.api;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * An advisor that owns the memory lifecycle: it stores, retrieves and updates the
 * conversation a request belongs to, named by {@link #CONVERSATION_ID}.
 *
 * <p>
 * {@link org.springframework.ai.chat.client.DefaultChatClient} uses this marker to detect
 * whether a memory advisor is already present in the chain, and to avoid auto-registering
 * a duplicate when memory management is configured on the {@code ChatClient}.
 *
 * @author Christian Tzolov
 * @since 2.0.0
 * @see org.springframework.ai.chat.client.advisor.api.BaseChatMemoryAdvisor
 */
public interface MemoryAdvisor extends Advisor {

	/**
	 * The advisor context key of the conversation a request belongs to. Memory advisors
	 * read it to find the conversation or session, and the {@code ChatClient} observation
	 * records it. Set with
	 * {@code .advisors(a -> a.param(MemoryAdvisor.CONVERSATION_ID, conversationId))}.
	 */
	String CONVERSATION_ID = "chat_memory_conversation_id";

	/**
	 * Retrieve the conversation ID from the given context.
	 */
	default String getConversationId(Map<String, @Nullable Object> context) {
		Assert.notNull(context, "context cannot be null");
		Assert.noNullElements(context.keySet().toArray(), "context cannot contain null keys");
		Assert.notNull(context.get(CONVERSATION_ID), "conversationId cannot be null");
		return context.get(CONVERSATION_ID).toString();
	}

}
