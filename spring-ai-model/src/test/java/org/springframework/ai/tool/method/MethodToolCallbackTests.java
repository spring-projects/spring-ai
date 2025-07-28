
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

package org.springframework.ai.tool.method;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.ToolExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link MethodToolCallback}.
 *
 * @author Thomas Vitale
 */
class MethodToolCallbackTests {

	@Test
	void shouldThrowToolExecutionExceptionForInvalidEnumArgument() throws Exception {
		// Given
		TestToolClass testTool = new TestToolClass();
		Method method = TestToolClass.class.getDeclaredMethod("processOrder", OrderType.class);
		ToolDefinition toolDefinition = DefaultToolDefinition.builder()
			.name("processOrder")
			.description("Process an order")
			.inputSchema("{}")
			.build();

		MethodToolCallback callback = new MethodToolCallback(toolDefinition, null, method, testTool, null);

		// When/Then - Invalid enum value should throw ToolExecutionException
		assertThatThrownBy(() -> callback.call("{\"orderType\": \"INVALID_TYPE\"}"))
			.isInstanceOf(ToolExecutionException.class)
			.hasCauseInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("No enum constant");

		// Verify the ToolExecutionException contains the correct tool definition
		try {
			callback.call("{\"orderType\": \"INVALID_TYPE\"}");
		}
		catch (ToolExecutionException ex) {
			assertThat(ex.getToolDefinition()).isEqualTo(toolDefinition);
			assertThat(ex.getCause()).isInstanceOf(IllegalArgumentException.class);
		}
	}

	@Test
	void shouldSucceedWithValidEnumArgument() throws Exception {
		// Given
		TestToolClass testTool = new TestToolClass();
		Method method = TestToolClass.class.getDeclaredMethod("processOrder", OrderType.class);
		ToolDefinition toolDefinition = DefaultToolDefinition.builder()
			.name("processOrder")
			.description("Process an order")
			.inputSchema("{}")
			.build();

		MethodToolCallback callback = new MethodToolCallback(toolDefinition, null, method, testTool, null);

		// When
		String result = callback.call("{\"orderType\": \"ONE_DAY\"}");

		// Then
		assertThat(result).isEqualTo("\"Processing ONE_DAY order\"");
	}

	// Test classes
	static class TestToolClass {

		@Tool(description = "Process an order with the specified delivery type")
		public String processOrder(OrderType orderType) {
			return "Processing " + orderType + " order";
		}

	}

	enum OrderType {

		ONE_DAY, TWO_DAY, THREE_DAY

	}

}