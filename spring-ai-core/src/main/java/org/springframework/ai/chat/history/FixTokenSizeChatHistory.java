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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.util.CollectionUtils;

/**
 * @author Christian Tzolov
 */
public class FixTokenSizeChatHistory implements ChatHistory, AutoCloseable {

	static final Logger logger = LoggerFactory.getLogger(FixTokenSizeChatHistory.class);

	/**
	 * Temporal storage used to aggregate streaming messages until the finishReason=STOP
	 * is received.
	 */
	private final Map<String, CopyOnWriteArrayList<Message>> chatHistory;

	/**
	 * Streaming message aggregator used to aggregate streaming messages until the
	 * finishReason=STOP is received.
	 */
	private final StreamingMessageAggregator streamingMessageAggregator;

	/**
	 * Token encoding used to estimate the size of the message content.
	 */
	private final Encoding tokenEncoding;

	/**
	 * Maximum token size allowed in the chat history.
	 */
	private final int maxTokenSize;

	public FixTokenSizeChatHistory(int maxTokenSize) {
		this.maxTokenSize = maxTokenSize;
		this.tokenEncoding = Encodings.newLazyEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);
		this.chatHistory = new ConcurrentHashMap<>();
		this.streamingMessageAggregator = new StreamingMessageAggregator();
	}

	@Override
	public void add(String sessionId, List<Message> messages) {
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
			else if (message.getMessageType() == MessageType.USER || message.getMessageType() == MessageType.FUNCTION) {
				commitToHistoryLog(sessionId, message);
			}
			else if (message.getMessageType() == MessageType.SYSTEM) {
				logger.warn("DROP system message: " + message);
				// TODO: handle system messages
			}
			else {
				logger.warn("Ignore a non user, system or assistant message: " + message);
			}
		});

		this.rebalance(sessionId);
	}

	private void rebalance(String sessionId) {

		List<Message> sessionHistory = this.chatHistory.get(sessionId);

		int currentTokenSize = sessionHistory.stream()
			.map(message -> tokenEstimate(message.getContent()))
			.mapToInt(i -> i)
			.sum();

		CopyOnWriteArrayList<Message> newList = new CopyOnWriteArrayList<>();

		if (currentTokenSize > this.maxTokenSize) {

			int index = 0;

			while (index < sessionHistory.size() && currentTokenSize > this.maxTokenSize) {

				Message oldMessage = sessionHistory.get(index++);

				if (oldMessage.getMessageType() == MessageType.SYSTEM) {
					// retain system messages.
					newList.add(oldMessage);
				}
				int oldMessageTokenSize = tokenEstimate(oldMessage.getContent());

				currentTokenSize = currentTokenSize - oldMessageTokenSize;

				if (oldMessage instanceof AssistantMessage assistantMessage) {
					// If a tool call request is evicted then remove all related function
					// call response ¬messages.
					if (assistantMessage.isToolCallRequest()) {
						while (index < sessionHistory.size()
								&& sessionHistory.get(index).getMessageType() == MessageType.FUNCTION) {
							currentTokenSize = currentTokenSize - tokenEstimate(sessionHistory.get(index++));
						}
					}
				}
			}

			if (index >= sessionHistory.size()) {
				throw new IllegalStateException("Failed to rebalance the token size!");
			}

			// add the rest of the messages.
			newList.addAll(sessionHistory.subList(index, sessionHistory.size()));
			// replace the old list with the new one.
			this.chatHistory.put(sessionId, newList);
		}
	}

	private void commitToHistoryLog(String sessionId, Message message) {
		this.chatHistory.putIfAbsent(sessionId, new CopyOnWriteArrayList<>());
		this.chatHistory.get(sessionId).add(message);
	}

	private int tokenEstimate(String content) {
		return this.tokenEncoding.encode(content).size();
	}

	private int tokenEstimate(Message message) {
		int estimate = 0;

		estimate = estimate + tokenEstimate(message.getContent());

		if (!CollectionUtils.isEmpty(message.getMedia())) {
			estimate = estimate + message.getMedia().stream().mapToInt(media -> tokenEstimate(media.getData())).sum();
		}

		if (message instanceof AssistantMessage assistantMessage) {
			if (assistantMessage.isToolCallRequest()) {
				// TODO
			}
		}

		return estimate;
	}

	private int tokenEstimate(Object content) {
		if (content == null) {
			return 0;
		}
		if (content instanceof String) {
			return tokenEstimate((String) content);
		}
		if (content instanceof Message) {
			return tokenEstimate((Message) content);
		}
		if (content instanceof byte[]) {
			return ((byte[]) content).length;
		}
		throw new IllegalArgumentException("Unsupported content type: " + content.getClass());
	}

	@Override
	public List<Message> get(String sessionId) {
		return this.chatHistory.get(sessionId);
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
