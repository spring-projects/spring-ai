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

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import org.junit.jupiter.api.Test;
import org.springframework.ai.observation.conventions.AiOperationType;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.ai.observation.conventions.SpringAiKind;
import org.springframework.ai.tool.definition.ToolDefinition;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DefaultToolCallingObservationConvention}.
 *
 * @author Thomas Vitale
 */
class DefaultToolCallingObservationConventionTests {

	private final DefaultToolCallingObservationConvention observationConvention = new DefaultToolCallingObservationConvention();

	@Test
	void shouldHaveName() {
		assertThat(this.observationConvention.getName())
			.isEqualTo(DefaultToolCallingObservationConvention.DEFAULT_NAME);
	}

	@Test
	void contextualName() {
		ToolCallingObservationContext observationContext = ToolCallingObservationContext.builder()
			.toolDefinition(ToolDefinition.builder().name("toolA").description("description").inputSchema("{}").build())
			.toolCallArguments("input")
			.build();
		assertThat(this.observationConvention.getContextualName(observationContext)).isEqualTo("tool_call toolA");
	}

	@Test
	void supportsOnlyChatModelObservationContext() {
		ToolCallingObservationContext observationContext = ToolCallingObservationContext.builder()
			.toolDefinition(ToolDefinition.builder().name("toolA").description("description").inputSchema("{}").build())
			.toolCallArguments("input")
			.build();
		assertThat(this.observationConvention.supportsContext(observationContext)).isTrue();
		assertThat(this.observationConvention.supportsContext(new Observation.Context())).isFalse();
	}

	@Test
	void shouldHaveLowCardinalityKeyValues() {
		ToolCallingObservationContext observationContext = ToolCallingObservationContext.builder()
			.toolDefinition(ToolDefinition.builder().name("toolA").description("description").inputSchema("{}").build())
			.toolCallArguments("input")
			.build();
		assertThat(this.observationConvention.getLowCardinalityKeyValues(observationContext)).contains(
				KeyValue.of(ToolCallingObservationDocumentation.LowCardinalityKeyNames.TOOL_DEFINITION_NAME.asString(),
						"toolA"),
				KeyValue.of(ToolCallingObservationDocumentation.LowCardinalityKeyNames.AI_OPERATION_TYPE.asString(),
						AiOperationType.FRAMEWORK.value()),
				KeyValue.of(ToolCallingObservationDocumentation.LowCardinalityKeyNames.AI_PROVIDER.asString(),
						AiProvider.SPRING_AI.value()),
				KeyValue.of(ToolCallingObservationDocumentation.LowCardinalityKeyNames.SPRING_AI_KIND,
						SpringAiKind.TOOL_CALL.value()));
	}

	@Test
	void shouldHaveHighCardinalityKeyValues() {
		String toolCallInput = """
				{
					"lizard": "George"
				}
				""";
		ToolCallingObservationContext observationContext = ToolCallingObservationContext.builder()
			.toolDefinition(ToolDefinition.builder().name("toolA").description("description").inputSchema("{}").build())
			.toolCallArguments(toolCallInput)
			.toolCallResult("Mission accomplished!")
			.build();
		assertThat(this.observationConvention.getHighCardinalityKeyValues(observationContext)).contains(
				KeyValue.of(ToolCallingObservationDocumentation.HighCardinalityKeyNames.TOOL_DEFINITION_DESCRIPTION
					.asString(), "description"),
				KeyValue.of(
						ToolCallingObservationDocumentation.HighCardinalityKeyNames.TOOL_DEFINITION_SCHEMA.asString(),
						"{}"));
	}

}
