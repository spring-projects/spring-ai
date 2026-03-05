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

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
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
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionRequest.ResponseFormat;
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
	private ChatModel chatModel;

	@Autowired
	private StreamingChatModel streamingChatModel;

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
			.blockOptional()
			.stream()
			.flatMap(List::stream)
			.map(ChatResponse::getResults)
			.flatMap(List::stream)
			.map(Generation::getOutput)
			.map(AssistantMessage::getText)
			.collect(Collectors.joining());

		ActorsFilmsRecord actorsFilms = outputConverter.convert(generationTextFromStream);
		logger.info(actorsFilms.toString());
		assertThat(actorsFilms.actor()).isEqualTo("Tom Hanks");
		assertThat(actorsFilms.movies()).hasSize(5);
	}

	@Test
	void functionCallTest() {

		UserMessage userMessage = new UserMessage(
				"What's the weather like in San Francisco, Tokyo, and Paris? Response in Celsius");

		List<Message> messages = new ArrayList<>(List.of(userMessage));

		var promptOptions = MistralAiChatOptions.builder()
			.model(MistralAiApi.ChatModel.MISTRAL_SMALL.getValue())
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
			.model(MistralAiApi.ChatModel.MISTRAL_SMALL.getValue())
			.toolCallbacks(List.of(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
				.description("Get the weather in location")
				.inputType(MockWeatherService.Request.class)
				.build()))
			.build();

		Flux<ChatResponse> response = this.streamingChatModel.stream(new Prompt(messages, promptOptions));

		String content = response.collectList()
			.blockOptional()
			.stream()
			.flatMap(List::stream)
			.map(ChatResponse::getResults)
			.flatMap(List::stream)
			.map(Generation::getOutput)
			.map(AssistantMessage::getText)
			.collect(Collectors.joining());
		logger.info("Response: {}", content);

		assertThat(content).containsAnyOf("10.0", "10");
	}

	@Test
	void multiModalityEmbeddedImage() {
		var imageData = new ClassPathResource("/test.png");

		var userMessage = UserMessage.builder()
			.text("Explain what do you see on this picture?")
			.media(List.of(new Media(MimeTypeUtils.IMAGE_PNG, imageData)))
			.build();

		var chatOptions = ChatOptions.builder().model(MistralAiApi.ChatModel.PIXTRAL_LARGE.getValue()).build();

		var response = this.chatModel.call(new Prompt(List.of(userMessage), chatOptions));

		logger.info(response.getResult().getOutput().getText());
		assertThat(response.getResult().getOutput().getText()).containsAnyOf("bananas", "apple", "bowl", "basket",
				"fruit stand");
	}

	@Test
	void multiModalityImageUrl() {
		var userMessage = UserMessage.builder()
			.text("Explain what do you see on this picture?")
			.media(List.of(Media.builder()
				.mimeType(MimeTypeUtils.IMAGE_PNG)
				.data(URI.create("https://docs.spring.io/spring-ai/reference/_images/multimodal.test.png"))
				.build()))
			.build();

		var chatOptions = ChatOptions.builder().model(MistralAiApi.ChatModel.PIXTRAL_LARGE.getValue()).build();

		ChatResponse response = this.chatModel.call(new Prompt(List.of(userMessage), chatOptions));

		logger.info(response.getResult().getOutput().getText());
		assertThat(response.getResult().getOutput().getText()).contains("bananas", "apple");
		assertThat(response.getResult().getOutput().getText()).containsAnyOf("bowl", "basket", "fruit stand");
	}

	@Test
	void streamingMultiModalityImageUrl() {
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
			.blockOptional()
			.stream()
			.flatMap(List::stream)
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
			.model(MistralAiApi.ChatModel.MISTRAL_SMALL.getValue())
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
	void structuredOutputWithJsonSchema() {
		// Test using ResponseFormat.jsonSchema(Class<?>) for structured output

		var promptOptions = MistralAiChatOptions.builder()
			.model(MistralAiApi.ChatModel.MISTRAL_SMALL.getValue())
			.responseFormat(ResponseFormat.jsonSchema(MovieRecommendation.class))
			.build();

		UserMessage userMessage = new UserMessage(
				"Recommend a classic science fiction movie. Provide the title, director, release year, and a brief plot summary.");

		ChatResponse response = this.chatModel.call(new Prompt(List.of(userMessage), promptOptions));

		logger.info("Response: {}", response.getResult().getOutput().getText());

		String content = response.getResult().getOutput().getText();
		assertThat(content).isNotNull();
		assertThat(content).contains("title");
		assertThat(content).contains("director");
		assertThat(content).contains("year");
		assertThat(content).contains("plotSummary");

		// Verify the response can be parsed as the expected record
		BeanOutputConverter<MovieRecommendation> outputConverter = new BeanOutputConverter<>(MovieRecommendation.class);
		MovieRecommendation movie = outputConverter.convert(content);

		assertThat(movie).isNotNull();
		assertThat(movie.title()).isNotBlank();
		assertThat(movie.director()).isNotBlank();
		assertThat(movie.year()).isGreaterThan(1900);
		assertThat(movie.plotSummary()).isNotBlank();

		logger.info("Parsed movie: {}", movie);
	}

	@Test
	void structuredOutputWithJsonSchemaFromMap() {
		// Test using ResponseFormat.jsonSchema(Map) for structured output

		Map<String, Object> schema = Map.of("type", "object", "properties",
				Map.of("city", Map.of("type", "string"), "country", Map.of("type", "string"), "population",
						Map.of("type", "integer"), "famousFor", Map.of("type", "string")),
				"required", List.of("city", "country", "population", "famousFor"), "additionalProperties", false);

		var promptOptions = MistralAiChatOptions.builder()
			.model(MistralAiApi.ChatModel.MISTRAL_SMALL.getValue())
			.responseFormat(ResponseFormat.jsonSchema(schema))
			.build();

		UserMessage userMessage = new UserMessage(
				"Tell me about Paris, France. Include the city name, country, approximate population, and what it is famous for.");

		ChatResponse response = this.chatModel.call(new Prompt(List.of(userMessage), promptOptions));

		logger.info("Response: {}", response.getResult().getOutput().getText());

		String content = response.getResult().getOutput().getText();
		assertThat(content).isNotNull();
		assertThat(content).containsIgnoringCase("Paris");
		assertThat(content).containsIgnoringCase("France");
	}

	@Test
	void chatClientEntityWithStructuredOutput() {
		// Test using ChatClient high-level API with .entity(Class) method
		// This verifies that StructuredOutputChatOptions implementation works correctly
		// with ChatClient

		ChatClient chatClient = ChatClient.builder(this.chatModel).build();

		// Advisor to verify that native structured output is being used
		AtomicBoolean nativeStructuredOutputUsed = new AtomicBoolean(false);
		CallAdvisor verifyNativeStructuredOutputAdvisor = new CallAdvisor() {
			@Override
			public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
				ChatClientResponse response = chain.nextCall(request);
				ChatOptions chatOptions = request.prompt().getOptions();

				if (chatOptions instanceof MistralAiChatOptions mistralAiChatOptions) {
					ResponseFormat responseFormat = mistralAiChatOptions.getResponseFormat();
					if (responseFormat != null && responseFormat.getType() == ResponseFormat.Type.JSON_SCHEMA) {
						nativeStructuredOutputUsed.set(true);
						logger.info("Native structured output verified - ResponseFormat type: {}",
								responseFormat.getType());
					}
				}

				return response;
			}

			@Override
			public String getName() {
				return "VerifyNativeStructuredOutputAdvisor";
			}

			@Override
			public int getOrder() {
				return 0;
			}
		};

		ActorsFilmsRecord actorsFilms = chatClient.prompt("Generate the filmography of 5 movies for Tom Hanks.")
			// forces native structured output handling via StructuredOutputChatOptions
			.advisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
			.advisors(verifyNativeStructuredOutputAdvisor)
			.call()
			.entity(ActorsFilmsRecord.class);

		logger.info("ChatClient entity result: {}", actorsFilms);

		// Verify that native structured output was used
		assertThat(nativeStructuredOutputUsed.get())
			.as("Native structured output should be used with ResponseFormat.Type.JSON_SCHEMA")
			.isTrue();

		assertThat(actorsFilms).isNotNull();
		assertThat(actorsFilms.actor()).isEqualTo("Tom Hanks");
		assertThat(actorsFilms.movies()).hasSize(5);
	}

	static class MathTools {

		@SuppressWarnings("unused")
		@Tool(description = "Multiply the two numbers")
		double multiply(double a, double b) {
			return a * b;
		}

	}

	record ActorsFilmsRecord(String actor, List<String> movies) {

	}

	record MovieRecommendation(String title, String director, int year, String plotSummary) {

	}

}
