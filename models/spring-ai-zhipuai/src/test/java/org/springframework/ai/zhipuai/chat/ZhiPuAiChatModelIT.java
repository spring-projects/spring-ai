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

package org.springframework.ai.zhipuai.chat;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
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
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.zhipuai.ZhiPuAiAssistantMessage;
import org.springframework.ai.zhipuai.ZhiPuAiChatOptions;
import org.springframework.ai.zhipuai.ZhiPuAiTestConfiguration;
import org.springframework.ai.zhipuai.api.MockWeatherService;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi.ChatCompletionRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.StringUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Geng Rong
 * @author YunKui Lu
 */
@SpringBootTest(classes = ZhiPuAiTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "ZHIPU_AI_API_KEY", matches = ".+")
class ZhiPuAiChatModelIT {

	private static final Logger logger = LoggerFactory.getLogger(ZhiPuAiChatModelIT.class);

	@Autowired
	protected ChatModel chatModel;

	@Autowired
	protected StreamingChatModel streamingChatModel;

	@Value("classpath:/prompts/system-message.st")
	private Resource systemResource;

	/**
	 * Default chat options to use for the tests.
	 * <p>
	 * glm-4-flash is a free model, so it is used by default on the tests.
	 */
	private static final ZhiPuAiChatOptions DEFAULT_CHAT_OPTIONS = ZhiPuAiChatOptions.builder()
		.model(ZhiPuAiApi.ChatModel.GLM_4_Flash.getValue())
		.build();

	@Test
	void roleTest() {
		UserMessage userMessage = new UserMessage(
				"Tell me about 3 famous pirates from the Golden Age of Piracy and what they did.");
		SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(this.systemResource);
		Message systemMessage = systemPromptTemplate.createMessage(Map.of("name", "Bob", "voice", "pirate"));
		Prompt prompt = new Prompt(List.of(userMessage, systemMessage), DEFAULT_CHAT_OPTIONS);
		ChatResponse response = this.chatModel.call(prompt);
		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getResults().get(0).getOutput().getText()).contains("Blackbeard");
		// needs fine tuning... evaluateQuestionAndAnswer(request, response, false);
	}

	@Test
	void streamRoleTest() {
		UserMessage userMessage = new UserMessage(
				"Tell me about 3 famous pirates from the Golden Age of Piracy and what they did.");
		SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(this.systemResource);
		Message systemMessage = systemPromptTemplate.createMessage(Map.of("name", "Bob", "voice", "pirate"));
		Prompt prompt = new Prompt(List.of(userMessage, systemMessage), DEFAULT_CHAT_OPTIONS);
		Flux<ChatResponse> flux = this.streamingChatModel.stream(prompt);

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
		Prompt prompt = new Prompt(promptTemplate.createMessage(), DEFAULT_CHAT_OPTIONS);
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
		Prompt prompt = new Prompt(promptTemplate.createMessage(), DEFAULT_CHAT_OPTIONS);
		ChatResponse chatResponse = this.chatModel.call(prompt);
		Generation generation = chatResponse.getResult();

		Map<String, Object> result = outputConverter.convert(generation.getOutput().getText());
		assertThat(result.get("numbers")).isEqualTo(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9));

	}

	@Test
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
		Prompt prompt = new Prompt(promptTemplate.createMessage(), DEFAULT_CHAT_OPTIONS);
		Generation generation = this.chatModel.call(prompt).getResult();

		ActorsFilms actorsFilms = outputConverter.convert(generation.getOutput().getText());
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
		Prompt prompt = new Prompt(promptTemplate.createMessage(), DEFAULT_CHAT_OPTIONS);
		Generation generation = this.chatModel.call(prompt).getResult();

		ActorsFilmsRecord actorsFilms = outputConverter.convert(generation.getOutput().getText());
		logger.info("actorsFilms:{}", actorsFilms);

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
		Prompt prompt = new Prompt(promptTemplate.createMessage(), DEFAULT_CHAT_OPTIONS);

		String generationTextFromStream = Objects
			.requireNonNull(this.streamingChatModel.stream(prompt).collectList().block())
			.stream()
			.map(ChatResponse::getResults)
			.flatMap(List::stream)
			.map(Generation::getOutput)
			.map(AssistantMessage::getText)
			.collect(Collectors.joining());

		ActorsFilmsRecord actorsFilms = outputConverter.convert(generationTextFromStream);
		logger.info("actorsFilms:{}", actorsFilms);

		assertThat(actorsFilms.actor()).isEqualTo("Tom Hanks");
		assertThat(actorsFilms.movies()).hasSize(5);
	}

	@Test
	void jsonObjectResponseFormatOutputConverterRecords() {
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
		Prompt prompt = new Prompt(promptTemplate.createMessage(),
				ZhiPuAiChatOptions.builder()
					.model(ZhiPuAiApi.ChatModel.GLM_4_Flash.getValue())
					.responseFormat(ChatCompletionRequest.ResponseFormat.jsonObject())
					.build());

		String generationTextFromStream = Objects
			.requireNonNull(this.streamingChatModel.stream(prompt).collectList().block())
			.stream()
			.map(ChatResponse::getResults)
			.flatMap(List::stream)
			.map(Generation::getOutput)
			.map(AssistantMessage::getText)
			.collect(Collectors.joining());
		logger.info("generationTextFromStream:{}", generationTextFromStream);

		ActorsFilmsRecord actorsFilms = outputConverter.convert(generationTextFromStream);
		logger.info("actorsFilms:{}", actorsFilms);

		assertThat(actorsFilms.actor()).isEqualTo("Tom Hanks");
		assertThat(actorsFilms.movies()).hasSize(5);
	}

	@Test
	void functionCallTest() {

		UserMessage userMessage = new UserMessage(
				"What's the weather like in San Francisco, Tokyo, and Paris? Return the temperature in Celsius.");

		List<Message> messages = new ArrayList<>(List.of(userMessage));

		var promptOptions = ZhiPuAiChatOptions.builder()
			.model(ZhiPuAiApi.ChatModel.GLM_4_Flash.getValue())
			.toolCallbacks(List.of(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
				.description("Get the weather in location")
				.inputType(MockWeatherService.Request.class)
				.build()))
			.build();

		ChatResponse response = this.chatModel.call(new Prompt(messages, promptOptions));

		logger.info("Response: {}", response);

		assertThat(response.getResult().getOutput().getText()).containsAnyOf("30.0", "30");
		assertThat(response.getResult().getOutput().getText()).containsAnyOf("10.0", "10");
		assertThat(response.getResult().getOutput().getText()).containsAnyOf("15.0", "15");
	}

	@Test
	void streamFunctionCallTest() {

		UserMessage userMessage = new UserMessage(
				"What's the weather like in San Francisco, Tokyo, and Paris? Return the temperature in Celsius.");

		List<Message> messages = new ArrayList<>(List.of(userMessage));

		var promptOptions = ZhiPuAiChatOptions.builder()
			.model(ZhiPuAiApi.ChatModel.GLM_4_Flash.getValue())
			.toolCallbacks(List.of(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
				.description("Get the weather in location")
				.inputType(MockWeatherService.Request.class)
				.build()))
			.build();

		Flux<ChatResponse> response = this.streamingChatModel.stream(new Prompt(messages, promptOptions));

		String content = Objects.requireNonNull(response.collectList().block())
			.stream()
			.map(ChatResponse::getResults)
			.flatMap(List::stream)
			.map(Generation::getOutput)
			.map(AssistantMessage::getText)
			.collect(Collectors.joining());
		logger.info("Response: {}", content);

		assertThat(content).containsAnyOf("30.0", "30");
		assertThat(content).containsAnyOf("10.0", "10");
		assertThat(content).containsAnyOf("15.0", "15");
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "glm-4.5-flash" })
	void enabledThinkingTest(String modelName) {
		UserMessage userMessage = new UserMessage("9.11 and 9.8, which is greater?");

		var promptOptions = ZhiPuAiChatOptions.builder()
			.model(modelName)
			.maxTokens(8192)
			.thinking(new ChatCompletionRequest.Thinking("enabled"))
			.build();

		ChatResponse response = this.chatModel.call(new Prompt(List.of(userMessage), promptOptions));
		logger.info("Response: {}", response);

		Generation generation = response.getResult();
		AssistantMessage message = generation.getOutput();

		assertThat(message).isInstanceOf(ZhiPuAiAssistantMessage.class);

		assertThat(message.getText()).isNotBlank();
		assertThat(((ZhiPuAiAssistantMessage) message).getReasoningContent()).isNotBlank();

		ZhiPuAiApi.Usage nativeUsage = (ZhiPuAiApi.Usage) response.getMetadata().getUsage().getNativeUsage();
		assertThat(nativeUsage.promptTokensDetails()).isNotNull();
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "glm-4.5-flash" })
	void disabledThinkingTest(String modelName) {
		UserMessage userMessage = new UserMessage(
				"Are there an infinite number of prime numbers such that n mod 4 == 3?");

		var promptOptions = ZhiPuAiChatOptions.builder()
			.model(modelName)
			.maxTokens(8192)
			.thinking(new ChatCompletionRequest.Thinking("disabled"))
			.build();

		ChatResponse response = this.chatModel.call(new Prompt(List.of(userMessage), promptOptions));
		logger.info("Response: {}", response);

		for (Generation generation : response.getResults()) {
			AssistantMessage message = generation.getOutput();

			assertThat(message).isInstanceOf(ZhiPuAiAssistantMessage.class);

			assertThat(message.getText()).isNotBlank();
			assertThat(((ZhiPuAiAssistantMessage) message).getReasoningContent()).isBlank();
		}
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "glm-4.5-flash" })
	void streamAndEnableThinkingTest(String modelName) {
		UserMessage userMessage = new UserMessage("9.11 and 9.8, which is greater?");

		var promptOptions = ZhiPuAiChatOptions.builder()
			.model(modelName)
			.maxTokens(8192)
			.thinking(new ChatCompletionRequest.Thinking("enabled"))
			.build();

		Flux<ChatResponse> response = this.streamingChatModel.stream(new Prompt(userMessage, promptOptions));

		StringBuilder reasoningContent = new StringBuilder();
		String content = Objects.requireNonNull(response.collectList().block())
			.stream()
			.map(ChatResponse::getResults)
			.flatMap(List::stream)
			.map(Generation::getOutput)
			.map(message -> {
				if (message instanceof ZhiPuAiAssistantMessage zhiPuAiAssistantMessage) {
					if (StringUtils.hasText(zhiPuAiAssistantMessage.getReasoningContent())) {
						reasoningContent.append(zhiPuAiAssistantMessage.getReasoningContent());
						return "";
					}
				}
				return message.getText();
			})
			.filter(StringUtils::hasText)
			.collect(Collectors.joining());

		logger.info("reasoningContent: {}", reasoningContent);
		logger.info("content: {}", content);

		// assertThat(message).isInstanceOf(ZhiPuAiAssistantMessage.class);

		assertThat(reasoningContent).isNotBlank();
		assertThat(content).isNotBlank();
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "glm-4v-flash" })
	void multiModalityEmbeddedImage(String modelName) throws IOException {

		var imageData = new ClassPathResource("/test.png");

		var userMessage = UserMessage.builder()
			.text("Explain what do you see on this picture?")
			.media(List.of(new Media(MimeTypeUtils.IMAGE_PNG, imageData)))
			.build();

		var response = this.chatModel
			.call(new Prompt(List.of(userMessage), ZhiPuAiChatOptions.builder().model(modelName).build()));

		logger.info(response.getResult().getOutput().getText());
		assertThat(response.getResult().getOutput().getText()).containsAnyOf("bananas", "apple", "bowl", "basket",
				"fruit stand");
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "glm-4.1v-thinking-flash" })
	void reasonerMultiModalityEmbeddedImageThinkingModel(String modelName) throws IOException {
		var imageData = new ClassPathResource("/test.png");

		var userMessage = UserMessage.builder()
			.text("Explain what do you see on this picture?")
			.media(List.of(new Media(MimeTypeUtils.IMAGE_PNG, imageData)))
			.build();

		var response = this.chatModel
			.call(new Prompt(List.of(userMessage), ZhiPuAiChatOptions.builder().model(modelName).build()));

		logger.info(response.getResult().getOutput().getText());
		assertThat(response.getResult().getOutput().getText()).containsAnyOf("bananas", "apple", "bowl", "basket",
				"fruit stand");

		logger.info(((ZhiPuAiAssistantMessage) response.getResult().getOutput()).getReasoningContent());
		assertThat(((ZhiPuAiAssistantMessage) response.getResult().getOutput()).getReasoningContent())
			.containsAnyOf("bananas", "apple", "bowl", "basket", "fruit stand");
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "glm-4v-flash", "glm-4.1v-thinking-flash" })
	void multiModalityImageUrl(String modelName) throws IOException {

		var userMessage = UserMessage.builder()
			.text("Explain what do you see on this picture?")
			.media(List.of(Media.builder()
				.mimeType(MimeTypeUtils.IMAGE_PNG)
				.data(URI.create("https://docs.spring.io/spring-ai/reference/_images/multimodal.test.png"))
				.build()))
			.build();

		ChatResponse response = this.chatModel
			.call(new Prompt(List.of(userMessage), ZhiPuAiChatOptions.builder().model(modelName).build()));

		logger.info(response.getResult().getOutput().getText());
		assertThat(response.getResult().getOutput().getText()).containsAnyOf("bananas", "apple", "bowl", "basket",
				"fruit stand");
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "glm-4.1v-thinking-flash" })
	void reasonerMultiModalityImageUrl(String modelName) throws IOException {

		var userMessage = UserMessage.builder()
			.text("Explain what do you see on this picture?")
			.media(List.of(Media.builder()
				.mimeType(MimeTypeUtils.IMAGE_PNG)
				.data(URI.create("https://docs.spring.io/spring-ai/reference/_images/multimodal.test.png"))
				.build()))
			.build();

		ChatResponse response = this.chatModel
			.call(new Prompt(List.of(userMessage), ZhiPuAiChatOptions.builder().model(modelName).build()));

		logger.info(response.getResult().getOutput().getText());
		assertThat(response.getResult().getOutput().getText()).containsAnyOf("bananas", "apple", "bowl", "basket",
				"fruit stand");

		logger.info(((ZhiPuAiAssistantMessage) response.getResult().getOutput()).getReasoningContent());
		assertThat(((ZhiPuAiAssistantMessage) response.getResult().getOutput()).getReasoningContent())
			.containsAnyOf("bananas", "apple", "bowl", "basket", "fruit stand");
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "glm-4v-flash" })
	void streamingMultiModalityImageUrl(String modelName) throws IOException {

		var userMessage = UserMessage.builder()
			.text("Explain what do you see on this picture?")
			.media(List.of(Media.builder()
				.mimeType(MimeTypeUtils.IMAGE_PNG)
				.data(URI.create("https://docs.spring.io/spring-ai/reference/_images/multimodal.test.png"))
				.build()))
			.build();

		Flux<ChatResponse> response = this.streamingChatModel
			.stream(new Prompt(List.of(userMessage), ZhiPuAiChatOptions.builder().model(modelName).build()));

		String content = Objects.requireNonNull(response.collectList().block())
			.stream()
			.map(ChatResponse::getResults)
			.flatMap(List::stream)
			.map(Generation::getOutput)
			.map(AssistantMessage::getText)
			.collect(Collectors.joining());
		logger.info("Response: {}", content);
		assertThat(content).containsAnyOf("bananas", "apple", "bowl", "basket", "fruit stand");
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "glm-4.1v-thinking-flash" })
	void reasonerStreamingMultiModalityImageUrl(String modelName) throws IOException {

		var userMessage = UserMessage.builder()
			.text("Explain what do you see on this picture?")
			.media(List.of(Media.builder()
				.mimeType(MimeTypeUtils.IMAGE_PNG)
				.data(URI.create("https://docs.spring.io/spring-ai/reference/_images/multimodal.test.png"))
				.build()))
			.build();

		Flux<ChatResponse> response = this.streamingChatModel
			.stream(new Prompt(List.of(userMessage), ZhiPuAiChatOptions.builder().model(modelName).build()));

		List<ZhiPuAiAssistantMessage> streamingMessages = Objects.requireNonNull(response.collectList().block())
			.stream()
			.map(ChatResponse::getResults)
			.flatMap(List::stream)
			.map(m -> (ZhiPuAiAssistantMessage) m.getOutput())
			.toList();

		String reasoningContent = streamingMessages.stream()
			.map(ZhiPuAiAssistantMessage::getReasoningContent)
			.filter(StringUtils::hasText)
			.collect(Collectors.joining());

		String content = streamingMessages.stream()
			.map(AssistantMessage::getText)
			.filter(StringUtils::hasText)
			.collect(Collectors.joining());

		logger.info("CoT: {}", reasoningContent);
		assertThat(reasoningContent).containsAnyOf("bananas", "apple", "bowl", "basket", "fruit stand");

		logger.info("Response: {}", content);
		assertThat(content).containsAnyOf("bananas", "apple", "bowl", "basket", "fruit stand");
	}

	record ActorsFilmsRecord(String actor, List<String> movies) {

	}

}
