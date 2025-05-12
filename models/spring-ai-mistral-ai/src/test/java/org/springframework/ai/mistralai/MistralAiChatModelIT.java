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

package org.springframework.ai.mistralai;

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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.content.Media;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.ListOutputConverter;
import org.springframework.ai.converter.MapOutputConverter;
import org.springframework.ai.mistralai.api.MistralAiApi;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 * @author Alexandros Pappas
 * @author Thomas Vitale
 * @since 0.8.1
 */
@SpringBootTest(classes = MistralAiTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "MISTRAL_AI_API_KEY", matches = ".+")
class MistralAiChatModelIT {

	private static final Logger logger = LoggerFactory.getLogger(MistralAiChatModelIT.class);

	@Autowired
	protected ChatModel chatModel;

	@Autowired
	protected StreamingChatModel streamingChatModel;

	@Value("classpath:/prompts/eval/qa-evaluator-accurate-answer.st")
	protected Resource qaEvaluatorAccurateAnswerResource;

	@Value("classpath:/prompts/eval/qa-evaluator-not-related-message.st")
	protected Resource qaEvaluatorNotRelatedResource;

	@Value("classpath:/prompts/eval/qa-evaluator-fact-based-answer.st")
	protected Resource qaEvaluatorFactBasedAnswerResource;

	@Value("classpath:/prompts/eval/user-evaluator-message.st")
	protected Resource userEvaluatorResource;

	@Value("classpath:/prompts/system-message.st")
	private Resource systemResource;

	@Test
	void roleTest() {
		UserMessage userMessage = new UserMessage(
				"Tell me about 3 famous pirates from the Golden Age of Piracy and why they did.");
		SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(this.systemResource);
		Message systemMessage = systemPromptTemplate.createMessage(Map.of("name", "Bob", "voice", "pirate"));
		// NOTE: Mistral expects the system message to be before the user message or
		// will
		// fail with 400 error.
		Prompt prompt = new Prompt(List.of(systemMessage, userMessage));
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

		var promptOptions = MistralAiChatOptions.builder()
			.model(MistralAiApi.ChatModel.SMALL.getValue())
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
		assertThat(response.getMetadata().getUsage().getTotalTokens()).isLessThan(1050).isGreaterThan(750);
	}

	@Test
	void streamFunctionCallTest() {

		UserMessage userMessage = new UserMessage("What's the weather like in Tokyo, Japan? Response in Celsius");

		List<Message> messages = new ArrayList<>(List.of(userMessage));

		var promptOptions = MistralAiChatOptions.builder()
			.model(MistralAiApi.ChatModel.SMALL.getValue())
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

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "pixtral-large-latest" })
	void multiModalityEmbeddedImage(String modelName) {
		var imageData = new ClassPathResource("/test.png");

		var userMessage = UserMessage.builder()
			.text("Explain what do you see on this picture?")
			.media(List.of(new Media(MimeTypeUtils.IMAGE_PNG, imageData)))
			.build();

		var response = this.chatModel
			.call(new Prompt(List.of(userMessage), ChatOptions.builder().model(modelName).build()));

		logger.info(response.getResult().getOutput().getText());
		assertThat(response.getResult().getOutput().getText()).containsAnyOf("bananas", "apple", "bowl", "basket",
				"fruit stand");
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "pixtral-large-latest" })
	void multiModalityImageUrl(String modelName) throws IOException {
		var userMessage = UserMessage.builder()
			.text("Explain what do you see on this picture?")
			.media(List.of(Media.builder()
				.mimeType(MimeTypeUtils.IMAGE_PNG)
				.data(URI.create("https://docs.spring.io/spring-ai/reference/_images/multimodal.test.png"))
				.build()))
			.build();

		ChatResponse response = this.chatModel
			.call(new Prompt(List.of(userMessage), ChatOptions.builder().model(modelName).build()));

		logger.info(response.getResult().getOutput().getText());
		assertThat(response.getResult().getOutput().getText()).contains("bananas", "apple");
		assertThat(response.getResult().getOutput().getText()).containsAnyOf("bowl", "basket", "fruit stand");
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
				ChatOptions.builder().model(MistralAiApi.ChatModel.PIXTRAL_LARGE.getValue()).build()));

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

	@Test
	void streamFunctionCallUsageTest() {
		UserMessage userMessage = new UserMessage(
				"What's the weather like in San Francisco, Tokyo, and Paris? Response in Celsius");

		List<Message> messages = new ArrayList<>(List.of(userMessage));

		var promptOptions = MistralAiChatOptions.builder()
			.model(MistralAiApi.ChatModel.SMALL.getValue())
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
		assertThat(chatResponse.getMetadata().getUsage().getTotalTokens()).isLessThan(1050).isGreaterThan(650);
	}

	@Test
	void chatMemory() {
		ChatMemory memory = MessageWindowChatMemory.builder().build();
		String conversationId = UUID.randomUUID().toString();

		UserMessage userMessage1 = new UserMessage("My name is James Bond");
		memory.add(conversationId, userMessage1);
		ChatResponse response1 = chatModel.call(new Prompt(memory.get(conversationId)));

		assertThat(response1).isNotNull();
		memory.add(conversationId, response1.getResult().getOutput());

		UserMessage userMessage2 = new UserMessage("What is my name?");
		memory.add(conversationId, userMessage2);
		ChatResponse response2 = chatModel.call(new Prompt(memory.get(conversationId)));

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
		ChatResponse chatResponse = chatModel.call(promptWithMemory);
		chatMemory.add(conversationId, chatResponse.getResult().getOutput());

		while (chatResponse.hasToolCalls()) {
			ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(promptWithMemory,
					chatResponse);
			chatMemory.add(conversationId, toolExecutionResult.conversationHistory()
				.get(toolExecutionResult.conversationHistory().size() - 1));
			promptWithMemory = new Prompt(chatMemory.get(conversationId), chatOptions);
			chatResponse = chatModel.call(promptWithMemory);
			chatMemory.add(conversationId, chatResponse.getResult().getOutput());
		}

		assertThat(chatResponse).isNotNull();
		assertThat(chatResponse.getResult().getOutput().getText()).contains("48");

		UserMessage newUserMessage = new UserMessage("What did I ask you earlier?");
		chatMemory.add(conversationId, newUserMessage);

		ChatResponse newResponse = chatModel.call(new Prompt(chatMemory.get(conversationId)));

		assertThat(newResponse).isNotNull();
		assertThat(newResponse.getResult().getOutput().getText()).contains("6").contains("8");
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
