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

import java.util.List;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.ollama.api.ThinkOption;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.util.ClassUtils;

import static org.springframework.ai.aot.AiRuntimeHints.findJsonAnnotatedClassesInPackage;

/**
 * The OllamaRuntimeHints class is responsible for registering runtime hints for Ollama AI
 * API classes.
 *
 * @author Josh Long
 * @author Christian Tzolov
 * @author Mark Pollack
 */
public class OllamaRuntimeHints implements RuntimeHintsRegistrar {

	private static final String TYPESAFE_PACKAGE = "org.springaicommunity.typesafe.";

	/**
	 * The TypeSafe SDK types the decision model sends and receives as JSON. The SDK ships
	 * no native image metadata, and scanning its package misses types such as
	 * {@code JsonContent} that use {@code @JsonValue} and {@code @JsonCreator} only. They
	 * are referenced by name, since applications that don't use the decision model can
	 * exclude the SDK.
	 */
	static final List<String> TYPESAFE_JSON_TYPES = List.of(TYPESAFE_PACKAGE + "question.SystemOneRequest",
			TYPESAFE_PACKAGE + "question.Question", TYPESAFE_PACKAGE + "question.QuestionType",
			TYPESAFE_PACKAGE + "question.Noul", TYPESAFE_PACKAGE + "question.NoulCriteria",
			TYPESAFE_PACKAGE + "question.Choice", TYPESAFE_PACKAGE + "question.Score", TYPESAFE_PACKAGE + "JsonContent",
			TYPESAFE_PACKAGE + "response.SystemOneResponse", TYPESAFE_PACKAGE + "response.Answer",
			TYPESAFE_PACKAGE + "response.AnswerType", TYPESAFE_PACKAGE + "response.AnswerDeserializer",
			TYPESAFE_PACKAGE + "response.NoulAnswer", TYPESAFE_PACKAGE + "response.ChoiceAnswer",
			TYPESAFE_PACKAGE + "response.ScoreAnswer", TYPESAFE_PACKAGE + "response.UnknownAnswer",
			TYPESAFE_PACKAGE + "response.Usage");

	static final String TYPESAFE_CLIENT = TYPESAFE_PACKAGE + "TypeSafeClient";

	@Override
	public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
		var mcs = MemberCategory.values();
		for (var tr : findJsonAnnotatedClassesInPackage("org.springframework.ai.ollama")) {
			hints.reflection().registerType(tr, mcs);
		}
		hints.reflection().registerType(ThinkOption.ThinkOptionDeserializer.class, mcs);
		hints.reflection().registerType(ThinkOption.ThinkOptionSerializer.class, mcs);

		if (ClassUtils.isPresent(TYPESAFE_CLIENT, classLoader)) {
			for (String type : TYPESAFE_JSON_TYPES) {
				hints.reflection().registerType(TypeReference.of(type), mcs);
			}
		}
	}

}
