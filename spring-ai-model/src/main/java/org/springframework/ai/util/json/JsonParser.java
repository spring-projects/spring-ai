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

package org.springframework.ai.util.json;

import java.lang.reflect.Type;

import org.jspecify.annotations.Nullable;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.util.JacksonUtils;
import org.springframework.ai.util.JsonHelper;

/**
 * Utilities to perform parsing operations between JSON and Java.
 *
 * @deprecated Use {@link JacksonUtils} or {@link JsonHelper} instead
 */
@Deprecated(forRemoval = true)
public final class JsonParser {

	private static final JsonHelper jsonHelper = new JsonHelper();

	private JsonParser() {
	}

	/**
	 * Returns a Jackson {@link JsonMapper} instance tailored for JSON-parsing operations
	 * for tool calling and structured output.
	 * @deprecated Use {@link JacksonUtils#getDefaultJsonMapper} instead
	 */
	@Deprecated(forRemoval = true)
	public static JsonMapper getJsonMapper() {
		return JacksonUtils.getDefaultJsonMapper();
	}

	/**
	 * Converts a JSON string to a Java object.
	 * @deprecated Use {@link JsonHelper#fromJson(String, Class)} instead
	 */
	@Deprecated(forRemoval = true)
	public static <T> @Nullable T fromJson(String json, Class<T> type) {
		return jsonHelper.fromJson(json, type);
	}

	/**
	 * Converts a JSON string to a Java object.
	 * @deprecated Use {@link JsonHelper#fromJson(String, Type)} instead
	 */
	@Deprecated(forRemoval = true)
	public static <T> @Nullable T fromJson(String json, Type type) {
		return jsonHelper.fromJson(json, type);
	}

	/**
	 * Converts a JSON string to a Java object.
	 * @deprecated Use
	 * {@link JsonHelper#fromJson(String, org.springframework.core.ParameterizedTypeReference)}
	 * instead
	 */
	@Deprecated(forRemoval = true)
	public static <T> @Nullable T fromJson(String json, TypeReference<T> type) {
		return jsonHelper.fromJson(json, type.getType());
	}

	/**
	 * Converts a Java object to a JSON string if it's not already a valid JSON string.
	 * @deprecated Use {@link JsonHelper#toJson(Object, boolean)} instead
	 */
	@Deprecated(forRemoval = true)
	public static String toJson(@Nullable Object object) {
		return jsonHelper.toJson(object, true);
	}

	/**
	 * Convert a Java Object to a typed Object. Based on the implementation in
	 * MethodToolCallback.
	 * @deprecated Use {@link JsonHelper#convertToTypedObject} instead
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Deprecated(forRemoval = true)
	public static Object toTypedObject(Object value, Class<?> type) {
		return jsonHelper.convertToTypedObject(value, type);
	}

}
