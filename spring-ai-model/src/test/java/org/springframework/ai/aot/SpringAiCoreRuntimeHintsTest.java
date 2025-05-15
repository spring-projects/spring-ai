/*
 * Copyright 2023-2024 the original author or authors.
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

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.aot.hint.RuntimeHints;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.aot.hint.predicate.RuntimeHintsPredicates.reflection;
import static org.springframework.aot.hint.predicate.RuntimeHintsPredicates.resource;

class SpringAiCoreRuntimeHintsTest {

	@Test
	void core() {
		var runtimeHints = new RuntimeHints();
		var springAiCore = new SpringAiCoreRuntimeHints();
		springAiCore.registerHints(runtimeHints, null);

		// Verify resource hints
		assertThat(runtimeHints).matches(resource().forResource("embedding/embedding-model-dimensions.properties"));

		// Verify ToolCallback and ToolDefinition type registration
		assertThat(runtimeHints).matches(reflection().onType(ToolCallback.class));
		assertThat(runtimeHints).matches(reflection().onType(ToolDefinition.class));
	}

}
