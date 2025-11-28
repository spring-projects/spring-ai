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

import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.util.StringUtils;

/**
 * Utility methods for working with JSON schemas.
 *
 * @author Guangdong Liu
 * @author Ilayaperumal Gopinathan
 * @since 1.0.0
 */
public final class JsonSchemaUtils {

	private JsonSchemaUtils() {
	}

	/**
	 * Ensures that the input schema is valid for AI model APIs. Many AI models require
	 * that the parameters object must have a "properties" field, even if it's empty. This
	 * method normalizes schemas from external sources (like MCP tools) that may not
	 * include this field.
	 * @param inputSchema the input schema as a JSON string
	 * @return a valid input schema as a JSON string with required fields
	 */
	public static String ensureValidInputSchema(String inputSchema) {
		if (!StringUtils.hasText(inputSchema)) {
			return inputSchema;
		}

		Map<String, Object> schemaMap = ModelOptionsUtils.jsonToMap(inputSchema);

		if (schemaMap == null || schemaMap.isEmpty()) {
			// Create a minimal valid schema
			schemaMap = new java.util.HashMap<>();
			schemaMap.put("type", "object");
			schemaMap.put("properties", new java.util.HashMap<>());
			return ModelOptionsUtils.toJsonString(schemaMap);
		}

		// Ensure "type" field exists
		if (!schemaMap.containsKey("type")) {
			schemaMap.put("type", "object");
		}

		// Ensure "properties" field exists for object types
		if ("object".equals(schemaMap.get("type")) && !schemaMap.containsKey("properties")) {
			schemaMap.put("properties", new java.util.HashMap<>());
		}

		return ModelOptionsUtils.toJsonString(schemaMap);
	}

}
