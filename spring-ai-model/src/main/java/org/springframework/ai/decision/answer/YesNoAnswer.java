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

package org.springframework.ai.decision.answer;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.decision.question.YesNoQuestion;
import org.springframework.util.Assert;

/**
 * The answer to a {@link YesNoQuestion}: the probability that the answer is yes.
 *
 * @author Thomas Vitale
 * @since 2.1.0
 */
public final class YesNoAnswer implements DecisionAnswer {

	/**
	 * The threshold {@link #isYes()} applies.
	 */
	public static final double DEFAULT_THRESHOLD = 0.5;

	private final double probability;

	private YesNoAnswer(double probability) {
		Assert.isTrue(probability >= 0 && probability <= 1,
				() -> "probability must be between 0 and 1, but was " + probability);
		this.probability = probability;
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * The probability, between 0 and 1, that the answer is yes.
	 */
	public double getProbability() {
		return this.probability;
	}

	/**
	 * Whether the answer is yes, that is, the probability is at least 0.5.
	 */
	public boolean isYes() {
		return isYes(DEFAULT_THRESHOLD);
	}

	/**
	 * Whether the probability is at least the given threshold.
	 */
	public boolean isYes(double threshold) {
		return this.probability >= threshold;
	}

	@Override
	public boolean equals(@Nullable Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof YesNoAnswer that)) {
			return false;
		}
		return Double.compare(this.probability, that.probability) == 0;
	}

	@Override
	public int hashCode() {
		return Double.hashCode(this.probability);
	}

	@Override
	public String toString() {
		return "YesNoAnswer{probability=" + this.probability + "}";
	}

	public static final class Builder {

		private @Nullable Double probability;

		private Builder() {
		}

		public Builder probability(double probability) {
			this.probability = probability;
			return this;
		}

		public YesNoAnswer build() {
			Assert.state(this.probability != null, "probability cannot be null");
			return new YesNoAnswer(this.probability);
		}

	}

}
