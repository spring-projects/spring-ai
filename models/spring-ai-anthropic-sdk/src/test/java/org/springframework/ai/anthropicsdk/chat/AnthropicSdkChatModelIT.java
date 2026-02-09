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

package org.springframework.ai.anthropicsdk.chat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.ToolChoice;
import com.anthropic.models.messages.ToolChoiceAny;
import com.anthropic.models.messages.ToolChoiceNone;
import com.anthropic.models.messages.ToolChoiceTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import org.springframework.ai.anthropicsdk.AnthropicSdkChatModel;
import org.springframework.ai.anthropicsdk.AnthropicSdkChatOptions;
import org.springframework.ai.anthropicsdk.AnthropicSdkTestConfiguration;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.content.Media;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.ListOutputConverter;
import org.springframework.ai.converter.MapOutputConverter;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link AnthropicSdkChatModel}.
 *
 * @author Soby Chacko
 */
@SpringBootTest(classes = AnthropicSdkTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class AnthropicSdkChatModelIT {

	private static final Logger logger = LoggerFactory.getLogger(AnthropicSdkChatModelIT.class);

	@Value("classpath:/prompts/system-message.st")
	private Resource systemResource;

	@Autowired
	private AnthropicSdkChatModel chatModel;

	private static void validateChatResponseMetadata(ChatResponse response, String model) {
		assertThat(response.getMetadata().getId()).isNotEmpty();
		assertThat(response.getMetadata().getUsage().getPromptTokens()).isPositive();
		assertThat(response.getMetadata().getUsage().getCompletionTokens()).isPositive();
		assertThat(response.getMetadata().getUsage().getTotalTokens()).isPositive();
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "claude-sonnet-4-20250514" })
	void roleTest(String modelName) {
		UserMessage userMessage = new UserMessage(
				"Tell me about 3 famous pirates from the Golden Age of Piracy and why they did.");
		SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(this.systemResource);
		Message systemMessage = systemPromptTemplate.createMessage(Map.of("name", "Bob", "voice", "pirate"));
		Prompt prompt = new Prompt(List.of(userMessage, systemMessage),
				AnthropicSdkChatOptions.builder().model(modelName).build());
		ChatResponse response = this.chatModel.call(prompt);
		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getMetadata().getUsage().getCompletionTokens()).isGreaterThan(0);
		assertThat(response.getMetadata().getUsage().getPromptTokens()).isGreaterThan(0);
		assertThat(response.getMetadata().getUsage().getTotalTokens())
			.isEqualTo(response.getMetadata().getUsage().getPromptTokens()
					+ response.getMetadata().getUsage().getCompletionTokens());
		Generation generation = response.getResults().get(0);
		assertThat(generation.getOutput().getText()).contains("Blackbeard");
		assertThat(generation.getMetadata().getFinishReason()).isEqualTo("end_turn");
		logger.info(response.toString());
	}

	@Test
	void testMessageHistory() {
		// First turn - ask about pirates
		UserMessage firstUserMessage = new UserMessage(
				"Tell me about 3 famous pirates from the Golden Age of Piracy and why they did.");
		SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(this.systemResource);
		Message systemMessage = systemPromptTemplate.createMessage(Map.of("name", "Bob", "voice", "pirate"));
		Prompt prompt = new Prompt(List.of(systemMessage, firstUserMessage),
				AnthropicSdkChatOptions.builder().model(Model.CLAUDE_SONNET_4_20250514).build());

		ChatResponse response = this.chatModel.call(prompt);
		assertThat(response.getResult().getOutput().getText()).containsAnyOf("Blackbeard", "Bartholomew");

		// Second turn - include the first exchange in history, then ask to repeat
		var promptWithMessageHistory = new Prompt(List.of(systemMessage, firstUserMessage,
				response.getResult().getOutput(), new UserMessage("Repeat the names of the pirates you mentioned.")));
		response = this.chatModel.call(promptWithMessageHistory);

		assertThat(response.getResult().getOutput().getText()).containsAnyOf("Blackbeard", "Bartholomew");
	}

	@Test
	void listOutputConverter() {
		DefaultConversionService conversionService = new DefaultConversionService();
		ListOutputConverter listOutputConverter = new ListOutputConverter(conversionService);

		String format = listOutputConverter.getFormat();
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

		List<String> list = listOutputConverter.convert(generation.getOutput().getText());
		assertThat(list).hasSize(5);
	}

	@Test
	void mapOutputConverter() {
		MapOutputConverter mapOutputConverter = new MapOutputConverter();

		String format = mapOutputConverter.getFormat();
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

		Map<String, Object> result = mapOutputConverter.convert(generation.getOutput().getText());
		assertThat(result.get("numbers")).isEqualTo(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9));
	}

	@Test
	void beanOutputConverterRecords() {
		BeanOutputConverter<ActorsFilmsRecord> beanOutputConverter = new BeanOutputConverter<>(ActorsFilmsRecord.class);

		String format = beanOutputConverter.getFormat();
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

		ActorsFilmsRecord actorsFilms = beanOutputConverter.convert(generation.getOutput().getText());
		logger.info("" + actorsFilms);
		assertThat(actorsFilms.actor()).isEqualTo("Tom Hanks");
		assertThat(actorsFilms.movies()).hasSize(5);
	}

	@Test
	void validateCallResponseMetadata() {
		String model = Model.CLAUDE_SONNET_4_20250514.asString();
		// @formatter:off
		ChatResponse response = ChatClient.create(this.chatModel).prompt()
				.options(AnthropicSdkChatOptions.builder().model(model).build())
				.user("Tell me about 3 famous pirates from the Golden Age of Piracy and what they did")
				.call()
				.chatResponse();
		// @formatter:on

		logger.info(response.toString());
		validateChatResponseMetadata(response, model);
	}

	@Test
	void streamingBasicTest() {
		Prompt prompt = new Prompt("Tell me a short joke about programming.");

		List<ChatResponse> responses = this.chatModel.stream(prompt).collectList().block();

		assertThat(responses).isNotEmpty();

		// Concatenate all text from streaming responses
		String fullResponse = responses.stream()
			.filter(response -> response.getResult() != null)
			.map(response -> response.getResult().getOutput().getText())
			.filter(text -> text != null)
			.reduce("", String::concat);

		assertThat(fullResponse).isNotEmpty();
		logger.info("Streaming response: {}", fullResponse);
	}

	@Test
	void streamingWithTokenUsage() {
		Prompt prompt = new Prompt("Tell me a very short joke.");

		List<ChatResponse> responses = this.chatModel.stream(prompt).collectList().block();

		assertThat(responses).isNotEmpty();

		// Find the response with usage metadata (comes from message_delta event)
		ChatResponse lastResponseWithUsage = responses.stream()
			.filter(response -> response.getMetadata() != null && response.getMetadata().getUsage() != null
					&& response.getMetadata().getUsage().getTotalTokens() > 0)
			.reduce((first, second) -> second)
			.orElse(null);

		assertThat(lastResponseWithUsage).isNotNull();

		var usage = lastResponseWithUsage.getMetadata().getUsage();
		logger.info("Streaming usage - Input: {}, Output: {}, Total: {}", usage.getPromptTokens(),
				usage.getCompletionTokens(), usage.getTotalTokens());

		// Verify both input and output tokens are captured
		assertThat(usage.getPromptTokens()).as("Input tokens should be captured from message_start").isPositive();
		assertThat(usage.getCompletionTokens()).as("Output tokens should be captured from message_delta").isPositive();
		assertThat(usage.getTotalTokens()).isEqualTo(usage.getPromptTokens() + usage.getCompletionTokens());

		// Also verify message metadata is captured
		assertThat(lastResponseWithUsage.getMetadata().getId()).as("Message ID should be captured").isNotEmpty();
		assertThat(lastResponseWithUsage.getMetadata().getModel()).as("Model should be captured").isNotEmpty();
	}

	@Test
	void functionCallTest() {
		UserMessage userMessage = new UserMessage(
				"What's the weather like in San Francisco, Tokyo and Paris? Return the result in Celsius.");

		List<Message> messages = new ArrayList<>(List.of(userMessage));

		var promptOptions = AnthropicSdkChatOptions.builder()
			.model(Model.CLAUDE_3_5_HAIKU_20241022.asString())
			.toolCallbacks(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
				.description(
						"Get the weather in location. Return temperature in 36°F or 36°C format. Use multi-turn if needed.")
				.inputType(MockWeatherService.Request.class)
				.build())
			.build();

		ChatResponse response = this.chatModel.call(new Prompt(messages, promptOptions));

		logger.info("Response: {}", response);

		Generation generation = response.getResult();
		assertThat(generation).isNotNull();
		assertThat(generation.getOutput()).isNotNull();
		assertThat(generation.getOutput().getText()).contains("30", "10", "15");
		assertThat(response.getMetadata()).isNotNull();
		assertThat(response.getMetadata().getUsage()).isNotNull();
		assertThat(response.getMetadata().getUsage().getTotalTokens()).isGreaterThan(100);
	}

	@Test
	void streamFunctionCallTest() {
		UserMessage userMessage = new UserMessage(
				"What's the weather like in San Francisco, Tokyo and Paris? Return the result in Celsius.");

		List<Message> messages = new ArrayList<>(List.of(userMessage));

		var promptOptions = AnthropicSdkChatOptions.builder()
			.model(Model.CLAUDE_3_5_HAIKU_20241022.asString())
			.toolCallbacks(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
				.description(
						"Get the weather in location. Return temperature in 36°F or 36°C format. Use multi-turn if needed.")
				.inputType(MockWeatherService.Request.class)
				.build())
			.build();

		Flux<ChatResponse> responseFlux = this.chatModel.stream(new Prompt(messages, promptOptions));

		String content = responseFlux.collectList()
			.block()
			.stream()
			.filter(cr -> cr.getResult() != null)
			.map(cr -> cr.getResult().getOutput().getText())
			.filter(text -> text != null)
			.collect(java.util.stream.Collectors.joining());

		logger.info("Streaming Response: {}", content);
		assertThat(content).contains("30", "10", "15");
	}

	@Test
	void streamFunctionCallUsageTest() {
		UserMessage userMessage = new UserMessage(
				"What's the weather like in San Francisco, Tokyo and Paris? Return the result in Celsius.");

		List<Message> messages = new ArrayList<>(List.of(userMessage));

		var promptOptions = AnthropicSdkChatOptions.builder()
			.model(Model.CLAUDE_3_5_HAIKU_20241022.asString())
			.toolCallbacks(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
				.description(
						"Get the weather in location. Return temperature in 36°F or 36°C format. Use multi-turn if needed.")
				.inputType(MockWeatherService.Request.class)
				.build())
			.build();

		Flux<ChatResponse> responseFlux = this.chatModel.stream(new Prompt(messages, promptOptions));

		ChatResponse lastResponse = responseFlux.collectList()
			.block()
			.stream()
			.filter(cr -> cr.getMetadata() != null && cr.getMetadata().getUsage() != null
					&& cr.getMetadata().getUsage().getTotalTokens() > 0)
			.reduce((first, second) -> second)
			.orElse(null);

		logger.info("Streaming Response with usage: {}", lastResponse);

		assertThat(lastResponse).isNotNull();
		Usage usage = lastResponse.getMetadata().getUsage();
		assertThat(usage).isNotNull();
		// Tool calling uses more tokens due to multi-turn conversation
		assertThat(usage.getTotalTokens()).isGreaterThan(100);
	}

	@Test
	void beanStreamOutputConverterRecords() {
		BeanOutputConverter<ActorsFilmsRecord> beanOutputConverter = new BeanOutputConverter<>(ActorsFilmsRecord.class);

		String format = beanOutputConverter.getFormat();
		String template = """
				Generate the filmography of 5 movies for Tom Hanks.
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
			.filter(text -> text != null)
			.collect(Collectors.joining());

		ActorsFilmsRecord actorsFilms = beanOutputConverter.convert(generationTextFromStream);
		logger.info("" + actorsFilms);
		assertThat(actorsFilms.actor()).isEqualTo("Tom Hanks");
		assertThat(actorsFilms.movies()).hasSize(5);
	}

	@Test
	void validateStreamCallResponseMetadata() {
		String model = Model.CLAUDE_SONNET_4_20250514.asString();
		// @formatter:off
		ChatResponse response = ChatClient.create(this.chatModel).prompt()
				.options(AnthropicSdkChatOptions.builder().model(model).build())
				.user("Tell me about 3 famous pirates from the Golden Age of Piracy and what they did")
				.stream()
				.chatResponse()
				.blockLast();
		// @formatter:on

		logger.info(response.toString());
		validateChatResponseMetadata(response, model);
	}

	@Test
	void testToolUseContentBlock() {
		UserMessage userMessage = new UserMessage(
				"What's the weather like in San Francisco, Tokyo and Paris? Return the result in Celsius.");

		List<Message> messages = new ArrayList<>(List.of(userMessage));

		var promptOptions = AnthropicSdkChatOptions.builder()
			.model(Model.CLAUDE_3_5_HAIKU_20241022.asString())
			.internalToolExecutionEnabled(false)
			.toolCallbacks(List.of(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
				.description(
						"Get the weather in location. Return temperature in 36°F or 36°C format. Use multi-turn if needed.")
				.inputType(MockWeatherService.Request.class)
				.build()))
			.build();

		ChatResponse response = this.chatModel.call(new Prompt(messages, promptOptions));

		logger.info("Response: {}", response);
		for (Generation generation : response.getResults()) {
			AssistantMessage message = generation.getOutput();
			if (!message.getToolCalls().isEmpty()) {
				assertThat(message.getToolCalls()).isNotEmpty();
				AssistantMessage.ToolCall toolCall = message.getToolCalls().get(0);
				assertThat(toolCall.id()).isNotBlank();
				assertThat(toolCall.name()).isNotBlank();
				assertThat(toolCall.arguments()).isNotBlank();
			}
		}
	}

	@Test
	void testToolChoiceAny() {
		// A user question that would not typically result in a tool request
		UserMessage userMessage = new UserMessage("Say hi");

		List<Message> messages = new ArrayList<>(List.of(userMessage));

		var promptOptions = AnthropicSdkChatOptions.builder()
			.model(Model.CLAUDE_SONNET_4_20250514.asString())
			.toolChoice(ToolChoice.ofAny(ToolChoiceAny.builder().build()))
			.internalToolExecutionEnabled(false)
			.toolCallbacks(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
				.description(
						"Get the weather in location. Return temperature in 36°F or 36°C format. Use multi-turn if needed.")
				.inputType(MockWeatherService.Request.class)
				.build())
			.build();

		ChatResponse response = this.chatModel.call(new Prompt(messages, promptOptions));

		logger.info("Response: {}", response);
		assertThat(response.getResults()).isNotNull();
		// When tool choice is "any", the model MUST use at least one tool
		boolean hasToolCalls = response.getResults()
			.stream()
			.anyMatch(generation -> !generation.getOutput().getToolCalls().isEmpty());
		assertThat(hasToolCalls).isTrue();
	}

	@Test
	void testToolChoiceTool() {
		UserMessage userMessage = new UserMessage(
				"What's the weather like in San Francisco? Return the result in Celsius.");

		List<Message> messages = new ArrayList<>(List.of(userMessage));

		var promptOptions = AnthropicSdkChatOptions.builder()
			.model(Model.CLAUDE_SONNET_4_20250514.asString())
			.toolChoice(ToolChoice.ofTool(ToolChoiceTool.builder().name("getFunResponse").build()))
			.internalToolExecutionEnabled(false)
			.toolCallbacks(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
				.description(
						"Get the weather in location. Return temperature in 36°F or 36°C format. Use multi-turn if needed.")
				.inputType(MockWeatherService.Request.class)
				.build(),
					// Based on the user's question the model should want to call
					// getCurrentWeather
					// however we're going to force getFunResponse
					FunctionToolCallback.builder("getFunResponse", new MockWeatherService())
						.description("Get a fun response")
						.inputType(MockWeatherService.Request.class)
						.build())
			.build();

		ChatResponse response = this.chatModel.call(new Prompt(messages, promptOptions));

		logger.info("Response: {}", response);
		assertThat(response.getResults()).isNotNull();
		// When tool choice is a specific tool, the model MUST use that specific tool
		List<AssistantMessage.ToolCall> allToolCalls = response.getResults()
			.stream()
			.flatMap(generation -> generation.getOutput().getToolCalls().stream())
			.toList();
		assertThat(allToolCalls).isNotEmpty();
		assertThat(allToolCalls).hasSize(1);
		assertThat(allToolCalls.get(0).name()).isEqualTo("getFunResponse");
	}

	@Test
	void testToolChoiceNone() {
		UserMessage userMessage = new UserMessage("What's the weather like in San Francisco?");

		List<Message> messages = new ArrayList<>(List.of(userMessage));

		var promptOptions = AnthropicSdkChatOptions.builder()
			.model(Model.CLAUDE_SONNET_4_20250514.asString())
			.toolChoice(ToolChoice.ofNone(ToolChoiceNone.builder().build()))
			.toolCallbacks(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
				.description(
						"Get the weather in location. Return temperature in 36°F or 36°C format. Use multi-turn if needed.")
				.inputType(MockWeatherService.Request.class)
				.build())
			.build();

		ChatResponse response = this.chatModel.call(new Prompt(messages, promptOptions));

		logger.info("Response: {}", response);
		assertThat(response.getResults()).isNotNull();
		// When tool choice is "none", the model MUST NOT use any tools
		List<AssistantMessage.ToolCall> allToolCalls = response.getResults()
			.stream()
			.flatMap(generation -> generation.getOutput().getToolCalls().stream())
			.toList();
		assertThat(allToolCalls).isEmpty();
	}

	@Test
	void multiModalityTest() throws IOException {
		var imageData = new ClassPathResource("/test.png");

		var userMessage = UserMessage.builder()
			.text("Explain what do you see on this picture?")
			.media(List.of(new Media(MimeTypeUtils.IMAGE_PNG, imageData)))
			.build();

		var response = this.chatModel.call(new Prompt(List.of(userMessage)));

		logger.info("Response: {}", response.getResult().getOutput().getText());
		assertThat(response.getResult().getOutput().getText()).containsAnyOf("bananas", "apple", "bowl", "basket",
				"fruit");
	}

	@Test
	void multiModalityPdfTest() throws IOException {
		var pdfData = new ClassPathResource("/spring-ai-reference-overview.pdf");

		var userMessage = UserMessage.builder()
			.text("You are a very professional document summarization specialist. Please summarize the given document.")
			.media(List.of(new Media(new MimeType("application", "pdf"), pdfData)))
			.build();

		var response = this.chatModel.call(new Prompt(List.of(userMessage)));

		logger.info("Response: {}", response.getResult().getOutput().getText());
		assertThat(response.getResult().getOutput().getText()).containsAnyOf("Spring AI", "portable API");
	}

	@Test
	void thinkingTest() {
		UserMessage userMessage = new UserMessage(
				"Are there an infinite number of prime numbers such that n mod 4 == 3?");

		var promptOptions = AnthropicSdkChatOptions.builder()
			.model(Model.CLAUDE_SONNET_4_20250514.asString())
			.temperature(1.0) // temperature must be 1 when thinking is enabled
			.maxTokens(16000)
			.thinkingEnabled(10000L)
			.build();

		ChatResponse response = this.chatModel.call(new Prompt(List.of(userMessage), promptOptions));

		assertThat(response.getResults()).isNotEmpty();
		assertThat(response.getResults().size()).isGreaterThanOrEqualTo(2);

		for (Generation generation : response.getResults()) {
			AssistantMessage message = generation.getOutput();
			if (message.getText() != null && !message.getText().isBlank()) {
				// Text block
				assertThat(message.getText()).isNotBlank();
			}
			else if (message.getMetadata().containsKey("signature")) {
				// Thinking block
				assertThat(message.getMetadata().get("signature")).isNotNull();
			}
			else if (message.getMetadata().containsKey("data")) {
				// Redacted thinking block
				assertThat(message.getMetadata().get("data")).isNotNull();
			}
		}
	}

	@Test
	void thinkingWithStreamingTest() {
		UserMessage userMessage = new UserMessage(
				"Are there an infinite number of prime numbers such that n mod 4 == 3?");

		var promptOptions = AnthropicSdkChatOptions.builder()
			.model(Model.CLAUDE_SONNET_4_20250514.asString())
			.temperature(1.0) // temperature must be 1 when thinking is enabled
			.maxTokens(16000)
			.thinkingEnabled(10000L)
			.build();

		Flux<ChatResponse> responseFlux = this.chatModel.stream(new Prompt(List.of(userMessage), promptOptions));

		List<ChatResponse> responses = responseFlux.collectList().block();

		// Verify we got text content
		String content = responses.stream()
			.map(ChatResponse::getResults)
			.flatMap(List::stream)
			.map(Generation::getOutput)
			.map(AssistantMessage::getText)
			.filter(text -> text != null && !text.isBlank())
			.collect(Collectors.joining());

		logger.info("Thinking streaming response: {}", content);
		assertThat(content).isNotBlank();

		// Verify signature was captured in the stream
		boolean hasSignature = responses.stream()
			.map(ChatResponse::getResults)
			.flatMap(List::stream)
			.map(Generation::getOutput)
			.anyMatch(msg -> msg.getMetadata().containsKey("signature"));

		assertThat(hasSignature).as("Streaming should capture the thinking block signature").isTrue();
	}

	record ActorsFilmsRecord(String actor, List<String> movies) {

	}

}
