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

package org.springframework.ai.anthropicsdk.chat;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.anthropic.models.messages.Model;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.anthropicsdk.AnthropicSdkChatModel;
import org.springframework.ai.anthropicsdk.AnthropicSdkChatOptions;
import org.springframework.ai.anthropicsdk.AnthropicSdkTestConfiguration;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.ListOutputConverter;
import org.springframework.ai.converter.MapOutputConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link AnthropicSdkChatModel}.
 * <p>
 * These tests mirror the tests in AnthropicChatModelIT for feature parity verification.
 * Tests for features not yet implemented (streaming, tool calling, multi-modal, thinking)
 * will be added in subsequent phases.
 *
 * @author Soby Chacko
 */
@SpringBootTest(classes = AnthropicSdkTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class AnthropicSdkChatModelIT {

	private static final Logger logger = LoggerFactory.getLogger(AnthropicSdkChatModelIT.class);

	@Value("classpath:/prompts/system-message.st")
	private Resource systemResource;

	@Autowired
	private AnthropicSdkChatModel chatModel;

	private static void validateChatResponseMetadata(ChatResponse response, String model) {
		assertThat(response.getMetadata().getId()).isNotEmpty();
		assertThat(response.getMetadata().getUsage().getPromptTokens()).isPositive();
		assertThat(response.getMetadata().getUsage().getCompletionTokens()).isPositive();
		assertThat(response.getMetadata().getUsage().getTotalTokens()).isPositive();
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "claude-sonnet-4-20250514" })
	void roleTest(String modelName) {
		UserMessage userMessage = new UserMessage(
				"Tell me about 3 famous pirates from the Golden Age of Piracy and why they did.");
		SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(this.systemResource);
		Message systemMessage = systemPromptTemplate.createMessage(Map.of("name", "Bob", "voice", "pirate"));
		Prompt prompt = new Prompt(List.of(userMessage, systemMessage),
				AnthropicSdkChatOptions.builder().model(modelName).build());
		ChatResponse response = this.chatModel.call(prompt);
		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getMetadata().getUsage().getCompletionTokens()).isGreaterThan(0);
		assertThat(response.getMetadata().getUsage().getPromptTokens()).isGreaterThan(0);
		assertThat(response.getMetadata().getUsage().getTotalTokens())
			.isEqualTo(response.getMetadata().getUsage().getPromptTokens()
					+ response.getMetadata().getUsage().getCompletionTokens());
		Generation generation = response.getResults().get(0);
		assertThat(generation.getOutput().getText()).contains("Blackbeard");
		assertThat(generation.getMetadata().getFinishReason()).isEqualTo("end_turn");
		logger.info(response.toString());
	}

	@Test
	void testMessageHistory() {
		// First turn - ask about pirates
		UserMessage firstUserMessage = new UserMessage(
				"Tell me about 3 famous pirates from the Golden Age of Piracy and why they did.");
		SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(this.systemResource);
		Message systemMessage = systemPromptTemplate.createMessage(Map.of("name", "Bob", "voice", "pirate"));
		Prompt prompt = new Prompt(List.of(systemMessage, firstUserMessage),
				AnthropicSdkChatOptions.builder().model(Model.CLAUDE_SONNET_4_20250514).build());

		ChatResponse response = this.chatModel.call(prompt);
		assertThat(response.getResult().getOutput().getText()).containsAnyOf("Blackbeard", "Bartholomew");

		// Second turn - include the first exchange in history, then ask to repeat
		var promptWithMessageHistory = new Prompt(List.of(systemMessage, firstUserMessage,
				response.getResult().getOutput(), new UserMessage("Repeat the names of the pirates you mentioned.")));
		response = this.chatModel.call(promptWithMessageHistory);

		assertThat(response.getResult().getOutput().getText()).containsAnyOf("Blackbeard", "Bartholomew");
	}

	@Test
	void listOutputConverter() {
		DefaultConversionService conversionService = new DefaultConversionService();
		ListOutputConverter listOutputConverter = new ListOutputConverter(conversionService);

		String format = listOutputConverter.getFormat();
		String template = """
				List five {subject}
				{format}
				""";
		PromptTemplate promptTemplate = PromptTemplate.builder()
			.template(template)
			.variables(Map.of("subject", "ice cream flavors", "format", format))
			.build();
		Prompt prompt = new Prompt(promptTemplate.createMessage());
		Generation generation = this.chatModel.call(prompt).getResult();

		List<String> list = listOutputConverter.convert(generation.getOutput().getText());
		assertThat(list).hasSize(5);
	}

	@Test
	void mapOutputConverter() {
		MapOutputConverter mapOutputConverter = new MapOutputConverter();

		String format = mapOutputConverter.getFormat();
		String template = """
				Provide me a List of {subject}
				{format}
				""";
		PromptTemplate promptTemplate = PromptTemplate.builder()
			.template(template)
			.variables(Map.of("subject", "an array of numbers from 1 to 9 under they key name 'numbers'", "format",
					format))
			.build();
		Prompt prompt = new Prompt(promptTemplate.createMessage());
		Generation generation = this.chatModel.call(prompt).getResult();

		Map<String, Object> result = mapOutputConverter.convert(generation.getOutput().getText());
		assertThat(result.get("numbers")).isEqualTo(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9));
	}

	@Test
	void beanOutputConverterRecords() {
		BeanOutputConverter<ActorsFilmsRecord> beanOutputConverter = new BeanOutputConverter<>(ActorsFilmsRecord.class);

		String format = beanOutputConverter.getFormat();
		String template = """
				Generate the filmography of 5 movies for Tom Hanks.
				{format}
				""";
		PromptTemplate promptTemplate = PromptTemplate.builder()
			.template(template)
			.variables(Map.of("format", format))
			.build();
		Prompt prompt = new Prompt(promptTemplate.createMessage());
		Generation generation = this.chatModel.call(prompt).getResult();

		ActorsFilmsRecord actorsFilms = beanOutputConverter.convert(generation.getOutput().getText());
		logger.info("" + actorsFilms);
		assertThat(actorsFilms.actor()).isEqualTo("Tom Hanks");
		assertThat(actorsFilms.movies()).hasSize(5);
	}

	@Test
	void validateCallResponseMetadata() {
		String model = Model.CLAUDE_SONNET_4_20250514.asString();
		// @formatter:off
		ChatResponse response = ChatClient.create(this.chatModel).prompt()
				.options(AnthropicSdkChatOptions.builder().model(model).build())
				.user("Tell me about 3 famous pirates from the Golden Age of Piracy and what they did")
				.call()
				.chatResponse();
		// @formatter:on

		logger.info(response.toString());
		validateChatResponseMetadata(response, model);
	}

	// ==================================================================================
	// Tests below are placeholders for features to be implemented in subsequent phases.
	// They will be uncommented/added as each phase is completed.
	// ==================================================================================

	// Phase 2: Streaming tests
	// - streamingWithTokenUsage
	// - beanStreamOutputConverterRecords
	// - validateStreamCallResponseMetadata

	// Phase 3: Tool calling tests
	// - functionCallTest
	// - streamFunctionCallTest
	// - streamFunctionCallUsageTest
	// - testToolUseContentBlock
	// - testToolChoiceAny
	// - testToolChoiceTool
	// - testToolChoiceNone

	// Phase 4: Multi-modal tests
	// - multiModalityTest
	// - multiModalityPdfTest

	// Phase 5: Extended thinking tests
	// - thinkingTest
	// - thinkingWithStreamingTest

	record ActorsFilmsRecord(String actor, List<String> movies) {

	}

}
