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

package org.springframework.ai.openai.chat.proxy;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.ollama.OllamaContainer;
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
import org.springframework.ai.content.Media;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.ListOutputConverter;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for OpenAI SDK Chat Model using Ollama as an OpenAI-compatible
 * provider.
 *
 * @author Ilayaperumal Gopinathan
 */
@Testcontainers
@SpringBootTest(classes = OllamaWithOpenAiChatModelIT.Config.class)
class OllamaWithOpenAiChatModelIT {

	private static final Logger logger = LoggerFactory.getLogger(OllamaWithOpenAiChatModelIT.class);

	private static final String DEFAULT_OLLAMA_MODEL = "qwen2.5:3b";

	private static final String MULTIMODAL_MODEL = "gemma3:4b";

	private static final boolean SKIP_CONTAINER_CREATION = Boolean
		.parseBoolean(System.getenv().getOrDefault("OLLAMA_WITH_REUSE", "false"));

	static OllamaContainer ollamaContainer;

	static String getBaseUrl() {
		return SKIP_CONTAINER_CREATION ? "http://localhost:11434/v1" : ollamaContainer.getEndpoint() + "/v1";
	}

	@Value("classpath:/prompts/system-message.st")
	private Resource systemResource;

	@Autowired
	private OpenAiChatModel chatModel;

	@BeforeAll
	public static void beforeAll() throws IOException, InterruptedException {
		if (!SKIP_CONTAINER_CREATION) {
			ollamaContainer = new OllamaContainer("ollama/ollama:0.23.1").withReuse(true);
			ollamaContainer.start();
			logger.info(
					"Start pulling the '" + DEFAULT_OLLAMA_MODEL + " ' generative ... would take several minutes ...");
			ollamaContainer.execInContainer("ollama", "pull", DEFAULT_OLLAMA_MODEL);
			ollamaContainer.execInContainer("ollama", "pull", MULTIMODAL_MODEL);
			logger.info(DEFAULT_OLLAMA_MODEL + " pulling competed!");

			// No need to set baseUrl here, it's evaluated dynamically
		}
	}

	@AfterAll
	public static void afterAll() {
		if (ollamaContainer != null) {
			ollamaContainer.stop();
		}
	}

	@Test
	void roleTest() {
		UserMessage userMessage = new UserMessage("What's the capital of Denmark?");
		SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(this.systemResource);
		Message systemMessage = systemPromptTemplate.createMessage(Map.of("name", "Bob", "voice", "pirate"));
		Prompt prompt = new Prompt(List.of(userMessage, systemMessage));
		ChatResponse response = this.chatModel.call(prompt);
		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getResults().get(0).getOutput().getText()).containsIgnoringCase("Copenhag");
	}

	@Test
	void streamRoleTest() {
		UserMessage userMessage = new UserMessage("What's the capital of Denmark?");
		SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(this.systemResource);
		Message systemMessage = systemPromptTemplate.createMessage(Map.of("name", "Bob", "voice", "pirate"));
		Prompt prompt = new Prompt(List.of(userMessage, systemMessage));
		Flux<ChatResponse> flux = this.chatModel.stream(prompt);

		List<ChatResponse> responses = flux.collectList().block();
		assertThat(responses.size()).isGreaterThan(1);

		String stitchedResponseContent = responses.stream()
			.map(ChatResponse::getResults)
			.flatMap(List::stream)
			.map(Generation::getOutput)
			.map(AssistantMessage::getText)
			.collect(Collectors.joining());

		assertThat(stitchedResponseContent).containsIgnoringCase("Copenhag");
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
	void beanOutputConverterRecords() {
		BeanOutputConverter<ActorsFilmsRecord> outputConverter = new BeanOutputConverter<>(ActorsFilmsRecord.class);

		String format = outputConverter.getFormat();
		String template = """
				Generate the filmography of 5 movies for Tom Hanks.
				{format}
				Return ONLY the JSON without any markdown formatting or comments.
				""";
		PromptTemplate promptTemplate = PromptTemplate.builder()
			.template(template)
			.variables(Map.of("format", format))
			.build();
		Prompt prompt = new Prompt(promptTemplate.createMessage(),
				OpenAiChatOptions.builder()
					.responseFormat(OpenAiChatModel.ResponseFormat.builder()
						.type(OpenAiChatModel.ResponseFormat.Type.JSON_OBJECT)
						.build())
					.build());
		Generation generation = this.chatModel.call(prompt).getResult();

		ActorsFilmsRecord actorsFilms = outputConverter.convert(generation.getOutput().getText());
		logger.info("" + actorsFilms);
		assertThat(actorsFilms.actor()).isEqualTo("Tom Hanks");
		assertThat(actorsFilms.movies()).hasSize(5);
	}

	@Test
	void functionCallTest() {
		UserMessage userMessage = new UserMessage(
				"What's the weather like in San Francisco, Tokyo, and Paris? Return a list with the temperature in Celsius for each of the three locations.");

		List<Message> messages = new ArrayList<>(List.of(userMessage));

		var promptOptions = OpenAiChatOptions.builder()
			.model(DEFAULT_OLLAMA_MODEL)
			.toolCallbacks(List.of(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
				.description(
						"Find the weather conditions, forecasts, and temperatures for a location, like a city or state.")
				.inputType(MockWeatherService.Request.class)
				.build()))
			.build();

		ChatResponse response = this.chatModel.call(new Prompt(messages, promptOptions));

		logger.info("Response: {}", response);

		assertThat(response.getResult().getOutput().getText()).contains("30", "10", "15");
	}

	@Test
	void multiModalityEmbeddedImage() {
		var imageData = new ClassPathResource("/test.png");

		var userMessage = UserMessage.builder()
			.text("Explain what do you see on this picture?")
			.media(List.of(new Media(MimeTypeUtils.IMAGE_PNG, imageData)))
			.build();

		var response = this.chatModel
			.call(new Prompt(List.of(userMessage), OpenAiChatOptions.builder().model(MULTIMODAL_MODEL).build()));

		logger.info(response.getResult().getOutput().getText());
		assertThat(response.getResult().getOutput().getText()).containsAnyOf("bananas", "apple", "bowl", "basket",
				"fruit stand");
	}

	@Test
	void validateCallResponseMetadata() {
		ChatResponse response = ChatClient.create(this.chatModel)
			.prompt()
			.options(OpenAiChatOptions.builder().model(DEFAULT_OLLAMA_MODEL))
			.user("Tell me about 3 famous pirates from the Golden Age of Piracy and what they did")
			.call()
			.chatResponse();

		logger.info(response.toString());
		assertThat(response.getMetadata().getId()).isNotEmpty();
		assertThat(response.getMetadata().getModel()).containsIgnoringCase(DEFAULT_OLLAMA_MODEL);
		assertThat(response.getMetadata().getUsage().getPromptTokens()).isPositive();
		assertThat(response.getMetadata().getUsage().getCompletionTokens()).isPositive();
		assertThat(response.getMetadata().getUsage().getTotalTokens()).isPositive();
	}

	@Test
	void extraBodySupport() {
		// Provide a parameter via extraBody that will predictably affect the response
		// 'max_tokens' placed in extraBody should be flattened to the root and limit the
		// response length.
		Map<String, Object> extraBody = Map.of("max_tokens", 2);

		OpenAiChatOptions options = OpenAiChatOptions.builder()
			.model(DEFAULT_OLLAMA_MODEL)
			.extraBody(extraBody)
			.build();

		Prompt prompt = new Prompt("Tell me a short joke.", options);

		ChatResponse response = this.chatModel.call(prompt);

		assertThat(response).isNotNull();
		assertThat(response.getResult().getOutput().getText()).isNotEmpty();
		// Because max_tokens is 2, the finish reason should be length or similar
		// indicating truncation
		assertThat(response.getResult().getMetadata().getFinishReason().toLowerCase()).contains("length");
	}

	record ActorsFilmsRecord(String actor, List<String> movies) {

	}

	public static class MockWeatherService
			implements java.util.function.Function<MockWeatherService.Request, MockWeatherService.Response> {

		@Override
		public Response apply(Request request) {
			double temperature = switch (request.location()) {
				case "San Francisco", "San Francisco, CA" -> 30.0;
				case "Tokyo", "Tokyo, Japan" -> 10.0;
				case "Paris", "Paris, France" -> 15.0;
				default -> 0.0;
			};
			return new Response(temperature, request.unit() != null ? request.unit() : "C");
		}

		public record Request(String location, String unit) {

		}

		public record Response(double temp, String unit) {

		}

	}

	@SpringBootConfiguration
	static class Config {

		@Bean
		public OpenAiChatModel openAiSdkChatModel() {
			return OpenAiChatModel.builder()
				.options(OpenAiChatOptions.builder()
					.baseUrl(getBaseUrl())
					.model(DEFAULT_OLLAMA_MODEL)
					.timeout(Duration.ofMinutes(5))
					.build())
				.build();
		}

	}

}
