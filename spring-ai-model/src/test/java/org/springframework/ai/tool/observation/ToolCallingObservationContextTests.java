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

package org.springframework.ai.tool.observation;

import org.junit.jupiter.api.Test;

import org.springframework.ai.tool.definition.ToolDefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ToolCallingObservationContext}.
 *
 * @author Thomas Vitale
 */
class ToolCallingObservationContextTests {

	@Test
	void whenMandatoryRequestOptionsThenReturn() {
		var observationContext = ToolCallingObservationContext.builder()
			.toolDefinition(ToolDefinition.builder().name("toolA").description("description").inputSchema("{}").build())
			.build();
		assertThat(observationContext).isNotNull();
	}

	@Test
	void whenToolArgumentsIsNullThenReturn() {
		var observationContext = ToolCallingObservationContext.builder()
			.toolDefinition(ToolDefinition.builder().name("toolA").description("description").inputSchema("{}").build())
			.toolCallArguments(null)
			.build();
		assertThat(observationContext).isNotNull();
		assertThat(observationContext.getToolCallArguments()).isEqualTo("{}");
	}

	@Test
	void whenToolArgumentsIsNotNullThenReturn() {
		var observationContext = ToolCallingObservationContext.builder()
			.toolDefinition(ToolDefinition.builder().name("toolA").description("description").inputSchema("{}").build())
			.toolCallArguments("lizard")
			.build();
		assertThat(observationContext).isNotNull();
		assertThat(observationContext.getToolCallArguments()).isEqualTo("lizard");
	}

	@Test
	void whenToolDefinitionIsNullThenThrow() {
		assertThatThrownBy(() -> ToolCallingObservationContext.builder().toolCallArguments("lizard").build())
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("toolDefinition cannot be null");
	}

	@Test
	void whenToolMetadataIsNullThenThrow() {
		assertThatThrownBy(() -> ToolCallingObservationContext.builder()
			.toolDefinition(ToolDefinition.builder().name("toolA").description("description").inputSchema("{}").build())
			.toolCallArguments("lizard")
			.toolMetadata(null)
			.build()).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("toolMetadata cannot be null");
	}

	@Test
	void whenToolArgumentsIsEmptyStringThenReturnEmptyString() {
		var observationContext = ToolCallingObservationContext.builder()
			.toolDefinition(ToolDefinition.builder().name("toolA").description("description").inputSchema("{}").build())
			.toolCallArguments("")
			.build();
		assertThat(observationContext).isNotNull();
		assertThat(observationContext.getToolCallArguments()).isEqualTo("");
	}

	@Test
	void whenToolCallResultIsNullThenReturnNull() {
		var observationContext = ToolCallingObservationContext.builder()
			.toolDefinition(ToolDefinition.builder().name("toolA").description("description").inputSchema("{}").build())
			.toolCallResult(null)
			.build();
		assertThat(observationContext).isNotNull();
		assertThat(observationContext.getToolCallResult()).isNull();
	}

	@Test
	void whenToolCallResultIsEmptyStringThenReturnEmptyString() {
		var observationContext = ToolCallingObservationContext.builder()
			.toolDefinition(ToolDefinition.builder().name("toolA").description("description").inputSchema("{}").build())
			.toolCallResult("")
			.build();
		assertThat(observationContext).isNotNull();
		assertThat(observationContext.getToolCallResult()).isEqualTo("");
	}

	@Test
	void whenToolDefinitionIsSetThenGetReturnsIt() {
		var toolDef = ToolDefinition.builder()
			.name("testTool")
			.description("Test description")
			.inputSchema("{\"type\": \"object\"}")
			.build();

		var observationContext = ToolCallingObservationContext.builder().toolDefinition(toolDef).build();

		assertThat(observationContext.getToolDefinition()).isEqualTo(toolDef);
		assertThat(observationContext.getToolDefinition().name()).isEqualTo("testTool");
		assertThat(observationContext.getToolDefinition().description()).isEqualTo("Test description");
		assertThat(observationContext.getToolDefinition().inputSchema()).isEqualTo("{\"type\": \"object\"}");
	}

}
