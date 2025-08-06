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

package org.springframework.ai.anthropic.aot;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;
import org.springframework.aot.hint.MemberCategory;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.ai.aot.AiRuntimeHints.findJsonAnnotatedClassesInPackage;

class AnthropicRuntimeHintsTests {

	private RuntimeHints runtimeHints;

	private AnthropicRuntimeHints anthropicRuntimeHints;

	@BeforeEach
	void setUp() {
		runtimeHints = new RuntimeHints();
		anthropicRuntimeHints = new AnthropicRuntimeHints();
	}

	@Test
	void registerHints() {
		anthropicRuntimeHints.registerHints(runtimeHints, null);

		Set<TypeReference> jsonAnnotatedClasses = findJsonAnnotatedClassesInPackage("org.springframework.ai.anthropic");

		Set<TypeReference> registeredTypes = new HashSet<>();
		runtimeHints.reflection().typeHints().forEach(typeHint -> registeredTypes.add(typeHint.getType()));

		for (TypeReference jsonAnnotatedClass : jsonAnnotatedClasses) {
			assertThat(registeredTypes.contains(jsonAnnotatedClass)).isTrue();
		}

		// Check a few more specific ones
		assertThat(registeredTypes.contains(TypeReference.of(AnthropicApi.class))).isTrue();
		assertThat(registeredTypes.contains(TypeReference.of(AnthropicApi.Role.class))).isTrue();
		assertThat(registeredTypes.contains(TypeReference.of(AnthropicApi.ThinkingType.class))).isTrue();
		assertThat(registeredTypes.contains(TypeReference.of(AnthropicApi.EventType.class))).isTrue();
		assertThat(registeredTypes.contains(TypeReference.of(AnthropicApi.ContentBlock.class))).isTrue();
		assertThat(registeredTypes.contains(TypeReference.of(AnthropicApi.ChatCompletionRequest.class))).isTrue();
		assertThat(registeredTypes.contains(TypeReference.of(AnthropicApi.AnthropicMessage.class))).isTrue();
	}

	@Test
	void registerHintsWithNullClassLoader() {
		// Test that registering hints with null ClassLoader works correctly
		anthropicRuntimeHints.registerHints(runtimeHints, null);

		Set<TypeReference> registeredTypes = new HashSet<>();
		runtimeHints.reflection().typeHints().forEach(typeHint -> registeredTypes.add(typeHint.getType()));

		assertThat(registeredTypes.size()).isGreaterThan(0);
	}

	@Test
	void registerHintsWithCustomClassLoader() {
		// Test that registering hints with a custom ClassLoader works correctly
		ClassLoader customClassLoader = Thread.currentThread().getContextClassLoader();
		anthropicRuntimeHints.registerHints(runtimeHints, customClassLoader);

		Set<TypeReference> registeredTypes = new HashSet<>();
		runtimeHints.reflection().typeHints().forEach(typeHint -> registeredTypes.add(typeHint.getType()));

		assertThat(registeredTypes.size()).isGreaterThan(0);
	}

	@Test
	void allMemberCategoriesAreRegistered() {
		anthropicRuntimeHints.registerHints(runtimeHints, null);

		Set<TypeReference> jsonAnnotatedClasses = findJsonAnnotatedClassesInPackage("org.springframework.ai.anthropic");

		// Verify that all MemberCategory values are registered for each type
		runtimeHints.reflection().typeHints().forEach(typeHint -> {
			if (jsonAnnotatedClasses.contains(typeHint.getType())) {
				Set<MemberCategory> expectedCategories = Set.of(MemberCategory.values());
				Set<MemberCategory> actualCategories = typeHint.getMemberCategories();
				assertThat(actualCategories.containsAll(expectedCategories)).isTrue();
			}
		});
	}

	@Test
	void emptyRuntimeHintsInitiallyContainsNoTypes() {
		// Verify that fresh RuntimeHints instance contains no reflection hints
		RuntimeHints emptyHints = new RuntimeHints();
		Set<TypeReference> emptyRegisteredTypes = new HashSet<>();
		emptyHints.reflection().typeHints().forEach(typeHint -> emptyRegisteredTypes.add(typeHint.getType()));

		assertThat(emptyRegisteredTypes.size()).isEqualTo(0);
	}

	@Test
	void multipleRegistrationCallsAreIdempotent() {
		// Register hints multiple times and verify no duplicates
		anthropicRuntimeHints.registerHints(runtimeHints, null);
		int firstRegistrationCount = (int) runtimeHints.reflection().typeHints().count();

		anthropicRuntimeHints.registerHints(runtimeHints, null);
		int secondRegistrationCount = (int) runtimeHints.reflection().typeHints().count();

		assertThat(firstRegistrationCount).isEqualTo(secondRegistrationCount);
	}

	@Test
	void verifyJsonAnnotatedClassesInPackageIsNotEmpty() {
		Set<TypeReference> jsonAnnotatedClasses = findJsonAnnotatedClassesInPackage("org.springframework.ai.anthropic");
		assertThat(jsonAnnotatedClasses.isEmpty()).isFalse();
	}

	@Test
	void verifyEnumTypesAreRegistered() {
		anthropicRuntimeHints.registerHints(runtimeHints, null);

		Set<TypeReference> registeredTypes = new HashSet<>();
		runtimeHints.reflection().typeHints().forEach(typeHint -> registeredTypes.add(typeHint.getType()));

		// Verify enum types are properly registered
		assertThat(registeredTypes.contains(TypeReference.of(AnthropicApi.Role.class))).isTrue();
		assertThat(registeredTypes.contains(TypeReference.of(AnthropicApi.ThinkingType.class))).isTrue();
		assertThat(registeredTypes.contains(TypeReference.of(AnthropicApi.EventType.class))).isTrue();
	}

}
