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
import org.springframework.ai.tool.definition.ToolDefinition;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ToolCallingContentObservationFilter}.
 *
 * @author Thomas Vitale
 */
class ToolCallingContentObservationFilterTests {

	ToolCallingContentObservationFilter observationFilter = new ToolCallingContentObservationFilter();

	@Test
	void whenNotSupportedObservationContextThenReturnOriginalContext() {
		var expectedContext = new Observation.Context();
		var actualContext = this.observationFilter.map(expectedContext);

		assertThat(actualContext).isEqualTo(expectedContext);
	}

	@Test
	void augmentContext() {
		var originalContext = ToolCallingObservationContext.builder()
			.toolDefinition(ToolDefinition.builder().name("toolA").description("description").inputSchema("{}").build())
			.toolCallArguments("input")
			.toolCallResult("result")
			.build();
		var augmentedContext = this.observationFilter.map(originalContext);

		assertThat(augmentedContext.getHighCardinalityKeyValues()).contains(KeyValue
			.of(ToolCallingObservationDocumentation.HighCardinalityKeyNames.TOOL_CALL_ARGUMENTS.asString(), "input"));
		assertThat(augmentedContext.getHighCardinalityKeyValues()).contains(KeyValue
			.of(ToolCallingObservationDocumentation.HighCardinalityKeyNames.TOOL_CALL_RESULT.asString(), "result"));
	}

	@Test
	void augmentContextWhenNullResult() {
		var originalContext = ToolCallingObservationContext.builder()
			.toolDefinition(ToolDefinition.builder().name("toolA").description("description").inputSchema("{}").build())
			.toolCallArguments("input")
			.toolCallResult("result")
			.build();
		var augmentedContext = this.observationFilter.map(originalContext);

		assertThat(augmentedContext.getHighCardinalityKeyValues()).contains(KeyValue
			.of(ToolCallingObservationDocumentation.HighCardinalityKeyNames.TOOL_CALL_ARGUMENTS.asString(), "input"));
		assertThat(augmentedContext.getHighCardinalityKeyValues()
			.stream()
			.filter(kv -> kv.getKey()
				.equals(ToolCallingObservationDocumentation.HighCardinalityKeyNames.TOOL_CALL_RESULT.name())))
			.isEmpty();
	}

}
