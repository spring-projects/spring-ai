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

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;

import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.util.json.JsonParser;

/**
 * This utility provides functionality to augment a JSON Schema with additional fields
 * based on a provided Record type. It uses a JSON Schema Generator to generate the schema
 * for the Record's fields and integrates them into an existing JSON Schema. The augmented
 * schema can then be used to re-define the tool inputs for tool calling.
 *
 * @author Christian Tzolov
 */
public final class ToolInputSchemaAugmenter {

	private ToolInputSchemaAugmenter() {
	}

	/**
	 * Extracts the tool argument types from a record class annotated with
	 * {@link ToolParam}. It retrieves the field names, types, descriptions, and required
	 * status from the record components.
	 * @param recordClass The record class to extract argument types from.
	 * @return A list of {@link AugmentedArgumentType} representing the tool input
	 * argument types.
	 */
	public static <T extends Record> List<AugmentedArgumentType> toAugmentedArgumentTypes(Class<T> recordClass) {
		try {

			return Arrays.stream(recordClass.getRecordComponents()).map(c -> {
				// Get the annotation from the corresponding field, not the record
				// component
				ToolParam toolParam = null;
				try {
					var field = recordClass.getDeclaredField(c.getName());
					toolParam = field.getAnnotation(ToolParam.class);
				}
				catch (NoSuchFieldException e) {
					// Field not found, toolParam remains null
				}

				return new AugmentedArgumentType(c.getName(), c.getGenericType(),
						toolParam != null ? toolParam.description() : "no description",
						toolParam != null ? toolParam.required() : false);
			}).toList();

		}
		catch (Exception e) {
			throw new RuntimeException("Failed to extract record field types", e);
		}
	}

	public static String augmentToolInputSchema(String jsonSchemaString, String propertyName, Type propertyType,
			String description, boolean required) {

		return augmentToolInputSchema(jsonSchemaString,
				List.of(new AugmentedArgumentType(propertyName, propertyType, description, required)));
	}

	public static String augmentToolInputSchema(String jsonSchemaString, List<AugmentedArgumentType> argumentType) {

		try {

			ObjectNode schemaObjectNode = (ObjectNode) ModelOptionsUtils.JSON_MAPPER.readTree(jsonSchemaString);

			// Handle properties
			ObjectNode propertiesNode;
			if (schemaObjectNode.has("properties")) {
				propertiesNode = (ObjectNode) schemaObjectNode.get("properties");
			}
			else {
				propertiesNode = ModelOptionsUtils.JSON_MAPPER.createObjectNode();
				schemaObjectNode.set("properties", propertiesNode);
			}

			for (AugmentedArgumentType argument : argumentType) {

				ObjectNode parameterNode = ModelOptionsUtils.getJsonSchema(argument.type());

				if (argument.description() != null && !argument.description().isEmpty()) {
					parameterNode.put("description", argument.description());
				}
				propertiesNode.set(argument.name(), parameterNode);

				if (argument.required()) {

					ArrayNode requiredArray;
					if (schemaObjectNode.has("required")) {
						requiredArray = (ArrayNode) schemaObjectNode.get("required");
					}
					else {
						requiredArray = JsonParser.getJsonMapper().createArrayNode();
						schemaObjectNode.set("required", requiredArray);
					}
					requiredArray.add(argument.name());

				}
			}

			return JsonParser.getJsonMapper().writerWithDefaultPrettyPrinter().writeValueAsString(schemaObjectNode);

		}
		catch (Exception e) {
			throw new RuntimeException("Failed to parse JSON Schema", e);
		}
	}

	/**
	 * Represents an extended argument type with additional metadata such as description
	 * and required status.
	 */
	public record AugmentedArgumentType(String name, Type type, String description, boolean required) {
	}

}
