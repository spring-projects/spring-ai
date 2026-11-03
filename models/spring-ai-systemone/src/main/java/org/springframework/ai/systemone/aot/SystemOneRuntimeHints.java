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

import java.util.List;

import org.jspecify.annotations.Nullable;
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

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;

/**
 * Registers runtime hints for the TypeSafe SDK types that the System One decision models
 * send and receive as JSON.
 *
 * <p>
 * The SDK ships no native image metadata, and scanning its package misses types such as
 * {@link JsonContent} that use {@code @JsonValue} and {@code @JsonCreator} only, so the
 * types are listed here. Every decision model built on the SDK, such as the Ollama one,
 * gets these hints through its dependency on this module.
 *
 * @author Thomas Vitale
 * @author Christian Tzolov
 * @since 2.1.0
 */
public class SystemOneRuntimeHints implements RuntimeHintsRegistrar {

	private static final List<Class<?>> TYPESAFE_JSON_TYPES = List.of(SystemOneRequest.class, Question.class,
			QuestionType.class, Noul.class, NoulCriteria.class, Choice.class, Score.class, JsonContent.class,
			SystemOneResponse.class, Answer.class, AnswerType.class, NoulAnswer.class, ChoiceAnswer.class,
			ScoreAnswer.class, UnknownAnswer.class, Usage.class);

	/**
	 * The deserializer Jackson reads the answers of a {@link SystemOneResponse} with. It
	 * is package-private in the SDK, so it can only be named.
	 */
	private static final String ANSWER_DESERIALIZER = "org.springaicommunity.typesafe.response.AnswerDeserializer";

	@Override
	public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
		var mcs = MemberCategory.values();
		for (Class<?> type : TYPESAFE_JSON_TYPES) {
			hints.reflection().registerType(type, mcs);
		}
		hints.reflection().registerType(TypeReference.of(ANSWER_DESERIALIZER), mcs);
	}

}
