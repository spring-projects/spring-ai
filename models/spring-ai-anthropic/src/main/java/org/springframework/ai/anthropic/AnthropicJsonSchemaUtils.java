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

package org.springframework.ai.anthropic;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.anthropic.core.JsonValue;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Converts JSON Schemas into the additional properties of the Anthropic SDK schema types,
 * such as the output format schema and the tool input schema.
 * <p>
 * Every schema keyword is kept except {@code $schema}, which the Anthropic API does not
 * accept. In particular, {@code $defs} and {@code definitions} must be kept together with
 * the {@code $ref} pointers that resolve against them.
 *
 * @author Dimitar Proynov
 * @since 2.0.4
 */
final class AnthropicJsonSchemaUtils {

	private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

	private static final String SCHEMA_KEYWORD = "$schema";

	private AnthropicJsonSchemaUtils() {
	}

	/**
	 * Parses a JSON Schema into a map of its root keywords.
	 * @param schema the JSON Schema
	 * @return the root keywords of the schema
	 */
	static Map<String, Object> parse(String schema) {
		return JSON_MAPPER.readValue(schema, new TypeReference<Map<String, Object>>() {
		});
	}

	/**
	 * Returns the root keywords of a schema to send as additional properties, without
	 * {@code $schema} and the given keywords.
	 * @param schema the parsed JSON Schema
	 * @param excludedKeywords the keywords that the caller sets through dedicated builder
	 * methods
	 * @return the remaining keywords, in schema order
	 */
	static Map<String, JsonValue> toAdditionalProperties(Map<String, Object> schema, String... excludedKeywords) {
		Set<String> excluded = Set.of(excludedKeywords);
		Map<String, JsonValue> additionalProperties = new LinkedHashMap<>();
		schema.forEach((keyword, value) -> {
			if (!SCHEMA_KEYWORD.equals(keyword) && !excluded.contains(keyword)) {
				additionalProperties.put(keyword, JsonValue.from(value));
			}
		});
		return additionalProperties;
	}

}
