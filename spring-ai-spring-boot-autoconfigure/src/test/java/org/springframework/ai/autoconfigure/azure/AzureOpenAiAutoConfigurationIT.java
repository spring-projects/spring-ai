/*
 * Copyright 2023-2023 the original author or authors.
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

package org.springframework.ai.autoconfigure.azure;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.messages.AssistantMessage;
import reactor.core.publisher.Flux;

import org.springframework.ai.autoconfigure.azure.openai.AzureOpenAiAutoConfiguration;
import org.springframework.ai.azure.openai.AzureOpenAiChatClient;
import org.springframework.ai.azure.openai.AzureOpenAiEmbeddingClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.Generation;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 * @since 0.8.0
 */
@EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_ENDPOINT", matches = ".+")
public class AzureOpenAiAutoConfigurationIT {

	private static String CHAT_MODEL_NAME = "gpt-35-turbo";

	private static String EMBEDDING_MODEL_NAME = "text-embedding-ada-002";

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner().withPropertyValues(
	// @formatter:off
			"spring.ai.azure.openai.api-key=" + System.getenv("AZURE_OPENAI_API_KEY"),
			"spring.ai.azure.openai.endpoint=" + System.getenv("AZURE_OPENAI_ENDPOINT"),

			"spring.ai.azure.openai.chat.options.model=" + CHAT_MODEL_NAME,
			"spring.ai.azure.openai.chat.options.temperature=0.8",
			"spring.ai.azure.openai.chat.options.maxTokens=123",

			"spring.ai.azure.openai.embedding.options.model=" + EMBEDDING_MODEL_NAME
			// @formatter:on
	).withConfiguration(AutoConfigurations.of(AzureOpenAiAutoConfiguration.class));

	private final Message systemMessage = new SystemPromptTemplate("""
			You are a helpful AI assistant. Your name is {name}.
			You are an AI assistant that helps people find information.
			Your name is {name}
			You should reply to the user's request with your name and also in the style of a {voice}.
			""").createMessage(Map.of("name", "Bob", "voice", "pirate"));

	private final UserMessage userMessage = new UserMessage(
			"Tell me about 3 famous pirates from the Golden Age of Piracy and why they did.");

	@Test
	public void chatCompletion() {
		contextRunner.run(context -> {
			AzureOpenAiChatClient chatClient = context.getBean(AzureOpenAiChatClient.class);
			ChatResponse response = chatClient.call(new Prompt(List.of(userMessage, systemMessage)));
			assertThat(response.getResult().getOutput().getContent()).contains("Blackbeard");
		});
	}

	@Test
	public void chatCompletionStreaming() {
		contextRunner.run(context -> {

			AzureOpenAiChatClient chatClient = context.getBean(AzureOpenAiChatClient.class);

			Flux<ChatResponse> response = chatClient.stream(new Prompt(List.of(userMessage, systemMessage)));

			List<ChatResponse> responses = response.collectList().block();
			assertThat(responses.size()).isGreaterThan(1);

			String stitchedResponseContent = responses.stream()
				.map(ChatResponse::getResults)
				.flatMap(List::stream)
				.map(Generation::getOutput)
				.map(AssistantMessage::getContent)
				.collect(Collectors.joining());

			assertThat(stitchedResponseContent).contains("Blackbeard");
		});
	}

	@Test
	void embedding() {
		contextRunner.run(context -> {
			AzureOpenAiEmbeddingClient embeddingClient = context.getBean(AzureOpenAiEmbeddingClient.class);

			EmbeddingResponse embeddingResponse = embeddingClient
				.embedForResponse(List.of("Hello World", "World is big and salvation is near"));
			assertThat(embeddingResponse.getResults()).hasSize(2);
			assertThat(embeddingResponse.getResults().get(0).getOutput()).isNotEmpty();
			assertThat(embeddingResponse.getResults().get(0).getIndex()).isEqualTo(0);
			assertThat(embeddingResponse.getResults().get(1).getOutput()).isNotEmpty();
			assertThat(embeddingResponse.getResults().get(1).getIndex()).isEqualTo(1);

			assertThat(embeddingClient.dimensions()).isEqualTo(1536);
		});
	}

}
