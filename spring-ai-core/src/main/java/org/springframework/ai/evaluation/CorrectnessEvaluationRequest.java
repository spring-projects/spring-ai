package org.springframework.ai.evaluation;

import java.util.List;

import org.springframework.ai.model.Content;

/**
 * Represents an evaluation request for correctness evaluation.
 *
 * @author Craig Walls
 * @since 1.0.0 M2
 */
public class CorrectnessEvaluationRequest extends EvaluationRequest {

	private final String referenceAnswer;

	public CorrectnessEvaluationRequest(String userText, List<Content> dataList, String responseContent,
			String referenceAnswer) {
		super(userText, dataList, responseContent);
		this.referenceAnswer = referenceAnswer;
	}

	public String getReferenceAnswer() {
		return referenceAnswer;
	}

}
