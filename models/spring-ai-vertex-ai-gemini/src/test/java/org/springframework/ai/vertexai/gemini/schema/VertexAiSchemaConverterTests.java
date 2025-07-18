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

package org.springframework.ai.vertexai.gemini.schema;

import java.util.List;

import com.google.cloud.vertexai.api.Schema;
import com.google.cloud.vertexai.api.Type;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VertexAiSchemaConverterTests {

	@Test
	public void fromOpenApiSchemaShouldConvertGenericFields() {
		String openApiSchema = """
				{
					"type": "OBJECT",
					"format": "date-time",
					"title": "Title",
					"description": "Description",
					"nullable": true,
					"example": "Example",
					"default": "0"
				}""";

		Schema schema = VertexAiSchemaConverter.fromOpenApiSchema(openApiSchema);

		assertEquals(Type.OBJECT, schema.getType());
		assertEquals("date-time", schema.getFormat());
		assertEquals("Title", schema.getTitle());
		assertEquals("Description", schema.getDescription());
		assertTrue(schema.getNullable());
		assertEquals("Example", schema.getExample().getStringValue());
		assertEquals("0", schema.getDefault().getStringValue());
	}

	@Test
	public void fromOpenApiSchemaShouldConvertStringFields() {
		String openApiSchema = """
				{
					"type": "STRING",
					"enum": ["a", "b", "c"],
					"minLength": 1,
					"maxLength": 10,
					"pattern": "[0-9.]+"
				}""";

		Schema schema = VertexAiSchemaConverter.fromOpenApiSchema(openApiSchema);

		assertEquals(Type.STRING, schema.getType());
		assertEquals(List.of("a", "b", "c"), schema.getEnumList());
		assertEquals(1, schema.getMinLength());
		assertEquals(10, schema.getMaxLength());
		assertEquals("[0-9.]+", schema.getPattern());
	}

	@Test
	public void fromOpenApiSchemaShouldConvertIntegerAndNumberFields() {
		String openApiSchema = """
				{
					"anyOf": [{"type": "INTEGER"}, {"type": "NUMBER"}],
					"minimum": 0,
					"maximum": 100
				}""";

		Schema schema = VertexAiSchemaConverter.fromOpenApiSchema(openApiSchema);

		assertEquals(Type.TYPE_UNSPECIFIED, schema.getType());
		assertEquals(Type.INTEGER, schema.getAnyOf(0).getType());
		assertEquals(Type.NUMBER, schema.getAnyOf(1).getType());
		assertEquals(0, schema.getMinimum());
		assertEquals(100, schema.getMaximum());
	}

	@Test
	public void fromOpenApiSchemaShouldConvertArrayFields() {
		String openApiSchema = """
				{
					"type": "ARRAY",
					"items": {
						"type": "BOOLEAN"
					},
					"minItems": 1,
					"maxItems": 5
				}""";

		Schema schema = VertexAiSchemaConverter.fromOpenApiSchema(openApiSchema);

		assertEquals(Type.ARRAY, schema.getType());
		assertEquals(Type.BOOLEAN, schema.getItems().getType());
		assertEquals(1, schema.getMinItems());
		assertEquals(5, schema.getMaxItems());
	}

	@Test
	public void fromOpenApiSchemaShouldConvertObjectFields() {
		String openApiSchema = """
				{
					"type": "OBJECT",
					"properties": {
						"property1": {
							"type": "STRING"
						},
						"property2": {
							"type": "INTEGER"
						}
					},
					"minProperties": 1,
					"maxProperties": 2,
					"required": ["property1"],
					"propertyOrdering": ["property1", "property2"]
				}""";

		Schema schema = VertexAiSchemaConverter.fromOpenApiSchema(openApiSchema);

		assertEquals(Type.OBJECT, schema.getType());
		assertEquals(2, schema.getPropertiesMap().size());
		assertEquals(1, schema.getMinProperties());
		assertEquals(2, schema.getMaxProperties());
		assertEquals(List.of("property1"), schema.getRequiredList());
		assertEquals(List.of("property1", "property2"), schema.getPropertyOrderingList());
	}

}
