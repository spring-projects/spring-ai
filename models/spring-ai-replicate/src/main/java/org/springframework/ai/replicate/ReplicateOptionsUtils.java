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

package org.springframework.ai.replicate;

import java.util.HashMap;
import java.util.Map;

import org.springframework.lang.Nullable;

/**
 * Utility class for handling Replicate options, when set via application.properties. This
 * utility is needed because replicate expects various different Types in the "input" map
 * and we cannot automatically infer the type from the properties.
 *
 * @author Rene Maierhofer
 * @since 1.1.0
 */
public abstract class ReplicateOptionsUtils {

	private ReplicateOptionsUtils() {
	}

	/**
	 * Convert all string values in a map to their appropriate types
	 * @param source the source map with potentially string-typed values
	 * @return a new map with properly typed values, or an empty map if source is null
	 */
	public static Map<String, Object> convertMapValues(@Nullable Map<String, Object> source) {
		if (source == null) {
			return new HashMap<>();
		}
		Map<String, Object> result = new HashMap<>(source.size());
		for (Map.Entry<String, Object> entry : source.entrySet()) {
			result.put(entry.getKey(), convertValue(entry.getValue()));
		}
		return result;
	}

	/**
	 * Convert a value to its appropriate type if it's a string representation of a number
	 * or boolean. Non-string values are returned as-is.
	 * @param value the value to convert
	 * @return the converted value with the appropriate type
	 */
	public static Object convertValue(@Nullable Object value) {
		if (!(value instanceof String strValue)) {
			return value;
		}
		if ("true".equalsIgnoreCase(strValue) || "false".equalsIgnoreCase(strValue)) {
			return Boolean.parseBoolean(strValue);
		}
		if (!strValue.contains(".") && !strValue.contains("e") && !strValue.contains("E")) {
			try {
				return Integer.parseInt(strValue);
			}
			catch (NumberFormatException ex) {
				// Not an integer, continue to next type check
			}
		}
		try {
			return Double.parseDouble(strValue);
		}
		catch (NumberFormatException ex) {
			// Not a number, return as string
		}
		// Return as string
		return strValue;
	}

}
