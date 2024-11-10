/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.ollama;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptionsBuilder;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.ListOutputConverter;
import org.springframework.ai.converter.MapOutputConverter;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaModel;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.ollama.management.OllamaModelManager;
import org.springframework.ai.ollama.management.PullModelStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.support.DefaultConversionService;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OllamaChatModelIT extends BaseOllamaIT {

	private static final String MODEL = OllamaModel.LLAMA3_2.getName();

	private static final String ADDITIONAL_MODEL = "tinyllama";

	@Autowired
	private OllamaChatModel chatModel;

	@Autowired
	private OllamaApi ollamaApi;

	@Test
	void autoPullModelTest() {
		var modelManager = new OllamaModelManager(this.ollamaApi);
		assertThat(modelManager.isModelAvailable(ADDITIONAL_MODEL)).isTrue();

		String joke = ChatClient.create(this.chatModel)
			.prompt("Tell me a joke")
			.options(OllamaOptions.builder().withModel(ADDITIONAL_MODEL).build())
			.call()
			.content();

		assertThat(joke).isNotEmpty();

		modelManager.deleteModel(ADDITIONAL_MODEL);
	}

	@Test
	void roleTest() {
		Message systemMessage = new SystemPromptTemplate("""
				You are a helpful AI assistant. Your name is {name}.
				You are an AI assistant that helps people find information.
				Your name is {name}
				You should reply to the user's request with your name and also in the style of a {voice}.
				""").createMessage(Map.of("name", "Bob", "voice", "pirate"));

		UserMessage userMessage = new UserMessage("Tell me about 5 famous pirates from the Golden Age of Piracy.");

		// portable/generic options
		var portableOptions = ChatOptionsBuilder.builder().withTemperature(0.7).build();

		Prompt prompt = new Prompt(List.of(systemMessage, userMessage), portableOptions);

		ChatResponse response = this.chatModel.call(prompt);
		assertThat(response.getResult().getOutput().getContent()).contains("Blackbeard");

		// ollama specific options
		var ollamaOptions = new OllamaOptions().withLowVRAM(true);

		response = this.chatModel.call(new Prompt(List.of(systemMessage, userMessage), ollamaOptions));
		assertThat(response.getResult().getOutput().getContent()).contains("Blackbeard");
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
				"Tell me about 3 famous pirates from the Golden Age of Piracy and why they did.");

		Prompt prompt = new Prompt(List.of(systemMessage, userMessage));

		ChatResponse response = this.chatModel.call(prompt);
		assertThat(response.getResult().getOutput().getContent()).containsAnyOf("Blackbeard");

		var promptWithMessageHistory = new Prompt(List.of(new UserMessage("Hello"), response.getResult().getOutput(),
				new UserMessage("Tell me just the names of those pirates.")));
		response = this.chatModel.call(promptWithMessageHistory);

		assertThat(response.getResult().getOutput().getContent()).containsAnyOf("Blackbeard");
	}

	@Test
	void usageTest() {
		Prompt prompt = new Prompt("Tell me a joke");
		ChatResponse response = this.chatModel.call(prompt);
		Usage usage = response.getMetadata().getUsage();

		assertThat(usage).isNotNull();
		assertThat(usage.getPromptTokens()).isPositive();
		assertThat(usage.getGenerationTokens()).isPositive();
		assertThat(usage.getTotalTokens()).isPositive();
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
		PromptTemplate promptTemplate = new PromptTemplate(template,
				Map.of("subject", "ice cream flavors.", "format", format));
		Prompt prompt = new Prompt(promptTemplate.createMessage());
		Generation generation = this.chatModel.call(prompt).getResult();

		List<String> list = outputConverter.convert(generation.getOutput().getContent());
		assertThat(list).hasSize(5);
	}

	@Test
	void mapOutputConvert() {
		MapOutputConverter outputConverter = new MapOutputConverter();

		String format = outputConverter.getFormat();
		String template = """
				For each letter in the RGB color scheme, tell me what it stands for.
				Example: R -> Red.
				{format}
				""";
		PromptTemplate promptTemplate = new PromptTemplate(template, Map.of("format", format));
		Prompt prompt = new Prompt(promptTemplate.createMessage());

		Generation generation = this.chatModel.call(prompt).getResult();

		Map<String, Object> result = outputConverter.convert(generation.getOutput().getContent());
		assertThat(result).isNotNull();
		assertThat((String) result.get("R")).containsIgnoringCase("red");
		assertThat((String) result.get("G")).containsIgnoringCase("green");
		assertThat((String) result.get("B")).containsIgnoringCase("blue");
	}

	@Test
	void beanOutputConverterRecords() {
		BeanOutputConverter<ActorsFilmsRecord> outputConverter = new BeanOutputConverter<>(ActorsFilmsRecord.class);

		String format = outputConverter.getFormat();
		String template = """
				Consider the filmography of Tom Hanks and tell me 5 of his movies.
				{format}
				""";
		PromptTemplate promptTemplate = new PromptTemplate(template, Map.of("format", format));
		Prompt prompt = new Prompt(promptTemplate.createMessage());
		Generation generation = this.chatModel.call(prompt).getResult();

		ActorsFilmsRecord actorsFilms = outputConverter.convert(generation.getOutput().getContent());
		assertThat(actorsFilms.actor()).isEqualTo("Tom Hanks");
		assertThat(actorsFilms.movies()).hasSize(5);
	}

	@Test
	void beanStreamOutputConverterRecords() {
		BeanOutputConverter<ActorsFilmsRecord> outputConverter = new BeanOutputConverter<>(ActorsFilmsRecord.class);

		String format = outputConverter.getFormat();
		String template = """
				Consider the filmography of Tom Hanks and tell me 5 of his movies.
				{format}
				""";
		PromptTemplate promptTemplate = new PromptTemplate(template, Map.of("format", format));
		Prompt prompt = new Prompt(promptTemplate.createMessage());

		String generationTextFromStream = this.chatModel.stream(prompt)
			.collectList()
			.block()
			.stream()
			.map(ChatResponse::getResults)
			.flatMap(List::stream)
			.map(Generation::getOutput)
			.map(AssistantMessage::getContent)
			.collect(Collectors.joining());

		ActorsFilmsRecord actorsFilms = outputConverter.convert(generationTextFromStream);

		assertThat(actorsFilms.actor()).isEqualTo("Tom Hanks");
		assertThat(actorsFilms.movies()).hasSize(5);
	}

	record ActorsFilmsRecord(String actor, List<String> movies) {

	}

	@SpringBootConfiguration
	public static class TestConfiguration {

		@Bean
		public OllamaApi ollamaApi() {
			return initializeOllama(MODEL);
		}

		@Bean
		public OllamaChatModel ollamaChat(OllamaApi ollamaApi) {
			return OllamaChatModel.builder()
				.withOllamaApi(ollamaApi)
				.withDefaultOptions(OllamaOptions.create().withModel(MODEL).withTemperature(0.9))
				.withModelManagementOptions(ModelManagementOptions.builder()
					.withPullModelStrategy(PullModelStrategy.WHEN_MISSING)
					.withAdditionalModels(List.of(ADDITIONAL_MODEL))
					.build())
				.build();
		}

	}

}
