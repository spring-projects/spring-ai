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

package org.springframework.ai.util;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.util.Assert;

/**
 * Helper for JSON processing.
 *
 * @author Sebastien Deleuze
 * @since 2.0.0
 */
public class JsonHelper {

	private final @Nullable JsonMapper jsonMapper;

	private static final TypeReference<Map<String, Object>> MAP_TYPE_REF = new TypeReference<>() {
	};

	/**
	 * Creates a helper that follows the default mapper from
	 * {@link JacksonUtils#getDefaultJsonMapper}, resolved on each call so a later
	 * {@link JacksonUtils#setDefaultJsonMapper} override is picked up.
	 */
	public JsonHelper() {
		this.jsonMapper = null;
	}

	public JsonHelper(JsonMapper jsonMapper) {
		Assert.notNull(jsonMapper, "jsonMapper cannot be null");
		this.jsonMapper = jsonMapper;
	}

	private JsonMapper jsonMapper() {
		JsonMapper mapper = this.jsonMapper;
		return mapper != null ? mapper : JacksonUtils.getDefaultJsonMapper();
	}

	/**
	 * Converts a JSON string to a Java object.
	 * @param json the JSON string to parse
	 * @param type the target type
	 * @return the converted object
	 */
	public <T> @Nullable T fromJson(String json, Class<T> type) {
		Assert.notNull(json, "json cannot be null");
		Assert.notNull(type, "type cannot be null");

		try {
			return jsonMapper().readValue(json, type);
		}
		catch (JacksonException ex) {
			throw new IllegalStateException("Conversion from JSON to %s failed".formatted(type.getName()), ex);
		}
	}

	/**
	 * Converts a JSON string to a Java object.
	 * @param json the JSON string to parse
	 * @param type the target type
	 * @return the converted object
	 */
	public <T> @Nullable T fromJson(String json, Type type) {
		Assert.notNull(json, "json cannot be null");
		Assert.notNull(type, "type cannot be null");

		try {
			return jsonMapper().readValue(json, jsonMapper().constructType(type));
		}
		catch (JacksonException ex) {
			throw new IllegalStateException("Conversion from JSON to %s failed".formatted(type.getTypeName()), ex);
		}
	}

	/**
	 * Converts a JSON string to a Java object.
	 * @param json the JSON string to parse
	 * @param type the target type
	 * @return the converted object
	 */
	public <T> @Nullable T fromJson(String json, ParameterizedTypeReference<T> type) {
		Assert.notNull(json, "json cannot be null");
		Assert.notNull(type, "type cannot be null");

		try {
			return jsonMapper().readValue(json, jsonMapper().constructType(type.getType()));
		}
		catch (JacksonException ex) {
			throw new IllegalStateException("Conversion from JSON to %s failed".formatted(type.getType().getTypeName()),
					ex);
		}
	}

	/**
	 * Converts the given JSON string to a Map of String and Object.
	 * @param json the JSON string to parse
	 * @return the converted map
	 */
	public Map<String, Object> fromJsonToMap(String json) {
		try {
			Map<String, Object> map = jsonMapper().readValue(json, MAP_TYPE_REF);
			return map != null ? map : Collections.emptyMap();
		}
		catch (JacksonException ex) {
			throw new IllegalStateException(
					"Conversion from JSON to %s failed".formatted(MAP_TYPE_REF.getType().getTypeName()), ex);
		}
	}

	/**
	 * Converts a Java object to a JSON string.
	 */
	public String toJson(@Nullable Object object) {
		return toJson(object, false);
	}

	/**
	 * Converts a Java object to a JSON string.
	 * @param forwardIfValidJson when true and object being a valid JSON string, just
	 * return it
	 */
	public String toJson(@Nullable Object object, boolean forwardIfValidJson) {
		if (forwardIfValidJson && object instanceof String str && isValidJson(str)) {
			return str;
		}
		return jsonMapper().writeValueAsString(object);
	}

	/**
	 * Checks if a string is a valid JSON string.
	 */
	private boolean isValidJson(String input) {
		try {
			jsonMapper().readTree(input);
			return true;
		}
		catch (JacksonException e) {
			return false;
		}
	}

	/**
	 * Converts a map to a Java object.
	 * @param map the map to convert
	 * @param type the target type
	 * @return the converted object
	 */
	public <T> T convertFromMap(Map<String, Object> map, Class<T> type) {
		return jsonMapper().convertValue(map, type);
	}

	/**
	 * Converts a map to a Java object.
	 * @param map the map to convert
	 * @param type the target type
	 * @return the converted object
	 */
	public <T> T convertFromMap(Map<String, Object> map, ParameterizedTypeReference<T> type) {
		return jsonMapper().convertValue(map, jsonMapper().constructType(type.getType()));
	}

	public Map<String, Object> convertToMap(Object object) {
		Assert.notNull(object, "object cannot be null");
		return jsonMapper().convertValue(object, MAP_TYPE_REF);
	}

	/**
	 * Convert a Java Object to a typed Object. Based on the implementation in
	 * MethodToolCallback.
	 * @param value the object to convert
	 * @param type the target type
	 * @return the converted typed object
	 */
	public Object convertToTypedObject(Object value, Class<?> type) {
		Assert.notNull(value, "value cannot be null");
		Assert.notNull(type, "type cannot be null");

		Object result = null;
		if (value instanceof String jsonString) {
			try {
				result = jsonMapper().convertValue(jsonString, type);
			}
			catch (DatabindException e) {
				// If the type is a raw string that should read as JSON String,
				// parsing will fail but pass the following toJson -> fromJson cycle.
				// Example: LocalDate, with jsonString = "2026-04-22", which is not valid
				// JSON. Instead, it should be "\"2026-04-22\"".
			}
		}

		if (result == null) {
			String json = toJson(value, true);
			result = fromJson(json, type);
		}

		return Objects.requireNonNull(result);
	}

}
