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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.ListOutputConverter;
import org.springframework.ai.converter.MapOutputConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.support.DefaultConversionService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link HuggingfaceChatModel}. These tests require a valid
 * HuggingFace API key set in the HUGGINGFACE_API_KEY environment variable.
 *
 * @author Myeongdeok Kang
 */
@EnabledIfEnvironmentVariable(named = "HUGGINGFACE_API_KEY", matches = ".+")
class HuggingfaceChatModelIT extends BaseHuggingfaceIT {

	@Autowired
	private HuggingfaceChatModel chatModel;

	@Test
	void roleTest() {
		Message systemMessage = new SystemPromptTemplate("""
				You are a helpful AI assistant. Your name is {name}.
				You are an AI assistant that helps people find information.
				Your name is {name}
				You should reply to the user's request with your name and also in the style of a {voice}.
				""").createMessage(Map.of("name", "Bob", "voice", "pirate"));

		UserMessage userMessage = new UserMessage("Tell me about 3 famous pirates from the Golden Age of Piracy.");

		// portable/generic options
		var portableOptions = ChatOptions.builder().temperature(0.7).build();

		Prompt prompt = new Prompt(List.of(systemMessage, userMessage), portableOptions);

		ChatResponse response = this.chatModel.call(prompt);
		assertThat(response.getResult().getOutput().getText()).isNotEmpty();

		// huggingface specific options
		var huggingfaceOptions = HuggingfaceChatOptions.builder().temperature(0.8).maxTokens(200).build();

		response = this.chatModel.call(new Prompt(List.of(systemMessage, userMessage), huggingfaceOptions));
		assertThat(response.getResult().getOutput().getText()).isNotEmpty();
	}

	@Test
	void testMessageHistory() {
		Message systemMessage = new SystemPromptTemplate("""
				You are a helpful AI assistant. Your name is {name}.
				You are an AI assistant that helps people find information.
				Your name is {name}
				You should reply to the user's request with your name and also in the style of a {voice}.
				""").createMessage(Map.of("name", "Bob", "voice", "pirate"));

		UserMessage userMessage = new UserMessage(
				"Tell me about 3 famous pirates from the Golden Age of Piracy and why they were famous.");

		Prompt prompt = new Prompt(List.of(systemMessage, userMessage));

		ChatResponse response = this.chatModel.call(prompt);
		assertThat(response.getResult().getOutput().getText()).isNotEmpty();

		var promptWithMessageHistory = new Prompt(List.of(new UserMessage("Hello"), response.getResult().getOutput(),
				new UserMessage("Tell me just the names of those pirates.")));
		response = this.chatModel.call(promptWithMessageHistory);

		assertThat(response.getResult().getOutput().getText()).isNotEmpty();
	}

	@Test
	void simplePromptTest() {
		Prompt prompt = new Prompt("Tell me a short joke about programming");
		ChatResponse response = this.chatModel.call(prompt);

		assertThat(response).isNotNull();
		assertThat(response.getResult()).isNotNull();
		assertThat(response.getResult().getOutput().getText()).isNotEmpty();
	}

	@Test
	void usageTest() {
		Prompt prompt = new Prompt("Tell me a short joke");
		ChatResponse response = this.chatModel.call(prompt);
		Usage usage = response.getMetadata().getUsage();

		assertThat(usage).isNotNull();
		assertThat(usage.getPromptTokens()).isPositive();
		assertThat(usage.getCompletionTokens()).isPositive();
		assertThat(usage.getTotalTokens()).isPositive();
		assertThat(usage.getTotalTokens()).isEqualTo(usage.getPromptTokens() + usage.getCompletionTokens());
	}

	@Test
	void listOutputConverter() {
		DefaultConversionService conversionService = new DefaultConversionService();
		ListOutputConverter outputConverter = new ListOutputConverter(conversionService);

		String format = outputConverter.getFormat();
		String template = """
				List five {subject}
				{format}
				""";
		PromptTemplate promptTemplate = PromptTemplate.builder()
			.template(template)
			.variables(Map.of("subject", "ice cream flavors.", "format", format))
			.build();
		Prompt prompt = new Prompt(promptTemplate.createMessage());
		Generation generation = this.chatModel.call(prompt).getResult();

		List<String> list = outputConverter.convert(generation.getOutput().getText());
		assertThat(list).hasSizeGreaterThanOrEqualTo(3); // At least 3 items
	}

	@Test
	void mapOutputConverter() {
		MapOutputConverter outputConverter = new MapOutputConverter();

		String format = outputConverter.getFormat();
		String template = """
				For each letter in the RGB color scheme, tell me what it stands for.
				Example: R -> Red.
				{format}
				""";
		PromptTemplate promptTemplate = PromptTemplate.builder()
			.template(template)
			.variables(Map.of("format", format))
			.build();
		Prompt prompt = new Prompt(promptTemplate.createMessage());

		Generation generation = this.chatModel.call(prompt).getResult();

		Map<String, Object> result = outputConverter.convert(generation.getOutput().getText());
		assertThat(result).isNotNull();
		assertThat(result).containsKeys("R", "G", "B");
	}

	@Test
	void beanOutputConverterRecords() {
		BeanOutputConverter<ActorsFilmsRecord> outputConverter = new BeanOutputConverter<>(ActorsFilmsRecord.class);

		String format = outputConverter.getFormat();
		String template = """
				Generate the filmography of 3 movies for Tom Hanks.
				{format}
				""";
		PromptTemplate promptTemplate = PromptTemplate.builder()
			.template(template)
			.variables(Map.of("format", format))
			.build();

		// Set higher maxTokens and lower temperature to ensure complete JSON response
		HuggingfaceChatOptions options = HuggingfaceChatOptions.builder().maxTokens(1000).temperature(0.1).build();

		Prompt prompt = new Prompt(promptTemplate.createMessage(), options);
		Generation generation = this.chatModel.call(prompt).getResult();

		ActorsFilmsRecord actorsFilms = outputConverter.convert(generation.getOutput().getText());
		assertThat(actorsFilms.actor()).containsIgnoringCase("Tom Hanks");
		assertThat(actorsFilms.movies()).hasSizeGreaterThanOrEqualTo(3);
	}

	@Test
	void chatMemory() {
		ChatMemory memory = MessageWindowChatMemory.builder().build();
		String conversationId = UUID.randomUUID().toString();

		UserMessage userMessage1 = new UserMessage("My name is James Bond");
		memory.add(conversationId, userMessage1);
		ChatResponse response1 = this.chatModel.call(new Prompt(memory.get(conversationId)));

		assertThat(response1).isNotNull();
		memory.add(conversationId, response1.getResult().getOutput());

		UserMessage userMessage2 = new UserMessage("What is my name?");
		memory.add(conversationId, userMessage2);
		ChatResponse response2 = this.chatModel.call(new Prompt(memory.get(conversationId)));

		assertThat(response2).isNotNull();
		memory.add(conversationId, response2.getResult().getOutput());

		assertThat(response2.getResults()).hasSize(1);
		assertThat(response2.getResult().getOutput().getText()).containsIgnoringCase("James Bond");
	}

	@Test
	void chatClientSimplePrompt() {
		String joke = ChatClient.create(this.chatModel).prompt("Tell me a joke about developers").call().content();

		assertThat(joke).isNotEmpty();
	}

	@Test
	void customOptionsTest() {
		HuggingfaceChatOptions customOptions = HuggingfaceChatOptions.builder()
			.model(DEFAULT_CHAT_MODEL)
			.temperature(0.3)
			.maxTokens(50)
			.build();

		Prompt prompt = new Prompt("Say 'Hello'", customOptions);
		ChatResponse response = this.chatModel.call(prompt);

		assertThat(response).isNotNull();
		assertThat(response.getResult().getOutput().getText()).isNotEmpty();
	}

	@Test
	void multipleGenerationsTest() {
		Prompt prompt = new Prompt("What is 2 + 2?");
		ChatResponse response = this.chatModel.call(prompt);

		assertThat(response).isNotNull();
		assertThat(response.getResults()).isNotEmpty();
		assertThat(response.getResult().getOutput().getText()).contains("4");
	}

	@Test
	void testStopSequences() {
		List<String> stopSequences = Arrays.asList("STOP", "END");
		HuggingfaceChatOptions options = HuggingfaceChatOptions.builder()
			.model(DEFAULT_CHAT_MODEL)
			.temperature(0.7)
			.maxTokens(100)
			.stopSequences(stopSequences)
			.build();

		Prompt prompt = new Prompt("Count from 1 to 10. When you see STOP, stop counting.", options);
		ChatResponse response = this.chatModel.call(prompt);

		assertThat(response).isNotNull();
		assertThat(response.getResult().getOutput().getText()).isNotEmpty();
		// The response should be limited by stop sequences
	}

	@Test
	void testSeedForReproducibility() {
		int seed = 42;
		HuggingfaceChatOptions options = HuggingfaceChatOptions.builder()
			.model(DEFAULT_CHAT_MODEL)
			.temperature(0.7)
			.seed(seed)
			.build();

		Prompt prompt = new Prompt("Tell me a random number between 1 and 100", options);

		// Call twice with the same seed
		ChatResponse response1 = this.chatModel.call(prompt);
		ChatResponse response2 = this.chatModel.call(prompt);

		assertThat(response1).isNotNull();
		assertThat(response2).isNotNull();
		assertThat(response1.getResult().getOutput().getText()).isNotEmpty();
		assertThat(response2.getResult().getOutput().getText()).isNotEmpty();
		// With the same seed, responses should be deterministic (same or very similar)
	}

	@Test
	void testResponseFormatJsonObject() {
		Map<String, Object> responseFormat = new HashMap<>();
		responseFormat.put("type", "json_object");

		HuggingfaceChatOptions options = HuggingfaceChatOptions.builder()
			.model(DEFAULT_CHAT_MODEL)
			.temperature(0.7)
			.maxTokens(200)
			.responseFormat(responseFormat)
			.build();

		Prompt prompt = new Prompt(
				"Generate a JSON object with fields: name (string), age (number), city (string). Make up the values.",
				options);
		ChatResponse response = this.chatModel.call(prompt);

		assertThat(response).isNotNull();
		String output = response.getResult().getOutput().getText();
		assertThat(output).isNotEmpty();
		// The output should be valid JSON when response_format is json_object
		assertThat(output).contains("{");
		assertThat(output).contains("}");
	}

	record ActorsFilmsRecord(String actor, List<String> movies) {
	}

}
