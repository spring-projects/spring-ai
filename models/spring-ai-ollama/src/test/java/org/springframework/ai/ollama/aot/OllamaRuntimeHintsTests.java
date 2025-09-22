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

package org.springframework.ai.ollama.aot;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.ai.aot.AiRuntimeHints.findJsonAnnotatedClassesInPackage;

class OllamaRuntimeHintsTests {

	@Test
	void registerHints() {
		RuntimeHints runtimeHints = new RuntimeHints();
		OllamaRuntimeHints ollamaRuntimeHints = new OllamaRuntimeHints();
		ollamaRuntimeHints.registerHints(runtimeHints, null);

		Set<TypeReference> jsonAnnotatedClasses = findJsonAnnotatedClassesInPackage("org.springframework.ai.ollama");

		Set<TypeReference> registeredTypes = new HashSet<>();
		runtimeHints.reflection().typeHints().forEach(typeHint -> registeredTypes.add(typeHint.getType()));

		for (TypeReference jsonAnnotatedClass : jsonAnnotatedClasses) {
			assertThat(registeredTypes.contains(jsonAnnotatedClass)).isTrue();
		}

		// Check a few more specific ones
		assertThat(registeredTypes.contains(TypeReference.of(OllamaApi.ChatRequest.class))).isTrue();
		assertThat(registeredTypes.contains(TypeReference.of(OllamaApi.ChatRequest.Tool.class))).isTrue();
		assertThat(registeredTypes.contains(TypeReference.of(OllamaApi.Message.class))).isTrue();
		assertThat(registeredTypes.contains(TypeReference.of(OllamaOptions.class))).isTrue();
	}

	@Test
	void verifyNoProxyHintsAreRegistered() {
		RuntimeHints runtimeHints = new RuntimeHints();
		OllamaRuntimeHints ollamaRuntimeHints = new OllamaRuntimeHints();
		ollamaRuntimeHints.registerHints(runtimeHints, null);

		// Ollama should only register reflection hints, not proxy hints
		assertThat(runtimeHints.proxies().jdkProxyHints().count()).isEqualTo(0);
	}

	@Test
	void verifyNoSerializationHintsAreRegistered() {
		RuntimeHints runtimeHints = new RuntimeHints();
		OllamaRuntimeHints ollamaRuntimeHints = new OllamaRuntimeHints();
		ollamaRuntimeHints.registerHints(runtimeHints, null);

		// Ollama should only register reflection hints, not serialization hints
		assertThat(runtimeHints.serialization().javaSerializationHints().count()).isEqualTo(0);
	}

	@Test
	void verifyConstructorHintsAreRegistered() {
		RuntimeHints runtimeHints = new RuntimeHints();
		OllamaRuntimeHints ollamaRuntimeHints = new OllamaRuntimeHints();
		ollamaRuntimeHints.registerHints(runtimeHints, null);

		// Verify that reflection hints include constructor access for JSON
		// deserialization
		boolean hasConstructorHints = runtimeHints.reflection()
			.typeHints()
			.anyMatch(typeHint -> typeHint.constructors().findAny().isPresent() || typeHint.getMemberCategories()
				.contains(org.springframework.aot.hint.MemberCategory.INVOKE_DECLARED_CONSTRUCTORS));

		assertThat(hasConstructorHints).as("Should register constructor hints for JSON deserialization").isTrue();
	}

	@Test
	void verifyEnumTypesAreRegistered() {
		RuntimeHints runtimeHints = new RuntimeHints();
		OllamaRuntimeHints ollamaRuntimeHints = new OllamaRuntimeHints();
		ollamaRuntimeHints.registerHints(runtimeHints, null);

		Set<TypeReference> registeredTypes = new HashSet<>();
		runtimeHints.reflection().typeHints().forEach(typeHint -> registeredTypes.add(typeHint.getType()));

		// Verify enum types are registered (critical for JSON deserialization)
		boolean hasEnumTypes = registeredTypes.stream()
			.anyMatch(tr -> tr.getName().contains("$") || tr.getName().toLowerCase().contains("role")
					|| tr.getName().toLowerCase().contains("type"));

		assertThat(hasEnumTypes).as("Enum types should be registered for native image compatibility").isTrue();
	}

	@Test
	void verifyResponseTypesAreRegistered() {
		RuntimeHints runtimeHints = new RuntimeHints();
		OllamaRuntimeHints ollamaRuntimeHints = new OllamaRuntimeHints();
		ollamaRuntimeHints.registerHints(runtimeHints, null);

		Set<TypeReference> registeredTypes = new HashSet<>();
		runtimeHints.reflection().typeHints().forEach(typeHint -> registeredTypes.add(typeHint.getType()));

		// Verify response wrapper types are registered
		assertThat(registeredTypes.stream().anyMatch(tr -> tr.getName().contains("Response")))
			.as("Response types should be registered")
			.isTrue();

		assertThat(registeredTypes.stream().anyMatch(tr -> tr.getName().contains("ChatResponse")))
			.as("ChatResponse type should be registered")
			.isTrue();
	}

	@Test
	void verifyToolRelatedClassesAreRegistered() {
		RuntimeHints runtimeHints = new RuntimeHints();
		OllamaRuntimeHints ollamaRuntimeHints = new OllamaRuntimeHints();
		ollamaRuntimeHints.registerHints(runtimeHints, null);

		Set<TypeReference> registeredTypes = new HashSet<>();
		runtimeHints.reflection().typeHints().forEach(typeHint -> registeredTypes.add(typeHint.getType()));

		// Verify tool-related classes are registered
		assertThat(registeredTypes.contains(TypeReference.of(OllamaApi.ChatRequest.Tool.class))).isTrue();

		// Count tool-related classes
		long toolClassCount = registeredTypes.stream()
			.filter(typeRef -> typeRef.getName().toLowerCase().contains("tool"))
			.count();
		assertThat(toolClassCount).isGreaterThan(0);
	}

}
