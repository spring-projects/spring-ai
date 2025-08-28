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

package org.springframework.ai.azure.openai.aot;

import java.util.HashSet;
import java.util.Set;

import com.azure.ai.openai.OpenAIAsyncClient;
import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.models.ChatChoice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.aot.AiRuntimeHints;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.aot.hint.predicate.RuntimeHintsPredicates.reflection;
import static org.springframework.aot.hint.predicate.RuntimeHintsPredicates.resource;

class AzureOpenAiRuntimeHintsTests {

	private RuntimeHints runtimeHints;

	private AzureOpenAiRuntimeHints azureOpenAiRuntimeHints;

	@BeforeEach
	void setUp() {
		this.runtimeHints = new RuntimeHints();
		this.azureOpenAiRuntimeHints = new AzureOpenAiRuntimeHints();
	}

	@Test
	void registerHints() {
		this.azureOpenAiRuntimeHints.registerHints(this.runtimeHints, null);

		Set<TypeReference> azureModelTypes = AiRuntimeHints.findClassesInPackage(ChatChoice.class.getPackageName(),
				(metadataReader, metadataReaderFactory) -> true);
		for (TypeReference modelType : azureModelTypes) {
			assertThat(this.runtimeHints).matches(reflection().onType(modelType));
		}
		assertThat(this.runtimeHints).matches(reflection().onType(OpenAIClient.class));
		assertThat(this.runtimeHints).matches(reflection().onType(OpenAIAsyncClient.class));

		assertThat(this.runtimeHints).matches(resource().forResource("/azure-ai-openai.properties"));
	}

	@Test
	void registerHintsWithNullClassLoader() {
		// Test that registering hints with null ClassLoader works correctly
		this.azureOpenAiRuntimeHints.registerHints(this.runtimeHints, null);

		assertThat(this.runtimeHints).matches(reflection().onType(OpenAIClient.class));
		assertThat(this.runtimeHints).matches(reflection().onType(OpenAIAsyncClient.class));
		assertThat(this.runtimeHints).matches(resource().forResource("/azure-ai-openai.properties"));
	}

	@Test
	void registerHintsWithCustomClassLoader() {
		// Test that registering hints with a custom ClassLoader works correctly
		ClassLoader customClassLoader = Thread.currentThread().getContextClassLoader();
		this.azureOpenAiRuntimeHints.registerHints(this.runtimeHints, customClassLoader);

		assertThat(this.runtimeHints).matches(reflection().onType(OpenAIClient.class));
		assertThat(this.runtimeHints).matches(reflection().onType(OpenAIAsyncClient.class));
		assertThat(this.runtimeHints).matches(resource().forResource("/azure-ai-openai.properties"));
	}

	@Test
	void allMemberCategoriesAreRegisteredForAzureTypes() {
		this.azureOpenAiRuntimeHints.registerHints(this.runtimeHints, null);

		Set<TypeReference> azureModelTypes = AiRuntimeHints.findClassesInPackage(ChatChoice.class.getPackageName(),
				(metadataReader, metadataReaderFactory) -> true);

		// Verify that all MemberCategory values are registered for Azure model types
		this.runtimeHints.reflection().typeHints().forEach(typeHint -> {
			if (azureModelTypes.contains(typeHint.getType())) {
				Set<MemberCategory> expectedCategories = Set.of(MemberCategory.values());
				Set<MemberCategory> actualCategories = typeHint.getMemberCategories();
				assertThat(actualCategories.containsAll(expectedCategories)).isTrue();
			}
		});
	}

	@Test
	void verifySpecificAzureOpenAiClasses() {
		this.azureOpenAiRuntimeHints.registerHints(this.runtimeHints, null);

		// Verify specific Azure OpenAI classes are registered
		assertThat(this.runtimeHints).matches(reflection().onType(OpenAIClient.class));
		assertThat(this.runtimeHints).matches(reflection().onType(OpenAIAsyncClient.class));
		assertThat(this.runtimeHints).matches(reflection().onType(ChatChoice.class));
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
		this.azureOpenAiRuntimeHints.registerHints(this.runtimeHints, null);
		int firstRegistrationCount = (int) this.runtimeHints.reflection().typeHints().count();

		this.azureOpenAiRuntimeHints.registerHints(this.runtimeHints, null);
		int secondRegistrationCount = (int) this.runtimeHints.reflection().typeHints().count();

		assertThat(firstRegistrationCount).isEqualTo(secondRegistrationCount);

		// Verify resource hint registration is also idempotent
		assertThat(this.runtimeHints).matches(resource().forResource("/azure-ai-openai.properties"));
	}

	@Test
	void verifyAzureModelTypesInPackageIsNotEmpty() {
		Set<TypeReference> azureModelTypes = AiRuntimeHints.findClassesInPackage(ChatChoice.class.getPackageName(),
				(metadataReader, metadataReaderFactory) -> true);
		assertThat(azureModelTypes.size()).isGreaterThan(0);
	}

	@Test
	void verifyResourceHintIsRegistered() {
		this.azureOpenAiRuntimeHints.registerHints(this.runtimeHints, null);

		// Verify the specific resource hint is registered
		assertThat(this.runtimeHints).matches(resource().forResource("/azure-ai-openai.properties"));
	}

	@Test
	void verifyAllRegisteredTypesHaveReflectionHints() {
		this.azureOpenAiRuntimeHints.registerHints(this.runtimeHints, null);

		// Ensure every registered type has proper reflection hints
		this.runtimeHints.reflection().typeHints().forEach(typeHint -> {
			assertThat(typeHint.getType()).isNotNull();
			assertThat(typeHint.getMemberCategories().size()).isGreaterThan(0);
		});
	}

	@Test
	void verifyClientTypesAreRegistered() {
		this.azureOpenAiRuntimeHints.registerHints(this.runtimeHints, null);

		// Verify both sync and async client types are properly registered
		assertThat(this.runtimeHints).matches(reflection().onType(OpenAIClient.class));
		assertThat(this.runtimeHints).matches(reflection().onType(OpenAIAsyncClient.class));
	}

}
