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

package org.springframework.ai.util.json.schema;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

import org.springframework.ai.util.JsonHelper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link JsonSchemaUtils}.
 *
 * @author Ilayaperumal Gopinathan
 */
class JsonSchemaUtilsTests {

	private static final JsonHelper jsonHelper = new JsonHelper();

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

		Map<String, Object> schemaMap = jsonHelper.fromJsonToMap(normalizedSchema);
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

	@Test
	void testEnsureValidInputSchemaAddsRequiredFieldForObjectSchema() {
		String inputSchema = "{\"type\":\"object\"}";

		String normalizedSchema = JsonSchemaUtils.ensureValidInputSchema(inputSchema);

		Map<String, Object> schemaMap = jsonHelper.fromJsonToMap(normalizedSchema);
		assertThat(schemaMap).isNotNull();
		assertThat(schemaMap).containsKey("required");
		assertThat((List<?>) schemaMap.get("required")).isEmpty();
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

		Map<String, Object> schemaMap = jsonHelper.fromJsonToMap(normalizedSchema);
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

		Map<String, Object> schemaMap = jsonHelper.fromJsonToMap(normalizedSchema);
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

		Map<String, Object> schemaMap = jsonHelper.fromJsonToMap(normalizedSchema);
		assertThat(schemaMap).isNotNull();
		assertThat(schemaMap).containsKey("type");
		assertThat(schemaMap).containsKey("properties");

		// Verify existing properties are preserved
		Map<String, Object> properties = (Map<String, Object>) schemaMap.get("properties");
		assertThat(properties).isNotEmpty();
		assertThat(properties).containsKey("cityName");
	}

	@Test
	void testEnsureValidInputSchemaPreservesExistingRequiredFields() {
		String inputSchema = """
				{"type":"object","properties":{"cityName":{"type":"string"}},"required":["cityName"]}
				""";

		String normalizedSchema = JsonSchemaUtils.ensureValidInputSchema(inputSchema);

		Map<String, Object> schemaMap = jsonHelper.fromJsonToMap(normalizedSchema);
		assertThat(schemaMap).isNotNull();
		assertThat(schemaMap.get("required")).isEqualTo(List.of("cityName"));
	}

	@Test
	void ensureValidInputSchemaNormalizesNestedObjectSchemas() {
		String inputSchema = """
				{
					"type": "object",
					"properties": {
						"filters": {
							"type": "object",
							"properties": {
								"owner": {
									"type": "object"
								}
							}
						},
						"matches": {
							"type": "array",
							"items": {
								"type": "object"
							}
						},
						"metadata": {
							"type": "object",
							"additionalProperties": {
								"type": "object"
							}
						}
					},
					"oneOf": [
						{
							"type": "object"
						}
					]
				}
				""";

		String normalizedSchema = JsonSchemaUtils.ensureValidInputSchema(inputSchema);

		Map<String, Object> schemaMap = jsonHelper.fromJsonToMap(normalizedSchema);
		Map<String, Object> properties = (Map<String, Object>) schemaMap.get("properties");
		Map<String, Object> filters = (Map<String, Object>) properties.get("filters");
		Map<String, Object> filterProperties = (Map<String, Object>) filters.get("properties");
		Map<String, Object> owner = (Map<String, Object>) filterProperties.get("owner");
		Map<String, Object> matches = (Map<String, Object>) properties.get("matches");
		Map<String, Object> items = (Map<String, Object>) matches.get("items");
		Map<String, Object> metadata = (Map<String, Object>) properties.get("metadata");
		Map<String, Object> metadataValues = (Map<String, Object>) metadata.get("additionalProperties");
		List<Map<String, Object>> oneOf = (List<Map<String, Object>>) schemaMap.get("oneOf");

		assertThat((List<?>) filters.get("required")).isEmpty();
		assertThat((Map<?, ?>) owner.get("properties")).isEmpty();
		assertThat((List<?>) owner.get("required")).isEmpty();
		assertThat((Map<?, ?>) items.get("properties")).isEmpty();
		assertThat((List<?>) items.get("required")).isEmpty();
		assertThat((Map<?, ?>) metadataValues.get("properties")).isEmpty();
		assertThat((List<?>) metadataValues.get("required")).isEmpty();
		assertThat((Map<?, ?>) oneOf.get(0).get("properties")).isEmpty();
		assertThat((List<?>) oneOf.get(0).get("required")).isEmpty();
	}

	@Test
	void ensureValidInputSchemaDoesNotTreatPropertyNamesAsSchemaKeywords() {
		String inputSchema = """
				{
					"type": "object",
					"properties": {
						"type": {
							"type": "string"
						},
						"properties": {
							"type": "string"
						}
					}
				}
				""";

		String normalizedSchema = JsonSchemaUtils.ensureValidInputSchema(inputSchema);

		Map<String, Object> schemaMap = jsonHelper.fromJsonToMap(normalizedSchema);
		Map<String, Object> properties = (Map<String, Object>) schemaMap.get("properties");
		Map<String, Object> typeProperty = (Map<String, Object>) properties.get("type");
		Map<String, Object> propertiesProperty = (Map<String, Object>) properties.get("properties");

		assertThat(properties).containsOnlyKeys("type", "properties");
		assertThat(typeProperty).containsEntry("type", "string");
		assertThat(typeProperty).doesNotContainKeys("properties", "required");
		assertThat(propertiesProperty).containsEntry("type", "string");
		assertThat(propertiesProperty).doesNotContainKeys("properties", "required");
	}

	/**
	 * Test that a schema with "type": "string" (not "object") is not modified.
	 */
	@Test
	void testEnsureValidInputSchemaWithNonObjectType() {
		String inputSchema = "{\"type\":\"string\"}";

		String normalizedSchema = JsonSchemaUtils.ensureValidInputSchema(inputSchema);

		Map<String, Object> schemaMap = jsonHelper.fromJsonToMap(normalizedSchema);
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

	@Test
	void getJsonSchemaCanRunConcurrently() throws Exception {
		List<ObjectNode> schemas = generateConcurrently(() -> JsonSchemaUtils.getJsonSchema(OrderedStatement.class));

		assertThat(schemas).hasSize(240);
		assertThat(schemas).allSatisfy(schema -> assertThat(schema.toString()).contains("\"properties\""));

	}

	private static <T> List<T> generateConcurrently(Callable<T> generator) throws Exception {
		int threadCount = 12;
		int callCount = 240;
		ExecutorService executor = Executors.newFixedThreadPool(threadCount);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<T>> futures = new ArrayList<>();
			for (int i = 0; i < callCount; i++) {
				futures.add(executor.submit(() -> {
					start.await();
					return generator.call();
				}));
			}
			start.countDown();
			List<T> schemas = new ArrayList<>();
			for (Future<T> future : futures) {
				schemas.add(future.get(30, TimeUnit.SECONDS));
			}
			return schemas;
		}
		finally {
			executor.shutdownNow();
		}
	}

	@JsonPropertyOrder({ "accountId", "accountName", "currency", "totals" })
	record OrderedStatement(@JsonProperty(required = true) String accountId,
			@JsonProperty(required = true) String accountName, String currency, Map<String, Double> totals) {

	}

}
