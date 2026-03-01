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

/**
 * @author Christian Tzolov
 * @since 1.0.0
 */

import java.util.Map;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import org.springframework.ai.util.json.JsonParser;
import org.springframework.util.Assert;

/**
 * Utility class for converting JSON Schema to OpenAPI schema format.
 */
public final class JsonSchemaConverter {

	private JsonSchemaConverter() {
		// Prevent instantiation
	}

	/**
	 * Parses a JSON string into an ObjectNode.
	 * @param jsonString The JSON string to parse
	 * @return ObjectNode containing the parsed JSON
	 * @throws RuntimeException if the JSON string cannot be parsed
	 */
	public static ObjectNode fromJson(String jsonString) {
		try {
			return (ObjectNode) JsonParser.getJsonMapper().readTree(jsonString);
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to parse JSON: " + jsonString, e);
		}
	}

	/**
	 * Converts a JSON Schema ObjectNode to OpenAPI schema format.
	 * @param jsonSchemaNode The input JSON Schema as ObjectNode
	 * @return ObjectNode containing the OpenAPI schema
	 * @throws IllegalArgumentException if jsonSchemaNode is null
	 */
	public static ObjectNode convertToOpenApiSchema(ObjectNode jsonSchemaNode) {
		Assert.notNull(jsonSchemaNode, "JSON Schema node must not be null");
		Assert.isTrue(!jsonSchemaNode.has("$defs"), "Google's Structured Output schema doesn't support $defs property");

		try {
			// Convert to OpenAPI schema using our custom conversion logic
			ObjectNode openApiSchema = convertSchema(jsonSchemaNode, JsonParser.getJsonMapper().getNodeFactory());

			// Add OpenAPI-specific metadata
			if (!openApiSchema.has("openapi")) {
				openApiSchema.put("openapi", "3.0.0");
			}

			return openApiSchema;
		}
		catch (Exception e) {
			throw new IllegalStateException("Failed to convert JSON Schema to OpenAPI format: " + e.getMessage(), e);
		}
	}

	/**
	 * Copies common properties from source to target node.
	 * @param source The source ObjectNode containing JSON Schema properties
	 * @param target The target ObjectNode to copy properties to
	 */
	private static void copyCommonProperties(ObjectNode source, ObjectNode target) {
		Assert.notNull(source, "Source node must not be null");
		Assert.notNull(target, "Target node must not be null");
		String[] commonProperties = {
				// Core schema properties
				"format", "description", "default", "maximum", "minimum", "maxLength", "minLength", "pattern", "enum",
				"multipleOf", "uniqueItems",
				// OpenAPI specific properties
				"example", "deprecated", "readOnly", "writeOnly", "discriminator", "xml", "externalDocs" };

		for (String prop : commonProperties) {
			if (source.has(prop)) {
				target.set(prop, source.get(prop));
			}
		}
	}

	/**
	 * Handles JSON Schema specific attributes and converts them to OpenAPI format.
	 * @param source The source ObjectNode containing JSON Schema
	 * @param target The target ObjectNode to store OpenAPI schema
	 * @param factory The JsonNodeFactory to create new nodes
	 */
	private static void handleJsonSchemaSpecifics(ObjectNode source, ObjectNode target,
			com.fasterxml.jackson.databind.node.JsonNodeFactory factory) {
		Assert.notNull(source, "Source node must not be null");
		Assert.notNull(target, "Target node must not be null");
		Assert.notNull(factory, "JsonNodeFactory must not be null");

		// Handle nullable types
		JsonNode typeNode = source.get("type");
		boolean nullable = false;
		if (typeNode != null) {
			if (typeNode.isArray()) {
				ArrayNode nonNullTypes = factory.arrayNode();
				for (JsonNode typeValue : typeNode) {
					if (typeValue.isTextual() && "null".equals(typeValue.asText())) {
						nullable = true;
					}
					else {
						nonNullTypes.add(typeValue);
					}
				}
				if (nonNullTypes.size() == 1) {
					target.set("type", nonNullTypes.get(0));
				}
				else if (nonNullTypes.size() > 1) {
					target.set("type", nonNullTypes);
				}
			}
			else if (typeNode.isTextual() && "null".equals(typeNode.asText())) {
				nullable = true;
			}
			else {
				target.set("type", typeNode);
			}
		}
		if (source.has("nullable")) {
			target.set("nullable", source.get("nullable"));
		}
		if (nullable) {
			target.put("nullable", true);
		}

		// Handle properties
		if (source.has("properties")) {
			ObjectNode properties = target.putObject("properties");
			var fields = source.get("properties").properties();
			for (Map.Entry<String, JsonNode> entry : fields) {
				if (entry.getValue() instanceof ObjectNode) {
					properties.set(entry.getKey(),
							convertSchema((ObjectNode) entry.getValue(), JsonParser.getJsonMapper().getNodeFactory()));
				}
			}
		}

		// Handle required array
		if (source.has("required")) {
			target.set("required", source.get("required"));
		}

		// Convert JSON Schema specific attributes to OpenAPI equivalents
		if (source.has("additionalProperties")) {
			JsonNode additionalProps = source.get("additionalProperties");
			if (additionalProps.isBoolean()) {
				target.put("additionalProperties", additionalProps.asBoolean());
			}
			else if (additionalProps.isObject()) {
				target.set("additionalProperties",
						convertSchema((ObjectNode) additionalProps, JsonParser.getJsonMapper().getNodeFactory()));
			}
		}

		// Handle arrays
		if (source.has("items")) {
			JsonNode items = source.get("items");
			if (items.isObject()) {
				target.set("items", convertSchema((ObjectNode) items, JsonParser.getJsonMapper().getNodeFactory()));
			}
		}

		// Handle allOf, anyOf, oneOf
		String[] combiners = { "allOf", "anyOf", "oneOf" };
		for (String combiner : combiners) {
			if (source.has(combiner)) {
				JsonNode combinerNode = source.get(combiner);
				if (combinerNode.isArray()) {
					target.putArray(combiner).addAll((ArrayNode) combinerNode);
				}
			}
		}
	}

	/**
	 * Recursively converts a JSON Schema node to OpenAPI format.
	 * @param source The source ObjectNode containing JSON Schema
	 * @param factory The JsonNodeFactory to create new nodes
	 * @return The converted OpenAPI schema as ObjectNode
	 */
	private static ObjectNode convertSchema(ObjectNode source, JsonNodeFactory factory) {
		Assert.notNull(source, "Source node must not be null");
		Assert.notNull(factory, "JsonNodeFactory must not be null");

		ObjectNode converted = factory.objectNode();
		copyCommonProperties(source, converted);
		handleJsonSchemaSpecifics(source, converted, factory);
		return converted;
	}

}
