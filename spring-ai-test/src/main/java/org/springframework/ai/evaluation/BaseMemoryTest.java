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

import org.springframework.ai.chat.chatbot.ChatBot;
import org.springframework.ai.chat.chatbot.StreamingChatBot;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.transformer.PromptContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 */
public class BaseMemoryTest {

	protected final Logger logger = LoggerFactory.getLogger(getClass());

	protected RelevancyEvaluator relevancyEvaluator;

	protected ChatBot chatBot;

	protected StreamingChatBot streamingChatBot;

	public BaseMemoryTest(RelevancyEvaluator relevancyEvaluator, ChatBot chatBot,
			StreamingChatBot streamingChatClient) {
		this.relevancyEvaluator = relevancyEvaluator;
		this.chatBot = chatBot;
		this.streamingChatBot = streamingChatClient;
	}

	@Test
	void memoryChatBot() {

		var prompt = new Prompt(new UserMessage("my name John Vincent Atanasoff"));
		PromptContext promptContext = new PromptContext(prompt);

		var chatBotResponse1 = this.chatBot.call(promptContext);

		logger.info("Response1: " + chatBotResponse1.getChatResponse().getResult().getOutput().getContent());
		// response varies too much.
		// assertThat(chatBotResponse1.getChatResponse().getResult().getOutput().getContent()).contains("John");

		var chatBotResponse2 = this.chatBot.call(new PromptContext(new Prompt(new String("What is my name?"))));
		logger.info("Response2: " + chatBotResponse2.getChatResponse().getResult().getOutput().getContent());
		assertThat(chatBotResponse2.getChatResponse().getResult().getOutput().getContent())
			.contains("John Vincent Atanasoff");

		EvaluationResponse evaluationResponse = this.relevancyEvaluator
			.evaluate(new EvaluationRequest(chatBotResponse2));
		logger.info("" + evaluationResponse);
	}

	@Test
	void memoryStreamingChatBot() {

		var prompt = new Prompt(new UserMessage("my name John Vincent Atanasoff"));
		PromptContext promptContext = new PromptContext(prompt);

		var fluxChatBotResponse1 = this.streamingChatBot.stream(promptContext);

		String chatBotResponse1 = fluxChatBotResponse1.getChatResponse()
			.collectList()
			.block()
			.stream()
			.filter(response -> response.getResult() != null)
			.map(response -> response.getResult().getOutput().getContent())
			.collect(Collectors.joining());

		logger.info("Response1: " + chatBotResponse1);
		// response varies too much assertThat(chatBotResponse1).contains("John");

		var fluxChatBotResponse2 = this.streamingChatBot
			.stream(new PromptContext(new Prompt(new String("What is my name?"))));

		String chatBotResponse2 = fluxChatBotResponse2.getChatResponse()
			.collectList()
			.block()
			.stream()
			.filter(response -> response.getResult() != null)
			.map(response -> response.getResult().getOutput().getContent())
			.collect(Collectors.joining());

		logger.info("Response2: " + chatBotResponse2);
		assertThat(chatBotResponse2).contains("John Vincent Atanasoff");
	}

}
