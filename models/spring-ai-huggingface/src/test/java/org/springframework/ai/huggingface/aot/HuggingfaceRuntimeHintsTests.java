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

package org.springframework.ai.huggingface.aot;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import org.springframework.ai.huggingface.HuggingfaceChatOptions;
import org.springframework.ai.huggingface.HuggingfaceEmbeddingOptions;
import org.springframework.ai.huggingface.api.HuggingfaceApi;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.ai.aot.AiRuntimeHints.findJsonAnnotatedClassesInPackage;

/**
 * Unit tests for {@link HuggingfaceRuntimeHints}.
 *
 * @author Myeongdeok Kang
 */
class HuggingfaceRuntimeHintsTests {

	@Test
	void registerHints() {
		RuntimeHints runtimeHints = new RuntimeHints();
		HuggingfaceRuntimeHints huggingfaceRuntimeHints = new HuggingfaceRuntimeHints();
		huggingfaceRuntimeHints.registerHints(runtimeHints, null);

		Set<TypeReference> jsonAnnotatedClasses = findJsonAnnotatedClassesInPackage(
				"org.springframework.ai.huggingface");

		Set<TypeReference> registeredTypes = new HashSet<>();
		runtimeHints.reflection().typeHints().forEach(typeHint -> registeredTypes.add(typeHint.getType()));

		for (TypeReference jsonAnnotatedClass : jsonAnnotatedClasses) {
			assertThat(registeredTypes.contains(jsonAnnotatedClass)).isTrue();
		}

		// Check specific HuggingFace API classes
		assertThat(registeredTypes.contains(TypeReference.of(HuggingfaceApi.ChatRequest.class))).isTrue();
		assertThat(registeredTypes.contains(TypeReference.of(HuggingfaceApi.Message.class))).isTrue();
		assertThat(registeredTypes.contains(TypeReference.of(HuggingfaceApi.ChatResponse.class))).isTrue();
		assertThat(registeredTypes.contains(TypeReference.of(HuggingfaceApi.FunctionTool.class))).isTrue();
		assertThat(registeredTypes.contains(TypeReference.of(HuggingfaceChatOptions.class))).isTrue();
		assertThat(registeredTypes.contains(TypeReference.of(HuggingfaceEmbeddingOptions.class))).isTrue();
	}

	@Test
	void registerHintsWithNullClassLoader() {
		RuntimeHints runtimeHints = new RuntimeHints();
		HuggingfaceRuntimeHints huggingfaceRuntimeHints = new HuggingfaceRuntimeHints();

		// Should not throw exception with null ClassLoader
		org.assertj.core.api.Assertions.assertThatCode(() -> huggingfaceRuntimeHints.registerHints(runtimeHints, null))
			.doesNotThrowAnyException();
	}

	@Test
	void ensureReflectionHintsAreRegistered() {
		RuntimeHints runtimeHints = new RuntimeHints();
		HuggingfaceRuntimeHints huggingfaceRuntimeHints = new HuggingfaceRuntimeHints();
		huggingfaceRuntimeHints.registerHints(runtimeHints, null);

		// Ensure reflection hints are properly registered
		assertThat(runtimeHints.reflection().typeHints().spliterator().estimateSize()).isGreaterThan(0);
	}

	@Test
	void verifyMultipleRegistrationCallsAreIdempotent() {
		RuntimeHints runtimeHints = new RuntimeHints();
		HuggingfaceRuntimeHints huggingfaceRuntimeHints = new HuggingfaceRuntimeHints();

		// Register hints multiple times
		huggingfaceRuntimeHints.registerHints(runtimeHints, null);
		long firstCount = runtimeHints.reflection().typeHints().spliterator().estimateSize();

		huggingfaceRuntimeHints.registerHints(runtimeHints, null);
		long secondCount = runtimeHints.reflection().typeHints().spliterator().estimateSize();

		// Should not register duplicate hints
		assertThat(firstCount).isEqualTo(secondCount);
	}

	@Test
	void verifyMainApiClassesRegistered() {
		RuntimeHints runtimeHints = new RuntimeHints();
		HuggingfaceRuntimeHints huggingfaceRuntimeHints = new HuggingfaceRuntimeHints();
		huggingfaceRuntimeHints.registerHints(runtimeHints, null);

		Set<TypeReference> registeredTypes = new HashSet<>();
		runtimeHints.reflection().typeHints().forEach(typeHint -> registeredTypes.add(typeHint.getType()));

		// Verify that the main classes we know exist are registered
		assertThat(registeredTypes.contains(TypeReference.of(HuggingfaceApi.ChatRequest.class))).isTrue();
		assertThat(registeredTypes.contains(TypeReference.of(HuggingfaceApi.Message.class))).isTrue();
		assertThat(registeredTypes.contains(TypeReference.of(HuggingfaceChatOptions.class))).isTrue();
	}

	@Test
	void verifyJsonAnnotatedClassesFromCorrectPackage() {
		Set<TypeReference> jsonAnnotatedClasses = findJsonAnnotatedClassesInPackage(
				"org.springframework.ai.huggingface");

		// Ensure we found some JSON annotated classes in the expected package
		assertThat(jsonAnnotatedClasses.spliterator().estimateSize()).isGreaterThan(0);

		// Verify all found classes are from the expected package
		for (TypeReference classRef : jsonAnnotatedClasses) {
			assertThat(classRef.getName()).startsWith("org.springframework.ai.huggingface");
		}
	}

	@Test
	void verifyNoUnnecessaryHintsRegistered() {
		RuntimeHints runtimeHints = new RuntimeHints();
		HuggingfaceRuntimeHints huggingfaceRuntimeHints = new HuggingfaceRuntimeHints();
		huggingfaceRuntimeHints.registerHints(runtimeHints, null);

		Set<TypeReference> jsonAnnotatedClasses = findJsonAnnotatedClassesInPackage(
				"org.springframework.ai.huggingface");

		Set<TypeReference> registeredTypes = new HashSet<>();
		runtimeHints.reflection().typeHints().forEach(typeHint -> registeredTypes.add(typeHint.getType()));

		// Ensure we don't register significantly more types than needed
		// Allow for some additional utility types but prevent hint bloat
		assertThat(registeredTypes.size()).isLessThanOrEqualTo(jsonAnnotatedClasses.size() + 15);
	}

	@Test
	void verifyNestedClassHintsAreRegistered() {
		RuntimeHints runtimeHints = new RuntimeHints();
		HuggingfaceRuntimeHints huggingfaceRuntimeHints = new HuggingfaceRuntimeHints();
		huggingfaceRuntimeHints.registerHints(runtimeHints, null);

		Set<TypeReference> registeredTypes = new HashSet<>();
		runtimeHints.reflection().typeHints().forEach(typeHint -> registeredTypes.add(typeHint.getType()));

		// Verify nested classes that we know exist
		assertThat(registeredTypes.contains(TypeReference.of(HuggingfaceApi.FunctionTool.Function.class))).isTrue();

		// Count nested classes to ensure comprehensive registration
		long nestedClassCount = registeredTypes.stream().filter(typeRef -> typeRef.getName().contains("$")).count();
		assertThat(nestedClassCount).isGreaterThan(0);
	}

	@Test
	void verifyEmbeddingRelatedClassesAreRegistered() {
		RuntimeHints runtimeHints = new RuntimeHints();
		HuggingfaceRuntimeHints huggingfaceRuntimeHints = new HuggingfaceRuntimeHints();
		huggingfaceRuntimeHints.registerHints(runtimeHints, null);

		Set<TypeReference> registeredTypes = new HashSet<>();
		runtimeHints.reflection().typeHints().forEach(typeHint -> registeredTypes.add(typeHint.getType()));

		// Verify embedding-related classes are registered for reflection
		assertThat(registeredTypes.contains(TypeReference.of(HuggingfaceApi.EmbeddingsRequest.class))).isTrue();
		assertThat(registeredTypes.contains(TypeReference.of(HuggingfaceApi.EmbeddingsResponse.class))).isTrue();

		// Count classes related to embedding functionality
		long embeddingClassCount = registeredTypes.stream()
			.filter(typeRef -> typeRef.getName().toLowerCase().contains("embedding"))
			.count();
		assertThat(embeddingClassCount).isGreaterThan(0);
	}

	@Test
	void verifyHintsRegistrationWithCustomClassLoader() {
		RuntimeHints runtimeHints = new RuntimeHints();
		HuggingfaceRuntimeHints huggingfaceRuntimeHints = new HuggingfaceRuntimeHints();

		// Create a custom class loader
		ClassLoader customClassLoader = Thread.currentThread().getContextClassLoader();

		// Should work with custom class loader
		org.assertj.core.api.Assertions
			.assertThatCode(() -> huggingfaceRuntimeHints.registerHints(runtimeHints, customClassLoader))
			.doesNotThrowAnyException();

		// Verify hints are still registered properly
		Set<TypeReference> registeredTypes = new HashSet<>();
		runtimeHints.reflection().typeHints().forEach(typeHint -> registeredTypes.add(typeHint.getType()));

		assertThat(registeredTypes.size()).isGreaterThan(0);
		assertThat(registeredTypes.contains(TypeReference.of(HuggingfaceApi.ChatRequest.class))).isTrue();
		assertThat(registeredTypes.contains(TypeReference.of(HuggingfaceChatOptions.class))).isTrue();
	}

	@Test
	void verifyNoProxyHintsAreRegistered() {
		RuntimeHints runtimeHints = new RuntimeHints();
		HuggingfaceRuntimeHints huggingfaceRuntimeHints = new HuggingfaceRuntimeHints();
		huggingfaceRuntimeHints.registerHints(runtimeHints, null);

		// HuggingFace should only register reflection hints, not proxy hints
		assertThat(runtimeHints.proxies().jdkProxyHints().count()).isEqualTo(0);
	}

	@Test
	void verifyNoSerializationHintsAreRegistered() {
		RuntimeHints runtimeHints = new RuntimeHints();
		HuggingfaceRuntimeHints huggingfaceRuntimeHints = new HuggingfaceRuntimeHints();
		huggingfaceRuntimeHints.registerHints(runtimeHints, null);

		// HuggingFace should only register reflection hints, not serialization hints
		assertThat(runtimeHints.serialization().javaSerializationHints().count()).isEqualTo(0);
	}

	@Test
	void verifyConstructorHintsAreRegistered() {
		RuntimeHints runtimeHints = new RuntimeHints();
		HuggingfaceRuntimeHints huggingfaceRuntimeHints = new HuggingfaceRuntimeHints();
		huggingfaceRuntimeHints.registerHints(runtimeHints, null);

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
		HuggingfaceRuntimeHints huggingfaceRuntimeHints = new HuggingfaceRuntimeHints();
		huggingfaceRuntimeHints.registerHints(runtimeHints, null);

		Set<TypeReference> registeredTypes = new HashSet<>();
		runtimeHints.reflection().typeHints().forEach(typeHint -> registeredTypes.add(typeHint.getType()));

		// Verify enum types are registered (critical for JSON deserialization)
		assertThat(registeredTypes.contains(TypeReference.of(HuggingfaceApi.FunctionTool.Type.class))).isTrue();

		boolean hasEnumTypes = registeredTypes.stream()
			.anyMatch(tr -> tr.getName().contains("$") || tr.getName().toLowerCase().contains("type"));

		assertThat(hasEnumTypes).as("Enum types should be registered for native image compatibility").isTrue();
	}

	@Test
	void verifyResponseTypesAreRegistered() {
		RuntimeHints runtimeHints = new RuntimeHints();
		HuggingfaceRuntimeHints huggingfaceRuntimeHints = new HuggingfaceRuntimeHints();
		huggingfaceRuntimeHints.registerHints(runtimeHints, null);

		Set<TypeReference> registeredTypes = new HashSet<>();
		runtimeHints.reflection().typeHints().forEach(typeHint -> registeredTypes.add(typeHint.getType()));

		// Verify response wrapper types are registered
		assertThat(registeredTypes.stream().anyMatch(tr -> tr.getName().contains("Response")))
			.as("Response types should be registered")
			.isTrue();

		assertThat(registeredTypes.contains(TypeReference.of(HuggingfaceApi.ChatResponse.class)))
			.as("ChatResponse type should be registered")
			.isTrue();
	}

	@Test
	void verifyToolRelatedClassesAreRegistered() {
		RuntimeHints runtimeHints = new RuntimeHints();
		HuggingfaceRuntimeHints huggingfaceRuntimeHints = new HuggingfaceRuntimeHints();
		huggingfaceRuntimeHints.registerHints(runtimeHints, null);

		Set<TypeReference> registeredTypes = new HashSet<>();
		runtimeHints.reflection().typeHints().forEach(typeHint -> registeredTypes.add(typeHint.getType()));

		// Verify tool-related classes are registered
		assertThat(registeredTypes.contains(TypeReference.of(HuggingfaceApi.FunctionTool.class))).isTrue();
		assertThat(registeredTypes.contains(TypeReference.of(HuggingfaceApi.ToolCall.class))).isTrue();

		// Count tool-related classes
		long toolClassCount = registeredTypes.stream()
			.filter(typeRef -> typeRef.getName().toLowerCase().contains("tool"))
			.count();
		assertThat(toolClassCount).isGreaterThan(0);
	}

}
