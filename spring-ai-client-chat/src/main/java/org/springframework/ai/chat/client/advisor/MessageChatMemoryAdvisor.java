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

import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.MessageAggregator;

/**
 * Memory is retrieved added as a collection of messages to the prompt
 *
 * @author Christian Tzolov
 * @since 1.0.0
 */
public class MessageChatMemoryAdvisor extends AbstractConversationHistoryAdvisor {

	public MessageChatMemoryAdvisor(ChatMemory chatMemory) {
		super(chatMemory);
	}

	public MessageChatMemoryAdvisor(ChatMemory chatMemory, String defaultConversationId) {
		this(chatMemory, defaultConversationId, Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER);
	}

	public MessageChatMemoryAdvisor(ChatMemory chatMemory, String defaultConversationId, int order) {
		super(chatMemory, defaultConversationId, true, order);
	}

	public static Builder builder(ChatMemory chatMemory) {
		return new Builder(chatMemory);
	}

	@Override
	public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
		chatClientRequest = this.before(chatClientRequest);

		ChatClientResponse chatClientResponse = callAdvisorChain.nextCall(chatClientRequest);

		this.after(chatClientResponse);

		return chatClientResponse;
	}

	@Override
	public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest,
			StreamAdvisorChain streamAdvisorChain) {
		Flux<ChatClientResponse> chatClientResponses = this.doNextWithProtectFromBlockingBefore(chatClientRequest,
				streamAdvisorChain, this::before);

		return new MessageAggregator().aggregateChatClientResponse(chatClientResponses, this::after);
	}

	@Override
	protected ChatClientRequest before(ChatClientRequest request) {
		String conversationId = this.doGetConversationId(request.context());
		
		// Add the new user messages from the current prompt to memory
		List<UserMessage> newUserMessages = request.prompt().getUserMessages();
		for (UserMessage userMessage : newUserMessages) {
			this.getChatMemoryStore().add(conversationId, userMessage);
		}
		
		// Use the parent class implementation to handle retrieving and applying messages
		return super.before(request);
	}

	@Override
	protected void after(ChatClientResponse chatClientResponse) {
		List<Message> assistantMessages = new ArrayList<>();
		if (chatClientResponse.chatResponse() != null) {
			assistantMessages = chatClientResponse.chatResponse()
				.getResults()
				.stream()
				.map(g -> (Message) g.getOutput())
				.toList();
		}
		this.getChatMemoryStore().add(this.doGetConversationId(chatClientResponse.context()), assistantMessages);
	}

	public static class Builder extends AbstractChatMemoryAdvisor.AbstractBuilder<ChatMemory, Builder> {

		protected Builder(ChatMemory chatMemory) {
			super(chatMemory);
		}

		@Override
		protected Builder self() {
			return this;
		}

		@Override
		public MessageChatMemoryAdvisor build() {
			return new MessageChatMemoryAdvisor(this.chatMemory, this.conversationId, this.order);
		}

	}

}
