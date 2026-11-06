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

import java.util.Collections;
import java.util.Map;

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
 * Evaluates a response against a user-provided rubric using an LLM-as-a-judge prompt.
 */
public class RubricEvaluator implements Evaluator {

	private final ChatClient.Builder chatClientBuilder;

	private final PromptTemplate promptTemplate;

	private final String criteria;

	public RubricEvaluator(ChatClient.Builder chatClientBuilder, String criteria) {
		this(chatClientBuilder, criteria, null);
	}

	private RubricEvaluator(ChatClient.Builder chatClientBuilder, String criteria,
			@Nullable PromptTemplate promptTemplate) {
		Assert.notNull(chatClientBuilder, "chatClientBuilder cannot be null");
		Assert.hasText(criteria, "criteria cannot be null or empty");

		this.chatClientBuilder = chatClientBuilder;
		this.criteria = criteria;
		this.promptTemplate = promptTemplate != null ? promptTemplate : EvaluationPrompts.RUBRIC_PROMPT_TEMPLATE;
	}

	@Override
	public EvaluationResponse evaluate(EvaluationRequest evaluationRequest) {
		var prompt = this.promptTemplate
			.render(Map.of("output", evaluationRequest.getResponseContent(), "rubric", this.criteria));

		String evaluationResponse = this.chatClientBuilder.build().prompt().system(prompt).call().content();

		JsonNode result = parseEvaluationResponse(evaluationResponse);
		JsonNode reason = result.get("reason");
		JsonNode pass = result.get("pass");
		JsonNode score = result.get("score");

		Assert.state(reason != null && reason.isTextual(), "Evaluation response must contain a string 'reason'");
		Assert.state(pass != null && pass.isBoolean(), "Evaluation response must contain a boolean 'pass'");
		Assert.state(score != null && score.isNumber(), "Evaluation response must contain a numeric 'score'");

		return new EvaluationResponse(pass.booleanValue(), score.floatValue(), reason.textValue(),
				Collections.emptyMap());
	}

	private JsonNode parseEvaluationResponse(@Nullable String evaluationResponse) {
		Assert.state(evaluationResponse != null && !evaluationResponse.isBlank(),
				"Evaluation response must not be null or blank");

		try {
			JsonNode result = JsonMapper.shared().readTree(evaluationResponse);

			Assert.state(result != null && result.isObject(), "Evaluation response must be a JSON object");

			return result;
		}
		catch (JacksonException ex) {
			throw new IllegalStateException("Evaluation response must be valid JSON", ex);
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private ChatClient.@Nullable Builder chatClientBuilder;

		private @Nullable PromptTemplate promptTemplate;

		private @Nullable String criteria;

		private Builder() {
		}

		public Builder chatClientBuilder(ChatClient.Builder chatClientBuilder) {
			this.chatClientBuilder = chatClientBuilder;

			return this;
		}

		public Builder promptTemplate(PromptTemplate promptTemplate) {
			this.promptTemplate = promptTemplate;

			return this;
		}

		public Builder criteria(String criteria) {
			this.criteria = criteria;

			return this;
		}

		public RubricEvaluator build() {
			Assert.state(this.chatClientBuilder != null, "chatClientBuilder cannot be null");
			Assert.hasText(this.criteria, "criteria cannot be null or empty");

			return new RubricEvaluator(this.chatClientBuilder, this.criteria, this.promptTemplate);
		}

	}

}
