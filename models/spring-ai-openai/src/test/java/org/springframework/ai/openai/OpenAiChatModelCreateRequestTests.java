/*
 * Copyright 2025 the original author or authors.
 */

package org.springframework.ai.openai;

import java.util.List;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
import org.springframework.http.ResponseEntity;
import org.springframework.retry.support.RetryTemplate;
import io.micrometer.observation.ObservationRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonClassDescription("Weather API request")
record MyMockWeatherServiceRequest(@JsonProperty(required = true,
		value = "location") @JsonPropertyDescription("The city and state e.g. San Francisco, CA") String location,
		@JsonProperty(required = true, value = "lat") @JsonPropertyDescription("The city latitude") double lat,
		@JsonProperty(required = true, value = "lon") @JsonPropertyDescription("The city longitude") double lon,
		@JsonProperty(value = "unit") @JsonPropertyDescription("Temperature unit") MockWeatherService.Unit unit) {

}

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class OpenAiChatModelCreateRequestTests {

	private static Stream<Arguments> schemaProvider() {
		return Stream.of(
				// no 'additionalProperties', 'required' matches 'properties'
				Arguments.of("""
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
						""", null),
				// 'additionalProperties' to 'false', 'required' matches 'properties'
				Arguments.of("""
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
						  "required": ["location", "lat", "lon", "unit"],
						  "additionalProperties": false
						}
						""", true),
				// no 'additionalProperties', 'required' do not match 'properties'
				Arguments.of("""
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
						  "required": ["location", "lat", "lon"]
						}
						""", null),
				// 'additionalProperties' to true
				Arguments.of("""
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
						  "required": ["location", "lat", "lon", "unit"],
						  "additionalProperties": true
						}
						""", null));
	}

	@ParameterizedTest
	@MethodSource("schemaProvider")
	void createRequestWithStrictCompatiblePropertiesHasStrictTrue(String schema, Boolean expectedStrictToBe) {

		OpenAiChatOptions options = OpenAiChatOptions.builder()
			.model(OpenAiApi.DEFAULT_CHAT_MODEL)
			.toolCallbacks(List.of(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
				.description("Get the weather in location")
				.inputSchema(schema)
				.inputType(MyMockWeatherServiceRequest.class)
				.build()))
			.build();

		Prompt prompt = new Prompt(List.of(new UserMessage("Get weather in SF, CA")), options);

		OpenAiChatModel model = OpenAiChatModel.builder()
			.openAiApi(OpenAiApi.builder().apiKey(System.getenv("OPENAI_API_KEY")).build())
			.build();

		ChatCompletionRequest request = model.createRequest(prompt, false);

		assertThat(request.tools().stream().allMatch(tool -> tool.getFunction().getStrict() == expectedStrictToBe))
			.isTrue();

		assertThatCode(() -> {
			var ignored = model.call(prompt);
		}).doesNotThrowAnyException();
	}

}
