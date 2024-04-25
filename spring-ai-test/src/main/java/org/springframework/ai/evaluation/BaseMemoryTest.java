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

import org.springframework.ai.chat.agent.ChatAgent;
import org.springframework.ai.chat.agent.StreamingChatAgent;
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

	protected ChatAgent chatAgent;

	protected StreamingChatAgent streamingChatAgent;

	public BaseMemoryTest(RelevancyEvaluator relevancyEvaluator, ChatAgent chatAgent,
			StreamingChatAgent streamingChatClient) {
		this.relevancyEvaluator = relevancyEvaluator;
		this.chatAgent = chatAgent;
		this.streamingChatAgent = streamingChatClient;
	}

	@Test
	void memoryChatAgent() {

		var prompt = new Prompt(new UserMessage("my name John Vincent Atanasoff"));
		PromptContext promptContext = new PromptContext(prompt);

		var agentResponse1 = this.chatAgent.call(promptContext);

		logger.info("Response1: " + agentResponse1.getChatResponse().getResult().getOutput().getContent());
		assertThat(agentResponse1.getChatResponse().getResult().getOutput().getContent()).contains("John");

		var agentResponse2 = this.chatAgent.call(new PromptContext(new Prompt(new String("What is my name?"))));
		logger.info("Response2: " + agentResponse2.getChatResponse().getResult().getOutput().getContent());
		assertThat(agentResponse2.getChatResponse().getResult().getOutput().getContent())
			.contains("John Vincent Atanasoff");

		EvaluationResponse evaluationResponse = this.relevancyEvaluator.evaluate(new EvaluationRequest(agentResponse2));
		logger.info("" + evaluationResponse);
	}

	@Test
	void memoryStreamingChatAgent() {

		var prompt = new Prompt(new UserMessage("my name John Vincent Atanasoff"));
		PromptContext promptContext = new PromptContext(prompt);

		var fluxAgentResponse1 = this.streamingChatAgent.stream(promptContext);

		String agentResponse1 = fluxAgentResponse1.getChatResponse()
			.collectList()
			.block()
			.stream()
			.filter(response -> response.getResult() != null)
			.map(response -> response.getResult().getOutput().getContent())
			.collect(Collectors.joining());

		logger.info("Response1: " + agentResponse1);
		assertThat(agentResponse1).contains("John");

		var fluxAgentResponse2 = this.streamingChatAgent
			.stream(new PromptContext(new Prompt(new String("What is my name?"))));

		String agentResponse2 = fluxAgentResponse2.getChatResponse()
			.collectList()
			.block()
			.stream()
			.filter(response -> response.getResult() != null)
			.map(response -> response.getResult().getOutput().getContent())
			.collect(Collectors.joining());

		logger.info("Response2: " + agentResponse2);
		assertThat(agentResponse2).contains("John Vincent Atanasoff");
	}

}
