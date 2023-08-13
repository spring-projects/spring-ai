/*
 * Copyright 2023 the original author or authors.
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

package org.springframework.ai.openai.llm;

import java.util.ArrayList;
import java.util.List;

import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.service.OpenAiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.core.llm.LLMResponse;
import org.springframework.ai.core.llm.LlmClient;
import org.springframework.ai.core.prompt.Prompt;

import org.springframework.ai.core.prompt.messages.Message;
import org.springframework.util.Assert;

/**
 * Implementation of {@link LlmClient} backed by an OpenAiService
 */
public class OpenAiClient implements LlmClient {

	private static final Logger logger = LoggerFactory.getLogger(OpenAiClient.class);

	// TODO how to set default options for the entire client
	// TODO expose request options into Prompt API via PromptOptions
	private Double temperature = 0.5;

	private String model = "gpt-3.5-turbo";

	private final OpenAiService openAiService;

	public OpenAiClient(OpenAiService openAiService) {
		Assert.notNull(openAiService, "OpenAiService must not be null");
		this.openAiService = openAiService;
	}

	public Double getTemperature() {
		return temperature;
	}

	public void setTemperature(Double temperature) {
		this.temperature = temperature;
	}

	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	@Override
	public String generate(String text) {
		ChatCompletionRequest chatCompletionRequest = getChatCompletionRequest(text);
		return getResponse(chatCompletionRequest);
	}

	@Override
	public LLMResponse generate(Prompt prompt) {
		List<ChatCompletionRequest> chatCompletionRequests = getChatCompletionRequest(prompt);
		return getLLMResult(chatCompletionRequests);
	}

	private ChatCompletionRequest getChatCompletionRequest(String text) {
		ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest.builder()
			.model(this.model)
			.temperature(this.temperature)
			.messages(List.of(new ChatMessage("user", text)))
			.build();
		return chatCompletionRequest;
	}

	private String getResponse(ChatCompletionRequest chatCompletionRequest) {
		StringBuilder builder = new StringBuilder();
		this.openAiService.createChatCompletion(chatCompletionRequest).getChoices().forEach(choice -> {
			builder.append(choice.getMessage().getContent());
		});

		String response = builder.toString();
		return response;
	}

	private LLMResponse getLLMResult(List<ChatCompletionRequest> chatCompletionRequest) {
		// TODO
		throw new RuntimeException("LLMResult getLLMResult not yet implemented");
	}

	private List<ChatCompletionRequest> getChatCompletionRequest(Prompt prompt) {
		List<ChatCompletionRequest> chatCompletionRequests = new ArrayList<>();

		List<ChatMessage> chatMessages = convertToChatMessages(prompt.getMessages());
		ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest.builder()
			.model(this.model)
			.temperature(this.temperature)
			.messages(chatMessages)
			.build();
		chatCompletionRequests.add(chatCompletionRequest);

		return chatCompletionRequests;
	}

	private List<ChatMessage> convertToChatMessages(List<Message> messages) {
		List<ChatMessage> chatMessages = new ArrayList<>();
		for (Message promptMessage : messages) {
			switch (promptMessage.getMessageType()) {
				case USER:
					chatMessages.add(new ChatMessage("user", promptMessage.getContent()));
					break;
				case ASSISTANT:
					// TODO - valid?
					chatMessages.add(new ChatMessage("assistant", promptMessage.getContent()));
					break;
				case SYSTEM:
					chatMessages.add(new ChatMessage("system", promptMessage.getContent()));
					break;
				case FUNCTION:
					logger.error(
							"Can not send a Spring AI Function MessageType to the ChatGPT API, use 'system', 'user' or 'ai' message types.");
					break;
				default:
					logger.error("Unknown Spring AI Chat MessageType.  Use 'system', 'human' or 'ai' message types.");
					break;
			}
		}
		return chatMessages;
	}

}
