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

package org.springframework.ai.huggingface;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.huggingface.api.tool.MockWeatherService;
import org.springframework.ai.huggingface.api.tool.MockWeatherService.Request;
import org.springframework.ai.huggingface.api.tool.MockWeatherService.Response;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for HuggingFace ChatModel function calling capabilities. These tests
 * verify that the high-level ChatModel API correctly handles automatic tool/function
 * execution.
 *
 * <p>
 * Note: Function calling requires specific models and providers. This test uses
 * meta-llama/Llama-3.2-3B-Instruct with the 'together' provider which supports function
 * calling through the HuggingFace Inference API.
 * </p>
 *
 * <p>
 * <strong>Streaming Support:</strong> HuggingfaceChatModel currently does NOT implement
 * StreamingChatModel, so streaming function calling tests are not included. Streaming
 * support will be added in a future PR when WebClient integration is implemented.
 * </p>
 *
 * @author Myeongdeok Kang
 * @see <a href=
 * "https://huggingface.co/docs/inference-providers/guides/function-calling">HuggingFace
 * Function Calling Guide</a>
 * @see <a href=
 * "https://huggingface.co/collections/MarketAgents/function-calling-models-tool-use">Function
 * Calling Models Collection</a>
 */
@EnabledIfEnvironmentVariable(named = "HUGGINGFACE_API_KEY", matches = ".+")
class HuggingfaceChatModelFunctionCallingIT extends BaseHuggingfaceIT {

	private static final Logger logger = LoggerFactory.getLogger(HuggingfaceChatModelFunctionCallingIT.class);

	// Use function-calling compatible model with provider specification
	// Provider suffix notation (":together") is required for function calling support
	private static final String FUNCTION_CALLING_MODEL = "meta-llama/Llama-3.2-3B-Instruct:together";

	@Autowired
	ChatModel chatModel;

	/**
	 * Test basic function calling with automatic tool execution. Verifies that:
	 * <ul>
	 * <li>Function callbacks are properly registered via HuggingfaceChatOptions</li>
	 * <li>The model correctly identifies when to call functions</li>
	 * <li>Functions are automatically executed by the framework</li>
	 * <li>Tool results are integrated into the final response</li>
	 * </ul>
	 */
	@Test
	void functionCallTest() {
		functionCallTest(HuggingfaceChatOptions.builder()
			.model(FUNCTION_CALLING_MODEL)
			.toolCallbacks(List.of(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
				.description("Get the weather in location. Return temperature in Celsius.")
				.inputType(MockWeatherService.Request.class)
				.build()))
			.build());
	}

	/**
	 * Test function calling with ToolContext support. Verifies that:
	 * <ul>
	 * <li>ToolContext can be passed to function callbacks</li>
	 * <li>BiFunction&lt;Request, ToolContext, Response&gt; signature works correctly</li>
	 * <li>Context values are accessible during function execution</li>
	 * <li>Context propagates correctly through the tool execution flow</li>
	 * </ul>
	 */
	@Test
	void functionCallWithToolContextTest() {

		var biFunction = new BiFunction<MockWeatherService.Request, ToolContext, MockWeatherService.Response>() {

			@Override
			public Response apply(Request request, ToolContext toolContext) {

				// Verify ToolContext contains expected values
				assertThat(toolContext.getContext()).containsEntry("sessionId", "123");

				double temperature = 0;
				if (request.location().contains("Paris")) {
					temperature = 15;
				}
				else if (request.location().contains("Tokyo")) {
					temperature = 10;
				}
				else if (request.location().contains("San Francisco")) {
					temperature = 30;
				}

				return new MockWeatherService.Response(temperature, 15, 20, 2, 53, 45, MockWeatherService.Unit.C);
			}

		};

		functionCallTest(HuggingfaceChatOptions.builder()
			.model(FUNCTION_CALLING_MODEL)
			.toolCallbacks(List.of(FunctionToolCallback.builder("getCurrentWeather", biFunction)
				.description("Get the weather in location. Return temperature in Celsius.")
				.inputType(MockWeatherService.Request.class)
				.build()))
			.toolContext(Map.of("sessionId", "123"))
			.build());
	}

	/**
	 * Common test logic for function calling scenarios.
	 * @param promptOptions The chat options including tool callbacks and context
	 */
	void functionCallTest(HuggingfaceChatOptions promptOptions) {

		UserMessage userMessage = new UserMessage("What's the weather like in San Francisco, Tokyo, and Paris?");

		List<Message> messages = new ArrayList<>(List.of(userMessage));

		ChatResponse response = this.chatModel.call(new Prompt(messages, promptOptions));

		logger.info("Response: {}", response);

		// Verify the response contains the expected temperatures from MockWeatherService
		// San Francisco: 30C, Tokyo: 10C, Paris: 15C
		assertThat(response.getResult().getOutput().getText()).contains("30", "10", "15");
	}

}
