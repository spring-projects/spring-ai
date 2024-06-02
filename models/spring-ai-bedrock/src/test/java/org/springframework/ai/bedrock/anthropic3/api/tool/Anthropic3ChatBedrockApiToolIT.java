/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.bedrock.anthropic3.api.tool;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.bedrock.anthropic3.api.Anthropic3ChatBedrockApi;
import org.springframework.ai.bedrock.anthropic3.api.Anthropic3ChatBedrockApi.AnthropicChatModel;
import org.springframework.ai.bedrock.anthropic3.api.Anthropic3ChatBedrockApi.AnthropicChatRequest;
import org.springframework.ai.bedrock.anthropic3.api.Anthropic3ChatBedrockApi.AnthropicChatResponse;
import org.springframework.ai.bedrock.anthropic3.api.Anthropic3ChatBedrockApi.ChatCompletionMessage;
import org.springframework.ai.bedrock.anthropic3.api.Anthropic3ChatBedrockApi.ChatCompletionMessage.Role;
import org.springframework.ai.bedrock.anthropic3.api.Anthropic3ChatBedrockApi.MediaContent;
import org.springframework.ai.bedrock.anthropic3.api.Anthropic3ChatBedrockApi.MediaContent.Type;
import org.springframework.ai.bedrock.anthropic3.api.Anthropic3ChatBedrockApi.Tool;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.util.CollectionUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.regions.Region;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.ai.bedrock.anthropic3.api.Anthropic3ChatBedrockApi.DEFAULT_ANTHROPIC_VERSION;

/**
 * @author Wei Jiang
 * @since 1.0.0
 */
@EnabledIfEnvironmentVariable(named = "AWS_ACCESS_KEY_ID", matches = ".*")
@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".*")
@SuppressWarnings({ "rawtypes", "unchecked" })
public class Anthropic3ChatBedrockApiToolIT {

	private static final Logger logger = LoggerFactory.getLogger(Anthropic3ChatBedrockApiToolIT.class);

	private Anthropic3ChatBedrockApi anthropicChatApi = new Anthropic3ChatBedrockApi(
			AnthropicChatModel.CLAUDE_V3_SONNET.id(), EnvironmentVariableCredentialsProvider.create(),
			Region.US_EAST_1.id(), new ObjectMapper(), Duration.ofMinutes(2));

	public static final ConcurrentHashMap<String, Function> FUNCTIONS = new ConcurrentHashMap<>();

	static {
		FUNCTIONS.put("getCurrentWeather", new MockWeatherService());
	}

	List<Tool> tools = List.of(new Tool("getCurrentWeather",
			"Get the weather in location. Return temperature in 30°F or 30°C format.", ModelOptionsUtils.jsonToMap("""
					{
						"type": "object",
						"properties": {
							"location": {
								"type": "string",
								"description": "The city and state e.g. San Francisco, CA"
							},
							"unit": {
								"type": "string",
								"enum": ["C", "F"]
							}
						},
						"required": ["location", "unit"]
					}
					""")));

	@Test
	void toolCalls() {

		List<ChatCompletionMessage> messageConversation = new ArrayList<>();

		ChatCompletionMessage chatCompletionMessage = new ChatCompletionMessage(List.of(new MediaContent(
				"What's the weather like in San Francisco, Tokyo, and Paris? Show the temperature in Celsius.")),
				Role.USER);

		messageConversation.add(chatCompletionMessage);

		AnthropicChatResponse chatCompletion = doCall(messageConversation);

		var responseText = chatCompletion.content().get(0).text();
		logger.info("FINAL RESPONSE: " + responseText);

		assertThat(responseText).contains("15");
		assertThat(responseText).contains("10");
		assertThat(responseText).contains("30");
	}

	private AnthropicChatResponse doCall(List<ChatCompletionMessage> messageConversation) {

		AnthropicChatRequest chatCompletionRequest = AnthropicChatRequest.builder(messageConversation)
			.withMaxTokens(1500)
			.withTemperature(0.8f)
			.withTools(tools)
			.withAnthropicVersion(DEFAULT_ANTHROPIC_VERSION)
			.build();

		AnthropicChatResponse response = anthropicChatApi.chatCompletion(chatCompletionRequest);

		List<MediaContent> toolToUseList = response.content()
			.stream()
			.filter(c -> c.type() == MediaContent.Type.TOOL_USE)
			.toList();

		if (CollectionUtils.isEmpty(toolToUseList)) {
			return response;
		}
		// Add use tool message to the conversation history
		messageConversation.add(new ChatCompletionMessage(response.content(), Role.ASSISTANT));

		List<MediaContent> toolResults = new ArrayList<>();

		for (MediaContent toolToUse : toolToUseList) {

			var id = toolToUse.id();
			var name = toolToUse.name();
			var input = toolToUse.input();

			logger.info("FunctionCalls from the LLM: " + name);

			MockWeatherService.Request request = ModelOptionsUtils.mapToClass(input, MockWeatherService.Request.class);

			logger.info("Resolved function request param: " + request);

			Object functionCallResponseData = FUNCTIONS.get(name).apply(request);

			String content = ModelOptionsUtils.toJsonString(functionCallResponseData);

			logger.info("Function response : " + content);

			toolResults.add(new MediaContent(Type.TOOL_RESULT, id, content));
		}

		// Add function response message to the conversation history
		messageConversation.add(new ChatCompletionMessage(toolResults, Role.USER));

		return doCall(messageConversation);
	}

}
