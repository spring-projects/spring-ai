/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.bedrock.converse.experiments;

import java.util.List;

import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.regions.Region;

import org.springframework.ai.bedrock.converse.BedrockProxyChatModel;
import org.springframework.ai.bedrock.converse.MockWeatherService;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.function.FunctionToolCallback;

/**
 * Used for reverse engineering the protocol
 */
public final class BedrockConverseChatModelMain3 {

	private BedrockConverseChatModelMain3() {

	}

	public static void main(String[] args) {

		// String modelId = "anthropic.claude-3-5-sonnet-20240620-v1:0";
		// String modelId = "ai21.jamba-1-5-large-v1:0";
		String modelId = "anthropic.claude-3-5-sonnet-20240620-v1:0";

		// var prompt = new Prompt("Tell me a joke?",
		// ChatOptions.builder().model(modelId).build();
		var prompt = new Prompt(
				// "What's the weather like in San Francisco, Tokyo, and Paris? Return the
				// temperature in Celsius.",
				"What's the weather like in Paris? Return the temperature in Celsius.",
				ToolCallingChatOptions.builder()
					.model(modelId)
					.toolCallbacks(List.of(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
						.description("Get the weather in location")
						.inputType(MockWeatherService.Request.class)
						.build()))
					.build());

		BedrockProxyChatModel chatModel = BedrockProxyChatModel.builder()
			.credentialsProvider(EnvironmentVariableCredentialsProvider.create())
			.region(Region.US_EAST_1)
			.build();

		var response = chatModel.call(prompt);

		System.out.println(response);

	}

}
