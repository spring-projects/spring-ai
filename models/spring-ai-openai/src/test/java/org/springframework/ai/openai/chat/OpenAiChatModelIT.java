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

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.ChatClient;
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
import org.springframework.ai.model.Media;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiTestConfiguration;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.tool.MockWeatherService;
import org.springframework.ai.openai.testutils.AbstractIT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = OpenAiTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class OpenAiChatModelIT extends AbstractIT {

	private static final Logger logger = LoggerFactory.getLogger(OpenAiChatModelIT.class);

	@Value("classpath:/prompts/system-message.st")
	private Resource systemResource;

	@Test
	void roleTest() {
		UserMessage userMessage = new UserMessage(
				"Tell me about 3 famous pirates from the Golden Age of Piracy and what they did.");
		SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(this.systemResource);
		Message systemMessage = systemPromptTemplate.createMessage(Map.of("name", "Bob", "voice", "pirate"));
		Prompt prompt = new Prompt(List.of(userMessage, systemMessage));
		ChatResponse response = this.chatModel.call(prompt);
		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getResults().get(0).getOutput().getContent()).contains("Blackbeard");
		// needs fine tuning... evaluateQuestionAndAnswer(request, response, false);
	}

	@Test
	void testMessageHistory() {
		UserMessage userMessage = new UserMessage(
				"Tell me about 3 famous pirates from the Golden Age of Piracy and why they did.");
		SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(this.systemResource);
		Message systemMessage = systemPromptTemplate.createMessage(Map.of("name", "Bob", "voice", "pirate"));
		Prompt prompt = new Prompt(List.of(userMessage, systemMessage));

		ChatResponse response = this.chatModel.call(prompt);
		assertThat(response.getResult().getOutput().getContent()).containsAnyOf("Blackbeard", "Bartholomew");

		var promptWithMessageHistory = new Prompt(List.of(new UserMessage("Dummy"), response.getResult().getOutput(),
				new UserMessage("Repeat the last assistant message.")));
		response = this.chatModel.call(promptWithMessageHistory);

		assertThat(response.getResult().getOutput().getContent()).containsAnyOf("Blackbeard", "Bartholomew");
	}

	@Test
	void streamCompletenessTest() throws InterruptedException {
		UserMessage userMessage = new UserMessage(
				"List ALL natural numbers in range [1, 1000]. Make sure to not omit any.");
		Prompt prompt = new Prompt(List.of(userMessage));

		StringBuilder answer = new StringBuilder();
		CountDownLatch latch = new CountDownLatch(1);

		Flux<ChatResponse> chatResponseFlux = this.streamingChatModel.stream(prompt).doOnNext(chatResponse -> {
			String responseContent = chatResponse.getResults().get(0).getOutput().getContent();
			answer.append(responseContent);
		}).doOnComplete(() -> {
			logger.info(answer.toString());
			latch.countDown();
		});
		chatResponseFlux.subscribe();
		assertThat(latch.await(120, TimeUnit.SECONDS)).isTrue();
		IntStream.rangeClosed(1, 1000).forEach(n -> assertThat(answer).contains(String.valueOf(n)));
	}

	@Test
	void streamCompletenessTestWithChatResponse() throws InterruptedException {
		UserMessage userMessage = new UserMessage("Who is George Washington? - use first as 1st");
		Prompt prompt = new Prompt(List.of(userMessage));

		StringBuilder answer = new StringBuilder();
		CountDownLatch latch = new CountDownLatch(1);

		ChatClient chatClient = ChatClient.builder(this.openAiChatModel).build();

		Flux<ChatResponse> chatResponseFlux = chatClient.prompt(prompt)
			.stream()
			.chatResponse()
			.doOnNext(chatResponse -> {
				String responseContent = chatResponse.getResults().get(0).getOutput().getContent();
				answer.append(responseContent);
			})
			.doOnComplete(() -> {
				logger.info(answer.toString());
				latch.countDown();
			});
		chatResponseFlux.subscribe();
		assertThat(latch.await(120, TimeUnit.SECONDS)).isTrue();
		assertThat(answer).contains("1st ");
	}

	@Test
	void ensureChatResponseAsContentDoesNotSwallowBlankSpace() throws InterruptedException {
		UserMessage userMessage = new UserMessage("Who is George Washington? - use first as 1st");
		Prompt prompt = new Prompt(List.of(userMessage));

		StringBuilder answer = new StringBuilder();
		CountDownLatch latch = new CountDownLatch(1);

		ChatClient chatClient = ChatClient.builder(this.openAiChatModel).build();

		Flux<String> chatResponseFlux = chatClient.prompt(prompt)
			.stream()
			.content()
			.doOnNext(answer::append)
			.doOnComplete(() -> {
				logger.info(answer.toString());
				latch.countDown();
			});
		chatResponseFlux.subscribe();
		assertThat(latch.await(120, TimeUnit.SECONDS)).isTrue();
		assertThat(answer).contains("1st ");
	}

	@Test
	void streamRoleTest() {
		UserMessage userMessage = new UserMessage(
				"Tell me about 3 famous pirates from the Golden Age of Piracy and what they did.");
		SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(this.systemResource);
		Message systemMessage = systemPromptTemplate.createMessage(Map.of("name", "Bob", "voice", "pirate"));
		Prompt prompt = new Prompt(List.of(userMessage, systemMessage));
		Flux<ChatResponse> flux = this.streamingChatModel.stream(prompt);

		List<ChatResponse> responses = flux.collectList().block();
		assertThat(responses.size()).isGreaterThan(1);

		String stitchedResponseContent = responses.stream()
			.map(ChatResponse::getResults)
			.flatMap(List::stream)
			.map(Generation::getOutput)
			.map(AssistantMessage::getContent)
			.collect(Collectors.joining());

		assertThat(stitchedResponseContent).contains("Blackbeard");

	}

	@Test
	void streamingWithTokenUsage() {
		var promptOptions = OpenAiChatOptions.builder().withStreamUsage(true).withSeed(1).build();

		var prompt = new Prompt("List two colors of the Polish flag. Be brief.", promptOptions);
		var streamingTokenUsage = this.chatModel.stream(prompt).blockLast().getMetadata().getUsage();
		var referenceTokenUsage = this.chatModel.call(prompt).getMetadata().getUsage();

		assertThat(streamingTokenUsage.getPromptTokens()).isGreaterThan(0);
		assertThat(streamingTokenUsage.getGenerationTokens()).isGreaterThan(0);
		assertThat(streamingTokenUsage.getTotalTokens()).isGreaterThan(0);

		assertThat(streamingTokenUsage.getPromptTokens()).isEqualTo(referenceTokenUsage.getPromptTokens());
		assertThat(streamingTokenUsage.getGenerationTokens()).isEqualTo(referenceTokenUsage.getGenerationTokens());
		assertThat(streamingTokenUsage.getTotalTokens()).isEqualTo(referenceTokenUsage.getTotalTokens());

	}

	@Test
	void listOutputConverter() {
		DefaultConversionService conversionService = new DefaultConversionService();
		ListOutputConverter outputConverter = new ListOutputConverter(conversionService);

		String format = outputConverter.getFormat();
		String template = """
				List five {subject}
				{format}
				""";
		PromptTemplate promptTemplate = new PromptTemplate(template,
				Map.of("subject", "ice cream flavors", "format", format));
		Prompt prompt = new Prompt(promptTemplate.createMessage());
		Generation generation = this.chatModel.call(prompt).getResult();

		List<String> list = outputConverter.convert(generation.getOutput().getContent());
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
				Map.of("subject", "numbers from 1 to 9 under they key name 'numbers'", "format", format));
		Prompt prompt = new Prompt(promptTemplate.createMessage());
		Generation generation = this.chatModel.call(prompt).getResult();

		Map<String, Object> result = outputConverter.convert(generation.getOutput().getContent());
		assertThat(result.get("numbers")).isEqualTo(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9));

	}

	@Test
	void beanOutputConverter() {

		BeanOutputConverter<ActorsFilms> outputConverter = new BeanOutputConverter<>(ActorsFilms.class);

		String format = outputConverter.getFormat();
		String template = """
				Generate the filmography for a random actor.
				{format}
				""";
		PromptTemplate promptTemplate = new PromptTemplate(template, Map.of("format", format));
		Prompt prompt = new Prompt(promptTemplate.createMessage());
		Generation generation = this.chatModel.call(prompt).getResult();

		ActorsFilms actorsFilms = outputConverter.convert(generation.getOutput().getContent());
	}

	@Test
	void beanOutputConverterRecords() {

		BeanOutputConverter<ActorsFilmsRecord> outputConverter = new BeanOutputConverter<>(ActorsFilmsRecord.class);

		String format = outputConverter.getFormat();
		String template = """
				Generate the filmography of 5 movies for Tom Hanks.
				{format}
				""";
		PromptTemplate promptTemplate = new PromptTemplate(template, Map.of("format", format));
		Prompt prompt = new Prompt(promptTemplate.createMessage());
		Generation generation = this.chatModel.call(prompt).getResult();

		ActorsFilmsRecord actorsFilms = outputConverter.convert(generation.getOutput().getContent());
		logger.info("" + actorsFilms);
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
			.map(AssistantMessage::getContent)
			.collect(Collectors.joining());

		ActorsFilmsRecord actorsFilms = outputConverter.convert(generationTextFromStream);
		logger.info("" + actorsFilms);
		assertThat(actorsFilms.actor()).isEqualTo("Tom Hanks");
		assertThat(actorsFilms.movies()).hasSize(5);
	}

	@Test
	void functionCallTest() {

		UserMessage userMessage = new UserMessage("What's the weather like in San Francisco, Tokyo, and Paris?");

		List<Message> messages = new ArrayList<>(List.of(userMessage));

		var promptOptions = OpenAiChatOptions.builder()
			.withModel(OpenAiApi.ChatModel.GPT_4_O.getValue())
			.withFunctionCallbacks(List.of(FunctionCallback.builder()
				.description("Get the weather in location")
				.function("getCurrentWeather", new MockWeatherService())
				.inputType(MockWeatherService.Request.class)
				.build()))
			.build();

		ChatResponse response = this.chatModel.call(new Prompt(messages, promptOptions));

		logger.info("Response: {}", response);

		assertThat(response.getResult().getOutput().getContent()).containsAnyOf("30.0", "30");
		assertThat(response.getResult().getOutput().getContent()).containsAnyOf("10.0", "10");
		assertThat(response.getResult().getOutput().getContent()).containsAnyOf("15.0", "15");
	}

	@Test
	void streamFunctionCallTest() {

		UserMessage userMessage = new UserMessage("What's the weather like in San Francisco, Tokyo, and Paris?");

		List<Message> messages = new ArrayList<>(List.of(userMessage));

		var promptOptions = OpenAiChatOptions.builder()
			// .withModel(OpenAiApi.ChatModel.GPT_4_TURBO_PREVIEW.getValue())
			.withFunctionCallbacks(List.of(FunctionCallback.builder()
				.description("Get the weather in location")
				.function("getCurrentWeather", new MockWeatherService())
				.inputType(MockWeatherService.Request.class)
				.build()))
			.build();

		Flux<ChatResponse> response = this.streamingChatModel.stream(new Prompt(messages, promptOptions));

		String content = response.collectList()
			.block()
			.stream()
			.map(ChatResponse::getResults)
			.flatMap(List::stream)
			.map(Generation::getOutput)
			.map(AssistantMessage::getContent)
			.collect(Collectors.joining());
		logger.info("Response: {}", content);

		assertThat(content).containsAnyOf("30.0", "30");
		assertThat(content).containsAnyOf("10.0", "10");
		assertThat(content).containsAnyOf("15.0", "15");
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "gpt-4o" })
	void multiModalityEmbeddedImage(String modelName) throws IOException {

		var imageData = new ClassPathResource("/test.png");

		var userMessage = new UserMessage("Explain what do you see on this picture?",
				List.of(new Media(MimeTypeUtils.IMAGE_PNG, imageData)));

		var response = this.chatModel
			.call(new Prompt(List.of(userMessage), OpenAiChatOptions.builder().withModel(modelName).build()));

		logger.info(response.getResult().getOutput().getContent());
		assertThat(response.getResult().getOutput().getContent()).contains("bananas", "apple");
		assertThat(response.getResult().getOutput().getContent()).containsAnyOf("bowl", "basket", "fruit stand");
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "gpt-4o" })
	void multiModalityImageUrl(String modelName) throws IOException {

		var userMessage = new UserMessage("Explain what do you see on this picture?",
				List.of(new Media(MimeTypeUtils.IMAGE_PNG,
						new URL("https://docs.spring.io/spring-ai/reference/_images/multimodal.test.png"))));

		ChatResponse response = this.chatModel
			.call(new Prompt(List.of(userMessage), OpenAiChatOptions.builder().withModel(modelName).build()));

		logger.info(response.getResult().getOutput().getContent());
		assertThat(response.getResult().getOutput().getContent()).contains("bananas", "apple");
		assertThat(response.getResult().getOutput().getContent()).containsAnyOf("bowl", "basket", "fruit stand");
	}

	@Test
	void streamingMultiModalityImageUrl() throws IOException {

		var userMessage = new UserMessage("Explain what do you see on this picture?",
				List.of(new Media(MimeTypeUtils.IMAGE_PNG,
						new URL("https://docs.spring.io/spring-ai/reference/_images/multimodal.test.png"))));

		Flux<ChatResponse> response = this.streamingChatModel.stream(new Prompt(List.of(userMessage),
				OpenAiChatOptions.builder().withModel(OpenAiApi.ChatModel.GPT_4_O.getValue()).build()));

		String content = response.collectList()
			.block()
			.stream()
			.map(ChatResponse::getResults)
			.flatMap(List::stream)
			.map(Generation::getOutput)
			.map(AssistantMessage::getContent)
			.collect(Collectors.joining());
		logger.info("Response: {}", content);
		assertThat(content).contains("bananas", "apple");
		assertThat(content).containsAnyOf("bowl", "basket", "fruit stand");
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "gpt-4o-audio-preview" })
	void multiModalityInputAudio(String modelName) {
		var audioResource = new ClassPathResource("speech1.mp3");
		var userMessage = new UserMessage("What is this recording about?",
				List.of(new Media(MimeTypeUtils.parseMimeType("audio/mp3"), audioResource)));

		ChatResponse response = chatModel
			.call(new Prompt(List.of(userMessage), OpenAiChatOptions.builder().withModel(modelName).build()));

		logger.info(response.getResult().getOutput().getContent());
		assertThat(response.getResult().getOutput().getContent()).containsIgnoringCase("hobbits");
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "gpt-4o-audio-preview" })
	void streamingMultiModalityInputAudio(String modelName) {
		var audioResource = new ClassPathResource("speech1.mp3");
		var userMessage = new UserMessage("What is this recording about?",
				List.of(new Media(MimeTypeUtils.parseMimeType("audio/mp3"), audioResource)));

		Flux<ChatResponse> response = chatModel
			.stream(new Prompt(List.of(userMessage), OpenAiChatOptions.builder().withModel(modelName).build()));

		String content = response.collectList()
			.block()
			.stream()
			.map(ChatResponse::getResults)
			.flatMap(List::stream)
			.map(Generation::getOutput)
			.map(AssistantMessage::getContent)
			.collect(Collectors.joining());
		logger.info("Response: {}", content);
		assertThat(content).containsIgnoringCase("hobbits");
	}

	@Test
	void validateCallResponseMetadata() {
		String model = OpenAiApi.ChatModel.GPT_3_5_TURBO.getName();
		// @formatter:off
		ChatResponse response = ChatClient.create(this.chatModel).prompt()
				.options(OpenAiChatOptions.builder().withModel(model).build())
				.user("Tell me about 3 famous pirates from the Golden Age of Piracy and what they did")
				.call()
				.chatResponse();
		// @formatter:on

		logger.info(response.toString());
		assertThat(response.getMetadata().getId()).isNotEmpty();
		assertThat(response.getMetadata().getModel()).containsIgnoringCase(model);
		assertThat(response.getMetadata().getUsage().getPromptTokens()).isPositive();
		assertThat(response.getMetadata().getUsage().getGenerationTokens()).isPositive();
		assertThat(response.getMetadata().getUsage().getTotalTokens()).isPositive();
	}

	record ActorsFilmsRecord(String actor, List<String> movies) {

	}

}
