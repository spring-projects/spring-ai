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

package org.springframework.ai.tool.method;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link MethodToolCallback} with generic types.
 */
class MethodToolCallbackGenericTypesTest {

	@Test
	void testGenericListType() throws Exception {
		// Create a test object with a method that takes a List<String>
		TestGenericClass testObject = new TestGenericClass();
		Method method = TestGenericClass.class.getMethod("processStringList", List.class);

		// Create a tool definition
		ToolDefinition toolDefinition = DefaultToolDefinition.builder()
			.name("processStringList")
			.description("Process a list of strings")
			.inputSchema("{}")
			.build();

		// Create a MethodToolCallback
		MethodToolCallback callback = MethodToolCallback.builder()
			.toolDefinition(toolDefinition)
			.toolMethod(method)
			.toolObject(testObject)
			.build();

		// Create a JSON input with a list of strings
		String toolInput = """
				{
				    "strings": ["one", "two", "three"]
				}
				""";

		// Call the tool
		String result = callback.call(toolInput);

		// Verify the result
		assertThat(result).isEqualTo("\"3 strings processed: [one, two, three]\"");
	}

	@Test
	void testGenericMapType() throws Exception {
		// Create a test object with a method that takes a Map<String, Integer>
		TestGenericClass testObject = new TestGenericClass();
		Method method = TestGenericClass.class.getMethod("processStringIntMap", Map.class);

		// Create a tool definition
		ToolDefinition toolDefinition = DefaultToolDefinition.builder()
			.name("processStringIntMap")
			.description("Process a map of string to integer")
			.inputSchema("{}")
			.build();

		// Create a MethodToolCallback
		MethodToolCallback callback = MethodToolCallback.builder()
			.toolDefinition(toolDefinition)
			.toolMethod(method)
			.toolObject(testObject)
			.build();

		// Create a JSON input with a map of string to integer
		String toolInput = """
				{
				    "map": {"one": 1, "two": 2, "three": 3}
				}
				""";

		// Call the tool
		String result = callback.call(toolInput);

		// Verify the result
		assertThat(result).isEqualTo("\"3 entries processed: {one=1, two=2, three=3}\"");
	}

	@Test
	void testNestedGenericType() throws Exception {
		// Create a test object with a method that takes a List<Map<String, Integer>>
		TestGenericClass testObject = new TestGenericClass();
		Method method = TestGenericClass.class.getMethod("processListOfMaps", List.class);

		// Create a tool definition
		ToolDefinition toolDefinition = DefaultToolDefinition.builder()
			.name("processListOfMaps")
			.description("Process a list of maps")
			.inputSchema("{}")
			.build();

		// Create a MethodToolCallback
		MethodToolCallback callback = MethodToolCallback.builder()
			.toolDefinition(toolDefinition)
			.toolMethod(method)
			.toolObject(testObject)
			.build();

		// Create a JSON input with a list of maps
		String toolInput = """
				{
				    "listOfMaps": [
				        {"a": 1, "b": 2},
				        {"c": 3, "d": 4}
				    ]
				}
				""";

		// Call the tool
		String result = callback.call(toolInput);

		// Verify the result
		assertThat(result).isEqualTo("\"2 maps processed: [{a=1, b=2}, {c=3, d=4}]\"");
	}

	/**
	 * Test class with methods that use generic types.
	 */
	public static class TestGenericClass {

		public String processStringList(List<String> strings) {
			return strings.size() + " strings processed: " + strings;
		}

		public String processStringIntMap(Map<String, Integer> map) {
			return map.size() + " entries processed: " + map;
		}

		public String processListOfMaps(List<Map<String, Integer>> listOfMaps) {
			return listOfMaps.size() + " maps processed: " + listOfMaps;
		}

	}

}
