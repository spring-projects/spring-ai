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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.ai.aot.AiRuntimeHints.findJsonAnnotatedClassesInPackage;

class AnthropicRuntimeHintsTests {

	private RuntimeHints runtimeHints;

	private AnthropicRuntimeHints anthropicRuntimeHints;

	@BeforeEach
	void setUp() {
		this.runtimeHints = new RuntimeHints();
		this.anthropicRuntimeHints = new AnthropicRuntimeHints();
	}

	@Test
	void registerHints() {
		this.anthropicRuntimeHints.registerHints(this.runtimeHints, null);

		Set<TypeReference> jsonAnnotatedClasses = findJsonAnnotatedClassesInPackage("org.springframework.ai.anthropic");

		Set<TypeReference> registeredTypes = new HashSet<>();
		this.runtimeHints.reflection().typeHints().forEach(typeHint -> registeredTypes.add(typeHint.getType()));

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
		this.anthropicRuntimeHints.registerHints(this.runtimeHints, null);

		Set<TypeReference> registeredTypes = new HashSet<>();
		this.runtimeHints.reflection().typeHints().forEach(typeHint -> registeredTypes.add(typeHint.getType()));

		assertThat(registeredTypes.size()).isGreaterThan(0);
	}

	@Test
	void registerHintsWithCustomClassLoader() {
		// Test that registering hints with a custom ClassLoader works correctly
		ClassLoader customClassLoader = Thread.currentThread().getContextClassLoader();
		this.anthropicRuntimeHints.registerHints(this.runtimeHints, customClassLoader);

		Set<TypeReference> registeredTypes = new HashSet<>();
		this.runtimeHints.reflection().typeHints().forEach(typeHint -> registeredTypes.add(typeHint.getType()));

		assertThat(registeredTypes.size()).isGreaterThan(0);
	}

	@Test
	void allMemberCategoriesAreRegistered() {
		this.anthropicRuntimeHints.registerHints(this.runtimeHints, null);

		Set<TypeReference> jsonAnnotatedClasses = findJsonAnnotatedClassesInPackage("org.springframework.ai.anthropic");

		// Verify that all MemberCategory values are registered for each type
		this.runtimeHints.reflection().typeHints().forEach(typeHint -> {
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
		this.anthropicRuntimeHints.registerHints(this.runtimeHints, null);
		int firstRegistrationCount = (int) this.runtimeHints.reflection().typeHints().count();

		this.anthropicRuntimeHints.registerHints(this.runtimeHints, null);
		int secondRegistrationCount = (int) this.runtimeHints.reflection().typeHints().count();

		assertThat(firstRegistrationCount).isEqualTo(secondRegistrationCount);
	}

	@Test
	void verifyJsonAnnotatedClassesInPackageIsNotEmpty() {
		Set<TypeReference> jsonAnnotatedClasses = findJsonAnnotatedClassesInPackage("org.springframework.ai.anthropic");
		assertThat(jsonAnnotatedClasses.isEmpty()).isFalse();
	}

	@Test
	void verifyEnumTypesAreRegistered() {
		this.anthropicRuntimeHints.registerHints(this.runtimeHints, null);

		Set<TypeReference> registeredTypes = new HashSet<>();
		this.runtimeHints.reflection().typeHints().forEach(typeHint -> registeredTypes.add(typeHint.getType()));

		// Verify enum types are properly registered
		assertThat(registeredTypes.contains(TypeReference.of(AnthropicApi.Role.class))).isTrue();
		assertThat(registeredTypes.contains(TypeReference.of(AnthropicApi.ThinkingType.class))).isTrue();
		assertThat(registeredTypes.contains(TypeReference.of(AnthropicApi.EventType.class))).isTrue();
	}

	@Test
	void verifyNestedClassesAreRegistered() {
		this.anthropicRuntimeHints.registerHints(this.runtimeHints, null);

		Set<TypeReference> registeredTypes = new HashSet<>();
		this.runtimeHints.reflection().typeHints().forEach(typeHint -> registeredTypes.add(typeHint.getType()));

		// Verify nested classes within AnthropicApi are registered
		assertThat(registeredTypes.contains(TypeReference.of(AnthropicApi.ChatCompletionRequest.class))).isTrue();
		assertThat(registeredTypes.contains(TypeReference.of(AnthropicApi.AnthropicMessage.class))).isTrue();
		assertThat(registeredTypes.contains(TypeReference.of(AnthropicApi.ContentBlock.class))).isTrue();
	}

	@Test
	void verifyNoProxyHintsAreRegistered() {
		this.anthropicRuntimeHints.registerHints(this.runtimeHints, null);

		// This implementation should only register reflection hints, not proxy hints
		long proxyHintCount = this.runtimeHints.proxies().jdkProxyHints().count();
		assertThat(proxyHintCount).isEqualTo(0);
	}

	@Test
	void verifyNoSerializationHintsAreRegistered() {
		this.anthropicRuntimeHints.registerHints(this.runtimeHints, null);

		// This implementation should only register reflection hints, not serialization
		// hints
		long serializationHintCount = this.runtimeHints.serialization().javaSerializationHints().count();
		assertThat(serializationHintCount).isEqualTo(0);
	}

	@Test
	void verifyJsonAnnotatedClassesContainExpectedTypes() {
		Set<TypeReference> jsonAnnotatedClasses = findJsonAnnotatedClassesInPackage("org.springframework.ai.anthropic");

		// Verify that key API classes are found
		boolean containsApiClass = jsonAnnotatedClasses.stream()
			.anyMatch(typeRef -> typeRef.getName().contains("AnthropicApi")
					|| typeRef.getName().contains("ChatCompletion") || typeRef.getName().contains("AnthropicMessage"));

		assertThat(containsApiClass).isTrue();
	}

	@Test
	void verifyConsistencyAcrossInstances() {
		RuntimeHints hints1 = new RuntimeHints();
		RuntimeHints hints2 = new RuntimeHints();

		AnthropicRuntimeHints anthropicHints1 = new AnthropicRuntimeHints();
		AnthropicRuntimeHints anthropicHints2 = new AnthropicRuntimeHints();

		anthropicHints1.registerHints(hints1, null);
		anthropicHints2.registerHints(hints2, null);

		// Different instances should register the same hints
		Set<TypeReference> types1 = new HashSet<>();
		Set<TypeReference> types2 = new HashSet<>();

		hints1.reflection().typeHints().forEach(hint -> types1.add(hint.getType()));
		hints2.reflection().typeHints().forEach(hint -> types2.add(hint.getType()));

		assertThat(types1).isEqualTo(types2);
	}

	@Test
	void verifyPackageSpecificity() {
		Set<TypeReference> jsonAnnotatedClasses = findJsonAnnotatedClassesInPackage("org.springframework.ai.anthropic");

		// All found classes should be from the anthropic package specifically
		for (TypeReference classRef : jsonAnnotatedClasses) {
			assertThat(classRef.getName()).startsWith("org.springframework.ai.anthropic");
		}

		// Should not include classes from other AI packages
		for (TypeReference classRef : jsonAnnotatedClasses) {
			assertThat(classRef.getName()).doesNotContain("vertexai");
			assertThat(classRef.getName()).doesNotContain("openai");
			assertThat(classRef.getName()).doesNotContain("ollama");
		}
	}

}
