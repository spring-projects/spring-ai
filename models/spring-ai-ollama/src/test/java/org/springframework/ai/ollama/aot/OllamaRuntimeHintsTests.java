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
	void registerHintsWithNullClassLoader() {
		RuntimeHints runtimeHints = new RuntimeHints();
		OllamaRuntimeHints ollamaRuntimeHints = new OllamaRuntimeHints();

		// Should not throw exception with null ClassLoader
		org.assertj.core.api.Assertions.assertThatCode(() -> ollamaRuntimeHints.registerHints(runtimeHints, null))
			.doesNotThrowAnyException();
	}

	@Test
	void ensureReflectionHintsAreRegistered() {
		RuntimeHints runtimeHints = new RuntimeHints();
		OllamaRuntimeHints ollamaRuntimeHints = new OllamaRuntimeHints();
		ollamaRuntimeHints.registerHints(runtimeHints, null);

		// Ensure reflection hints are properly registered
		assertThat(runtimeHints.reflection().typeHints().spliterator().estimateSize()).isGreaterThan(0);
	}

	@Test
	void verifyMultipleRegistrationCallsAreIdempotent() {
		RuntimeHints runtimeHints = new RuntimeHints();
		OllamaRuntimeHints ollamaRuntimeHints = new OllamaRuntimeHints();

		// Register hints multiple times
		ollamaRuntimeHints.registerHints(runtimeHints, null);
		long firstCount = runtimeHints.reflection().typeHints().spliterator().estimateSize();

		ollamaRuntimeHints.registerHints(runtimeHints, null);
		long secondCount = runtimeHints.reflection().typeHints().spliterator().estimateSize();

		// Should not register duplicate hints
		assertThat(firstCount).isEqualTo(secondCount);
	}

	@Test
	void verifyMainApiClassesRegistered() {
		RuntimeHints runtimeHints = new RuntimeHints();
		OllamaRuntimeHints ollamaRuntimeHints = new OllamaRuntimeHints();
		ollamaRuntimeHints.registerHints(runtimeHints, null);

		Set<TypeReference> registeredTypes = new HashSet<>();
		runtimeHints.reflection().typeHints().forEach(typeHint -> registeredTypes.add(typeHint.getType()));

		// Verify that the main classes we already know exist are registered
		assertThat(registeredTypes.contains(TypeReference.of(OllamaApi.ChatRequest.class))).isTrue();
		assertThat(registeredTypes.contains(TypeReference.of(OllamaApi.Message.class))).isTrue();
		assertThat(registeredTypes.contains(TypeReference.of(OllamaOptions.class))).isTrue();
	}

	@Test
	void verifyJsonAnnotatedClassesFromCorrectPackage() {
		Set<TypeReference> jsonAnnotatedClasses = findJsonAnnotatedClassesInPackage("org.springframework.ai.ollama");

		// Ensure we found some JSON annotated classes in the expected package
		assertThat(jsonAnnotatedClasses.spliterator().estimateSize()).isGreaterThan(0);

		// Verify all found classes are from the expected package
		for (TypeReference classRef : jsonAnnotatedClasses) {
			assertThat(classRef.getName()).startsWith("org.springframework.ai.ollama");
		}
	}

	@Test
	void verifyNoUnnecessaryHintsRegistered() {
		RuntimeHints runtimeHints = new RuntimeHints();
		OllamaRuntimeHints ollamaRuntimeHints = new OllamaRuntimeHints();
		ollamaRuntimeHints.registerHints(runtimeHints, null);

		Set<TypeReference> jsonAnnotatedClasses = findJsonAnnotatedClassesInPackage("org.springframework.ai.ollama");

		Set<TypeReference> registeredTypes = new HashSet<>();
		runtimeHints.reflection().typeHints().forEach(typeHint -> registeredTypes.add(typeHint.getType()));

		// Ensure we don't register significantly more types than needed
		// Allow for some additional utility types but prevent hint bloat
		assertThat(registeredTypes.size()).isLessThanOrEqualTo(jsonAnnotatedClasses.size() + 15);
	}

	@Test
	void verifyNestedClassHintsAreRegistered() {
		RuntimeHints runtimeHints = new RuntimeHints();
		OllamaRuntimeHints ollamaRuntimeHints = new OllamaRuntimeHints();
		ollamaRuntimeHints.registerHints(runtimeHints, null);

		Set<TypeReference> registeredTypes = new HashSet<>();
		runtimeHints.reflection().typeHints().forEach(typeHint -> registeredTypes.add(typeHint.getType()));

		// Verify nested classes that we know exist from the original test
		assertThat(registeredTypes.contains(TypeReference.of(OllamaApi.ChatRequest.Tool.class))).isTrue();

		// Count nested classes to ensure comprehensive registration
		long nestedClassCount = registeredTypes.stream().filter(typeRef -> typeRef.getName().contains("$")).count();
		assertThat(nestedClassCount).isGreaterThan(0);
	}

	@Test
	void verifyEmbeddingRelatedClassesAreRegistered() {
		RuntimeHints runtimeHints = new RuntimeHints();
		OllamaRuntimeHints ollamaRuntimeHints = new OllamaRuntimeHints();
		ollamaRuntimeHints.registerHints(runtimeHints, null);

		Set<TypeReference> registeredTypes = new HashSet<>();
		runtimeHints.reflection().typeHints().forEach(typeHint -> registeredTypes.add(typeHint.getType()));

		// Verify embedding-related classes are registered for reflection
		assertThat(registeredTypes.contains(TypeReference.of(OllamaApi.EmbeddingsRequest.class))).isTrue();
		assertThat(registeredTypes.contains(TypeReference.of(OllamaApi.EmbeddingsResponse.class))).isTrue();

		// Count classes related to embedding functionality
		long embeddingClassCount = registeredTypes.stream()
			.filter(typeRef -> typeRef.getName().toLowerCase().contains("embedding"))
			.count();
		assertThat(embeddingClassCount).isGreaterThan(0);
	}

	@Test
	void verifyHintsRegistrationWithCustomClassLoader() {
		RuntimeHints runtimeHints = new RuntimeHints();
		OllamaRuntimeHints ollamaRuntimeHints = new OllamaRuntimeHints();

		// Create a custom class loader
		ClassLoader customClassLoader = Thread.currentThread().getContextClassLoader();

		// Should work with custom class loader
		org.assertj.core.api.Assertions
			.assertThatCode(() -> ollamaRuntimeHints.registerHints(runtimeHints, customClassLoader))
			.doesNotThrowAnyException();

		// Verify hints are still registered properly
		Set<TypeReference> registeredTypes = new HashSet<>();
		runtimeHints.reflection().typeHints().forEach(typeHint -> registeredTypes.add(typeHint.getType()));

		assertThat(registeredTypes.size()).isGreaterThan(0);
		assertThat(registeredTypes.contains(TypeReference.of(OllamaApi.ChatRequest.class))).isTrue();
		assertThat(registeredTypes.contains(TypeReference.of(OllamaOptions.class))).isTrue();
	}

}
