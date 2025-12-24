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

package org.springframework.ai.model.openai.autoconfigure.tool;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi.ChatModel;
import org.springframework.ai.utils.SpringAiTestAutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".*")
class FunctionCallbackWithPlainFunctionBeanIT {

	private static final Logger logger = LoggerFactory.getLogger(FunctionCallbackWithPlainFunctionBeanIT.class);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.openai.apiKey=" + System.getenv("OPENAI_API_KEY"),
				"spring.ai.openai.chat.options.model=" + ChatModel.GPT_4_O_MINI.getName())
		.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiChatAutoConfiguration.class))
		.withUserConfiguration(Config.class);

	private static Map<String, Object> feedback = new ConcurrentHashMap<>();

	@BeforeEach
	void setUp() {
		feedback.clear();
	}

	@Test
	void functionCallingVoidInput() {
		this.contextRunner.run(context -> {

			OpenAiChatModel chatModel = context.getBean(OpenAiChatModel.class);

			// Test weatherFunction
			UserMessage userMessage = new UserMessage("Turn the light on in the living room");

			ChatResponse response = chatModel.call(new Prompt(List.of(userMessage),
					OpenAiChatOptions.builder().toolNames("turnLivingRoomLightOn").build()));

			logger.info("Response: {}", response);
			assertThat(feedback).hasSize(1);
			assertThat(feedback.get("turnLivingRoomLightOn")).isEqualTo(Boolean.valueOf(true));
		});
	}

	@Test
	void functionCallingSupplier() {
		this.contextRunner.run(context -> {

			OpenAiChatModel chatModel = context.getBean(OpenAiChatModel.class);

			// Test weatherFunction
			UserMessage userMessage = new UserMessage("Turn the light on in the living room");

			ChatResponse response = chatModel.call(new Prompt(List.of(userMessage),
					OpenAiChatOptions.builder().toolNames("turnLivingRoomLightOnSupplier").build()));

			logger.info("Response: {}", response);
			assertThat(feedback).hasSize(1);
			assertThat(feedback.get("turnLivingRoomLightOnSupplier")).isEqualTo(Boolean.valueOf(true));
		});
	}

	@Test
	void functionCallingVoidOutput() {
		this.contextRunner.run(context -> {

			OpenAiChatModel chatModel = context.getBean(OpenAiChatModel.class);

			// Test weatherFunction
			UserMessage userMessage = new UserMessage("Turn the light on in the kitchen and in the living room");

			ChatResponse response = chatModel
				.call(new Prompt(List.of(userMessage), OpenAiChatOptions.builder().toolNames("turnLight").build()));

			logger.info("Response: {}", response);
			assertThat(feedback).hasSize(2);
			assertThat(feedback.get("kitchen")).isEqualTo(Boolean.valueOf(true));
			assertThat(feedback.get("living room")).isEqualTo(Boolean.valueOf(true));
		});
	}

	@Test
	void functionCallingConsumer() {
		this.contextRunner.run(context -> {

			OpenAiChatModel chatModel = context.getBean(OpenAiChatModel.class);

			// Test weatherFunction
			UserMessage userMessage = new UserMessage("Turn the light on in the kitchen and in the living room");

			ChatResponse response = chatModel.call(new Prompt(List.of(userMessage),
					OpenAiChatOptions.builder().toolNames("turnLightConsumer").build()));

			logger.info("Response: {}", response);
			assertThat(feedback).hasSize(2);
			assertThat(feedback.get("kitchen")).isEqualTo(Boolean.valueOf(true));
			assertThat(feedback.get("living room")).isEqualTo(Boolean.valueOf(true));

		});
	}

	@Test
	void trainScheduler() {
		this.contextRunner.run(context -> {

			OpenAiChatModel chatModel = context.getBean(OpenAiChatModel.class);

			// Test weatherFunction
			UserMessage userMessage = new UserMessage(
					"Please schedule a train from San Francisco to Los Angeles on 2023-12-25");

			ToolCallingChatOptions functionOptions = ToolCallingChatOptions.builder()
				.toolNames("trainReservation")
				.build();

			ChatResponse response = chatModel.call(new Prompt(List.of(userMessage), functionOptions));

			logger.info("Response: {}", response.getResult().getOutput().getText());
		});
	}

	@Test
	void functionCallWithDirectBiFunction() {
		this.contextRunner.run(context -> {

			OpenAiChatModel chatModel = context.getBean(OpenAiChatModel.class);

			ChatClient chatClient = ChatClient.builder(chatModel).build();

			String content = chatClient.prompt("What's the weather like in San Francisco, Tokyo, and Paris?")
				.toolNames("weatherFunctionWithContext")
				.toolContext(Map.of("sessionId", "123"))
				.call()
				.content();
			logger.info(content);

			// Test weatherFunction
			UserMessage userMessage = new UserMessage(
					"What's the weather like in San Francisco, Tokyo, and Paris? You can call the following functions 'weatherFunction'");

			ChatResponse response = chatModel.call(new Prompt(List.of(userMessage),
					OpenAiChatOptions.builder()
						.toolNames("weatherFunctionWithContext")
						.toolContext(Map.of("sessionId", "123"))
						.build()));

			logger.info("Response: {}", response);

			assertThat(response.getResult().getOutput().getText()).contains("30", "10", "15");

		});
	}

	@Test
	void functionCallWithBiFunctionClass() {
		this.contextRunner.run(context -> {

			OpenAiChatModel chatModel = context.getBean(OpenAiChatModel.class);

			ChatClient chatClient = ChatClient.builder(chatModel).build();

			String content = chatClient.prompt("What's the weather like in San Francisco, Tokyo, and Paris?")
				.toolNames("weatherFunctionWithClassBiFunction")
				.toolContext(Map.of("sessionId", "123"))
				.call()
				.content();
			logger.info(content);

			// Test weatherFunction
			UserMessage userMessage = new UserMessage(
					"What's the weather like in San Francisco, Tokyo, and Paris? You can call the following functions 'weatherFunction'");

			ChatResponse response = chatModel.call(new Prompt(List.of(userMessage),
					OpenAiChatOptions.builder()
						.toolNames("weatherFunctionWithClassBiFunction")
						.toolContext(Map.of("sessionId", "123"))
						.build()));

			logger.info("Response: {}", response);

			assertThat(response.getResult().getOutput().getText()).contains("30", "10", "15");

		});
	}

	@Test
	void functionCallTest() {
		this.contextRunner.run(context -> {

			OpenAiChatModel chatModel = context.getBean(OpenAiChatModel.class);

			// Test weatherFunction
			UserMessage userMessage = new UserMessage(
					"What's the weather like in San Francisco, Tokyo, and Paris? You can call the following functions 'weatherFunction'");

			ChatResponse response = chatModel.call(
					new Prompt(List.of(userMessage), OpenAiChatOptions.builder().toolNames("weatherFunction").build()));

			logger.info("Response: {}", response);

			assertThat(response.getResult().getOutput().getText()).contains("30", "10", "15");

			// Test weatherFunctionTwo
			response = chatModel.call(new Prompt(List.of(userMessage),
					OpenAiChatOptions.builder().toolNames("weatherFunctionTwo").build()));

			logger.info("Response: {}", response);

			assertThat(response.getResult().getOutput().getText()).contains("30", "10", "15");

		});
	}

	@Test
	void functionCallWithPortableFunctionCallingOptions() {
		this.contextRunner.run(context -> {

			OpenAiChatModel chatModel = context.getBean(OpenAiChatModel.class);

			// Test weatherFunction
			UserMessage userMessage = new UserMessage("What's the weather like in San Francisco, Tokyo, and Paris?");

			ToolCallingChatOptions functionOptions = ToolCallingChatOptions.builder()
				.toolNames("weatherFunction")
				.build();

			ChatResponse response = chatModel.call(new Prompt(List.of(userMessage), functionOptions));

			logger.info("Response: {}", response.getResult().getOutput().getText());

			assertThat(response.getResult().getOutput().getText()).contains("30", "10", "15");
		});
	}

	@Test
	void streamFunctionCallTest() {
		this.contextRunner.run(context -> {

			OpenAiChatModel chatModel = context.getBean(OpenAiChatModel.class);

			// Test weatherFunction
			UserMessage userMessage = new UserMessage(
					"What's the weather like in San Francisco, Tokyo, and Paris? You can call the following functions 'weatherFunction'");

			Flux<ChatResponse> response = chatModel.stream(
					new Prompt(List.of(userMessage), OpenAiChatOptions.builder().toolNames("weatherFunction").build()));

			String content = response.collectList()
				.block()
				.stream()
				.map(ChatResponse::getResults)
				.flatMap(List::stream)
				.map(Generation::getOutput)
				.map(AssistantMessage::getText)
				.collect(Collectors.joining());
			logger.info("Response: {}", content);

			assertThat(content).contains("30", "10", "15");

			// Test weatherFunctionTwo
			response = chatModel.stream(new Prompt(List.of(userMessage),
					OpenAiChatOptions.builder().toolNames("weatherFunctionTwo").build()));

			content = response.collectList()
				.block()
				.stream()
				.map(ChatResponse::getResults)
				.flatMap(List::stream)
				.map(Generation::getOutput)
				.map(AssistantMessage::getText)
				.collect(Collectors.joining());
			logger.info("Response: {}", content);

			assertThat(content).withFailMessage("Content returned from OpenAI model is empty").isNotEmpty();
			assertThat(content).contains("30", "10", "15");

		});
	}

	@Configuration
	static class Config {

		@Bean
		@Description("Get the weather in location")
		public MyBiFunction weatherFunctionWithClassBiFunction() {
			return new MyBiFunction();
		}

		@Bean
		@Description("Get the weather in location")
		public BiFunction<MockWeatherService.Request, ToolContext, MockWeatherService.Response> weatherFunctionWithContext() {
			return (request, context) -> new MockWeatherService().apply(request);
		}

		@Bean
		@Description("Get the weather in location")
		public Function<MockWeatherService.Request, MockWeatherService.Response> weatherFunction() {
			return new MockWeatherService();
		}

		// Relies on the Request's JsonClassDescription annotation to provide the
		// function description.
		@Bean
		public Function<MockWeatherService.Request, MockWeatherService.Response> weatherFunctionTwo() {
			MockWeatherService weatherService = new MockWeatherService();
			return (weatherService::apply);
		}

		@Bean
		@Description("Turn light on or off in a room")
		public Function<LightInfo, Void> turnLight() {
			return (LightInfo lightInfo) -> {
				logger.info("Turning light to [" + lightInfo.isOn + "] in " + lightInfo.roomName());
				feedback.put(lightInfo.roomName(), lightInfo.isOn());
				return null;
			};
		}

		@Bean
		@Description("Turn light on or off in a room")
		public Consumer<LightInfo> turnLightConsumer() {
			return (LightInfo lightInfo) -> {
				logger.info("Turning light to [" + lightInfo.isOn + "] in " + lightInfo.roomName());
				feedback.put(lightInfo.roomName(), lightInfo.isOn());
			};
		}

		@Bean
		@Description("Turns light on in the living room")
		public Function<Void, String> turnLivingRoomLightOn() {
			return (Void v) -> {
				logger.info("Turning light on in the living room");
				feedback.put("turnLivingRoomLightOn", Boolean.TRUE);
				return "Done";
			};
		}

		@Bean
		@Description("Turns light on in the living room")
		public Supplier<String> turnLivingRoomLightOnSupplier() {
			return () -> {
				logger.info("Turning light on in the living room");
				feedback.put("turnLivingRoomLightOnSupplier", Boolean.TRUE);
				return "Done";
			};
		}

		@Bean
		@Description("Schedule a train reservation")
		public Function<TrainSearchRequest<TrainSearchSchedule>, TrainSearchResponse<TrainSearchScheduleResponse>> trainReservation() {
			return (TrainSearchRequest<TrainSearchSchedule> request) -> {
				logger.info("Turning light to [" + request.data().from() + "] in " + request.data().to());
				return new TrainSearchResponse<>(
						new TrainSearchScheduleResponse(request.data().from(), request.data().to(), "", "123"));
			};
		}

	}

	public static class MyBiFunction
			implements BiFunction<MockWeatherService.Request, ToolContext, MockWeatherService.Response> {

		@Override
		public MockWeatherService.Response apply(MockWeatherService.Request request, ToolContext context) {
			return new MockWeatherService().apply(request);
		}

	}

	record LightInfo(String roomName, boolean isOn) {
	}

	record TrainSearchSchedule(String from, String to, String date) {
	}

	record TrainSearchScheduleResponse(String from, String to, String date, String trainNumber) {
	}

	record TrainSearchRequest<T>(T data) {
	}

	record TrainSearchResponse<T>(T data) {
	}

}
