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
package org.springframework.ai.bedrock.cohere;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.messages.AssistantMessage;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.regions.Region;

import org.springframework.ai.bedrock.cohere.api.CohereChatBedrockApi;
import org.springframework.ai.bedrock.cohere.api.CohereChatBedrockApi.CohereChatModel;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.Generation;
import org.springframework.ai.parser.BeanOutputParser;
import org.springframework.ai.parser.ListOutputParser;
import org.springframework.ai.parser.MapOutputParser;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "AWS_ACCESS_KEY_ID", matches = ".*")
@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".*")
class BedrockCohereChatClientIT {

	@Autowired
	private BedrockCohereChatClient client;

	@Value("classpath:/prompts/system-message.st")
	private Resource systemResource;

	@Test
	void multipleStreamAttempts() {

		Flux<ChatResponse> joke1Stream = client.stream(new Prompt(new UserMessage("Tell me a joke?")));
		Flux<ChatResponse> joke2Stream = client.stream(new Prompt(new UserMessage("Tell me a toy joke?")));

		String joke1 = joke1Stream.collectList()
			.block()
			.stream()
			.map(ChatResponse::getResults)
			.flatMap(List::stream)
			.map(Generation::getOutput)
			.map(AssistantMessage::getContent)
			.collect(Collectors.joining());
		String joke2 = joke2Stream.collectList()
			.block()
			.stream()
			.map(ChatResponse::getResults)
			.flatMap(List::stream)
			.map(Generation::getOutput)
			.map(AssistantMessage::getContent)
			.collect(Collectors.joining());

		assertThat(joke1).isNotBlank();
		assertThat(joke2).isNotBlank();
	}

	@Test
	void roleTest() {
		String request = "Tell me about 3 famous pirates from the Golden Age of Piracy and why they did.";
		String name = "Bob";
		String voice = "pirate";
		UserMessage userMessage = new UserMessage(request);
		SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(systemResource);
		Message systemMessage = systemPromptTemplate.createMessage(Map.of("name", name, "voice", voice));
		Prompt prompt = new Prompt(List.of(userMessage, systemMessage));
		ChatResponse response = client.call(prompt);

		Generation generation = response.getResults().get(0);
		assertThat(generation.getOutput().getContent()).contains("Blackbeard");

		assertThat(response.getId()).isNotBlank();

		assertThat(generation.getIndex()).isEqualTo(0);
		assertThat(generation.isCompleted()).isTrue();

		AssistantMessage assistantMessage = generation.getOutput();
		assertThat(assistantMessage.getId()).isNotBlank();
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
				Map.of("subject", "ice cream flavors.", "format", format));
		Prompt prompt = new Prompt(promptTemplate.createMessage());
		Generation generation = this.client.call(prompt).getResult();

		List<String> list = outputParser.parse(generation.getOutput().getContent());
		assertThat(list).hasSize(5);
	}

	@Test
	void mapOutputParser() {
		MapOutputParser outputParser = new MapOutputParser();

		String format = outputParser.getFormat();
		String template = """
				Remove Markdown code blocks from the output.
				Provide me a List of {subject}
				{format}
				""";
		PromptTemplate promptTemplate = new PromptTemplate(template,
				Map.of("subject", "an array of numbers from 1 to 9 under they key name 'numbers'", "format", format));
		Prompt prompt = new Prompt(promptTemplate.createMessage());
		Generation generation = client.call(prompt).getResult();

		Map<String, Object> result = outputParser.parse(generation.getOutput().getContent());
		assertThat(result.get("numbers")).isEqualTo(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9));

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
				Remove Markdown code blocks from the output.
				""";
		PromptTemplate promptTemplate = new PromptTemplate(template, Map.of("format", format));
		Prompt prompt = new Prompt(promptTemplate.createMessage());
		Generation generation = client.call(prompt).getResult();

		ActorsFilmsRecord actorsFilms = outputParser.parse(generation.getOutput().getContent());
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
				Remove Markdown code blocks from the output.
				""";
		PromptTemplate promptTemplate = new PromptTemplate(template, Map.of("format", format));
		Prompt prompt = new Prompt(promptTemplate.createMessage());

		Flux<ChatResponse> response = client.stream(prompt);

		List<ChatResponse> chatResponseList = response.collectList().block();

		String generationTextFromStream = chatResponseList.stream()
			.map(ChatResponse::getResults)
			.flatMap(List::stream)
			.map(Generation::getOutput)
			.map(AssistantMessage::getContent)
			.collect(Collectors.joining());

		ActorsFilmsRecord actorsFilms = outputParser.parse(generationTextFromStream);
		// logger.info("" + actorsFilms);
		assertThat(actorsFilms.actor()).isEqualTo("Tom Hanks");
		assertThat(actorsFilms.movies()).hasSize(5);

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
		public CohereChatBedrockApi cohereApi() {
			return new CohereChatBedrockApi(CohereChatModel.COHERE_COMMAND_V14.id(),
					EnvironmentVariableCredentialsProvider.create(), Region.US_EAST_1.id(), new ObjectMapper());
		}

		@Bean
		public BedrockCohereChatClient cohereChatClient(CohereChatBedrockApi cohereApi) {
			return new BedrockCohereChatClient(cohereApi);
		}

	}

}
