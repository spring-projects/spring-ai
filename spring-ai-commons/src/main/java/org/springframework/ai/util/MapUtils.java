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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Utility methods for working with {@link Map} instances.
 *
 * @author Evan Yao
 * @since 1.0.0
 */
public abstract class MapUtils {

	private MapUtils() {
	}

	/**
	 * Unwrap any {@link Optional} values in the given map, replacing them with their
	 * contained value or {@code null} if empty. Non-{@link Optional} values are left
	 * unchanged. This is useful when serializing metadata maps to drivers (e.g. Neo4j,
	 * MongoDB) that do not support {@link Optional} as a value type.
	 * @param map the source map, must not be {@literal null}
	 * @return a new map with all {@link Optional} values unwrapped
	 */
	public static Map<String, Object> unwrapOptionals(Map<String, Object> map) {
		Map<String, Object> result = new HashMap<>(map.size());
		for (Map.Entry<String, Object> entry : map.entrySet()) {
			Object value = entry.getValue();
			if (value instanceof Optional<?> optional) {
				result.put(entry.getKey(), optional.orElse(null));
			}
			else {
				result.put(entry.getKey(), value);
			}
		}
		return result;
	}

}
