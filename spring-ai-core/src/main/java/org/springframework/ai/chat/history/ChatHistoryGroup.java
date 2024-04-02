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

import java.time.Instant;
import java.util.List;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;

/**
 * @author Christian Tzolov
 */
public class ChatHistoryGroup {

	private final String sessionId;

	private final List<Message> messages;

	private final long timestamp;

	public ChatHistoryGroup(String sessionId, List<Message> messages, long timestamp) {
		this.sessionId = sessionId;
		this.messages = messages;
		this.timestamp = timestamp;
	}

	public ChatHistoryGroup(String sessionId, List<Message> messages) {
		this(sessionId, messages, Instant.now().toEpochMilli());
	}

	public ChatHistoryGroup(String sessionId, AssistantMessage assistantMessage, long timestamp) {
		this.sessionId = sessionId;
		this.messages = List.of(assistantMessage);
		this.timestamp = timestamp;
	}

	public List<Message> getMessages() {
		return this.messages;
	}

	public String getSessionId() {
		return this.sessionId;
	}

	public long getTimestamp() {
		return this.timestamp;
	}

}
