/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.observation;

import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Utilities for observability.
 *
 * @author Thomas Vitale
 */
public final class ObservabilityHelper {

	private ObservabilityHelper() {
	}

	public static String concatenateEntries(Map<String, Object> keyValues) {
		var keyValuesJoiner = new StringJoiner(", ", "[", "]");
		keyValues.forEach((key, value) -> keyValuesJoiner.add("\"" + key + "\":\"" + value + "\""));
		return keyValuesJoiner.toString();
	}

	public static String concatenateStrings(List<String> strings) {
		var stringsJoiner = new StringJoiner(", ", "[", "]");
		strings.forEach(string -> stringsJoiner.add("\"" + string + "\""));
		return stringsJoiner.toString();
	}

}
