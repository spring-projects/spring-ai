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

package org.springframework.ai.chat.client.advisor.guardrail;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import org.springframework.ai.decision.DecisionModel;
import org.springframework.ai.decision.DecisionPrompt;
import org.springframework.ai.decision.DecisionResponse;
import org.springframework.ai.decision.answer.DecisionAnswer;
import org.springframework.ai.decision.answer.RefusalAnswer;
import org.springframework.ai.decision.answer.ScoreAnswer;
import org.springframework.ai.decision.answer.YesNoAnswer;
import org.springframework.ai.decision.input.FieldsInput;

/**
 * A decision model for guardrail tests: answers the hazards of a battery from the text it
 * is asked to screen, and keeps the prompts it got.
 *
 * @author Christian Tzolov
 */
final class ScreeningModel implements DecisionModel {

	private final Function<String, Map<String, DecisionAnswer>> answersForText;

	private final List<DecisionPrompt> prompts = new CopyOnWriteArrayList<>();

	ScreeningModel(Function<String, Map<String, DecisionAnswer>> answersForText) {
		this.answersForText = answersForText;
	}

	/** A model that finds nothing in any text. */
	static ScreeningModel findingNothing() {
		return new ScreeningModel(text -> clean());
	}

	@Override
	public DecisionResponse call(DecisionPrompt prompt) {
		this.prompts.add(prompt);
		return respond(prompt, this.answersForText.apply(textOf(prompt)));
	}

	/** The texts this model was asked to screen, in order. */
	List<String> screened() {
		return this.prompts.stream().map(ScreeningModel::textOf).toList();
	}

	List<DecisionPrompt> prompts() {
		return this.prompts;
	}

	/** Answers the questions the prompt asks, and only those. */
	static DecisionResponse respond(DecisionPrompt prompt, Map<String, DecisionAnswer> answers) {
		DecisionResponse.Builder response = DecisionResponse.builder(prompt);
		for (String name : prompt.getQuestions().keySet()) {
			DecisionAnswer answer = answers.get(name);
			if (answer != null) {
				response.answer(name, answer);
			}
		}
		return response.build();
	}

	/**
	 * The answers to the hazards of the default batteries. The two share three of four
	 * names, so one set serves either direction; the first value answers
	 * {@code jailbreak} on the way in and {@code complied_with_refusable} on the way out.
	 */
	static Map<String, DecisionAnswer> answers(double first, double physicalHarm, double illegal, double selfHarm,
			double severity) {
		Map<String, DecisionAnswer> answers = new LinkedHashMap<>();
		answers.put("jailbreak", yes(first));
		answers.put("complied_with_refusable", yes(first));
		answers.put("physical_harm", yes(physicalHarm));
		answers.put("illegal", yes(illegal));
		answers.put("self_harm", yes(selfHarm));
		answers.put(DecisionGuardrail.SEVERITY_QUESTION, severity(severity));
		return answers;
	}

	static Map<String, DecisionAnswer> clean() {
		return answers(0.01d, 0.0d, 0.0d, 0.0d, 0.0d);
	}

	static YesNoAnswer yes(double probability) {
		return YesNoAnswer.builder().probability(probability).build();
	}

	static ScoreAnswer severity(double value) {
		return ScoreAnswer.builder().value(value).build();
	}

	static RefusalAnswer refusal(String reason) {
		return RefusalAnswer.builder().reason(reason).build();
	}

	private static String textOf(DecisionPrompt prompt) {
		return String.valueOf(((FieldsInput) prompt.getInstructions()).getFields().get(DecisionGuardrail.TEXT_FIELD));
	}

}
