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

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.ai.evaluation.Evaluator;
import org.springframework.util.Assert;

/**
 * Evaluates the relevancy of a response to a query based on the context provided.
 */
public class RelevancyEvaluator implements Evaluator {

	private boolean reasoningEnabled = false;
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

	private static final PromptTemplate DEFAULT_PROMPT_TEMPLATE_FOR_REASONING = new PromptTemplate("""
				Your task is to evaluate if the response for the query
				is in line with the context information provided.

				Query:
				{query}

				Response:
				{response}

				Context:
				{context}

			""");

	private final ChatClient.Builder chatClientBuilder;

	private final PromptTemplate promptTemplate;

	public RelevancyEvaluator(ChatClient.Builder chatClientBuilder) {
		this(chatClientBuilder, null);
	}

	private RelevancyEvaluator(ChatClient.Builder chatClientBuilder, @Nullable PromptTemplate promptTemplate) {
		this(chatClientBuilder, promptTemplate, false);
	}

	private RelevancyEvaluator(ChatClient.Builder chatClientBuilder, @Nullable PromptTemplate promptTemplate, boolean reasoningEnabled) {
		Assert.notNull(chatClientBuilder, "chatClientBuilder cannot be null");
		this.chatClientBuilder = chatClientBuilder;
		this.promptTemplate = promptTemplate != null ? promptTemplate :
				(reasoningEnabled ? DEFAULT_PROMPT_TEMPLATE_FOR_REASONING : DEFAULT_PROMPT_TEMPLATE);
		this.reasoningEnabled = reasoningEnabled;
	}

	@Override
	public EvaluationResponse evaluate(EvaluationRequest evaluationRequest) {
		var inputResponse = evaluationRequest.getResponseContent();
		var inputContext = doGetSupportingData(evaluationRequest);

		var userMessage = this.promptTemplate
				.render(Map.of("query", evaluationRequest.getUserText(), "response", inputResponse, "context", inputContext));

		Response evalResponse = evaluateUserMessage(userMessage);

		return new EvaluationResponse(
				evalResponse.isInLine(),
				evalResponse.isInLine() ? 1 : 0,
				evalResponse.reasoning(),
				Collections.emptyMap()
		);
	}

	private Response evaluateUserMessage(String userMessage) {
		Response evalResponse;
		if (reasoningEnabled) {
			evalResponse = this.chatClientBuilder.build().prompt().user(userMessage).call().entity(Response.class);

			if (evalResponse == null) {
				evalResponse = new Response("Error: NULL response from the chatClient during relevancy evaluation", false);
			}
		} else {
			String clientResponse = this.chatClientBuilder.build().prompt().user(userMessage).call().content();

			boolean passing = "yes".equalsIgnoreCase(clientResponse);
			evalResponse = new Response("", passing);
		}
		return evalResponse;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private boolean feedbackEnabled = false;
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

		public Builder feedbackEnabled(boolean feedbackEnabled) {
			this.feedbackEnabled = feedbackEnabled;
			return this;
		}

		public RelevancyEvaluator build() {
			Assert.state(this.chatClientBuilder != null, "chatClientBuilder cannot be null");
			return new RelevancyEvaluator(this.chatClientBuilder, this.promptTemplate, feedbackEnabled);
		}
	}

	record Response(
			@JsonPropertyDescription("Provides a short explanation of how the response for the query is or is not in line with the context information provided")
			String reasoning,
			@JsonPropertyDescription("Indicates whether the response for the query is in line with the context information provided")
			boolean isInLine) {
	}
}
