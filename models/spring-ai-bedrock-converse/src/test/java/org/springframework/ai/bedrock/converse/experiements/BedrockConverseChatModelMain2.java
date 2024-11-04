/*
* Copyright 2024 - 2024 the original author or authors.
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
package org.springframework.ai.bedrock.converse.experiements;

import java.util.List;

import org.springframework.ai.bedrock.converse.BedrockProxyChatModel;
import org.springframework.ai.bedrock.converse.MockWeatherService;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.function.FunctionCallbackWrapper;
import org.springframework.ai.model.function.FunctionCallingOptionsBuilder.PortableFunctionCallingOptions;

import reactor.core.publisher.Flux;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamOutput;

/**
 * Used for reverse engineering the protocol
 */
public class BedrockConverseChatModelMain2 {

	public static void main(String[] args) {

		// String modelId = "anthropic.claude-3-5-sonnet-20240620-v1:0";
		// String modelId = "ai21.jamba-1-5-large-v1:0";
		String modelId = "anthropic.claude-3-5-sonnet-20240620-v1:0";

		// var prompt = new Prompt("Tell me a joke?",
		// ChatOptionsBuilder.builder().withModel(modelId).build());
		var prompt = new Prompt(
				// "What's the weather like in San Francisco, Tokyo, and Paris? Return the
				// temperature in Celsius.",
				"What's the weather like in Paris? Return the temperature in Celsius.",
				PortableFunctionCallingOptions.builder()
					.withModel(modelId)
					.withFunctionCallbacks(List.of(FunctionCallbackWrapper.builder(new MockWeatherService())
						.withName("getCurrentWeather")
						.withDescription("Get the weather in location")
						.build()))
					.build());

		BedrockProxyChatModel chatModel = BedrockProxyChatModel.builder()
			.withCredentialsProvider(EnvironmentVariableCredentialsProvider.create())
			.withRegion(Region.US_EAST_1)
			.build();

		var streamRequest = chatModel.createStreamRequest(prompt);

		Flux<ConverseStreamOutput> responses = chatModel.converseStream(streamRequest);
		List<ConverseStreamOutput> responseList = responses.collectList().block();
		System.out.println(responseList);

		// Flux<ChatResponse> responses2 = ConverseApiUtils.toChatResponse(responses);
		// List<ChatResponse> responseList2 = responses2.collectList().block();
		// System.out.println(responseList2);

	}

}
