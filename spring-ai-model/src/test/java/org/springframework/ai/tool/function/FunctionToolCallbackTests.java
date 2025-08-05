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

package org.springframework.ai.tool.function;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.execution.ToolExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

/**
 * Unit tests for {@link FunctionToolCallback}.
 *
 * @author Marco Schäck
 */
class FunctionToolCallbackTests {

	@Test
	void whenToolFunctionExecutesSuccessfullyThenReturnExpectedValue() {
		// Given
		SquareRootTool squareRootTool = new SquareRootTool();
		FunctionToolCallback<SquareRootTool.Input, SquareRootTool.Output> callback = FunctionToolCallback
			.builder("squareRootTool", squareRootTool::calculate)
			.inputType(SquareRootTool.Input.class)
			.build();

		// When
		String result = callback.call("{\"number\":25}");

		// Then
		assertThat(result).isEqualTo("{\"result\":5.0}");
	}

	@Test
	void whenToolFunctionThrowsExceptionThenWrapInToolExecutionException() {
		// Given
		SquareRootTool squareRootTool = new SquareRootTool();
		FunctionToolCallback<SquareRootTool.Input, SquareRootTool.Output> callback = FunctionToolCallback
			.builder("squareRootTool", squareRootTool::calculate)
			.inputType(SquareRootTool.Input.class)
			.build();

		// When & Then
		assertThatThrownBy(() -> callback.call("{\"number\":-9}")).isInstanceOf(ToolExecutionException.class)
			.hasCause(new IllegalArgumentException("Cannot calculate square root of negative number: -9"))
			.hasMessageContaining("Cannot calculate square root of negative number: -9");
	}

	static class SquareRootTool {

		record Input(int number) {
		}

		record Output(double result) {
		}

		public Output calculate(Input input) {
			if (input.number < 0) {
				throw new IllegalArgumentException("Cannot calculate square root of negative number: " + input.number);
			}
			return new Output(Math.sqrt(input.number));
		}

	}

}
