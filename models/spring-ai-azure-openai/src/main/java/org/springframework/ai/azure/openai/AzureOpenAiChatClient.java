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

package org.springframework.ai.azure.openai;

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
import com.azure.core.util.IterableStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import reactor.core.publisher.Flux;

import org.springframework.ai.azure.openai.metadata.AzureOpenAiChatResponseMetadata;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.Generation;
import org.springframework.ai.chat.StreamingChatClient;
import org.springframework.ai.chat.metadata.PromptMetadata;
import org.springframework.ai.chat.metadata.PromptMetadata.PromptFilterMetadata;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.Message;
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
public class AzureOpenAiChatClient implements ChatClient, StreamingChatClient {

	/**
	 * The sampling temperature to use that controls the apparent creativity of generated
	 * completions. Higher values will make output more random while lower values will
	 * make results more focused and deterministic. It is not recommended to modify
	 * temperature and top_p for the same completions request as the interaction of these
	 * two settings is difficult to predict.
	 */
	private Double temperature = 0.7;

	/**
	 * An alternative to sampling with temperature called nucleus sampling. This value
	 * causes the model to consider the results of tokens with the provided probability
	 * mass. As an example, a value of 0.15 will cause only the tokens comprising the top
	 * 15% of probability mass to be considered. It is not recommended to modify
	 * temperature and top_p for the same completions request as the interaction of these
	 * two settings is difficult to predict.
	 */
	private Double topP;

	/**
	 * Creates an instance of ChatCompletionsOptions class.
	 */
	private String model = "gpt-35-turbo";

	/**
	 * The maximum number of tokens to generate.
	 */
	private Integer maxTokens;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final OpenAIClient openAIClient;

	public AzureOpenAiChatClient(OpenAIClient microsoftOpenAiClient) {
		Assert.notNull(microsoftOpenAiClient, "com.azure.ai.openai.OpenAIClient must not be null");
		this.openAIClient = microsoftOpenAiClient;
	}

	public String getModel() {
		return this.model;
	}

	public AzureOpenAiChatClient withModel(String model) {
		this.model = model;
		return this;
	}

	public Double getTemperature() {
		return this.temperature;
	}

	public AzureOpenAiChatClient withTemperature(Double temperature) {
		this.temperature = temperature;
		return this;
	}

	public Double getTopP() {
		return topP;
	}

	public AzureOpenAiChatClient withTopP(Double topP) {
		this.topP = topP;
		return this;
	}

	public Integer getMaxTokens() {
		return maxTokens;
	}

	public AzureOpenAiChatClient withMaxTokens(Integer maxTokens) {
		this.maxTokens = maxTokens;
		return this;
	}

	@Override
	public String call(String text) {

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
	public ChatResponse call(Prompt prompt) {

		ChatCompletionsOptions options = toAzureChatCompletionsOptions(prompt);
		options.setStream(false);

		logger.trace("Azure ChatCompletionsOptions: {}", options);

		ChatCompletions chatCompletions = this.openAIClient.getChatCompletions(this.getModel(), options);

		logger.trace("Azure ChatCompletions: {}", chatCompletions);

		List<Generation> generations = chatCompletions.getChoices()
			.stream()
			.map(choice -> new Generation(choice.getMessage().getContent())
				.withGenerationMetadata(generateChoiceMetadata(choice)))
			.toList();

		PromptMetadata promptFilterMetadata = generatePromptMetadata(chatCompletions);
		return new ChatResponse(generations,
				AzureOpenAiChatResponseMetadata.from(chatCompletions, promptFilterMetadata));
	}

	@Override
	public Flux<ChatResponse> streamingCall(Prompt prompt) {

		ChatCompletionsOptions options = toAzureChatCompletionsOptions(prompt);
		options.setStream(true);

		IterableStream<ChatCompletions> chatCompletionsStream = this.openAIClient
			.getChatCompletionsStream(this.getModel(), options);

		return Flux.fromStream(chatCompletionsStream.stream()
			// Note: the first chat completions can be ignored when using Azure OpenAI
			// service which is a
			// known service bug.
			.skip(1)
			.map(ChatCompletions::getChoices)
			.flatMap(List::stream)
			.map(choice -> {
				var content = (choice.getDelta() != null) ? choice.getDelta().getContent() : null;
				var generation = new Generation(content).withGenerationMetadata(generateChoiceMetadata(choice));
				return new ChatResponse(List.of(generation));
			}));
	}

	private ChatCompletionsOptions toAzureChatCompletionsOptions(Prompt prompt) {

		List<ChatRequestMessage> azureMessages = prompt.getInstructions()
			.stream()
			.map(this::fromSpringAiMessage)
			.toList();

		ChatCompletionsOptions options = new ChatCompletionsOptions(azureMessages);

		options.setTemperature(this.getTemperature());
		options.setModel(this.getModel());
		options.setTopP(this.getTopP());
		options.setMaxTokens(this.getMaxTokens());

		return options;
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

	private ChatGenerationMetadata generateChoiceMetadata(ChatChoice choice) {
		return ChatGenerationMetadata.from(String.valueOf(choice.getFinishReason()), choice.getContentFilterResults());
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