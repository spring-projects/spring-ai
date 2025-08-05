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

}
