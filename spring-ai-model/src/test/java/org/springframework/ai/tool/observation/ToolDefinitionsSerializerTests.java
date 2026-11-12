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

package org.springframework.ai.tool.observation;

import java.util.List;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.tool.definition.ToolDefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Unit tests for {@link ToolDefinitionsSerializer}.
 *
 * @author Ajax-1
 */
class ToolDefinitionsSerializerTests {

	private static final ToolDefinition GET_WEATHER = ToolDefinition.builder()
		.name("getWeather")
		.description("Get the weather in a location")
		.inputSchema("{\"type\":\"object\",\"properties\":{\"location\":{\"type\":\"string\"}}}")
		.build();

	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	@Test
	void serializeTypesAndNames() {
		String json = new ToolDefinitionsSerializer(false, false).serialize(List.of(GET_WEATHER));

		assertThat(readTree(json)).isEqualTo(readTree("""
				[ { "type": "function", "name": "getWeather" } ]
				"""));
	}

	@Test
	void serializeWithDescriptions() {
		String json = new ToolDefinitionsSerializer(true, false).serialize(List.of(GET_WEATHER));

		assertThat(readTree(json)).isEqualTo(readTree("""
				[ { "type": "function", "name": "getWeather", "description": "Get the weather in a location" } ]
				"""));
	}

	@Test
	void serializeWithParameters() {
		String json = new ToolDefinitionsSerializer(false, true).serialize(List.of(GET_WEATHER));

		assertThat(readTree(json)).isEqualTo(readTree("""
				[
					{
						"type": "function",
						"name": "getWeather",
						"parameters": { "type": "object", "properties": { "location": { "type": "string" } } }
					}
				]
				"""));
	}

	@Test
	void whenInputSchemaIsNotJsonThenParametersAreOmitted() {
		ToolDefinition toolDefinition = ToolDefinition.builder()
			.name("getTime")
			.description("Get the current time")
			.inputSchema("not json")
			.build();

		String json = new ToolDefinitionsSerializer(true, true).serialize(List.of(toolDefinition));

		assertThat(readTree(json)).isEqualTo(readTree("""
				[ { "type": "function", "name": "getTime", "description": "Get the current time" } ]
				"""));
	}

	@Test
	void serializeEmptyList() {
		assertThat(new ToolDefinitionsSerializer(true, true).serialize(List.of())).isEqualTo("[]");
	}

	@Test
	void whenToolDefinitionsAreNullThenThrow() {
		assertThatIllegalArgumentException().isThrownBy(() -> new ToolDefinitionsSerializer(true, true).serialize(null))
			.withMessage("toolDefinitions cannot be null");
	}

	private JsonNode readTree(String json) {
		return this.jsonMapper.readTree(json);
	}

}
