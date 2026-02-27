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

package org.springframework.ai.anthropic;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.anthropic.api.tool.MockWeatherService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.content.Media;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.ListOutputConverter;
import org.springframework.ai.converter.MapOutputConverter;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.function.FunctionToolCallback;
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
import org.springframework.util.StringUtils;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = AnthropicChatModelIT.Config.class, properties = "spring.ai.retry.on-http-codes=429")
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class AnthropicChatModelIT {

	private static final Logger logger = LoggerFactory.getLogger(AnthropicChatModelIT.class);

	@Autowired
	protected ChatModel chatModel;

	@Autowired
	protected StreamingChatModel streamingChatModel;

	@Value("classpath:/prompts/system-message.st")
	private Resource systemResource;

	private static void validateChatResponseMetadata(ChatResponse response, String model) {
		assertThat(response.getMetadata().getId()).isNotEmpty();
		assertThat(response.getMetadata().getUsage().getPromptTokens()).isPositive();
		assertThat(response.getMetadata().getUsage().getCompletionTokens()).isPositive();
		assertThat(response.getMetadata().getUsage().getTotalTokens()).isPositive();
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "claude-sonnet-4-5" })
	void roleTest(String modelName) {
		UserMessage userMessage = new UserMessage(
				"Tell me about 3 famous pirates from the Golden Age of Piracy and why they did.");
		SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(this.systemResource);
		Message systemMessage = systemPromptTemplate.createMessage(Map.of("name", "Bob", "voice", "pirate"));
		Prompt prompt = new Prompt(List.of(userMessage, systemMessage),
				AnthropicChatOptions.builder().model(modelName).build());
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
		UserMessage userMessage = new UserMessage(
				"Tell me about 3 famous pirates from the Golden Age of Piracy and why they did.");
		SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(this.systemResource);
		Message systemMessage = systemPromptTemplate.createMessage(Map.of("name", "Bob", "voice", "pirate"));
		Prompt prompt = new Prompt(List.of(userMessage, systemMessage),
				AnthropicChatOptions.builder().model(AnthropicApi.ChatModel.CLAUDE_SONNET_4_5).build());

		ChatResponse response = this.chatModel.call(prompt);
		assertThat(response.getResult().getOutput().getText()).containsAnyOf("Blackbeard", "Bartholomew");

		var promptWithMessageHistory = new Prompt(List.of(new UserMessage("Dummy"), response.getResult().getOutput(),
				new UserMessage("Repeat the last assistant message.")));
		response = this.chatModel.call(promptWithMessageHistory);

		assertThat(response.getResult().getOutput().getText()).containsAnyOf("Blackbeard", "Bartholomew");
	}

	@Test
	void streamingWithTokenUsage() {
		var promptOptions = AnthropicChatOptions.builder().temperature(0.0).build();

		var prompt = new Prompt("List two colors of the Polish flag. Be brief.", promptOptions);
		var streamingTokenUsage = this.chatModel.stream(prompt).blockLast().getMetadata().getUsage();
		var referenceTokenUsage = this.chatModel.call(prompt).getMetadata().getUsage();

		assertThat(streamingTokenUsage.getPromptTokens()).isGreaterThan(0);
		assertThat(streamingTokenUsage.getCompletionTokens()).isGreaterThan(0);
		assertThat(streamingTokenUsage.getTotalTokens()).isGreaterThan(0);

		assertThat(streamingTokenUsage.getPromptTokens()).isEqualTo(referenceTokenUsage.getPromptTokens());
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

		String generationTextFromStream = this.streamingChatModel.stream(prompt)
			.collectList()
			.block()
			.stream()
			.map(ChatResponse::getResults)
			.flatMap(List::stream)
			.map(Generation::getOutput)
			.map(AssistantMessage::getText)
			.collect(Collectors.joining());

		ActorsFilmsRecord actorsFilms = beanOutputConverter.convert(generationTextFromStream);
		logger.info("" + actorsFilms);
		assertThat(actorsFilms.actor()).isEqualTo("Tom Hanks");
		assertThat(actorsFilms.movies()).hasSize(5);
	}

	@Test
	void multiModalityTest() throws IOException {

		var imageData = new ClassPathResource("/test.png");

		var userMessage = UserMessage.builder()
			.text("Explain what do you see on this picture?")
			.media(List.of(new Media(MimeTypeUtils.IMAGE_PNG, imageData)))
			.build();

		var response = this.chatModel.call(new Prompt(List.of(userMessage)));

		logger.info(response.getResult().getOutput().getText());
		assertThat(response.getResult().getOutput().getText()).containsAnyOf("bananas", "apple", "bowl", "basket",
				"fruit stand");
	}

	@Test
	void multiModalityPdfTest() throws IOException {

		var pdfData = new ClassPathResource("/spring-ai-reference-overview.pdf");

		var userMessage = UserMessage.builder()
			.text("You are a very professional document summarization specialist. Please summarize the given document.")
			.media(List.of(new Media(new MimeType("application", "pdf"), pdfData)))
			.build();

		var response = this.chatModel.call(new Prompt(List.of(userMessage),
				ToolCallingChatOptions.builder().model(AnthropicApi.ChatModel.CLAUDE_SONNET_4_5.getName()).build()));

		assertThat(response.getResult().getOutput().getText()).containsAnyOf("Spring AI", "portable API");
	}

	@Test
	void functionCallTest() {

		UserMessage userMessage = new UserMessage(
				"What's the weather like in San Francisco, Tokyo and Paris? Return the result in Celsius.");

		List<Message> messages = new ArrayList<>(List.of(userMessage));

		var promptOptions = AnthropicChatOptions.builder()
			.model(AnthropicApi.ChatModel.CLAUDE_HAIKU_4_5.getName())
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
		assertThat(response.getMetadata().getUsage().getTotalTokens()).isLessThan(4000).isGreaterThan(100);
	}

	@Test
	void streamFunctionCallTest() {

		UserMessage userMessage = new UserMessage(
				// "What's the weather like in San Francisco? Return the result in
				// Celsius.");
				"What's the weather like in San Francisco, Tokyo and Paris? Return the result in Celsius.");

		List<Message> messages = new ArrayList<>(List.of(userMessage));

		var promptOptions = AnthropicChatOptions.builder()
			.model(AnthropicApi.ChatModel.CLAUDE_SONNET_4_5.getName())
			.toolCallbacks(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
				.description(
						"Get the weather in location. Return temperature in 36°F or 36°C format. Use multi-turn if needed.")
				.inputType(MockWeatherService.Request.class)
				.build())
			.build();

		Flux<ChatResponse> response = this.chatModel.stream(new Prompt(messages, promptOptions));

		String content = response.collectList()
			.block()
			.stream()
			.filter(cr -> cr.getResult() != null)
			.map(cr -> cr.getResult().getOutput().getText())
			.collect(Collectors.joining());

		logger.info("Response: {}", content);
		assertThat(content).contains("30", "10", "15");
	}

	@Test
	void streamFunctionCallUsageTest() {

		UserMessage userMessage = new UserMessage(
				"What's the weather like in San Francisco, Tokyo and Paris? Return the result in Celsius.");

		List<Message> messages = new ArrayList<>(List.of(userMessage));

		var promptOptions = AnthropicChatOptions.builder()
			.model(AnthropicApi.ChatModel.CLAUDE_SONNET_4_5.getName())
			.toolCallbacks(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
				.description(
						"Get the weather in location. Return temperature in 36°F or 36°C format. Use multi-turn if needed.")
				.inputType(MockWeatherService.Request.class)
				.build())
			.build();

		Flux<ChatResponse> responseFlux = this.chatModel.stream(new Prompt(messages, promptOptions));

		ChatResponse chatResponse = responseFlux.last().block();

		logger.info("Response: {}", chatResponse);
		Usage usage = chatResponse.getMetadata().getUsage();

		assertThat(usage).isNotNull();
		assertThat(usage.getTotalTokens()).isLessThan(4000).isGreaterThan(1800);
	}

	@Test
	void validateCallResponseMetadata() {
		String model = AnthropicApi.ChatModel.CLAUDE_SONNET_4_5.getName();
		// @formatter:off
		ChatResponse response = ChatClient.create(this.chatModel).prompt()
				.options(AnthropicChatOptions.builder().model(model).build())
				.user("Tell me about 3 famous pirates from the Golden Age of Piracy and what they did")
				.call()
				.chatResponse();
		// @formatter:on

		logger.info(response.toString());
		validateChatResponseMetadata(response, model);
	}

	@Test
	void validateStreamCallResponseMetadata() {
		String model = AnthropicApi.ChatModel.CLAUDE_SONNET_4_5.getName();
		// @formatter:off
		ChatResponse response = ChatClient.create(this.chatModel).prompt()
				.options(AnthropicChatOptions.builder().model(model).build())
				.user("Tell me about 3 famous pirates from the Golden Age of Piracy and what they did")
				.stream()
				.chatResponse()
				.blockLast();
		// @formatter:on

		logger.info(response.toString());
		// Note, brittle test.
		validateChatResponseMetadata(response, "claude-3-5-sonnet-latest");
	}

	@Test
	void thinkingTest() {
		UserMessage userMessage = new UserMessage(
				"Are there an infinite number of prime numbers such that n mod 4 == 3?");

		var promptOptions = AnthropicChatOptions.builder()
			.model(AnthropicApi.ChatModel.CLAUDE_SONNET_4_5.getName())
			.temperature(1.0) // temperature should be set to 1 when thinking is enabled
			.maxTokens(8192)
			.thinking(AnthropicApi.ThinkingType.ENABLED, 2048) // Must be ≥1024 && <
																// max_tokens
			.build();

		ChatResponse response = this.chatModel.call(new Prompt(List.of(userMessage), promptOptions));

		logger.info("Response: {}", response);

		for (Generation generation : response.getResults()) {
			AssistantMessage message = generation.getOutput();
			if (message.getText() != null) { // text
				assertThat(message.getText()).isNotBlank();
			}
			else if (message.getMetadata().containsKey("signature")) { // thinking
				assertThat(message.getMetadata().get("signature")).isNotNull();
				assertThat(message.getMetadata().get("thinking")).isNotNull();
			}
			else if (message.getMetadata().containsKey("data")) { // redacted thinking
				assertThat(message.getMetadata().get("data")).isNotNull();
			}
		}
	}

	@Test
	void thinkingWithStreamingTest() {
		UserMessage userMessage = new UserMessage(
				"Are there an infinite number of prime numbers such that n mod 4 == 3?");

		var promptOptions = AnthropicChatOptions.builder()
			.model(AnthropicApi.ChatModel.CLAUDE_SONNET_4_5.getName())
			.temperature(1.0) // Temperature should be set to 1 when thinking is enabled
			.maxTokens(8192)
			.thinking(AnthropicApi.ThinkingType.ENABLED, 2048) // Must be ≥1024 && <
																// max_tokens
			.build();

		Flux<ChatResponse> responseFlux = this.streamingChatModel
			.stream(new Prompt(List.of(userMessage), promptOptions));

		String content = responseFlux.collectList()
			.block()
			.stream()
			.map(ChatResponse::getResults)
			.flatMap(List::stream)
			.map(Generation::getOutput)
			.map(AssistantMessage::getText)
			.filter(text -> text != null && !text.isBlank())
			.collect(Collectors.joining());

		logger.info("Response: {}", content);

		assertThat(content).isNotBlank();
		assertThat(content).contains("prime numbers");
	}

	@Test
	void testToolUseContentBlock() {
		UserMessage userMessage = new UserMessage(
				"What's the weather like in San Francisco, Tokyo and Paris? Return the result in Celsius.");

		List<Message> messages = new ArrayList<>(List.of(userMessage));

		var promptOptions = AnthropicChatOptions.builder()
			.model(AnthropicApi.ChatModel.CLAUDE_HAIKU_4_5.getName())
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

	record ActorsFilmsRecord(String actor, List<String> movies) {

	}

	@SpringBootConfiguration
	public static class Config {

		@Bean
		public AnthropicApi anthropicApi() {
			return AnthropicApi.builder().apiKey(getApiKey()).build();
		}

		private String getApiKey() {
			String apiKey = System.getenv("ANTHROPIC_API_KEY");
			if (!StringUtils.hasText(apiKey)) {
				throw new IllegalArgumentException(
						"You must provide an API key.  Put it in an environment variable under the name ANTHROPIC_API_KEY");
			}
			return apiKey;
		}

		@Bean
		public AnthropicChatModel openAiChatModel(AnthropicApi api) {
			return AnthropicChatModel.builder().anthropicApi(api).build();
		}

	}

}
