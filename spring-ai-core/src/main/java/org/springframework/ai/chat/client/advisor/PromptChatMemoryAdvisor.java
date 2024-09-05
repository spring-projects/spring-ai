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

import org.springframework.ai.chat.client.AdvisedRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisedResponse;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.model.Content;

/**
 * Memory is retrieved added into the prompt's system text.
 *
 * @author Christian Tzolov
 * @since 1.0.0 M1
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
		super(chatMemory, defaultConversationId, chatHistoryWindowSize);
		this.systemTextAdvise = systemTextAdvise;
	}

	@Override
	public AdvisedRequest adviseRequest(AdvisedRequest request) {

		// 1. Advise system parameters.
		List<Message> memoryMessages = this.getChatMemoryStore()
			.get(this.doGetConversationId(request.adviseContext()),
					this.doGetChatMemoryRetrieveSize(request.adviseContext()));

		String memory = (memoryMessages != null) ? memoryMessages.stream()
			.filter(m -> m.getMessageType() == MessageType.USER || m.getMessageType() == MessageType.ASSISTANT)
			.map(m -> m.getMessageType() + ":" + ((Content) m).getContent())
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
		this.getChatMemoryStore().add(this.doGetConversationId(request.adviseContext()), userMessage);

		return advisedRequest;
	}

	@Override
	public AdvisedResponse adviseResponse(AdvisedResponse advisedResponse) {

		List<Message> assistantMessages = advisedResponse.response()
			.getResults()
			.stream()
			.map(g -> (Message) g.getOutput())
			.toList();

		this.getChatMemoryStore().add(this.doGetConversationId(advisedResponse.adviseContext()), assistantMessages);

		return advisedResponse;
	}

}