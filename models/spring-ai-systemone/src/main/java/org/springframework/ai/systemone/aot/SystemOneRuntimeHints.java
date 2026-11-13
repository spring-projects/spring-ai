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

/**
 * Registers runtime hints for the System One API types that the client sends and receives
 * as JSON.
 *
 * <p>
 * Scanning the package would miss types such as {@link JsonContent} that use
 * {@code @JsonValue} and {@code @JsonCreator} only, so the types are listed here. Every
 * decision model built on the client, such as the Ollama one, gets these hints through
 * its dependency on this module.
 *
 * @author Thomas Vitale
 * @author Christian Tzolov
 * @since 2.1.0
 */
public class SystemOneRuntimeHints implements RuntimeHintsRegistrar {

	private static final List<Class<?>> SYSTEM_ONE_JSON_TYPES = List.of(SystemOneRequest.class, Question.class,
			QuestionType.class, Noul.class, NoulCriteria.class, Choice.class, Score.class, JsonContent.class,
			SystemOneResponse.class, Answer.class, AnswerType.class, NoulAnswer.class, ChoiceAnswer.class,
			ScoreAnswer.class, UnknownAnswer.class, Usage.class, ListModelsResponse.class, ModelMetadata.class);

	/**
	 * The deserializer Jackson reads the answers of a {@link SystemOneResponse} with. It
	 * is package-private, so it can only be named.
	 */
	private static final String ANSWER_DESERIALIZER = "org.springframework.ai.systemone.api.response.AnswerDeserializer";

	@Override
	public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
		var mcs = MemberCategory.values();
		for (Class<?> type : SYSTEM_ONE_JSON_TYPES) {
			hints.reflection().registerType(type, mcs);
		}
		hints.reflection().registerType(TypeReference.of(ANSWER_DESERIALIZER), mcs);
	}

}
