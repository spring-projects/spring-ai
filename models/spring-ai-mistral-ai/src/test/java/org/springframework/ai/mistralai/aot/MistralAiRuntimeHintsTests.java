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

package org.springframework.ai.mistralai.aot;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import org.springframework.ai.mistralai.MistralAiChatOptions;
import org.springframework.ai.mistralai.MistralAiEmbeddingOptions;
import org.springframework.ai.mistralai.api.MistralAiApi;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.ai.aot.AiRuntimeHints.findJsonAnnotatedClassesInPackage;

class MistralAiRuntimeHintsTests {

	@Test
	void registerHints() {
		RuntimeHints runtimeHints = new RuntimeHints();
		MistralAiRuntimeHints mistralAiRuntimeHints = new MistralAiRuntimeHints();
		mistralAiRuntimeHints.registerHints(runtimeHints, null);

		Set<TypeReference> jsonAnnotatedClasses = findJsonAnnotatedClassesInPackage("org.springframework.ai.mistralai");

		Set<TypeReference> registeredTypes = new HashSet<>();
		runtimeHints.reflection().typeHints().forEach(typeHint -> registeredTypes.add(typeHint.getType()));

		for (TypeReference jsonAnnotatedClass : jsonAnnotatedClasses) {
			assertThat(registeredTypes.contains(jsonAnnotatedClass)).isTrue();
		}

		// Check a few more specific ones
		assertThat(registeredTypes.contains(TypeReference.of(MistralAiApi.ChatCompletion.class))).isTrue();
		assertThat(registeredTypes.contains(TypeReference.of(MistralAiApi.ChatCompletionChunk.class))).isTrue();
		assertThat(registeredTypes.contains(TypeReference.of(MistralAiApi.LogProbs.class))).isTrue();
		assertThat(registeredTypes.contains(TypeReference.of(MistralAiApi.ChatCompletionFinishReason.class))).isTrue();
		assertThat(registeredTypes.contains(TypeReference.of(MistralAiChatOptions.class))).isTrue();
		assertThat(registeredTypes.contains(TypeReference.of(MistralAiEmbeddingOptions.class))).isTrue();
	}

	@Test
	void registerHintsWithNullClassLoader() {
		RuntimeHints runtimeHints = new RuntimeHints();
		MistralAiRuntimeHints mistralAiRuntimeHints = new MistralAiRuntimeHints();

		// Should not throw exception with null classLoader
		mistralAiRuntimeHints.registerHints(runtimeHints, null);

		// Verify hints were registered
		assertThat(runtimeHints.reflection().typeHints().count()).isGreaterThan(0);
	}

	@Test
	void registerHintsWithValidClassLoader() {
		RuntimeHints runtimeHints = new RuntimeHints();
		MistralAiRuntimeHints mistralAiRuntimeHints = new MistralAiRuntimeHints();
		ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

		mistralAiRuntimeHints.registerHints(runtimeHints, classLoader);

		// Verify hints were registered
		assertThat(runtimeHints.reflection().typeHints().count()).isGreaterThan(0);
	}

	@Test
	void registerHintsIsIdempotent() {
		RuntimeHints runtimeHints = new RuntimeHints();
		MistralAiRuntimeHints mistralAiRuntimeHints = new MistralAiRuntimeHints();

		// Register hints twice
		mistralAiRuntimeHints.registerHints(runtimeHints, null);
		long firstCount = runtimeHints.reflection().typeHints().count();

		mistralAiRuntimeHints.registerHints(runtimeHints, null);
		long secondCount = runtimeHints.reflection().typeHints().count();

		// Should have same number of hints
		assertThat(firstCount).isEqualTo(secondCount);
	}

	@Test
	void verifyExpectedTypesAreRegistered() {
		RuntimeHints runtimeHints = new RuntimeHints();
		MistralAiRuntimeHints mistralAiRuntimeHints = new MistralAiRuntimeHints();
		mistralAiRuntimeHints.registerHints(runtimeHints, null);

		Set<TypeReference> registeredTypes = new HashSet<>();
		runtimeHints.reflection().typeHints().forEach(typeHint -> registeredTypes.add(typeHint.getType()));

		// Verify some expected types are registered (adjust class names as needed)
		assertThat(registeredTypes.stream().anyMatch(tr -> tr.getName().contains("MistralAi"))).isTrue();
		assertThat(registeredTypes.stream().anyMatch(tr -> tr.getName().contains("ChatCompletion"))).isTrue();
	}

	@Test
	void verifyPackageScanningWorks() {
		Set<TypeReference> jsonAnnotatedClasses = findJsonAnnotatedClassesInPackage("org.springframework.ai.mistralai");

		// Verify package scanning found classes
		assertThat(jsonAnnotatedClasses.size()).isGreaterThan(0);
	}

	@Test
	void verifyAllCriticalApiClassesAreRegistered() {
		RuntimeHints runtimeHints = new RuntimeHints();
		MistralAiRuntimeHints mistralAiRuntimeHints = new MistralAiRuntimeHints();
		mistralAiRuntimeHints.registerHints(runtimeHints, null);

		Set<TypeReference> registeredTypes = new HashSet<>();
		runtimeHints.reflection().typeHints().forEach(typeHint -> registeredTypes.add(typeHint.getType()));

		// Ensure critical API classes are registered for GraalVM native image reflection
		String[] criticalClasses = { "MistralAiApi$ChatCompletionRequest", "MistralAiApi$ChatCompletionMessage",
				"MistralAiApi$EmbeddingRequest", "MistralAiApi$EmbeddingList", "MistralAiApi$Usage" };

		for (String className : criticalClasses) {
			assertThat(registeredTypes.stream()
				.anyMatch(tr -> tr.getName().contains(className.replace("$", "."))
						|| tr.getName().contains(className.replace("$", "$"))))
				.as("Critical class %s should be registered", className)
				.isTrue();
		}
	}

	@Test
	void verifyEnumTypesAreRegistered() {
		RuntimeHints runtimeHints = new RuntimeHints();
		MistralAiRuntimeHints mistralAiRuntimeHints = new MistralAiRuntimeHints();
		mistralAiRuntimeHints.registerHints(runtimeHints, null);

		Set<TypeReference> registeredTypes = new HashSet<>();
		runtimeHints.reflection().typeHints().forEach(typeHint -> registeredTypes.add(typeHint.getType()));

		// Enums are critical for JSON deserialization in native images
		assertThat(registeredTypes.contains(TypeReference.of(MistralAiApi.ChatModel.class)))
			.as("ChatModel enum should be registered")
			.isTrue();

		assertThat(registeredTypes.contains(TypeReference.of(MistralAiApi.EmbeddingModel.class)))
			.as("EmbeddingModel enum should be registered")
			.isTrue();
	}

	@Test
	void verifyReflectionHintsIncludeConstructors() {
		RuntimeHints runtimeHints = new RuntimeHints();
		MistralAiRuntimeHints mistralAiRuntimeHints = new MistralAiRuntimeHints();
		mistralAiRuntimeHints.registerHints(runtimeHints, null);

		// Verify that reflection hints include constructor access
		boolean hasConstructorHints = runtimeHints.reflection()
			.typeHints()
			.anyMatch(typeHint -> typeHint.constructors().findAny().isPresent() || typeHint.getMemberCategories()
				.contains(org.springframework.aot.hint.MemberCategory.INVOKE_DECLARED_CONSTRUCTORS));

		assertThat(hasConstructorHints).as("Should register constructor hints for JSON deserialization").isTrue();
	}

}
