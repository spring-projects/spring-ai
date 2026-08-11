/*
 * Copyright 2023-2025 the original author or authors.
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.ai.evaluation.Evaluator;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

/**
 * An {@link Evaluator} that measures how relevant an LLM-generated response is with
 * respect to the original user query, optionally considering additional supporting
 * context.
 *
 * <p>
 * The evaluator returns a relevance score between {@code 0.0} (not relevant) and
 * {@code 1.0} (fully relevant). A response passes the evaluation when its score is
 * greater than or equal to a configurable relevance threshold (default: {@code 0.8}).
 * </p>
 *
 * @author Alessandro Russo
 * @see Evaluator
 * @see EvaluationRequest
 * @see EvaluationResponse
 */

public class ResponseRelevancyEvaluator implements Evaluator {

	private static final PromptTemplate DEFAULT_PROMPT_TEMPLATE = new PromptTemplate(
			"""
					Your task is to evaluate if a given response is relevant to a query, if provided you can take in account the optional context.
					Provide your evaluation in a Floating point format between 0.00, for no relevant response and 1.00 for a total relevant response.
					Your answer always must be only relevance point.
					Query:
					{query}
					Response:
					{response}
					Context:
					{context}

					Float Answer:
					""");

	private final ChatClient.Builder chatClientBuilder;

	private final PromptTemplate promptTemplate;

	private final Float relevanceThreshold;

	private static final Logger logger = LoggerFactory.getLogger(ResponseRelevancyEvaluator.class);

	public ResponseRelevancyEvaluator(ChatClient.Builder chatClientBuilder) {
		this(chatClientBuilder, (PromptTemplate) null, (Float) null);
	}

	private ResponseRelevancyEvaluator(ChatClient.Builder chatClientBuilder, @Nullable PromptTemplate promptTemplate,
			@Nullable Float relevanceThreshold) {
		Assert.notNull(chatClientBuilder, "chatClientBuilder cannot be null");
		this.chatClientBuilder = chatClientBuilder;
		this.promptTemplate = promptTemplate != null ? promptTemplate : DEFAULT_PROMPT_TEMPLATE;
		this.relevanceThreshold = relevanceThreshold != null ? relevanceThreshold : 0.8F;

	}

	public EvaluationResponse evaluate(EvaluationRequest evaluationRequest) {
		String response = evaluationRequest.getResponseContent();
		String context = this.doGetSupportingData(evaluationRequest);
		String userMessage = this.promptTemplate
			.render(Map.of("query", evaluationRequest.getUserText(), "response", response, "context", context));
		logger.debug("Evaluator input message: {}", userMessage);
		String evaluationResponse = this.chatClientBuilder.build().prompt().user(userMessage).call().content();
		logger.debug("Evaluator output: {}", evaluationResponse);

		Float score;
		try {
			score = Float.parseFloat(evaluationResponse.trim());
		}
		catch (NumberFormatException ex) {
			throw new IllegalStateException("Invalid relevance score returned by model: " + evaluationResponse, ex);
		}

		boolean passing = score >= this.relevanceThreshold;

		return new EvaluationResponse(passing, score, "", Collections.emptyMap());
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private ChatClient.Builder chatClientBuilder;

		private PromptTemplate promptTemplate;

		private Float relevanceThreshold;

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

		public Builder relevanceThreshold(Float relevanceThreshold) {
			this.relevanceThreshold = relevanceThreshold;
			return this;
		}

		public ResponseRelevancyEvaluator build() {
			return new ResponseRelevancyEvaluator(this.chatClientBuilder, this.promptTemplate, this.relevanceThreshold);
		}

	}

}