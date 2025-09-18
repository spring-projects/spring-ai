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

package org.springframework.ai.aot;

import org.junit.jupiter.api.Test;

import org.springframework.ai.tool.execution.DefaultToolCallResultConverter;
import org.springframework.aot.hint.RuntimeHints;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.aot.hint.predicate.RuntimeHintsPredicates.reflection;

/**
 * Unit tests for {@link ToolRuntimeHints}.
 */
class ToolRuntimeHintsTests {

	@Test
	void registerHints() {
		RuntimeHints runtimeHints = new RuntimeHints();
		ToolRuntimeHints toolRuntimeHints = new ToolRuntimeHints();
		toolRuntimeHints.registerHints(runtimeHints, null);
		assertThat(runtimeHints).matches(reflection().onType(DefaultToolCallResultConverter.class));
	}

	@Test
	void registerHintsWithNullClassLoader() {
		RuntimeHints runtimeHints = new RuntimeHints();
		ToolRuntimeHints toolRuntimeHints = new ToolRuntimeHints();

		// Should not throw exception with null ClassLoader
		assertThatCode(() -> toolRuntimeHints.registerHints(runtimeHints, null)).doesNotThrowAnyException();
	}

	@Test
	void registerHintsWithCustomClassLoader() {
		RuntimeHints runtimeHints = new RuntimeHints();
		ToolRuntimeHints toolRuntimeHints = new ToolRuntimeHints();
		ClassLoader customClassLoader = Thread.currentThread().getContextClassLoader();

		toolRuntimeHints.registerHints(runtimeHints, customClassLoader);

		assertThat(runtimeHints).matches(reflection().onType(DefaultToolCallResultConverter.class));
	}

	@Test
	void registerHintsMultipleTimes() {
		RuntimeHints runtimeHints = new RuntimeHints();
		ToolRuntimeHints toolRuntimeHints = new ToolRuntimeHints();

		toolRuntimeHints.registerHints(runtimeHints, null);
		toolRuntimeHints.registerHints(runtimeHints, null);

		assertThat(runtimeHints).matches(reflection().onType(DefaultToolCallResultConverter.class));
	}

	@Test
	void toolRuntimeHintsInstanceCreation() {
		assertThatCode(() -> new ToolRuntimeHints()).doesNotThrowAnyException();

		ToolRuntimeHints hints1 = new ToolRuntimeHints();
		ToolRuntimeHints hints2 = new ToolRuntimeHints();

		assertThat(hints1).isNotSameAs(hints2);
	}

}
