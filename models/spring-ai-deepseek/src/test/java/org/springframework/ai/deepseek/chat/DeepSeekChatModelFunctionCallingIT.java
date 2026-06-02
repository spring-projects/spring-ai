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
import java.util.Objects;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekAssistantMessage;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.DeepSeekTestConfiguration;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.deepseek.api.MockWeatherService;
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
			.model(DeepSeekApi.ChatModel.DEEPSEEK_CHAT.getValue())
			.toolCallbacks(List.of(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
				.description("Get the weather in location")
				.inputType(MockWeatherService.Request.class)
				.build()))
			.build();

		ChatResponse response = this.chatModel.call(new Prompt(messages, promptOptions));

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

		Flux<ChatResponse> response = this.chatModel.stream(new Prompt(messages, promptOptions));

		String content = response.collectList()
			.block()
			.stream()
			.map(ChatResponse::getResults)
			.flatMap(List::stream)
			.map(Generation::getOutput)
			.map(AssistantMessage::getText)
			.filter(Objects::nonNull)
			.collect(Collectors.joining());
		logger.info("Response: {}", content);

		assertThat(content).contains("30", "10", "15");
	}

	@Test
	public void toolFunctionCallWithUsage() {
		var promptOptions = DeepSeekChatOptions.builder()
			.model(DeepSeekApi.ChatModel.DEEPSEEK_CHAT.getValue())
			.tools(Arrays.asList(FUNCTION_TOOL))
			.toolCallbacks(List.of(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
				.description("Get the weather in location")
				.inputType(MockWeatherService.Request.class)
				.build()))
			.build();
		Prompt prompt = new Prompt("What's the weather like in San Francisco? Return the temperature in Celsius.",
				promptOptions);

		ChatResponse chatResponse = this.chatModel.call(prompt);
		assertThat(chatResponse).isNotNull();
		assertThat(chatResponse.getResult().getOutput()).isNotNull();
		assertThat(chatResponse.getResult().getOutput().getText()).contains("San Francisco");
		assertThat(chatResponse.getResult().getOutput().getText()).contains("30");
		assertThat(chatResponse.getMetadata().getUsage()).isNotNull();
	}

	@Test
	public void testStreamFunctionCallUsage() {
		var promptOptions = DeepSeekChatOptions.builder()
			.model(DeepSeekApi.ChatModel.DEEPSEEK_CHAT.getValue())
			.tools(Arrays.asList(FUNCTION_TOOL))
			.toolCallbacks(List.of(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
				.description("Get the weather in location")
				.inputType(MockWeatherService.Request.class)
				.build()))
			.build();
		Prompt prompt = new Prompt("What's the weather like in San Francisco? Return the temperature in Celsius.",
				promptOptions);

		ChatResponse chatResponse = this.chatModel.stream(prompt).blockLast();
		assertThat(chatResponse).isNotNull();
		assertThat(chatResponse.getMetadata()).isNotNull();
		assertThat(chatResponse.getMetadata().getUsage()).isNotNull();
	}

	/**
	 * Multi-round conversation with function calls, preserving the assistant message
	 * (including any reasoningContent) across rounds. This verifies that
	 * {@link DeepSeekAssistantMessage} with tool calls can be passed back to subsequent
	 * requests without losing reasoningContent.
	 */
	@Test
	void functionCallMultiRoundWithReasoningContentPreserved() {

		UserMessage userMessage = new UserMessage(
				"What's the weather like in San Francisco, Tokyo, and Paris? Return the temperature in Celsius.");

		List<Message> messages = new ArrayList<>(List.of(userMessage));

		var promptOptions = DeepSeekChatOptions.builder()
				.model(DeepSeekApi.ChatModel.DEEPSEEK_REASONER.getValue())
				.toolCallbacks(List.of(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
						.description("Get the weather in location")
						.inputType(MockWeatherService.Request.class)
						.build()))
				.build();

		ChatResponse response = this.chatModel.call(new Prompt(messages, promptOptions));

		assertThat(response.getResult().getOutput().getText()).contains("30", "10", "15");

		// This test must exercise the reasoner + tool-calling scenario.
		// In this scenario, DeepSeek returns reasoning content on the assistant message.
		AssistantMessage firstAssistantMessage = response.getResult().getOutput();

		assertThat(firstAssistantMessage)
				.as("The first assistant message should be a DeepSeek assistant message")
				.isInstanceOf(DeepSeekAssistantMessage.class);

		DeepSeekAssistantMessage firstDeepSeekAssistantMessage = (DeepSeekAssistantMessage) firstAssistantMessage;

		assertThat(firstDeepSeekAssistantMessage.getReasoningContent())
				.as("The first assistant message should contain reasoning content")
				.isNotBlank();

		// Add the previous assistant message back to the conversation history.
		// Since this message was produced by a reasoner model in a tool-calling round,
		// its reasoning content must be sent back in all subsequent requests.
		messages.add(firstAssistantMessage);
		messages.add(new UserMessage("Which of these cities has the highest temperature?"));

		// The second request is the actual regression check.
		//
		// Without the fix, Spring AI drops the reasoning content when building this
		// follow-up request. DeepSeek then rejects the request with HTTP 400.
		//
		// With the fix applied, the reasoning content is preserved in the follow-up
		// request, so the API accepts it and the second round succeeds.
		ChatResponse response2 = this.chatModel.call(new Prompt(messages, promptOptions));

		assertThat(response2.getResult().getOutput().getText())
				.as("The second round should succeed when the previous reasoning content is preserved")
				.isNotBlank();
	}

}
