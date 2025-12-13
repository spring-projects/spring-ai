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

package org.springframework.ai.mistralai;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.mistralai.api.MistralAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Mistral AI Magistral reasoning models. These tests verify that
 * the Magistral models (magistral-small-latest, magistral-medium-latest) properly return
 * thinking/reasoning content alongside the regular response.
 *
 * <p>
 * Magistral models are reasoning models that show their thought process before providing
 * an answer. The thinking content is returned in a separate field from the main response
 * content.
 * </p>
 *
 * @author Kyle Kreuter
 */
@SpringBootTest(classes = MistralAiTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "MISTRAL_AI_API_KEY", matches = ".+")
class MistralAiMagistralIT {

	private static final Logger logger = LoggerFactory.getLogger(MistralAiMagistralIT.class);

	@Autowired
	private ChatModel chatModel;

	@Autowired
	private StreamingChatModel streamingChatModel;

	@Test
	void testMagistralModelReturnsThinkingContent() {
		// Magistral models excel at reasoning tasks - use a question that requires
		// step-by-step thinking
		var promptOptions = MistralAiChatOptions.builder()
			.model(MistralAiApi.ChatModel.MAGISTRAL_SMALL.getValue())
			.build();

		Prompt prompt = new Prompt("9.11 and 9.8, which is greater?", promptOptions);
		ChatResponse response = this.chatModel.call(prompt);

		assertThat(response).isNotNull();
		assertThat(response.getResult()).isNotNull();
		assertThat(response.getResult().getOutput()).isInstanceOf(MistralAiAssistantMessage.class);

		MistralAiAssistantMessage assistantMessage = (MistralAiAssistantMessage) response.getResult().getOutput();

		// Magistral models should provide thinking content for reasoning questions
		assertThat(assistantMessage.getThinkingContent()).isNotNull().isNotEmpty();
		assertThat(assistantMessage.getText()).isNotNull().isNotEmpty();

		logger.info("Thinking content: {}", assistantMessage.getThinkingContent());
		logger.info("Response text: {}", assistantMessage.getText());
	}

	@Test
	void testMagistralModelHandlesMathProblemsWithReasoning() {
		var promptOptions = MistralAiChatOptions.builder()
			.model(MistralAiApi.ChatModel.MAGISTRAL_SMALL.getValue())
			.build();

		Prompt prompt = new Prompt(
				"If a train travels at 60 mph for 2.5 hours, how far does it travel? Show your reasoning.",
				promptOptions);
		ChatResponse response = this.chatModel.call(prompt);

		assertThat(response).isNotNull();
		assertThat(response.getResult()).isNotNull();

		MistralAiAssistantMessage assistantMessage = (MistralAiAssistantMessage) response.getResult().getOutput();

		assertThat(assistantMessage.getThinkingContent()).isNotNull().isNotEmpty();
		assertThat(assistantMessage.getText()).isNotNull();

		// The answer should contain 150 (60 * 2.5 = 150 miles)
		assertThat(assistantMessage.getText()).containsAnyOf("150", "one hundred fifty");

		logger.info("Math problem thinking: {}", assistantMessage.getThinkingContent());
		logger.info("Math problem answer: {}", assistantMessage.getText());
	}

	@Test
	void testMagistralModelStreamingWorks() {
		var promptOptions = MistralAiChatOptions.builder()
			.model(MistralAiApi.ChatModel.MAGISTRAL_SMALL.getValue())
			.build();

		Prompt prompt = new Prompt("What is 25 * 4? Think step by step.", promptOptions);

		String aggregatedContent = this.streamingChatModel.stream(prompt)
			.collectList()
			.block()
			.stream()
			.map(ChatResponse::getResults)
			.flatMap(List::stream)
			.map(Generation::getOutput)
			.map(AssistantMessage::getText)
			.filter(text -> text != null)
			.collect(Collectors.joining());

		assertThat(aggregatedContent).isNotEmpty();
		// The answer should contain 100 (25 * 4 = 100)
		assertThat(aggregatedContent).containsAnyOf("100", "one hundred");

		logger.info("Streamed response: {}", aggregatedContent);
	}

	@Test
	void testMagistralModelMultiRoundConversationPreservesContext() {
		List<Message> messages = new ArrayList<>();
		messages.add(new UserMessage("What is 5 + 3?"));

		var promptOptions = MistralAiChatOptions.builder()
			.model(MistralAiApi.ChatModel.MAGISTRAL_SMALL.getValue())
			.build();

		// First round
		Prompt prompt1 = new Prompt(messages, promptOptions);
		ChatResponse response1 = this.chatModel.call(prompt1);

		assertThat(response1).isNotNull();
		MistralAiAssistantMessage message1 = (MistralAiAssistantMessage) response1.getResult().getOutput();
		assertThat(message1.getText()).containsAnyOf("8", "eight");

		logger.info("First response thinking: {}", message1.getThinkingContent());
		logger.info("First response: {}", message1.getText());

		// Add assistant response to conversation
		messages.add(new AssistantMessage(message1.getText()));
		messages.add(new UserMessage("Now multiply that result by 2"));

		// Second round
		Prompt prompt2 = new Prompt(messages, promptOptions);
		ChatResponse response2 = this.chatModel.call(prompt2);

		assertThat(response2).isNotNull();
		MistralAiAssistantMessage message2 = (MistralAiAssistantMessage) response2.getResult().getOutput();
		// 8 * 2 = 16
		assertThat(message2.getText()).containsAnyOf("16", "sixteen");

		logger.info("Second response thinking: {}", message2.getThinkingContent());
		logger.info("Second response: {}", message2.getText());
	}

	@Test
	void testMagistralModelHandlesLogicPuzzles() {
		var promptOptions = MistralAiChatOptions.builder()
			.model(MistralAiApi.ChatModel.MAGISTRAL_SMALL.getValue())
			.build();

		String puzzle = """
				There are three boxes. One contains only apples, one contains only oranges,
				and one contains both apples and oranges. The boxes have been incorrectly labeled
				such that no label identifies the actual contents of the box it labels.
				Opening just one box, and without looking in the box, you take out one piece of fruit.
				By looking at the fruit, how can you immediately label all of the boxes correctly?
				Which box should you open?
				""";

		Prompt prompt = new Prompt(puzzle, promptOptions);
		ChatResponse response = this.chatModel.call(prompt);

		assertThat(response).isNotNull();
		MistralAiAssistantMessage assistantMessage = (MistralAiAssistantMessage) response.getResult().getOutput();

		// For reasoning puzzles, thinking content should be substantial
		assertThat(assistantMessage.getThinkingContent()).isNotNull().isNotEmpty();
		assertThat(assistantMessage.getText()).isNotNull().isNotEmpty();

		// The answer should mention the "both" or "mixed" box
		assertThat(assistantMessage.getText().toLowerCase()).containsAnyOf("both", "mixed", "apples and oranges");

		logger.info("Logic puzzle thinking (length: {}): {}",
				assistantMessage.getThinkingContent() != null ? assistantMessage.getThinkingContent().length() : 0,
				assistantMessage.getThinkingContent());
		logger.info("Logic puzzle answer: {}", assistantMessage.getText());
	}

	@Test
	void testResponseMetadataPopulatedCorrectly() {
		var promptOptions = MistralAiChatOptions.builder()
			.model(MistralAiApi.ChatModel.MAGISTRAL_SMALL.getValue())
			.build();

		Prompt prompt = new Prompt("What is 2 + 2?", promptOptions);
		ChatResponse response = this.chatModel.call(prompt);

		assertThat(response).isNotNull();
		assertThat(response.getMetadata()).isNotNull();
		assertThat(response.getMetadata().getModel()).containsIgnoringCase("magistral");
		assertThat(response.getMetadata().getUsage()).isNotNull();
		assertThat(response.getMetadata().getUsage().getPromptTokens()).isGreaterThan(0);
		assertThat(response.getMetadata().getUsage().getCompletionTokens()).isGreaterThan(0);
		assertThat(response.getMetadata().getUsage().getTotalTokens()).isGreaterThan(0);

		logger.info("Model used: {}", response.getMetadata().getModel());
		logger.info("Token usage - Prompt: {}, Completion: {}, Total: {}",
				response.getMetadata().getUsage().getPromptTokens(),
				response.getMetadata().getUsage().getCompletionTokens(),
				response.getMetadata().getUsage().getTotalTokens());
	}

}
