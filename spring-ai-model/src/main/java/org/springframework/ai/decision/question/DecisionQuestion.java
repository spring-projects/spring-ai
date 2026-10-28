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

package org.springframework.ai.decision.question;

import org.springframework.ai.decision.DecisionModel;

/**
 * A question a {@link DecisionModel} answers about an input: a {@link YesNoQuestion}, a
 * {@link ChoiceQuestion} or a {@link ScoreQuestion}. The interface is sealed, like
 * {@link org.springframework.ai.decision.input.DecisionInput}, so that providers and
 * applications can handle every kind of question exhaustively; a new kind is an addition
 * to the API, not to a provider.
 *
 * @author Thomas Vitale
 * @author Christian Tzolov
 * @since 2.1.0
 */
public sealed interface DecisionQuestion permits YesNoQuestion, ChoiceQuestion, ScoreQuestion {

	/**
	 * The question text, which tells the model what to decide.
	 */
	String getText();

}
