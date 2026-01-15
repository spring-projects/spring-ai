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
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.BaseChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.util.Assert;

/**
 * Memory is retrieved added into the prompt's system text.
 *
 * @author Christian Tzolov
 * @author Miloš Havránek
 * @author Thomas Vitale
 * @author Mark Pollack
 * @since 1.0.0
 */
public final class PromptChatMemoryAdvisor implements BaseChatMemoryAdvisor {

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

	private final String defaultConversationId;

	private final int order;

	private final Scheduler scheduler;

	private final ChatMemory chatMemory;

	private PromptChatMemoryAdvisor(ChatMemory chatMemory, String defaultConversationId, int order, Scheduler scheduler,
			PromptTemplate systemPromptTemplate) {
		Assert.notNull(chatMemory, "chatMemory cannot be null");
		Assert.hasText(defaultConversationId, "defaultConversationId cannot be null or empty");
		Assert.notNull(scheduler, "scheduler cannot be null");
		Assert.notNull(systemPromptTemplate, "systemPromptTemplate cannot be null");
		this.chatMemory = chatMemory;
		this.defaultConversationId = defaultConversationId;
		this.order = order;
		this.scheduler = scheduler;
		this.systemPromptTemplate = systemPromptTemplate;
	}

	public static Builder builder(ChatMemory chatMemory) {
		return new Builder(chatMemory);
	}

	@Override
	public int getOrder() {
		return this.order;
	}

	@Override
	public Scheduler getScheduler() {
		return this.scheduler;
	}

	@Override
	public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
		String conversationId = getConversationId(chatClientRequest.context(), this.defaultConversationId);
		// 1. Retrieve the chat memory for the current conversation.
		List<Message> memoryMessages = this.chatMemory.get(conversationId);
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
		// 4. Add the new user message to the conversation memory.
		Message userMessage = processedChatClientRequest.prompt().getLastUserOrToolResponseMessage();
		this.chatMemory.add(conversationId, userMessage);

		return processedChatClientRequest;
	}

	@Override
	public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
		List<Message> assistantMessages = new ArrayList<>();
		// Extract assistant messages from chat client response.
		// Processes all results from getResults() which automatically handles both single
		// and multiple
		// result scenarios (since getResult() == getResults().get(0)). Uses Optional
		// chaining for
		// null safety and returns empty list if no results are available.
		assistantMessages = Optional.ofNullable(chatClientResponse)
			.map(ChatClientResponse::chatResponse)
			.filter(response -> response.getResults() != null && !response.getResults().isEmpty())
			.map(response -> response.getResults()
				.stream()
				.map(g -> (Message) g.getOutput())
				.collect(Collectors.toList()))
			.orElse(List.of());

		if (!assistantMessages.isEmpty()) {
			this.chatMemory.add(this.getConversationId(chatClientResponse.context(), this.defaultConversationId),
					assistantMessages);

			if (logger.isDebugEnabled()) {
				logger.debug(
						"[PromptChatMemoryAdvisor.after] Added ASSISTANT messages to memory for conversationId={}: {}",
						this.getConversationId(chatClientResponse.context(), this.defaultConversationId),
						assistantMessages);
				List<Message> memoryMessages = this.chatMemory
					.get(this.getConversationId(chatClientResponse.context(), this.defaultConversationId));
				logger.debug("[PromptChatMemoryAdvisor.after] Memory after ASSISTANT add for conversationId={}: {}",
						this.getConversationId(chatClientResponse.context(), this.defaultConversationId),
						memoryMessages);
			}
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
			.transform(flux -> new ChatClientMessageAggregator().aggregateChatClientResponse(flux,
					response -> this.after(response, streamAdvisorChain)));
	}

	/**
	 * Builder for PromptChatMemoryAdvisor.
	 */
	public static final class Builder {

		private PromptTemplate systemPromptTemplate = DEFAULT_SYSTEM_PROMPT_TEMPLATE;

		private String conversationId = ChatMemory.DEFAULT_CONVERSATION_ID;

		private int order = Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER;

		private Scheduler scheduler = BaseAdvisor.DEFAULT_SCHEDULER;

		private final ChatMemory chatMemory;

		private Builder(ChatMemory chatMemory) {
			this.chatMemory = chatMemory;
		}

		/**
		 * Set the system prompt template.
		 * @param systemPromptTemplate the system prompt template
		 * @return the builder
		 */
		public Builder systemPromptTemplate(PromptTemplate systemPromptTemplate) {
			this.systemPromptTemplate = systemPromptTemplate;
			return this;
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

		public Builder scheduler(Scheduler scheduler) {
			this.scheduler = scheduler;
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
		public PromptChatMemoryAdvisor build() {
			return new PromptChatMemoryAdvisor(this.chatMemory, this.conversationId, this.order, this.scheduler,
					this.systemPromptTemplate);
		}

	}

}
