package org.springframework.ai.evaluation;

import org.springframework.ai.chat.client.ChatClient;

import java.util.Collections;

public class HallucinationDetectionEvaluator implements Evaluator {

	private static final String DEFAULT_EVALUATION_PROMPT_TEXT = """
			    Document: \\n {document}\\n
			    Claim: \\n {claim}
			""";

	private final ChatClient.Builder chatClientBuilder;

	public HallucinationDetectionEvaluator(ChatClient.Builder chatClientBuilder) {
		this.chatClientBuilder = chatClientBuilder;
	}

	@Override
	public EvaluationResponse evaluate(EvaluationRequest evaluationRequest) {
		var response = evaluationRequest.getResponseContent();
		var context = doGetSupportingData(evaluationRequest);

		String evaluationResponse = this.chatClientBuilder.build()
			.prompt()
			.user(userSpec -> userSpec.text(DEFAULT_EVALUATION_PROMPT_TEXT)
				.param("document", context)
				.param("claim", response))
			.call()
			.content();

		boolean passing = evaluationResponse.equalsIgnoreCase("yes");
		return new EvaluationResponse(passing, "", Collections.emptyMap());
	}

}
