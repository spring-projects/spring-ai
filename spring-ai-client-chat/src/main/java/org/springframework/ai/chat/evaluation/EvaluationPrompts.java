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

/*!
* Portions of this code are based on Promptfoo (MIT License)
* Copyright (c) 2025 Promptfoo
*/

package org.springframework.ai.chat.evaluation;

import org.springframework.ai.chat.prompt.PromptTemplate;

final class EvaluationPrompts {

	static final PromptTemplate STEPS_PROMPT_TEMPLATE = new PromptTemplate(
			"""
					Given evaluation criteria that outline how you should judge a piece of text, generate 3-4 concise evaluation steps applicable to any text based on the criteria below and designed to check whether the criteria are satisfied by the text.

					**EVALUATION CRITERIA**
					{criteria}

					**OUTPUT FORMAT**
					IMPORTANT:
					- Return output ONLY as a minified JSON object (no code fences).
					- The JSON object must contain a single key, "steps", whose value is a list of strings.
					- Each string must represent one evaluation step.
					- Do NOT include any explanations, commentary, extra text, or additional formatting.

					Format:
					\\{"steps": <list_of_strings>\\}

					Example:
					\\{"steps":["<Evaluation Step 1>","<Evaluation Step 2>","<Evaluation Step 3>","<Evaluation Step 4>"]\\}

					Here are the 3-4 concise evaluation steps, formatted as required in a minified JSON:
					JSON:""");

	static final PromptTemplate GEVAL_PROMPT_TEMPLATE = new PromptTemplate(
			"""
					You will be given one Reply for a Prompt below. Your task is to rate the Reply on one metric.
					Make sure you read and understand these instructions carefully.

					**Evaluation Criteria**
					{criteria}

					**Evaluation Steps**
					{steps}

					Given the evaluation steps, return a JSON with two keys:
					1) a "score" key that MUST be an integer from 0 to {maxValue}, where {maxValue} indicates that the condition described by the Evaluation Criteria is fully and clearly observed in the Reply according to the Evaluation Steps, and 0 indicates that it is not observed at all;
					2) a "reason" key, a reason for the given score, but DO NOT QUOTE THE SCORE in your reason. Mention specific information from Prompt and Reply in your reason, but be very concise.

					**Prompt**
					{input}

					**Reply**
					{output}

					**OUTPUT FORMAT**
					IMPORTANT:
					- Return output ONLY as a minified JSON object (no code fences).
					- The JSON object must contain exactly two keys: "score" and "reason".
					- No additional words, explanations, or formatting are needed.
					- Absolutely no additional text, explanations, line breaks, or formatting outside the JSON object are allowed.

					Example JSON:
					\\{"score":0,"reason":"The text of reply does not follow the evaluation criteria provided."\\}

					Here is the final evaluation in the required minified JSON format:
					JSON:""");

	static final PromptTemplate RUBRIC_PROMPT_TEMPLATE = new PromptTemplate(
			"""
					You are grading output according to a user-specified rubric. If the statement in the rubric is true, then the output passes the test.
					Respond with a JSON object containing a reason (string), pass (boolean), and score (number).

					Examples:

					<Output>Hello world</Output>
					<Rubric>Content contains a greeting</Rubric>
					\\{"reason": "the content contains the word 'Hello'", "pass": true, "score": 1.0\\}

					<Output>Avast ye swabs, repel the invaders!</Output>
					<Rubric>Does not speak like a pirate</Rubric>
					\\{"reason": "'avast ye' is a common pirate term", "pass": false, "score": 0.0\\}

					<Output>{output}</Output>
					<Rubric>{rubric}</Rubric>""");

	private EvaluationPrompts() {
	}

}
