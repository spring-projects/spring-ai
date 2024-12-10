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

package org.springframework.ai.bedrock.llama;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import reactor.core.publisher.Flux;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.regions.Region;

import org.springframework.ai.bedrock.llama.api.LlamaChatBedrockApi;
import org.springframework.ai.bedrock.llama.api.LlamaChatBedrockApi.LlamaChatModel;
import org.springframework.ai.chat.messages.AssistantMessage;
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
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "AWS_ACCESS_KEY_ID", matches = ".*")
@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".*")
class BedrockLlamaChatModelIT {

	@Autowired
	private BedrockLlamaChatModel chatModel;

	@Value("classpath:/prompts/system-message.st")
	private Resource systemResource;

	@Test
	void multipleStreamAttempts() {

		Flux<ChatResponse> joke2Stream = this.chatModel.stream(new Prompt(new UserMessage("Tell me a Toy joke?")));
		Flux<ChatResponse> joke1Stream = this.chatModel.stream(new Prompt(new UserMessage("Tell me a joke?")));

		String joke1 = joke1Stream.collectList()
			.block()
			.stream()
			.map(ChatResponse::getResults)
			.flatMap(List::stream)
			.map(Generation::getOutput)
			.map(AssistantMessage::getText)
			.collect(Collectors.joining());
		String joke2 = joke2Stream.collectList()
			.block()
			.stream()
			.map(ChatResponse::getResults)
			.flatMap(List::stream)
			.map(Generation::getOutput)
			.map(AssistantMessage::getText)
			.collect(Collectors.joining());

		assertThat(joke1).isNotBlank();
		assertThat(joke2).isNotBlank();
	}

	@Test
	void roleTest() {
		String message = """
				Describe 3 of the most feared and legendary pirates from the Golden Age of Piracy, particularly
				those known for their intimidating tactics and whose stories influenced popular culture.
				""";
		UserMessage userMessage = new UserMessage(message);
		SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(this.systemResource);
		Message systemMessage = systemPromptTemplate.createMessage(Map.of("name", "Bob", "voice", "pirate"));

		Prompt prompt = new Prompt(List.of(userMessage, systemMessage));

		ChatResponse response = this.chatModel.call(prompt);
		assertThat(response.getResult().getOutput().getText()).satisfies(content -> {
			// Check for name
			assertThat(content).contains("Bob");

			// Check for pirate speech patterns - should match at least one
			assertThat(content).matches(text -> text.contains("Arrr") || text.contains("matey") || text.contains("ye")
					|| text.contains("yer") || text.contains("shiver me timbers") || text.contains("scurvy"));
		});
	}

	@Test
	void listOutputConverter() {
		DefaultConversionService conversionService = new DefaultConversionService();
		ListOutputConverter outputConverter = new ListOutputConverter(conversionService);

		String format = outputConverter.getFormat();
		String template = """
				List exactly five {subject}, no more and no less.
				{format}
				""";
		PromptTemplate promptTemplate = new PromptTemplate(template,
				Map.of("subject", "ice cream flavors.", "format", format));
		Prompt prompt = new Prompt(promptTemplate.createMessage());
		Generation generation = this.chatModel.call(prompt).getResult();

		List<String> list = outputConverter.convert(generation.getOutput().getText());
		assertThat(list).hasSize(5);
	}

	@Test
	void mapOutputConverter() {
		MapOutputConverter outputConverter = new MapOutputConverter();

		String format = outputConverter.getFormat();
		String template = """
				Provide me a List of {subject}
				{format}
				""";
		PromptTemplate promptTemplate = new PromptTemplate(template,
				Map.of("subject", "an array of numbers from 1 to 9 under they key name 'numbers'", "format", format));
		Prompt prompt = new Prompt(promptTemplate.createMessage());
		Generation generation = this.chatModel.call(prompt).getResult();

		Map<String, Object> result = outputConverter.convert(generation.getOutput().getText());
		assertThat(result.get("numbers")).isEqualTo(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9));

	}

	@Test
	void beanOutputConverterRecords() {

		BeanOutputConverter<ActorsFilmsRecord> outputConverter = new BeanOutputConverter<>(ActorsFilmsRecord.class);

		String format = outputConverter.getFormat();
		String template = """
				Generate the filmography of 5 movies for Tom Hanks.
				{format}
				Remove non JSON tex blocks from the output.
				Provide your answer in the JSON format with the feature names as the keys.
				""";
		PromptTemplate promptTemplate = new PromptTemplate(template, Map.of("format", format));
		Prompt prompt = new Prompt(promptTemplate.createMessage());
		Generation generation = this.chatModel.call(prompt).getResult();

		ActorsFilmsRecord actorsFilms = outputConverter.convert(generation.getOutput().getText());
		assertThat(actorsFilms.actor()).isEqualTo("Tom Hanks");
		assertThat(actorsFilms.movies()).hasSize(5);
	}

	@Test
	void beanStreamOutputConverterRecords() {

		BeanOutputConverter<ActorsFilmsRecord> outputConverter = new BeanOutputConverter<>(ActorsFilmsRecord.class);

		String format = outputConverter.getFormat();
		String template = """
				Generate the filmography of 5 movies for Tom Hanks.
				{format}
				Remove Markdown code blocks from the output.
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
			.map(AssistantMessage::getText)
			.collect(Collectors.joining());

		ActorsFilmsRecord actorsFilms = outputConverter.convert(generationTextFromStream);
		System.out.println(actorsFilms);
		assertThat(actorsFilms.actor()).isEqualTo("Tom Hanks");
		assertThat(actorsFilms.movies()).hasSize(5);
	}

	record ActorsFilmsRecord(String actor, List<String> movies) {

	}

	@SpringBootConfiguration
	public static class TestConfiguration {

		@Bean
		public LlamaChatBedrockApi llamaApi() {
			return new LlamaChatBedrockApi(LlamaChatModel.LLAMA3_70B_INSTRUCT_V1.id(),
					EnvironmentVariableCredentialsProvider.create(), Region.US_EAST_1.id(), new ObjectMapper(),
					Duration.ofMinutes(2));
		}

		@Bean
		public BedrockLlamaChatModel llamaChatModel(LlamaChatBedrockApi llamaApi) {
			return new BedrockLlamaChatModel(llamaApi,
					BedrockLlamaChatOptions.builder().withTemperature(0.5).withMaxGenLen(100).withTopP(0.9).build());
		}

	}

}
