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

package org.springframework.ai.chat.evaluation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.ai.evaluation.Evaluator;
import org.springframework.util.Assert;

/**
 * Evaluates a response using the two-stage G-Eval approach, with a configurable score
 * range.
 */
public class GevalEvaluator implements Evaluator {

	private static final int DEFAULT_MAX_VALUE = 10;

	private static final Map<String, List<String>> CACHED_STEPS = new ConcurrentHashMap<>();

	private final String criteria;

	private final int maxValue;

	private final ChatClient.Builder chatClientBuilder;

	private final PromptTemplate stepsPromptTemplate;

	private final PromptTemplate evaluationPromptTemplate;

	public GevalEvaluator(ChatClient.Builder chatClientBuilder, String criteria) {
		this(chatClientBuilder, criteria, DEFAULT_MAX_VALUE, null, null);
	}

	private GevalEvaluator(ChatClient.Builder chatClientBuilder, String criteria, int maxValue,
			@Nullable PromptTemplate stepsPromptTemplate, @Nullable PromptTemplate evaluationPromptTemplate) {
		Assert.notNull(chatClientBuilder, "chatClientBuilder cannot be null");
		Assert.hasText(criteria, "criteria cannot be null or empty");
		Assert.isTrue(maxValue > 0, "maxValue must be greater than zero");

		this.chatClientBuilder = chatClientBuilder;
		this.criteria = criteria;
		this.maxValue = maxValue;

		this.stepsPromptTemplate = stepsPromptTemplate != null ? stepsPromptTemplate
				: EvaluationPrompts.STEPS_PROMPT_TEMPLATE;
		this.evaluationPromptTemplate = evaluationPromptTemplate != null ? evaluationPromptTemplate
				: EvaluationPrompts.GEVAL_PROMPT_TEMPLATE;
	}

	@Override
	public EvaluationResponse evaluate(EvaluationRequest evaluationRequest) {
		List<String> steps = getSteps();
		String formattedSteps = steps.stream()
			.map(step -> "- " + step)
			.collect(Collectors.joining(System.lineSeparator()));

		String evaluationPrompt = this.evaluationPromptTemplate
			.render(Map.of("criteria", this.criteria, "steps", formattedSteps, "maxValue", this.maxValue, "input",
					evaluationRequest.getUserText(), "output", evaluationRequest.getResponseContent()));

		String evaluationResponse = this.chatClientBuilder.build().prompt().system(evaluationPrompt).call().content();

		JsonNode result = parseJsonObject(evaluationResponse, "Evaluation response");
		JsonNode score = result.get("score");
		JsonNode reason = result.get("reason");

		Assert.state(score != null && score.isIntegralNumber(), "Evaluation response must contain an integer 'score'");
		Assert.state(reason != null && reason.isTextual(), "Evaluation response must contain a string 'reason'");

		int numericScore = score.intValue();
		Assert.state(numericScore >= 0 && numericScore <= this.maxValue,
				"Evaluation response score must be between 0 and " + this.maxValue);

		return new EvaluationResponse(numericScore == this.maxValue, numericScore, reason.textValue(),
				Collections.emptyMap());
	}

	private List<String> getSteps() {
		return CACHED_STEPS.computeIfAbsent(this.criteria, key -> {
			String stepsPrompt = this.stepsPromptTemplate.render(Map.of("criteria", this.criteria));
			String stepsResponse = this.chatClientBuilder.build().prompt().system(stepsPrompt).call().content();

			return List.copyOf(parseSteps(stepsResponse));
		});
	}

	private List<String> parseSteps(@Nullable String stepsResponse) {
		JsonNode result = parseJsonObject(stepsResponse, "Evaluation steps response");
		JsonNode steps = result.get("steps");

		Assert.state(steps != null && steps.isArray() && steps.size() > 0,
				"Evaluation steps response must contain a non-empty 'steps' array");

		List<String> parsedSteps = new ArrayList<>();
		for (JsonNode step : steps) {
			Assert.state(step.isTextual() && !step.textValue().isBlank(),
					"Evaluation steps response must contain only non-blank strings");

			parsedSteps.add(step.textValue());
		}

		return parsedSteps;
	}

	private JsonNode parseJsonObject(@Nullable String response, String responseDescription) {
		Assert.state(response != null && !response.isBlank(), responseDescription + " must not be null or blank");

		try {
			JsonNode result = JsonMapper.shared().readTree(response);

			Assert.state(result != null && result.isObject(), responseDescription + " must be a JSON object");

			return result;
		}
		catch (JacksonException ex) {
			throw new IllegalStateException(responseDescription + " must be valid JSON", ex);
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private int maxValue = DEFAULT_MAX_VALUE;

		private ChatClient.@Nullable Builder chatClientBuilder;

		private @Nullable String criteria;

		private @Nullable PromptTemplate stepsPromptTemplate;

		private @Nullable PromptTemplate evaluationPromptTemplate;

		private Builder() {
		}

		public Builder chatClientBuilder(ChatClient.Builder chatClientBuilder) {
			this.chatClientBuilder = chatClientBuilder;
			return this;
		}

		public Builder criteria(String criteria) {
			this.criteria = criteria;
			return this;
		}

		public Builder maxValue(int maxValue) {
			this.maxValue = maxValue;
			return this;
		}

		public Builder stepsPromptTemplate(PromptTemplate stepsPromptTemplate) {
			this.stepsPromptTemplate = stepsPromptTemplate;
			return this;
		}

		public Builder evaluationPromptTemplate(PromptTemplate evaluationPromptTemplate) {
			this.evaluationPromptTemplate = evaluationPromptTemplate;
			return this;
		}

		public GevalEvaluator build() {
			Assert.notNull(this.chatClientBuilder, "chatClientBuilder cannot be null");
			Assert.hasText(this.criteria, "criteria cannot be null or empty");
			Assert.isTrue(this.maxValue > 0, "maxValue must be greater than zero");

			return new GevalEvaluator(this.chatClientBuilder, this.criteria, this.maxValue, this.stepsPromptTemplate,
					this.evaluationPromptTemplate);
		}

	}

}
