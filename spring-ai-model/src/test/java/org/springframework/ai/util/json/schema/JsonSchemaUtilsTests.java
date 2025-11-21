/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.util.json.schema;

import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.model.ModelOptionsUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link JsonSchemaUtils}.
 *
 * @author Ilayaperumal Gopinathan
 */
class JsonSchemaUtilsTests {

	/**
	 * Test that a schema with only "type": "object" and no "properties" field is
	 * normalized to include an empty "properties" field.
	 * <p>
	 * This scenario occurs when external MCP servers (like Claude Desktop) provide tool
	 * schemas for parameterless tools that don't include the "properties" field.
	 */
	@Test
	void testEnsureValidInputSchemaAddsPropertiesField() {
		// Simulate a schema from an external MCP server without "properties"
		String inputSchema = "{\"type\":\"object\",\"additionalProperties\":false}";

		String normalizedSchema = JsonSchemaUtils.ensureValidInputSchema(inputSchema);

		Map<String, Object> schemaMap = ModelOptionsUtils.jsonToMap(normalizedSchema);
		assertThat(schemaMap).isNotNull();
		assertThat(schemaMap).containsKey("type");
		assertThat(schemaMap.get("type")).isEqualTo("object");

		// The key assertion: verify that "properties" field was added
		assertThat(schemaMap).containsKey("properties");
		assertThat(schemaMap.get("properties")).isInstanceOf(Map.class);

		// For a parameterless tool, properties should be empty
		Map<String, Object> properties = (Map<String, Object>) schemaMap.get("properties");
		assertThat(properties).isEmpty();
	}

	/**
	 * Test that a schema without a "type" field is normalized to include both "type" and
	 * "properties" fields.
	 */
	@Test
	void testEnsureValidInputSchemaAddsTypeAndPropertiesFields() {
		// Simulate a minimal schema without "type"
		String inputSchema = "{\"additionalProperties\":false}";

		String normalizedSchema = JsonSchemaUtils.ensureValidInputSchema(inputSchema);

		Map<String, Object> schemaMap = ModelOptionsUtils.jsonToMap(normalizedSchema);
		assertThat(schemaMap).isNotNull();

		// Verify both "type" and "properties" were added
		assertThat(schemaMap).containsKey("type");
		assertThat(schemaMap.get("type")).isEqualTo("object");
		assertThat(schemaMap).containsKey("properties");
		assertThat(schemaMap.get("properties")).isInstanceOf(Map.class);
	}

	/**
	 * Test that an empty or null schema is normalized to a minimal valid schema.
	 */
	@Test
	void testEnsureValidInputSchemaWithEmptySchema() {
		String inputSchema = "{}";

		String normalizedSchema = JsonSchemaUtils.ensureValidInputSchema(inputSchema);

		Map<String, Object> schemaMap = ModelOptionsUtils.jsonToMap(normalizedSchema);
		assertThat(schemaMap).isNotNull();
		assertThat(schemaMap).containsKey("type");
		assertThat(schemaMap.get("type")).isEqualTo("object");
		assertThat(schemaMap).containsKey("properties");
		assertThat(schemaMap.get("properties")).isInstanceOf(Map.class);
	}

	/**
	 * Test that a schema with existing "properties" field is not modified.
	 */
	@Test
	void testEnsureValidInputSchemaPreservesExistingProperties() {
		// A properly formed schema with properties
		String inputSchema = "{\"type\":\"object\",\"properties\":{\"cityName\":{\"type\":\"string\"}}}";

		String normalizedSchema = JsonSchemaUtils.ensureValidInputSchema(inputSchema);

		Map<String, Object> schemaMap = ModelOptionsUtils.jsonToMap(normalizedSchema);
		assertThat(schemaMap).isNotNull();
		assertThat(schemaMap).containsKey("type");
		assertThat(schemaMap).containsKey("properties");

		// Verify existing properties are preserved
		Map<String, Object> properties = (Map<String, Object>) schemaMap.get("properties");
		assertThat(properties).isNotEmpty();
		assertThat(properties).containsKey("cityName");
	}

	/**
	 * Test that a schema with "type": "string" (not "object") is not modified.
	 */
	@Test
	void testEnsureValidInputSchemaWithNonObjectType() {
		String inputSchema = "{\"type\":\"string\"}";

		String normalizedSchema = JsonSchemaUtils.ensureValidInputSchema(inputSchema);

		Map<String, Object> schemaMap = ModelOptionsUtils.jsonToMap(normalizedSchema);
		assertThat(schemaMap).isNotNull();
		assertThat(schemaMap).containsKey("type");
		assertThat(schemaMap.get("type")).isEqualTo("string");

		// Properties field should not be added for non-object types
		assertThat(schemaMap).doesNotContainKey("properties");
	}

	/**
	 * Test that null or empty input returns a valid minimal schema.
	 */
	@Test
	void testEnsureValidInputSchemaWithNullInput() {
		String normalizedSchema = JsonSchemaUtils.ensureValidInputSchema(null);

		// Null input should be handled gracefully
		assertThat(normalizedSchema).isNull();
	}

	/**
	 * Test that blank input returns the input as-is.
	 */
	@Test
	void testEnsureValidInputSchemaWithBlankInput() {
		String normalizedSchema = JsonSchemaUtils.ensureValidInputSchema("");

		assertThat(normalizedSchema).isEmpty();
	}

}
