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
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
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
	 * When streaming tool call aggregation produces an empty argument map (for example,
	 * when partial JSON arrives as {@code "{}"}), a required parameter will be null in
	 * the input map. Previously, {@code buildTypedArgument} returned null, the tool was
	 * invoked with null, and it often produced a valid-looking empty response. The model
	 * would then retry the exact same call repeatedly, causing runaway token usage.
	 *
	 * <p>
	 * The fix must throw a {@link ToolExecutionException} so the standard error processor
	 * surfaces the problem to the model.
	 *
	 * <p>
	 * <b>Loop safety for this test:</b> The test calls the tool callback exactly once and
	 * asserts that the call throws. There is no recursion, no retry loop, and no chat
	 * model involved — so this test cannot itself trigger a runaway loop even if the fix
	 * regresses.
	 */
	@Test
	void testMissingRequiredParameterThrowsToolExecutionException() {
		AtomicBoolean methodWasInvoked = new AtomicBoolean(false);
		RequiredParamTools testObject = new RequiredParamTools(methodWasInvoked);

		var callback = MethodToolCallbackProvider.builder().toolObjects(testObject).build().getToolCallbacks()[0];

		// Simulate the streaming aggregation failure scenario: the arguments JSON parsed
		// successfully but the required parameter is absent from the object.
		String emptyArgs = "{}";

		assertThatThrownBy(() -> callback.call(emptyArgs)).isInstanceOf(ToolExecutionException.class)
			.hasMessageContaining("Missing required parameter")
			.hasMessageContaining("points");

		// Critical: the underlying method must NOT have been invoked with null. If it
		// had been, the tool would silently "succeed" and the model would loop.
		assertThat(methodWasInvoked).isFalse();
	}

	/**
	 * Complementary test: a parameter marked {@code @ToolParam(required = false)} should
	 * be allowed to be null — we must not over-correct and start rejecting legitimately
	 * optional arguments.
	 */
	@Test
	void testMissingOptionalParameterIsAllowed() {
		OptionalParamTools testObject = new OptionalParamTools();

		var callback = MethodToolCallbackProvider.builder().toolObjects(testObject).build().getToolCallbacks()[0];

		// The method will be invoked with a null `note` and should return normally.
		assertThatCode(() -> {
			String result = callback.call("{}");
			assertThat(result).contains("note=null");
		}).doesNotThrowAnyException();
	}

	/**
	 * A method with no parameters at all should still be callable with an empty argument
	 * map — the zero-param case. This protects against regressing in the opposite
	 * direction.
	 */
	@Test
	void testZeroParamToolIsCallableWithEmptyArgs() {
		ZeroParamTools testObject = new ZeroParamTools();

		var callback = MethodToolCallbackProvider.builder().toolObjects(testObject).build().getToolCallbacks()[0];

		assertThatCode(() -> {
			String result = callback.call("{}");
			assertThat(result).isEqualTo("\"pong\"");
		}).doesNotThrowAnyException();
	}

	public static class TestTools {

		@Tool(description = "Process a list of strings")
		public String stringList(List<String> strings) {
			return strings.size() + " strings processed: " + strings;
		}

	}

	public static class RequiredParamTools {

		private final AtomicBoolean invoked;

		RequiredParamTools(AtomicBoolean invoked) {
			this.invoked = invoked;
		}

		@Tool(description = "Draw a path on a chart")
		public String drawPath(@ToolParam(description = "Points of the path") List<String> points) {
			this.invoked.set(true);
			// If this ever runs, return a value that would look like success to a model,
			// demonstrating exactly the silent-failure pattern the test guards against.
			return "drew path with " + (points == null ? "null" : points.size() + " points");
		}

	}

	public static class OptionalParamTools {

		@Tool(description = "Record something with an optional note")
		public String record(@ToolParam(description = "An optional note", required = false) String note) {
			return "note=" + note;
		}

	}

	public static class ZeroParamTools {

		@Tool(description = "Ping the server")
		public String ping() {
			return "pong";
		}

	}

}
