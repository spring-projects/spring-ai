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

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import org.jspecify.annotations.Nullable;

import org.springframework.util.ClassUtils;

/**
 * Internal helper for detecting {@code org.openapitools.jackson.nullable.JsonNullable}
 * without making it a hard dependency. Keeps the optional class lookup in one place so
 * that {@link JsonSchemaGenerator} and {@link AbstractSpringAiSchemaModule} can stay
 * compilable when the {@code jackson-databind-nullable} artifact is absent.
 *
 * @author Jewoo Shin
 */
final class JsonNullableSupport {

	private static final String JSON_NULLABLE_CLASS_NAME = "org.openapitools.jackson.nullable.JsonNullable";

	private static final @Nullable Class<?> JSON_NULLABLE_CLASS = loadJsonNullableClass();

	private JsonNullableSupport() {
	}

	private static @Nullable Class<?> loadJsonNullableClass() {
		ClassLoader classLoader = JsonNullableSupport.class.getClassLoader();
		if (!ClassUtils.isPresent(JSON_NULLABLE_CLASS_NAME, classLoader)) {
			return null;
		}
		try {
			return ClassUtils.forName(JSON_NULLABLE_CLASS_NAME, classLoader);
		}
		catch (ClassNotFoundException ex) {
			return null;
		}
	}

	static boolean isPresent() {
		return JSON_NULLABLE_CLASS != null;
	}

	/**
	 * Whether the given reflective type is {@code JsonNullable} or
	 * {@code JsonNullable<T>}. Accepts both raw {@link Class} and
	 * {@link ParameterizedType}; safe to call when {@code JsonNullable} is not on the
	 * classpath.
	 */
	static boolean isJsonNullableType(Type type) {
		if (JSON_NULLABLE_CLASS == null) {
			return false;
		}
		if (type instanceof ParameterizedType parameterizedType) {
			return parameterizedType.getRawType() == JSON_NULLABLE_CLASS;
		}
		return type instanceof Class<?> rawClass && rawClass == JSON_NULLABLE_CLASS;
	}

}
