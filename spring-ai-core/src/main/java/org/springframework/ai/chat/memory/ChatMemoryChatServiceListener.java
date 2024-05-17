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

import java.util.List;

import org.springframework.ai.chat.service.ChatServiceResponse;
import org.springframework.ai.chat.service.ChatServiceListener;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.prompt.transformer.ChatServiceContext;
import org.springframework.ai.chat.prompt.transformer.TransformerContentType;

/**
 * @author Christian Tzolov
 */
public class ChatMemoryChatServiceListener implements ChatServiceListener {

	private final ChatMemory chatHistory;

	public ChatMemoryChatServiceListener(ChatMemory chatHistory) {
		this.chatHistory = chatHistory;
	}

	@Override
	public void onStart(ChatServiceContext chatServiceContext) {
		var messagesToAdd = chatServiceContext.getPrompt()
			.getInstructions()
			.stream()
			.filter(m -> !m.getMetadata().containsKey(TransformerContentType.MEMORY))
			.filter(m -> (m.getMessageType() == MessageType.ASSISTANT || m.getMessageType() == MessageType.USER))
			.toList();
		this.chatHistory.add(chatServiceContext.getConversationId(), messagesToAdd);
	}

	@Override
	public void onComplete(ChatServiceResponse chatServiceResponse) {
		List<Message> assistantMessages = chatServiceResponse.getChatResponse()
			.getResults()
			.stream()
			.map(g -> (Message) g.getOutput())
			.toList();
		this.chatHistory.add(chatServiceResponse.getPromptContext().getConversationId(), assistantMessages);
	}

}
