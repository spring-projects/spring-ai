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

package org.springframework.ai.openai.chat.client;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.ListOutputConverter;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiTestConfiguration;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionRequest.AudioParameters;
import org.springframework.ai.openai.api.tool.MockWeatherService;
import org.springframework.ai.openai.testutils.AbstractIT;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.ai.test.CurlyBracketEscaper;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest(classes = OpenAiTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@ActiveProfiles("logging-test")
class OpenAiChatClientIT extends AbstractIT {

	private static final Logger logger = LoggerFactory.getLogger(OpenAiChatClientIT.class);

	@Value("classpath:/prompts/system-message.st")
	private Resource systemTextResource;

	@Test
	@Disabled("Although the Re2 advisor improves the response correctness it is not always guarantied to work.")
	void re2() {
		// .user(" Could Scooby Doo fit in a Kangaroo Pouch? Choices: (A) Yes (B) No")
		// .user("Roger has 5 tennis balls. He buys 2 more cans of tennis " +
		// "balls. Each can has 3 tennis balls. How many tennis balls " +
		// "does he have now?")

		String REASON_QUESTION = """
				What do these words have in common?
				Freight Stone Often Canine.
					""";

		// @formatter:off
		ChatClient chatClient = ChatClient.builder(this.chatModel)
			.defaultOptions(OpenAiChatOptions.builder()
				.model(OpenAiApi.ChatModel.GPT_4_O.getValue()).build())
			.defaultUser(REASON_QUESTION)
			.build();

		String response = chatClient.prompt()
				.advisors(new ReReadingAdvisor())
				.call()
				.content();
		// @formatter:on

		logger.info("" + response);
		assertThat(response.toLowerCase().replace("(", " ").replace(")", " ").replace("\"", " ").replace("\"", " "))
			.contains(" eight", " one", " ten", " nine");
	}

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
				.entity(new ParameterizedTypeReference<>() {
				});
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
	void beanOutputConverterNativeStructuredOutput() {

		// @formatter:off
		ActorsFilms actorsFilms = ChatClient.create(this.chatModel).prompt()
				.advisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
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
	void beanOutputConverterRecordsNativeStructuredOutput() {

		// @formatter:off
		ActorsFilms actorsFilms = ChatClient.create(this.chatModel).prompt()
				.advisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
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
				.options(OpenAiChatOptions.builder().streamUsage(true).build())
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
				.filter(text -> text != null && !text.trim().isEmpty()) // Filter out empty/null text
				.collect(Collectors.joining());
		// @formatter:on

		// Add debugging to understand what text we're trying to parse
		logger.debug("Aggregated streaming text: {}", generationTextFromStream);

		// Ensure we have valid JSON before attempting conversion
		if (generationTextFromStream.trim().isEmpty()) {
			fail("Empty aggregated text from streaming response - this indicates a problem with streaming aggregation");
		}

		ActorsFilms actorsFilms = outputConverter.convert(generationTextFromStream);

		logger.info("" + actorsFilms);
		assertThat(actorsFilms.actor()).isEqualTo("Tom Hanks");
		assertThat(actorsFilms.movies()).hasSize(5);
	}

	@Test
	void functionCallTest() {

		// @formatter:off
		String response = ChatClient.create(this.chatModel).prompt()
				.user(u -> u.text("What's the weather like in San Francisco, Tokyo, and Paris?"))
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
	void defaultFunctionCallTest() {

		// @formatter:off
		String response = ChatClient.builder(this.chatModel)
				.defaultToolCallbacks(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
					.description("Get the weather in location")
					.inputType(MockWeatherService.Request.class)
					.build())
				.defaultUser(u -> u.text("What's the weather like in San Francisco, Tokyo, and Paris?"))
			.build()
			.prompt().call().content();
		// @formatter:on

		logger.info("Response: {}", response);

		assertThat(response).contains("30", "10", "15");
	}

	@Test
	void streamFunctionCallTest() {

		// @formatter:off
		Flux<String> response = ChatClient.create(this.chatModel).prompt()
				.user("What's the weather like in San Francisco, Tokyo, and Paris?")
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
	@ValueSource(strings = { "gpt-4o" })
	void multiModalityEmbeddedImage(String modelName) throws IOException {

		// @formatter:off
		String response = ChatClient.create(this.chatModel).prompt()
				.options(OpenAiChatOptions.builder().model(modelName).build())
				.user(u -> u.text("Explain what do you see on this picture?")
						.media(MimeTypeUtils.IMAGE_PNG, new ClassPathResource("/test.png")))
				.call()
				.content();
		// @formatter:on

		logger.info(response);
		assertThat(response).containsAnyOf("bananas", "apple", "bowl", "basket", "fruit stand");
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "gpt-4o" })
	void multiModalityImageUrl(String modelName) throws IOException {

		// TODO: add url method that wraps the checked exception.
		URL url = new URL("https://docs.spring.io/spring-ai/reference/_images/multimodal.test.png");

		// @formatter:off
		String response = ChatClient.create(this.chatModel).prompt()
				// TODO consider adding model(...) method to ChatClient as a shortcut to
				.options(OpenAiChatOptions.builder().model(modelName).build())
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
				.options(OpenAiChatOptions.builder().model(OpenAiApi.ChatModel.GPT_4_O.getValue())
						.build())
				.user(u -> u.text("Explain what do you see on this picture?")
						.media(MimeTypeUtils.IMAGE_PNG, url))
				.stream()
				.content();
		// @formatter:on

		String content = response.collectList().block().stream().collect(Collectors.joining());

		logger.info("Response: {}", content);
		assertThat(content).containsAnyOf("bananas", "apple", "bowl", "basket", "fruit stand");
	}

	@Test
	void multiModalityAudioResponse() {
		ChatResponse response = ChatClient.create(this.chatModel)
			.prompt("Tell me joke about Spring Framework")
			.options(OpenAiChatOptions.builder()
				.model(OpenAiApi.ChatModel.GPT_4_O_AUDIO_PREVIEW)
				.outputAudio(new AudioParameters(AudioParameters.Voice.ALLOY, AudioParameters.AudioResponseFormat.WAV))
				.outputModalities(List.of("text", "audio"))
				.build())
			.call()
			.chatResponse();

		assertThat(response).isNotNull();
		assertThat(response.getResult().getOutput().getMedia().get(0).getDataAsByteArray()).isNotEmpty();
		logger.info("Response: " + response);
	}

	@Test
	void customTemplateRendererWithCall() {
		BeanOutputConverter<ActorsFilms> outputConverter = new BeanOutputConverter<>(ActorsFilms.class);

		// @formatter:off
		String result = ChatClient.create(this.chatModel).prompt()
				.user(u -> u
						.text("Generate the filmography of 5 movies for Tom Hanks. " + System.lineSeparator()
								+ "<format>")
						.param("format", outputConverter.getFormat()))
				.templateRenderer(StTemplateRenderer.builder().startDelimiterToken('<').endDelimiterToken('>').build())
				.call()
				.content();
		// @formatter:on

		assertThat(result).isNotEmpty();
		ActorsFilms actorsFilms = outputConverter.convert(result);

		logger.info("" + actorsFilms);
		assertThat(actorsFilms.actor()).isEqualTo("Tom Hanks");
		assertThat(actorsFilms.movies()).hasSize(5);
	}

	@Test
	void customTemplateRendererWithCallAndAdvisor() {
		BeanOutputConverter<ActorsFilms> outputConverter = new BeanOutputConverter<>(ActorsFilms.class);

		// @formatter:off
		String result = ChatClient.create(this.chatModel).prompt()
				.advisors(new SimpleLoggerAdvisor())
				.user(u -> u
						.text("Generate the filmography of 5 movies for Tom Hanks. " + System.lineSeparator()
								+ "<format>")
						.param("format", outputConverter.getFormat()))
				.templateRenderer(StTemplateRenderer.builder().startDelimiterToken('<').endDelimiterToken('>').build())
				.call()
				.content();
		// @formatter:on

		assertThat(result).isNotEmpty();
		ActorsFilms actorsFilms = outputConverter.convert(result);

		logger.info("" + actorsFilms);
		assertThat(actorsFilms.actor()).isEqualTo("Tom Hanks");
		assertThat(actorsFilms.movies()).hasSize(5);
	}

	@Test
	void customTemplateRendererWithStream() {
		BeanOutputConverter<ActorsFilms> outputConverter = new BeanOutputConverter<>(ActorsFilms.class);

		// @formatter:off
		Flux<ChatResponse> chatResponse = ChatClient.create(this.chatModel)
				.prompt()
				.options(OpenAiChatOptions.builder().streamUsage(true).build())
				.user(u -> u
						.text("Generate the filmography of 5 movies for Tom Hanks. " + System.lineSeparator()
								+ "<format>")
						.param("format", outputConverter.getFormat()))
				.templateRenderer(StTemplateRenderer.builder().startDelimiterToken('<').endDelimiterToken('>').build())
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
	void customTemplateRendererWithStreamAndAdvisor() {
		BeanOutputConverter<ActorsFilms> outputConverter = new BeanOutputConverter<>(ActorsFilms.class);

		// @formatter:off
		Flux<ChatResponse> chatResponse = ChatClient.create(this.chatModel)
				.prompt()
				.options(OpenAiChatOptions.builder().streamUsage(true).build())
				.advisors(new SimpleLoggerAdvisor())
				.user(u -> u
						.text("Generate the filmography of 5 movies for Tom Hanks. " + System.lineSeparator()
								+ "<format>")
						.param("format", outputConverter.getFormat()))
				.templateRenderer(StTemplateRenderer.builder().startDelimiterToken('<').endDelimiterToken('>').build())
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

	record ActorsFilms(String actor, List<String> movies) {

	}

}
