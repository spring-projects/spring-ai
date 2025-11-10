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

package org.springframework.ai.openai.api;

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for FunctionTool.Function parameters validation.
 *
 * @author Liu Guodong
 */
class FunctionToolParametersTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void testFunctionWithNoParameters() throws Exception {
		// Test case 1: JSON schema with no properties field
		String jsonSchemaNoProperties = """
				{
					"type": "object"
				}
				""";

		OpenAiApi.FunctionTool.Function function = new OpenAiApi.FunctionTool.Function(
				"Test function with no parameters", "test_function", jsonSchemaNoProperties);

		assertThat(function.getParameters()).isNotNull();
		assertThat(function.getParameters().get("type")).isEqualTo("object");
		assertThat(function.getParameters().get("properties")).isNotNull();
		assertThat(function.getParameters().get("properties")).isInstanceOf(Map.class);

		// Verify serialization produces valid JSON
		String json = this.objectMapper.writeValueAsString(function);
		assertThat(json).contains("\"properties\"");
	}

	@Test
	void testFunctionWithEmptyProperties() throws Exception {
		// Test case 2: JSON schema with empty properties
		String jsonSchemaEmptyProperties = """
				{
					"type": "object",
					"properties": {}
				}
				""";

		OpenAiApi.FunctionTool.Function function = new OpenAiApi.FunctionTool.Function(
				"Test function with empty properties", "test_function", jsonSchemaEmptyProperties);

		assertThat(function.getParameters()).isNotNull();
		assertThat(function.getParameters().get("type")).isEqualTo("object");
		assertThat(function.getParameters().get("properties")).isNotNull();

		// Verify serialization produces valid JSON
		String json = this.objectMapper.writeValueAsString(function);
		assertThat(json).contains("\"properties\"");
	}

	@Test
	void testFunctionWithParameters() throws Exception {
		// Test case 3: JSON schema with actual parameters
		String jsonSchemaWithParams = """
				{
					"type": "object",
					"properties": {
						"param1": {
							"type": "string",
							"description": "First parameter"
						}
					},
					"required": ["param1"]
				}
				""";

		OpenAiApi.FunctionTool.Function function = new OpenAiApi.FunctionTool.Function("Test function with parameters",
				"test_function", jsonSchemaWithParams);

		assertThat(function.getParameters()).isNotNull();
		assertThat(function.getParameters().get("type")).isEqualTo("object");
		assertThat(function.getParameters().get("properties")).isNotNull();
		assertThat(function.getParameters().get("properties")).isInstanceOf(Map.class);

		@SuppressWarnings("unchecked")
		Map<String, Object> properties = (Map<String, Object>) function.getParameters().get("properties");
		assertThat(properties).containsKey("param1");

		// Verify serialization produces valid JSON
		String json = this.objectMapper.writeValueAsString(function);
		assertThat(json).contains("\"properties\"");
		assertThat(json).contains("\"param1\"");
	}

	@Test
	void testFunctionWithNullSchema() throws Exception {
		// Test case 4: null JSON schema (edge case)
		String nullSchema = null;
		OpenAiApi.FunctionTool.Function function = new OpenAiApi.FunctionTool.Function("Test function with null schema",
				"test_function", nullSchema);

		// Should create a valid empty parameters object
		assertThat(function.getParameters()).isNotNull();
		assertThat(function.getParameters().get("type")).isEqualTo("object");
		assertThat(function.getParameters().get("properties")).isNotNull();

		// Verify serialization produces valid JSON
		String json = this.objectMapper.writeValueAsString(function);
		assertThat(json).contains("\"properties\"");
	}

	@Test
	void testFunctionWithVoidTypeSchema() throws Exception {
		// Test case 5: Schema generated for Void.class (common case for no-param
		// functions)
		// This simulates what JsonSchemaGenerator would produce
		String voidSchema = """
				{
					"$schema": "https://json-schema.org/draft/2020-12/schema",
					"type": "object",
					"additionalProperties": false
				}
				""";

		OpenAiApi.FunctionTool.Function function = new OpenAiApi.FunctionTool.Function("Test function for Void type",
				"test_void_function", voidSchema);

		assertThat(function.getParameters()).isNotNull();
		assertThat(function.getParameters().get("type")).isEqualTo("object");
		assertThat(function.getParameters().get("properties")).isNotNull();
		assertThat(function.getParameters().get("properties")).isInstanceOf(Map.class);

		// Verify serialization produces valid JSON for OpenAI API
		String json = this.objectMapper.writeValueAsString(function);
		assertThat(json).contains("\"properties\"");
	}

}

