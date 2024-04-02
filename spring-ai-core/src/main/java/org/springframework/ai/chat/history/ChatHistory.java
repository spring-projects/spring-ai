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

package org.springframework.ai.chat.history;

import java.util.List;

import org.springframework.ai.chat.messages.Message;
import org.springframework.util.CollectionUtils;

/**
 * Core abstraction for storing and retrieving chat history.
 *
 * @author Christian Tzolov
 */
public interface ChatHistory {

	/**
	 * Add a new message to the chat history.
	 * @param sessionId history session id.
	 * @param message the message to add.
	 */
	void add(String sessionId, Message message);

	/**
	 * Add a list of messages to the chat history.
	 * @param sessionId history session id.
	 * @param messages the list of messages to add.
	 */
	default void add(String sessionId, List<Message> messages) {
		if (!CollectionUtils.isEmpty(messages)) {
			for (Message message : messages) {
				add(sessionId, message);
			}
		}
	}

	/**
	 * Retrieve the chat history for the given session id.
	 * @param sessionId history session id.
	 * @return the list of messages for the given session id.
	 */
	List<Message> get(String sessionId);

	/**
	 * Clear the chat history for the given session id.
	 * @param sessionId history session id.
	 */
	void clear(String sessionId);

}
