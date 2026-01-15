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

package org.springframework.ai.tool.augment;

import java.util.List;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.augment.ToolInputSchemaAugmenter.AugmentedArgumentType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Comprehensive test suite for {@link ToolInputSchemaAugmenter} class. Tests schema
 * augmentation, record field extraction, and JSON manipulation.
 *
 * @author Christian Tzolov
 */
class ToolInputSchemaAugmenterTest {

	// Test record classes
	public record SimpleRecord(@ToolParam(description = "A simple string field", required = true) String name,
			@ToolParam(description = "A simple integer field", required = false) int age) {
	}

	public record ComplexRecord(@ToolParam(description = "List of strings", required = true) List<String> items,
			@ToolParam(description = "Nested object", required = false) NestedRecord nested) {
	}

	public record NestedRecord(@ToolParam(description = "Nested field", required = true) String value) {
	}

	public record RecordWithoutAnnotations(String field1, int field2) {
	}

	public record MixedAnnotationsRecord(@ToolParam(description = "Annotated field", required = true) String annotated,
			String notAnnotated) {
	}

	@Nested
	@DisplayName("AugmentedArgumentType Tests")
	class AugmentedArgumentTypeTests {

		@Test
		@DisplayName("Should create AugmentedArgumentType with all parameters")
		void shouldCreateAugmentedArgumentType() {
			AugmentedArgumentType argType = new AugmentedArgumentType("testName", String.class, "Test description",
					true);

			assertEquals("testName", argType.name());
			assertEquals(String.class, argType.type());
			assertEquals("Test description", argType.description());
			assertTrue(argType.required());
		}

		@Test
		@DisplayName("Should handle optional parameters")
		void shouldHandleOptionalParameters() {
			AugmentedArgumentType argType = new AugmentedArgumentType("optionalField", Integer.class, null, false);

			assertEquals("optionalField", argType.name());
			assertEquals(Integer.class, argType.type());
			assertNull(argType.description());
			assertFalse(argType.required());
		}

	}

	@Nested
	@DisplayName("toAugmentedArgumentTypes Tests")
	class ToAugmentedArgumentTypesTests {

		@Test
		@DisplayName("Should extract argument types from simple record")
		void shouldExtractArgumentTypesFromSimpleRecord() {
			List<AugmentedArgumentType> argumentTypes = ToolInputSchemaAugmenter
				.toAugmentedArgumentTypes(SimpleRecord.class);

			assertEquals(2, argumentTypes.size());

			AugmentedArgumentType nameArg = argumentTypes.stream()
				.filter(arg -> "name".equals(arg.name()))
				.findFirst()
				.orElseThrow();
			assertEquals("A simple string field", nameArg.description());
			assertTrue(nameArg.required());
			assertEquals(String.class, nameArg.type());

			AugmentedArgumentType ageArg = argumentTypes.stream()
				.filter(arg -> "age".equals(arg.name()))
				.findFirst()
				.orElseThrow();
			assertEquals("A simple integer field", ageArg.description());
			assertFalse(ageArg.required());
			assertEquals(int.class, ageArg.type());
		}

		@Test
		@DisplayName("Should extract argument types from complex record")
		void shouldExtractArgumentTypesFromComplexRecord() {
			List<AugmentedArgumentType> argumentTypes = ToolInputSchemaAugmenter
				.toAugmentedArgumentTypes(ComplexRecord.class);

			assertEquals(2, argumentTypes.size());

			AugmentedArgumentType itemsArg = argumentTypes.stream()
				.filter(arg -> "items".equals(arg.name()))
				.findFirst()
				.orElseThrow();
			assertEquals("List of strings", itemsArg.description());
			assertTrue(itemsArg.required());

			AugmentedArgumentType nestedArg = argumentTypes.stream()
				.filter(arg -> "nested".equals(arg.name()))
				.findFirst()
				.orElseThrow();
			assertEquals("Nested object", nestedArg.description());
			assertFalse(nestedArg.required());
		}

		@Test
		@DisplayName("Should handle record without annotations")
		void shouldHandleRecordWithoutAnnotations() {
			List<AugmentedArgumentType> argumentTypes = ToolInputSchemaAugmenter
				.toAugmentedArgumentTypes(RecordWithoutAnnotations.class);

			assertEquals(2, argumentTypes.size());

			for (AugmentedArgumentType argType : argumentTypes) {
				assertEquals("no description", argType.description());
				assertFalse(argType.required());
			}
		}

		@Test
		@DisplayName("Should handle mixed annotations record")
		void shouldHandleMixedAnnotationsRecord() {
			List<AugmentedArgumentType> argumentTypes = ToolInputSchemaAugmenter
				.toAugmentedArgumentTypes(MixedAnnotationsRecord.class);

			assertEquals(2, argumentTypes.size());

			AugmentedArgumentType annotatedArg = argumentTypes.stream()
				.filter(arg -> "annotated".equals(arg.name()))
				.findFirst()
				.orElseThrow();
			assertEquals("Annotated field", annotatedArg.description());
			assertTrue(annotatedArg.required());

			AugmentedArgumentType notAnnotatedArg = argumentTypes.stream()
				.filter(arg -> "notAnnotated".equals(arg.name()))
				.findFirst()
				.orElseThrow();
			assertEquals("no description", notAnnotatedArg.description());
			assertFalse(notAnnotatedArg.required());
		}

		@Test
		@DisplayName("Should throw exception for non-record class")
		void shouldThrowExceptionForNonRecordClass() {
			// Test with a regular class that is not a record
			// We'll use reflection to bypass the generic type checking
			assertThrows(RuntimeException.class, () -> {
				try {
					java.lang.reflect.Method method = ToolInputSchemaAugmenter.class
						.getMethod("toAugmentedArgumentTypes", Class.class);
					method.invoke(null, String.class);
				}
				catch (java.lang.reflect.InvocationTargetException e) {
					if (e.getCause() instanceof RuntimeException) {
						throw (RuntimeException) e.getCause();
					}
					throw new RuntimeException(e.getCause());
				}
				catch (Exception e) {
					throw new RuntimeException(e);
				}
			});
		}

	}

	@Nested
	@DisplayName("augmentToolInputSchema Tests")
	class AugmentToolInputSchemaTests {

		private final String baseSchema = """
				{
					"$schema": "https://json-schema.org/draft/2020-12/schema",
					"title": "Test Schema",
					"type": "object",
					"properties": {
						"existingField": {
							"type": "string",
							"description": "An existing field"
						}
					},
					"required": ["existingField"]
				}
				""";

		@Test
		@DisplayName("Should augment schema with single property")
		void shouldAugmentSchemaWithSingleProperty() throws Exception {
			String augmentedSchema = ToolInputSchemaAugmenter.augmentToolInputSchema(this.baseSchema, "newField",
					String.class, "A new field", true);

			JsonNode schemaNode = JsonMapper.shared().readTree(augmentedSchema);

			// Check that new property was added
			assertTrue(schemaNode.get("properties").has("newField"));
			JsonNode newFieldNode = schemaNode.get("properties").get("newField");
			assertEquals("A new field", newFieldNode.get("description").asText());

			// Check that required array was updated
			JsonNode requiredArray = schemaNode.get("required");
			assertTrue(requiredArray.isArray());
			boolean foundNewField = false;
			for (JsonNode requiredField : requiredArray) {
				if ("newField".equals(requiredField.asText())) {
					foundNewField = true;
					break;
				}
			}
			assertTrue(foundNewField);

			// Check that existing field is still there
			assertTrue(schemaNode.get("properties").has("existingField"));
		}

		@Test
		@DisplayName("Should augment schema with multiple properties")
		void shouldAugmentSchemaWithMultipleProperties() throws Exception {
			List<AugmentedArgumentType> argumentTypes = List.of(
					new AugmentedArgumentType("field1", String.class, "First field", true),
					new AugmentedArgumentType("field2", Integer.class, "Second field", false));

			String augmentedSchema = ToolInputSchemaAugmenter.augmentToolInputSchema(this.baseSchema, argumentTypes);

			JsonNode schemaNode = JsonMapper.shared().readTree(augmentedSchema);

			// Check that both new properties were added
			assertTrue(schemaNode.get("properties").has("field1"));
			assertTrue(schemaNode.get("properties").has("field2"));

			// Check descriptions
			assertEquals("First field", schemaNode.get("properties").get("field1").get("description").asText());
			assertEquals("Second field", schemaNode.get("properties").get("field2").get("description").asText());

			// Check required array - should contain field1 but not field2
			JsonNode requiredArray = schemaNode.get("required");
			boolean foundField1 = false;
			boolean foundField2 = false;
			for (JsonNode requiredField : requiredArray) {
				if ("field1".equals(requiredField.asText())) {
					foundField1 = true;
				}
				else if ("field2".equals(requiredField.asText())) {
					foundField2 = true;
				}
			}
			assertTrue(foundField1);
			assertFalse(foundField2);
		}

		@Test
		@DisplayName("Should handle schema without existing properties")
		void shouldHandleSchemaWithoutExistingProperties() throws Exception {
			String minimalSchema = """
					{
						"$schema": "https://json-schema.org/draft/2020-12/schema",
						"title": "Minimal Schema",
						"type": "object"
					}
					""";

			String augmentedSchema = ToolInputSchemaAugmenter.augmentToolInputSchema(minimalSchema, "newField",
					String.class, "A new field", true);

			JsonNode schemaNode = JsonMapper.shared().readTree(augmentedSchema);

			// Check that properties object was created
			assertTrue(schemaNode.has("properties"));
			assertTrue(schemaNode.get("properties").has("newField"));

			// Check that required array was created
			assertTrue(schemaNode.has("required"));
			JsonNode requiredArray = schemaNode.get("required");
			assertEquals(1, requiredArray.size());
			assertEquals("newField", requiredArray.get(0).asText());
		}

		@Test
		@DisplayName("Should handle schema without existing required array")
		void shouldHandleSchemaWithoutExistingRequiredArray() throws Exception {
			String schemaWithoutRequired = """
					{
						"$schema": "https://json-schema.org/draft/2020-12/schema",
						"title": "Test Schema",
						"type": "object",
						"properties": {
							"existingField": {
								"type": "string"
							}
						}
					}
					""";

			String augmentedSchema = ToolInputSchemaAugmenter.augmentToolInputSchema(schemaWithoutRequired, "newField",
					String.class, "A new field", true);

			JsonNode schemaNode = JsonMapper.shared().readTree(augmentedSchema);

			// Check that required array was created
			assertTrue(schemaNode.has("required"));
			JsonNode requiredArray = schemaNode.get("required");
			assertEquals(1, requiredArray.size());
			assertEquals("newField", requiredArray.get(0).asText());
		}

		@Test
		@DisplayName("Should handle empty description")
		void shouldHandleEmptyDescription() throws Exception {
			String augmentedSchema = ToolInputSchemaAugmenter.augmentToolInputSchema(this.baseSchema, "newField",
					String.class, "", false);

			JsonNode schemaNode = JsonMapper.shared().readTree(augmentedSchema);
			JsonNode newFieldNode = schemaNode.get("properties").get("newField");

			// Should not have description property when empty
			assertFalse(newFieldNode.has("description"));
		}

		@Test
		@DisplayName("Should handle null description")
		void shouldHandleNullDescription() throws Exception {
			String augmentedSchema = ToolInputSchemaAugmenter.augmentToolInputSchema(this.baseSchema, "newField",
					String.class, null, false);

			JsonNode schemaNode = JsonMapper.shared().readTree(augmentedSchema);
			JsonNode newFieldNode = schemaNode.get("properties").get("newField");

			// Should not have description property when null
			assertFalse(newFieldNode.has("description"));
		}

		@Test
		@DisplayName("Should throw exception for invalid JSON schema")
		void shouldThrowExceptionForInvalidJsonSchema() {
			String invalidSchema = "{ invalid json }";

			assertThrows(RuntimeException.class, () -> ToolInputSchemaAugmenter.augmentToolInputSchema(invalidSchema,
					"newField", String.class, "description", false));
		}

		@Test
		@DisplayName("Should augment schema using record class")
		void shouldAugmentSchemaUsingRecordClass() throws Exception {
			List<AugmentedArgumentType> argumentTypes = ToolInputSchemaAugmenter
				.toAugmentedArgumentTypes(SimpleRecord.class);
			String augmentedSchema = ToolInputSchemaAugmenter.augmentToolInputSchema(this.baseSchema, argumentTypes);

			JsonNode schemaNode = JsonMapper.shared().readTree(augmentedSchema);

			// Check that record fields were added
			assertTrue(schemaNode.get("properties").has("name"));
			assertTrue(schemaNode.get("properties").has("age"));

			// Check descriptions from annotations
			assertEquals("A simple string field", schemaNode.get("properties").get("name").get("description").asText());
			assertEquals("A simple integer field", schemaNode.get("properties").get("age").get("description").asText());

			// Check required array - should contain name but not age
			JsonNode requiredArray = schemaNode.get("required");
			boolean foundName = false;
			boolean foundAge = false;
			for (JsonNode requiredField : requiredArray) {
				if ("name".equals(requiredField.asText())) {
					foundName = true;
				}
				else if ("age".equals(requiredField.asText())) {
					foundAge = true;
				}
			}
			assertTrue(foundName);
			assertFalse(foundAge);
		}

	}

	@Nested
	@DisplayName("Integration Tests")
	class IntegrationTests {

		@Test
		@DisplayName("Should handle complete workflow from record to augmented schema")
		void shouldHandleCompleteWorkflow() throws Exception {
			// Start with a basic schema
			String originalSchema = """
					{
						"type": "object",
						"properties": {
							"productId": {
								"type": "integer",
								"description": "Product identifier"
							}
						},
						"required": ["productId"]
					}
					""";

			// Extract argument types from record
			List<AugmentedArgumentType> argumentTypes = ToolInputSchemaAugmenter
				.toAugmentedArgumentTypes(SimpleRecord.class);

			// Augment the schema
			String augmentedSchema = ToolInputSchemaAugmenter.augmentToolInputSchema(originalSchema, argumentTypes);

			// Verify the result
			JsonNode schemaNode = JsonMapper.shared().readTree(augmentedSchema);

			// Original field should still be there
			assertTrue(schemaNode.get("properties").has("productId"));

			// New fields should be added
			assertTrue(schemaNode.get("properties").has("name"));
			assertTrue(schemaNode.get("properties").has("age"));

			// Required array should contain both original and new required fields
			JsonNode requiredArray = schemaNode.get("required");
			boolean foundProductId = false;
			boolean foundName = false;
			for (JsonNode requiredField : requiredArray) {
				String fieldName = requiredField.asText();
				if ("productId".equals(fieldName)) {
					foundProductId = true;
				}
				else if ("name".equals(fieldName)) {
					foundName = true;
				}
			}
			assertTrue(foundProductId);
			assertTrue(foundName);
		}

		@Test
		@DisplayName("Should preserve schema structure and metadata")
		void shouldPreserveSchemaStructureAndMetadata() throws Exception {
			String complexSchema = """
					{
						"$schema": "https://json-schema.org/draft/2020-12/schema",
						"$id": "https://example.com/product.schema.json",
						"title": "Product Schema",
						"description": "A product from catalog",
						"type": "object",
						"properties": {
							"productId": {
								"type": "integer",
								"description": "Product identifier",
								"minimum": 1
							}
						},
						"required": ["productId"],
						"additionalProperties": false
					}
					""";

			String augmentedSchema = ToolInputSchemaAugmenter.augmentToolInputSchema(complexSchema, "newField",
					String.class, "New field", false);

			JsonNode schemaNode = JsonMapper.shared().readTree(augmentedSchema);

			// Check that metadata is preserved
			assertEquals("https://json-schema.org/draft/2020-12/schema", schemaNode.get("$schema").asText());
			assertEquals("https://example.com/product.schema.json", schemaNode.get("$id").asText());
			assertEquals("Product Schema", schemaNode.get("title").asText());
			assertEquals("A product from catalog", schemaNode.get("description").asText());
			assertEquals("object", schemaNode.get("type").asText());
			assertFalse(schemaNode.get("additionalProperties").asBoolean());

			// Check that original property constraints are preserved
			JsonNode productIdNode = schemaNode.get("properties").get("productId");
			assertEquals(1, productIdNode.get("minimum").asInt());

			// Check that new field was added
			assertTrue(schemaNode.get("properties").has("newField"));
		}

	}

}
