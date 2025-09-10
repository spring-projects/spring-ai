/*
 * Copyright 2023-2025 the original author or authors.
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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

/**
 * Unit tests for {@link ObservabilityHelper}.
 *
 * @author Jonatan Ivanov
 */
class ObservabilityHelperTests {

	@Test
	void shouldGetEmptyBracketsForEmptyMap() {
		assertThat(ObservabilityHelper.concatenateEntries(Map.of())).isEqualTo("[]");
	}

	@Test
	void shouldGetEntriesForNonEmptyMap() {
		TreeMap<String, Object> map = new TreeMap<>(Map.of("a", "1", "b", "2"));
		assertThat(ObservabilityHelper.concatenateEntries(map)).isEqualTo("[\"a\":\"1\", \"b\":\"2\"]");
	}

	@Test
	void shouldGetEmptyBracketsForEmptyList() {
		assertThat(ObservabilityHelper.concatenateStrings(List.of())).isEqualTo("[]");
	}

	@Test
	void shouldGetEntriesForNonEmptyList() {
		assertThat(ObservabilityHelper.concatenateStrings(List.of("a", "b"))).isEqualTo("[\"a\", \"b\"]");
	}

	@Test
	void shouldHandleSingleEntryMap() {
		assertThat(ObservabilityHelper.concatenateEntries(Map.of("key", "value"))).isEqualTo("[\"key\":\"value\"]");
	}

	@Test
	void shouldHandleSingleEntryList() {
		assertThat(ObservabilityHelper.concatenateStrings(List.of("single"))).isEqualTo("[\"single\"]");
	}

	@Test
	void shouldHandleEmptyStringsInList() {
		assertThat(ObservabilityHelper.concatenateStrings(List.of("", "non-empty", "")))
			.isEqualTo("[\"\", \"non-empty\", \"\"]");
	}

	@Test
	void shouldHandleNullInputsGracefully() {
		// Test null map
		assertThatThrownBy(() -> ObservabilityHelper.concatenateEntries(null)).isInstanceOf(NullPointerException.class);

		// Test null list
		assertThatThrownBy(() -> ObservabilityHelper.concatenateStrings(null)).isInstanceOf(NullPointerException.class);
	}

	@Test
	void shouldHandleNullValuesInMap() {
		Map<String, Object> mapWithNulls = new HashMap<>();
		mapWithNulls.put("key1", "value1");
		mapWithNulls.put("key2", null);
		mapWithNulls.put("key3", "value3");

		String result = ObservabilityHelper.concatenateEntries(mapWithNulls);

		// Result should handle null values appropriately
		assertThat(result).contains("\"key1\":\"value1\"");
		assertThat(result).contains("\"key3\":\"value3\"");
		// Check how null is handled - could be "null" or omitted
		assertThat(result).satisfiesAnyOf(r -> assertThat(r).contains("\"key2\":null"),
				r -> assertThat(r).contains("\"key2\":\"null\""), r -> assertThat(r).doesNotContain("key2"));
	}

	@Test
	void shouldHandleNullValuesInList() {
		List<String> listWithNulls = Arrays.asList("first", null, "third");

		String result = ObservabilityHelper.concatenateStrings(listWithNulls);

		assertThat(result).contains("\"first\"");
		assertThat(result).contains("\"third\"");
		// Check how null is handled in list
		assertThat(result).satisfiesAnyOf(r -> assertThat(r).contains("null"), r -> assertThat(r).contains("\"null\""),
				r -> assertThat(r).contains("\"\""));
	}

	@Test
	void shouldHandleSpecialCharactersInMapValues() {
		Map<String, Object> specialCharsMap = Map.of("quotes", "value with \"quotes\"", "newlines",
				"value\nwith\nnewlines", "tabs", "value\twith\ttabs", "backslashes", "value\\with\\backslashes");

		String result = ObservabilityHelper.concatenateEntries(specialCharsMap);

		assertThat(result).isNotNull();
		assertThat(result).startsWith("[");
		assertThat(result).endsWith("]");
		// Should properly escape or handle special characters
		assertThat(result).contains("quotes");
		assertThat(result).contains("newlines");
	}

	@Test
	void shouldHandleSpecialCharactersInList() {
		List<String> specialCharsList = List.of("string with \"quotes\"", "string\nwith\nnewlines",
				"string\twith\ttabs", "string\\with\\backslashes");

		String result = ObservabilityHelper.concatenateStrings(specialCharsList);

		assertThat(result).isNotNull();
		assertThat(result).startsWith("[");
		assertThat(result).endsWith("]");
		assertThat(result).contains("quotes");
		assertThat(result).contains("newlines");
	}

	@Test
	void shouldHandleWhitespaceOnlyStrings() {
		List<String> whitespaceList = List.of("   ", "\t", "\n", " \t\n ");

		String result = ObservabilityHelper.concatenateStrings(whitespaceList);

		assertThat(result).isNotNull();
		assertThat(result).startsWith("[");
		assertThat(result).endsWith("]");
		// Whitespace should be preserved in quotes
		assertThat(result).contains("\"   \"");
	}

	@Test
	void shouldHandleNumericAndBooleanValues() {
		Map<String, Object> mixedTypesMap = Map.of("integer", 1, "double", 1.1, "boolean", true, "string", "text");

		String result = ObservabilityHelper.concatenateEntries(mixedTypesMap);

		assertThat(result).contains("1");
		assertThat(result).contains("1.1");
		assertThat(result).contains("true");
		assertThat(result).contains("text");
	}

	@Test
	void shouldMaintainOrderForOrderedMaps() {
		// Using TreeMap to ensure ordering
		TreeMap<String, Object> orderedMap = new TreeMap<>();
		orderedMap.put("z", "last");
		orderedMap.put("a", "first");
		orderedMap.put("m", "middle");

		String result = ObservabilityHelper.concatenateEntries(orderedMap);

		// Should maintain alphabetical order
		int posA = result.indexOf("\"a\"");
		int posM = result.indexOf("\"m\"");
		int posZ = result.indexOf("\"z\"");

		assertThat(posA).isLessThan(posM);
		assertThat(posM).isLessThan(posZ);
	}

	@Test
	void shouldHandleComplexObjectsAsValues() {
		Map<String, Object> complexMap = Map.of("list", List.of("a", "b"), "array", new String[] { "x", "y" }, "object",
				new Object());

		String result = ObservabilityHelper.concatenateEntries(complexMap);

		assertThat(result).isNotNull();
		assertThat(result).contains("list");
		assertThat(result).contains("array");
		assertThat(result).contains("object");
	}

	@Test
	void shouldProduceConsistentOutput() {
		Map<String, Object> map = Map.of("key", "value");
		List<String> list = List.of("item");

		// Multiple calls should produce same result
		String mapResult1 = ObservabilityHelper.concatenateEntries(map);
		String mapResult2 = ObservabilityHelper.concatenateEntries(map);
		String listResult1 = ObservabilityHelper.concatenateStrings(list);
		String listResult2 = ObservabilityHelper.concatenateStrings(list);

		assertThat(mapResult1).isEqualTo(mapResult2);
		assertThat(listResult1).isEqualTo(listResult2);
	}

	@Test
	void shouldHandleMapWithEmptyStringKeys() {
		Map<String, Object> mapWithEmptyKey = new HashMap<>();
		mapWithEmptyKey.put("", "empty key value");
		mapWithEmptyKey.put("normal", "normal value");

		String result = ObservabilityHelper.concatenateEntries(mapWithEmptyKey);

		assertThat(result).contains("\"\":\"empty key value\"");
		assertThat(result).contains("\"normal\":\"normal value\"");
	}

	@Test
	void shouldFormatBracketsCorrectly() {
		// Verify proper bracket formatting in all cases
		assertThat(ObservabilityHelper.concatenateEntries(Map.of())).isEqualTo("[]");
		assertThat(ObservabilityHelper.concatenateStrings(List.of())).isEqualTo("[]");

		String singleMapResult = ObservabilityHelper.concatenateEntries(Map.of("a", "b"));
		assertThat(singleMapResult).startsWith("[");
		assertThat(singleMapResult).endsWith("]");

		String singleListResult = ObservabilityHelper.concatenateStrings(List.of("item"));
		assertThat(singleListResult).startsWith("[");
		assertThat(singleListResult).endsWith("]");
	}

}
