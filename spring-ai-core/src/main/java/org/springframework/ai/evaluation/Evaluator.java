package org.springframework.ai.evaluation;

import org.springframework.ai.model.Content;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@FunctionalInterface
public interface Evaluator {

	EvaluationResponse evaluate(EvaluationRequest evaluationRequest);

	default String doGetSupportingData(EvaluationRequest evaluationRequest) {
		List<Content> data = evaluationRequest.getDataList();
		return data.stream()
			.map(Content::getContent)
			.filter(StringUtils::hasText)
			.collect(Collectors.joining(System.lineSeparator()));
	}

}
