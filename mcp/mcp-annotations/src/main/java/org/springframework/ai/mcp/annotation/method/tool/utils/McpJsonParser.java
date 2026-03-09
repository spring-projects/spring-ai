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

package org.springframework.ai.mcp.annotation.method.tool.utils;

import java.util.Map;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JavaType;

import org.springframework.ai.util.json.JsonParser;
import org.springframework.util.Assert;

/**
 * Additional utilities for JSON parsing operations specific to MCP annotations and tools.
 * Reuses the underlying JsonMapper from {@link JsonParser} but provides convenience methods
 * for converting between Maps and Java objects, which is a common pattern in MCP tool interactions.
 */
public final class McpJsonParser {

	private McpJsonParser() {
	}

	private static TypeReference<Map<String, Object>> MAP_TYPE_REF = new TypeReference<Map<String, Object>>() {
	};

	public static Map<String, Object> toMap(Object object) {
		Assert.notNull(object, "object cannot be null");
		return JsonParser.getJsonMapper().convertValue(object, MAP_TYPE_REF);
	}

	public static <T> T fromMap(Map<String, Object> map, Class<T> targetType) {
		JavaType javaType = JsonParser.getJsonMapper().getTypeFactory().constructType(targetType);
		return JsonParser.getJsonMapper().convertValue(map, javaType);
	}

	public static <T> T fromMap(Map<String, Object> map, TypeReference<T> targetType) {
		JavaType javaType = JsonParser.getJsonMapper().getTypeFactory().constructType(targetType);
		return JsonParser.getJsonMapper().convertValue(map, javaType);
	}

}
