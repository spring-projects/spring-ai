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

package org.springframework.ai.openai.chat.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallingHelper;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiTestConfiguration;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.testutils.AbstractIT;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.CollectionUtils;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = OpenAiTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@ActiveProfiles("logging-test")
class OpenAiChatClientProxyFunctionCallsIT extends AbstractIT {

	private static final Logger logger = LoggerFactory.getLogger(OpenAiChatClientMultipleFunctionCallsIT.class);

	@Value("classpath:/prompts/system-message.st")
	private Resource systemTextResource;

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
	void toolProxyFunctionCall() throws JsonMappingException, JsonProcessingException {

		List<Message> messages = List
			.of(new UserMessage("What's the weather like in San Francisco, Tokyo, and Paris?"));

		boolean isToolCall = false;

		ChatResponse chatResponse = null;

		var chatClient = ChatClient.builder(this.chatModel).build();

		do {

			chatResponse = chatClient.prompt()
				.messages(messages)
				.tools(this.functionDefinition)
				.options(OpenAiChatOptions.builder().proxyToolCalls(true).build())
				.call()
				.chatResponse();

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

				messages = this.functionCallingHelper.buildToolCallConversation(messages, assistantMessage,
						toolMessageResponse);

				assertThat(messages).isNotEmpty();

				// prompt = new Prompt(toolCallConversation, prompt.getOptions());
			}
		}
		while (isToolCall);

		logger.info("Response: {}", chatResponse);

		assertThat(chatResponse.getResult().getOutput().getText()).contains("30", "10", "15");
	}

}
