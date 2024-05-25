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

package org.springframework.ai.chat.client.advisor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.AdvisedRequest;
import org.springframework.ai.chat.client.RequestResponseAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.util.Assert;

/**
 * @author Christian Tzolov
 * @since 1.0.0 M1
 */
public class PromptChatMemoryAdvisor implements RequestResponseAdvisor {

	private static final int CHAT_HISTORY_WINDOW_SIZE = 40;

	private static final String DEFAULT_SYSTEM_TEXT_ADVISE = """

			Use the conversation memory from the MEMORY section to provide accurate answers.

			---------------------
			MEMORY:
			{memory}
			---------------------

			""";

	private final String systemTextAdvise;

	private final String conversationId;

	private final ChatMemory chatMemory;

	private final int chatHistoryWindowSize;

	public PromptChatMemoryAdvisor(String conversationId, ChatMemory chatMemory) {
		this(conversationId, chatMemory, DEFAULT_SYSTEM_TEXT_ADVISE, CHAT_HISTORY_WINDOW_SIZE);
	}

	public PromptChatMemoryAdvisor(String conversationId, ChatMemory chatMemory, String systemTextAdvise,
			int chatHistoryWindowSize) {
		Assert.hasText(conversationId, "The conversationId must not be empty!");
		Assert.notNull(chatMemory, "The chatMemory must not be null!");
		Assert.hasText(systemTextAdvise, "The systemTextAdvise must not be empty!");
		Assert.isTrue(chatHistoryWindowSize > 0, "The chatHistoryWindowSize must be greater than 0!");

		this.conversationId = conversationId;
		this.chatMemory = chatMemory;
		this.systemTextAdvise = systemTextAdvise;
		this.chatHistoryWindowSize = chatHistoryWindowSize;
	}

	@Override
	public AdvisedRequest adviseRequest(AdvisedRequest request, Map<String, Object> context) {

		// 1. Advise system parameters.
		List<Message> memoryMessages = this.chatMemory.get(this.conversationId, this.chatHistoryWindowSize);

		String memory = (memoryMessages != null) ? memoryMessages.stream()
			.filter(m -> m.getMessageType() != MessageType.SYSTEM)
			.map(m -> m.getMessageType() + ":" + m.getContent())
			.collect(Collectors.joining(System.lineSeparator())) : "";

		Map<String, Object> advisedSystemParams = new HashMap<>(request.systemParams());
		advisedSystemParams.put("memory", memory);

		// 2. Advise the system text.
		String advisedSystemText = request.systemText() + System.lineSeparator() + this.systemTextAdvise;

		// 3. Create a new request with the advised system text and parameters.
		AdvisedRequest advisedRequest = AdvisedRequest.from(request)
			.withSystemText(advisedSystemText)
			.withSystemParams(advisedSystemParams)
			.build();

		// 4. Add the new user input to the conversation memory.
		UserMessage userMessage = new UserMessage(request.userText(), request.media());
		this.chatMemory.add(this.conversationId, userMessage);

		return advisedRequest;
	}

	@Override
	public ChatResponse adviseResponse(ChatResponse chatResponse, Map<String, Object> context) {

		List<Message> assistantMessages = chatResponse.getResults().stream().map(g -> (Message) g.getOutput()).toList();

		this.chatMemory.add(this.conversationId, assistantMessages);

		return chatResponse;
	}

	@Override
	public Flux<ChatResponse> adviseResponse(Flux<ChatResponse> fluxChatResponse, Map<String, Object> context) {

		return new MessageAggregator().aggregate(fluxChatResponse, chatResponse -> {
			List<Message> assistantMessages = chatResponse.getResults()
				.stream()
				.map(g -> (Message) g.getOutput())
				.toList();

			this.chatMemory.add(this.conversationId, assistantMessages);
		});
	}

}