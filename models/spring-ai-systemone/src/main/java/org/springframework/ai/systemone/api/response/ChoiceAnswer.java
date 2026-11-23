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

package org.springframework.ai.systemone.api.response;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.ai.systemone.api.question.Choice;

/**
 * The answer to a {@link Choice}: the selected label, the probability of every option and
 * a confidence statistic computed from how concentrated that distribution is. A flat
 * distribution means the options were not well separated for this state.
 *
 * @param value the selected option label
 * @param probabilities the probability of each option, keyed by label
 * @param confidence how concentrated the distribution is, between {@code 0} and {@code 1}
 * @author Christian Tzolov
 * @since 2.1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChoiceAnswer(@JsonProperty("choice") String value,
		@JsonProperty("probabilities") Map<String, Double> probabilities,
		@JsonProperty("confidence") double confidence) implements Answer {

	public ChoiceAnswer {
		probabilities = probabilities == null ? Map.of()
				: Collections.unmodifiableMap(new LinkedHashMap<>(probabilities));
	}

	@Override
	public AnswerType type() {
		return AnswerType.CHOICE;
	}

	/**
	 * @param option the option label
	 * @return the probability assigned to the option, or {@code 0} when it is not present
	 */
	public double probabilityOf(String option) {
		return this.probabilities.getOrDefault(option, 0.0d);
	}

	/**
	 * Returns the options whose probability is at least {@code threshold}, most likely
	 * first. Useful for the fan-out pattern, where a close split is explored along
	 * several branches instead of committing to the top option.
	 * @param threshold the inclusive lower probability bound
	 * @return the matching option labels, ordered by descending probability
	 */
	public List<String> optionsAbove(double threshold) {
		return this.probabilities.entrySet()
			.stream()
			.filter(entry -> entry.getValue() >= threshold)
			.sorted(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder()))
			.map(Map.Entry::getKey)
			.toList();
	}
}
