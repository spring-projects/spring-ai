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

package org.springframework.ai.chat.observation;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ChatModelToolDefinitionsObservationFilter}.
 *
 * @author Ajax-1
 */
class ChatModelToolDefinitionsObservationFilterTests {

	private final ChatModelToolDefinitionsObservationFilter observationFilter = new ChatModelToolDefinitionsObservationFilter();

	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	@Test
	void whenNotSupportedObservationContextThenReturnOriginalContext() {
		var expectedContext = new Observation.Context();
		var actualContext = this.observationFilter.map(expectedContext);

		assertThat(actualContext).isEqualTo(expectedContext);
	}

	@Test
	void whenNoToolsThenNoToolDefinitions() {
		var context = observationContext(ToolCallingChatOptions.builder().model("mistral").build());

		var augmentedContext = this.observationFilter.map(context);

		assertThat(augmentedContext.getHighCardinalityKeyValues()).noneMatch(keyValue -> keyValue.getKey()
			.equals(ChatModelObservationDocumentation.HighCardinalityKeyNames.REQUEST_TOOL_DEFINITIONS.asString()));
	}

	@Test
	void whenOptionsWithoutToolCallingThenNoToolDefinitions() {
		var context = observationContext(ChatOptions.builder().model("mistral").build());

		var augmentedContext = this.observationFilter.map(context);

		assertThat(augmentedContext.getHighCardinalityKeyValues()).noneMatch(keyValue -> keyValue.getKey()
			.equals(ChatModelObservationDocumentation.HighCardinalityKeyNames.REQUEST_TOOL_DEFINITIONS.asString()));
	}

	@Test
	void augmentContextWithToolDefinitions() {
		var context = observationContext(ToolCallingChatOptions.builder()
			.model("mistral")
			.toolCallbacks(toolCallback("getWeather", "Get the weather in a location",
					"{\"type\":\"object\",\"properties\":{\"location\":{\"type\":\"string\"}},\"required\":[\"location\"]}"),
					toolCallback("getTime", "Get the current time", "{\"type\":\"object\",\"properties\":{}}"))
			.build());

		var augmentedContext = this.observationFilter.map(context);

		assertThat(this.jsonMapper.readTree(toolDefinitions(augmentedContext))).isEqualTo(this.jsonMapper.readTree("""
				[
					{
						"type": "function",
						"name": "getWeather",
						"description": "Get the weather in a location",
						"parameters": {
							"type": "object",
							"properties": { "location": { "type": "string" } },
							"required": ["location"]
						}
					},
					{
						"type": "function",
						"name": "getTime",
						"description": "Get the current time",
						"parameters": { "type": "object", "properties": {} }
					}
				]
				"""));
	}

	@Test
	void whenInputSchemaIsNotJsonThenParametersAreOmitted() {
		var context = observationContext(ToolCallingChatOptions.builder()
			.toolCallbacks(toolCallback("getTime", "Get the current time", "not json"))
			.build());

		var augmentedContext = this.observationFilter.map(context);

		assertThat(this.jsonMapper.readTree(toolDefinitions(augmentedContext))).isEqualTo(this.jsonMapper.readTree("""
				[ { "type": "function", "name": "getTime", "description": "Get the current time" } ]
				"""));
	}

	@Test
	void whenFilterAppliedMultipleTimesThenIdempotent() {
		var context = observationContext(ToolCallingChatOptions.builder()
			.toolCallbacks(toolCallback("getTime", "Get the current time", "{}"))
			.build());

		var augmentedTwice = this.observationFilter.map(this.observationFilter.map(context));

		assertThat(augmentedTwice.getHighCardinalityKeyValues())
			.filteredOn(keyValue -> keyValue.getKey()
				.equals(ChatModelObservationDocumentation.HighCardinalityKeyNames.REQUEST_TOOL_DEFINITIONS.asString()))
			.hasSize(1);
	}

	private static ChatModelObservationContext observationContext(ChatOptions options) {
		return ChatModelObservationContext.builder()
			.prompt(new Prompt("What's the weather?", options))
			.provider("superprovider")
			.build();
	}

	private static String toolDefinitions(Observation.Context context) {
		KeyValue keyValue = context.getHighCardinalityKeyValue(
				ChatModelObservationDocumentation.HighCardinalityKeyNames.REQUEST_TOOL_DEFINITIONS.asString());
		assertThat(keyValue).isNotNull();
		return keyValue.getValue();
	}

	private static ToolCallback toolCallback(String name, String description, String inputSchema) {
		ToolDefinition toolDefinition = ToolDefinition.builder()
			.name(name)
			.description(description)
			.inputSchema(inputSchema)
			.build();
		return new ToolCallback() {

			@Override
			public ToolDefinition getToolDefinition() {
				return toolDefinition;
			}

			@Override
			public String call(String toolInput) {
				return "";
			}

		};
	}

}
