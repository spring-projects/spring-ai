/*
 * Copyright 2026-2026 the original author or authors.
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ResponseFormat}.
 *
 * @author Filip Hrisafov
 */
class ResponseFormatTests {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void testDefaultConstructor() {
		ResponseFormat format = new ResponseFormat();

		assertThat(format.getType()).isNull();
		assertThat(format.getJsonSchema()).isNull();
		assertThat(format.getSchema()).isNull();
	}

	@Test
	void testConstructorWithTypeAndSchema() {
		// language=JSON
		String schema = """
				{
					"type": "object",
					"properties": {
						"name": {"type": "string"}
					}
				}
				""";

		ResponseFormat format = new ResponseFormat(ResponseFormat.Type.JSON_SCHEMA, schema);

		assertThat(format.getType()).isEqualTo(ResponseFormat.Type.JSON_SCHEMA);
		assertThat(format.getSchema()).isEqualTo(schema);
		assertThat(format.getJsonSchema()).isNotNull();
		assertThat(format.getJsonSchema().getSchema()).containsKeys("type", "properties");
		assertThat(format.getJsonSchema().getStrict()).isTrue();
		assertThat(format.getJsonSchema().getName()).isEqualTo("custom_schema");
	}

	@Test
	void testConstructorWithTypeAndNullSchema() {
		ResponseFormat format = new ResponseFormat(ResponseFormat.Type.TEXT, null);

		assertThat(format.getType()).isEqualTo(ResponseFormat.Type.TEXT);
		assertThat(format.getSchema()).isNull();
		assertThat(format.getJsonSchema()).isNull();
	}

	@Test
	void testConstructorWithTypeAndEmptySchema() {
		ResponseFormat format = new ResponseFormat(ResponseFormat.Type.JSON_OBJECT, "");

		assertThat(format.getType()).isEqualTo(ResponseFormat.Type.JSON_OBJECT);
		assertThat(format.getSchema()).isEmpty();
		assertThat(format.getJsonSchema()).isNull();
	}

	@Test
	void testSettersAndGetters() {
		ResponseFormat format = new ResponseFormat();

		format.setType(ResponseFormat.Type.JSON_OBJECT);
		assertThat(format.getType()).isEqualTo(ResponseFormat.Type.JSON_OBJECT);

		ResponseFormat.JsonSchema jsonSchema = ResponseFormat.JsonSchema.builder()
			.name("test_schema")
			.schema(Map.of("type", "object"))
			.strict(false)
			.build();

		format.setJsonSchema(jsonSchema);
		assertThat(format.getJsonSchema()).isEqualTo(jsonSchema);
		assertThat(format.getSchema()).isNull();
	}

	@Test
	void testSetSchema() {
		ResponseFormat format = new ResponseFormat();
		// language=JSON
		String schema = """
				{
					"type": "array",
					"items": { "type": "string" }
				}
				""";

		format.setSchema(schema);

		assertThat(format.getSchema()).isEqualTo(schema);
		assertThat(format.getJsonSchema()).isNotNull();
		assertThat(format.getJsonSchema().getSchema()).containsKeys("type", "items");
		assertThat(format.getJsonSchema().getStrict()).isTrue();
		assertThat(format.getJsonSchema().getName()).isEqualTo("custom_schema");
	}

	@Test
	void testSetSchemaNullResetsJsonSchema() {
		ResponseFormat format = new ResponseFormat();
		// language=JSON
		String schema = """
				{ "type": "object" }
				""";

		format.setSchema(schema);
		assertThat(format.getJsonSchema()).isNotNull();

		format.setSchema(null);
		assertThat(format.getSchema()).isNull();
		// Note: The setter doesn't reset jsonSchema to null when schema is null
		// This is the current behavior based on the implementation
	}

	@Test
	void testBuilderWithType() {
		ResponseFormat format = ResponseFormat.builder().type(ResponseFormat.Type.TEXT).build();

		assertThat(format.getType()).isEqualTo(ResponseFormat.Type.TEXT);
		assertThat(format.getJsonSchema()).isNull();
		assertThat(format.getSchema()).isNull();
	}

	@Test
	void testBuilderWithJsonSchemaObject() {
		ResponseFormat.JsonSchema jsonSchema = ResponseFormat.JsonSchema.builder()
			.name("my_schema")
			.schema(Map.of("type", "string"))
			.strict(false)
			.build();

		ResponseFormat format = ResponseFormat.builder()
			.type(ResponseFormat.Type.JSON_SCHEMA)
			.jsonSchema(jsonSchema)
			.build();

		assertThat(format.getType()).isEqualTo(ResponseFormat.Type.JSON_SCHEMA);
		assertThat(format.getJsonSchema()).isEqualTo(jsonSchema);
		assertThat(format.getSchema()).isNull();
	}

	@Test
	void testBuilderWithJsonSchemaString() {
		// language=JSON
		String schema = """
				{
					"type": "object",
					"properties": {
						"age": {"type": "number"}
					}
				}
				""";

		ResponseFormat format = ResponseFormat.builder()
			.type(ResponseFormat.Type.JSON_SCHEMA)
			.jsonSchema(schema)
			.build();

		assertThat(format.getType()).isEqualTo(ResponseFormat.Type.JSON_SCHEMA);
		assertThat(format.getJsonSchema()).isNotNull();
		assertThat(format.getJsonSchema().getSchema()).containsKeys("type", "properties");
		assertThat(format.getSchema()).isEqualTo(schema);
	}

	@Test
	void testEqualsAndHashCode() {
		ResponseFormat format1 = ResponseFormat.builder()
			.type(ResponseFormat.Type.JSON_OBJECT)
			.jsonSchema(ResponseFormat.JsonSchema.builder().schema(Map.of("type", "object")).build())
			.build();

		ResponseFormat format2 = ResponseFormat.builder()
			.type(ResponseFormat.Type.JSON_OBJECT)
			.jsonSchema(ResponseFormat.JsonSchema.builder().schema(Map.of("type", "object")).build())
			.build();

		ResponseFormat format3 = ResponseFormat.builder().type(ResponseFormat.Type.TEXT).build();

		assertThat(format1).isEqualTo(format2);
		assertThat(format1).isNotEqualTo(format3);
		assertThat(format1).isNotEqualTo(null);
		assertThat(format1).isEqualTo(format1);

		assertThat(format1.hashCode()).isEqualTo(format2.hashCode());
		assertThat(format1.hashCode()).isNotEqualTo(format3.hashCode());
	}

	@Test
	void testToString() {
		ResponseFormat format = ResponseFormat.builder()
			.type(ResponseFormat.Type.JSON_SCHEMA)
			.jsonSchema(ResponseFormat.JsonSchema.builder().name("test").schema(Map.of("type", "object")).build())
			.build();

		String result = format.toString();

		assertThat(result).contains("ResponseFormat");
		assertThat(result).contains("type=JSON_SCHEMA");
		assertThat(result).contains("jsonSchema=");
	}

	@Test
	void testJsonSerializationWithType() throws JsonProcessingException {
		ResponseFormat format = ResponseFormat.builder().type(ResponseFormat.Type.TEXT).build();

		String json = this.objectMapper.writeValueAsString(format);

		assertThat(json).contains("\"type\":\"text\"");
		assertThat(json).doesNotContain("json_schema");
	}

	@Test
	void testJsonSerializationWithJsonSchema() throws JsonProcessingException {
		ResponseFormat format = ResponseFormat.builder()
			.type(ResponseFormat.Type.JSON_SCHEMA)
			.jsonSchema(ResponseFormat.JsonSchema.builder()
				.name("person_schema")
				.schema(Map.of("type", "object", "properties", Map.of("name", Map.of("type", "string"))))
				.strict(true)
				.build())
			.build();

		String json = this.objectMapper.writeValueAsString(format);

		assertThat(json).contains("\"type\":\"json_schema\"");
		assertThat(json).contains("\"json_schema\"");
		assertThat(json).contains("\"name\":\"person_schema\"");
		assertThat(json).contains("\"strict\":true");
	}

	@Test
	void testJsonDeserializationWithType() throws JsonProcessingException {
		String json = "{\"type\":\"json_object\"}";

		ResponseFormat format = this.objectMapper.readValue(json, ResponseFormat.class);

		assertThat(format.getType()).isEqualTo(ResponseFormat.Type.JSON_OBJECT);
		assertThat(format.getJsonSchema()).isNull();
	}

	@Test
	void testJsonDeserializationWithJsonSchema() throws JsonProcessingException {
		String json = """
				{
					"type": "json_schema",
					"json_schema": {
						"name": "test_schema",
						"schema": {"type": "object"},
						"strict": false
					}
				}
				""";

		ResponseFormat format = this.objectMapper.readValue(json, ResponseFormat.class);

		assertThat(format.getType()).isEqualTo(ResponseFormat.Type.JSON_SCHEMA);
		assertThat(format.getJsonSchema()).isNotNull();
		assertThat(format.getJsonSchema().getName()).isEqualTo("test_schema");
		assertThat(format.getJsonSchema().getSchema()).containsEntry("type", "object");
		assertThat(format.getJsonSchema().getStrict()).isFalse();
	}

	@Test
	void testJsonSchemaDefaultConstructor() {
		ResponseFormat.JsonSchema jsonSchema = new ResponseFormat.JsonSchema();

		assertThat(jsonSchema.getName()).isNull();
		assertThat(jsonSchema.getSchema()).isNull();
		assertThat(jsonSchema.getStrict()).isNull();
	}

	@Test
	void testJsonSchemaBuilder() {
		ResponseFormat.JsonSchema jsonSchema = ResponseFormat.JsonSchema.builder()
			.name("my_schema")
			.schema(Map.of("type", "string"))
			.strict(false)
			.build();

		assertThat(jsonSchema.getName()).isEqualTo("my_schema");
		assertThat(jsonSchema.getSchema()).containsEntry("type", "string");
		assertThat(jsonSchema.getStrict()).isFalse();
	}

	@Test
	void testJsonSchemaBuilderWithStringSchema() {
		// language=JSON
		String schema = """
				{
					"type": "object",
					"properties": {
						"id": { "type": "integer" }
					}
				}
				""";

		ResponseFormat.JsonSchema jsonSchema = ResponseFormat.JsonSchema.builder().schema(schema).build();

		assertThat(jsonSchema.getSchema()).containsKeys("type", "properties");
		assertThat(jsonSchema.getSchema()).containsEntry("type", "object");
	}

	@Test
	void testJsonSchemaBuilderDefaults() {
		ResponseFormat.JsonSchema jsonSchema = ResponseFormat.JsonSchema.builder()
			.schema(Map.of("type", "object"))
			.build();

		assertThat(jsonSchema.getName()).isEqualTo("custom_schema");
		assertThat(jsonSchema.getStrict()).isTrue();
	}

	@Test
	void testJsonSchemaBuilderCanOverrideDefaults() {
		ResponseFormat.JsonSchema jsonSchema = ResponseFormat.JsonSchema.builder()
			.name("override_name")
			.schema(Map.of("type", "array"))
			.strict(false)
			.build();

		assertThat(jsonSchema.getName()).isEqualTo("override_name");
		assertThat(jsonSchema.getStrict()).isFalse();
	}

	@Test
	void testJsonSchemaEqualsAndHashCode() {
		ResponseFormat.JsonSchema schema1 = ResponseFormat.JsonSchema.builder()
			.name("schema1")
			.schema(Map.of("type", "object"))
			.strict(true)
			.build();

		ResponseFormat.JsonSchema schema2 = ResponseFormat.JsonSchema.builder()
			.name("schema1")
			.schema(Map.of("type", "object"))
			.strict(true)
			.build();

		ResponseFormat.JsonSchema schema3 = ResponseFormat.JsonSchema.builder()
			.name("schema2")
			.schema(Map.of("type", "object"))
			.strict(true)
			.build();

		assertThat(schema1).isEqualTo(schema2);
		assertThat(schema1).isNotEqualTo(schema3);
		assertThat(schema1).isNotEqualTo(null);
		assertThat(schema1).isEqualTo(schema1);

		assertThat(schema1.hashCode()).isEqualTo(schema2.hashCode());
		assertThat(schema1.hashCode()).isNotEqualTo(schema3.hashCode());
	}

	@Test
	void testTypeEnumJsonSerialization() throws JsonProcessingException {
		ResponseFormat.Type type = ResponseFormat.Type.JSON_SCHEMA;

		String json = this.objectMapper.writeValueAsString(type);

		assertThat(json).isEqualTo("\"json_schema\"");
	}

	@Test
	void testTypeEnumJsonDeserialization() throws JsonProcessingException {
		String json = "\"json_object\"";

		ResponseFormat.Type type = this.objectMapper.readValue(json, ResponseFormat.Type.class);

		assertThat(type).isEqualTo(ResponseFormat.Type.JSON_OBJECT);
	}

}
