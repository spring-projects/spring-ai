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

package org.springframework.ai.deliverance;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deliverance.api.DeliveranceApi;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = DeliveranceChatModelFunctionCallingIT.Config.class)
class DeliveranceChatModelFunctionCallingIT extends BaseDeliveranceIT {

	@Autowired
	ChatModel chatModel;

	@Test
	void functionCallTest() {
		assumeDeliverancePortOpen();
		UserMessage userMessage = new UserMessage(
				"Use the weather tool for San Francisco. Return only the Celsius temperature.");
		List<Message> messages = new ArrayList<>(List.of(userMessage));
		var promptOptions = DeliveranceChatOptions.builder()
			.model(model())
			.temperature(0.0)
			.maxTokens(64)
			.toolCallbacks(List.of(FunctionToolCallback.builder("weather", new MockWeatherService())
				.description("Return the Celsius temperature for a city.")
				.inputType(MockWeatherService.Request.class)
				.build()))
			.build();

		ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();
		Prompt prompt = new Prompt(messages, promptOptions);
		ChatResponse response = this.chatModel.call(prompt);

		while (response.hasToolCalls()) {
			ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, response);
			prompt = new Prompt(toolExecutionResult.conversationHistory(), promptOptions);
			response = this.chatModel.call(prompt);
		}
		assertThat(response.getResult().getOutput().getText()).contains("30");
	}

	@SpringBootConfiguration
	static class Config {

		@Bean
		DeliveranceApi deliveranceApi() {
			return initializeDeliverance();
		}

		@Bean
		DeliveranceChatModel deliveranceChatModel(DeliveranceApi deliveranceApi) {
			return new DeliveranceChatModel(deliveranceApi, new ObjectMapper(),
					DeliveranceChatOptions.builder().model(model()).temperature(0.0).build());
		}

	}

	private static final class MockWeatherService
			implements Function<MockWeatherService.Request, MockWeatherService.Response> {

		@Override
		public Response apply(Request request) {
			return new Response(request.location().contains("San Francisco") ? 30 : 0, Unit.C);
		}

		enum Unit {

			C

		}

		record Request(String location) {

		}

		record Response(double temp, Unit unit) {

		}

	}

}
