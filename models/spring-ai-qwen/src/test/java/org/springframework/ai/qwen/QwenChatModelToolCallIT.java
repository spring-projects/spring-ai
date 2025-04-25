package org.springframework.ai.qwen;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.qwen.api.QwenApi;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = QwenChatModelToolCallIT.TestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
public class QwenChatModelToolCallIT {

	private static final Logger logger = LoggerFactory.getLogger(QwenChatModelIT.class);

	@Autowired
	private QwenChatModel chatModel;

	private final MockWeatherService weatherService = new MockWeatherService();

	@Test
	void functionCallTest() {

		UserMessage userMessage = new UserMessage("What's the weather like in San Francisco, in Tokyo, and in Paris?");

		List<Message> messages = new ArrayList<>(List.of(userMessage));

		var promptOptions = QwenChatOptions.builder()
			.toolCallbacks(List.of(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
				.description("Get the current weather in a given location")
				.inputType(MockWeatherService.Request.class)
				.build()))
			.build();

		ChatResponse response = this.chatModel.call(new Prompt(messages, promptOptions));

		logger.info("Response: {}", response);

		assertThat(response.getResult()).isNotNull();
		assertThat(response.getResult().getOutput()).isNotNull();
		assertThat(response.getResult().getOutput().getText()).contains("30", "10", "15");
		assertThat(response.getMetadata()).isNotNull();
		assertThat(response.getMetadata().getUsage()).isNotNull();
		assertThat(response.getMetadata().getUsage().getTotalTokens()).isGreaterThan(600);
	}

	@Test
	void functionCallSequentialTest() {

		UserMessage userMessage = new UserMessage(
				"What's the weather like in San Francisco? If the weather is above 25 degrees, please check the weather in Tokyo and Paris.");

		List<Message> messages = new ArrayList<>(List.of(userMessage));

		var promptOptions = QwenChatOptions.builder()
			.toolCallbacks(List.of(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
				.description("Get the current weather in a given location")
				.inputType(MockWeatherService.Request.class)
				.build()))
			.build();

		ChatResponse response = this.chatModel.call(new Prompt(messages, promptOptions));

		logger.info("Response: {}", response);

		assertThat(response.getResult().getOutput().getText()).contains("30", "10", "15");
	}

	@Test
	void streamFunctionCallTest() {
		UserMessage userMessage = new UserMessage("What's the weather like in San Francisco, Tokyo, and Paris?");

		List<Message> messages = new ArrayList<>(List.of(userMessage));

		var promptOptions = QwenChatOptions.builder()
			.toolCallbacks(List.of(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
				.description("Get the current weather in a given location")
				.inputType(MockWeatherService.Request.class)
				.build()))
			.build();

		Flux<ChatResponse> response = this.chatModel.stream(new Prompt(messages, promptOptions));

		final var counter = new AtomicInteger();
		String content = response.doOnEach(listSignal -> counter.getAndIncrement())
			.collectList()
			.block()
			.stream()
			.map(ChatResponse::getResults)
			.flatMap(List::stream)
			.map(Generation::getOutput)
			.map(AssistantMessage::getText)
			.collect(Collectors.joining());
		logger.info("Response: {}", content);

		assertThat(counter.get()).isGreaterThan(30).as("The response should be chunked in more than 30 messages");

		assertThat(content).contains("30", "10", "15");

	}

	@Test
	void streamFunctionCallUsageTest() {
		UserMessage userMessage = new UserMessage("What's the weather like in San Francisco, Tokyo, and Paris?");

		List<Message> messages = new ArrayList<>(List.of(userMessage));

		var promptOptions = QwenChatOptions.builder()
			.toolCallbacks(List.of(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
				.description("Get the current weather in a given location")
				.inputType(MockWeatherService.Request.class)
				.build()))
			.build();

		Flux<ChatResponse> response = this.chatModel.stream(new Prompt(messages, promptOptions));

		ChatResponse chatResponse = response.last().block();
		logger.info("Response: {}", chatResponse);

		assertThat(chatResponse.getMetadata().getUsage().getTotalTokens()).isGreaterThan(600);

	}

	@Test
	void functionCallSequentialAndStreamTest() {

		UserMessage userMessage = new UserMessage(
				"What's the weather like in San Francisco? If the weather is above 25 degrees, please check the weather in Tokyo and Paris.");

		List<Message> messages = new ArrayList<>(List.of(userMessage));

		var promptOptions = QwenChatOptions.builder()
			.toolCallbacks(List.of(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
				.description("Get the current weather in a given location")
				.inputType(MockWeatherService.Request.class)
				.build()))
			.build();

		var response = this.chatModel.stream(new Prompt(messages, promptOptions));

		final var counter = new AtomicInteger();
		String content = response.doOnEach(listSignal -> counter.getAndIncrement())
			.collectList()
			.block()
			.stream()
			.map(ChatResponse::getResults)
			.flatMap(List::stream)
			.map(Generation::getOutput)
			.map(AssistantMessage::getText)
			.filter(Objects::nonNull)
			.collect(Collectors.joining());

		logger.info("Response: {}", response);

		assertThat(content).contains("30", "10", "15");
	}

	@SpringBootConfiguration
	public static class TestConfiguration {

		@Bean
		public QwenApi qwenApi() {
			return QwenApi.builder().apiKey(System.getenv("DASHSCOPE_API_KEY")).build();
		}

		@Bean
		public QwenChatModel qwenChatModel(QwenApi qwenApi) {
			return QwenChatModel.builder().qwenApi(qwenApi).build();
		}

	}

}
