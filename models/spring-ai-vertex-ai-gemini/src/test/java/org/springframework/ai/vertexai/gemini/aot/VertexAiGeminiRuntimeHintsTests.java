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

package org.springframework.ai.vertexai.gemini.aot;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatOptions;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.ai.aot.AiRuntimeHints.findJsonAnnotatedClassesInPackage;

/**
 * @author Christian Tzolov
 * @since 0.8.1
 */
class VertexAiGeminiRuntimeHintsTests {

	@Test
	void registerHints() {
		RuntimeHints runtimeHints = new RuntimeHints();
		VertexAiGeminiRuntimeHints vertexAiGeminiRuntimeHints = new VertexAiGeminiRuntimeHints();
		vertexAiGeminiRuntimeHints.registerHints(runtimeHints, null);

		Set<TypeReference> jsonAnnotatedClasses = findJsonAnnotatedClassesInPackage(
				"org.springframework.ai.vertexai.gemini");

		Set<TypeReference> registeredTypes = new HashSet<>();
		runtimeHints.reflection().typeHints().forEach(typeHint -> registeredTypes.add(typeHint.getType()));

		for (TypeReference jsonAnnotatedClass : jsonAnnotatedClasses) {
			assertThat(registeredTypes.contains(jsonAnnotatedClass)).isTrue();
		}

		assertThat(registeredTypes.contains(TypeReference.of(VertexAiGeminiChatOptions.class))).isTrue();
	}

	@Test
	void registerHintsWithNullClassLoader() {
		RuntimeHints runtimeHints = new RuntimeHints();
		VertexAiGeminiRuntimeHints vertexAiGeminiRuntimeHints = new VertexAiGeminiRuntimeHints();

		// Should not throw exception with null ClassLoader
		org.assertj.core.api.Assertions
			.assertThatCode(() -> vertexAiGeminiRuntimeHints.registerHints(runtimeHints, null))
			.doesNotThrowAnyException();
	}

	@Test
	void ensureReflectionHintsAreRegistered() {
		RuntimeHints runtimeHints = new RuntimeHints();
		VertexAiGeminiRuntimeHints vertexAiGeminiRuntimeHints = new VertexAiGeminiRuntimeHints();
		vertexAiGeminiRuntimeHints.registerHints(runtimeHints, null);

		// Ensure reflection hints are properly registered
		assertThat(runtimeHints.reflection().typeHints().spliterator().estimateSize()).isGreaterThan(0);
	}

	@Test
	void verifyMultipleRegistrationCallsAreIdempotent() {
		RuntimeHints runtimeHints = new RuntimeHints();
		VertexAiGeminiRuntimeHints vertexAiGeminiRuntimeHints = new VertexAiGeminiRuntimeHints();

		// Register hints multiple times
		vertexAiGeminiRuntimeHints.registerHints(runtimeHints, null);
		long firstCount = runtimeHints.reflection().typeHints().spliterator().estimateSize();

		vertexAiGeminiRuntimeHints.registerHints(runtimeHints, null);
		long secondCount = runtimeHints.reflection().typeHints().spliterator().estimateSize();

		// Should not register duplicate hints
		assertThat(firstCount).isEqualTo(secondCount);
	}

	@Test
	void verifyJsonAnnotatedClassesFromCorrectPackage() {
		Set<TypeReference> jsonAnnotatedClasses = findJsonAnnotatedClassesInPackage(
				"org.springframework.ai.vertexai.gemini");

		// Ensure we found some JSON annotated classes in the expected package
		assertThat(jsonAnnotatedClasses.spliterator().estimateSize()).isGreaterThan(0);

		// Verify all found classes are from the expected package
		for (TypeReference classRef : jsonAnnotatedClasses) {
			assertThat(classRef.getName()).startsWith("org.springframework.ai.vertexai.gemini");
		}
	}

	@Test
	void verifyNoUnnecessaryHintsRegistered() {
		RuntimeHints runtimeHints = new RuntimeHints();
		VertexAiGeminiRuntimeHints vertexAiGeminiRuntimeHints = new VertexAiGeminiRuntimeHints();
		vertexAiGeminiRuntimeHints.registerHints(runtimeHints, null);

		Set<TypeReference> jsonAnnotatedClasses = findJsonAnnotatedClassesInPackage(
				"org.springframework.ai.vertexai.gemini");

		Set<TypeReference> registeredTypes = new HashSet<>();
		runtimeHints.reflection().typeHints().forEach(typeHint -> registeredTypes.add(typeHint.getType()));

		// Ensure we don't register significantly more types than needed
		// Allow for some additional utility types but prevent hint bloat
		assertThat(registeredTypes.size()).isLessThanOrEqualTo(jsonAnnotatedClasses.size() + 10);
	}

	@Test
	void verifySpecificReflectionHintTypes() {
		RuntimeHints runtimeHints = new RuntimeHints();
		VertexAiGeminiRuntimeHints vertexAiGeminiRuntimeHints = new VertexAiGeminiRuntimeHints();
		vertexAiGeminiRuntimeHints.registerHints(runtimeHints, null);

		Set<TypeReference> registeredTypes = new HashSet<>();
		runtimeHints.reflection().typeHints().forEach(typeHint -> {
			registeredTypes.add(typeHint.getType());
			// Verify that constructors, fields, and methods are properly registered
			assertThat(typeHint.constructors()).isNotNull();
			assertThat(typeHint.fields()).isNotNull();
			assertThat(typeHint.methods()).isNotNull();
		});

		// Verify that at least the main chat options class is registered
		assertThat(registeredTypes.contains(TypeReference.of(VertexAiGeminiChatOptions.class))).isTrue();
	}

	@Test
	void verifyRuntimeHintsWithCustomClassLoader() {
		RuntimeHints runtimeHints = new RuntimeHints();
		VertexAiGeminiRuntimeHints vertexAiGeminiRuntimeHints = new VertexAiGeminiRuntimeHints();
		ClassLoader customClassLoader = Thread.currentThread().getContextClassLoader();

		// Should work with custom ClassLoader
		org.assertj.core.api.Assertions
			.assertThatCode(() -> vertexAiGeminiRuntimeHints.registerHints(runtimeHints, customClassLoader))
			.doesNotThrowAnyException();

		// Verify hints were still registered
		assertThat(runtimeHints.reflection().typeHints().spliterator().estimateSize()).isGreaterThan(0);
	}

	@Test
	void verifyProxyHintsAreEmpty() {
		RuntimeHints runtimeHints = new RuntimeHints();
		VertexAiGeminiRuntimeHints vertexAiGeminiRuntimeHints = new VertexAiGeminiRuntimeHints();
		vertexAiGeminiRuntimeHints.registerHints(runtimeHints, null);

		// This implementation should only register reflection hints, not proxy hints
		assertThat(runtimeHints.proxies().jdkProxyHints().spliterator().estimateSize()).isEqualTo(0);
	}

	@Test
	void verifySerializationHintsAreEmpty() {
		RuntimeHints runtimeHints = new RuntimeHints();
		VertexAiGeminiRuntimeHints vertexAiGeminiRuntimeHints = new VertexAiGeminiRuntimeHints();
		vertexAiGeminiRuntimeHints.registerHints(runtimeHints, null);

		// This implementation should only register reflection hints, not serialization
		// hints
		assertThat(runtimeHints.serialization().javaSerializationHints().spliterator().estimateSize()).isEqualTo(0);
	}

	@Test
	void verifyAllRegisteredTypesHaveValidPackage() {
		RuntimeHints runtimeHints = new RuntimeHints();
		VertexAiGeminiRuntimeHints vertexAiGeminiRuntimeHints = new VertexAiGeminiRuntimeHints();
		vertexAiGeminiRuntimeHints.registerHints(runtimeHints, null);

		runtimeHints.reflection().typeHints().forEach(typeHint -> {
			String typeName = typeHint.getType().getName();
			// All registered types should be from expected packages
			assertThat(typeName).satisfiesAnyOf(
					name -> assertThat(name).startsWith("org.springframework.ai.vertexai.gemini"),
					name -> assertThat(name).startsWith("java.lang"), // for basic types
					name -> assertThat(name).startsWith("java.util") // for collection
																		// types
			);
		});
	}

	@Test
	void verifyHintsRegistrationPerformance() {
		RuntimeHints runtimeHints = new RuntimeHints();
		VertexAiGeminiRuntimeHints vertexAiGeminiRuntimeHints = new VertexAiGeminiRuntimeHints();

		long startTime = System.currentTimeMillis();
		vertexAiGeminiRuntimeHints.registerHints(runtimeHints, null);
		long endTime = System.currentTimeMillis();

		// Registration should be fast (less than 1 second)
		assertThat(endTime - startTime).isLessThan(1000);

		// And should have registered some hints
		assertThat(runtimeHints.reflection().typeHints().spliterator().estimateSize()).isGreaterThan(0);
	}

	@Test
	void verifyHintsRegistrationWithEmptyRuntimeHints() {
		RuntimeHints emptyRuntimeHints = new RuntimeHints();
		VertexAiGeminiRuntimeHints vertexAiGeminiRuntimeHints = new VertexAiGeminiRuntimeHints();

		// Verify initial state
		assertThat(emptyRuntimeHints.reflection().typeHints().spliterator().estimateSize()).isEqualTo(0);

		// Register hints
		vertexAiGeminiRuntimeHints.registerHints(emptyRuntimeHints, null);

		// Verify hints were added
		assertThat(emptyRuntimeHints.reflection().typeHints().spliterator().estimateSize()).isGreaterThan(0);
	}

	@Test
	void verifyJsonAnnotatedClassesContainExpectedTypes() {
		Set<TypeReference> jsonAnnotatedClasses = findJsonAnnotatedClassesInPackage(
				"org.springframework.ai.vertexai.gemini");

		// Verify that our main configuration class is included
		boolean containsChatOptions = jsonAnnotatedClasses.stream()
			.anyMatch(typeRef -> typeRef.getName().contains("ChatOptions")
					|| typeRef.getName().contains("VertexAiGemini"));

		assertThat(containsChatOptions).isTrue();
	}

	@Test
	void verifyHintsConsistencyAcrossInstances() {
		RuntimeHints runtimeHints1 = new RuntimeHints();
		RuntimeHints runtimeHints2 = new RuntimeHints();

		VertexAiGeminiRuntimeHints hints1 = new VertexAiGeminiRuntimeHints();
		VertexAiGeminiRuntimeHints hints2 = new VertexAiGeminiRuntimeHints();

		hints1.registerHints(runtimeHints1, null);
		hints2.registerHints(runtimeHints2, null);

		// Different instances should register the same hints
		Set<TypeReference> types1 = new HashSet<>();
		Set<TypeReference> types2 = new HashSet<>();

		runtimeHints1.reflection().typeHints().forEach(hint -> types1.add(hint.getType()));
		runtimeHints2.reflection().typeHints().forEach(hint -> types2.add(hint.getType()));

		assertThat(types1).isEqualTo(types2);
	}

}
