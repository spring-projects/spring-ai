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
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.messages.AssistantMessage;

/**
 * Utility to aggregate streaming message response into a single message to log in the
 * history.
 *
 * @author Christian Tzolov
 * @since 1.0.0
 */
public class StreamingMessageAggregator {

	static final Logger logger = LoggerFactory.getLogger(StreamingMessageAggregator.class);

	/**
	 * Temporal storage used to aggregate streaming messages until the finishReason=STOP
	 * is received.
	 */
	private final Map<String, List<AssistantMessage>> messageAggregations;

	public StreamingMessageAggregator() {
		this.messageAggregations = new ConcurrentHashMap<>();
	}

	public StreamingMessageAggregator(Map<String, List<AssistantMessage>> messageAggregations) {
		this.messageAggregations = messageAggregations;
	}

	public AssistantMessage addStreamingMessage(String sessionId, AssistantMessage message) {

		String aggregationId = getAggregationId(sessionId, message);

		this.messageAggregations.putIfAbsent(aggregationId, new ArrayList<>());

		if (this.messageAggregations.keySet().size() > 1) {
			// This might not be a real issue given parallel access.
			logger.warn("Multiple active sessions: " + this.messageAggregations.keySet());
		}

		this.messageAggregations.get(aggregationId).add(message);

		if (message.isCompleted()) {
			return this.finalizeAggregation(aggregationId);
		}

		return null;
	}

	private String getAggregationId(String sessionId, AssistantMessage message) {
		return sessionId + ":" + message.getId();
	}

	private AssistantMessage finalizeAggregation(String aggregationId) {

		if (!this.messageAggregations.containsKey(aggregationId)) {
			throw new IllegalStateException("No active session for groupId: " + aggregationId);
		}

		List<AssistantMessage> sessionMessages = this.messageAggregations.get(aggregationId);
		this.messageAggregations.remove(aggregationId);

		var firstMessage = sessionMessages.get(0);
		if (sessionMessages.size() == 1) {
			return firstMessage;
		}

		String aggregatedContent = sessionMessages.stream()
			.filter(m -> m.getContent() != null)
			.map(m -> m.getContent())
			.collect(Collectors.joining());

		return new AssistantMessage(firstMessage.getId(), 0, true, aggregatedContent, Map.of());
	}

}