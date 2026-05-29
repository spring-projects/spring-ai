/*
 * Copyright 2023-present the original author or authors.
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
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientAttributes;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
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
import org.springframework.ai.util.JsonHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.json.BasicJsonTester;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 * @author Alexandros Pappas
 * @author Thomas Vitale
 * @author Nicolas Krier
 * @since 0.8.1
 */
@SpringBootTest(classes = MistralAiTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "MISTRAL_AI_API_KEY", matches = ".+")
class MistralAiChatModelIT {

	private static final JsonHelper jsonHelper = new JsonHelper();

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
		// NOTE: Mistral expects the system message to be before the user message or will
		// fail with 400 error.
		Prompt prompt = new Prompt(List.of(systemMessage, userMessage));
		var results = this.chatModel.call(prompt).getResults();
		assertThat(results).hasSize(1);
		var output = results.get(0).getOutput();
		assertThat(output.getText()).contains("Blackbeard");
		var outputMetadata = output.getMetadata();
		assertThat(outputMetadata).doesNotContainKey(MistralAiChatModel.REFERENCE_CONTENT_METADATA);
		assertThat(outputMetadata).doesNotContainKey(MistralAiChatModel.REFERENCE_THINKING_CONTENT_METADATA);
		assertThat(outputMetadata).doesNotContainKey(MistralAiChatModel.THINKING_CONTENT_METADATA);
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

	/**
	 * This integration test verifies the correct handling of citations and references in
	 * responses generated by Mistral Large model, as described in the
	 * <a href="https://docs.mistral.ai/capabilities/citations">Mistral AI Citations &
	 * References Documentation</a>.
	 */
	@Test
	void referenceCall() {
		var systemMessage = new SystemMessage(
				"Answer the user by providing references to the source of the information using the tool available.");
		var userMessage = new UserMessage("Who won the Nobel Prize in 2024?");
		var functionName = "get_information";
		var toolCallId = "3DHY8663m";
		var toolCall = new AssistantMessage.ToolCall(toolCallId, "function", functionName, "{}");
		var assistantMessage = AssistantMessage.builder().toolCalls(List.of(toolCall)).build();
		var referencesJsonString = extractReferencesAsJsonString();
		var toolResponse = new ToolResponseMessage.ToolResponse(toolCallId, functionName, referencesJsonString);
		var toolResponseMessage = ToolResponseMessage.builder().responses(List.of(toolResponse)).build();
		// @formatter:off
		var parameters = Map.of(
				"type", "object",
				"properties", Map.of(),
				"additionalProperties", false
		);
		// @formatter:on
		var function = new MistralAiApi.FunctionTool.Function("Get information from external source.", functionName,
				parameters);
		var functionTool = new MistralAiApi.FunctionTool(MistralAiApi.FunctionTool.Type.FUNCTION, function);
		var mistralAiChatOptions = MistralAiChatOptions.builder()
			.model(MistralAiApi.ChatModel.MISTRAL_LARGE.getValue())
			.tools(List.of(functionTool))
			.toolChoice(MistralAiApi.ChatCompletionRequest.ToolChoice.AUTO)
			.build();
		var prompt = Prompt.builder()
			.chatOptions(mistralAiChatOptions)
			.messages(systemMessage, userMessage, assistantMessage, toolResponseMessage)
			.build();

		var generation = this.chatModel.call(prompt).getResult();
		assertThat(generation).isNotNull();
		var output = generation.getOutput();
		assertThat(output.getText()).contains("Nihon Hidankyo");
		assertThat(output.getMetadata()).containsEntry(MistralAiChatModel.REFERENCE_CONTENT_METADATA, List.of(0));
	}

	@ThinkingModelSource
	@ParameterizedTest
	void thinkingCall(MistralAiApi.ChatModel chatModel) {
		var prompt = createThinkingPrompt(chatModel);

		var generation = this.chatModel.call(prompt).getResult();
		assertThat(generation).isNotNull();
		var output = generation.getOutput();
		var outputText = output.getText();
		assertThat(outputText).contains("Jupiter");
		var outputMetadata = output.getMetadata();
		var thinkingContent = outputMetadata.get(MistralAiChatModel.THINKING_CONTENT_METADATA);
		assertThat(thinkingContent).asString().isNotEmpty();
		logger.info("Thinking content: {}", thinkingContent);
		assertThat(outputMetadata).doesNotContainKey(MistralAiChatModel.REFERENCE_CONTENT_METADATA);
		assertThat(outputMetadata).doesNotContainKey(MistralAiChatModel.REFERENCE_THINKING_CONTENT_METADATA);
	}

	@ThinkingModelSource
	@ParameterizedTest
	void thinkingStream(MistralAiApi.ChatModel chatModel) {
		var prompt = createThinkingPrompt(chatModel);

		var assistantMessages = this.chatModel.stream(prompt)
			.collectList()
			.blockOptional()
			.stream()
			.flatMap(List::stream)
			.map(ChatResponse::getResults)
			.flatMap(List::stream)
			.map(Generation::getOutput)
			.toList();

		var content = assistantMessages.stream().map(AssistantMessage::getText).collect(Collectors.joining());
		logger.info("Content: {}", content);
		assertThat(content).contains("Jupiter");

		var thinkingContent = assistantMessages.stream()
			.map(AssistantMessage::getMetadata)
			.map(metadata -> metadata.get(MistralAiChatModel.THINKING_CONTENT_METADATA))
			.filter(Objects::nonNull)
			.map(String.class::cast)
			.collect(Collectors.joining());
		logger.info("Thinking content: {}", thinkingContent);
		assertThat(thinkingContent).isNotEmpty();

		assertThat(hasMetadata(assistantMessages, MistralAiChatModel.REFERENCE_CONTENT_METADATA)).isFalse();
		assertThat(hasMetadata(assistantMessages, MistralAiChatModel.REFERENCE_THINKING_CONTENT_METADATA)).isFalse();
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
		logger.info(actorsFilms.toString());
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
		assertThat(response.getMetadata().getUsage().getTotalTokens()).isLessThan(1050).isGreaterThan(500);
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

		var chatOptions = ChatOptions.builder().model(MistralAiApi.ChatModel.MISTRAL_LARGE.getValue()).build();

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

		var chatOptions = ChatOptions.builder().model(MistralAiApi.ChatModel.MISTRAL_LARGE.getValue()).build();

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
				ChatOptions.builder().model(MistralAiApi.ChatModel.MISTRAL_LARGE.getValue()).build()));

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

		// Advisor to verify that native structured output is being used.
		// The schema is passed via context to ChatModelCallAdvisor which applies it
		// to a new ChatClientRequest - so we verify the context attributes here,
		// not the prompt options (which are only modified inside ChatModelCallAdvisor).
		var expectedOutputSchemaMap = new BeanOutputConverter<>(ActorsFilmsRecord.class).getJsonSchemaMap();
		AtomicBoolean nativeStructuredOutputUsed = new AtomicBoolean(false);
		CallAdvisor verifyNativeStructuredOutputAdvisor = new CallAdvisor() {
			@Override
			public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
				var nativeFlag = request.context().get(ChatClientAttributes.STRUCTURED_OUTPUT_NATIVE.getKey());
				var schemaString = (String) request.context()
					.get(ChatClientAttributes.STRUCTURED_OUTPUT_SCHEMA.getKey());

				if (Boolean.TRUE.equals(nativeFlag) && schemaString != null) {
					var actualSchemaMap = jsonHelper.fromJsonToMap(schemaString);
					if (expectedOutputSchemaMap.equals(actualSchemaMap)) {
						nativeStructuredOutputUsed.set(true);
					}
				}
				return chain.nextCall(request);
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

	private static boolean hasMetadata(List<AssistantMessage> assistantMessages, String metadataKey) {
		return assistantMessages.stream()
			.map(AssistantMessage::getMetadata)
			.anyMatch(metadata -> metadata.containsKey(metadataKey));
	}

	private static Prompt createThinkingPrompt(MistralAiApi.ChatModel chatModel) {
		var promptMode = ThinkingModelUtils.providePromptMode(chatModel);
		var reasoningEffort = ThinkingModelUtils.provideReasoningEffort(chatModel);
		var model = ThinkingModelUtils.provideChatModelValue(chatModel);
		var chatOptions = MistralAiChatOptions.builder()
			.model(model)
			.promptMode(promptMode)
			.reasoningEffort(reasoningEffort)
			.build();
		var systemMessage = new SystemMessage("You are a helpful assistant providing accurate short answers.");
		var userMessage = new UserMessage(
				"What is the first planet of the solar system based on the mass in descending order?");

		return Prompt.builder().messages(systemMessage, userMessage).chatOptions(chatOptions).build();
	}

	private static String extractReferencesAsJsonString() {
		return new BasicJsonTester(MistralAiChatModelIT.class).from(new ClassPathResource("references.json")).getJson();
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
