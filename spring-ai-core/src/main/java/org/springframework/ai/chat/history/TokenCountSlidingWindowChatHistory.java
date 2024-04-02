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
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.messages.Media;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.util.CollectionUtils;

/**
 * In-memory chat history implementation that uses a token count based sliding window to
 * limit the size of the chat history.
 *
 * @author Christian Tzolov
 * @since 1.0.0
 */
public class TokenCountSlidingWindowChatHistory implements ChatHistory, AutoCloseable {

	static final Logger logger = LoggerFactory.getLogger(TokenCountSlidingWindowChatHistory.class);

	/**
	 * Chat history storage.
	 */
	protected final ConcurrentHashMap<String, List<Message>> chatHistory;

	/**
	 * Token encoding used to estimate the size of the message content.
	 */
	protected final Encoding tokenEncoding;

	/**
	 * Maximum token size allowed in the chat history.
	 */
	private final int maxTokenSize;

	public TokenCountSlidingWindowChatHistory(int maxTokenSize) {
		this(Encodings.newLazyEncodingRegistry().getEncoding(EncodingType.CL100K_BASE), maxTokenSize);
	}

	public TokenCountSlidingWindowChatHistory(Encoding tokenEncoding, int maxTokenSize) {
		this.tokenEncoding = tokenEncoding;
		this.maxTokenSize = maxTokenSize;
		this.chatHistory = new ConcurrentHashMap<>();
	}

	@Override
	public void add(String sessionId, Message message) {

		if (message == null) {
			return;
		}

		List<Message> sessionMessages = this.get(sessionId);

		if (message.getMessageType() == MessageType.SYSTEM) {
			Optional<Message> existingSystemMessage = this.findSystem(sessionMessages);
			if (existingSystemMessage.isPresent()) {
				if (!message.equals(existingSystemMessage.get())) {
					sessionMessages.remove(existingSystemMessage.get());
					sessionMessages.add(message);
				}
			}
			else {
				sessionMessages.add(message);
			}
		}
		else {
			sessionMessages.add(message);
		}

		sessionMessages = this.purgeExcess(sessionMessages);

		this.update(sessionId, sessionMessages);
	}

	private void update(String sessionId, List<Message> sessionMessages) {
		this.chatHistory.put(sessionId, sessionMessages);
	}

	@Override
	public void clear(String sessionId) {
		this.chatHistory.remove(sessionId);
	}

	@Override
	public List<Message> get(String sessionId) {
		this.chatHistory.putIfAbsent(sessionId, new ArrayList<>());
		return new ArrayList<>(this.chatHistory.get(sessionId));
	}

	protected List<Message> purgeExcess(List<Message> sessionMessages) {

		int totalSize = this.estimateTokenCount(sessionMessages);

		if (totalSize <= this.maxTokenSize) {
			return sessionMessages;
		}

		int index = 0;
		List<Message> newList = new ArrayList<>();

		while (index < sessionMessages.size() && totalSize > this.maxTokenSize) {
			Message oldMessage = sessionMessages.get(index++);

			int oldMessageTokenSize = estimateTokenCount(oldMessage);

			if (oldMessage.getMessageType() == MessageType.SYSTEM) {
				// retain system messages.
				newList.add(oldMessage);
			}
			else {
				totalSize = totalSize - oldMessageTokenSize;
			}
		}

		if (index >= sessionMessages.size()) {
			throw new IllegalStateException("Failed to rebalance the token size!");
		}

		// add the rest of the messages.
		newList.addAll(sessionMessages.subList(index, sessionMessages.size()));

		return newList;
	}

	protected Optional<Message> findSystem(List<Message> sessionMessages) {
		for (Message message : sessionMessages) {
			if (message.getMessageType() == MessageType.SYSTEM) {
				return Optional.of(message);
			}
		}
		return Optional.empty();
	}

	protected int estimateTokenCount(String text) {
		if (text == null) {
			return 0;
		}
		return this.tokenEncoding.countTokens(text);
	}

	protected int estimateTokenCount(List<Message> sessionMessages) {
		if (CollectionUtils.isEmpty(sessionMessages)) {
			return 0;
		}

		int totalSize = 0;
		for (Message message : sessionMessages) {
			totalSize += this.estimateTokenCount(message);
		}
		return totalSize;
	}

	@Override
	public void close() {
		this.chatHistory.clear();
	}

	public int estimateTokenCount(Message message) {
		int tokenCount = 0;

		if (message.getContent() != null) {
			tokenCount += this.estimateTokenCount(message.getContent());
		}

		if (!CollectionUtils.isEmpty(message.getMedia())) {

			for (Media media : message.getMedia()) {

				tokenCount += this.estimateTokenCount(media.getMimeType().toString());

				if (media.getData() instanceof String textData) {
					tokenCount += this.estimateTokenCount(textData);
				}
				else if (media.getData() instanceof byte[] binaryData) {
					tokenCount += binaryData.length; // This is likely incorrect.
				}
			}
		}

		return tokenCount;
	}

}