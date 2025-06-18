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

import java.util.List;
import org.junit.jupiter.api.Test;

import org.springframework.ai.tool.definition.DefaultToolDefinition;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

/**
 * Unit tests for {@link DefaultToolExecutionExceptionProcessor}.
 *
 * @author Daniel Garnier-Moiroux
 */
class DefaultToolExecutionExceptionProcessorTests {

	private final IllegalStateException toolException = new IllegalStateException("Inner exception");

	private final DefaultToolDefinition toolDefinition = new DefaultToolDefinition("toolName", "toolDescription",
			"inputSchema");

	private final ToolExecutionException toolExecutionException = new ToolExecutionException(toolDefinition,
			toolException);

	@Test
	void processReturnsMessage() {
		DefaultToolExecutionExceptionProcessor processor = DefaultToolExecutionExceptionProcessor.builder().build();

		String result = processor.process(this.toolExecutionException);

		assertThat(result).isEqualTo(this.toolException.getMessage());
	}

	@Test
	void processAlwaysThrows() {
		DefaultToolExecutionExceptionProcessor processor = DefaultToolExecutionExceptionProcessor.builder()
			.alwaysThrow(true)
			.build();

		assertThatThrownBy(() -> processor.process(this.toolExecutionException))
			.hasMessage(this.toolException.getMessage())
			.hasCauseInstanceOf(this.toolException.getClass())
			.asInstanceOf(type(ToolExecutionException.class))
			.extracting(ToolExecutionException::getToolDefinition)
			.isEqualTo(this.toolDefinition);
	}

	@Test
	void processRethrows() {
		DefaultToolExecutionExceptionProcessor processor = DefaultToolExecutionExceptionProcessor.builder()
			.alwaysThrow(false)
			.rethrowExceptions(List.of(IllegalStateException.class))
			.build();

		assertThatThrownBy(() -> processor.process(this.toolExecutionException)).isEqualTo(this.toolException);
	}

	@Test
	void processRethrowsExceptionSubclasses() {
		DefaultToolExecutionExceptionProcessor processor = DefaultToolExecutionExceptionProcessor.builder()
			.alwaysThrow(false)
			.rethrowExceptions(List.of(RuntimeException.class))
			.build();

		assertThatThrownBy(() -> processor.process(this.toolExecutionException)).isEqualTo(this.toolException);
	}

	@Test
	void processRethrowsOnlySelectExceptions() {
		DefaultToolExecutionExceptionProcessor processor = DefaultToolExecutionExceptionProcessor.builder()
			.alwaysThrow(false)
			.rethrowExceptions(List.of(IllegalStateException.class))
			.build();

		ToolExecutionException exception = new ToolExecutionException(this.toolDefinition,
				new RuntimeException("This exception was not rethrown"));
		String result = processor.process(exception);

		assertThat(result).isEqualTo("This exception was not rethrown");
	}

}
