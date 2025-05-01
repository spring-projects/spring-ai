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

package org.springframework.ai.tool.method;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import org.springframework.ai.tool.annotation.Tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link MethodToolCallbackProvider}.
 *
 * @author Christian Tzolov
 */
class MethodToolCallbackProviderTests {

	@Test
	void whenToolObjectHasToolAnnotatedMethodThenSucceed() {
		MethodToolCallbackProvider provider = MethodToolCallbackProvider.builder()
			.toolObjects(new ValidToolObject())
			.build();

		assertThat(provider.getToolCallbacks()).hasSize(1);
		assertThat(provider.getToolCallbacks()[0].getToolDefinition().name()).isEqualTo("validTool");
	}

	@Test
	void whenToolObjectHasNoToolAnnotatedMethodThenThrow() {
		assertThatThrownBy(
				() -> MethodToolCallbackProvider.builder().toolObjects(new NoToolAnnotatedMethodObject()).build())
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("No @Tool annotated methods found in");
	}

	@Test
	void whenToolObjectHasOnlyFunctionalTypeToolMethodsThenThrow() {
		assertThatThrownBy(() -> MethodToolCallbackProvider.builder()
			.toolObjects(new OnlyFunctionalTypeToolMethodsObject())
			.build()).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("No @Tool annotated methods found in");
	}

	@Test
	void whenToolObjectHasMixOfValidAndFunctionalTypeToolMethodsThenSucceed() {
		MethodToolCallbackProvider provider = MethodToolCallbackProvider.builder()
			.toolObjects(new MixedToolMethodsObject())
			.build();

		assertThat(provider.getToolCallbacks()).hasSize(1);
		assertThat(provider.getToolCallbacks()[0].getToolDefinition().name()).isEqualTo("validTool");
	}

	@Test
	void whenMultipleToolObjectsWithSameToolNameThenThrow() {
		assertThatThrownBy(() -> MethodToolCallbackProvider.builder()
			.toolObjects(new ValidToolObject(), new DuplicateToolNameObject())
			.build()).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Multiple tools with the same name (validTool) found in sources");
	}

	static class ValidToolObject {

		@Tool
		public String validTool() {
			return "Valid tool result";
		}

	}

	static class NoToolAnnotatedMethodObject {

		public String notATool() {
			return "Not a tool";
		}

	}

	static class OnlyFunctionalTypeToolMethodsObject {

		@Tool
		public Function<String, String> functionTool() {
			return input -> "Function result: " + input;
		}

		@Tool
		public Supplier<String> supplierTool() {
			return () -> "Supplier result";
		}

		@Tool
		public Consumer<String> consumerTool() {
			return input -> System.out.println("Consumer received: " + input);
		}

	}

	static class MixedToolMethodsObject {

		@Tool
		public String validTool() {
			return "Valid tool result";
		}

		@Tool
		public Function<String, String> functionTool() {
			return input -> "Function result: " + input;
		}

	}

	static class DuplicateToolNameObject {

		@Tool
		public String validTool() {
			return "Duplicate tool result";
		}

	}

}
