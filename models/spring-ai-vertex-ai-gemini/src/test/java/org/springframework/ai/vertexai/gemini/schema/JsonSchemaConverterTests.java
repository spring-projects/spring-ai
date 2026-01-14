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

package org.springframework.ai.vertexai.gemini.schema;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link JsonSchemaConverter}.
 *
 * @author Christian Tzolov
 */
class JsonSchemaConverterTests {

	@Test
	void fromJsonShouldParseValidJson() {
		String json = "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"}}}";
		ObjectNode result = JsonSchemaConverter.fromJson(json);

		assertThat(result.get("type").asText()).isEqualTo("object");
		assertThat(result.get("properties").get("name").get("type").asText()).isEqualTo("string");
	}

	@Test
	void fromJsonShouldThrowOnInvalidJson() {
		String invalidJson = "{invalid:json}";
		assertThatThrownBy(() -> JsonSchemaConverter.fromJson(invalidJson)).isInstanceOf(RuntimeException.class)
			.hasMessageContaining("Failed to parse JSON");
	}

	@Test
	void convertToOpenApiSchemaShouldThrowOnNullInput() {
		assertThatThrownBy(() -> JsonSchemaConverter.convertToOpenApiSchema(null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("JSON Schema node must not be null");
	}

	@Nested
	class SchemaConversionTests {

		@Test
		void shouldConvertBasicSchema() {
			String json = """
					{
						"type": "object",
						"properties": {
							"name": {
								"type": "string",
								"description": "The name property"
							}
						},
						"required": ["name"]
					}
					""";

			ObjectNode result = JsonSchemaConverter.convertToOpenApiSchema(JsonSchemaConverter.fromJson(json));

			assertThat(result.get("openapi").asText()).isEqualTo("3.0.0");
			assertThat(result.get("type").asText()).isEqualTo("object");
			assertThat(result.get("properties").get("name").get("type").asText()).isEqualTo("string");
			assertThat(result.get("properties").get("name").get("description").asText()).isEqualTo("The name property");
			assertThat(result.get("required").get(0).asText()).isEqualTo("name");
		}

		@Test
		void shouldHandleArrayTypes() {
			String json = """
					{
						"type": "object",
						"properties": {
							"tags": {
								"type": "array",
								"items": {
									"type": "string"
								}
							}
						}
					}
					""";

			ObjectNode result = JsonSchemaConverter.convertToOpenApiSchema(JsonSchemaConverter.fromJson(json));

			assertThat(result.get("properties").get("tags").get("type").asText()).isEqualTo("array");
			assertThat(result.get("properties").get("tags").get("items").get("type").asText()).isEqualTo("string");
		}

		@Test
		void shouldHandleAdditionalProperties() {
			String json = """
					{
						"type": "object",
						"additionalProperties": {
							"type": "string"
						}
					}
					""";

			ObjectNode result = JsonSchemaConverter.convertToOpenApiSchema(JsonSchemaConverter.fromJson(json));

			assertThat(result.get("additionalProperties").get("type").asText()).isEqualTo("string");
		}

		@Test
		void shouldHandleCombiningSchemas() {
			String json = """
					{
						"type": "object",
						"allOf": [
							{"type": "object", "properties": {"name": {"type": "string"}}},
							{"type": "object", "properties": {"age": {"type": "integer"}}}
						]
					}
					""";

			ObjectNode result = JsonSchemaConverter.convertToOpenApiSchema(JsonSchemaConverter.fromJson(json));

			assertThat(result.get("allOf")).isNotNull();
			assertThat(result.get("allOf").isArray()).isTrue();
			assertThat(result.get("allOf").size()).isEqualTo(2);
		}

		@Test
		void shouldCopyCommonProperties() {
			String json = """
					{
						"type": "string",
						"format": "email",
						"description": "Email address",
						"minLength": 5,
						"maxLength": 100,
						"pattern": "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\\\.[a-zA-Z]{2,}$",
						"example": "user@example.com",
						"deprecated": false
					}
					""";

			ObjectNode result = JsonSchemaConverter.convertToOpenApiSchema(JsonSchemaConverter.fromJson(json));

			assertThat(result.get("type").asText()).isEqualTo("string");
			assertThat(result.get("format").asText()).isEqualTo("email");
			assertThat(result.get("description").asText()).isEqualTo("Email address");
			assertThat(result.get("minLength").asInt()).isEqualTo(5);
			assertThat(result.get("maxLength").asInt()).isEqualTo(100);
			assertThat(result.get("pattern").asText()).isEqualTo("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
			assertThat(result.get("example").asText()).isEqualTo("user@example.com");
			assertThat(result.get("deprecated").asBoolean()).isFalse();
		}

		@Test
		void shouldHandleNestedObjects() {
			String json = """
					{
						"type": "object",
						"properties": {
							"user": {
								"type": "object",
								"properties": {
									"address": {
										"type": "object",
										"properties": {
											"street": {"type": "string"},
											"city": {"type": "string"}
										}
									}
								}
							}
						}
					}
					""";

			ObjectNode result = JsonSchemaConverter.convertToOpenApiSchema(JsonSchemaConverter.fromJson(json));

			assertThat(result.get("properties")
				.get("user")
				.get("properties")
				.get("address")
				.get("properties")
				.get("street")
				.get("type")
				.asText()).isEqualTo("string");
			assertThat(result.get("properties")
				.get("user")
				.get("properties")
				.get("address")
				.get("properties")
				.get("city")
				.get("type")
				.asText()).isEqualTo("string");
		}

	}

}
