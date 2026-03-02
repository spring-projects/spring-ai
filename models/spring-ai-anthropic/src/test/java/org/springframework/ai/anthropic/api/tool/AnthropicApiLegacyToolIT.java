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

package org.springframework.ai.anthropic.api.tool;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.anthropic.api.AnthropicApi.AnthropicMessage;
import org.springframework.ai.anthropic.api.AnthropicApi.ChatCompletionRequest;
import org.springframework.ai.anthropic.api.AnthropicApi.ChatCompletionResponse;
import org.springframework.ai.anthropic.api.AnthropicApi.ContentBlock;
import org.springframework.ai.anthropic.api.AnthropicApi.Role;
import org.springframework.ai.anthropic.api.tool.XmlHelper.FunctionCalls;
import org.springframework.ai.anthropic.api.tool.XmlHelper.Tools;
import org.springframework.ai.anthropic.api.tool.XmlHelper.Tools.ToolDescription;
import org.springframework.ai.anthropic.api.tool.XmlHelper.Tools.ToolDescription.Parameter;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Experiments with
 * <a href="https://docs.anthropic.com/claude/docs/functions-external-tools">Anthropic
 * Functions & external tools</a>.
 *
 * <p>
 * <a href=
 * "https://www.linkedin.com/pulse/tool-usefunction-calling-anthropics-claude-3-opus-llm-micky-multani-fsmrc">Tool
 * Use(Function Calling) with Anthropic's Claude 3 Opus LLM</a>
 * <p>
 * <a href=
 * "https://www.codeproject.com/Articles/5379174/Csharp-Anthropic-Claude-Library-You-Can-Call-Claud">Anthropic
 * Functions & external tools</a>
 *
 * @author Christian Tzolov
 * @since 1.0.0
 */
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
@SuppressWarnings("null")
public class AnthropicApiLegacyToolIT {

	public static final String TOO_SYSTEM_PROMPT_TEMPLATE = """
			In this environment you have access to a set of tools you can use to answer the user's question.

			You may call them like this:
			<function_calls>
				<invoke>
					<tool_name>$TOOL_NAME</tool_name>
					<parameters>
						<$PARAMETER_NAME>$PARAMETER_VALUE</$PARAMETER_NAME>
						...
					</parameters>
				</invoke>
			</function_calls>

			Here are the tools available:
			<tools>%s</tools>
			""";

	public static final ConcurrentHashMap<String, Function> FUNCTIONS = new ConcurrentHashMap<>();

	private static final Logger logger = LoggerFactory.getLogger(AnthropicApiLegacyToolIT.class);

	AnthropicApi anthropicApi = AnthropicApi.builder().apiKey(System.getenv("ANTHROPIC_API_KEY")).build();

	@Test
	void toolCalls() {

		String toolDescription = XmlHelper.toXml(new Tools(List.of(new ToolDescription("getCurrentWeather",
				"Get the weather in location. Return temperature in 30°F or 30°C format.",
				List.of(new Parameter("location", "string", "The city and state e.g. San Francisco, CA"),
						new Parameter("unit", "enum", "Temperature unit. Use only C or F. Default is C."))))));

		logger.info("TOOLS: " + toolDescription);

		String systemPrompt = String.format(TOO_SYSTEM_PROMPT_TEMPLATE, toolDescription);

		AnthropicMessage chatCompletionMessage = new AnthropicMessage(
				List.of(new ContentBlock("What's the weather like in Paris? Show the temperature in Celsius.")),
				// "What's the weather like in San Francisco, Tokyo, and Paris? Show the
				// temperature in Celsius.")),
				Role.USER);

		ChatCompletionRequest chatCompletionRequest = new ChatCompletionRequest(
				AnthropicApi.ChatModel.CLAUDE_HAIKU_4_5.getValue(), List.of(chatCompletionMessage), systemPrompt, 500,
				0.8, false);

		ResponseEntity<ChatCompletionResponse> chatCompletion = doCall(chatCompletionRequest);

		var responseText = chatCompletion.getBody().content().get(0).text();
		logger.info("FINAL RESPONSE: " + responseText);

		assertThat(responseText).contains("15");
	}

	private ResponseEntity<ChatCompletionResponse> doCall(ChatCompletionRequest chatCompletionRequest) {

		ResponseEntity<ChatCompletionResponse> response = this.anthropicApi.chatCompletionEntity(chatCompletionRequest);

		FunctionCalls functionCalls = XmlHelper.extractFunctionCalls(response.getBody().content().get(0).text());

		if (functionCalls == null) {
			return response;
		}

		logger.info("FunctionCalls from the LLM: " + functionCalls);

		// Transform parameters to the expected format
		Map<String, Object> parameters = transformParameters(functionCalls.invoke().parameters());

		MockWeatherService.Request request = ModelOptionsUtils.mapToClass(parameters, MockWeatherService.Request.class);

		logger.info("Resolved function request param: " + request);

		Object functionCallResponseData = FUNCTIONS.get(functionCalls.invoke().toolName()).apply(request);

		XmlHelper.FunctionResults functionResults = new XmlHelper.FunctionResults(List
			.of(new XmlHelper.FunctionResults.Result(functionCalls.invoke().toolName(), functionCallResponseData)));

		String content = XmlHelper.toXml(functionResults);

		logger.info("Function response XML : " + content);

		AnthropicMessage chatCompletionMessage2 = new AnthropicMessage(List.of(new ContentBlock(content)), Role.USER);

		return doCall(new ChatCompletionRequest(AnthropicApi.ChatModel.CLAUDE_HAIKU_4_5.getValue(),
				List.of(chatCompletionMessage2), null, 500, 0.8, false));
	}

	/**
	 * Transforms parameters from nested format to flat format. Handles both the expected
	 * format (e.g., {location: "Paris", unit: "C"}) and various nested formats returned
	 * by Claude models.
	 * @param parameters the parameters map from the function call
	 * @return transformed parameters in flat format
	 */
	@SuppressWarnings("unchecked")
	private static Map<String, Object> transformParameters(Map<String, Object> parameters) {
		// Handle format: {parameter_name: [...], parameter_value: [...]}
		if (parameters.containsKey("parameter_name") && parameters.containsKey("parameter_value")) {
			List<String> names = (List<String>) parameters.get("parameter_name");
			List<String> values = (List<String>) parameters.get("parameter_value");
			Map<String, Object> flatParams = new HashMap<>();
			for (int i = 0; i < Math.min(names.size(), values.size()); i++) {
				flatParams.put(names.get(i), values.get(i));
			}
			return flatParams;
		}

		// Handle format: {parameter: [{name: "location", value/parameter/"": "Paris"},
		// ...]}
		if (parameters.containsKey("parameter")) {
			Object paramValue = parameters.get("parameter");
			if (paramValue instanceof List) {
				Map<String, Object> flatParams = new HashMap<>();
				List<Map<String, Object>> paramList = (List<Map<String, Object>>) paramValue;
				for (Map<String, Object> param : paramList) {
					String name = (String) param.get("name");
					// Try different keys for the value: "value", "parameter", or empty
					// string
					Object value = param.get("value");
					if (value == null) {
						value = param.get("parameter");
					}
					if (value == null) {
						value = param.get("");
					}
					if (name != null && value != null) {
						flatParams.put(name, value);
					}
				}
				return flatParams.isEmpty() ? parameters : flatParams;
			}
		}

		// Return as-is if already in the expected format
		return parameters;
	}

	static {
		FUNCTIONS.put("getCurrentWeather", new MockWeatherService());
	}

}
