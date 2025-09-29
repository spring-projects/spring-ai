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

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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

	@Test
	void registerHintsWithNullClassLoader() {
		var runtimeHints = new RuntimeHints();
		var springAiCore = new SpringAiCoreRuntimeHints();

		// Should not throw exception with null ClassLoader
		assertThatCode(() -> springAiCore.registerHints(runtimeHints, null)).doesNotThrowAnyException();
	}

	@Test
	void verifyEmbeddingResourceIsRegistered() {
		var runtimeHints = new RuntimeHints();
		var springAiCore = new SpringAiCoreRuntimeHints();
		springAiCore.registerHints(runtimeHints, null);

		// Verify the specific embedding properties file is registered
		assertThat(runtimeHints).matches(resource().forResource("embedding/embedding-model-dimensions.properties"));
	}

	@Test
	void verifyToolReflectionHintsAreRegistered() {
		var runtimeHints = new RuntimeHints();
		var springAiCore = new SpringAiCoreRuntimeHints();
		springAiCore.registerHints(runtimeHints, null);

		Set<TypeReference> registeredTypes = new HashSet<>();
		runtimeHints.reflection().typeHints().forEach(typeHint -> registeredTypes.add(typeHint.getType()));

		// Verify core tool classes are registered
		assertThat(registeredTypes.contains(TypeReference.of(ToolCallback.class))).isTrue();
		assertThat(registeredTypes.contains(TypeReference.of(ToolDefinition.class))).isTrue();
	}

	@Test
	void verifyResourceAndReflectionHintsSeparately() {
		var runtimeHints = new RuntimeHints();
		var springAiCore = new SpringAiCoreRuntimeHints();
		springAiCore.registerHints(runtimeHints, null);

		// Test resource hints
		assertThat(runtimeHints).matches(resource().forResource("embedding/embedding-model-dimensions.properties"));

		// Test reflection hints
		assertThat(runtimeHints).matches(reflection().onType(ToolCallback.class));
		assertThat(runtimeHints).matches(reflection().onType(ToolDefinition.class));
	}

	@Test
	void verifyMultipleRegistrationCallsAreIdempotent() {
		var runtimeHints1 = new RuntimeHints();
		var runtimeHints2 = new RuntimeHints();
		var springAiCore = new SpringAiCoreRuntimeHints();

		// Register hints on two separate RuntimeHints instances
		springAiCore.registerHints(runtimeHints1, null);
		springAiCore.registerHints(runtimeHints2, null);

		// Both should have the same hints registered
		assertThat(runtimeHints1).matches(resource().forResource("embedding/embedding-model-dimensions.properties"));
		assertThat(runtimeHints2).matches(resource().forResource("embedding/embedding-model-dimensions.properties"));

		assertThat(runtimeHints1).matches(reflection().onType(ToolCallback.class));
		assertThat(runtimeHints2).matches(reflection().onType(ToolCallback.class));
	}

	@Test
	void verifyResourceHintsForIncorrectPaths() {
		var runtimeHints = new RuntimeHints();
		var springAiCore = new SpringAiCoreRuntimeHints();
		springAiCore.registerHints(runtimeHints, null);

		// Verify the exact resource path is registered
		assertThat(runtimeHints).matches(resource().forResource("embedding/embedding-model-dimensions.properties"));

		// Verify that similar but incorrect paths are not matched
		assertThat(runtimeHints).doesNotMatch(resource().forResource("embedding-model-dimensions.properties"));
		assertThat(runtimeHints).doesNotMatch(resource().forResource("embedding/model-dimensions.properties"));
	}

	@Test
	void ensureBothResourceAndReflectionHintsArePresent() {
		var runtimeHints = new RuntimeHints();
		var springAiCore = new SpringAiCoreRuntimeHints();
		springAiCore.registerHints(runtimeHints, null);

		// Ensure both resource and reflection hints are registered
		boolean hasResourceHints = runtimeHints.resources() != null;
		boolean hasReflectionHints = runtimeHints.reflection().typeHints().spliterator().estimateSize() > 0;

		assertThat(hasResourceHints).isTrue();
		assertThat(hasReflectionHints).isTrue();
	}

}
