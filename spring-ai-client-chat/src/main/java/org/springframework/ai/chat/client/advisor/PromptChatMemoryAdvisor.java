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
import java.util.stream.Collectors;

import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.MessageAggregator;

/**
 * Memory is retrieved added into the prompt's system text.
 *
 * @author Christian Tzolov
 * @author Miloš Havránek
 * @author Thomas Vitale
 * @since 1.0.0
 */
public class PromptChatMemoryAdvisor extends AbstractChatMemoryAdvisor<ChatMemory> {

	private static final String DEFAULT_SYSTEM_TEXT_ADVISE = """

			Use the conversation memory from the MEMORY section to provide accurate answers.

			---------------------
			MEMORY:
			{memory}
			---------------------

			""";

	private final String systemTextAdvise;

	public PromptChatMemoryAdvisor(ChatMemory chatMemory) {
		this(chatMemory, DEFAULT_SYSTEM_TEXT_ADVISE);
	}

	public PromptChatMemoryAdvisor(ChatMemory chatMemory, String systemTextAdvise) {
		super(chatMemory);
		this.systemTextAdvise = systemTextAdvise;
	}

	public PromptChatMemoryAdvisor(ChatMemory chatMemory, String defaultConversationId, int chatHistoryWindowSize,
			String systemTextAdvise) {
		this(chatMemory, defaultConversationId, chatHistoryWindowSize, systemTextAdvise,
				Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER);
	}

	public PromptChatMemoryAdvisor(ChatMemory chatMemory, String defaultConversationId, int chatHistoryWindowSize,
			String systemTextAdvise, int order) {
		super(chatMemory, defaultConversationId, chatHistoryWindowSize, true, order);
		this.systemTextAdvise = systemTextAdvise;
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

	private ChatClientRequest before(ChatClientRequest chatClientRequest) {
		String conversationId = this.doGetConversationId(chatClientRequest.context());
		int chatMemoryRetrieveSize = this.doGetChatMemoryRetrieveSize(chatClientRequest.context());

		// 1. Retrieve the chat memory for the current conversation.
		List<Message> memoryMessages = this.getChatMemoryStore().get(conversationId, chatMemoryRetrieveSize);

		// 2. Processed memory messages as a string.
		String memory = memoryMessages.stream()
			.filter(m -> m.getMessageType() == MessageType.USER || m.getMessageType() == MessageType.ASSISTANT)
			.map(m -> m.getMessageType() + ":" + m.getText())
			.collect(Collectors.joining(System.lineSeparator()));

		// 2. Augment the system message.
		SystemMessage systemMessage = chatClientRequest.prompt().getSystemMessage();
		String augmentedSystemText = PromptTemplate.builder()
			.template(systemMessage.getText() + System.lineSeparator() + this.systemTextAdvise)
			.variables(Map.of("memory", memory))
			.build()
			.render();

		// 3. Create a new request with the augmented system message.
		ChatClientRequest processedChatClientRequest = chatClientRequest.mutate()
			.prompt(chatClientRequest.prompt().augmentSystemMessage(augmentedSystemText))
			.build();

		// 4. Add the new user message to the conversation memory.
		UserMessage userMessage = processedChatClientRequest.prompt().getUserMessage();
		this.getChatMemoryStore().add(conversationId, userMessage);

		return processedChatClientRequest;
	}

	private void after(ChatClientResponse chatClientResponse) {
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

	public static class Builder extends AbstractChatMemoryAdvisor.AbstractBuilder<ChatMemory> {

		private String systemTextAdvise = DEFAULT_SYSTEM_TEXT_ADVISE;

		protected Builder(ChatMemory chatMemory) {
			super(chatMemory);
		}

		public Builder systemTextAdvise(String systemTextAdvise) {
			this.systemTextAdvise = systemTextAdvise;
			return this;
		}

		public PromptChatMemoryAdvisor build() {
			return new PromptChatMemoryAdvisor(this.chatMemory, this.conversationId, this.chatMemoryRetrieveSize,
					this.systemTextAdvise, this.order);
		}

	}

}
