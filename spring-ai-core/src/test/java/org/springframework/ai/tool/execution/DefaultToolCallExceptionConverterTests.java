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

package org.springframework.ai.tool.execution;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DefaultToolCallExceptionConverter}.
 *
 * @author Thomas Vitale
 */
class DefaultToolCallExceptionConverterTests {

	@Test
	void whenDefaultThenReturnMessage() {
		ToolCallExceptionConverter converter = DefaultToolCallExceptionConverter.builder().build();
		ToolExecutionException exception = new ToolExecutionException(generateTestDefinition(),
				new RuntimeException("Test"));
		assertThat(converter.convert(exception)).isEqualTo("Test");
	}

	@Test
	void whenNotAlwaysThrowThenReturnMessage() {
		ToolCallExceptionConverter converter = DefaultToolCallExceptionConverter.builder().alwaysThrow(false).build();
		ToolExecutionException exception = new ToolExecutionException(generateTestDefinition(),
				new RuntimeException("Test"));
		assertThat(converter.convert(exception)).isEqualTo("Test");
	}

	@Test
	void whenAlwaysThrowThenThrow() {
		ToolCallExceptionConverter converter = DefaultToolCallExceptionConverter.builder().alwaysThrow(true).build();
		ToolExecutionException exception = new ToolExecutionException(generateTestDefinition(),
				new RuntimeException("Test"));
		assertThatThrownBy(() -> converter.convert(exception)).isInstanceOf(ToolExecutionException.class);
	}

	private ToolDefinition generateTestDefinition() {
		return DefaultToolDefinition.builder().name("test").inputSchema("{}").build();
	}

}
