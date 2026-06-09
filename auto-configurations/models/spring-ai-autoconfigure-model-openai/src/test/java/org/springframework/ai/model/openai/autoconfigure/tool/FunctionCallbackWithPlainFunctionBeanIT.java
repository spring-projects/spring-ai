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

package org.springframework.ai.model.openai.autoconfigure.tool;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ParameterizedTypeReference;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".*")
class FunctionCallbackWithPlainFunctionBeanIT {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.openai.api-key=" + System.getenv("OPENAI_API_KEY"),
				"spring.ai.openai.chat.model=" + "gpt-4o-mini")
		.withConfiguration(AutoConfigurations.of(OpenAiChatAutoConfiguration.class, ToolCallingAutoConfiguration.class))
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
			ToolCallingManager toolCallingManager = context.getBean(ToolCallingManager.class);
			ToolCallback turnLivingRoomLightOn = context.getBean("turnLivingRoomLightOn", ToolCallback.class);

			var chatClient = ChatClient
				.builder(chatModel, ObservationRegistry.NOOP, null, null,
						ToolCallingAdvisor.builder().toolCallingManager(toolCallingManager))
				.build();

			UserMessage userMessage = new UserMessage("Turn the light on in the living room");

			OpenAiChatOptions options = OpenAiChatOptions.builder().toolCallbacks(turnLivingRoomLightOn).build();

			Prompt prompt = new Prompt(List.of(userMessage), options);

			ChatResponse response = chatClient.prompt(prompt).call().chatResponse();
			assertThat(feedback).hasSize(1);
			assertThat(feedback.get("turnLivingRoomLightOn")).isEqualTo(Boolean.valueOf(true));
		});
	}

	@Test
	void functionCallingSupplier() {
		this.contextRunner.run(context -> {

			OpenAiChatModel chatModel = context.getBean(OpenAiChatModel.class);
			ToolCallingManager toolCallingManager = context.getBean(ToolCallingManager.class);
			ToolCallback turnLivingRoomLightOnSupplier = context.getBean("turnLivingRoomLightOnSupplier",
					ToolCallback.class);

			var chatClient = ChatClient
				.builder(chatModel, ObservationRegistry.NOOP, null, null,
						ToolCallingAdvisor.builder().toolCallingManager(toolCallingManager))
				.build();

			UserMessage userMessage = new UserMessage("Turn the light on in the living room");

			OpenAiChatOptions options = OpenAiChatOptions.builder()
				.toolCallbacks(turnLivingRoomLightOnSupplier)
				.build();

			Prompt prompt = new Prompt(List.of(userMessage), options);

			ChatResponse response = chatClient.prompt(prompt).call().chatResponse();
			assertThat(feedback).hasSize(1);
			assertThat(feedback.get("turnLivingRoomLightOnSupplier")).isEqualTo(Boolean.valueOf(true));
		});
	}

	@Test
	void functionCallingVoidOutput() {
		this.contextRunner.run(context -> {

			OpenAiChatModel chatModel = context.getBean(OpenAiChatModel.class);
			ToolCallingManager toolCallingManager = context.getBean(ToolCallingManager.class);
			ToolCallback turnLight = context.getBean("turnLight", ToolCallback.class);

			var chatClient = ChatClient
				.builder(chatModel, ObservationRegistry.NOOP, null, null,
						ToolCallingAdvisor.builder().toolCallingManager(toolCallingManager))
				.build();

			UserMessage userMessage = new UserMessage("Turn the light on in the kitchen and in the living room");

			OpenAiChatOptions options = OpenAiChatOptions.builder().toolCallbacks(turnLight).build();

			Prompt prompt = new Prompt(List.of(userMessage), options);

			ChatResponse response = chatClient.prompt(prompt).call().chatResponse();
			assertThat(feedback).hasSize(2);
			assertThat(feedback.get("kitchen")).isEqualTo(Boolean.valueOf(true));
			assertThat(feedback.get("living room")).isEqualTo(Boolean.valueOf(true));
		});
	}

	@Test
	void functionCallingConsumer() {
		this.contextRunner.run(context -> {

			OpenAiChatModel chatModel = context.getBean(OpenAiChatModel.class);
			ToolCallingManager toolCallingManager = context.getBean(ToolCallingManager.class);
			ToolCallback turnLightConsumer = context.getBean("turnLightConsumer", ToolCallback.class);

			var chatClient = ChatClient
				.builder(chatModel, ObservationRegistry.NOOP, null, null,
						ToolCallingAdvisor.builder().toolCallingManager(toolCallingManager))
				.build();

			UserMessage userMessage = new UserMessage("Turn the light on in the kitchen and in the living room");

			OpenAiChatOptions options = OpenAiChatOptions.builder().toolCallbacks(turnLightConsumer).build();

			Prompt prompt = new Prompt(List.of(userMessage), options);

			ChatResponse response = chatClient.prompt(prompt).call().chatResponse();
			assertThat(feedback).hasSize(2);
			assertThat(feedback.get("kitchen")).isEqualTo(Boolean.valueOf(true));
			assertThat(feedback.get("living room")).isEqualTo(Boolean.valueOf(true));
		});
	}

	@Test
	void trainScheduler() {
		this.contextRunner.run(context -> {

			OpenAiChatModel chatModel = context.getBean(OpenAiChatModel.class);
			ToolCallingManager toolCallingManager = context.getBean(ToolCallingManager.class);
			ToolCallback trainReservation = context.getBean("trainReservation", ToolCallback.class);

			UserMessage userMessage = new UserMessage(
					"Please schedule a train from San Francisco to Los Angeles on 2023-12-25");

			OpenAiChatOptions functionOptions = OpenAiChatOptions.builder().toolCallbacks(trainReservation).build();

			var chatClient = ChatClient
				.builder(chatModel, ObservationRegistry.NOOP, null, null,
						ToolCallingAdvisor.builder().toolCallingManager(toolCallingManager))
				.build();

			Prompt prompt = new Prompt(List.of(userMessage), functionOptions);

			ChatResponse response = chatClient.prompt(prompt).call().chatResponse();
		});
	}

	@Test
	void functionCallWithDirectBiFunction() {
		this.contextRunner.run(context -> {

			OpenAiChatModel chatModel = context.getBean(OpenAiChatModel.class);
			ToolCallingManager toolCallingManager = context.getBean(ToolCallingManager.class);
			ToolCallback weatherFunctionWithContext = context.getBean("weatherFunctionWithContext", ToolCallback.class);

			ChatClient chatClient = ChatClient.builder(chatModel)
				.defaultAdvisors(ToolCallingAdvisor.builder().toolCallingManager(toolCallingManager).build())
				.build();

			String content = chatClient.prompt(
					"What's the weather like in San Francisco, Tokyo, and Paris? Please use the provided tools to get the weather for all 3 cities.")
				.tools(weatherFunctionWithContext)
				.toolContext(Map.of("sessionId", "123"))
				.call()
				.content();

			UserMessage userMessage = new UserMessage(
					"What's the weather like in San Francisco, Tokyo, and Paris? Please use the provided tools to get the weather for all 3 cities. You can call the following functions 'weatherFunction'");

			OpenAiChatOptions options = OpenAiChatOptions.builder().toolCallbacks(weatherFunctionWithContext).build();

			Prompt prompt = new Prompt(List.of(userMessage), options);

			ChatResponse response = chatModel.call(prompt);

			while (response.hasToolCalls()) {
				ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, response);
				prompt = new Prompt(toolExecutionResult.conversationHistory(), options);
				response = chatModel.call(prompt);
			}

			assertThat(response.getResult().getOutput().getText()).contains("30", "10", "15");

		});
	}

	@Test
	void functionCallWithBiFunctionClass() {
		this.contextRunner.run(context -> {

			OpenAiChatModel chatModel = context.getBean(OpenAiChatModel.class);
			ToolCallingManager toolCallingManager = context.getBean(ToolCallingManager.class);
			ToolCallback weatherFunctionWithClassBiFunction = context.getBean("weatherFunctionWithClassBiFunction",
					ToolCallback.class);

			ChatClient chatClient = ChatClient.builder(chatModel)
				.defaultAdvisors(ToolCallingAdvisor.builder().toolCallingManager(toolCallingManager).build())
				.build();

			String content = chatClient.prompt(
					"What's the weather like in San Francisco, Tokyo, and Paris? Please use the provided tools to get the weather for all 3 cities.")
				.tools(weatherFunctionWithClassBiFunction)
				.toolContext(Map.of("sessionId", "123"))
				.call()
				.content();

			UserMessage userMessage = new UserMessage(
					"What's the weather like in San Francisco, Tokyo, and Paris? Please use the provided tools to get the weather for all 3 cities. You can call the following functions 'weatherFunction'");

			OpenAiChatOptions options = OpenAiChatOptions.builder()
				.toolCallbacks(weatherFunctionWithClassBiFunction)
				.build();

			Prompt prompt = new Prompt(List.of(userMessage), options);

			ChatResponse response = chatModel.call(prompt);

			while (response.hasToolCalls()) {
				ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, response);
				prompt = new Prompt(toolExecutionResult.conversationHistory(), options);
				response = chatModel.call(prompt);
			}

			assertThat(response.getResult().getOutput().getText()).contains("30", "10", "15");

		});
	}

	@Test
	void functionCallTest() {
		this.contextRunner.run(context -> {

			OpenAiChatModel chatModel = context.getBean(OpenAiChatModel.class);
			ToolCallingManager toolCallingManager = context.getBean(ToolCallingManager.class);
			ToolCallback weatherFunction = context.getBean("weatherFunction", ToolCallback.class);
			ToolCallback weatherFunctionTwo = context.getBean("weatherFunctionTwo", ToolCallback.class);

			var chatClient = ChatClient
				.builder(chatModel, ObservationRegistry.NOOP, null, null,
						ToolCallingAdvisor.builder().toolCallingManager(toolCallingManager))
				.build();

			UserMessage userMessage = new UserMessage(
					"What's the weather like in San Francisco, Tokyo, and Paris? Please use the provided tools to get the weather for all 3 cities. You can call the following functions 'weatherFunction'");

			OpenAiChatOptions options = OpenAiChatOptions.builder().toolCallbacks(weatherFunction).build();

			Prompt prompt = new Prompt(List.of(userMessage), options);

			ChatResponse response = chatClient.prompt(prompt).call().chatResponse();

			assertThat(response.getResult().getOutput().getText()).contains("30", "10", "15");

			OpenAiChatOptions optionsTwo = OpenAiChatOptions.builder().toolCallbacks(weatherFunctionTwo).build();

			Prompt promptTwo = new Prompt(List.of(userMessage), optionsTwo);

			response = chatClient.prompt(promptTwo).call().chatResponse();

			assertThat(response.getResult().getOutput().getText()).contains("30", "10", "15");

		});
	}

	@Test
	void functionCallWithPortableFunctionCallingOptions() {
		this.contextRunner.run(context -> {

			OpenAiChatModel chatModel = context.getBean(OpenAiChatModel.class);
			ToolCallingManager toolCallingManager = context.getBean(ToolCallingManager.class);
			ToolCallback weatherFunction = context.getBean("weatherFunction", ToolCallback.class);

			var chatClient = ChatClient
				.builder(chatModel, ObservationRegistry.NOOP, null, null,
						ToolCallingAdvisor.builder().toolCallingManager(toolCallingManager))
				.build();

			UserMessage userMessage = new UserMessage(
					"What's the weather like in San Francisco, Tokyo, and Paris? Please use the provided tools to get the weather for all 3 cities.");

			OpenAiChatOptions functionOptions = OpenAiChatOptions.builder().toolCallbacks(weatherFunction).build();

			Prompt prompt = new Prompt(List.of(userMessage), functionOptions);

			ChatResponse response = chatClient.prompt(prompt).call().chatResponse();

			assertThat(response.getResult().getOutput().getText()).contains("30", "10", "15");
		});
	}

	@Test
	void streamFunctionCallTest() {
		this.contextRunner.run(context -> {

			OpenAiChatModel chatModel = context.getBean(OpenAiChatModel.class);
			ToolCallingManager toolCallingManager = context.getBean(ToolCallingManager.class);
			ToolCallback weatherFunction = context.getBean("weatherFunction", ToolCallback.class);
			ToolCallback weatherFunctionTwo = context.getBean("weatherFunctionTwo", ToolCallback.class);

			var chatClient = ChatClient
				.builder(chatModel, ObservationRegistry.NOOP, null, null,
						ToolCallingAdvisor.builder().toolCallingManager(toolCallingManager))
				.build();

			UserMessage userMessage = new UserMessage(
					"What's the weather like in San Francisco, Tokyo, and Paris? Please use the provided tools to get the weather for all 3 cities. You can call the following functions 'weatherFunction'");

			OpenAiChatOptions options = OpenAiChatOptions.builder().toolCallbacks(weatherFunction).build();

			Prompt prompt = new Prompt(List.of(userMessage), options);

			AtomicReference<ChatResponse> aggregatedRef = new AtomicReference<>();
			new MessageAggregator().aggregate(chatClient.prompt(prompt).stream().chatResponse(), aggregatedRef::set)
				.collectList()
				.block();

			String content = aggregatedRef.get().getResult().getOutput().getText();

			assertThat(content).contains("30", "10", "15");

			OpenAiChatOptions optionsTwo = OpenAiChatOptions.builder().toolCallbacks(weatherFunctionTwo).build();

			Prompt promptTwo = new Prompt(List.of(userMessage), optionsTwo);

			AtomicReference<ChatResponse> aggregatedRefTwo = new AtomicReference<>();
			new MessageAggregator()
				.aggregate(chatClient.prompt(promptTwo).stream().chatResponse(), aggregatedRefTwo::set)
				.collectList()
				.block();

			content = aggregatedRefTwo.get().getResult().getOutput().getText();

			assertThat(content).isNotEmpty().withFailMessage("Content returned from OpenAI model is empty");
			assertThat(content).contains("30", "10", "15");

		});
	}

	@Configuration
	static class Config {

		@Bean
		ToolCallback weatherFunctionWithClassBiFunction() {
			return FunctionToolCallback.builder("weatherFunctionWithClassBiFunction", new MyBiFunction())
				.description("Get the weather in location")
				.inputType(MockWeatherService.Request.class)
				.build();
		}

		@Bean
		ToolCallback weatherFunctionWithContext() {
			return FunctionToolCallback
				.builder("weatherFunctionWithContext",
						(MockWeatherService.Request request, ToolContext tc) -> new MockWeatherService().apply(request))
				.description("Get the weather in location")
				.inputType(MockWeatherService.Request.class)
				.build();
		}

		@Bean
		ToolCallback weatherFunction() {
			return FunctionToolCallback.builder("weatherFunction", new MockWeatherService())
				.description("Get the weather in location")
				.inputType(MockWeatherService.Request.class)
				.build();
		}

		@Bean
		ToolCallback weatherFunctionTwo() {
			MockWeatherService weatherService = new MockWeatherService();
			return FunctionToolCallback.builder("weatherFunctionTwo", weatherService::apply)
				.description("Get the weather in location")
				.inputType(MockWeatherService.Request.class)
				.build();
		}

		@Bean
		ToolCallback turnLight() {
			return FunctionToolCallback.builder("turnLight", (LightInfo lightInfo) -> {
				feedback.put(lightInfo.roomName(), lightInfo.isOn());
				return null;
			}).description("Turn light on or off in a room").inputType(LightInfo.class).build();
		}

		@Bean
		ToolCallback turnLightConsumer() {
			return FunctionToolCallback
				.builder("turnLightConsumer",
						(LightInfo lightInfo) -> feedback.put(lightInfo.roomName(), lightInfo.isOn()))
				.description("Turn light on or off in a room")
				.inputType(LightInfo.class)
				.build();
		}

		@Bean
		ToolCallback turnLivingRoomLightOn() {
			return FunctionToolCallback.builder("turnLivingRoomLightOn", () -> {
				feedback.put("turnLivingRoomLightOn", Boolean.TRUE);
				return "Done";
			}).description("Turns light on in the living room").build();
		}

		@Bean
		ToolCallback turnLivingRoomLightOnSupplier() {
			return FunctionToolCallback.builder("turnLivingRoomLightOnSupplier", () -> {
				feedback.put("turnLivingRoomLightOnSupplier", Boolean.TRUE);
				return "Done";
			}).description("Turns light on in the living room").build();
		}

		@Bean
		ToolCallback trainReservation() {
			return FunctionToolCallback
				.builder("trainReservation",
						(TrainSearchRequest<TrainSearchSchedule> request) -> new TrainSearchResponse<>(
								new TrainSearchScheduleResponse(request.data().from(), request.data().to(), "", "123")))
				.description("Schedule a train reservation")
				.inputType(new ParameterizedTypeReference<TrainSearchRequest<TrainSearchSchedule>>() {
				})
				.build();
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
