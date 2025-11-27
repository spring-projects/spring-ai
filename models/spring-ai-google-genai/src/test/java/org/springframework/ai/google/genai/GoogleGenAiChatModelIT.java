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

package org.springframework.ai.google.genai;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.genai.Client;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.content.Media;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.ListOutputConverter;
import org.springframework.ai.converter.MapOutputConverter;
import org.springframework.ai.google.genai.GoogleGenAiChatModel.ChatModel;
import org.springframework.ai.google.genai.common.GoogleGenAiSafetySetting;
import org.springframework.ai.google.genai.common.GoogleGenAiThinkingLevel;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.lang.NonNull;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "GOOGLE_CLOUD_PROJECT", matches = ".*")
@EnabledIfEnvironmentVariable(named = "GOOGLE_CLOUD_LOCATION", matches = ".*")
class GoogleGenAiChatModelIT {

	private static final Logger logger = LoggerFactory.getLogger(GoogleGenAiChatModelIT.class);

	@Autowired
	private GoogleGenAiChatModel chatModel;

	@Value("classpath:/prompts/system-message.st")
	private Resource systemResource;

	@Test
	void roleTest() {
		Prompt prompt = createPrompt(GoogleGenAiChatOptions.builder().build());
		ChatResponse response = this.chatModel.call(prompt);
		assertThat(response.getResult().getOutput().getText()).containsAnyOf("Blackbeard", "Bartholomew");
	}

	@Test
	void testMessageHistory() {
		Prompt prompt = createPrompt(GoogleGenAiChatOptions.builder().build());
		ChatResponse response = this.chatModel.call(prompt);
		assertThat(response.getResult().getOutput().getText()).containsAnyOf("Blackbeard", "Bartholomew");

		var promptWithMessageHistory = new Prompt(List.of(new UserMessage("Dummy"), prompt.getInstructions().get(1),
				response.getResult().getOutput(), new UserMessage("Repeat the last assistant message.")));
		response = this.chatModel.call(promptWithMessageHistory);

		assertThat(response.getResult().getOutput().getText()).containsAnyOf("Blackbeard", "Bartholomew");
	}

	@Test
	void googleSearchToolPro() {
		Prompt prompt = createPrompt(
				GoogleGenAiChatOptions.builder().model(ChatModel.GEMINI_2_5_PRO).googleSearchRetrieval(true).build());
		ChatResponse response = this.chatModel.call(prompt);
		assertThat(response.getResult().getOutput().getText()).containsAnyOf("Blackbeard", "Bartholomew", "Calico Jack",
				"Bob", "Anne Bonny");
	}

	@Test
	void googleSearchToolFlash() {
		Prompt prompt = createPrompt(
				GoogleGenAiChatOptions.builder().model(ChatModel.GEMINI_2_0_FLASH).googleSearchRetrieval(true).build());
		ChatResponse response = this.chatModel.call(prompt);
		assertThat(response.getResult().getOutput().getText()).containsAnyOf("Blackbeard", "Bartholomew", "Bob");
	}

	@Test
	@Disabled
	void testSafetySettings() {
		List<GoogleGenAiSafetySetting> safetySettings = List.of(new GoogleGenAiSafetySetting.Builder()
			.withCategory(GoogleGenAiSafetySetting.HarmCategory.HARM_CATEGORY_DANGEROUS_CONTENT)
			.withThreshold(GoogleGenAiSafetySetting.HarmBlockThreshold.BLOCK_LOW_AND_ABOVE)
			.build());
		Prompt prompt = new Prompt("How to make cocktail Molotov bomb at home?",
				GoogleGenAiChatOptions.builder()
					.model(ChatModel.GEMINI_2_5_PRO)
					.safetySettings(safetySettings)
					.build());
		ChatResponse response = this.chatModel.call(prompt);
		assertThat(response.getResult().getMetadata().getFinishReason()).isEqualTo("SAFETY");
	}

	@NonNull
	private Prompt createPrompt(GoogleGenAiChatOptions chatOptions) {
		String request = "Name 3 famous pirates from the Golden Age of Piracy and tell me what they did.";
		String name = "Bob";
		String voice = "pirate";
		UserMessage userMessage = new UserMessage(request);
		SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(this.systemResource);
		Message systemMessage = systemPromptTemplate.createMessage(Map.of("name", name, "voice", voice));
		Prompt prompt = new Prompt(List.of(userMessage, systemMessage), chatOptions);
		return prompt;
	}

	@Test
	void listOutputConverter() {
		DefaultConversionService conversionService = new DefaultConversionService();
		ListOutputConverter converter = new ListOutputConverter(conversionService);

		String format = converter.getFormat();
		String template = """
				List five {subject}
				{format}
				""";
		PromptTemplate promptTemplate = PromptTemplate.builder()
			.template(template)
			.variables(Map.of("subject", "ice cream flavors.", "format", format))
			.build();
		Prompt prompt = new Prompt(promptTemplate.createMessage());
		Generation generation = this.chatModel.call(prompt).getResult();

		List<String> list = converter.convert(generation.getOutput().getText());
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

		BeanOutputConverter<ActorsFilmsRecord> outputConvert = new BeanOutputConverter<>(ActorsFilmsRecord.class);

		String format = outputConvert.getFormat();
		String template = """
				Generate the filmography of 5 movies for Tom Hanks.
				Remove the ```json outer brackets.
				{format}
				""";
		PromptTemplate promptTemplate = PromptTemplate.builder()
			.template(template)
			.variables(Map.of("format", format))
			.build();
		Prompt prompt = new Prompt(promptTemplate.createMessage());
		Generation generation = this.chatModel.call(prompt).getResult();

		ActorsFilmsRecord actorsFilms = outputConvert.convert(generation.getOutput().getText());
		assertThat(actorsFilms.actor()).isEqualTo("Tom Hanks");
		assertThat(actorsFilms.movies()).hasSize(5);
	}

	@Test
	void beanOutputConverterRecordsWithResponseSchema() {
		// Use the Google GenAI API to set the response schema
		beanOutputConverterRecordsWithStructuredOutput(jsonSchema -> GoogleGenAiChatOptions.builder()
			.responseSchema(jsonSchema)
			.responseMimeType("application/json")
			.build());
	}

	@Test
	void beanOutputConverterRecordsWithOutputSchema() {
		// Use the unified Spring AI API (StructuredOutputChatOptions) to set the output
		// schema.
		beanOutputConverterRecordsWithStructuredOutput(
				jsonSchema -> GoogleGenAiChatOptions.builder().outputSchema(jsonSchema).build());
	}

	private void beanOutputConverterRecordsWithStructuredOutput(Function<String, ChatOptions> chatOptionsProvider) {

		BeanOutputConverter<ActorsFilmsRecord> outputConvert = new BeanOutputConverter<>(ActorsFilmsRecord.class);

		String schema = outputConvert.getJsonSchema();

		Prompt prompt = Prompt.builder()
			.content("Generate the filmography of 5 movies for Tom Hanks.")
			.chatOptions(chatOptionsProvider.apply(schema))
			.build();

		Generation generation = this.chatModel.call(prompt).getResult();

		ActorsFilmsRecord actorsFilms = outputConvert.convert(generation.getOutput().getText());
		assertThat(actorsFilms.actor()).isEqualTo("Tom Hanks");
		assertThat(actorsFilms.movies()).hasSize(5);
	}

	@Test
	void chatClientBeanOutputConverterRecords() {

		var chatClient = ChatClient.builder(this.chatModel).build();

		ActorsFilmsRecord actorsFilms = chatClient.prompt("Generate the filmography of 5 movies for Tom Hanks.")
			.call()
			.entity(ActorsFilmsRecord.class);

		assertThat(actorsFilms.actor()).isEqualTo("Tom Hanks");
		assertThat(actorsFilms.movies()).hasSize(5);
	}

	@Test
	void chatClientBeanOutputConverterRecordsNative() {

		var chatClient = ChatClient.builder(this.chatModel).build();

		ActorsFilmsRecord actorsFilms = chatClient.prompt("Generate the filmography of 5 movies for Tom Hanks.")
			// forces native structured output handling
			.advisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
			.call()
			.entity(ActorsFilmsRecord.class);

		assertThat(actorsFilms.actor()).isEqualTo("Tom Hanks");
		assertThat(actorsFilms.movies()).hasSize(5);
	}

	@Test
	void listOutputConverterBean() {

		// @formatter:off
		List<ActorsFilmsRecord> actorsFilms = ChatClient.create(this.chatModel).prompt()
				.user("Generate the filmography of 5 movies for Tom Hanks and Bill Murray.")
				.call()
				.entity(new ParameterizedTypeReference<>() {
				});
		// @formatter:on

		assertThat(actorsFilms).hasSize(2);
	}

	@Test
	void listOutputConverterBeanNative() {

		// @formatter:off
		List<ActorsFilmsRecord> actorsFilms = ChatClient.create(this.chatModel).prompt()
				.advisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
				.user("Generate the filmography of 5 movies for Tom Hanks and Bill Murray.")
				.call()
				.entity(new ParameterizedTypeReference<>() {
				});
		// @formatter:on

		assertThat(actorsFilms).hasSize(2);
	}

	@Test
	void textStream() {

		String generationTextFromStream = this.chatModel
			.stream(new Prompt("Explain Bulgaria? Answer in 10 paragraphs."))
			.collectList()
			.block()
			.stream()
			.map(ChatResponse::getResults)
			.flatMap(List::stream)
			.map(Generation::getOutput)
			.map(AssistantMessage::getText)
			.collect(Collectors.joining());

		// logger.info("{}", actorsFilms);
		assertThat(generationTextFromStream).isNotEmpty();
	}

	@Test
	void beanStreamOutputConverterRecords() {

		BeanOutputConverter<ActorsFilmsRecord> outputConverter = new BeanOutputConverter<>(ActorsFilmsRecord.class);

		String format = outputConverter.getFormat();
		String template = """
				Generate the filmography of 5 movies for Tom Hanks.
				Remove the ```json outer brackets.
				{format}
				""";
		PromptTemplate promptTemplate = PromptTemplate.builder()
			.template(template)
			.variables(Map.of("format", format))
			.build();
		Prompt prompt = new Prompt(promptTemplate.createMessage());

		String generationTextFromStream = this.chatModel.stream(prompt)
			.collectList()
			.block()
			.stream()
			.map(ChatResponse::getResults)
			.flatMap(List::stream)
			.map(Generation::getOutput)
			.map(AssistantMessage::getText)
			.collect(Collectors.joining());

		ActorsFilmsRecord actorsFilms = outputConverter.convert(generationTextFromStream);
		// logger.info("{}", actorsFilms);
		assertThat(actorsFilms.actor()).isEqualTo("Tom Hanks");
		assertThat(actorsFilms.movies()).hasSize(5);
	}

	@Test
	void multiModalityTest() throws IOException {

		var data = new ClassPathResource("/vertex.test.png");

		var userMessage = UserMessage.builder()
			.text("Explain what do you see o this picture?")
			.media(List.of(new Media(MimeTypeUtils.IMAGE_PNG, data)))
			.build();

		var response = this.chatModel.call(new Prompt(List.of(userMessage)));

		// Response should contain something like:
		// I see a bunch of bananas in a golden basket. The bananas are ripe and yellow.
		// There are also some red apples in the basket. The basket is sitting on a
		// table.
		// The background is a blurred light blue color.'
		assertThat(response.getResult().getOutput().getText()).satisfies(content -> {
			long count = Stream.of("bananas", "apple", "basket").filter(content::contains).count();
			assertThat(count).isGreaterThanOrEqualTo(2);
		});

		// Error with image from URL:
		// com.google.api.gax.rpc.InvalidArgumentException:
		// io.grpc.StatusRuntimeException: INVALID_ARGUMENT: Only GCS URIs are supported
		// in file_uri and please make sure that the path is a valid GCS path.

		// String imageUrl =
		// "https://storage.googleapis.com/github-repo/img/gemini/multimodality_usecases_overview/banana-apple.jpg";

		// userMessage = new UserMessage("Explain what do you see o this picture?",
		// List.of(new Media(MimeTypeDetector.getMimeType(imageUrl), imageUrl)));
		// response = client.call(new Prompt(List.of(userMessage)));

		// assertThat(response.getResult().getOutput().getContent())..containsAnyOf("bananas",
		// "apple", "bowl", "basket", "fruit stand");

		// https://github.com/GoogleCloudPlatform/generative-ai/blob/main/gemini/use-cases/intro_multimodal_use_cases.ipynb
	}

	@Test
	void multiModalityPdfTest() throws IOException {

		var pdfData = new ClassPathResource("/spring-ai-reference-overview.pdf");

		var userMessage = UserMessage.builder()
			.text("You are a very professional document summarization specialist. Please summarize the given document.")
			.media(List.of(new Media(new MimeType("application", "pdf"), pdfData)))
			.build();

		var response = this.chatModel.call(new Prompt(List.of(userMessage)));

		assertThat(response.getResult().getOutput().getText()).containsAnyOf("Spring AI", "portable API");
	}

	/**
	 * Helper method to create a Client instance for tests.
	 */
	private Client genAiClient() {
		String projectId = System.getenv("GOOGLE_CLOUD_PROJECT");
		String location = System.getenv("GOOGLE_CLOUD_LOCATION");
		return Client.builder().project(projectId).location(location).vertexAI(true).build();
	}

	/**
	 * Helper method to create a Client with global endpoint for Gemini 3 Pro Preview.
	 * Gemini 3 Pro Preview is only available on global endpoints.
	 */
	private Client genAiClientGlobal() {
		String projectId = System.getenv("GOOGLE_CLOUD_PROJECT");
		return Client.builder().project(projectId).location("global").vertexAI(true).build();
	}

	@Test
	void jsonArrayToolCallingTest() {
		// Test for the improved jsonToStruct method that handles JSON arrays in tool
		// calling

		ToolCallingManager toolCallingManager = ToolCallingManager.builder()
			.observationRegistry(ObservationRegistry.NOOP)
			.build();

		GoogleGenAiChatModel chatModelWithTools = GoogleGenAiChatModel.builder()
			.genAiClient(genAiClient())
			.toolCallingManager(toolCallingManager)
			.defaultOptions(GoogleGenAiChatOptions.builder()
				.model(GoogleGenAiChatModel.ChatModel.GEMINI_2_0_FLASH)
				.temperature(0.1)
				.build())
			.build();

		ChatClient chatClient = ChatClient.builder(chatModelWithTools).build();

		// Create a prompt that will trigger the tool call with a specific request that
		// should invoke the tool
		String response = chatClient.prompt()
			.tools(new ScientistTools())
			.user("List 3 famous scientists and their discoveries. Make sure to use the tool to get this information.")
			.call()
			.content();

		assertThat(response).isNotEmpty();

		assertThat(response).satisfiesAnyOf(content -> assertThat(content).contains("Einstein"),
				content -> assertThat(content).contains("Newton"), content -> assertThat(content).contains("Curie"));

	}

	@Test
	void jsonTextToolCallingTest() {
		// Test for the improved jsonToStruct method that handles JSON texts in tool
		// calling

		ToolCallingManager toolCallingManager = ToolCallingManager.builder()
			.observationRegistry(ObservationRegistry.NOOP)
			.build();

		GoogleGenAiChatModel chatModelWithTools = GoogleGenAiChatModel.builder()
			.genAiClient(genAiClient())
			.toolCallingManager(toolCallingManager)
			.defaultOptions(GoogleGenAiChatOptions.builder()
				.model(GoogleGenAiChatModel.ChatModel.GEMINI_2_0_FLASH)
				.temperature(0.1)
				.build())
			.build();

		ChatClient chatClient = ChatClient.builder(chatModelWithTools).build();

		// Create a prompt that will trigger the tool call with a specific request that
		// should invoke the tool
		String response = chatClient.prompt()
			.tools(new CurrentTimeTools())
			.user("Get the current time in the users timezone. Make sure to use the getCurrentDateTime tool to get this information.")
			.call()
			.content();

		assertThat(response).isNotEmpty();
		assertThat(response).contains("2025-05-08T10:10:10+02:00");
	}

	@Test
	void testThinkingBudgetGeminiProAutomaticDecisionByModel() {
		GoogleGenAiChatModel chatModelWithThinkingBudget = GoogleGenAiChatModel.builder()
			.genAiClient(genAiClient())
			.defaultOptions(GoogleGenAiChatOptions.builder().model(ChatModel.GEMINI_2_5_PRO).temperature(0.1).build())
			.build();

		ChatClient chatClient = ChatClient.builder(chatModelWithThinkingBudget).build();

		// Create a prompt that will trigger the tool call with a specific request that
		// should invoke the tool
		long start = System.currentTimeMillis();
		String response = chatClient.prompt()
			.user("Explain to me briefly how I can start a SpringAI project")
			.call()
			.content();

		assertThat(response).isNotEmpty();
		logger.info("Response: {} in {} ms", response, System.currentTimeMillis() - start);
	}

	@Test
	void testThinkingBudgetGeminiProMinBudget() {
		GoogleGenAiChatModel chatModelWithThinkingBudget = GoogleGenAiChatModel.builder()
			.genAiClient(genAiClient())
			.defaultOptions(GoogleGenAiChatOptions.builder()
				.model(ChatModel.GEMINI_2_5_PRO)
				.temperature(0.1)
				.thinkingBudget(128)
				.build())
			.build();

		ChatClient chatClient = ChatClient.builder(chatModelWithThinkingBudget).build();

		// Create a prompt that will trigger the tool call with a specific request that
		// should invoke the tool
		long start = System.currentTimeMillis();
		String response = chatClient.prompt()
			.user("Explain to me briefly how I can start a SpringAI project")
			.call()
			.content();

		assertThat(response).isNotEmpty();
		logger.info("Response: {} in {} ms", response, System.currentTimeMillis() - start);
	}

	@Test
	void testThinkingBudgetGeminiFlashDefaultBudget() {
		GoogleGenAiChatModel chatModelWithThinkingBudget = GoogleGenAiChatModel.builder()
			.genAiClient(genAiClient())
			.defaultOptions(GoogleGenAiChatOptions.builder()
				.model(ChatModel.GEMINI_2_5_FLASH)
				.temperature(0.1)
				.thinkingBudget(8192)
				.build())
			.build();

		ChatClient chatClient = ChatClient.builder(chatModelWithThinkingBudget).build();

		// Create a prompt that will trigger the tool call with a specific request that
		// should invoke the tool
		long start = System.currentTimeMillis();
		String response = chatClient.prompt()
			.user("Explain to me briefly how I can start a SpringAI project")
			.call()
			.content();

		assertThat(response).isNotEmpty();
		logger.info("Response: {} in {} ms", response, System.currentTimeMillis() - start);
	}

	@Test
	void testThinkingBudgetGeminiFlashThinkingTurnedOff() {
		GoogleGenAiChatModel chatModelWithThinkingBudget = GoogleGenAiChatModel.builder()
			.genAiClient(genAiClient())
			.defaultOptions(GoogleGenAiChatOptions.builder()
				.model(ChatModel.GEMINI_2_5_FLASH)
				.temperature(0.1)
				.thinkingBudget(0)
				.build())
			.build();

		ChatClient chatClient = ChatClient.builder(chatModelWithThinkingBudget).build();

		// Create a prompt that will trigger the tool call with a specific request that
		// should invoke the tool
		long start = System.currentTimeMillis();
		String response = chatClient.prompt()
			.user("Explain to me briefly how I can start a SpringAI project")
			.call()
			.content();

		assertThat(response).isNotEmpty();
		logger.info("Response: {} in {} ms", response, System.currentTimeMillis() - start);
	}

	/**
	 * Tests that using thinkingLevel with models that don't support it results in an API
	 * error. The {@code thinkingLevel} option is only supported by Gemini 3 Pro models.
	 * For Gemini 2.5 series and earlier models, use {@code thinkingBudget} instead.
	 * @see <a href="https://ai.google.dev/gemini-api/docs/thinking">Google GenAI Thinking
	 * documentation</a>
	 */
	@Test
	void testThinkingLevelUnsupportedModels() {
		GoogleGenAiChatModel chatModelWithThinkingLevel = GoogleGenAiChatModel.builder()
			.genAiClient(genAiClient())
			.defaultOptions(GoogleGenAiChatOptions.builder()
				.model(ChatModel.GEMINI_2_5_FLASH)
				.temperature(0.1)
				.thinkingLevel(GoogleGenAiThinkingLevel.LOW)
				.build())
			.build();

		ChatClient chatClient = ChatClient.builder(chatModelWithThinkingLevel).build();

		// thinkingLevel is not supported on Gemini 2.5 models - use thinkingBudget
		// instead
		assertThatThrownBy(() -> chatClient.prompt().user("What is 2+2? Give a brief answer.").call().content())
			.isInstanceOf(RuntimeException.class)
			.hasMessageContaining("Failed to generate content");
	}

	@Test
	void testThinkingLevelLow() {
		GoogleGenAiChatModel chatModelWithThinkingLevel = GoogleGenAiChatModel.builder()
			.genAiClient(genAiClientGlobal())
			.defaultOptions(GoogleGenAiChatOptions.builder()
				.model(ChatModel.GEMINI_3_PRO_PREVIEW)
				.thinkingLevel(GoogleGenAiThinkingLevel.LOW)
				.build())
			.build();

		ChatClient chatClient = ChatClient.builder(chatModelWithThinkingLevel).build();

		long start = System.currentTimeMillis();
		String response = chatClient.prompt().user("What is 2+2? Give a brief answer.").call().content();

		assertThat(response).isNotEmpty();
		logger.info("ThinkingLevel=LOW Response: {} in {} ms", response, System.currentTimeMillis() - start);
	}

	@Test
	void testThinkingLevelHigh() {
		GoogleGenAiChatModel chatModelWithThinkingLevel = GoogleGenAiChatModel.builder()
			.genAiClient(genAiClientGlobal())
			.defaultOptions(GoogleGenAiChatOptions.builder()
				.model(ChatModel.GEMINI_3_PRO_PREVIEW)
				.temperature(0.1)
				.thinkingLevel(GoogleGenAiThinkingLevel.HIGH)
				.build())
			.build();

		ChatClient chatClient = ChatClient.builder(chatModelWithThinkingLevel).build();

		long start = System.currentTimeMillis();
		String response = chatClient.prompt()
			.user("Explain the theory of relativity in simple terms.")
			.call()
			.content();

		assertThat(response).isNotEmpty();
		logger.info("ThinkingLevel=HIGH Response: {} in {} ms", response, System.currentTimeMillis() - start);
	}

	/**
	 * Tests that combining thinkingLevel and thinkingBudget in the same request results
	 * in an API error. According to Google's API documentation, these options are
	 * mutually exclusive:
	 * <ul>
	 * <li>Use {@code thinkingLevel} (LOW, HIGH) for Gemini 3 Pro models</li>
	 * <li>Use {@code thinkingBudget} (token count) for Gemini 2.5 series models</li>
	 * </ul>
	 * Specifying both in the same request will return a 400 error from the API.
	 * @see <a href="https://ai.google.dev/gemini-api/docs/thinking">Google GenAI Thinking
	 * documentation</a>
	 */
	@Test
	void testThinkingLevelWithBudgetCombinedExpectsError() {
		GoogleGenAiChatModel chatModelWithThinkingLevel = GoogleGenAiChatModel.builder()
			.genAiClient(genAiClientGlobal())
			.defaultOptions(GoogleGenAiChatOptions.builder()
				.model(ChatModel.GEMINI_3_PRO_PREVIEW)
				.temperature(0.1)
				.thinkingBudget(4096)
				.thinkingLevel(GoogleGenAiThinkingLevel.HIGH)
				.includeThoughts(true)
				.build())
			.build();

		ChatClient chatClient = ChatClient.builder(chatModelWithThinkingLevel).build();

		// thinkingLevel and thinkingBudget are mutually exclusive - API returns 400 error
		assertThatThrownBy(() -> chatClient.prompt().user("What is 2+2? Give a brief answer.").call().content())
			.isInstanceOf(RuntimeException.class)
			.hasMessageContaining("Failed to generate content");
	}

	/**
	 * Tool class that returns a JSON array to test the jsonToStruct method's ability to
	 * handle JSON arrays. This specifically tests the PR changes that improve the
	 * jsonToStruct method to handle JSON arrays in addition to JSON objects.
	 */
	public static class ScientistTools {

		@Tool(description = "Get information about famous scientists and their discoveries")
		public List<Map<String, String>> getScientists() {
			// Return a JSON array with scientist information
			return List.of(Map.of("name", "Albert Einstein", "discovery", "Theory of Relativity"),
					Map.of("name", "Isaac Newton", "discovery", "Laws of Motion"),
					Map.of("name", "Marie Curie", "discovery", "Radioactivity"));
		}

	}

	/**
	 * Tool class that returns a String to test the jsonToStruct method's ability to
	 * handle JSON texts. This specifically tests the PR changes that improve the
	 * jsonToStruct method to handle JSON texts in addition to JSON objects and JSON
	 * arrays.
	 */
	public static class CurrentTimeTools {

		@Tool(description = "Get the current date and time in the user's timezone")
		String getCurrentDateTime() {
			return "2025-05-08T10:10:10+02:00[Europe/Berlin]";
		}

	}

	record ActorsFilmsRecord(String actor, List<String> movies) {

	}

	@SpringBootConfiguration
	public static class TestConfiguration {

		@Bean
		public Client genAiClient() {
			String projectId = System.getenv("GOOGLE_CLOUD_PROJECT");
			String location = System.getenv("GOOGLE_CLOUD_LOCATION");
			// TODO: Update this to use the proper GenAI client initialization
			// The new GenAI SDK may have different initialization requirements
			return Client.builder().project(projectId).location(location).vertexAI(true).build();
		}

		@Bean
		public GoogleGenAiChatModel vertexAiEmbedding(Client genAiClient) {
			return GoogleGenAiChatModel.builder()
				.genAiClient(genAiClient)
				.defaultOptions(
						GoogleGenAiChatOptions.builder().model(GoogleGenAiChatModel.ChatModel.GEMINI_2_0_FLASH).build())
				.build();
		}

	}

}
