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

import java.util.Map;
import java.util.Objects;

public class EvaluationResponse {

	private final boolean pass;

	private final float score;

	private final String feedback;

	private final Map<String, Object> metadata;

	@Deprecated
	public EvaluationResponse(boolean pass, float score, String feedback, Map<String, Object> metadata) {
		this.pass = pass;
		this.score = score;
		this.feedback = feedback;
		this.metadata = metadata;
	}

	public EvaluationResponse(boolean pass, String feedback, Map<String, Object> metadata) {
		this.pass = pass;
		this.score = 0;
		this.feedback = feedback;
		this.metadata = metadata;
	}

	public boolean isPass() {
		return this.pass;
	}

	public float getScore() {
		return this.score;
	}

	public String getFeedback() {
		return this.feedback;
	}

	public Map<String, Object> getMetadata() {
		return this.metadata;
	}

	@Override
	public String toString() {
		return "EvaluationResponse{" + "pass=" + this.pass + ", score=" + this.score + ", feedback='" + this.feedback
				+ '\'' + ", metadata=" + this.metadata + '}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof EvaluationResponse that)) {
			return false;
		}
		return this.pass == that.pass && Float.compare(this.score, that.score) == 0
				&& Objects.equals(this.feedback, that.feedback) && Objects.equals(this.metadata, that.metadata);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.pass, this.score, this.feedback, this.metadata);
	}

}
