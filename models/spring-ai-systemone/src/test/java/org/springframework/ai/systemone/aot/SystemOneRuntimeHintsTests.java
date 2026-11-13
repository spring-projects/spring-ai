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

package org.springframework.ai.systemone.aot;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

import org.springframework.ai.systemone.api.JsonContent;
import org.springframework.ai.systemone.api.question.Choice;
import org.springframework.ai.systemone.api.question.Noul;
import org.springframework.ai.systemone.api.question.NoulCriteria;
import org.springframework.ai.systemone.api.question.Question;
import org.springframework.ai.systemone.api.question.QuestionType;
import org.springframework.ai.systemone.api.question.Score;
import org.springframework.ai.systemone.api.question.SystemOneRequest;
import org.springframework.ai.systemone.api.response.Answer;
import org.springframework.ai.systemone.api.response.AnswerType;
import org.springframework.ai.systemone.api.response.ChoiceAnswer;
import org.springframework.ai.systemone.api.response.ListModelsResponse;
import org.springframework.ai.systemone.api.response.ModelMetadata;
import org.springframework.ai.systemone.api.response.NoulAnswer;
import org.springframework.ai.systemone.api.response.ScoreAnswer;
import org.springframework.ai.systemone.api.response.SystemOneResponse;
import org.springframework.ai.systemone.api.response.UnknownAnswer;
import org.springframework.ai.systemone.api.response.Usage;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.core.io.support.SpringFactoriesLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Tests for {@link SystemOneRuntimeHints}.
 *
 * @author Thomas Vitale
 * @author Christian Tzolov
 */
class SystemOneRuntimeHintsTests {

	private static final String SYSTEM_ONE_PACKAGE = "org.springframework.ai.systemone.api.";

	private final RuntimeHints hints = new RuntimeHints();

	private final SystemOneRuntimeHints registrar = new SystemOneRuntimeHints();

	@Test
	void aotFactoriesListRegistrar() {
		boolean listed = SpringFactoriesLoader.forResourceLocation("META-INF/spring/aot.factories")
			.load(RuntimeHintsRegistrar.class)
			.stream()
			.anyMatch(SystemOneRuntimeHints.class::isInstance);

		assertThat(listed).isTrue();
	}

	@Test
	void registersWithoutClassLoader() {
		assertThatNoException().isThrownBy(() -> this.registrar.registerHints(this.hints, null));
	}

	@Test
	void registerExactlyTheSystemOneApiTypesUsedByTheDecisionModel() throws Exception {
		this.registrar.registerHints(this.hints, getClass().getClassLoader());

		Set<String> registeredTypes = new HashSet<>();
		this.hints.reflection().typeHints().map(typeHint -> typeHint.getType().getName()).forEach(registeredTypes::add);

		Set<Class<?>> expectedTypes = expectedSystemOneTypes();
		assertThat(registeredTypes)
			.containsExactlyInAnyOrderElementsOf(expectedTypes.stream().map(Class::getName).toList());
		for (Class<?> type : expectedTypes) {
			assertThatAllMemberCategoriesAreRegistered(type);
		}
	}

	@Test
	void expectedSystemOneApiTypesCoverEveryTypeTheyReach() throws Exception {
		Set<Class<?>> expectedTypes = expectedSystemOneTypes();

		// A question or answer kind added to the client must be registered too
		assertThat(expectedTypes).contains(Question.class.getPermittedSubclasses())
			.contains(Answer.class.getPermittedSubclasses());

		// And so must a type the API records start to hold
		Set<Class<?>> reachedTypes = new HashSet<>();
		for (Class<?> type : expectedTypes) {
			if (type.isRecord()) {
				for (RecordComponent component : type.getRecordComponents()) {
					collectSystemOneTypes(component.getGenericType(), reachedTypes);
				}
			}
		}
		assertThat(expectedTypes).containsAll(reachedTypes);
	}

	/**
	 * The System One API types the client sends and receives as JSON, including the
	 * package-private answer deserializer the registrar can only name. Listed here on
	 * their own, not taken from the registrar, so that a type dropped from it is caught.
	 */
	private static Set<Class<?>> expectedSystemOneTypes() throws NoSuchMethodException {
		// Reached through the annotation that uses it: the answers parameter of the
		// constructor Jackson creates the response with
		Class<?> answerDeserializer = SystemOneResponse.class
			.getDeclaredConstructor(String.class, Map.class, Usage.class)
			.getParameters()[1].getAnnotation(JsonDeserialize.class)
			.contentUsing();
		assertThat(answerDeserializer).isNotEqualTo(ValueDeserializer.None.class);

		return Set.of(SystemOneRequest.class, Question.class, QuestionType.class, Noul.class, NoulCriteria.class,
				Choice.class, Score.class, JsonContent.class, SystemOneResponse.class, Answer.class, AnswerType.class,
				answerDeserializer, NoulAnswer.class, ChoiceAnswer.class, ScoreAnswer.class, UnknownAnswer.class,
				Usage.class, ListModelsResponse.class, ModelMetadata.class);
	}

	private static void collectSystemOneTypes(Type type, Set<Class<?>> collected) {
		if (type instanceof Class<?> clazz && clazz.getName().startsWith(SYSTEM_ONE_PACKAGE)) {
			collected.add(clazz);
		}
		else if (type instanceof ParameterizedType parameterizedType) {
			collectSystemOneTypes(parameterizedType.getRawType(), collected);
			for (Type argument : parameterizedType.getActualTypeArguments()) {
				collectSystemOneTypes(argument, collected);
			}
		}
	}

	private void assertThatAllMemberCategoriesAreRegistered(Class<?> type) {
		Set<MemberCategory> memberCategories = this.hints.reflection()
			.typeHints()
			.filter(typeHint -> typeHint.getType().equals(TypeReference.of(type)))
			.findFirst()
			.orElseThrow()
			.getMemberCategories();

		assertThat(memberCategories).containsAll(Set.of(MemberCategory.values()));
	}

}
