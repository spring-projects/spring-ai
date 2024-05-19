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

package org.springframework.ai.evaluation;

import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.prompt.transformer.ChatServiceContext;
import org.springframework.ai.chat.service.ChatService;
import org.springframework.ai.chat.service.StreamingChatService;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 */
public class BaseMemoryTest {

	protected final Logger logger = LoggerFactory.getLogger(getClass());

	protected RelevancyEvaluator relevancyEvaluator;

	protected ChatService chatService;

	protected StreamingChatService streamingChatService;

	public BaseMemoryTest(RelevancyEvaluator relevancyEvaluator, ChatService chatService,
			StreamingChatService streamingChatClient) {
		this.relevancyEvaluator = relevancyEvaluator;
		this.chatService = chatService;
		this.streamingChatService = streamingChatClient;
	}

	@Test
	void memoryChatService() {

		var prompt = new Prompt(new UserMessage("my name John Vincent Atanasoff"));
		ChatServiceContext chatServiceContext = new ChatServiceContext(prompt);

		var chatServiceResponse1 = this.chatService.call(chatServiceContext);

		logger.info("Response1: " + chatServiceResponse1.getChatResponse().getResult().getOutput().getContent());
		// response varies too much.
		// assertThat(chatServiceResponse1.getChatResponse().getResult().getOutput().getContent()).contains("John");

		var chatServiceResponse2 = this.chatService
			.call(new ChatServiceContext(new Prompt(new String("What is my name?"))));
		logger.info("Response2: " + chatServiceResponse2.getChatResponse().getResult().getOutput().getContent());
		assertThat(chatServiceResponse2.getChatResponse().getResult().getOutput().getContent())
			.contains("John Vincent Atanasoff");

		EvaluationResponse evaluationResponse = this.relevancyEvaluator
			.evaluate(chatServiceResponse2.toEvaluationRequest());
		logger.info("" + evaluationResponse);
	}

	@Test
	void memoryStreamingChatService() {

		var prompt = new Prompt(new UserMessage("my name John Vincent Atanasoff"));
		ChatServiceContext chatServiceContext = new ChatServiceContext(prompt);

		var fluxChatServiceResponse1 = this.streamingChatService.stream(chatServiceContext);

		String chatServiceResponse1 = fluxChatServiceResponse1.getChatResponse()
			.collectList()
			.block()
			.stream()
			.filter(response -> response.getResult() != null)
			.map(response -> response.getResult().getOutput().getContent())
			.collect(Collectors.joining());

		logger.info("Response1: " + chatServiceResponse1);
		// response varies too much assertThat(chatServiceResponse1).contains("John");

		var fluxChatServiceResponse2 = this.streamingChatService
			.stream(new ChatServiceContext(new Prompt(new String("What is my name?"))));

		String chatServiceResponse2 = fluxChatServiceResponse2.getChatResponse()
			.collectList()
			.block()
			.stream()
			.filter(response -> response.getResult() != null)
			.map(response -> response.getResult().getOutput().getContent())
			.collect(Collectors.joining());

		logger.info("Response2: " + chatServiceResponse2);
		assertThat(chatServiceResponse2).contains("John Vincent Atanasoff");
	}

}
