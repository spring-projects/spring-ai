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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.AdvisedRequest;
import org.springframework.ai.chat.client.RequestResponseAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.util.Assert;

/**
 * @author Christian Tzolov
 * @since 1.0.0 M1
 */
public class MessageChatMemoryAdvisor implements RequestResponseAdvisor {

	private static final int CHAT_HISTORY_WINDOW_SIZE = 40;

	private final String conversationId;

	private final ChatMemory chatMemory;

	private final int chatHistoryWindowSize;

	public MessageChatMemoryAdvisor(String conversationId, ChatMemory chatMemory) {
		this(conversationId, chatMemory, CHAT_HISTORY_WINDOW_SIZE);
	}

	public MessageChatMemoryAdvisor(String conversationId, ChatMemory chatMemory, int chatHistoryWindowSize) {
		Assert.hasText(conversationId, "The conversationId must not be empty!");
		Assert.notNull(chatMemory, "The chatMemory must not be null!");
		Assert.isTrue(chatHistoryWindowSize > 0, "The chatHistoryWindowSize must be greater than 0!");

		this.conversationId = conversationId;
		this.chatMemory = chatMemory;
		this.chatHistoryWindowSize = chatHistoryWindowSize;
	}

	@Override
	public AdvisedRequest adviseRequest(AdvisedRequest request, Map<String, Object> context) {

		// 1. Retrieve the chat memory for the current conversation.
		List<Message> memoryMessages = this.chatMemory.get(this.conversationId, this.chatHistoryWindowSize);

		// 2. Advise the request messages list.
		List<Message> advisedMessages = new ArrayList<>(request.messages());
		advisedMessages.addAll(memoryMessages);

		// 3. Create a new request with the advised messages.
		AdvisedRequest advisedRequest = AdvisedRequest.from(request).withMessages(advisedMessages).build();

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