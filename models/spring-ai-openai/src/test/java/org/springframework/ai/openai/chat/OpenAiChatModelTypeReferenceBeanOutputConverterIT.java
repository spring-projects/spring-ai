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

package org.springframework.ai.openai.chat;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiTestConfiguration;
import org.springframework.ai.openai.testutils.AbstractIT;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.log.LogAccessor;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = OpenAiTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiChatModelTypeReferenceBeanOutputConverterIT extends AbstractIT {

	private static final LogAccessor logger = new LogAccessor(OpenAiChatModelTypeReferenceBeanOutputConverterIT.class);

	@Test
	void typeRefOutputConverterRecords() {

		BeanOutputConverter<List<ActorsFilmsRecord>> outputConverter = new BeanOutputConverter<>(
				new ParameterizedTypeReference<List<ActorsFilmsRecord>>() {

				});

		String format = outputConverter.getFormat();
		String template = """
				Generate the filmography of 5 movies for Tom Hanks and Bill Murray.
				{format}
				""";
		PromptTemplate promptTemplate = new PromptTemplate(template, Map.of("format", format));
		Prompt prompt = new Prompt(promptTemplate.createMessage());
		Generation generation = this.chatModel.call(prompt).getResult();

		List<ActorsFilmsRecord> actorsFilms = outputConverter.convert(generation.getOutput().getText());
		logger.info("" + actorsFilms);
		assertThat(actorsFilms).hasSize(2);
		assertThat(actorsFilms.get(0).actor()).isEqualTo("Tom Hanks");
		assertThat(actorsFilms.get(0).movies()).hasSize(5);
		assertThat(actorsFilms.get(1).actor()).isEqualTo("Bill Murray");
		assertThat(actorsFilms.get(1).movies()).hasSize(5);
	}

	@Test
	void typeRefStreamOutputConverterRecords() {

		BeanOutputConverter<List<ActorsFilmsRecord>> outputConverter = new BeanOutputConverter<>(
				new ParameterizedTypeReference<List<ActorsFilmsRecord>>() {

				});

		String format = outputConverter.getFormat();
		String template = """
				Generate the filmography of 5 movies for Tom Hanks and Bill Murray.
					{format}
					""";
		PromptTemplate promptTemplate = new PromptTemplate(template, Map.of("format", format));
		Prompt prompt = new Prompt(promptTemplate.createMessage());

		String generationTextFromStream = this.streamingChatModel.stream(prompt)
			.collectList()
			.block()
			.stream()
			.map(ChatResponse::getResults)
			.flatMap(List::stream)
			.map(Generation::getOutput)
			.map(AssistantMessage::getText)
			.collect(Collectors.joining());

		List<ActorsFilmsRecord> actorsFilms = outputConverter.convert(generationTextFromStream);
		logger.info("" + actorsFilms);
		assertThat(actorsFilms).hasSize(2);
		assertThat(actorsFilms.get(0).actor()).isEqualTo("Tom Hanks");
		assertThat(actorsFilms.get(0).movies()).hasSize(5);
		assertThat(actorsFilms.get(1).actor()).isEqualTo("Bill Murray");
		assertThat(actorsFilms.get(1).movies()).hasSize(5);
	}

	record ActorsFilmsRecord(String actor, List<String> movies) {

	}

}
