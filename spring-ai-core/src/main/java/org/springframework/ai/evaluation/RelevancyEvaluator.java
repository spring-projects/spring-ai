/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.evaluation;

import java.util.Collections;

import org.springframework.ai.chat.client.ChatClient;

public class RelevancyEvaluator implements Evaluator {

	private static final String DEFAULT_EVALUATION_PROMPT_TEXT = """
				Your task is to evaluate if the response for the query
				is in line with the context information provided.\\n
				You have two options to answer. Either YES/ NO.\\n
				Answer - YES, if the response for the query
				is in line with context information otherwise NO.\\n
				Query: \\n {query}\\n
				Response: \\n {response}\\n
				Context: \\n {context}\\n
				Answer: "
			""";

	private final ChatClient.Builder chatClientBuilder;

	public RelevancyEvaluator(ChatClient.Builder chatClientBuilder) {
		this.chatClientBuilder = chatClientBuilder;
	}

	@Override
	public EvaluationResponse evaluate(EvaluationRequest evaluationRequest) {

		var response = evaluationRequest.getResponseContent();
		var context = doGetSupportingData(evaluationRequest);

		String evaluationResponse = this.chatClientBuilder.build()
			.prompt()
			.user(userSpec -> userSpec.text(DEFAULT_EVALUATION_PROMPT_TEXT)
				.param("query", evaluationRequest.getUserText())
				.param("response", response)
				.param("context", context))
			.call()
			.content();

		boolean passing = false;
		float score = 0;
		if (evaluationResponse.toLowerCase().contains("yes")) {
			passing = true;
			score = 1;
		}

		return new EvaluationResponse(passing, score, "", Collections.emptyMap());
	}

}
