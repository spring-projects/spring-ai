/*
 * Copyright 2024-2024 the original author or authors.
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

package org.springframework.ai.chat.memory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.ai.chat.messages.Message;

/**
 * The InMemoryChatMemory class is an implementation of the ChatMemory interface that
 * represents an in-memory storage for chat conversation history.
 *
 * This class stores the conversation history in a ConcurrentHashMap, where the keys are
 * the conversation IDs and the values are lists of messages representing the conversation
 * history.
 *
 * @see ChatMemory
 * @author Christian Tzolov
 * @since 1.0.0 M1
 */
public class InMemoryChatMemory implements ChatMemory {

	Map<String, List<Message>> conversationHistory = new ConcurrentHashMap<>();

	@Override
	public void add(String conversationId, List<Message> messages) {
		this.conversationHistory.putIfAbsent(conversationId, new ArrayList<>());
		this.conversationHistory.get(conversationId).addAll(messages);
	}

	@Override
	public List<Message> get(String conversationId, int lastN) {
		List<Message> all = this.conversationHistory.get(conversationId);
		return all != null ? all.stream().skip(Math.max(0, all.size() - lastN)).toList() : List.of();
	}

	@Override
	public void clear(String conversationId) {
		this.conversationHistory.remove(conversationId);
	}

}