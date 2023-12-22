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
import com.azure.ai.openai.models.ChatRequestAssistantMessage;
import com.azure.ai.openai.models.ChatRequestMessage;
import com.azure.ai.openai.models.ChatRequestSystemMessage;
import com.azure.ai.openai.models.ChatRequestUserMessage;
import com.azure.ai.openai.models.ChatResponseMessage;
import com.azure.ai.openai.models.ContentFilterResultsForPrompt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.azure.openai.metadata.AzureOpenAiGenerationMetadata;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.Generation;
import org.springframework.ai.metadata.ChoiceMetadata;
import org.springframework.ai.metadata.PromptMetadata;
import org.springframework.ai.metadata.PromptMetadata.PromptFilterMetadata;
import org.springframework.ai.prompt.Prompt;
import org.springframework.ai.prompt.messages.Message;
import org.springframework.util.Assert;

/**
 * {@link ChatClient} implementation for {@literal Microsoft Azure AI} backed by
 * {@link OpenAIClient}.
 *
 * @author Mark Pollack
 * @author Ueibin Kim
 * @author John Blum
 * @author Christian Tzolov
 * @see ChatClient
 * @see com.azure.ai.openai.OpenAIClient
 */
public class AzureOpenAiChatClient implements ChatClient {

	private Double temperature = 0.7;

	private String model = "gpt-35-turbo";

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final OpenAIClient openAIClient;

	public AzureOpenAiChatClient(OpenAIClient microsoftOpenAiChatClient) {
		Assert.notNull(microsoftOpenAiChatClient, "com.azure.ai.openai.OpenAIClient must not be null");
		this.openAIClient = microsoftOpenAiChatClient;
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

		ChatRequestMessage azureChatMessage = new ChatRequestUserMessage(text);

		ChatCompletionsOptions options = new ChatCompletionsOptions(List.of(azureChatMessage));
		options.setTemperature(this.getTemperature());
		options.setModel(this.getModel());
		logger.trace("Azure Chat Message: {}", azureChatMessage);

		ChatCompletions chatCompletions = this.openAIClient.getChatCompletions(this.getModel(), options);
		logger.trace("Azure ChatCompletions: {}", chatCompletions);

		StringBuilder stringBuilder = new StringBuilder();

		for (ChatChoice choice : chatCompletions.getChoices()) {
			ChatResponseMessage message = choice.getMessage();
			if (message != null && message.getContent() != null) {
				stringBuilder.append(message.getContent());
			}
		}

		return stringBuilder.toString();
	}

	@Override
	public ChatResponse generate(Prompt prompt) {

		List<ChatRequestMessage> azureMessages = prompt.getMessages().stream().map(this::fromSpringAiMessage).toList();

		ChatCompletionsOptions options = new ChatCompletionsOptions(azureMessages);

		options.setTemperature(this.getTemperature());
		options.setModel(this.getModel());

		logger.trace("Azure ChatCompletionsOptions: {}", options);

		ChatCompletions chatCompletions = this.openAIClient.getChatCompletions(this.getModel(), options);

		logger.trace("Azure ChatCompletions: {}", chatCompletions);

		List<Generation> generations = chatCompletions.getChoices()
			.stream()
			.map(choice -> new Generation(choice.getMessage().getContent())
				.withChoiceMetadata(generateChoiceMetadata(choice)))
			.toList();

		return new ChatResponse(generations, AzureOpenAiGenerationMetadata.from(chatCompletions))
			.withPromptMetadata(generatePromptMetadata(chatCompletions));
	}

	private ChatRequestMessage fromSpringAiMessage(Message message) {

		switch (message.getMessageType()) {
			case USER:
				return new ChatRequestUserMessage(message.getContent());
			case SYSTEM:
				return new ChatRequestSystemMessage(message.getContent());
			case ASSISTANT:
				return new ChatRequestAssistantMessage(message.getContent());
			default:
				throw new IllegalArgumentException("Unknown message type " + message.getMessageType());
		}

	}

	private ChoiceMetadata generateChoiceMetadata(ChatChoice choice) {
		return ChoiceMetadata.from(String.valueOf(choice.getFinishReason()), choice.getContentFilterResults());
	}

	private PromptMetadata generatePromptMetadata(ChatCompletions chatCompletions) {

		List<ContentFilterResultsForPrompt> promptFilterResults = nullSafeList(
				chatCompletions.getPromptFilterResults());

		return PromptMetadata.of(promptFilterResults.stream()
			.map(promptFilterResult -> PromptFilterMetadata.from(promptFilterResult.getPromptIndex(),
					promptFilterResult.getContentFilterResults()))
			.toList());
	}

	private <T> List<T> nullSafeList(List<T> list) {
		return list != null ? list : Collections.emptyList();
	}

}