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

package org.springframework.ai.openai.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallingHelper;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.util.CollectionUtils;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = OpenAiChatModelProxyToolCallsIT.Config.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiChatModelProxyToolCallsIT {

	private static final Logger logger = LoggerFactory.getLogger(OpenAiChatModelProxyToolCallsIT.class);

	private static final String DEFAULT_MODEL = "gpt-4o-mini";

	FunctionCallback functionDefinition = new FunctionCallingHelper.FunctionDefinition("getWeatherInLocation",
			"Get the weather in location", """
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
					""");

	@Autowired
	private OpenAiChatModel chatModel;

	// Helper class that reuses some of the {@link AbstractToolCallSupport} functionality
	// to help to implement the function call handling logic on the client side.
	private FunctionCallingHelper functionCallingHelper = new FunctionCallingHelper();

	@SuppressWarnings("unchecked")
	private static Map<String, String> getFunctionArguments(String functionArguments) {
		try {
			return new ObjectMapper().readValue(functionArguments, Map.class);
		}
		catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}

	// Function which will be called by the AI model.
	private String getWeatherInLocation(String location, String unit) {

		double temperature = 0;

		if (location.contains("Paris")) {
			temperature = 15;
		}
		else if (location.contains("Tokyo")) {
			temperature = 10;
		}
		else if (location.contains("San Francisco")) {
			temperature = 30;
		}

		return String.format("The weather in %s is %s%s", location, temperature, unit);
	}

	@Test
	void functionCall() throws JsonMappingException, JsonProcessingException {

		List<Message> messages = List
			.of(new UserMessage("What's the weather like in San Francisco, Tokyo, and Paris?"));

		var promptOptions = OpenAiChatOptions.builder().withFunctionCallbacks(List.of(this.functionDefinition)).build();

		var prompt = new Prompt(messages, promptOptions);

		boolean isToolCall = false;

		ChatResponse chatResponse = null;

		do {

			chatResponse = this.chatModel.call(prompt);

			// We will have to convert the chatResponse into OpenAI assistant message.

			// Note that the tool call check could be platform specific because the finish
			// reasons.
			isToolCall = this.functionCallingHelper.isToolCall(chatResponse,
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

					assertThat(functionName).isEqualTo("getWeatherInLocation");

					String functionArguments = toolCall.arguments();

					@SuppressWarnings("unchecked")
					Map<String, String> argumentsMap = new ObjectMapper().readValue(functionArguments, Map.class);

					String functionResponse = getWeatherInLocation(argumentsMap.get("location").toString(),
							argumentsMap.get("unit").toString());

					toolResponses.add(new ToolResponseMessage.ToolResponse(toolCall.id(), functionName,
							ModelOptionsUtils.toJsonString(functionResponse)));
				}

				ToolResponseMessage toolMessageResponse = new ToolResponseMessage(toolResponses, Map.of());

				List<Message> toolCallConversation = this.functionCallingHelper
					.buildToolCallConversation(prompt.getInstructions(), assistantMessage, toolMessageResponse);

				assertThat(toolCallConversation).isNotEmpty();

				prompt = new Prompt(toolCallConversation, prompt.getOptions());
			}
		}
		while (isToolCall);

		logger.info("Response: {}", chatResponse);

		assertThat(chatResponse.getResult().getOutput().getText()).contains("30", "10", "15");
	}

	@Test
	void functionStream() throws JsonMappingException, JsonProcessingException {

		List<Message> messages = List
			.of(new UserMessage("What's the weather like in San Francisco, Tokyo, and Paris?"));

		var promptOptions = OpenAiChatOptions.builder().withFunctionCallbacks(List.of(this.functionDefinition)).build();

		var prompt = new Prompt(messages, promptOptions);

		String response = processToolCall(prompt, Set.of(OpenAiApi.ChatCompletionFinishReason.TOOL_CALLS.name(),
				OpenAiApi.ChatCompletionFinishReason.STOP.name()), toolCall -> {

					var functionName = toolCall.name();

					assertThat(functionName).isEqualTo("getWeatherInLocation");

					String functionArguments = toolCall.arguments();

					Map<String, String> argumentsMap = getFunctionArguments(functionArguments);

					String functionResponse = getWeatherInLocation(argumentsMap.get("location").toString(),
							argumentsMap.get("unit").toString());

					return functionResponse;
				})
			.collectList()
			.block()
			.stream()
			.map(cr -> cr.getResult().getOutput().getText())
			.collect(Collectors.joining());

		logger.info("Response: {}", response);

		assertThat(response).contains("30", "10", "15");

	}

	private Flux<ChatResponse> processToolCall(Prompt prompt, Set<String> finishReasons,
			Function<AssistantMessage.ToolCall, String> customFunction) {

		Flux<ChatResponse> chatResponses = this.chatModel.stream(prompt);

		return chatResponses.flatMap(chatResponse -> {

			boolean isToolCall = this.functionCallingHelper.isToolCall(chatResponse, finishReasons);

			if (isToolCall) {

				Optional<Generation> toolCallGeneration = chatResponse.getResults()
					.stream()
					.filter(g -> !CollectionUtils.isEmpty(g.getOutput().getToolCalls()))
					.findFirst();

				assertThat(toolCallGeneration).isNotEmpty();

				AssistantMessage assistantMessage = toolCallGeneration.get().getOutput();

				List<ToolResponseMessage.ToolResponse> toolResponses = new ArrayList<>();

				for (AssistantMessage.ToolCall toolCall : assistantMessage.getToolCalls()) {

					String functionResponse = customFunction.apply(toolCall);

					toolResponses.add(new ToolResponseMessage.ToolResponse(toolCall.id(), toolCall.name(),
							ModelOptionsUtils.toJsonString(functionResponse)));
				}

				ToolResponseMessage toolMessageResponse = new ToolResponseMessage(toolResponses, Map.of());

				List<Message> toolCallConversation = this.functionCallingHelper
					.buildToolCallConversation(prompt.getInstructions(), assistantMessage, toolMessageResponse);

				assertThat(toolCallConversation).isNotEmpty();

				var prompt2 = new Prompt(toolCallConversation, prompt.getOptions());

				return processToolCall(prompt2, finishReasons, customFunction);
			}

			return Flux.just(chatResponse);
		});
	}

	@Test
	void functionCall2() throws JsonMappingException, JsonProcessingException {

		List<Message> messages = List
			.of(new UserMessage("What's the weather like in San Francisco, Tokyo, and Paris?"));

		var promptOptions = OpenAiChatOptions.builder().withFunctionCallbacks(List.of(this.functionDefinition)).build();

		var prompt = new Prompt(messages, promptOptions);

		ChatResponse chatResponse = this.functionCallingHelper.processCall(this.chatModel, prompt,
				Set.of(OpenAiApi.ChatCompletionFinishReason.TOOL_CALLS.name(),
						OpenAiApi.ChatCompletionFinishReason.STOP.name()),
				toolCall -> {

					var functionName = toolCall.name();

					assertThat(functionName).isEqualTo("getWeatherInLocation");

					String functionArguments = toolCall.arguments();

					Map<String, String> argumentsMap = getFunctionArguments(functionArguments);

					String functionResponse = getWeatherInLocation(argumentsMap.get("location").toString(),
							argumentsMap.get("unit").toString());

					return functionResponse;
				});

		logger.info("Response: {}", chatResponse);

		assertThat(chatResponse.getResult().getOutput().getText()).contains("30", "10", "15");
	}

	@Test
	void functionStream2() throws JsonMappingException, JsonProcessingException {

		List<Message> messages = List
			.of(new UserMessage("What's the weather like in San Francisco, Tokyo, and Paris?"));

		var promptOptions = OpenAiChatOptions.builder().withFunctionCallbacks(List.of(this.functionDefinition)).build();

		var prompt = new Prompt(messages, promptOptions);

		Flux<ChatResponse> responses = this.functionCallingHelper.processStream(this.chatModel, prompt,
				Set.of(OpenAiApi.ChatCompletionFinishReason.TOOL_CALLS.name(),
						OpenAiApi.ChatCompletionFinishReason.STOP.name()),
				toolCall -> {

					var functionName = toolCall.name();

					assertThat(functionName).isEqualTo("getWeatherInLocation");

					String functionArguments = toolCall.arguments();

					Map<String, String> argumentsMap = getFunctionArguments(functionArguments);

					String functionResponse = getWeatherInLocation(argumentsMap.get("location").toString(),
							argumentsMap.get("unit").toString());

					return functionResponse;
				});

		String response = responses.collectList()
			.block()
			.stream()
			.map(cr -> cr.getResult().getOutput().getText())
			.collect(Collectors.joining());

		logger.info("Response: {}", response);

		assertThat(response).contains("30", "10", "15");

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

	}

}
