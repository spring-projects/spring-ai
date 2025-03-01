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

package org.springframework.ai.tool.resolution;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DelegatingToolCallbackResolver}.
 *
 * @author Thomas Vitale
 */
class DelegatingToolCallbackResolverTests {

	@Test
	void whenToolCallbackResolversAreNullThenThrowException() {
		assertThatThrownBy(() -> new DelegatingToolCallbackResolver(null)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void whenToolCallbackResolversContainNullElementsThenThrowException() {
		var toolCallbackResolvers = new ArrayList<ToolCallbackResolver>();
		toolCallbackResolvers.add(null);
		assertThatThrownBy(() -> new DelegatingToolCallbackResolver(toolCallbackResolvers))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void whenToolCallbacksAreProvidedThenResolveToolCallback() {
		ToolCallback toolCallback = mock(ToolCallback.class);
		when(toolCallback.getToolDefinition())
			.thenReturn(ToolDefinition.builder().name("myTool").inputSchema("{}").build());
		StaticToolCallbackResolver staticToolCallbackResolver = new StaticToolCallbackResolver(List.of(toolCallback));

		DelegatingToolCallbackResolver delegatingToolCallbackResolver = new DelegatingToolCallbackResolver(
				List.of(staticToolCallbackResolver));

		assertThat(delegatingToolCallbackResolver.resolve("myTool")).isEqualTo(toolCallback);
	}

}
