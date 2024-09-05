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

import org.springframework.ai.chat.client.advisor.api.AdvisedRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisedResponse;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.MessageAggregator;

import reactor.core.publisher.Flux;

/**
 * Memory is retrieved added as a collection of messages to the prompt
 *
 * @author Christian Tzolov
 * @since 1.0.0
 */
public class MessageChatMemoryAdvisor2 extends AbstractChatMemoryAdvisor2<ChatMemory> {

	public MessageChatMemoryAdvisor2(ChatMemory chatMemory) {
		super(chatMemory);
	}

	public MessageChatMemoryAdvisor2(ChatMemory chatMemory, String defaultConversationId, int chatHistoryWindowSize) {
		super(chatMemory, defaultConversationId, chatHistoryWindowSize);
	}

	@Override
	public AdvisedRequest doBefore(AdvisedRequest advisedRequest) {
		return this.before(advisedRequest);
	}

	@Override
	public AdvisedResponse doAfter(AdvisedResponse advisedResponse) {
		this.observeAfter(advisedResponse);
		return advisedResponse;
	}

	@Override
	public Flux<AdvisedResponse> doAfterStream(Flux<AdvisedResponse> advisedResponses) {
		return new MessageAggregator().aggregateAdvisedResponse(advisedResponses, this::observeAfter);
	}

	private AdvisedRequest before(AdvisedRequest request) {

		String conversationId = this.doGetConversationId(request.adviseContext());

		int chatMemoryRetrieveSize = this.doGetChatMemoryRetrieveSize(request.adviseContext());

		// 1. Retrieve the chat memory for the current conversation.
		List<Message> memoryMessages = this.getChatMemoryStore().get(conversationId, chatMemoryRetrieveSize);

		// 2. Advise the request messages list.
		List<Message> advisedMessages = new ArrayList<>(request.messages());
		advisedMessages.addAll(memoryMessages);

		// 3. Create a new request with the advised messages.
		AdvisedRequest advisedRequest = AdvisedRequest.from(request).withMessages(advisedMessages).build();

		// 4. Add the new user input to the conversation memory.
		UserMessage userMessage = new UserMessage(request.userText(), request.media());
		this.getChatMemoryStore().add(this.doGetConversationId(request.adviseContext()), userMessage);

		return advisedRequest;
	}

	private void observeAfter(AdvisedResponse advisedResponse) {

		List<Message> assistantMessages = advisedResponse.response()
			.getResults()
			.stream()
			.map(g -> (Message) g.getOutput())
			.toList();

		this.getChatMemoryStore().add(this.doGetConversationId(advisedResponse.adviseContext()), assistantMessages);
	}

}