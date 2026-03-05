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

package org.springframework.ai.openai.aot;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.OpenAiAudioApi;
import org.springframework.ai.openai.api.OpenAiEmbeddingDeserializer;
import org.springframework.ai.openai.api.OpenAiImageApi;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.ai.aot.AiRuntimeHints.findJsonAnnotatedClassesInPackage;

class OpenAiRuntimeHintsTests {

	private RuntimeHints runtimeHints;

	private OpenAiRuntimeHints openAiRuntimeHints;

	@BeforeEach
	void setUp() {
		this.runtimeHints = new RuntimeHints();
		this.openAiRuntimeHints = new OpenAiRuntimeHints();
	}

	@Test
	void registerHints() {
		this.openAiRuntimeHints.registerHints(this.runtimeHints, null);

		Set<TypeReference> jsonAnnotatedClasses = findJsonAnnotatedClassesInPackage("org.springframework.ai.openai");

		Set<TypeReference> registeredTypes = new HashSet<>();
		this.runtimeHints.reflection().typeHints().forEach(typeHint -> registeredTypes.add(typeHint.getType()));

		for (TypeReference jsonAnnotatedClass : jsonAnnotatedClasses) {
			assertThat(registeredTypes.contains(jsonAnnotatedClass)).isTrue();
		}

		// Check a few more specific ones
		assertThat(registeredTypes.contains(TypeReference.of(OpenAiApi.class))).isTrue();
		assertThat(registeredTypes.contains(TypeReference.of(OpenAiAudioApi.class))).isTrue();
		assertThat(registeredTypes.contains(TypeReference.of(OpenAiAudioApi.TtsModel.class))).isTrue();
		assertThat(registeredTypes.contains(TypeReference.of(OpenAiAudioApi.WhisperModel.class))).isTrue();
		assertThat(registeredTypes.contains(TypeReference.of(OpenAiImageApi.class))).isTrue();
		assertThat(registeredTypes.contains(TypeReference.of(OpenAiApi.ChatCompletionFinishReason.class))).isTrue();
		assertThat(registeredTypes.contains(TypeReference.of(OpenAiApi.FunctionTool.class))).isTrue();
		assertThat(registeredTypes.contains(TypeReference.of(OpenAiApi.FunctionTool.Function.class))).isTrue();
		assertThat(registeredTypes.contains(TypeReference.of(OpenAiApi.OutputModality.class))).isTrue();
		assertThat(registeredTypes.contains(TypeReference.of(OpenAiChatOptions.class))).isTrue();
	}

	@Test
	void registerHintsWithNullClassLoader() {
		// Test that registering hints with null ClassLoader works correctly
		this.openAiRuntimeHints.registerHints(this.runtimeHints, null);

		Set<TypeReference> registeredTypes = new HashSet<>();
		this.runtimeHints.reflection().typeHints().forEach(typeHint -> registeredTypes.add(typeHint.getType()));

		assertThat(registeredTypes.size()).isGreaterThan(0);
	}

	@Test
	void registerHintsWithCustomClassLoader() {
		// Test that registering hints with a custom ClassLoader works correctly
		ClassLoader customClassLoader = Thread.currentThread().getContextClassLoader();
		this.openAiRuntimeHints.registerHints(this.runtimeHints, customClassLoader);

		Set<TypeReference> registeredTypes = new HashSet<>();
		this.runtimeHints.reflection().typeHints().forEach(typeHint -> registeredTypes.add(typeHint.getType()));

		assertThat(registeredTypes.size()).isGreaterThan(0);
	}

	@Test
	void allMemberCategoriesAreRegistered() {
		this.openAiRuntimeHints.registerHints(this.runtimeHints, null);

		Set<TypeReference> jsonAnnotatedClasses = findJsonAnnotatedClassesInPackage("org.springframework.ai.openai");

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
	void verifySpecificOpenAiApiClasses() {
		this.openAiRuntimeHints.registerHints(this.runtimeHints, null);

		Set<TypeReference> registeredTypes = new HashSet<>();
		this.runtimeHints.reflection().typeHints().forEach(typeHint -> registeredTypes.add(typeHint.getType()));

		// Verify specific OpenAI API classes are registered
		assertThat(registeredTypes.contains(TypeReference.of(OpenAiApi.class))).isTrue();
		assertThat(registeredTypes.contains(TypeReference.of(OpenAiAudioApi.class))).isTrue();
		assertThat(registeredTypes.contains(TypeReference.of(OpenAiImageApi.class))).isTrue();
		assertThat(registeredTypes.contains(TypeReference.of(OpenAiChatOptions.class))).isTrue();
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
		this.openAiRuntimeHints.registerHints(this.runtimeHints, null);
		int firstRegistrationCount = (int) this.runtimeHints.reflection().typeHints().count();

		this.openAiRuntimeHints.registerHints(this.runtimeHints, null);
		int secondRegistrationCount = (int) this.runtimeHints.reflection().typeHints().count();

		assertThat(firstRegistrationCount).isEqualTo(secondRegistrationCount);
	}

	@Test
	void verifyJsonAnnotatedClassesInPackageIsNotEmpty() {
		Set<TypeReference> jsonAnnotatedClasses = findJsonAnnotatedClassesInPackage("org.springframework.ai.openai");
		assertThat(jsonAnnotatedClasses.size()).isGreaterThan(0);
	}

	@Test
	void verifyAllRegisteredTypesHaveReflectionHints() {
		this.openAiRuntimeHints.registerHints(this.runtimeHints, null);

		// Ensure every registered type has proper reflection hints
		this.runtimeHints.reflection().typeHints().forEach(typeHint -> {
			assertThat(typeHint.getType()).isNotNull();
			assertThat(typeHint.getMemberCategories().size()).isGreaterThan(0);
		});
	}

	@Test
	void verifyEnumTypesAreRegistered() {
		this.openAiRuntimeHints.registerHints(this.runtimeHints, null);

		Set<TypeReference> registeredTypes = new HashSet<>();
		this.runtimeHints.reflection().typeHints().forEach(typeHint -> registeredTypes.add(typeHint.getType()));

		// Verify enum types are properly registered
		assertThat(registeredTypes.contains(TypeReference.of(OpenAiApi.ChatCompletionFinishReason.class))).isTrue();
		assertThat(registeredTypes.contains(TypeReference.of(OpenAiApi.OutputModality.class))).isTrue();
		assertThat(registeredTypes.contains(TypeReference.of(OpenAiAudioApi.TtsModel.class))).isTrue();
		assertThat(registeredTypes.contains(TypeReference.of(OpenAiAudioApi.WhisperModel.class))).isTrue();
	}

	@Test
	void verifyNestedClassesAreRegistered() {
		this.openAiRuntimeHints.registerHints(this.runtimeHints, null);

		Set<TypeReference> registeredTypes = new HashSet<>();
		this.runtimeHints.reflection().typeHints().forEach(typeHint -> registeredTypes.add(typeHint.getType()));

		// Verify nested classes are properly registered
		assertThat(registeredTypes.contains(TypeReference.of(OpenAiApi.FunctionTool.class))).isTrue();
		assertThat(registeredTypes.contains(TypeReference.of(OpenAiApi.FunctionTool.Function.class))).isTrue();
	}

	@Test
	void verifyPackageSpecificity() {
		Set<TypeReference> jsonAnnotatedClasses = findJsonAnnotatedClassesInPackage("org.springframework.ai.openai");

		// All found classes should be from the openai package specifically
		for (TypeReference classRef : jsonAnnotatedClasses) {
			assertThat(classRef.getName()).startsWith("org.springframework.ai.openai");
		}

		// Should not include classes from other AI packages
		for (TypeReference classRef : jsonAnnotatedClasses) {
			assertThat(classRef.getName()).doesNotContain("anthropic");
			assertThat(classRef.getName()).doesNotContain("vertexai");
			assertThat(classRef.getName()).doesNotContain("ollama");
		}
	}

	@Test
	void verifyConsistencyAcrossInstances() {
		RuntimeHints hints1 = new RuntimeHints();
		RuntimeHints hints2 = new RuntimeHints();

		OpenAiRuntimeHints openaiHints1 = new OpenAiRuntimeHints();
		OpenAiRuntimeHints openaiHints2 = new OpenAiRuntimeHints();

		openaiHints1.registerHints(hints1, null);
		openaiHints2.registerHints(hints2, null);

		// Different instances should register the same hints
		Set<TypeReference> types1 = new HashSet<>();
		Set<TypeReference> types2 = new HashSet<>();

		hints1.reflection().typeHints().forEach(hint -> types1.add(hint.getType()));
		hints2.reflection().typeHints().forEach(hint -> types2.add(hint.getType()));

		assertThat(types1).isEqualTo(types2);
	}

	@Test
	void verifySpecificApiClassDetails() {
		this.openAiRuntimeHints.registerHints(this.runtimeHints, null);

		Set<TypeReference> registeredTypes = new HashSet<>();
		this.runtimeHints.reflection().typeHints().forEach(typeHint -> registeredTypes.add(typeHint.getType()));

		// Verify critical OpenAI API classes are registered
		assertThat(registeredTypes.contains(TypeReference.of(OpenAiApi.class))).isTrue();
		assertThat(registeredTypes.contains(TypeReference.of(OpenAiAudioApi.class))).isTrue();
		assertThat(registeredTypes.contains(TypeReference.of(OpenAiImageApi.class))).isTrue();

		// Verify important nested/inner classes
		boolean containsChatCompletion = registeredTypes.stream()
			.anyMatch(typeRef -> typeRef.getName().contains("ChatCompletion"));
		assertThat(containsChatCompletion).isTrue();

		boolean containsFunctionTool = registeredTypes.stream()
			.anyMatch(typeRef -> typeRef.getName().contains("FunctionTool"));
		assertThat(containsFunctionTool).isTrue();
	}

	@Test
	void verifyClassLoaderIndependence() {
		RuntimeHints hintsWithNull = new RuntimeHints();
		RuntimeHints hintsWithClassLoader = new RuntimeHints();

		ClassLoader customClassLoader = Thread.currentThread().getContextClassLoader();

		this.openAiRuntimeHints.registerHints(hintsWithNull, null);
		this.openAiRuntimeHints.registerHints(hintsWithClassLoader, customClassLoader);

		// Both should register the same types regardless of ClassLoader
		Set<TypeReference> typesWithNull = new HashSet<>();
		Set<TypeReference> typesWithClassLoader = new HashSet<>();

		hintsWithNull.reflection().typeHints().forEach(hint -> typesWithNull.add(hint.getType()));
		hintsWithClassLoader.reflection().typeHints().forEach(hint -> typesWithClassLoader.add(hint.getType()));

		assertThat(typesWithNull).isEqualTo(typesWithClassLoader);
	}

	@Test
	void verifyAllApiModulesAreIncluded() {
		this.openAiRuntimeHints.registerHints(this.runtimeHints, null);

		Set<TypeReference> registeredTypes = new HashSet<>();
		this.runtimeHints.reflection().typeHints().forEach(typeHint -> registeredTypes.add(typeHint.getType()));

		// Verify all main OpenAI API modules are represented
		boolean hasMainApi = registeredTypes.stream().anyMatch(typeRef -> typeRef.getName().contains("OpenAiApi"));
		boolean hasAudioApi = registeredTypes.stream()
			.anyMatch(typeRef -> typeRef.getName().contains("OpenAiAudioApi"));
		boolean hasImageApi = registeredTypes.stream()
			.anyMatch(typeRef -> typeRef.getName().contains("OpenAiImageApi"));
		boolean hasChatOptions = registeredTypes.stream()
			.anyMatch(typeRef -> typeRef.getName().contains("OpenAiChatOptions"));

		assertThat(hasMainApi).isTrue();
		assertThat(hasAudioApi).isTrue();
		assertThat(hasImageApi).isTrue();
		assertThat(hasChatOptions).isTrue();
	}

	@Test
	void verifyJsonAnnotatedClassesContainCriticalTypes() {
		Set<TypeReference> jsonAnnotatedClasses = findJsonAnnotatedClassesInPackage("org.springframework.ai.openai");

		// Verify that critical OpenAI types are found
		boolean containsApiClass = jsonAnnotatedClasses.stream()
			.anyMatch(typeRef -> typeRef.getName().contains("OpenAiApi") || typeRef.getName().contains("ChatCompletion")
					|| typeRef.getName().contains("OpenAiChatOptions"));

		assertThat(containsApiClass).isTrue();

		// Verify audio and image API classes are found
		boolean containsAudioApi = jsonAnnotatedClasses.stream()
			.anyMatch(typeRef -> typeRef.getName().contains("AudioApi"));
		boolean containsImageApi = jsonAnnotatedClasses.stream()
			.anyMatch(typeRef -> typeRef.getName().contains("ImageApi"));

		assertThat(containsAudioApi).isTrue();
		assertThat(containsImageApi).isTrue();
	}

	@Test
	void verifyExplicitlyRegisteredEmbeddingClasses() {
		this.openAiRuntimeHints.registerHints(this.runtimeHints, null);

		Set<TypeReference> registeredTypes = new HashSet<>();
		this.runtimeHints.reflection().typeHints().forEach(typeHint -> registeredTypes.add(typeHint.getType()));

		// Verify the three classes explicitly registered in OpenAiRuntimeHints
		assertThat(registeredTypes.contains(TypeReference.of(OpenAiApi.Embedding.class))).isTrue();
		assertThat(registeredTypes.contains(TypeReference.of(OpenAiApi.EmbeddingList.class))).isTrue();
		assertThat(registeredTypes.contains(TypeReference.of(OpenAiEmbeddingDeserializer.class))).isTrue();
	}

}
