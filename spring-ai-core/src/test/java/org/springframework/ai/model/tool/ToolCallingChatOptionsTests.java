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
package org.springframework.ai.model.tool;

import org.junit.jupiter.api.Test;
import org.springframework.ai.model.function.FunctionCallingOptions;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

/**
 * Unit tests for {@link ToolCallingChatOptions}.
 *
 * @author Thomas Vitale
 */
class ToolCallingChatOptionsTests {

	@Test
	void whenToolCallingChatOptionsAndExecutionEnabledTrue() {
		ToolCallingChatOptions options = new DefaultToolCallingChatOptions();
		options.setInternalToolExecutionEnabled(true);
		assertThat(ToolCallingChatOptions.isInternalToolExecutionEnabled(options)).isTrue();
	}

	@Test
	void whenToolCallingChatOptionsAndExecutionEnabledFalse() {
		ToolCallingChatOptions options = new DefaultToolCallingChatOptions();
		options.setInternalToolExecutionEnabled(false);
		assertThat(ToolCallingChatOptions.isInternalToolExecutionEnabled(options)).isFalse();
	}

	@Test
	void whenToolCallingChatOptionsAndExecutionEnabledDefault() {
		ToolCallingChatOptions options = new DefaultToolCallingChatOptions();
		assertThat(ToolCallingChatOptions.isInternalToolExecutionEnabled(options)).isTrue();
	}

	@Test
	void whenFunctionCallingOptionsAndExecutionEnabledTrue() {
		FunctionCallingOptions options = FunctionCallingOptions.builder().build();
		options.setProxyToolCalls(false);
		assertThat(ToolCallingChatOptions.isInternalToolExecutionEnabled(options)).isTrue();
	}

	@Test
	void whenFunctionCallingOptionsAndExecutionEnabledFalse() {
		FunctionCallingOptions options = FunctionCallingOptions.builder().build();
		options.setProxyToolCalls(true);
		assertThat(ToolCallingChatOptions.isInternalToolExecutionEnabled(options)).isFalse();
	}

	@Test
	void whenFunctionCallingOptionsAndExecutionEnabledDefault() {
		FunctionCallingOptions options = FunctionCallingOptions.builder().build();
		assertThat(ToolCallingChatOptions.isInternalToolExecutionEnabled(options)).isTrue();
	}

}
