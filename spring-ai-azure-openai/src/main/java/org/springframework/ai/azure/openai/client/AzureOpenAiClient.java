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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.models.ChatChoice;
import com.azure.ai.openai.models.ChatCompletions;
import com.azure.ai.openai.models.ChatCompletionsOptions;
import com.azure.ai.openai.models.ChatMessage;
import com.azure.ai.openai.models.ChatRole;
import com.azure.ai.openai.models.PromptFilterResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.azure.openai.metadata.AzureOpenAiGenerationMetadata;
import org.springframework.ai.client.AiClient;
import org.springframework.ai.client.AiResponse;
import org.springframework.ai.client.Generation;
import org.springframework.ai.metadata.ChoiceMetadata;
import org.springframework.ai.metadata.PromptMetadata;
import org.springframework.ai.metadata.PromptMetadata.PromptFilterMetadata;
import org.springframework.ai.prompt.Prompt;
import org.springframework.ai.prompt.messages.Message;
import org.springframework.util.Assert;

/**
 * {@link AiClient} implementation for {@literal Microsoft Azure AI} backed by
 * {@link OpenAIClient}.
 *
 * @author Mark Pollack
 * @author Ueibin Kim
 * @author John Blum
 * @see org.springframework.ai.client.AiClient
 * @see com.azure.ai.openai.OpenAIClient
 */
public class AzureOpenAiClient implements AiClient {

	private Double temperature = 0.7;

	private String model = "gpt-35-turbo";

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final OpenAIClient azureOpenAiClient;

	public AzureOpenAiClient(OpenAIClient microsoftOpenAiClient) {
		Assert.notNull(microsoftOpenAiClient, "com.azure.ai.openai.OpenAIClient must not be null");
		this.azureOpenAiClient = microsoftOpenAiClient;
	}

	public String getModel() {
		return this.model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public Double getTemperature() {
		return this.temperature;
	}

	public void setTemperature(Double temperature) {
		this.temperature = temperature;
	}

	@Override
	public String generate(String text) {

		ChatMessage azureChatMessage = new ChatMessage(ChatRole.USER, text);

		ChatCompletionsOptions options = new ChatCompletionsOptions(List.of(azureChatMessage));
		options.setTemperature(this.getTemperature());
		options.setModel(this.getModel());
		logger.trace("Azure Chat Message: {}", azureChatMessage);

		ChatCompletions chatCompletions = this.azureOpenAiClient.getChatCompletions(this.getModel(), options);
		logger.trace("Azure ChatCompletions: {}", chatCompletions);

		StringBuilder stringBuilder = new StringBuilder();

		for (ChatChoice choice : chatCompletions.getChoices()) {
			ChatMessage message = choice.getMessage();
			if (message != null && message.getContent() != null) {
				stringBuilder.append(message.getContent());
			}
		}

		return stringBuilder.toString();
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
		logger.trace("Azure ChatCompletionsOptions: {}", options);

		ChatCompletions chatCompletions = this.azureOpenAiClient.getChatCompletions(this.getModel(), options);
		logger.trace("Azure ChatCompletions: {}", chatCompletions);

		List<Generation> generations = new ArrayList<>();

		for (ChatChoice choice : chatCompletions.getChoices()) {
			ChatMessage choiceMessage = choice.getMessage();
			Generation generation = new Generation(choiceMessage.getContent())
				.withChoiceMetadata(generateChoiceMetadata(choice));
			generations.add(generation);
		}

		return new AiResponse(generations, AzureOpenAiGenerationMetadata.from(chatCompletions))
			.withPromptMetadata(generatePromptMetadata(chatCompletions));
	}

	private ChoiceMetadata generateChoiceMetadata(ChatChoice choice) {
		return ChoiceMetadata.from(String.valueOf(choice.getFinishReason()), choice.getContentFilterResults());
	}

	private PromptMetadata generatePromptMetadata(ChatCompletions chatCompletions) {

		List<PromptFilterResult> promptFilterResults = nullSafeList(chatCompletions.getPromptFilterResults());

		return PromptMetadata.of(promptFilterResults.stream()
			.map(promptFilterResult -> PromptFilterMetadata.from(promptFilterResult.getPromptIndex(),
					promptFilterResult.getContentFilterResults()))
			.toList());
	}

	private <T> List<T> nullSafeList(List<T> list) {
		return list != null ? list : Collections.emptyList();
	}

}
