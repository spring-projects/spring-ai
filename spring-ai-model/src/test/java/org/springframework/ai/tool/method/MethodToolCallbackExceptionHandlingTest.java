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

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.execution.ToolExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author Christian Tzolov
 */
public class MethodToolCallbackExceptionHandlingTest {

	@Test
	void testGenericListType() throws Exception {
		// Create a test object with a method that takes a List<String>
		TestTools testObject = new TestTools();

		var callback = MethodToolCallbackProvider.builder().toolObjects(testObject).build().getToolCallbacks()[0];

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

	/**
	 * Regression test for https://github.com/spring-projects/spring-ai/issues/3884.
	 *
	 * When an LLM returns an empty string {@code ""} for a numeric parameter (e.g.
	 * {@code Long}), Jackson attempts to coerce it through {@code BigDecimal}, which
	 * throws {@code NumberFormatException}. The fix treats an empty string as absent
	 * (i.e. {@code null}) for non-String target types, matching the semantic intent of
	 * "the model produced no value for this argument".
	 */
	@Test
	void emptyStringParameterForNumericTypeMapsToNull() {
		NumericTools tools = new NumericTools();
		var callback = MethodToolCallbackProvider.builder().toolObjects(tools).build().getToolCallbacks()[0];

		// LLM responds with an empty string for the Long parameter — must not throw.
		String toolInput = """
				{
					"id": ""
				}
				""";

		assertThatCode(() -> callback.call(toolInput)).doesNotThrowAnyException();
		assertThat(tools.lastId).isNull();
	}

	@Test
	void emptyStringParameterPreservedForStringType() {
		StringTools tools = new StringTools();
		var callback = MethodToolCallbackProvider.builder().toolObjects(tools).build().getToolCallbacks()[0];

		String toolInput = """
				{
					"value": ""
				}
				""";

		assertThatCode(() -> callback.call(toolInput)).doesNotThrowAnyException();
		assertThat(tools.lastValue).isEmpty();
	}

	public static class TestTools {

		@Tool(description = "Process a list of strings")
		public String stringList(List<String> strings) {
			return strings.size() + " strings processed: " + strings;
		}

	}

	public static class NumericTools {

		Long lastId;

		@Tool(description = "Look up a record by its Long ID")
		public String findById(Long id) {
			this.lastId = id;
			return id == null ? "not found" : "found " + id;
		}

	}

	public static class StringTools {

		String lastValue;

		@Tool(description = "Echo a string value")
		public String echo(String value) {
			this.lastValue = value;
			return value == null ? "(null)" : value;
		}

	}

}
