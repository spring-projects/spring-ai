/*
 * Copyright 2024-2024 the original author or authors.
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

package org.springframework.ai.openai.chat.proxy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
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
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.tool.MockWeatherService;
import org.springframework.ai.openai.chat.ActorsFilms;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Alexandros Pappas
 *
 * Unlike other proxy implementations (e.g., NVIDIA), Perplexity operates differently:
 *
 * - Perplexity includes integrated real-time web search results as part of its response
 * rather than through explicit function calls. Consequently, no `toolCalls` or function
 * call mechanisms are exposed in the API responses
 *
 * For more information on Perplexity's behavior, refer to its API documentation:
 * <a href="https://docs.perplexity.ai/api-reference/chat-completions">perplexity-api</a>
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "PERPLEXITY_API_KEY", matches = ".+")
@Disabled("Requires Perplexity credits")
class PerplexityWithOpenAiChatModelIT {

	private static final Logger logger = LoggerFactory.getLogger(PerplexityWithOpenAiChatModelIT.class);

	private static final String PERPLEXITY_BASE_URL = "https://api.perplexity.ai";

	private static final String PERPLEXITY_COMPLETIONS_PATH = "/chat/completions";

	private static final String DEFAULT_PERPLEXITY_MODEL = "llama-3.1-sonar-small-128k-online";

	@Value("classpath:/prompts/system-message.st")
	private Resource systemResource;

	@Autowired
	private OpenAiChatModel chatModel;

	@Test
	void roleTest() {
		// Ensure the SystemMessage comes before UserMessage to comply with Perplexity
		// API's sequence rules
		SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(this.systemResource);
		Message systemMessage = systemPromptTemplate.createMessage(Map.of("name", "Bob", "voice", "pirate"));
		UserMessage userMessage = new UserMessage(
				"Tell me about 3 famous pirates from the Golden Age of Piracy and what they did.");
		Prompt prompt = new Prompt(List.of(systemMessage, userMessage));
		ChatResponse response = this.chatModel.call(prompt);
		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getResults().get(0).getOutput().getText()).contains("Blackbeard");
	}

	@Test
	void streamRoleTest() {
		// Ensure the SystemMessage comes before UserMessage to comply with Perplexity
		// API's sequence rules
		SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(this.systemResource);
		Message systemMessage = systemPromptTemplate.createMessage(Map.of("name", "Bob", "voice", "pirate"));
		UserMessage userMessage = new UserMessage(
				"Tell me about 3 famous pirates from the Golden Age of Piracy and what they did.");
		Prompt prompt = new Prompt(List.of(systemMessage, userMessage));
		Flux<ChatResponse> flux = this.chatModel.stream(prompt);

		List<ChatResponse> responses = flux.collectList().block();
		assertThat(responses.size()).isGreaterThan(1);

		String stitchedResponseContent = responses.stream()
			.map(ChatResponse::getResults)
			.flatMap(List::stream)
			.map(Generation::getOutput)
			.map(AssistantMessage::getText)
			.collect(Collectors.joining());

		assertThat(stitchedResponseContent).contains("Blackbeard");
	}

	@Test
	void streamingWithTokenUsage() {
		var promptOptions = OpenAiChatOptions.builder().streamUsage(true).seed(1).build();

		var prompt = new Prompt("List two colors of the Polish flag. Be brief.", promptOptions);

		var streamingTokenUsage = this.chatModel.stream(prompt).blockLast().getMetadata().getUsage();
		var referenceTokenUsage = this.chatModel.call(prompt).getMetadata().getUsage();

		assertThat(streamingTokenUsage.getPromptTokens()).isGreaterThan(0);
		assertThat(streamingTokenUsage.getCompletionTokens()).isGreaterThan(0);
		assertThat(streamingTokenUsage.getTotalTokens()).isGreaterThan(0);

		assertThat(streamingTokenUsage.getPromptTokens()).isEqualTo(referenceTokenUsage.getPromptTokens());
		assertThat(streamingTokenUsage.getCompletionTokens())
			.isGreaterThanOrEqualTo(referenceTokenUsage.getCompletionTokens());
		assertThat(streamingTokenUsage.getTotalTokens()).isGreaterThanOrEqualTo(referenceTokenUsage.getTotalTokens());
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
			.variables(Map.of("subject", "numbers from 1 to 9 under the key name 'numbers'", "format", format))
			.build();
		Prompt prompt = new Prompt(promptTemplate.createMessage());
		Generation generation = this.chatModel.call(prompt).getResult();

		Map<String, Object> result = outputConverter.convert(generation.getOutput().getText());
		assertThat(result.get("numbers")).isEqualTo(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9));
	}

	@Test
	@Disabled("Perplexity gets confused with the 'Generate the filmography for a random actor.' prompt")
	void beanOutputConverter() {
		BeanOutputConverter<ActorsFilms> outputConverter = new BeanOutputConverter<>(ActorsFilms.class);

		String format = outputConverter.getFormat();
		String template = """
				Generate the filmography for a random actor.
				{format}
				""";
		PromptTemplate promptTemplate = PromptTemplate.builder()
			.template(template)
			.variables(Map.of("format", format))
			.build();
		Prompt prompt = new Prompt(promptTemplate.createMessage());
		Generation generation = this.chatModel.call(prompt).getResult();

		ActorsFilms actorsFilms = outputConverter.convert(generation.getOutput().getText());
		assertThat(actorsFilms.getActor()).isNotEmpty();
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

		String generationTextFromStream = this.chatModel.stream(prompt)
			.collectList()
			.block()
			.stream()
			.map(ChatResponse::getResults)
			.flatMap(List::stream)
			.map(Generation::getOutput)
			.map(AssistantMessage::getText)
			.filter(c -> c != null)
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
			.toolCallbacks(List.of(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
				.description("Get the weather in location")
				.inputType(MockWeatherService.Request.class)
				.build()))
			.build();

		ChatResponse response = this.chatModel.call(new Prompt(messages, promptOptions));

		logger.info("Response: {}", response);

		assertThat(response.getResults().stream().mapToLong(r -> r.getOutput().getToolCalls().size()).sum()).isZero();
	}

	@Test
	void streamFunctionCallTest() {
		UserMessage userMessage = new UserMessage(
				"What's the weather like in San Francisco, Tokyo, and Paris? Return the temperature in Celsius.");

		List<Message> messages = new ArrayList<>(List.of(userMessage));

		var promptOptions = OpenAiChatOptions.builder()
			.toolCallbacks(List.of(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
				.description("Get the weather in location")
				.inputType(MockWeatherService.Request.class)
				.build()))
			.build();

		Flux<ChatResponse> response = this.chatModel.stream(new Prompt(messages, promptOptions));

		String content = response.collectList()
			.block()
			.stream()
			.map(ChatResponse::getResults)
			.flatMap(List::stream)
			.map(Generation::getOutput)
			.map(AssistantMessage::getText)
			.collect(Collectors.joining());
		logger.info("Response: {}", content);

		assertThat(content).doesNotContain("toolCalls");
	}

	@Test
	void validateCallResponseMetadata() {
		ChatResponse response = ChatClient.create(this.chatModel)
			.prompt()
			.options(OpenAiChatOptions.builder().model(DEFAULT_PERPLEXITY_MODEL).build())
			.user("Tell me about 3 famous pirates from the Golden Age of Piracy and what they did")
			.call()
			.chatResponse();

		logger.info(response.toString());
		assertThat(response.getMetadata().getId()).isNotEmpty();
		assertThat(response.getMetadata().getModel()).containsIgnoringCase(DEFAULT_PERPLEXITY_MODEL);
		assertThat(response.getMetadata().getUsage().getPromptTokens()).isPositive();
		assertThat(response.getMetadata().getUsage().getCompletionTokens()).isPositive();
		assertThat(response.getMetadata().getUsage().getTotalTokens()).isPositive();
	}

	record ActorsFilmsRecord(String actor, List<String> movies) {
	}

	@SpringBootConfiguration
	static class Config {

		@Bean
		public OpenAiApi chatCompletionApi() {
			return OpenAiApi.builder()
				.baseUrl(PERPLEXITY_BASE_URL)
				.apiKey(System.getenv("PERPLEXITY_API_KEY"))
				.completionsPath(PERPLEXITY_COMPLETIONS_PATH)
				.embeddingsPath("/v1/embeddings")
				.build();
		}

		@Bean
		public OpenAiChatModel openAiClient(OpenAiApi openAiApi) {
			return OpenAiChatModel.builder()
				.openAiApi(openAiApi)
				.defaultOptions(OpenAiChatOptions.builder().model(DEFAULT_PERPLEXITY_MODEL).build())
				.build();
		}

	}

}
