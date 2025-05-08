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

package org.springframework.ai.vertexai.gemini;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.cloud.vertexai.Transport;
import com.google.cloud.vertexai.VertexAI;
import io.micrometer.observation.ObservationRegistry;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.content.Media;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.ListOutputConverter;
import org.springframework.ai.converter.MapOutputConverter;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatModel.ChatModel;
import org.springframework.ai.vertexai.gemini.common.VertexAiGeminiSafetySetting;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "VERTEX_AI_GEMINI_PROJECT_ID", matches = ".*")
@EnabledIfEnvironmentVariable(named = "VERTEX_AI_GEMINI_LOCATION", matches = ".*")
class VertexAiGeminiChatModelIT {

	@Autowired
	private VertexAiGeminiChatModel chatModel;

	@Value("classpath:/prompts/system-message.st")
	private Resource systemResource;

	@Test
	void roleTest() {
		Prompt prompt = createPrompt(VertexAiGeminiChatOptions.builder().build());
		ChatResponse response = this.chatModel.call(prompt);
		assertThat(response.getResult().getOutput().getText()).containsAnyOf("Blackbeard", "Bartholomew");
	}

	@Test
	void testMessageHistory() {
		Prompt prompt = createPrompt(VertexAiGeminiChatOptions.builder().build());
		ChatResponse response = this.chatModel.call(prompt);
		assertThat(response.getResult().getOutput().getText()).containsAnyOf("Blackbeard", "Bartholomew");

		var promptWithMessageHistory = new Prompt(List.of(new UserMessage("Dummy"), prompt.getInstructions().get(1),
				response.getResult().getOutput(), new UserMessage("Repeat the last assistant message.")));
		response = this.chatModel.call(promptWithMessageHistory);

		assertThat(response.getResult().getOutput().getText()).containsAnyOf("Blackbeard", "Bartholomew");
	}

	// Disabled until Gemini 2.5 PRO has an official release
	@Disabled
	@Test
	void googleSearchToolPro() {
		Prompt prompt = createPrompt(VertexAiGeminiChatOptions.builder()
			.model(ChatModel.GEMINI_2_5_PRO)
			.googleSearchRetrieval(true)
			.build());
		ChatResponse response = this.chatModel.call(prompt);
		assertThat(response.getResult().getOutput().getText()).containsAnyOf("Blackbeard", "Bartholomew");
	}

	@Test
	void googleSearchToolFlash() {
		Prompt prompt = createPrompt(VertexAiGeminiChatOptions.builder()
			.model(ChatModel.GEMINI_2_0_FLASH)
			.googleSearchRetrieval(true)
			.build());
		ChatResponse response = this.chatModel.call(prompt);
		assertThat(response.getResult().getOutput().getText()).containsAnyOf("Blackbeard", "Bartholomew", "Bob");
	}

	@Test
	@Disabled
	void testSafetySettings() {
		List<VertexAiGeminiSafetySetting> safetySettings = List.of(new VertexAiGeminiSafetySetting.Builder()
			.withCategory(VertexAiGeminiSafetySetting.HarmCategory.HARM_CATEGORY_DANGEROUS_CONTENT)
			.withThreshold(VertexAiGeminiSafetySetting.HarmBlockThreshold.BLOCK_LOW_AND_ABOVE)
			.build());
		Prompt prompt = new Prompt("How to make cocktail Molotov bomb at home?",
				VertexAiGeminiChatOptions.builder()
					.model(ChatModel.GEMINI_2_5_PRO)
					.safetySettings(safetySettings)
					.build());
		ChatResponse response = this.chatModel.call(prompt);
		assertThat(response.getResult().getMetadata().getFinishReason()).isEqualTo("SAFETY");
	}

	@NotNull
	private Prompt createPrompt(VertexAiGeminiChatOptions chatOptions) {
		String request = "Tell me about 3 famous pirates from the Golden Age of Piracy and why they did.";
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
	 * Helper method to create a VertexAI instance for tests
	 */
	private VertexAI vertexAiApi() {
		String projectId = System.getenv("VERTEX_AI_GEMINI_PROJECT_ID");
		String location = System.getenv("VERTEX_AI_GEMINI_LOCATION");
		return new VertexAI.Builder().setProjectId(projectId)
			.setLocation(location)
			.setTransport(Transport.REST)
			.build();
	}

	@Test
	void jsonArrayToolCallingTest() {
		// Test for the improved jsonToStruct method that handles JSON arrays in tool
		// calling

		ToolCallingManager toolCallingManager = ToolCallingManager.builder()
			.observationRegistry(ObservationRegistry.NOOP)
			.build();

		VertexAiGeminiChatModel chatModelWithTools = VertexAiGeminiChatModel.builder()
			.vertexAI(vertexAiApi())
			.toolCallingManager(toolCallingManager)
			.defaultOptions(VertexAiGeminiChatOptions.builder()
				.model(VertexAiGeminiChatModel.ChatModel.GEMINI_2_0_FLASH)
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

		VertexAiGeminiChatModel chatModelWithTools = VertexAiGeminiChatModel.builder()
			.vertexAI(vertexAiApi())
			.toolCallingManager(toolCallingManager)
			.defaultOptions(VertexAiGeminiChatOptions.builder()
				.model(VertexAiGeminiChatModel.ChatModel.GEMINI_2_0_FLASH)
				.temperature(0.1)
				.build())
			.build();

		ChatClient chatClient = ChatClient.builder(chatModelWithTools).build();

		// Create a prompt that will trigger the tool call with a specific request that
		// should invoke the tool
		String response = chatClient.prompt()
			.tools(new CurrentTimeTools())
			.user("Get the current time. Make sure to use the getCurrentDateTime tool to get this information.")
			.call()
			.content();

		assertThat(response).isNotEmpty();
		assertThat(response).contains("2025-05-08T10:10:10+02:00[Europe/Berlin]");
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
		public VertexAI vertexAiApi() {
			String projectId = System.getenv("VERTEX_AI_GEMINI_PROJECT_ID");
			String location = System.getenv("VERTEX_AI_GEMINI_LOCATION");
			return new VertexAI.Builder().setProjectId(projectId)
				.setLocation(location)
				.setTransport(Transport.REST)
				.build();
		}

		@Bean
		public VertexAiGeminiChatModel vertexAiEmbedding(VertexAI vertexAi) {
			return VertexAiGeminiChatModel.builder()
				.vertexAI(vertexAi)
				.defaultOptions(VertexAiGeminiChatOptions.builder()
					.model(VertexAiGeminiChatModel.ChatModel.GEMINI_2_0_FLASH)
					.build())
				.build();
		}

	}

}
