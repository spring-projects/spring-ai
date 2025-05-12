/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.chat.client.advisor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

/**
 * Memory is retrieved added as a collection of messages to the prompt
 *
 * @author Christian Tzolov
 * @author Mark Pollack
 * @since 1.0.0
 */
public class MessageChatMemoryAdvisor extends AbstractChatMemoryAdvisor<ChatMemory> {

	private static final Logger logger = LoggerFactory.getLogger(MessageChatMemoryAdvisor.class);

	private MessageChatMemoryAdvisor(ChatMemory chatMemory, String defaultConversationId, int order) {
		super(chatMemory, defaultConversationId, true, order);
	}

	@Override
	public ChatClientRequest before(ChatClientRequest request, AdvisorChain advisorChain) {
		String conversationId = doGetConversationId(request.context());
		// Add the new user messages from the current prompt to memory
		List<UserMessage> newUserMessages = request.prompt().getUserMessages();
		for (UserMessage userMessage : newUserMessages) {
			this.getChatMemoryStore().add(conversationId, userMessage);
		}
		List<Message> memoryMessages = chatMemoryStore.get(conversationId);
		return applyMessagesToRequest(request, memoryMessages);
	}

	protected String doGetConversationId(Map<String, Object> context) {
		if (context == null || !context.containsKey(ChatMemory.CHAT_MEMORY_CONVERSATION_ID_KEY)) {
			logger.warn("No conversation ID found in context; using defaultConversationId '{}'.",
					this.defaultConversationId);
		}
		return context != null && context.containsKey(ChatMemory.CHAT_MEMORY_CONVERSATION_ID_KEY)
				? context.get(ChatMemory.CHAT_MEMORY_CONVERSATION_ID_KEY).toString() : this.defaultConversationId;
	}

	private ChatClientRequest applyMessagesToRequest(ChatClientRequest request, List<Message> memoryMessages) {
		if (memoryMessages == null || memoryMessages.isEmpty()) {
			return request;
		}
		// Combine memory messages with the instructions from the current prompt
		List<Message> combinedMessages = new ArrayList<>(memoryMessages);
		combinedMessages.addAll(request.prompt().getInstructions());

		// Mutate the prompt to use the combined messages
		// insead of combiedMinessage from the logic above
		// request.prompt().mutate().messages(chatMemoryStore.get(conversationId););
		var promptBuilder = request.prompt().mutate().messages(combinedMessages);

		// Return a new ChatClientRequest with the updated prompt
		return request.mutate().prompt(promptBuilder.build()).build();
	}

	@Override
	public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
		List<Message> assistantMessages = new ArrayList<>();
		if (chatClientResponse.chatResponse() != null) {
			assistantMessages = chatClientResponse.chatResponse()
				.getResults()
				.stream()
				.map(g -> (Message) g.getOutput())
				.toList();
		}
		this.getChatMemoryStore().add(this.doGetConversationId(chatClientResponse.context()), assistantMessages);
		return chatClientResponse;
	}

	public static Builder builder(ChatMemory chatMemory) {
		return new Builder(chatMemory);
	}

	public static class Builder {

		private String conversationId = ChatMemory.DEFAULT_CONVERSATION_ID;

		private boolean protectFromBlocking = true;

		private int order = Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER;

		private ChatMemory chatMemory;

		protected Builder(ChatMemory chatMemory) {
			this.chatMemory = chatMemory;
		}

		/**
		 * Set the conversation id.
		 * @param conversationId the conversation id
		 * @return the builder
		 */
		public Builder conversationId(String conversationId) {
			this.conversationId = conversationId;
			return this;
		}

		/**
		 * Set whether to protect from blocking.
		 * @param protectFromBlocking whether to protect from blocking
		 * @return the builder
		 */
		public Builder protectFromBlocking(boolean protectFromBlocking) {
			this.protectFromBlocking = protectFromBlocking;
			return this;
		}

		/**
		 * Set the order.
		 * @param order the order
		 * @return the builder
		 */
		public Builder order(int order) {
			this.order = order;
			return this;
		}

		/**
		 * Build the advisor.
		 * @return the advisor
		 */
		public MessageChatMemoryAdvisor build() {
			return new MessageChatMemoryAdvisor(this.chatMemory, this.conversationId, this.order);
		}

	}

}
