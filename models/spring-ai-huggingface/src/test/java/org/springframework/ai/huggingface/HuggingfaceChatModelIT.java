/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.huggingface;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link HuggingfaceChatModel}.
 *
 * @author Mark Pollack
 * @author Jihoon Kim
 */
@SpringBootTest(classes = HuggingfaceTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "HUGGINGFACE_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "HUGGINGFACE_CHAT_URL", matches = ".+")
class HuggingfaceChatModelIT {

	@Autowired
	private HuggingfaceChatModel chatModel;

	@Test
	void simpleCallTest() {
		UserMessage userMessage = new UserMessage("What is the capital of France?");
		Prompt prompt = new Prompt(List.of(userMessage));

		ChatResponse response = this.chatModel.call(prompt);

		assertThat(response).isNotNull();
		assertThat(response.getResults()).isNotEmpty();
		assertThat(response.getResult().getOutput().getText()).isNotEmpty();
		assertThat(response.getResult().getOutput().getText().toLowerCase()).contains("paris");
	}

	@Test
	void roleTest() {
		Message systemMessage = new SystemPromptTemplate("""
				You are a helpful AI assistant. Your name is {name}.
				You should reply to the user's request with your name and in a {voice} style.
				""").createMessage(Map.of("name", "Alice", "voice", "friendly"));

		UserMessage userMessage = new UserMessage("Tell me about yourself.");

		Prompt prompt = new Prompt(List.of(systemMessage, userMessage));

		ChatResponse response = this.chatModel.call(prompt);

		assertThat(response).isNotNull();
		assertThat(response.getResults()).isNotEmpty();
		assertThat(response.getResult().getOutput().getText()).isNotEmpty();
		String responseText = response.getResult().getOutput().getText();
		assertThat(responseText.toLowerCase()).containsAnyOf("alice", "assistant");
	}

	@Test
	void testMessageHistory() {
		UserMessage initialMessage = new UserMessage("My favorite color is blue.");
		Prompt initialPrompt = new Prompt(List.of(initialMessage));

		ChatResponse initialResponse = this.chatModel.call(initialPrompt);
		assertThat(initialResponse).isNotNull();

		AssistantMessage assistantMessage = initialResponse.getResult().getOutput();

		UserMessage followUpMessage = new UserMessage("What is my favorite color?");
		Prompt followUpPrompt = new Prompt(List.of(initialMessage, assistantMessage, followUpMessage));

		ChatResponse followUpResponse = this.chatModel.call(followUpPrompt);

		assertThat(followUpResponse).isNotNull();
		assertThat(followUpResponse.getResult().getOutput().getText().toLowerCase()).contains("blue");
	}

	@Test
	void testPromptTemplate() {
		PromptTemplate promptTemplate = new PromptTemplate("Tell me a {adjective} joke about {topic}");
		Prompt prompt = promptTemplate.create(Map.of("adjective", "funny", "topic", "programming"));

		ChatResponse response = this.chatModel.call(prompt);

		assertThat(response).isNotNull();
		assertThat(response.getResults()).isNotEmpty();
		assertThat(response.getResult().getOutput().getText()).isNotEmpty();
	}

	@Test
	void testMultipleResults() {
		UserMessage userMessage = new UserMessage("Write a haiku about spring.");
		Prompt prompt = new Prompt(List.of(userMessage));

		ChatResponse response = this.chatModel.call(prompt);

		assertThat(response).isNotNull();
		assertThat(response.getResults()).isNotEmpty();

		for (Generation generation : response.getResults()) {
			assertThat(generation.getOutput()).isNotNull();
			assertThat(generation.getOutput().getText()).isNotEmpty();
		}
	}

	@Test
	void testMaxNewTokensConfiguration() {
		int originalMaxTokens = this.chatModel.getMaxNewTokens();

		this.chatModel.setMaxNewTokens(50);
		assertThat(this.chatModel.getMaxNewTokens()).isEqualTo(50);

		UserMessage userMessage = new UserMessage("Tell me a story.");
		Prompt prompt = new Prompt(List.of(userMessage));

		ChatResponse response = this.chatModel.call(prompt);

		assertThat(response).isNotNull();
		assertThat(response.getResult().getOutput().getText()).isNotEmpty();

		this.chatModel.setMaxNewTokens(originalMaxTokens);
	}

	@Test
	void testEmptyPromptHandling() {
		Prompt prompt = new Prompt("");

		ChatResponse response = this.chatModel.call(prompt);

		assertThat(response).isNotNull();
		assertThat(response.getResults()).isNotEmpty();
	}

	@Test
	void testLongContextHandling() {
		StringBuilder longContext = new StringBuilder();
		for (int i = 0; i < 100; i++) {
			longContext.append("This is sentence number ").append(i).append(". ");
		}
		longContext.append("What number did I just mention?");

		UserMessage userMessage = new UserMessage(longContext.toString());
		Prompt prompt = new Prompt(List.of(userMessage));

		ChatResponse response = this.chatModel.call(prompt);

		assertThat(response).isNotNull();
		assertThat(response.getResult().getOutput().getText()).isNotEmpty();
	}

	@Test
	void testMultipleMessagesInPrompt() {
		Message systemMessage = new SystemPromptTemplate("You are a math tutor.").createMessage();
		UserMessage userMessage1 = new UserMessage("What is 2 + 2?");
		AssistantMessage assistantMessage = new AssistantMessage("2 + 2 equals 4.");
		UserMessage userMessage2 = new UserMessage("What about 3 + 3?");

		Prompt prompt = new Prompt(List.of(systemMessage, userMessage1, assistantMessage, userMessage2));

		ChatResponse response = this.chatModel.call(prompt);

		assertThat(response).isNotNull();
		assertThat(response.getResult().getOutput().getText()).isNotEmpty();
		assertThat(response.getResult().getOutput().getText()).containsAnyOf("6", "six");
	}

	@Test
	void testSpecialCharactersInPrompt() {
		UserMessage userMessage = new UserMessage(
				"Translate this to French: Hello! How are you? I'm fine, thank you.");
		Prompt prompt = new Prompt(List.of(userMessage));

		ChatResponse response = this.chatModel.call(prompt);

		assertThat(response).isNotNull();
		assertThat(response.getResult().getOutput().getText()).isNotEmpty();
	}

	@Test
	void testGenerationMetadata() {
		UserMessage userMessage = new UserMessage("Say hello");
		Prompt prompt = new Prompt(List.of(userMessage));

		ChatResponse response = this.chatModel.call(prompt);

		assertThat(response).isNotNull();
		assertThat(response.getResults()).isNotEmpty();

		Generation generation = response.getResult();
		assertThat(generation.getOutput()).isNotNull();
		assertThat(generation.getOutput()).isInstanceOf(AssistantMessage.class);

		AssistantMessage assistantMessage = (AssistantMessage) generation.getOutput();
		assertThat(assistantMessage.getProperties()).isNotNull();
	}

	@Test
	void testConsecutiveCalls() {
		UserMessage userMessage1 = new UserMessage("What is 1 + 1?");
		Prompt prompt1 = new Prompt(List.of(userMessage1));

		ChatResponse response1 = this.chatModel.call(prompt1);
		assertThat(response1).isNotNull();
		assertThat(response1.getResult().getOutput().getText()).isNotEmpty();

		UserMessage userMessage2 = new UserMessage("What is 2 + 2?");
		Prompt prompt2 = new Prompt(List.of(userMessage2));

		ChatResponse response2 = this.chatModel.call(prompt2);
		assertThat(response2).isNotNull();
		assertThat(response2.getResult().getOutput().getText()).isNotEmpty();

		assertThat(response1.getResult().getOutput().getText())
			.isNotEqualTo(response2.getResult().getOutput().getText());
	}

}
