/*
 * Copyright 2023-2026 the original author or authors.
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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.ListOutputConverter;
import org.springframework.ai.mistralai.api.MistralAiApi;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionRequest.ToolChoice;
import org.springframework.ai.test.CurlyBracketEscaper;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = MistralAiTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "MISTRAL_AI_API_KEY", matches = ".+")
class MistralAiChatClientIT {

	private static final Logger logger = LoggerFactory.getLogger(MistralAiChatClientIT.class);

	@Autowired
	MistralAiChatModel chatModel;

	@Value("classpath:/prompts/system-message.st")
	private Resource systemTextResource;

	@Test
	void call() {
		// @formatter:off
		ChatResponse response = ChatClient.create(this.chatModel).prompt()
				.system(s -> s.text(this.systemTextResource)
						.param("name", "Bob")
						.param("voice", "pirate"))
				.user("Tell me about 3 famous pirates from the Golden Age of Piracy and what they did")
				.call()
				.chatResponse();
		// @formatter:on

		logger.info("" + response);
		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getResults().get(0).getOutput().getText()).contains("Blackbeard");
	}

	@Test
	void testMessageHistory() {

		// @formatter:off
		ChatResponse response = ChatClient.create(this.chatModel).prompt()
				.system(s -> s.text(this.systemTextResource)
						.param("name", "Bob")
						.param("voice", "pirate"))
				.user("Tell me about 3 famous pirates from the Golden Age of Piracy and what they did")
				.call()
				.chatResponse();
		// @formatter:on
		assertThat(response.getResult().getOutput().getText()).containsAnyOf("Blackbeard");

		// @formatter:off
		response = ChatClient.create(this.chatModel).prompt()
				.messages(List.of(new UserMessage("Dummy"), response.getResult().getOutput()))
				.user("Repeat the last assistant message.")
				.call()
				.chatResponse();
		// @formatter:on

		logger.info("" + response);
		assertThat(response.getResult().getOutput().getText().toLowerCase()).containsAnyOf("blackbeard",
				"bartholomew roberts");
	}

	@Test
	void listOutputConverterString() {
		// @formatter:off
		List<String> collection = ChatClient.create(this.chatModel).prompt()
				.user(u -> u.text("List five {subject}")
						.param("subject", "ice cream flavors"))
				.call()
				.entity(new ParameterizedTypeReference<>() { });
		// @formatter:on

		logger.info(collection.toString());
		assertThat(collection).hasSize(5);
	}

	@Test
	void listOutputConverterBean() {

		// @formatter:off
		List<ActorsFilms> actorsFilms = ChatClient.create(this.chatModel).prompt()
				.user("Generate the filmography of 5 movies for Tom Hanks and Bill Murray.")
				.call()
				.entity(new ParameterizedTypeReference<>() {
				});
		// @formatter:on

		logger.info("" + actorsFilms);
		assertThat(actorsFilms).hasSize(2);
	}

	@Test
	void customOutputConverter() {

		var toStringListConverter = new ListOutputConverter(new DefaultConversionService());

		// @formatter:off
		List<String> flavors = ChatClient.create(this.chatModel).prompt()
				.user(u -> u.text("List 10 {subject}")
				.param("subject", "ice cream flavors"))
				.call()
				.entity(toStringListConverter);
		// @formatter:on

		logger.info("ice cream flavors" + flavors);
		assertThat(flavors).hasSize(10);
		assertThat(flavors).containsAnyOf("Vanilla", "vanilla");
	}

	@Test
	void mapOutputConverter() {
		// @formatter:off
		Map<String, Object> result = ChatClient.create(this.chatModel).prompt()
				.user(u -> u.text("Provide me a List of {subject}")
						.param("subject", "an array of numbers from 1 to 9 under they key name 'numbers'"))
				.call()
				.entity(new ParameterizedTypeReference<>() {
				});
		// @formatter:on

		assertThat(result.get("numbers")).isEqualTo(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9));
	}

	@Test
	void beanOutputConverter() {

		// @formatter:off
		ActorsFilms actorsFilms = ChatClient.create(this.chatModel).prompt()
				.user("Generate the filmography for a random actor.")
				.call()
				.entity(ActorsFilms.class);
		// @formatter:on

		logger.info("" + actorsFilms);
		assertThat(actorsFilms.actor()).isNotBlank();
	}

	@Test
	void beanOutputConverterRecords() {

		// @formatter:off
		ActorsFilms actorsFilms = ChatClient.create(this.chatModel).prompt()
				.user("Generate the filmography of 5 movies for Tom Hanks.")
				.call()
				.entity(ActorsFilms.class);
		// @formatter:on

		logger.info("" + actorsFilms);
		assertThat(actorsFilms.actor()).isEqualTo("Tom Hanks");
		assertThat(actorsFilms.movies()).hasSize(5);
	}

	@Test
	void beanStreamOutputConverterRecords() {

		BeanOutputConverter<ActorsFilms> outputConverter = new BeanOutputConverter<>(ActorsFilms.class);

		// @formatter:off
		Flux<String> chatResponse = ChatClient.create(this.chatModel)
				.prompt()
				.advisors(new SimpleLoggerAdvisor())
				.user(u -> u
						.text("Generate the filmography of 5 movies for Tom Hanks. " + System.lineSeparator()
								+ "{format}")
						.param("format", CurlyBracketEscaper.escapeCurlyBrackets(outputConverter.getFormat())))
				.stream()
				.content();

		String generationTextFromStream = chatResponse.collectList()
				.block()
				.stream()
				.collect(Collectors.joining());
		// @formatter:on

		ActorsFilms actorsFilms = outputConverter.convert(generationTextFromStream);

		logger.info("" + actorsFilms);
		assertThat(actorsFilms.actor()).isEqualTo("Tom Hanks");
		assertThat(actorsFilms.movies()).hasSize(5);
	}

	@Test
	void functionCallTest() {

		// @formatter:off
		String response = ChatClient.create(this.chatModel).prompt()
				.options(MistralAiChatOptions.builder().model(MistralAiApi.ChatModel.MISTRAL_SMALL).toolChoice(ToolChoice.AUTO).build())
				.user(u -> u.text("What's the weather like in San Francisco, Tokyo, and Paris? Use parallel function calling if required. Response should be in Celsius."))
				.toolCallbacks(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
					.description("Get the weather in location")
					.inputType(MockWeatherService.Request.class)
					.build())
				.call()
				.content();
		// @formatter:on

		logger.info("Response: {}", response);

		assertThat(response).containsAnyOf("30.0", "30");
		assertThat(response).containsAnyOf("10.0", "10");
		assertThat(response).containsAnyOf("15.0", "15");
	}

	@Test
	void defaultFunctionCallTest() {

		// @formatter:off
		String response = ChatClient.builder(this.chatModel)
				.defaultOptions(MistralAiChatOptions.builder().model(MistralAiApi.ChatModel.MISTRAL_SMALL).build())
				.defaultToolCallbacks(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
					.description("Get the weather in location")
					.inputType(MockWeatherService.Request.class)
					.build())
				.defaultUser(u -> u.text("What's the weather like in San Francisco, Tokyo, and Paris? Use parallel function calling if required. Response should be in Celsius."))
			.build()
			.prompt().call().content();
		// @formatter:on

		logger.info("Response: {}", response);

		assertThat(response).containsAnyOf("30.0", "30");
		assertThat(response).containsAnyOf("10.0", "10");
		assertThat(response).containsAnyOf("15.0", "15");
	}

	@Test
	void streamFunctionCallTest() {

		// @formatter:off
		Flux<String> response = ChatClient.create(this.chatModel).prompt()
				.options(MistralAiChatOptions.builder().model(MistralAiApi.ChatModel.MISTRAL_SMALL).build())
				.user("What's the weather like in San Francisco, Tokyo, and Paris? Use parallel function calling if required. Response should be in Celsius.")
				.toolCallbacks(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
					.description("Get the weather in location")
					.inputType(MockWeatherService.Request.class)
					.build())
				.stream()
				.content();
		// @formatter:on

		String content = response.collectList().block().stream().collect(Collectors.joining());
		logger.info("Response: {}", content);

		assertThat(content).containsAnyOf("30.0", "30");
		assertThat(content).containsAnyOf("10.0", "10");
		assertThat(content).containsAnyOf("15.0", "15");
	}

	@Test
	void validateCallResponseMetadata() {
		// String model = MistralAiApi.ChatModel.OPEN_MISTRAL_7B.getName();
		String model = MistralAiApi.ChatModel.PIXTRAL_12B.getName();
		// String model = MistralAiApi.ChatModel.PIXTRAL_LARGE.getName();
		// @formatter:off
		ChatResponse response = ChatClient.create(this.chatModel).prompt()
				.options(MistralAiChatOptions.builder().model(model).build())
				.user("Tell me about 3 famous pirates from the Golden Age of Piracy and what they did")
				.call()
				.chatResponse();
		// @formatter:on

		logger.info(response.toString());
		assertThat(response.getMetadata().getId()).isNotEmpty();
		assertThat(response.getMetadata().getModel()).containsIgnoringCase(model);
		assertThat(response.getMetadata().getUsage().getPromptTokens()).isPositive();
		assertThat(response.getMetadata().getUsage().getCompletionTokens()).isPositive();
		assertThat(response.getMetadata().getUsage().getTotalTokens()).isPositive();
	}

	record ActorsFilms(String actor, List<String> movies) {

	}

}
