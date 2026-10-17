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

import java.util.Map;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.ai.evaluation.Evaluator;
import org.springframework.util.Assert;

/**
 * Evaluates the relevancy of a response to a query based on the context provided.
 *
 * @author Thomas Vitale
 * @author Kuntal Maity
 */
public class RelevancyEvaluator implements Evaluator {

	public static final String LLM_RAW_RESPONSE_METADATA_KEY = "llm_raw_response";

	private static final PromptTemplate DEFAULT_PROMPT_TEMPLATE = new PromptTemplate("""
				Your task is to evaluate if the response for the query
				is in line with the context information provided.

				You have two options to answer. Either YES or NO.

				Answer YES, if the response for the query
				is in line with context information otherwise NO.

				Query:
				{query}

				Response:
				{response}

				Context:
				{context}

				Answer:
			""");

	private final ChatClient.Builder chatClientBuilder;

	private final PromptTemplate promptTemplate;

	public RelevancyEvaluator(ChatClient.Builder chatClientBuilder) {
		this(chatClientBuilder, null);
	}

	private RelevancyEvaluator(ChatClient.Builder chatClientBuilder, @Nullable PromptTemplate promptTemplate) {
		Assert.notNull(chatClientBuilder, "chatClientBuilder cannot be null");
		this.chatClientBuilder = chatClientBuilder;
		this.promptTemplate = promptTemplate != null ? promptTemplate : DEFAULT_PROMPT_TEMPLATE;
	}

	/**
	 * Evaluates whether the response is relevant to the query given the context.
	 * @param evaluationRequest the request containing the query, context, and response
	 * @return an EvaluationResponse with {@code pass=true} and {@code score=1.0} when
	 * relevant. The {@code feedback} field contains the raw LLM response text. The
	 * {@code metadata} map contains the same value under the key
	 * {@link #LLM_RAW_RESPONSE_METADATA_KEY}.
	 */
	@Override
	public EvaluationResponse evaluate(EvaluationRequest evaluationRequest) {
		var response = evaluationRequest.getResponseContent();
		var context = doGetSupportingData(evaluationRequest);

		var userMessage = this.promptTemplate
			.render(Map.of("query", evaluationRequest.getUserText(), "response", response, "context", context));

		String evaluationResponse = this.chatClientBuilder.build().prompt().user(userMessage).call().content();

		String llmResponse = (evaluationResponse != null) ? evaluationResponse : "";
		boolean passing = "yes".equalsIgnoreCase(llmResponse.strip());
		float score = passing ? 1 : 0;
		return new EvaluationResponse(passing, score, llmResponse,
				Map.of(LLM_RAW_RESPONSE_METADATA_KEY, (Object) llmResponse));
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private ChatClient.@Nullable Builder chatClientBuilder;

		private @Nullable PromptTemplate promptTemplate;

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

		public RelevancyEvaluator build() {
			Assert.state(this.chatClientBuilder != null, "chatClientBuilder cannot be null");
			return new RelevancyEvaluator(this.chatClientBuilder, this.promptTemplate);
		}

	}

}
