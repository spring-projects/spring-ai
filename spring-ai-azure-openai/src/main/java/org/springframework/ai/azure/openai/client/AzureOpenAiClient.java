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

package org.springframework.ai.azure.openai.client;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.models.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.client.AiClient;
import org.springframework.ai.client.AiResponse;
import org.springframework.ai.client.Generation;
import org.springframework.ai.prompt.Prompt;
import org.springframework.ai.prompt.messages.Message;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of {@link AiClient} backed by an OpenAiService
 */
public class AzureOpenAiClient implements AiClient {

	private static final Logger logger = LoggerFactory.getLogger(AzureOpenAiClient.class);

	private final OpenAIClient msoftOpenAiClient;

	private Double temperature = 0.7;

	private String model = "gpt-35-turbo";

	public AzureOpenAiClient(OpenAIClient msoftOpenAiClient) {
		Assert.notNull(msoftOpenAiClient, "com.azure.ai.openai.OpenAIClient must not be null");
		this.msoftOpenAiClient = msoftOpenAiClient;
	}

	@Override
	public String generate(String text) {
		ChatMessage azureChatMessage = new ChatMessage(ChatRole.USER, text);

		ChatCompletionsOptions options = new ChatCompletionsOptions(List.of(azureChatMessage));
		options.setTemperature(this.getTemperature());
		options.setModel(this.getModel());

		logger.trace("Azure Chat Message: ", azureChatMessage);
		ChatCompletions chatCompletions = this.msoftOpenAiClient.getChatCompletions(this.getModel(), options);
		logger.trace("Azure ChatCompletions: ", chatCompletions);
		StringBuilder sb = new StringBuilder();
		for (ChatChoice choice : chatCompletions.getChoices()) {
			if (choice.getMessage() != null && choice.getMessage().getContent() != null) {
				sb.append(choice.getMessage().getContent());
			}
		}
		return sb.toString();
	}

	@Override
	public AiResponse generate(Prompt prompt) {
		List<Message> messages = prompt.getMessages();
		List<ChatMessage> azureMessages = new ArrayList<>();
		for (Message message : messages) {
			String messageType = message.getMessageTypeValue();
			ChatRole chatRole = ChatRole.fromString(messageType);
			azureMessages.add(new ChatMessage(chatRole, message.getContent()));
		}
		ChatCompletionsOptions options = new ChatCompletionsOptions(azureMessages);
		options.setTemperature(this.getTemperature());
		options.setModel(this.getModel());
		logger.trace("Azure ChatCompletionsOptions: ", options);
		ChatCompletions chatCompletions = this.msoftOpenAiClient.getChatCompletions(this.getModel(), options);
		logger.trace("Azure ChatCompletions: ", chatCompletions);
		List<Generation> generations = new ArrayList<>();
		for (ChatChoice choice : chatCompletions.getChoices()) {
			ChatMessage choiceMessage = choice.getMessage();
			// TODO investigate mapping of additional metadata/runtime info to the
			// general model.
			Generation generation = new Generation(choiceMessage.getContent());
			generations.add(generation);
		}
		return new AiResponse(generations);
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

}
