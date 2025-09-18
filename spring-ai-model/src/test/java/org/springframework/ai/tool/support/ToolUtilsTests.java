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

package org.springframework.ai.tool.support;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import org.springframework.ai.tool.annotation.Tool;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ToolUtils}.
 *
 * @author Hyunjoon Park
 * @since 1.0.0
 */
class ToolUtilsTests {

	@Test
	void getToolNameFromMethodWithoutAnnotation() throws NoSuchMethodException {
		Method method = TestTools.class.getMethod("simpleMethod");
		String toolName = ToolUtils.getToolName(method);
		assertThat(toolName).isEqualTo("simpleMethod");
	}

	@Test
	void getToolNameFromMethodWithAnnotationButNoName() throws NoSuchMethodException {
		Method method = TestTools.class.getMethod("annotatedMethodWithoutName");
		String toolName = ToolUtils.getToolName(method);
		assertThat(toolName).isEqualTo("annotatedMethodWithoutName");
	}

	@Test
	void getToolNameFromMethodWithValidName() throws NoSuchMethodException {
		Method method = TestTools.class.getMethod("methodWithValidName");
		String toolName = ToolUtils.getToolName(method);
		assertThat(toolName).isEqualTo("valid_tool-name.v1");
	}

	@Test
	void getToolNameFromMethodWithNameContainingSpaces() throws NoSuchMethodException {
		// Tool names with spaces are now allowed but will generate a warning log
		Method method = TestTools.class.getMethod("methodWithSpacesInName");
		String toolName = ToolUtils.getToolName(method);
		assertThat(toolName).isEqualTo("invalid tool name");
	}

	@Test
	void getToolNameFromMethodWithNameContainingSpecialChars() throws NoSuchMethodException {
		// Tool names with special characters are now allowed but will generate a warning
		// log
		Method method = TestTools.class.getMethod("methodWithSpecialCharsInName");
		String toolName = ToolUtils.getToolName(method);
		assertThat(toolName).isEqualTo("tool@name!");
	}

	@Test
	void getToolNameFromMethodWithNameContainingParentheses() throws NoSuchMethodException {
		// Tool names with parentheses are now allowed but will generate a warning log
		Method method = TestTools.class.getMethod("methodWithParenthesesInName");
		String toolName = ToolUtils.getToolName(method);
		assertThat(toolName).isEqualTo("tool()");
	}

	@Test
	void getToolNameFromMethodWithEmptyName() throws NoSuchMethodException {
		Method method = TestTools.class.getMethod("methodWithEmptyName");
		// When name is empty, it falls back to method name which is valid
		String toolName = ToolUtils.getToolName(method);
		assertThat(toolName).isEqualTo("methodWithEmptyName");
	}

	@Test
	void getToolDescriptionFromMethodWithoutAnnotation() throws NoSuchMethodException {
		Method method = TestTools.class.getMethod("simpleMethod");
		String description = ToolUtils.getToolDescription(method);
		assertThat(description).isEqualTo("simple method");
	}

	@Test
	void getToolDescriptionFromMethodWithAnnotationButNoDescription() throws NoSuchMethodException {
		Method method = TestTools.class.getMethod("annotatedMethodWithoutName");
		String description = ToolUtils.getToolDescription(method);
		assertThat(description).isEqualTo("annotatedMethodWithoutName");
	}

	@Test
	void getToolDescriptionFromMethodWithDescription() throws NoSuchMethodException {
		Method method = TestTools.class.getMethod("methodWithDescription");
		String description = ToolUtils.getToolDescription(method);
		assertThat(description).isEqualTo("This is a tool description");
	}

	@Test
	void getToolNameFromMethodWithUnicodeCharacters() throws NoSuchMethodException {
		// Tool names with unicode characters should be allowed for non-English contexts
		Method method = TestTools.class.getMethod("methodWithUnicodeName");
		String toolName = ToolUtils.getToolName(method);
		assertThat(toolName).isEqualTo("获取天气");
	}

	// Test helper class with various tool methods
	public static class TestTools {

		public void simpleMethod() {
			// Method without @Tool annotation
		}

		@Tool
		public void annotatedMethodWithoutName() {
			// Method with @Tool but no name specified
		}

		@Tool(name = "valid_tool-name.v1")
		public void methodWithValidName() {
			// Method with valid tool name
		}

		@Tool(name = "invalid tool name")
		public void methodWithSpacesInName() {
			// Method with spaces in tool name (invalid)
		}

		@Tool(name = "tool@name!")
		public void methodWithSpecialCharsInName() {
			// Method with special characters in tool name (invalid)
		}

		@Tool(name = "tool()")
		public void methodWithParenthesesInName() {
			// Method with parentheses in tool name (invalid)
		}

		@Tool(name = "")
		public void methodWithEmptyName() {
			// Method with empty name (falls back to method name)
		}

		@Tool(description = "This is a tool description")
		public void methodWithDescription() {
			// Method with description
		}

		@Tool(name = "获取天气")
		public void methodWithUnicodeName() {
			// Method with unicode characters in tool name (Chinese: "get weather")
		}

	}

}
