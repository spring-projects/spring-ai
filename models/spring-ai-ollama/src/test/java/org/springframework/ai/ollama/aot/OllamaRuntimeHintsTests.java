/*
 * Copyright 2023-present the original author or authors.
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

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springaicommunity.typesafe.JsonContent;
import org.springaicommunity.typesafe.question.Choice;
import org.springaicommunity.typesafe.question.Noul;
import org.springaicommunity.typesafe.question.NoulCriteria;
import org.springaicommunity.typesafe.question.Question;
import org.springaicommunity.typesafe.question.QuestionType;
import org.springaicommunity.typesafe.question.Score;
import org.springaicommunity.typesafe.question.SystemOneRequest;
import org.springaicommunity.typesafe.response.Answer;
import org.springaicommunity.typesafe.response.AnswerType;
import org.springaicommunity.typesafe.response.ChoiceAnswer;
import org.springaicommunity.typesafe.response.NoulAnswer;
import org.springaicommunity.typesafe.response.ScoreAnswer;
import org.springaicommunity.typesafe.response.SystemOneResponse;
import org.springaicommunity.typesafe.response.UnknownAnswer;
import org.springaicommunity.typesafe.response.Usage;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.ThinkOption;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;
import org.springframework.boot.test.context.FilteredClassLoader;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.ai.aot.AiRuntimeHints.findJsonAnnotatedClassesInPackage;
import static org.springframework.aot.hint.predicate.RuntimeHintsPredicates.reflection;

class OllamaRuntimeHintsTests {

	private static final String TYPESAFE_PACKAGE = "org.springaicommunity.typesafe.";

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
	}

	@Test
	void registerThinkOptionJacksonHandlers() {
		RuntimeHints runtimeHints = new RuntimeHints();
		OllamaRuntimeHints ollamaRuntimeHints = new OllamaRuntimeHints();
		ollamaRuntimeHints.registerHints(runtimeHints, null);

		assertThat(runtimeHints).matches(reflection().onType(ThinkOption.ThinkOptionDeserializer.class));
		assertThat(runtimeHints).matches(reflection().onType(ThinkOption.ThinkOptionSerializer.class));
		assertThatAllMemberCategoriesAreRegistered(runtimeHints, ThinkOption.ThinkOptionDeserializer.class);
		assertThatAllMemberCategoriesAreRegistered(runtimeHints, ThinkOption.ThinkOptionSerializer.class);
	}

	@Test
	void registerExactlyTheTypeSafeSdkTypesUsedByTheDecisionModel() throws Exception {
		RuntimeHints runtimeHints = new RuntimeHints();
		OllamaRuntimeHints ollamaRuntimeHints = new OllamaRuntimeHints();
		ollamaRuntimeHints.registerHints(runtimeHints, null);

		Set<String> registeredTypeSafeTypes = new HashSet<>();
		runtimeHints.reflection()
			.typeHints()
			.map(typeHint -> typeHint.getType().getName())
			.filter(name -> name.startsWith(TYPESAFE_PACKAGE))
			.forEach(registeredTypeSafeTypes::add);

		// The hints name the types as strings, so compare them with the types themselves
		Set<Class<?>> expectedTypes = expectedTypeSafeTypes();
		Assertions.assertThat(registeredTypeSafeTypes)
			.containsExactlyInAnyOrderElementsOf(expectedTypes.stream().map(Class::getName).toList());
		for (Class<?> type : expectedTypes) {
			assertThatAllMemberCategoriesAreRegistered(runtimeHints, type);
		}
	}

	@Test
	void expectedTypeSafeSdkTypesCoverEveryTypeTheyReach() throws Exception {
		Set<Class<?>> expectedTypes = expectedTypeSafeTypes();

		// A question or answer kind added to the SDK must be registered too
		Assertions.assertThat(expectedTypes)
			.contains(Question.class.getPermittedSubclasses())
			.contains(Answer.class.getPermittedSubclasses());

		// And so must a type the SDK records start to hold
		Set<Class<?>> reachedTypes = new HashSet<>();
		for (Class<?> type : expectedTypes) {
			if (type.isRecord()) {
				for (RecordComponent component : type.getRecordComponents()) {
					collectTypeSafeTypes(component.getGenericType(), reachedTypes);
				}
			}
		}
		Assertions.assertThat(expectedTypes).containsAll(reachedTypes);
	}

	@Test
	void skipsTypeSafeSdkTypesWhenTheSdkIsExcluded() {
		RuntimeHints runtimeHints = new RuntimeHints();
		OllamaRuntimeHints ollamaRuntimeHints = new OllamaRuntimeHints();
		var classLoaderWithoutSdk = new FilteredClassLoader("org.springaicommunity.typesafe");

		assertThatCode(() -> ollamaRuntimeHints.registerHints(runtimeHints, classLoaderWithoutSdk))
			.doesNotThrowAnyException();

		Set<TypeReference> registeredTypes = new HashSet<>();
		runtimeHints.reflection().typeHints().forEach(typeHint -> registeredTypes.add(typeHint.getType()));
		assertThat(registeredTypes.contains(TypeReference.of(SystemOneRequest.class))).isFalse();
		assertThat(registeredTypes.contains(TypeReference.of(OllamaApi.ChatRequest.class))).isTrue();
	}

	@Test
	void registerHintsWithNullClassLoader() {
		RuntimeHints runtimeHints = new RuntimeHints();
		OllamaRuntimeHints ollamaRuntimeHints = new OllamaRuntimeHints();

		// Should not throw exception with null ClassLoader
		assertThatCode(() -> ollamaRuntimeHints.registerHints(runtimeHints, null)).doesNotThrowAnyException();
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
		// The TypeSafe SDK types and its answer deserializer are registered on purpose
		int typeSafeTypes = OllamaRuntimeHints.TYPESAFE_JSON_TYPES.size();
		assertThat(registeredTypes.size()).isLessThanOrEqualTo(jsonAnnotatedClasses.size() + typeSafeTypes + 15);
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
			.filter(typeRef -> typeRef.getName().toLowerCase(Locale.ROOT).contains("embedding"))
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
		assertThatCode(() -> ollamaRuntimeHints.registerHints(runtimeHints, customClassLoader))
			.doesNotThrowAnyException();

		// Verify hints are still registered properly
		Set<TypeReference> registeredTypes = new HashSet<>();
		runtimeHints.reflection().typeHints().forEach(typeHint -> registeredTypes.add(typeHint.getType()));

		assertThat(registeredTypes.size()).isGreaterThan(0);
		assertThat(registeredTypes.contains(TypeReference.of(OllamaApi.ChatRequest.class))).isTrue();
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
			.anyMatch(tr -> tr.getName().contains("$") || tr.getName().toLowerCase(Locale.ROOT).contains("role")
					|| tr.getName().toLowerCase(Locale.ROOT).contains("type"));

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
			.filter(typeRef -> typeRef.getName().toLowerCase(Locale.ROOT).contains("tool"))
			.count();
		assertThat(toolClassCount).isGreaterThan(0);
	}

	/**
	 * The TypeSafe SDK types the decision model sends and receives as JSON, named by type
	 * so that the compiler checks them.
	 */
	private static Set<Class<?>> expectedTypeSafeTypes() throws NoSuchMethodException {
		// Package-private in the SDK, so reached through the annotation that uses it: the
		// answers parameter of the constructor Jackson creates the response with
		Class<?> answerDeserializer = SystemOneResponse.class
			.getDeclaredConstructor(String.class, Map.class, Usage.class)
			.getParameters()[1].getAnnotation(JsonDeserialize.class)
			.contentUsing();
		Assertions.assertThat(answerDeserializer).isNotEqualTo(ValueDeserializer.None.class);

		return Set.of(SystemOneRequest.class, Question.class, QuestionType.class, Noul.class, NoulCriteria.class,
				Choice.class, Score.class, JsonContent.class, SystemOneResponse.class, Answer.class, AnswerType.class,
				answerDeserializer, NoulAnswer.class, ChoiceAnswer.class, ScoreAnswer.class, UnknownAnswer.class,
				Usage.class);
	}

	private static void collectTypeSafeTypes(Type type, Set<Class<?>> collected) {
		if (type instanceof Class<?> clazz && clazz.getName().startsWith(TYPESAFE_PACKAGE)) {
			collected.add(clazz);
		}
		else if (type instanceof ParameterizedType parameterizedType) {
			collectTypeSafeTypes(parameterizedType.getRawType(), collected);
			for (Type argument : parameterizedType.getActualTypeArguments()) {
				collectTypeSafeTypes(argument, collected);
			}
		}
	}

	private static void assertThatAllMemberCategoriesAreRegistered(RuntimeHints runtimeHints, Class<?> type) {
		Set<MemberCategory> memberCategories = runtimeHints.reflection()
			.typeHints()
			.filter(typeHint -> typeHint.getType().equals(TypeReference.of(type)))
			.findFirst()
			.orElseThrow()
			.getMemberCategories();

		assertThat(memberCategories.containsAll(Set.of(MemberCategory.values()))).isTrue();
	}

}
