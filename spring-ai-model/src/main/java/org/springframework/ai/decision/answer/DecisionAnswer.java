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

package org.springframework.ai.decision.answer;

import org.springframework.ai.decision.DecisionModel;
import org.springframework.ai.decision.question.DecisionQuestion;

/**
 * The answer a {@link DecisionModel} gives to a {@link DecisionQuestion}: a
 * {@link YesNoAnswer}, a {@link ChoiceAnswer} or a {@link ScoreAnswer} matching the kind
 * of the question, or a {@link RefusalAnswer} when the provider declined to answer it.
 * The interface is sealed, like {@link DecisionQuestion}, so that applications can handle
 * every kind of answer exhaustively.
 *
 * @author Thomas Vitale
 * @author Christian Tzolov
 * @since 2.1.0
 */
public sealed interface DecisionAnswer permits YesNoAnswer, ChoiceAnswer, ScoreAnswer, RefusalAnswer {

}
