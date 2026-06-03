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

package org.springframework.ai.deepseek.chat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekAssistantMessage;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.DeepSeekTestConfiguration;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.deepseek.api.MockWeatherService;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Geng Rong
 */
@SpringBootTest(classes = DeepSeekTestConfiguration.class)
// @Disabled("the deepseek-chat model's Function Calling capability is unstable see:
// https://api-docs.deepseek.com/guides/function_calling")
@EnabledIfEnvironmentVariable(named = "DEEPSEEK_API_KEY", matches = ".+")
class DeepSeekChatModelFunctionCallingIT {

	private static final Logger logger = LoggerFactory.getLogger(DeepSeekChatModelFunctionCallingIT.class);

	@Autowired
	ChatModel chatModel;

	private static final DeepSeekApi.FunctionTool FUNCTION_TOOL = new DeepSeekApi.FunctionTool(
			DeepSeekApi.FunctionTool.Type.FUNCTION, new DeepSeekApi.FunctionTool.Function(
					"Get the weather in location. Return temperature in 30°F or 30°C format.", "getCurrentWeather", """
							{
								"type": "object",
								"properties": {
									"location": {
										"type": "string",
										"description": "The city and state e.g. San Francisco, CA"
									},
									"lat": {
										"type": "number",
										"description": "The city latitude"
									},
									"lon": {
										"type": "number",
										"description": "The city longitude"
									},
									"unit": {
										"type": "string",
										"enum": ["C", "F"]
									}
								},
								"required": ["location", "lat", "lon", "unit"]
							}
							"""));

	@Test
	void functionCallTest() {

		UserMessage userMessage = new UserMessage(
				"What's the weather like in San Francisco, Tokyo, and Paris? Return the temperature in Celsius.");

		List<Message> messages = new ArrayList<>(List.of(userMessage));

		var promptOptions = DeepSeekChatOptions.builder()
			.toolCallbacks(List.of(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
				.description("Get the weather in location")
				.inputType(MockWeatherService.Request.class)
				.build()))
			.build();

		ToolCallingManager toolCallingManager = DefaultToolCallingManager.builder().build();

		Prompt prompt = new Prompt(messages, promptOptions);

		ChatResponse response = this.chatModel.call(prompt);

		while (response.hasToolCalls()) {
			ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, response);
			prompt = new Prompt(toolExecutionResult.conversationHistory(), promptOptions);
			response = this.chatModel.call(prompt);
		}

		logger.info("Response: {}", response);

		assertThat(response.getResult().getOutput().getText()).contains("30", "10", "15");
	}

	@Test
	void streamFunctionCallTest() {

		UserMessage userMessage = new UserMessage(
				"What's the weather like in San Francisco, Tokyo, and Paris? Return the temperature in Celsius.");

		List<Message> messages = new ArrayList<>(List.of(userMessage));

		var promptOptions = DeepSeekChatOptions.builder()
			.toolCallbacks(List.of(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
				.description("Get the weather in location")
				.inputType(MockWeatherService.Request.class)
				.build()))
			.build();

		ToolCallingManager toolCallingManager = DefaultToolCallingManager.builder().build();

		Prompt prompt = new Prompt(messages, promptOptions);

		AtomicReference<ChatResponse> aggregatedRef = new AtomicReference<>();
		new MessageAggregator().aggregate(this.chatModel.stream(prompt), aggregatedRef::set).collectList().block();

		while (aggregatedRef.get().hasToolCalls()) {
			ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, aggregatedRef.get());
			prompt = new Prompt(toolExecutionResult.conversationHistory(), promptOptions);
			aggregatedRef.set(null);
			new MessageAggregator().aggregate(this.chatModel.stream(prompt), aggregatedRef::set).collectList().block();
		}

		String content = aggregatedRef.get().getResult().getOutput().getText();
		logger.info("Response: {}", content);

		assertThat(content).contains("30", "10", "15");
	}

	@Test
	public void toolFunctionCallWithUsage() {
		var promptOptions = DeepSeekChatOptions.builder()
			.tools(Arrays.asList(FUNCTION_TOOL))
			.toolCallbacks(List.of(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
				.description("Get the weather in location")
				.inputType(MockWeatherService.Request.class)
				.build()))
			.build();

		ToolCallingManager toolCallingManager = DefaultToolCallingManager.builder().build();

		Prompt prompt = new Prompt("What's the weather like in San Francisco? Return the temperature in Celsius.",
				promptOptions);

		ChatResponse chatResponse = this.chatModel.call(prompt);

		while (chatResponse.hasToolCalls()) {
			ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, chatResponse);
			prompt = new Prompt(toolExecutionResult.conversationHistory(), promptOptions);
			chatResponse = this.chatModel.call(prompt);
		}

		assertThat(chatResponse).isNotNull();
		assertThat(chatResponse.getResult().getOutput()).isNotNull();
		assertThat(chatResponse.getResult().getOutput().getText()).contains("San Francisco");
		assertThat(chatResponse.getResult().getOutput().getText()).contains("30");
		assertThat(chatResponse.getMetadata().getUsage()).isNotNull();
	}

	@Test
	public void testStreamFunctionCallUsage() {
		var promptOptions = DeepSeekChatOptions.builder()
			.tools(Arrays.asList(FUNCTION_TOOL))
			.toolCallbacks(List.of(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
				.description("Get the weather in location")
				.inputType(MockWeatherService.Request.class)
				.build()))
			.build();

		ToolCallingManager toolCallingManager = DefaultToolCallingManager.builder().build();

		Prompt prompt = new Prompt("What's the weather like in San Francisco? Return the temperature in Celsius.",
				promptOptions);

		AtomicReference<ChatResponse> aggregatedRef = new AtomicReference<>();
		new MessageAggregator().aggregate(this.chatModel.stream(prompt), aggregatedRef::set).collectList().block();

		while (aggregatedRef.get().hasToolCalls()) {
			ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, aggregatedRef.get());
			prompt = new Prompt(toolExecutionResult.conversationHistory(), promptOptions);
			aggregatedRef.set(null);
			new MessageAggregator().aggregate(this.chatModel.stream(prompt), aggregatedRef::set).collectList().block();
		}

		ChatResponse chatResponse = aggregatedRef.get();
		assertThat(chatResponse).isNotNull();
		assertThat(chatResponse.getMetadata()).isNotNull();
		assertThat(chatResponse.getMetadata().getUsage()).isNotNull();
	}

	@Test
	void reasonerFunctionCallTest() {
		UserMessage userMessage = new UserMessage(
				"What's the weather like in San Francisco? Return the temperature in Celsius.");
		List<Message> messages = new ArrayList<>(List.of(userMessage));

		var promptOptions = DeepSeekChatOptions.builder()
			.model(DeepSeekApi.ChatModel.DEEPSEEK_V4_PRO)
			.toolCallbacks(List.of(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
				.description("Get the weather in location")
				.inputType(MockWeatherService.Request.class)
				.build()))
			.build();

		ToolCallingManager toolCallingManager = DefaultToolCallingManager.builder().build();

		Prompt prompt = new Prompt(messages, promptOptions);

		ChatResponse response = this.chatModel.call(prompt);

		while (response.hasToolCalls()) {
			ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, response);
			prompt = new Prompt(toolExecutionResult.conversationHistory(), promptOptions);
			response = this.chatModel.call(prompt);
		}

		logger.info("Response: {}", response);

		assertThat(response).isNotNull();
		assertThat(response.getResult()).isNotNull();
		assertThat(response.getResult().getOutput()).isNotNull();
		assertThat(response.getResult().getOutput()).isInstanceOf(DeepSeekAssistantMessage.class);
		DeepSeekAssistantMessage assistantMessage = (DeepSeekAssistantMessage) response.getResult().getOutput();
		assertThat(assistantMessage.getReasoningContent()).isNotEmpty();
		assertThat(assistantMessage.getText()).isNotEmpty();
		assertThat(assistantMessage.getText()).contains("30");
	}

}
