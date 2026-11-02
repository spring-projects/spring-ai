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

package org.springframework.ai.decision;

import java.util.Map;

import org.springframework.ai.decision.input.DecisionInput;
import org.springframework.ai.decision.question.DecisionQuestion;
import org.springframework.ai.model.Model;

/**
 * A model that answers typed questions about an input instead of generating text. Each
 * answer carries the probabilities the model assigned, so that the application decides
 * what to do with them.
 *
 * @author Thomas Vitale
 * @since 2.1.0
 */
@FunctionalInterface
public interface DecisionModel extends Model<DecisionPrompt, DecisionResponse> {

	@Override
	DecisionResponse call(DecisionPrompt prompt);

	/**
	 * Ask the given questions about a text, with the model's default options.
	 */
	default DecisionResponse call(String text, Map<String, ? extends DecisionQuestion> questions) {
		return call(DecisionPrompt.builder().text(text).questions(questions).build());
	}

	/**
	 * Ask the given questions about an input, with the model's default options.
	 */
	default DecisionResponse call(DecisionInput input, Map<String, ? extends DecisionQuestion> questions) {
		return call(DecisionPrompt.builder().input(input).questions(questions).build());
	}

}
