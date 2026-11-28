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

package org.springframework.ai.systemone.api.response;

/**
 * One model answer, keyed in the response by the name the request gave its question.
 *
 * <p>
 * The hierarchy is sealed, so an answer can be handled exhaustively:
 *
 * <pre>{@code
 * Answer answer = response.answer("department");
 * if (answer instanceof ChoiceAnswer choice) {
 *     route(choice.value(), choice.confidence());
 * }
 * }</pre>
 *
 * {@link UnknownAnswer} is the forward-compatibility escape hatch: an answer kind added
 * to the API after this client version is surfaced as raw JSON rather than failing the
 * call.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public sealed interface Answer permits NoulAnswer, ChoiceAnswer, ScoreAnswer, UnknownAnswer {

	/**
	 * @return the kind of this answer
	 */
	AnswerType type();

}
