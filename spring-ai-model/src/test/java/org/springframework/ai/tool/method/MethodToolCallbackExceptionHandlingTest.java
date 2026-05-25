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

package org.springframework.ai.tool.method;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.execution.ToolExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author Christian Tzolov
 */
public class MethodToolCallbackExceptionHandlingTest {

	@Test
	void testGenericListType() throws Exception {
		// Create a test object with a method that takes a List<String>
		TestTools testObject = new TestTools();

		ToolCallback callback = Arrays
			.stream(MethodToolCallbackProvider.builder().toolObjects(testObject).build().getToolCallbacks())
			.filter(toolCallback -> "stringList".equals(toolCallback.getToolDefinition().name()))
			.findFirst()
			.orElseThrow();

		// Create a JSON input with a list of strings
		String toolInput = """
				{
					"strings": ["one", "two", "three"]
				}
				""";

		// Call the tool
		String result = callback.call(toolInput);

		// Verify the result
		assertThat(result).isEqualTo("3 strings processed: [one, two, three]");

		// Verify
		String ivalidToolInput = """
				{
					"strings": 678
				}
				""";

		// Call the tool
		assertThatThrownBy(() -> callback.call(ivalidToolInput)).isInstanceOf(ToolExecutionException.class)
			.hasMessageContaining("Cannot deserialize value");

		// Verify extractToolArguments

		String ivalidToolInput2 = """
				nill
					""";

		// Call the tool
		assertThatThrownBy(() -> callback.call(ivalidToolInput2)).isInstanceOf(ToolExecutionException.class)
			.hasMessageContaining("Unrecognized token");
	}

	@Test
	void testNumericEmptyStringInputProvidesClearMessage() {
		TestTools testObject = new TestTools();

		ToolCallback callback = Arrays
			.stream(MethodToolCallbackProvider.builder().toolObjects(testObject).build().getToolCallbacks())
			.filter(toolCallback -> "longInput".equals(toolCallback.getToolDefinition().name()))
			.findFirst()
			.orElseThrow();

		String invalidToolInput = """
				{
					"id": ""
				}
				""";

		assertThatThrownBy(() -> callback.call(invalidToolInput)).isInstanceOf(ToolExecutionException.class)
			.hasMessageContaining("Cannot convert value '' to numeric type 'java.lang.Long'");
	}

	public static class TestTools {

		@Tool(description = "Process a list of strings")
		public String stringList(List<String> strings) {
			return strings.size() + " strings processed: " + strings;
		}

		@Tool(description = "Accepts a long id")
		public String longInput(Long id) {
			return "ID: " + id;
		}

	}

}
