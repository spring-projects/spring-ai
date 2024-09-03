package org.springframework.ai.evaluation;

@FunctionalInterface
public interface Evaluator {

	EvaluationResponse evaluate(EvaluationRequest evaluationRequest);

}
