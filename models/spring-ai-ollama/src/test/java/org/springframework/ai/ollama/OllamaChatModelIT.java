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

package org.springframework.ai.ollama;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonProperty;
import net.javacrumbs.jsonunit.assertj.JsonAssertions;
import org.junit.jupiter.api.Test;

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
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.ListOutputConverter;
import org.springframework.ai.converter.MapOutputConverter;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.api.OllamaModel;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.ollama.management.OllamaModelManager;
import org.springframework.ai.ollama.management.PullModelStrategy;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.util.ResourceUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.support.DefaultConversionService;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OllamaChatModelIT extends BaseOllamaIT {

	private static final String MODEL = OllamaModel.QWEN_2_5_3B.getName();

	private static final String ADDITIONAL_MODEL = "tinyllama";

	@Autowired
	private OllamaChatModel chatModel;

	@Autowired
	private OllamaApi ollamaApi;

	@Test
	void autoPullModelTest() {
		var modelManager = new OllamaModelManager(this.ollamaApi);
		assertThat(modelManager.isModelAvailable(ADDITIONAL_MODEL)).isTrue();

		String joke = ChatClient.create(this.chatModel)
			.prompt("Tell me a joke")
			.options(OllamaChatOptions.builder().model(ADDITIONAL_MODEL).build())
			.call()
			.content();

		assertThat(joke).isNotEmpty();

		modelManager.deleteModel(ADDITIONAL_MODEL);
	}

	@Test
	void roleTest() {
		Message systemMessage = new SystemPromptTemplate("""
				You are a helpful AI assistant. Your name is {name}.
				You are an AI assistant that helps people find information.
				Your name is {name}
				You should reply to the user's request with your name and also in the style of a {voice}.
				""").createMessage(Map.of("name", "Bob", "voice", "pirate"));

		UserMessage userMessage = new UserMessage("Tell me about 5 famous pirates from the Golden Age of Piracy.");

		// portable/generic options
		var portableOptions = ChatOptions.builder().temperature(0.7).build();

		Prompt prompt = new Prompt(List.of(systemMessage, userMessage), portableOptions);

		ChatResponse response = this.chatModel.call(prompt);
		verifyMostFamousPiratePresence(response);

		// ollama specific options
		var ollamaOptions = OllamaChatOptions.builder().lowVRAM(true).build();

		response = this.chatModel.call(new Prompt(List.of(systemMessage, userMessage), ollamaOptions));
		verifyMostFamousPiratePresence(response);
	}

	@Test
	void testMessageHistory() {
		Message systemMessage = new SystemPromptTemplate("""
				You are a helpful AI assistant. Your name is {name}.
				You are an AI assistant that helps people find information.
				Your name is {name}
				You should reply to the user's request with your name and also in the style of a {voice}.
				""").createMessage(Map.of("name", "Bob", "voice", "pirate"));

		UserMessage userMessage = new UserMessage(
				"Tell me about 5 famous pirates from the Golden Age of Piracy and why they did.");

		Prompt prompt = new Prompt(List.of(systemMessage, userMessage));

		ChatResponse response = this.chatModel.call(prompt);
		verifyMostFamousPiratePresence(response);

		var promptWithMessageHistory = new Prompt(List.of(new UserMessage("Hello"), response.getResult().getOutput(),
				new UserMessage("Tell me just the names of those pirates.")));
		response = this.chatModel.call(promptWithMessageHistory);
		verifyMostFamousPiratePresence(response);
	}

	@Test
	void usageTest() {
		Prompt prompt = new Prompt("Tell me a joke");
		ChatResponse response = this.chatModel.call(prompt);
		Usage usage = response.getMetadata().getUsage();

		assertThat(usage).isNotNull();
		assertThat(usage.getPromptTokens()).isPositive();
		assertThat(usage.getCompletionTokens()).isPositive();
		assertThat(usage.getTotalTokens()).isPositive();
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
			.variables(Map.of("subject", "ice cream flavors.", "format", format))
			.build();
		Prompt prompt = new Prompt(promptTemplate.createMessage());
		Generation generation = this.chatModel.call(prompt).getResult();
		String outputText = generation.getOutput().getText();
		assertThat(outputText).isNotNull();
		List<String> list = outputConverter.convert(outputText);
		assertThat(list).hasSize(5);
	}

	@Test
	void mapOutputConvert() {
		MapOutputConverter outputConverter = new MapOutputConverter();

		String format = outputConverter.getFormat();
		String template = """
				For each letter in the RGB color scheme, tell me what it stands for.
				Example: R -> Red.
				{format}
				""";
		PromptTemplate promptTemplate = PromptTemplate.builder()
			.template(template)
			.variables(Map.of("format", format))
			.build();
		Prompt prompt = new Prompt(promptTemplate.createMessage());

		Generation generation = this.chatModel.call(prompt).getResult();

		String outputText = generation.getOutput().getText();
		assertThat(outputText).isNotNull();
		Map<String, Object> result = outputConverter.convert(outputText);
		assertThat(result).isNotNull();
		assertThat((String) result.get("R")).containsIgnoringCase("red");
		assertThat((String) result.get("G")).containsIgnoringCase("green");
		assertThat((String) result.get("B")).containsIgnoringCase("blue");
	}

	@Test
	void beanOutputConverterRecords() {
		BeanOutputConverter<ActorsFilmsRecord> outputConverter = new BeanOutputConverter<>(ActorsFilmsRecord.class);

		String format = outputConverter.getFormat();
		String template = """
				Consider the filmography of Tom Hanks and tell me 5 of his movies.
				{format}
				""";
		PromptTemplate promptTemplate = PromptTemplate.builder()
			.template(template)
			.variables(Map.of("format", format))
			.build();
		Prompt prompt = new Prompt(promptTemplate.createMessage());
		Generation generation = this.chatModel.call(prompt).getResult();

		String outputText = generation.getOutput().getText();
		assertThat(outputText).isNotNull();
		ActorsFilmsRecord actorsFilms = outputConverter.convert(outputText);
		assertThat(actorsFilms.actor()).isEqualTo("Tom Hanks");
		assertThat(actorsFilms.movies()).hasSize(5);
	}

	@Test
	void beanStreamOutputConverterRecords() {
		BeanOutputConverter<ActorsFilmsRecord> outputConverter = new BeanOutputConverter<>(ActorsFilmsRecord.class);

		String format = outputConverter.getFormat();
		String template = """
				Consider the filmography of Tom Hanks and tell me 5 of his movies.
				{format}
				""";
		PromptTemplate promptTemplate = PromptTemplate.builder()
			.template(template)
			.variables(Map.of("format", format))
			.build();
		Prompt prompt = new Prompt(promptTemplate.createMessage());

		String generationTextFromStream = this.chatModel.stream(prompt)
			.collectList()
			.blockOptional()
			.stream()
			.flatMap(Collection::stream)
			.map(ChatResponse::getResults)
			.flatMap(List::stream)
			.map(Generation::getOutput)
			.map(AssistantMessage::getText)
			.collect(Collectors.joining());

		ActorsFilmsRecord actorsFilms = outputConverter.convert(generationTextFromStream);

		assertThat(actorsFilms.actor()).isEqualTo("Tom Hanks");
		assertThat(actorsFilms.movies()).hasSize(5);
	}

	// Example inspired by https://ollama.com/blog/structured-outputs
	@Test
	void jsonStructuredOutputWithFormatOption() {
		var outputConverter = new BeanOutputConverter<>(CountryInfo.class);
		var userPromptTemplate = new PromptTemplate("""
				Tell me about {country}.
				""");
		Map<String, Object> model = Map.of("country", "denmark");
		var prompt = userPromptTemplate.create(model,
				OllamaChatOptions.builder().format(outputConverter.getJsonSchemaMap()).build());

		var chatResponse = this.chatModel.call(prompt);

		var outputText = chatResponse.getResult().getOutput().getText();
		assertThat(outputText).isNotNull();
		var countryInfo = outputConverter.convert(outputText);
		assertThat(countryInfo).isNotNull();
		assertThat(countryInfo.capital()).isEqualToIgnoringCase("Copenhagen");
	}

	// Example from https://ollama.com/blog/structured-outputs
	@Test
	void jsonStructuredOutputWithOutputSchemaOption() {
		var jsonSchemaAsText = ResourceUtils.getText("classpath:country-json-schema.json");
		var chatOptions = OllamaChatOptions.builder().outputSchema(jsonSchemaAsText).build();
		var prompt = new Prompt("Tell me about Canada.", chatOptions);

		var chatResponse = this.chatModel.call(prompt);

		var outputText = chatResponse.getResult().getOutput().getText();
		assertThat(outputText).isNotNull();
		JsonAssertions.assertThatJson(outputText)
			.isObject()
			.containsOnlyKeys("name", "capital", "languages")
			.containsEntry("name", "Canada")
			.containsEntry("capital", "Ottawa")
			.containsEntry("languages", List.of("English", "French"));
	}

	@Test
	void chatClientEntityWithStructuredOutput() {
		// Test using ChatClient high-level API with .entity(Class) method
		// This verifies that StructuredOutputChatOptions implementation works correctly
		// with ChatClient
		var chatClient = ChatClient.builder(this.chatModel).build();

		// Generate expected JSON schema as map for testing purpose
		var expectedOutputSchemaMap = new BeanOutputConverter<>(ActorsFilmsRecord.class).getJsonSchemaMap();

		// Advisor to verify that native structured output is being used
		var nativeStructuredOutputUsed = new AtomicBoolean(false);
		var verifyNativeStructuredOutputAdvisor = new CallAdvisor() {
			@Override
			public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
				var response = chain.nextCall(request);
				var chatOptions = request.prompt().getOptions();

				if (chatOptions instanceof OllamaChatOptions ollamaChatOptions
						&& ollamaChatOptions.getFormat() instanceof Map<?, ?> format
						&& expectedOutputSchemaMap.equals(format)) {
					nativeStructuredOutputUsed.set(true);
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

		var actorsFilms = chatClient.prompt("Generate the filmography of 5 movies for Tom Hanks.")
			// forces native structured output handling via StructuredOutputChatOptions
			.advisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
			.advisors(verifyNativeStructuredOutputAdvisor)
			.call()
			.entity(ActorsFilmsRecord.class);

		// Verify that native structured output was used
		assertThat(nativeStructuredOutputUsed.get())
			.as("Native structured output should be used with OllamaChatOptions.setFormat.")
			.isTrue();

		assertThat(actorsFilms).isNotNull();
		assertThat(actorsFilms.actor()).isEqualTo("Tom Hanks");
		assertThat(actorsFilms.movies()).hasSize(5);
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

	private static void verifyMostFamousPiratePresence(ChatResponse chatResponse) {
		var outputText = chatResponse.getResult().getOutput().getText();
		// From time to time, there is confusion between Blackbeard and Black Bart, and
		// the test fails unless both nicknames are provided.
		assertThat(outputText).containsAnyOf("Blackbeard", "Black Bart");
	}

	static class MathTools {

		@Tool(description = "Multiply the two numbers")
		@SuppressWarnings("unused")
		double multiply(double a, double b) {
			return a * b;
		}

	}

	record CountryInfo(@JsonProperty(required = true) String name, @JsonProperty(required = true) String capital,
			@JsonProperty(required = true) List<String> languages) {
	}

	record ActorsFilmsRecord(String actor, List<String> movies) {

	}

	@SpringBootConfiguration
	static class TestConfiguration {

		@Bean
		OllamaApi ollamaApi() {
			return initializeOllama(MODEL);
		}

		@Bean
		OllamaChatModel ollamaChat(OllamaApi ollamaApi) {
			return OllamaChatModel.builder()
				.ollamaApi(ollamaApi)
				.defaultOptions(OllamaChatOptions.builder().model(MODEL).temperature(0.9).build())
				.modelManagementOptions(ModelManagementOptions.builder()
					.pullModelStrategy(PullModelStrategy.WHEN_MISSING)
					.additionalModels(List.of(ADDITIONAL_MODEL))
					.build())
				.retryTemplate(RetryUtils.DEFAULT_RETRY_TEMPLATE)
				.build();
		}

	}

}
