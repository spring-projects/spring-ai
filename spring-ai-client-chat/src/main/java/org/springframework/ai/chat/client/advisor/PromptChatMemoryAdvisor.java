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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.chat.prompt.PromptTemplate;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

/**
 * Memory is retrieved added into the prompt's system text.
 *
 * @author Christian Tzolov
 * @author Miloš Havránek
 * @author Thomas Vitale
 * @author Mark Pollack
 * @since 1.0.0
 */
public class PromptChatMemoryAdvisor extends AbstractChatMemoryAdvisor<ChatMemory> {

	private static final Logger logger = LoggerFactory.getLogger(PromptChatMemoryAdvisor.class);

	private static final PromptTemplate DEFAULT_SYSTEM_PROMPT_TEMPLATE = new PromptTemplate("""
			{instructions}

			Use the conversation memory from the MEMORY section to provide accurate answers.

			---------------------
			MEMORY:
			{memory}
			---------------------

			""");

	private final PromptTemplate systemPromptTemplate;

	public PromptChatMemoryAdvisor(ChatMemory chatMemory) {
		this(chatMemory, DEFAULT_SYSTEM_PROMPT_TEMPLATE.getTemplate());
	}

	public PromptChatMemoryAdvisor(ChatMemory chatMemory, String systemPromptTemplate) {
		super(chatMemory);
		this.systemPromptTemplate = new PromptTemplate(systemPromptTemplate);
	}

	public PromptChatMemoryAdvisor(ChatMemory chatMemory, String defaultConversationId, String systemPromptTemplate) {
		this(chatMemory, defaultConversationId, new PromptTemplate(systemPromptTemplate),
				Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER);
	}

	public PromptChatMemoryAdvisor(ChatMemory chatMemory, String defaultConversationId, String systemPromptTemplate,
			int order) {
		this(chatMemory, defaultConversationId, new PromptTemplate(systemPromptTemplate), order);
	}

	private PromptChatMemoryAdvisor(ChatMemory chatMemory, String defaultConversationId,
			PromptTemplate systemPromptTemplate, int order) {
		super(chatMemory, defaultConversationId, true, order);
		this.systemPromptTemplate = systemPromptTemplate;
	}

	public static Builder builder(ChatMemory chatMemory) {
		return new Builder(chatMemory);
	}

	@Override
	public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
		String conversationId = doGetConversationId(chatClientRequest.context());
		// 1. Retrieve the chat memory for the current conversation.
		List<Message> memoryMessages = this.getChatMemoryStore().get(conversationId);
		logger.debug("[PromptChatMemoryAdvisor.before] Memory before processing for conversationId={}: {}",
				conversationId, memoryMessages);

		// 2. Process memory messages as a string.
		String memory = memoryMessages.stream()
			.filter(m -> m.getMessageType() == MessageType.USER || m.getMessageType() == MessageType.ASSISTANT)
			.map(m -> m.getMessageType() + ":" + m.getText())
			.collect(Collectors.joining(System.lineSeparator()));

		// 3. Augment the system message.
		SystemMessage systemMessage = chatClientRequest.prompt().getSystemMessage();
		String augmentedSystemText = this.systemPromptTemplate
			.render(Map.of("instructions", systemMessage.getText(), "memory", memory));

		// 4. Create a new request with the augmented system message.
		ChatClientRequest processedChatClientRequest = chatClientRequest.mutate()
			.prompt(chatClientRequest.prompt().augmentSystemMessage(augmentedSystemText))
			.build();

		// 5. Add all user messages from the current prompt to memory (after system
		// message is generated)
		List<UserMessage> userMessages = chatClientRequest.prompt().getUserMessages();
		for (UserMessage userMessage : userMessages) {
			this.getChatMemoryStore().add(conversationId, userMessage);
			logger.debug("[PromptChatMemoryAdvisor.before] Added USER message to memory for conversationId={}: {}",
					conversationId, userMessage.getText());
		}

		return processedChatClientRequest;
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
		// Handle streaming case where we have a single result
		else if (chatClientResponse.chatResponse() != null && chatClientResponse.chatResponse().getResult() != null
				&& chatClientResponse.chatResponse().getResult().getOutput() != null) {
			assistantMessages = List.of((Message) chatClientResponse.chatResponse().getResult().getOutput());
		}

		if (!assistantMessages.isEmpty()) {
			this.getChatMemoryStore().add(this.doGetConversationId(chatClientResponse.context()), assistantMessages);
			logger.debug("[PromptChatMemoryAdvisor.after] Added ASSISTANT messages to memory for conversationId={}: {}",
					this.doGetConversationId(chatClientResponse.context()), assistantMessages);
			List<Message> memoryMessages = this.getChatMemoryStore()
				.get(this.doGetConversationId(chatClientResponse.context()));
			logger.debug("[PromptChatMemoryAdvisor.after] Memory after ASSISTANT add for conversationId={}: {}",
					this.doGetConversationId(chatClientResponse.context()), memoryMessages);
		}
		return chatClientResponse;
	}

	@Override
	public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest,
			StreamAdvisorChain streamAdvisorChain) {
		// Get the scheduler from BaseAdvisor
		Scheduler scheduler = this.getScheduler();

		// Process the request with the before method
		return Mono.just(chatClientRequest)
			.publishOn(scheduler)
			.map(request -> this.before(request, streamAdvisorChain))
			.flatMapMany(streamAdvisorChain::nextStream)
			.transform(flux -> new MessageAggregator().aggregateChatClientResponse(flux,
					response -> this.after(response, streamAdvisorChain)));
	}

	public static class Builder extends AbstractChatMemoryAdvisor.AbstractBuilder<ChatMemory, Builder> {

		private PromptTemplate systemPromptTemplate = DEFAULT_SYSTEM_PROMPT_TEMPLATE;

		protected Builder(ChatMemory chatMemory) {
			super(chatMemory);
		}

		@Override
		protected Builder self() {
			return this;
		}

		public Builder systemTextAdvise(String systemTextAdvise) {
			this.systemPromptTemplate = new PromptTemplate(systemTextAdvise);
			return self();
		}

		public Builder systemPromptTemplate(PromptTemplate systemPromptTemplate) {
			this.systemPromptTemplate = systemPromptTemplate;
			return self();
		}

		public PromptChatMemoryAdvisor build() {
			return new PromptChatMemoryAdvisor(this.chatMemory, this.conversationId, this.systemPromptTemplate,
					this.order);
		}

	}

}
