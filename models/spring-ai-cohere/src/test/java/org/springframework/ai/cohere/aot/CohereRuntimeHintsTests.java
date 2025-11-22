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

package org.springframework.ai.cohere.aot;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import org.springframework.ai.cohere.api.CohereApi;
import org.springframework.ai.cohere.chat.CohereChatOptions;
import org.springframework.ai.cohere.embedding.CohereEmbeddingOptions;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.springframework.ai.aot.AiRuntimeHints.findJsonAnnotatedClassesInPackage;

class CohereRuntimeHintsTests {

	@Test
	void registerHints() {
		RuntimeHints runtimeHints = new RuntimeHints();
		CohereRuntimeHints cohereRuntimeHints = new CohereRuntimeHints();
		cohereRuntimeHints.registerHints(runtimeHints, null);

		Set<TypeReference> jsonAnnotatedClasses = findJsonAnnotatedClassesInPackage("org.springframework.ai.cohere");

		Set<TypeReference> registeredTypes = new HashSet<>();
		runtimeHints.reflection().typeHints().forEach(typeHint -> registeredTypes.add(typeHint.getType()));

		for (TypeReference jsonAnnotatedClass : jsonAnnotatedClasses) {
			assertThat(registeredTypes.contains(jsonAnnotatedClass)).isTrue();
		}

		// Check a few more specific ones
		assertThat(registeredTypes.contains(TypeReference.of(CohereApi.ChatCompletion.class))).isTrue();
		assertThat(registeredTypes.contains(TypeReference.of(CohereApi.ChatCompletionChunk.class))).isTrue();
		assertThat(registeredTypes.contains(TypeReference.of(CohereApi.LogProbs.class))).isTrue();
		assertThat(registeredTypes.contains(TypeReference.of(CohereApi.ChatCompletionFinishReason.class))).isTrue();
		assertThat(registeredTypes.contains(TypeReference.of(CohereChatOptions.class))).isTrue();
		assertThat(registeredTypes.contains(TypeReference.of(CohereEmbeddingOptions.class))).isTrue();
	}

	@Test
	void registerHintsWithNullClassLoader() {
		RuntimeHints runtimeHints = new RuntimeHints();
		CohereRuntimeHints cohereRuntimeHints = new CohereRuntimeHints();

		// Should not throw exception with null classLoader
		cohereRuntimeHints.registerHints(runtimeHints, null);

		// Verify hints were registered
		assertThat(runtimeHints.reflection().typeHints().count()).isGreaterThan(0);
	}

	@Test
	void registerHintsWithValidClassLoader() {
		RuntimeHints runtimeHints = new RuntimeHints();
		CohereRuntimeHints cohereRuntimeHints = new CohereRuntimeHints();
		ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

		cohereRuntimeHints.registerHints(runtimeHints, classLoader);

		// Verify hints were registered
		assertThat(runtimeHints.reflection().typeHints().count()).isGreaterThan(0);
	}

	@Test
	void registerHintsIsIdempotent() {
		RuntimeHints runtimeHints = new RuntimeHints();
		CohereRuntimeHints cohereRuntimeHints = new CohereRuntimeHints();

		// Register hints twice
		cohereRuntimeHints.registerHints(runtimeHints, null);
		long firstCount = runtimeHints.reflection().typeHints().count();

		cohereRuntimeHints.registerHints(runtimeHints, null);
		long secondCount = runtimeHints.reflection().typeHints().count();

		// Should have same number of hints
		assertThat(firstCount).isEqualTo(secondCount);
	}

	@Test
	void verifyExpectedTypesAreRegistered() {
		RuntimeHints runtimeHints = new RuntimeHints();
		CohereRuntimeHints cohereRuntimeHints = new CohereRuntimeHints();
		cohereRuntimeHints.registerHints(runtimeHints, null);

		Set<TypeReference> registeredTypes = new HashSet<>();
		runtimeHints.reflection().typeHints().forEach(typeHint -> registeredTypes.add(typeHint.getType()));

		// Verify some expected types are registered (adjust class names as needed)
		assertThat(registeredTypes.stream().anyMatch(tr -> tr.getName().contains("Cohere"))).isTrue();
		assertThat(registeredTypes.stream().anyMatch(tr -> tr.getName().contains("ChatCompletion"))).isTrue();
	}

	@Test
	void verifyPackageScanningWorks() {
		Set<TypeReference> jsonAnnotatedClasses = findJsonAnnotatedClassesInPackage("org.springframework.ai.cohere");

		// Verify package scanning found classes
		assertThat(jsonAnnotatedClasses.size()).isGreaterThan(0);
	}

	@Test
	void verifyAllCriticalApiClassesAreRegistered() {
		RuntimeHints runtimeHints = new RuntimeHints();
		CohereRuntimeHints cohereRuntimeHints = new CohereRuntimeHints();
		cohereRuntimeHints.registerHints(runtimeHints, null);

		Set<TypeReference> registeredTypes = new HashSet<>();
		runtimeHints.reflection().typeHints().forEach(typeHint -> registeredTypes.add(typeHint.getType()));

		// Ensure critical API classes are registered for GraalVM native image reflection
		String[] criticalClasses = { "CohereApi$ChatCompletionRequest", "CohereApi$ChatCompletionMessage",
				"CohereApi$EmbeddingRequest", "CohereApi$EmbeddingModel", "CohereApi$Usage" };

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
		CohereRuntimeHints cohereRuntimeHints = new CohereRuntimeHints();
		cohereRuntimeHints.registerHints(runtimeHints, null);

		Set<TypeReference> registeredTypes = new HashSet<>();
		runtimeHints.reflection().typeHints().forEach(typeHint -> registeredTypes.add(typeHint.getType()));

		// Enums are critical for JSON deserialization in native images
		assertThat(registeredTypes.contains(TypeReference.of(CohereApi.ChatModel.class)))
			.as("ChatModel enum should be registered")
			.isTrue();

		assertThat(registeredTypes.contains(TypeReference.of(CohereApi.EmbeddingModel.class)))
			.as("EmbeddingModel enum should be registered")
			.isTrue();
	}

	@Test
	void verifyReflectionHintsIncludeConstructors() {
		RuntimeHints runtimeHints = new RuntimeHints();
		CohereRuntimeHints cohereRuntimeHints = new CohereRuntimeHints();
		cohereRuntimeHints.registerHints(runtimeHints, null);

		// Verify that reflection hints include constructor access
		boolean hasConstructorHints = runtimeHints.reflection()
			.typeHints()
			.anyMatch(typeHint -> typeHint.constructors().findAny().isPresent() || typeHint.getMemberCategories()
				.contains(org.springframework.aot.hint.MemberCategory.INVOKE_DECLARED_CONSTRUCTORS));

		assertThat(hasConstructorHints).as("Should register constructor hints for JSON deserialization").isTrue();
	}

	@Test
	void verifyNoExceptionThrownWithEmptyRuntimeHints() {
		RuntimeHints emptyRuntimeHints = new RuntimeHints();
		CohereRuntimeHints cohereRuntimeHints = new CohereRuntimeHints();

		// Should not throw any exception even with empty runtime hints
		assertThatCode(() -> cohereRuntimeHints.registerHints(emptyRuntimeHints, null)).doesNotThrowAnyException();

		assertThat(emptyRuntimeHints.reflection().typeHints().count()).isGreaterThan(0);
	}

	@Test
	void verifyProxyHintsAreNotRegistered() {
		RuntimeHints runtimeHints = new RuntimeHints();
		CohereRuntimeHints cohereRuntimeHints = new CohereRuntimeHints();
		cohereRuntimeHints.registerHints(runtimeHints, null);

		// MistralAi should only register reflection hints, not proxy hints
		assertThat(runtimeHints.proxies().jdkProxyHints().count()).isEqualTo(0);
	}

	@Test
	void verifySerializationHintsAreNotRegistered() {
		RuntimeHints runtimeHints = new RuntimeHints();
		CohereRuntimeHints cohereRuntimeHints = new CohereRuntimeHints();
		cohereRuntimeHints.registerHints(runtimeHints, null);

		// MistralAi should only register reflection hints, not serialization hints
		assertThat(runtimeHints.serialization().javaSerializationHints().count()).isEqualTo(0);
	}

	@Test
	void verifyResponseTypesAreRegistered() {
		RuntimeHints runtimeHints = new RuntimeHints();
		CohereRuntimeHints cohereRuntimeHints = new CohereRuntimeHints();
		cohereRuntimeHints.registerHints(runtimeHints, null);

		Set<TypeReference> registeredTypes = new HashSet<>();
		runtimeHints.reflection().typeHints().forEach(typeHint -> registeredTypes.add(typeHint.getType()));

		// Verify response wrapper types are registered
		assertThat(registeredTypes.stream().anyMatch(tr -> tr.getName().contains("EmbeddingResponse")))
			.as("EmbeddingResponse type should be registered")
			.isTrue();

		assertThat(registeredTypes.stream().anyMatch(tr -> tr.getName().contains("ChatCompletion")))
			.as("ChatCompletion response type should be registered")
			.isTrue();
	}

	@Test
	void verifyMultipleInstancesRegisterSameHints() {
		RuntimeHints runtimeHints1 = new RuntimeHints();
		RuntimeHints runtimeHints2 = new RuntimeHints();

		CohereRuntimeHints hints1 = new CohereRuntimeHints();
		CohereRuntimeHints hints2 = new CohereRuntimeHints();

		hints1.registerHints(runtimeHints1, null);
		hints2.registerHints(runtimeHints2, null);

		long count1 = runtimeHints1.reflection().typeHints().count();
		long count2 = runtimeHints2.reflection().typeHints().count();

		assertThat(count1).isEqualTo(count2);
	}

}
