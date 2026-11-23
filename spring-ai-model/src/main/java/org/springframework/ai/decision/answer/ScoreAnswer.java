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

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.decision.question.ScoreQuestion;
import org.springframework.util.Assert;

/**
 * The answer to a {@link ScoreQuestion}: the expected level, as a value between 0 and
 * {@code levels - 1} that weighs each level by its probability, the probability of each
 * level when the provider reports them, and the provider's confidence when it reports
 * one.
 *
 * @author Thomas Vitale
 * @author Christian Tzolov
 * @since 2.1.0
 */
public final class ScoreAnswer implements DecisionAnswer {

	private final double value;

	private final List<Double> probabilities;

	private final @Nullable Double confidence;

	private ScoreAnswer(double value, List<Double> probabilities, @Nullable Double confidence) {
		Assert.isTrue(!Double.isNaN(value) && value >= 0, () -> "value must be 0 or more, but was " + value);
		Assert.notNull(probabilities, "probabilities cannot be null");
		this.value = value;
		probabilities.forEach(probability -> Assert.isTrue(probability >= 0 && probability <= 1,
				() -> "probabilities must be between 0 and 1, but found " + probability));
		this.probabilities = List.copyOf(probabilities);
		Assert.isTrue(confidence == null || (confidence >= 0 && confidence <= 1),
				() -> "confidence must be between 0 and 1, but was " + confidence);
		this.confidence = confidence;
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * The expected level, between 0 and {@code levels - 1}. It is not an integer: a value
	 * of 1.5 means the model is split between the second and the third level.
	 */
	public double getValue() {
		return this.value;
	}

	/**
	 * The probability of each level, by level position, or an empty list when the
	 * provider doesn't report them.
	 */
	public List<Double> getProbabilities() {
		return this.probabilities;
	}

	/**
	 * The provider's confidence in the answer, or {@code null} when the provider doesn't
	 * report one.
	 */
	public @Nullable Double getConfidence() {
		return this.confidence;
	}

	/**
	 * The level the model finds most likely: the one with the highest probability, or,
	 * when several share it, the one of them closest to the expected value. When the
	 * provider doesn't report probabilities, or reports none above zero, it falls back to
	 * {@link #getRoundedLevel()}.
	 * <p>
	 * This is the level to act on. It can differ from the rounded expected value: with
	 * probabilities of {@code 0.5, 0, 0.5} over three levels the expected value is
	 * {@code 1.0}, a level the model gave no probability at all, while the nearest level
	 * is {@code 0}.
	 */
	public int getNearestLevel() {
		int nearest = -1;
		for (int level = 0; level < this.probabilities.size(); level++) {
			double probability = this.probabilities.get(level);
			if (probability <= 0) {
				continue;
			}
			if (nearest < 0 || probability > this.probabilities.get(nearest)
					|| (probability == this.probabilities.get(nearest)
							&& Math.abs(level - this.value) < Math.abs(nearest - this.value))) {
				nearest = level;
			}
		}
		return nearest >= 0 ? nearest : getRoundedLevel();
	}

	/**
	 * The expected value rounded to the closest level, halves rounding up: {@code 1.5}
	 * gives {@code 2}. It reflects where the probability mass sits on average, which
	 * suits ordered scales such as a sentiment or a severity, but it can land on a level
	 * the model finds unlikely when the probabilities are split between distant levels;
	 * use {@link #getNearestLevel()} for the level the model actually favors.
	 * @since 2.1.0
	 */
	public int getRoundedLevel() {
		return (int) Math.round(this.value);
	}

	@Override
	public boolean equals(@Nullable Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof ScoreAnswer that)) {
			return false;
		}
		return Double.compare(this.value, that.value) == 0 && this.probabilities.equals(that.probabilities)
				&& Objects.equals(this.confidence, that.confidence);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.value, this.probabilities, this.confidence);
	}

	@Override
	public String toString() {
		return "ScoreAnswer{value=" + this.value + ", probabilities=" + this.probabilities + ", confidence="
				+ this.confidence + "}";
	}

	public static final class Builder {

		private @Nullable Double value;

		private List<Double> probabilities = List.of();

		private @Nullable Double confidence;

		private Builder() {
		}

		public Builder value(double value) {
			this.value = value;
			return this;
		}

		public Builder probabilities(List<Double> probabilities) {
			this.probabilities = probabilities;
			return this;
		}

		public Builder confidence(@Nullable Double confidence) {
			this.confidence = confidence;
			return this;
		}

		public ScoreAnswer build() {
			Assert.state(this.value != null, "value cannot be null");
			return new ScoreAnswer(this.value, this.probabilities, this.confidence);
		}

	}

}
