package org.springframework.ai.evaluation;

import java.util.Map;
import java.util.Objects;

public class EvaluationResponse {

	private boolean pass;

	private float score;

	private String feedback;

	Map<String, Object> metadata;

	public EvaluationResponse(boolean pass, float score, String feedback, Map<String, Object> metadata) {
		this.pass = pass;
		this.score = score;
		this.feedback = feedback;
		this.metadata = metadata;
	}

	public boolean isPass() {
		return pass;
	}

	public float getScore() {
		return score;
	}

	public String getFeedback() {
		return feedback;
	}

	public Map<String, Object> getMetadata() {
		return metadata;
	}

	@Override
	public String toString() {
		return "EvaluationResponse{" + "pass=" + pass + ", score=" + score + ", feedback='" + feedback + '\''
				+ ", metadata=" + metadata + '}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof EvaluationResponse that))
			return false;
		return pass == that.pass && Float.compare(score, that.score) == 0 && Objects.equals(feedback, that.feedback)
				&& Objects.equals(metadata, that.metadata);
	}

	@Override
	public int hashCode() {
		return Objects.hash(pass, score, feedback, metadata);
	}

}
