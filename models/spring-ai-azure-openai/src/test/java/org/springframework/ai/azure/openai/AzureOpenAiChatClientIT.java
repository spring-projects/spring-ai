/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.azure.openai;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.core.credential.AzureKeyCredential;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.parser.BeanOutputParser;
import org.springframework.ai.parser.ListOutputParser;
import org.springframework.ai.parser.MapOutputParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.support.DefaultConversionService;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = AzureOpenAiChatClientIT.TestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_ENDPOINT", matches = ".+")
class AzureOpenAiChatClientIT {

	private static final Logger logger = LoggerFactory.getLogger(AzureOpenAiChatClientIT.class);

	@Autowired
	private AzureOpenAiChatClient chatClient;

	record ActorsFilms(String actor, List<String> movies) {
	}

	@Test
	void roleTest() {
		Message systemMessage = new SystemPromptTemplate("""
				You are a helpful AI assistant. Your name is {name}.
				You are an AI assistant that helps people find information.
				Your name is {name}
				You should reply to the user's request with your name and also in the style of a {voice}.
				""").createMessage(Map.of("name", "Bob", "voice", "pirate"));

		UserMessage userMessage = new UserMessage("Generate the names of 5 famous pirates.");

		Prompt prompt = new Prompt(List.of(userMessage, systemMessage));
		ChatResponse response = chatClient.call(prompt);
		assertThat(response.getResult().getOutput().getContent()).contains("Blackbeard");

		assertThat(response.getId()).isNotBlank();
		var generation = response.getResults().get(0);

		assertThat(generation.getIndex()).isEqualTo(0);
		assertThat(generation.isCompleted()).isTrue();

		AssistantMessage assistantMessage = generation.getOutput();
		assertThat(assistantMessage.getId()).isEqualTo(response.getId());
		assertThat(assistantMessage.getIndex()).isEqualTo(generation.getIndex());
		assertThat(assistantMessage.isCompleted()).isTrue();
	}

	@Test
	void outputParser() {
		DefaultConversionService conversionService = new DefaultConversionService();
		ListOutputParser outputParser = new ListOutputParser(conversionService);

		String format = outputParser.getFormat();
		String template = """
				List five {subject}
				{format}
				""";
		PromptTemplate promptTemplate = new PromptTemplate(template,
				Map.of("subject", "ice cream flavors", "format", format));
		Prompt prompt = new Prompt(promptTemplate.createMessage());
		Generation generation = chatClient.call(prompt).getResult();

		List<String> list = outputParser.parse(generation.getOutput().getContent());
		assertThat(list).hasSize(5);

	}

	@Test
	void mapOutputParser() {
		MapOutputParser outputParser = new MapOutputParser();

		String format = outputParser.getFormat();
		String template = """
				Provide me a List of {subject}
				{format}
				""";
		PromptTemplate promptTemplate = new PromptTemplate(template,
				Map.of("subject", "an array of numbers from 1 to 9 under they key name 'numbers'", "format", format));
		Prompt prompt = new Prompt(promptTemplate.createMessage());
		Generation generation = chatClient.call(prompt).getResult();

		Map<String, Object> result = outputParser.parse(generation.getOutput().getContent());
		assertThat(result.get("numbers")).isEqualTo(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9));

	}

	@Test
	void beanOutputParser() {

		BeanOutputParser<ActorsFilms> outputParser = new BeanOutputParser<>(ActorsFilms.class);

		String format = outputParser.getFormat();
		String template = """
				Generate the filmography for a random actor.
				{format}
				""";
		PromptTemplate promptTemplate = new PromptTemplate(template, Map.of("format", format));
		Prompt prompt = new Prompt(promptTemplate.createMessage());
		Generation generation = chatClient.call(prompt).getResult();

		ActorsFilms actorsFilms = outputParser.parse(generation.getOutput().getContent());
		assertThat(actorsFilms.actor()).isNotNull();
	}

	record ActorsFilmsRecord(String actor, List<String> movies) {
	}

	@Test
	void beanOutputParserRecords() {

		BeanOutputParser<ActorsFilmsRecord> outputParser = new BeanOutputParser<>(ActorsFilmsRecord.class);

		String format = outputParser.getFormat();
		String template = """
				Generate the filmography of 5 movies for Tom Hanks.
				{format}
				""";
		PromptTemplate promptTemplate = new PromptTemplate(template, Map.of("format", format));
		Prompt prompt = new Prompt(promptTemplate.createMessage());
		Generation generation = chatClient.call(prompt).getResult();

		ActorsFilmsRecord actorsFilms = outputParser.parse(generation.getOutput().getContent());

		logger.info("ActorsFilmsRecord: {}", actorsFilms);

		assertThat(actorsFilms.actor()).isEqualTo("Tom Hanks");
		assertThat(actorsFilms.movies()).hasSize(5);
	}

	@Test
	void beanStreamOutputParserRecords() {

		BeanOutputParser<ActorsFilmsRecord> outputParser = new BeanOutputParser<>(ActorsFilmsRecord.class);

		String format = outputParser.getFormat();
		String template = """
				Generate the filmography of 5 movies for Tom Hanks.
				{format}
				""";
		PromptTemplate promptTemplate = new PromptTemplate(template, Map.of("format", format));
		Prompt prompt = new Prompt(promptTemplate.createMessage());

		Flux<ChatResponse> response = chatClient.stream(prompt);

		List<ChatResponse> chatResponseList = response.collectList().block();

		String generationTextFromStream = chatResponseList.stream()
			.map(ChatResponse::getResults)
			.flatMap(List::stream)
			.map(Generation::getOutput)
			.map(AssistantMessage::getContent)
			.collect(Collectors.joining());

		ActorsFilmsRecord actorsFilms = outputParser.parse(generationTextFromStream);

		logger.info("ActorsFilmsRecord: {}", actorsFilms);

		assertThat(actorsFilms.actor()).isEqualTo("Tom Hanks");
		assertThat(actorsFilms.movies()).hasSize(5);

		assertThat(chatResponseList).hasSizeGreaterThan(1);

		var firstResponse = chatResponseList.get(0);

		for (int i = 0; i < chatResponseList.size() - 1; i++) {
			var responseX = chatResponseList.get(i);
			assertThat(responseX.getId()).isEqualTo(firstResponse.getId());

			assertThat(responseX.getResults()).hasSize(1);
			var generation = responseX.getResults().get(0);

			assertThat(generation.getId()).isEqualTo(firstResponse.getId());
			assertThat(generation.getIndex()).isEqualTo(0);
			assertThat(generation.isCompleted()).isFalse();

			AssistantMessage assistantMessage = generation.getOutput();

			assertThat(assistantMessage.getId()).isEqualTo(firstResponse.getId());
			assertThat(assistantMessage.getIndex()).isEqualTo(0);
			assertThat(assistantMessage.isCompleted()).isFalse();
		}

		var lastResponse = chatResponseList.get(chatResponseList.size() - 1);
		assertThat(lastResponse.getId()).isEqualTo(firstResponse.getId());
		assertThat(lastResponse.getResults()).hasSize(1);
		var lastGeneration = lastResponse.getResults().get(0);

		assertThat(lastGeneration.getId()).isEqualTo(firstResponse.getId());
		assertThat(lastGeneration.getIndex()).isEqualTo(0);
		assertThat(lastGeneration.isCompleted()).isTrue();

		AssistantMessage lastAssistantMessage = lastGeneration.getOutput();

		assertThat(lastAssistantMessage.getId()).isEqualTo(firstResponse.getId());
		assertThat(lastAssistantMessage.getIndex()).isEqualTo(0);
		assertThat(lastAssistantMessage.isCompleted()).isTrue();

	}

	@SpringBootConfiguration
	public static class TestConfiguration {

		@Bean
		public OpenAIClient openAIClient() {
			return new OpenAIClientBuilder().credential(new AzureKeyCredential(System.getenv("AZURE_OPENAI_API_KEY")))
				.endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
				.buildClient();
		}

		@Bean
		public AzureOpenAiChatClient azureOpenAiChatClient(OpenAIClient openAIClient) {
			return new AzureOpenAiChatClient(openAIClient,
					AzureOpenAiChatOptions.builder().withDeploymentName("gpt-35-turbo").withMaxTokens(200).build());

		}

	}

}
