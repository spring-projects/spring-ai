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
package org.springframework.ai.openai.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.AbstractToolCallSupport;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallbackContext;
import org.springframework.ai.model.function.FunctionCallingOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.tool.MockWeatherService;
import org.springframework.ai.openai.api.tool.MockWeatherService.Response;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.util.CollectionUtils;

import io.micrometer.observation.ObservationRegistry;

@SpringBootTest(classes = OpenAiChatModelProxyToolCallsIT.Config.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiChatModelProxyToolCallsIT {

	private static final Logger logger = LoggerFactory.getLogger(OpenAiChatModelIT.class);

	private static final String DEFAULT_MODEL = "gpt-4o-mini";

	@Autowired
	private OpenAiChatModel chatModel;

	@Autowired
	private FunctionCallHelper functionCallUtils;

	/**
	 * Helper used to provide only the function definition, without the actual function
	 * call implementation.
	 */
	public static record FunctionDefinition(String name, String description,
			String inputTypeSchema) implements FunctionCallback {

		@Override
		public String getName() {
			return this.name();
		}

		@Override
		public String getDescription() {
			return this.description();
		}

		@Override
		public String getInputTypeSchema() {
			return this.inputTypeSchema();
		}

		@Override
		public String call(String functionInput) {
			throw new UnsupportedOperationException(
					"FunctionDefinition provides only metadata. It doesn't implement the call method.");
		}

	}

	@Test
	void functionCallTest() {

		var weatherService = new MockWeatherService();

		List<Message> messages = List
			.of(new UserMessage("What's the weather like in San Francisco, Tokyo, and Paris?"));

		FunctionCallback functionDefinition = new FunctionDefinition("getCurrentWeather", "Get the weather in location",
				"""
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
						""");

		var promptOptions = OpenAiChatOptions.builder().withFunctionCallbacks(List.of(functionDefinition)).build();

		var prompt = new Prompt(messages, promptOptions);

		boolean isToolCall = false;

		ChatResponse chatResponse = null;

		do {

			chatResponse = chatModel.call(prompt);

			// We will have to convert the chatResponse into OpenAI assistant message.

			// Code that the Python tools will have to implement
			isToolCall = functionCallUtils.isToolCall(chatResponse,
					Set.of(OpenAiApi.ChatCompletionFinishReason.TOOL_CALLS.name(),
							OpenAiApi.ChatCompletionFinishReason.STOP.name()));

			if (isToolCall) {

				Optional<Generation> toolCallGeneration = chatResponse.getResults()
					.stream()
					.filter(g -> !CollectionUtils.isEmpty(g.getOutput().getToolCalls()))
					.findFirst();

				assertThat(toolCallGeneration).isNotEmpty();

				AssistantMessage assistantMessage = toolCallGeneration.get().getOutput();

				List<ToolResponseMessage.ToolResponse> toolResponses = new ArrayList<>();

				for (AssistantMessage.ToolCall toolCall : assistantMessage.getToolCalls()) {

					var functionName = toolCall.name();

					assertThat(functionName).isEqualTo("getCurrentWeather");

					String functionArguments = toolCall.arguments();

					MockWeatherService.Request functionRequest = ModelOptionsUtils.jsonToObject(functionArguments,
							MockWeatherService.Request.class);

					Response functionResponse = weatherService.apply(functionRequest);

					toolResponses.add(new ToolResponseMessage.ToolResponse(toolCall.id(), functionName,
							ModelOptionsUtils.toJsonString(functionResponse)));
				}

				ToolResponseMessage toolMessageResponse = new ToolResponseMessage(toolResponses, Map.of());

				List<Message> toolCallConversation = functionCallUtils
					.buildToolCallConversation(prompt.getInstructions(), assistantMessage, toolMessageResponse);

				assertThat(toolCallConversation).isNotEmpty();

				prompt = new Prompt(toolCallConversation, prompt.getOptions());
			}
		}
		while (isToolCall);

		logger.info("Response: {}", chatResponse);

		assertThat(chatResponse.getResult().getOutput().getContent()).contains("30", "10", "15");
	}

	/**
	 * Helper class that reuses the {@link AbstractToolCallSupport} to implement the
	 * function call handling logic on the client side.
	 */
	public static class FunctionCallHelper extends AbstractToolCallSupport {

		protected FunctionCallHelper(FunctionCallbackContext functionCallbackContext,
				FunctionCallingOptions functionCallingOptions, List<FunctionCallback> toolFunctionCallbacks) {
			super(functionCallbackContext, functionCallingOptions, toolFunctionCallbacks);
		}

		@Override
		public boolean isToolCall(ChatResponse chatResponse, Set<String> toolCallFinishReasons) {
			return super.isToolCall(chatResponse, toolCallFinishReasons);
		}

		@Override
		public List<Message> buildToolCallConversation(List<Message> previousMessages,
				AssistantMessage assistantMessage, ToolResponseMessage toolResponseMessage) {
			return super.buildToolCallConversation(previousMessages, assistantMessage, toolResponseMessage);
		}

		@Override
		public List<Message> handleToolCalls(Prompt prompt, ChatResponse response) {
			return super.handleToolCalls(prompt, response);
		}

	}

	@SpringBootConfiguration
	static class Config {

		@Bean
		public OpenAiApi chatCompletionApi() {
			return new OpenAiApi(System.getenv("OPENAI_API_KEY"));
		}

		@Bean
		public OpenAiChatModel openAiClient(OpenAiApi openAiApi, List<FunctionCallback> toolFunctionCallbacks) {
			// enable the proxy tool calls option.
			var options = OpenAiChatOptions.builder().withModel(DEFAULT_MODEL).withProxyToolCalls(true).build();

			return new OpenAiChatModel(openAiApi, options, null, toolFunctionCallbacks,
					RetryUtils.DEFAULT_RETRY_TEMPLATE, ObservationRegistry.NOOP);
		}

		@Bean
		public FunctionCallHelper functionCallUtils(List<FunctionCallback> toolFunctionCallbacks) {
			OpenAiChatOptions functionCallingOptions = OpenAiChatOptions.builder().build();
			return new FunctionCallHelper(null, functionCallingOptions, toolFunctionCallbacks);
		}

	}

}