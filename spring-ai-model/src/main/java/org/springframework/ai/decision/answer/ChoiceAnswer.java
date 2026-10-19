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

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.decision.question.ChoiceQuestion;
import org.springframework.util.Assert;

/**
 * The answer to a {@link ChoiceQuestion}: the selected option, the probability of each
 * option when the provider reports them, and the provider's confidence when it reports
 * one. Probabilities are what the model assigned to each option. Confidence is the
 * provider's own estimate and is not a probability of correctness.
 *
 * @author Thomas Vitale
 * @since 2.1.0
 */
public final class ChoiceAnswer implements DecisionAnswer {

	private final String value;

	private final Map<String, Double> probabilities;

	private final @Nullable Double confidence;

	private ChoiceAnswer(String value, Map<String, Double> probabilities, @Nullable Double confidence) {
		Assert.hasText(value, "value cannot be null or empty");
		Assert.notNull(probabilities, "probabilities cannot be null");
		this.value = value;
		Map<String, Double> checked = new LinkedHashMap<>();
		probabilities.forEach((option, probability) -> {
			Assert.isTrue(probability >= 0 && probability <= 1,
					() -> "the probability of '" + option + "' must be between 0 and 1, but was " + probability);
			checked.put(option, probability);
		});
		this.probabilities = Collections.unmodifiableMap(checked);
		Assert.isTrue(confidence == null || (confidence >= 0 && confidence <= 1),
				() -> "confidence must be between 0 and 1, but was " + confidence);
		this.confidence = confidence;
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * The selected option.
	 */
	public String getValue() {
		return this.value;
	}

	/**
	 * The probability of the selected option, or {@code null} when not reported.
	 */
	public @Nullable Double getProbability() {
		return this.probabilities.get(this.value);
	}

	/**
	 * The probability of the given option, or {@code null} when not reported.
	 */
	public @Nullable Double getProbability(String option) {
		return this.probabilities.get(option);
	}

	/**
	 * The probability of each option, or an empty map when the provider doesn't report
	 * them.
	 */
	public Map<String, Double> getProbabilities() {
		return this.probabilities;
	}

	/**
	 * The options from the most to the least likely, or an empty list when probabilities
	 * weren't reported. Ties keep the reported order.
	 */
	public List<String> getRankedOptions() {
		return this.probabilities.entrySet()
			.stream()
			.sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
			.map(Map.Entry::getKey)
			.toList();
	}

	/**
	 * The provider's confidence in the selected option, or {@code null} when the provider
	 * doesn't report one.
	 */
	public @Nullable Double getConfidence() {
		return this.confidence;
	}

	@Override
	public boolean equals(@Nullable Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof ChoiceAnswer that)) {
			return false;
		}
		return this.value.equals(that.value) && this.probabilities.equals(that.probabilities)
				&& Objects.equals(this.confidence, that.confidence);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.value, this.probabilities, this.confidence);
	}

	@Override
	public String toString() {
		return "ChoiceAnswer{value='" + this.value + "', probabilities=" + this.probabilities + ", confidence="
				+ this.confidence + "}";
	}

	public static final class Builder {

		private @Nullable String value;

		private Map<String, Double> probabilities = Map.of();

		private @Nullable Double confidence;

		private Builder() {
		}

		public Builder value(String value) {
			this.value = value;
			return this;
		}

		public Builder probabilities(Map<String, Double> probabilities) {
			this.probabilities = probabilities;
			return this;
		}

		public Builder confidence(@Nullable Double confidence) {
			this.confidence = confidence;
			return this;
		}

		public ChoiceAnswer build() {
			Assert.state(this.value != null, "value cannot be null");
			return new ChoiceAnswer(this.value, this.probabilities, this.confidence);
		}

	}

}
