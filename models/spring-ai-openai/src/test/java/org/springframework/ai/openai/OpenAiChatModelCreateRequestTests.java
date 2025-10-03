/*
 * Copyright 2025 the original author or authors.
 */

package org.springframework.ai.openai;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionRequest;
import org.springframework.ai.openai.api.tool.MockWeatherService;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.retry.support.RetryTemplate;
import io.micrometer.observation.ObservationRegistry;

import static org.assertj.core.api.Assertions.assertThat;

public class OpenAiChatModelCreateRequestTests {

	@Test
	void createRequestWithStrictCompatiblePropertiesHasStrictTrue() {
		var schema = """
					{
						"type": "object",
						"properties": {
							"target_file": {
								"type": "string",
								"description": "Path to the new file, relative to the project root directory."
							},
							"content": {
								"type": "string",
								"description": "The contents to write to the file. Can be an empty string to create an empty file."
							}
						},
						"additionalProperties": false,
						"required": ["target_file", "content"]
					}
				""";
		OpenAiChatOptions options = OpenAiChatOptions.builder()
			.model(OpenAiApi.DEFAULT_CHAT_MODEL)
			.toolCallbacks(List.of(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
				.description("Get the weather in location")
				.inputSchema(schema)
				.inputType(MockWeatherService.Request.class)
				.build()))
			.build();

		Prompt prompt = new Prompt(List.of(new UserMessage("hello")), options);

		OpenAiChatModel model = OpenAiChatModel.builder()
			.openAiApi(OpenAiApi.builder().apiKey("sk-test").build())
			.build();

		ChatCompletionRequest request = model.createRequest(prompt, false);

		assertThat(request.tools().stream().allMatch(tool -> tool.getFunction().getStrict() == true)).isTrue();
	}

}
