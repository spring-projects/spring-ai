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
package org.springframework.ai.openai.chat;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiTestConfiguration;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.tool.MockWeatherService;
import org.springframework.ai.openai.testutils.AbstractIT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = OpenAiTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiChatClientIT extends AbstractIT {

	private static final Logger logger = LoggerFactory.getLogger(OpenAiChatClientIT.class);

	@Value("classpath:/prompts/system-message.st")
	private Resource systemTextResource;

	@Test
	void roleTest() {

		// @formatter:off
		ChatResponse response = ChatClient.builder(chatModel).build().prompt()
				.system(s -> s.text(systemTextResource)
						.param("name", "Bob")
						.param("voice", "pirate"))
				.user("Tell me about 3 famous pirates from the Golden Age of Piracy and what they did")
				.call()
				.chatResponse();
		// @formatter:on

		logger.info("" + response);
		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getResults().get(0).getOutput().getContent()).contains("Blackbeard");
	}

	@Test
	void listOutputConverter() {
		// @formatter:off
		Collection<String> collection = ChatClient.builder(chatModel).build().prompt()
				.user(u -> u.text("List five {subject}")
						.param("subject", "ice cream flavors"))
				.call()
				.entity(new ParameterizedTypeReference<List<String>>() {});
		// @formatter:on

		assertThat(collection).hasSize(5);
	}

	@Test
	void listOutputConverter2() {

		// @formatter:off
		List<ActorsFilmsRecord> actorsFilms = ChatClient.builder(chatModel).build().prompt()
				.user("Generate the filmography of 5 movies for Tom Hanks and Bill Murray.")
				.call()
				.entity(new ParameterizedTypeReference<List<ActorsFilmsRecord>>() {
				});
		// @formatter:on

		logger.info("" + actorsFilms);
		assertThat(actorsFilms).hasSize(2);

	}

	@Test
	void mapOutputConverter() {
		// @formatter:off
		Map<String, Object> result = ChatClient.builder(chatModel).build().prompt()
				.user(u -> u.text("Provide me a List of {subject}")
						.param("subject", "an array of numbers from 1 to 9 under they key name 'numbers'"))
				.call()
				.entity(new ParameterizedTypeReference<Map<String, Object>>() {
				});
		// @formatter:on

		assertThat(result.get("numbers")).isEqualTo(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9));
	}

	@Test
	void beanOutputConverter() {

		// @formatter:off
		ActorsFilms actorsFilms = ChatClient.builder(chatModel).build().prompt()
				.user("Generate the filmography for a random actor.")
				.call()
				.entity(ActorsFilms.class);
		// @formatter:on

		logger.info("" + actorsFilms);
		assertThat(actorsFilms.getActor()).isNotBlank();
	}

	record ActorsFilmsRecord(String actor, List<String> movies) {
	}

	@Test
	void beanOutputConverterRecords() {

		// @formatter:off
		ActorsFilmsRecord actorsFilms = ChatClient.builder(chatModel).build().prompt()
				.user("Generate the filmography of 5 movies for Tom Hanks.")
				.call()
				.entity(ActorsFilmsRecord.class);
		// @formatter:on

		logger.info("" + actorsFilms);
		assertThat(actorsFilms.actor()).isEqualTo("Tom Hanks");
		assertThat(actorsFilms.movies()).hasSize(5);
	}

	@Test
	void beanStreamOutputConverterRecords() {

		BeanOutputConverter<ActorsFilmsRecord> outputConverter = new BeanOutputConverter<>(ActorsFilmsRecord.class);

		// @formatter:off
		Flux<String> chatResponse = ChatClient.builder(chatModel)
				.build()
				.prompt()
				.user(u -> u
						.text("Generate the filmography of 5 movies for Tom Hanks. " + System.lineSeparator()
								+ "{format}")
						.param("format", outputConverter.getFormat()))
				.stream()
				.content();

		String generationTextFromStream = chatResponse.collectList()
				.block()
				.stream()
				.collect(Collectors.joining());
		// @formatter:on

		ActorsFilmsRecord actorsFilms = outputConverter.convert(generationTextFromStream);

		logger.info("" + actorsFilms);
		assertThat(actorsFilms.actor()).isEqualTo("Tom Hanks");
		assertThat(actorsFilms.movies()).hasSize(5);
	}

	@Test
	void functionCallTest() {

		// @formatter:off
		String response = ChatClient.builder(chatModel).build().prompt()
				.user(u -> u.text("What's the weather like in San Francisco, Tokyo, and Paris?"))
				.function("getCurrentWeather", "Get the weather in location", new MockWeatherService())
				.call()
				.content();
		// @formatter:on

		logger.info("Response: {}", response);

		assertThat(response).containsAnyOf("30.0", "30");
		assertThat(response).containsAnyOf("10.0", "10");
		assertThat(response).containsAnyOf("15.0", "15");
	}

	@Test
	void streamFunctionCallTest() {

		// @formatter:off
		Flux<String> response = ChatClient.builder(chatModel).build().prompt()
				.user("What's the weather like in San Francisco, Tokyo, and Paris?")
				.function("getCurrentWeather", "Get the weather in location", new MockWeatherService())
				.stream()
				.content();
		// @formatter:on

		String content = response.collectList().block().stream().collect(Collectors.joining());
		logger.info("Response: {}", content);

		assertThat(content).containsAnyOf("30.0", "30");
		assertThat(content).containsAnyOf("10.0", "10");
		assertThat(content).containsAnyOf("15.0", "15");
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "gpt-4-vision-preview", "gpt-4o" })
	void multiModalityEmbeddedImage(String modelName) throws IOException {

		// @formatter:off
		String response = ChatClient.builder(chatModel).build().prompt()
				// TODO consider adding model(...) method to ChatClient as a shortcut to
				// OpenAiChatOptions.builder().withModel(modelName).build()
				.options(OpenAiChatOptions.builder().withModel(modelName).build())
				.user(u -> u.text("Explain what do you see on this picture?")
						.media(MimeTypeUtils.IMAGE_PNG, new ClassPathResource("/test.png")))
				.call()
				.content();
		// @formatter:on

		logger.info(response);
		assertThat(response).contains("bananas", "apple");
		assertThat(response).containsAnyOf("bowl", "basket");
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "gpt-4-vision-preview", "gpt-4o" })
	void multiModalityImageUrl(String modelName) throws IOException {

		// TODO: add url method that wrapps the checked exception.
		URL url = new URL("https://docs.spring.io/spring-ai/reference/1.0-SNAPSHOT/_images/multimodal.test.png");

		// @formatter:off
		String response = ChatClient.builder(chatModel).build().prompt()
				// TODO consider adding model(...) method to ChatClient as a shortcut to
				// OpenAiChatOptions.builder().withModel(modelName).build()
				.options(OpenAiChatOptions.builder().withModel(modelName).build())
				.user(u -> u.text("Explain what do you see on this picture?").media(MimeTypeUtils.IMAGE_PNG, url))
				.call()
				.content();
		// @formatter:on

		logger.info(response);
		assertThat(response).contains("bananas", "apple");
		assertThat(response).containsAnyOf("bowl", "basket");
	}

	@Test
	void streamingMultiModalityImageUrl() throws IOException {

		// TODO: add url method that wrapps the checked exception.
		URL url = new URL("https://docs.spring.io/spring-ai/reference/1.0-SNAPSHOT/_images/multimodal.test.png");

		// @formatter:off
		Flux<String> response = ChatClient.builder(chatModel).build().prompt()
				.options(OpenAiChatOptions.builder().withModel(OpenAiApi.ChatModel.GPT_4_VISION_PREVIEW.getValue())
						.build())
				.user(u -> u.text("Explain what do you see on this picture?")
						.media(MimeTypeUtils.IMAGE_PNG, url))
				.stream()
				.content();
		// @formatter:on

		String content = response.collectList().block().stream().collect(Collectors.joining());

		logger.info("Response: {}", content);
		assertThat(content).contains("bananas", "apple");
		assertThat(content).containsAnyOf("bowl", "basket");
	}

}