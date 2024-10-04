package org.springframework.ai.evaluation;

import org.springframework.ai.chat.client.ChatClient;

import java.util.Collections;

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
