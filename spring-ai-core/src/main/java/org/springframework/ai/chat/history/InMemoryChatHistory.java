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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

/**
 * Implements a sliding window history, returning only the most recent messages in the
 * provided window size.
 *
 * @author Christian Tzolov
 * @since 1.0.0
 */
public class InMemoryChatHistory implements ChatHistory, AutoCloseable {

	static final Logger logger = LoggerFactory.getLogger(InMemoryChatHistory.class);

	/**
	 * Temporal storage used to aggregate streaming messages until the finishReason=STOP
	 * is received.
	 */
	private final Map<String, List<Message>> chatHistory;

	/**
	 * The number of messages to return when the get method is called.
	 */
	private final int windowSize;

	private final StreamingMessageAggregator streamingMessageAggregator;

	public InMemoryChatHistory(int returnWindowSize) {
		Assert.isTrue(returnWindowSize > 0, "The returnWindowSize must be greater than 0!");
		this.chatHistory = new ConcurrentHashMap<>();
		this.windowSize = returnWindowSize;
		this.streamingMessageAggregator = new StreamingMessageAggregator();
	}

	@Override
	public void add(String sessionId, List<Message> messages) {

		// TODO: Manage the system messages retention.
		// - The sys messages should not be purged and always returned in the window?
		// - Handle multiple sys messages in the history:
		// (1) Return all, (2) Merge, (3) Replace or (4) Drop?

		if (CollectionUtils.isEmpty(messages)) {
			return;
		}

		messages.stream().forEach(message -> {
			if (message.getMessageType() == MessageType.ASSISTANT) {
				Message aggregatedMessage = this.streamingMessageAggregator.addStreamingMessage(sessionId,
						(AssistantMessage) message);
				if (aggregatedMessage != null) {
					commitToHistoryLog(sessionId, message);
				}
			}
			else if (message.getMessageType() == MessageType.USER) {
				commitToHistoryLog(sessionId, message);
			}
			else if (message.getMessageType() == MessageType.SYSTEM) {
				logger.warn("DROP system message: " + message);
			}
			else {
				logger.warn("Ignore a non user, system or assistant message: " + message);
			}
		});
	}

	private void commitToHistoryLog(String sessionId, Message message) {
		this.chatHistory.putIfAbsent(sessionId, new ArrayList<>());
		this.chatHistory.get(sessionId).add(message);
	}

	@Override
	public List<Message> get(String sessionId) {
		if (!this.chatHistory.containsKey(sessionId)) {
			return List.of();
		}
		List<Message> messageList = this.chatHistory.get(sessionId);
		if (this.chatHistory.get(sessionId).size() < this.windowSize) {
			return messageList;
		}

		int from = messageList.size() - this.windowSize;
		int to = messageList.size();
		logger.debug("Returning last {} messages from {} to {}", this.windowSize, from, to);

		List<Message> responseWindow = messageList.subList(from, to);
		logger.debug("Returning last {} messages: {}", this.windowSize, responseWindow);

		// Purging the outdated messages if the history size exceed N*(windows size).
		// TODO: The purge trigger should be configurable.
		if (from > 2 * this.windowSize) {
			this.chatHistory.put(sessionId, responseWindow);
		}

		return responseWindow;
	}

	@Override
	public void clearSession(String sessionId) {
		this.chatHistory.remove(sessionId);
	}

	@Override
	public void close() throws Exception {
		this.chatHistory.clear();
	}

}
