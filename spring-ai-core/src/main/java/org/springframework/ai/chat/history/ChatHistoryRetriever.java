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

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;

import org.springframework.ai.chat.messages.Media;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.util.CollectionUtils;

/**
 * @author Christian Tzolov
 */
public class ChatHistoryRetriever {

	private final ChatHistory2 chatHistory;

	/**
	 * Token encoding used to estimate the size of the message content.
	 */
	protected final Encoding tokenEncoding;

	/**
	 * Maximum token size allowed in the chat history.
	 */
	private final int maxTokenSize;

	public ChatHistoryRetriever(ChatHistory2 chatHistory, int maxTokenSize) {
		this(chatHistory, Encodings.newLazyEncodingRegistry().getEncoding(EncodingType.CL100K_BASE), maxTokenSize);
	}

	public ChatHistoryRetriever(ChatHistory2 chatHistory, Encoding tokenEncoding, int maxTokenSize) {
		this.chatHistory = chatHistory;
		this.tokenEncoding = tokenEncoding;
		this.maxTokenSize = maxTokenSize;
	}

	public List<ChatMessages> retrieve(String sessionId) {

		List<ChatMessages> nonSystemChatMessages = (chatHistory.get(sessionId) != null) ? chatHistory.get(sessionId)
			.stream()
			.map(g -> new ChatMessages(sessionId,
					g.getMessages().stream().filter(m -> m.getMessageType() != MessageType.SYSTEM).toList()))
			.toList() : List.of();

		var flatMessages = nonSystemChatMessages.stream().map(g -> g.getMessages()).flatMap(List::stream).toList();

		int totalSize = this.estimateTokenCount(flatMessages);

		if (totalSize <= this.maxTokenSize) {
			return nonSystemChatMessages;
		}

		List<ChatMessages> newChatMessages = new ArrayList<>();

		for (ChatMessages chatMessage : nonSystemChatMessages) {
			List<Message> sessionMessages = chatMessage.getMessages();
			List<Message> newSessionMessages = this.purgeExcess2(sessionMessages, totalSize);
			if (!CollectionUtils.isEmpty(newSessionMessages)) {
				newChatMessages.add(new ChatMessages(chatMessage.getSessionId(), newSessionMessages));
			}
		}
		return newChatMessages;
	}

	protected List<Message> purgeExcess2(List<Message> sessionMessages, int totalSize) {

		int index = 0;
		List<Message> newList = new ArrayList<>();

		while (index < sessionMessages.size() && totalSize > this.maxTokenSize) {
			Message oldMessage = sessionMessages.get(index++);
			int oldMessageTokenSize = estimateTokenCount(oldMessage);
			totalSize = totalSize - oldMessageTokenSize;
		}

		if (index >= sessionMessages.size()) {
			return List.of();
		}

		// add the rest of the messages.
		newList.addAll(sessionMessages.subList(index, sessionMessages.size()));

		return newList;
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

	protected int estimateTokenCount(String text) {
		if (text == null) {
			return 0;
		}
		return this.tokenEncoding.countTokens(text);
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
