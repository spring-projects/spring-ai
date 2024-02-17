/*
 * Copyright 2024-2024 the original author or authors.
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

package org.springframework.ai.mistral.chat;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.Generation;
import org.springframework.ai.chat.StreamingChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.mistral.MistralAiTestConfiguration;
import org.springframework.ai.parser.BeanOutputParser;
import org.springframework.ai.parser.ListOutputParser;
import org.springframework.ai.parser.MapOutputParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 * @since 0.8.1
 */
@SpringBootTest(classes = MistralAiTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "MISTRAL_AI_API_KEY", matches = ".+")
class MistralAiChatClientIT {

	private static final Logger logger = LoggerFactory.getLogger(MistralAiChatClientIT.class);

	@Autowired
	protected ChatClient chatClient;

	@Autowired
	protected StreamingChatClient streamingChatClient;

	@Value("classpath:/prompts/system-message.st")
	private Resource systemResource;

	@Value("classpath:/prompts/eval/qa-evaluator-accurate-answer.st")
	protected Resource qaEvaluatorAccurateAnswerResource;

	@Value("classpath:/prompts/eval/qa-evaluator-not-related-message.st")
	protected Resource qaEvaluatorNotRelatedResource;

	@Value("classpath:/prompts/eval/qa-evaluator-fact-based-answer.st")
	protected Resource qaEvalutaorFactBasedAnswerResource;

	@Value("classpath:/prompts/eval/user-evaluator-message.st")
	protected Resource userEvaluatorResource;

	@Test
	void roleTest() {
		UserMessage userMessage = new UserMessage(
				"Tell me about 3 famous pirates from the Golden Age of Piracy and why they did.");
		SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(systemResource);
		Message systemMessage = systemPromptTemplate.createMessage(Map.of("name", "Bob", "voice", "pirate"));
		// NOTE: Mistral expects the system message to be before the user message or will
		// fail with 400 error.
		Prompt prompt = new Prompt(List.of(systemMessage, userMessage));
		ChatResponse response = chatClient.call(prompt);
		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getResults().get(0).getOutput().getContent()).contains("Blackbeard");
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
		Generation generation = this.chatClient.call(prompt).getResult();

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
		logger.info("" + actorsFilms);
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

		String generationTextFromStream = streamingChatClient.stream(prompt)
			.collectList()
			.block()
			.stream()
			.map(ChatResponse::getResults)
			.flatMap(List::stream)
			.map(Generation::getOutput)
			.map(AssistantMessage::getContent)
			.collect(Collectors.joining());

		ActorsFilmsRecord actorsFilms = outputParser.parse(generationTextFromStream);
		logger.info("" + actorsFilms);
		assertThat(actorsFilms.actor()).isEqualTo("Tom Hanks");
		assertThat(actorsFilms.movies()).hasSize(5);
	}

}
