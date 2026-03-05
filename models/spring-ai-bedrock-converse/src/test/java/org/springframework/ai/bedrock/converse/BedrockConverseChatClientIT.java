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

package org.springframework.ai.bedrock.converse;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.ListOutputConverter;
import org.springframework.ai.test.CurlyBracketEscaper;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = BedrockConverseTestConfiguration.class)
@RequiresAwsCredentials
class BedrockConverseChatClientIT {

	private static final Logger logger = LoggerFactory.getLogger(BedrockConverseChatClientIT.class);

	@Value("classpath:/prompts/system-message.st")
	private Resource systemTextResource;

	@Autowired
	private ChatModel chatModel;

	@Test
	void call() {

		// @formatter:off
		ChatResponse response = ChatClient.create(this.chatModel).prompt()
				.advisors(new SimpleLoggerAdvisor())
				.system(s -> s.text(this.systemTextResource)
						.param("name", "Bob")
						.param("voice", "pirate"))
				.user(u -> u.text("Tell me about 3 famous pirates from the Golden Age of Piracy and what they did")
						.metadata("requestId", "12345")
				)
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
		Flux<ChatResponse> chatResponse = ChatClient.create(this.chatModel)
				.prompt()
				.advisors(new SimpleLoggerAdvisor())
				.user(u -> u
						.text("Generate the filmography of 5 movies for Tom Hanks. " + System.lineSeparator()
								+ "{format}")
						.param("format", CurlyBracketEscaper.escapeCurlyBrackets(outputConverter.getFormat())))
				.stream()
				.chatResponse();

		List<ChatResponse> chatResponses = chatResponse.collectList()
				.block()
				.stream()
				.toList();

		String generationTextFromStream = chatResponses
				.stream()
				.filter(cr -> cr.getResult() != null)
				.map(cr -> cr.getResult().getOutput().getText())
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
		String response = ChatClient.create(this.chatModel)
				.prompt("What's the weather like in San Francisco, Tokyo, and Paris? Return the temperature in Celsius.")
				.toolCallbacks(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
					.description("Get the weather in location")
					.inputType(MockWeatherService.Request.class)
					.build())
				.call()
				.content();
		// @formatter:on

		logger.info("Response: {}", response);

		assertThat(response).contains("30", "10", "15");
	}

	@Test
	void functionCallWithUsageMetadataTest() {

		// @formatter:off
		ChatResponse response = ChatClient.create(this.chatModel)
				.prompt("What's the weather like in San Francisco, Tokyo, and Paris? Return the temperature in Celsius.")
				.toolCallbacks(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
					.description("Get the weather in location")
					.inputType(MockWeatherService.Request.class)
					.build())
				.call()
				.chatResponse();
		// @formatter:on

		var metadata = response.getMetadata();

		assertThat(metadata.getUsage()).isNotNull();

		logger.info(metadata.getUsage().toString());

		assertThat(metadata.getUsage().getPromptTokens()).isGreaterThan(500);
		assertThat(metadata.getUsage().getPromptTokens()).isLessThan(3500);

		assertThat(metadata.getUsage().getCompletionTokens()).isGreaterThan(0);
		assertThat(metadata.getUsage().getCompletionTokens()).isLessThan(1500);

		assertThat(metadata.getUsage().getTotalTokens())
			.isEqualTo(metadata.getUsage().getPromptTokens() + metadata.getUsage().getCompletionTokens());

		logger.info("Response: {}", response);

		assertThat(response.getResult().getOutput().getText()).contains("30", "10", "15");
	}

	@Test
	void functionCallWithAdvisorTest() {

		// @formatter:off
		String response = ChatClient.create(this.chatModel)
				.prompt("What's the weather like in San Francisco, Tokyo, and Paris? Return the temperature in Celsius.")
				.toolCallbacks(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
					.description("Get the weather in location")
					.inputType(MockWeatherService.Request.class)
					.build())
				.advisors(new SimpleLoggerAdvisor())
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
			.defaultUser(u -> u.text("What's the weather like in San Francisco, Tokyo, and Paris? Return the temperature in Celsius."))
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
		Flux<ChatResponse> response = ChatClient.create(this.chatModel).prompt()
				.user("What's the weather like in San Francisco, Tokyo, and Paris? Return the temperature in Celsius.")
				.toolCallbacks(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
					.description("Get the weather in location")
					.inputType(MockWeatherService.Request.class)
					.build())
				.stream()
				.chatResponse();
		// @formatter:on

		List<ChatResponse> chatResponses = response.collectList().block();

		// chatResponses.forEach(cr -> logger.info("Response: {}", cr));
		var lastChatResponse = chatResponses.get(chatResponses.size() - 1);
		var metadata = lastChatResponse.getMetadata();
		assertThat(metadata.getUsage()).isNotNull();

		logger.info(metadata.getUsage().toString());

		assertThat(metadata.getUsage().getPromptTokens()).isGreaterThan(1000);
		assertThat(metadata.getUsage().getPromptTokens()).isLessThan(1500);

		assertThat(metadata.getUsage().getCompletionTokens()).isGreaterThan(0);
		assertThat(metadata.getUsage().getCompletionTokens()).isLessThan(600);

		assertThat(metadata.getUsage().getTotalTokens())
			.isEqualTo(metadata.getUsage().getPromptTokens() + metadata.getUsage().getCompletionTokens());

		String content = chatResponses.stream()
			.filter(cr -> cr.getResult() != null)
			.map(cr -> cr.getResult().getOutput().getText())
			.collect(Collectors.joining());
		logger.info("Response: {}", content);

		assertThat(content).contains("30", "10", "15");
	}

	@Test
	void singularStreamFunctionCallTest() {

		// @formatter:off
		Flux<String> response = ChatClient.create(this.chatModel).prompt()
				.user("What's the weather like in Paris? Return the temperature in Celsius.")
				.toolCallbacks(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
					.description("Get the weather in location")
					.inputType(MockWeatherService.Request.class)
					.build())
				.stream()
				.content();
		// @formatter:on

		String content = response.collectList().block().stream().collect(Collectors.joining());
		logger.info("Response: {}", content);

		assertThat(content).contains("15");
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "us.anthropic.claude-haiku-4-5-20251001-v1:0" })
	void multiModalityEmbeddedImage(String modelName) throws IOException {

		// @formatter:off
		String response = ChatClient.create(this.chatModel).prompt()
				.options(BedrockChatOptions.builder().model(modelName).build())
				.user(u -> u.text("Explain what do you see on this picture?")
						.media(MimeTypeUtils.IMAGE_PNG, new ClassPathResource("/test.png")))
				.call()
				.content();
		// @formatter:on

		logger.info(response);
		assertThat(response).containsAnyOf("bananas", "apple", "bowl", "basket", "fruit stand");
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "us.anthropic.claude-haiku-4-5-20251001-v1:0" })
	void multiModalityImageUrl2(String modelName) throws IOException {

		// TODO: add url method that wraps the checked exception.
		URL url = new URL("https://docs.spring.io/spring-ai/reference/_images/multimodal.test.png");

		// @formatter:off
		String response = ChatClient.create(this.chatModel).prompt()
		// TODO consider adding model(...) method to ChatClient as a shortcut to
		.options(BedrockChatOptions.builder().model(modelName).build())
		.user(u -> u.text("Explain what do you see on this picture?").media(MimeTypeUtils.IMAGE_PNG, url))
		.call()
		.content();
		// @formatter:on

		logger.info(response);
		assertThat(response).containsAnyOf("bananas", "apple", "bowl", "basket", "fruit stand");
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "us.anthropic.claude-haiku-4-5-20251001-v1:0" })
	void multiModalityImageUrl(String modelName) throws IOException {

		// TODO: add url method that wraps the checked exception.
		URL url = new URL("https://docs.spring.io/spring-ai/reference/_images/multimodal.test.png");

		// @formatter:off
		String response = ChatClient.create(this.chatModel).prompt()
		// TODO consider adding model(...) method to ChatClient as a shortcut to
		.options(BedrockChatOptions.builder().model(modelName).build())
		.user(u -> u.text("Explain what do you see on this picture?").media(MimeTypeUtils.IMAGE_PNG, url))
		.call()
		.content();
		// @formatter:on

		logger.info(response);
		assertThat(response).containsAnyOf("bananas", "apple", "bowl", "basket", "fruit stand");
	}

	@Test
	void streamingMultiModalityImageUrl() throws IOException {

		// TODO: add url method that wraps the checked exception.
		URL url = new URL("https://docs.spring.io/spring-ai/reference/_images/multimodal.test.png");

		// @formatter:off
		Flux<String> response = ChatClient.create(this.chatModel).prompt()
				.user(u -> u.text("Explain what do you see on this picture?")
						.media(MimeTypeUtils.IMAGE_PNG, url))
				.stream()
				.content();
		// @formatter:on

		String content = response.collectList().block().stream().collect(Collectors.joining());

		logger.info("Response: {}", content);
		assertThat(content).containsAnyOf("bananas", "apple", "bowl", "basket", "fruit stand");
	}

	record ActorsFilms(String actor, List<String> movies) {

	}

}
