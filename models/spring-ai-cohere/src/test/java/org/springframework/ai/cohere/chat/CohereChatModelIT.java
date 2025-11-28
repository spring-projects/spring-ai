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

package org.springframework.ai.cohere.chat;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.cohere.CohereTestConfiguration;
import org.springframework.ai.cohere.api.CohereApi;
import org.springframework.ai.cohere.api.CohereApi.ChatModel;
import org.springframework.ai.cohere.api.tool.MockWeatherService;
import org.springframework.ai.cohere.testutils.AbstractIT;
import org.springframework.ai.content.Media;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.ListOutputConverter;
import org.springframework.ai.converter.MapOutputConverter;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Ricken Bazolo
 */
@SpringBootTest(classes = CohereTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "COHERE_API_KEY", matches = ".+")
class CohereChatModelIT extends AbstractIT {

	private static final Logger logger = LoggerFactory.getLogger(CohereChatModelIT.class);

	@Test
	void roleTest() {
		UserMessage userMessage = new UserMessage(
				"Tell me about 3 famous pirates from the Golden Age of Piracy and why they did.");
		SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(this.systemResource);
		Message systemMessage = systemPromptTemplate.createMessage(Map.of("name", "Bob", "voice", "pirate"));
		Prompt prompt = new Prompt(List.of(userMessage, systemMessage));
		ChatResponse response = this.chatModel.call(prompt);
		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getResults().get(0).getOutput().getText()).contains("Blackbeard");
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
		PromptTemplate promptTemplate = PromptTemplate.builder()
			.template(template)
			.variables(Map.of("subject", "ice cream flavors", "format", format))
			.build();
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
		PromptTemplate promptTemplate = PromptTemplate.builder()
			.template(template)
			.variables(Map.of("subject", "an array of numbers from 1 to 9 under they key name 'numbers'", "format",
					format))
			.build();
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
				""";
		PromptTemplate promptTemplate = PromptTemplate.builder()
			.template(template)
			.variables(Map.of("format", format))
			.build();
		Prompt prompt = new Prompt(promptTemplate.createMessage());
		Generation generation = this.chatModel.call(prompt).getResult();

		ActorsFilmsRecord actorsFilms = outputConverter.convert(generation.getOutput().getText());
		logger.info("{}", actorsFilms);
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
		PromptTemplate promptTemplate = PromptTemplate.builder()
			.template(template)
			.variables(Map.of("format", format))
			.build();
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

		ActorsFilmsRecord actorsFilms = outputConverter.convert(generationTextFromStream);
		logger.info("" + actorsFilms);
		assertThat(actorsFilms.actor()).isEqualTo("Tom Hanks");
		assertThat(actorsFilms.movies()).hasSize(5);
	}

	@Test
	void functionCallTest() {

		UserMessage userMessage = new UserMessage(
				"What's the weather like in San Francisco, Tokyo, and Paris? Response in Celsius");

		List<Message> messages = new ArrayList<>(List.of(userMessage));

		var promptOptions = CohereChatOptions.builder()
			.model(CohereApi.ChatModel.COMMAND_A_R7B.getValue())
			.toolCallbacks(List.of(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
				.description("Get the weather in location")
				.inputType(MockWeatherService.Request.class)
				.build()))
			.build();

		ChatResponse response = this.chatModel.call(new Prompt(messages, promptOptions));

		logger.info("Response: {}", response);

		assertThat(response.getResult().getOutput().getText()).containsAnyOf("30.0", "30");
		assertThat(response.getMetadata()).isNotNull();
		assertThat(response.getMetadata().getUsage()).isNotNull();
	}

	@Test
	void streamFunctionCallTest() {

		UserMessage userMessage = new UserMessage("What's the weather like in Tokyo, Japan? Response in Celsius");

		List<Message> messages = new ArrayList<>(List.of(userMessage));

		var promptOptions = CohereChatOptions.builder()
			.model(CohereApi.ChatModel.COMMAND_A_R7B.getValue())
			.toolCallbacks(List.of(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
				.description("Get the weather in location")
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
			.map(AssistantMessage::getText)
			.collect(Collectors.joining());
		logger.info("Response: {}", content);

		assertThat(content).containsAnyOf("10.0", "10");
	}

	@Test
	void streamFunctionCallUsageTest() {
		UserMessage userMessage = new UserMessage(
				"What's the weather like in San Francisco, Tokyo, and Paris? Response in Celsius");

		List<Message> messages = new ArrayList<>(List.of(userMessage));

		var promptOptions = CohereChatOptions.builder()
			.model(CohereApi.ChatModel.COMMAND_A_R7B.getValue())
			.toolCallbacks(List.of(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
				.description("Get the weather in location")
				.inputType(MockWeatherService.Request.class)
				.build()))
			.build();

		Flux<ChatResponse> response = this.streamingChatModel.stream(new Prompt(messages, promptOptions));
		ChatResponse chatResponse = response.last().block();

		logger.info("Response: {}", chatResponse);
		assertThat(chatResponse.getMetadata()).isNotNull();
		assertThat(chatResponse.getMetadata().getUsage()).isNotNull();
	}

	@Test
	void chatMemory() {
		ChatMemory memory = MessageWindowChatMemory.builder().build();
		String conversationId = UUID.randomUUID().toString();

		UserMessage userMessage1 = new UserMessage("My name is James Bond");
		memory.add(conversationId, userMessage1);
		ChatResponse response1 = this.chatModel.call(new Prompt(memory.get(conversationId)));

		assertThat(response1).isNotNull();
		memory.add(conversationId, response1.getResult().getOutput());

		UserMessage userMessage2 = new UserMessage("What is my name?");
		memory.add(conversationId, userMessage2);
		ChatResponse response2 = this.chatModel.call(new Prompt(memory.get(conversationId)));

		assertThat(response2).isNotNull();
		memory.add(conversationId, response2.getResult().getOutput());

		assertThat(response2.getResults()).hasSize(1);
		assertThat(response2.getResult().getOutput().getText()).contains("James Bond");
	}

	@Test
	void chatMemoryWithTools() {
		ToolCallingManager toolCallingManager = DefaultToolCallingManager.builder().build();
		ChatMemory chatMemory = MessageWindowChatMemory.builder().build();
		String conversationId = UUID.randomUUID().toString();

		ChatOptions chatOptions = ToolCallingChatOptions.builder()
			.toolCallbacks(ToolCallbacks.from(new MathTools()))
			.internalToolExecutionEnabled(false)
			.build();
		Prompt prompt = new Prompt(
				List.of(new SystemMessage("You are a helpful assistant."), new UserMessage("What is 6 * 8?")),
				chatOptions);
		chatMemory.add(conversationId, prompt.getInstructions());

		Prompt promptWithMemory = new Prompt(chatMemory.get(conversationId), chatOptions);
		ChatResponse chatResponse = this.chatModel.call(promptWithMemory);
		chatMemory.add(conversationId, chatResponse.getResult().getOutput());

		while (chatResponse.hasToolCalls()) {
			ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(promptWithMemory,
					chatResponse);
			chatMemory.add(conversationId, toolExecutionResult.conversationHistory()
				.get(toolExecutionResult.conversationHistory().size() - 1));
			promptWithMemory = new Prompt(chatMemory.get(conversationId), chatOptions);
			chatResponse = this.chatModel.call(promptWithMemory);
			chatMemory.add(conversationId, chatResponse.getResult().getOutput());
		}

		assertThat(chatResponse).isNotNull();
		assertThat(chatResponse.getResult().getOutput().getText()).contains("48");

		UserMessage newUserMessage = new UserMessage("What did I ask you earlier?");
		chatMemory.add(conversationId, newUserMessage);

		ChatResponse newResponse = this.chatModel.call(new Prompt(chatMemory.get(conversationId)));

		assertThat(newResponse).isNotNull();
		assertThat(newResponse.getResult().getOutput().getText()).contains("6").contains("8");
	}

	@Test
	void streamingMultiModalityImageUrl() throws IOException {

		var userMessage = UserMessage.builder()
			.text("Explain what do you see on this picture?")
			.media(List.of(Media.builder()
				.mimeType(MimeTypeUtils.IMAGE_PNG)
				.data(URI.create("https://docs.spring.io/spring-ai/reference/_images/multimodal.test.png"))
				.build()))
			.build();

		Flux<ChatResponse> response = this.streamingChatModel.stream(new Prompt(List.of(userMessage),
				CohereChatOptions.builder().model(ChatModel.COMMAND_A_VISION.getValue()).build()));

		String content = response.collectList()
			.block()
			.stream()
			.map(ChatResponse::getResults)
			.flatMap(List::stream)
			.map(Generation::getOutput)
			.map(AssistantMessage::getText)
			.collect(Collectors.joining());
		logger.info("Response: {}", content);
		assertThat(content).containsAnyOf("bananas", "apple", "bowl", "basket", "fruit stand");
	}

	static class MathTools {

		@Tool(description = "Multiply the two numbers")
		double multiply(double a, double b) {
			return a * b;
		}

	}

	record ActorsFilmsRecord(String actor, List<String> movies) {

	}

}
