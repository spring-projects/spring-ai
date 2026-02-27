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

package org.springframework.ai.anthropic.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.AnthropicTestConfiguration;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.anthropic.api.tool.MockWeatherService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.ListOutputConverter;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.test.CurlyBracketEscaper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.MimeTypeUtils;

import reactor.core.publisher.Flux;

@SpringBootTest(classes = AnthropicTestConfiguration.class, properties = "spring.ai.retry.on-http-codes=429")
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
@ActiveProfiles("logging-test")
class AnthropicChatClientIT {

	private static final Logger logger = LoggerFactory.getLogger(AnthropicChatClientIT.class);

	@Autowired
	ChatModel chatModel;

	@Value("classpath:/prompts/system-message.st")
	private Resource systemTextResource;

	@Test
	void call() {

		// @formatter:off
		ChatResponse response = ChatClient.create(this.chatModel).prompt()
				.advisors(new SimpleLoggerAdvisor())
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
				.user(u -> u.text("List five {subject}")
				.param("subject", "ice cream flavors"))
				.call()
				.entity(toStringListConverter);
		// @formatter:on

		logger.info("ice cream flavors" + flavors);
		assertThat(flavors).hasSize(5);
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
				.user("What's the weather like in San Francisco (California, USA), Tokyo (Japan), and Paris (France)? Use Celsius.")
				.toolCallbacks(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
					.inputType(MockWeatherService.Request.class)
					.build())
				.call()
				.content();
		// @formatter:on

		logger.info("Response: {}", response);

		assertThat(response).contains("30", "10", "15");
	}

	@Test
	void functionCallWithGeneratedDescription() {

		// @formatter:off
		String response = ChatClient.create(this.chatModel).prompt()
				.user("What's the weather like in San Francisco, Tokyo, and Paris?  Use Celsius.")
				.toolCallbacks(FunctionToolCallback.builder("getCurrentWeatherInLocation", new MockWeatherService())
					.inputType(MockWeatherService.Request.class)
					.build())
				.call()
				.content();
		// @formatter:on

		logger.info("Response: {}", response);

		assertThat(response).contains("30", "10", "15");
	}

	@Test
	void defaultFunctionCallTest() {

		// @formatter:off
		String response = ChatClient.builder(this.chatModel)
				.defaultToolCallbacks(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
					.description("Get the weather in location")
					.inputType(MockWeatherService.Request.class)
					.build())
				.defaultUser(u -> u.text("What's the weather like in San Francisco, Tokyo, and Paris? Use Celsius."))
				.build()
			.prompt()
			.call()
			.content();
		// @formatter:on

		logger.info("Response: {}", response);

		assertThat(response).contains("30", "10", "15");
	}

	@Test
	void streamFunctionCallTest() {

		// @formatter:off
		Flux<String> response = ChatClient.create(this.chatModel).prompt()
				.user("What's the weather like in San Francisco, Tokyo, and Paris? Use Celsius.")
				.toolCallbacks(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
					.description("Get the weather in location")
					.inputType(MockWeatherService.Request.class)
					.build())
				.stream()
				.content();
		// @formatter:on

		String content = response.collectList().block().stream().collect(Collectors.joining());
		logger.info("Response: {}", content);

		assertThat(content).contains("30", "10", "15");
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "claude-sonnet-4-5", "claude-sonnet-4-0" })
	void multiModalityEmbeddedImage(String modelName) throws IOException {

		// @formatter:off
		String response = ChatClient.create(this.chatModel).prompt()
				.options(AnthropicChatOptions.builder().model(modelName).build())
				.user(u -> u.text("Explain what do you see on this picture?")
						.media(MimeTypeUtils.IMAGE_PNG, new ClassPathResource("/test.png")))
				.call()
				.content();
		// @formatter:on

		logger.info(response);
		assertThat(response).containsAnyOf("bananas", "apple", "bowl", "basket", "fruit stand");
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "claude-sonnet-4-5", "claude-sonnet-4-0" })
	void multiModalityImageUrl(String modelName) throws IOException {

		// TODO: add url method that wrapps the checked exception.
		URL url = new URL("https://docs.spring.io/spring-ai/reference/_images/multimodal.test.png");

		// @formatter:off
		String response = ChatClient.create(this.chatModel).prompt()
				// TODO consider adding model(...) method to ChatClient as a shortcut to
				.options(AnthropicChatOptions.builder().model(modelName).build())
				.user(u -> u.text("Explain what do you see on this picture?").media(MimeTypeUtils.IMAGE_PNG, url))
				.call()
				.content();
		// @formatter:on

		logger.info(response);
		assertThat(response).containsAnyOf("bananas", "apple", "bowl", "basket", "fruit stand");
	}

	@Test
	void streamingMultiModality() throws IOException {

		// @formatter:off
		Flux<String> response = ChatClient.create(this.chatModel).prompt()
				.options(AnthropicChatOptions.builder().model(AnthropicApi.ChatModel.CLAUDE_SONNET_4_5)
						.build())
				.user(u -> u.text("Explain what do you see on this picture?")
						.media(MimeTypeUtils.IMAGE_PNG, new ClassPathResource("/test.png")))
				.stream()
				.content();
		// @formatter:on

		String content = response.collectList().block().stream().collect(Collectors.joining());

		logger.info("Response: {}", content);
		assertThat(content).containsAnyOf("bananas", "apple", "bowl", "basket", "fruit stand");
	}

	record ActorsFilms(String actor, List<String> movies) {

	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "claude-sonnet-4-5", "claude-sonnet-4-0" })
	void streamToolCallingResponseShouldNotContainToolCallMessages(String modelName) {

		ChatClient chatClient = ChatClient.builder(this.chatModel).build();

		Flux<ChatResponse> responses = chatClient.prompt()
			.options(ToolCallingChatOptions.builder().model(modelName).build())
			.tools(new MyTools())
			.user("Get current weather in Amsterdam and Paris")
			// .user("Get current weather in Amsterdam. Please don't explain that you will
			// call tools.")
			.stream()
			.chatResponse();

		List<ChatResponse> chatResponses = responses.collectList().block();

		assertThat(chatResponses).isNotEmpty();

		// Verify that none of the ChatResponse objects have tool calls
		chatResponses.forEach(chatResponse -> {
			logger.info("ChatResponse Results: {}", chatResponse.getResults());
			assertThat(chatResponse.hasToolCalls()).isFalse();
		});
	}

	public static class MyTools {

		@Tool(description = "Get the current weather forecast by city name")
		String getCurrentDateTime(String cityName) {
			return "For " + cityName + " Weather is hot and sunny with a temperature of 20 degrees";
		}

	}

}
