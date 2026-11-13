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
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.systemone.api.JsonContent;
import org.springframework.ai.systemone.api.question.Score;

/**
 * The answer to a {@link Score}: a probability-weighted value across the rubric levels,
 * the legend that names each level, the per-level probabilities and a confidence
 * statistic.
 *
 * <p>
 * The value is continuous. A {@code 1.6} on a three level rubric sits between
 * {@code "Frustrated"} and {@code "Very angry"}, closer to the latter, rather than
 * rounding to either.
 *
 * @param value the probability-weighted score
 * @param legend the description of each level, keyed by level index
 * @param probabilities the probability of each level, keyed by level index
 * @param confidence how concentrated the distribution is, between {@code 0} and {@code 1}
 * @author Christian Tzolov
 * @since 2.1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScoreAnswer(@JsonProperty("score") double value, @JsonProperty("legend") Map<Integer, JsonContent> legend,
		@JsonProperty("probabilities") Map<Integer, Double> probabilities,
		@JsonProperty("confidence") double confidence) implements Answer {

	public ScoreAnswer {
		legend = legend == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(legend));
		probabilities = probabilities == null ? Map.of()
				: Collections.unmodifiableMap(new LinkedHashMap<>(probabilities));
	}

	@Override
	public AnswerType type() {
		return AnswerType.SCORE;
	}

	/**
	 * @return the level carrying the highest probability, or {@code -1} when no
	 * probabilities were returned
	 */
	public int nearestLevel() {
		return this.probabilities.entrySet()
			.stream()
			.max(Map.Entry.comparingByValue())
			.map(Map.Entry::getKey)
			.orElse(-1);
	}

	/**
	 * Returns the legend description of a level.
	 * @param level the level index
	 * @return the description, or {@code null} when the legend does not name that level
	 */
	public @Nullable JsonContent labelOf(int level) {
		return this.legend.get(level);
	}

	/**
	 * @return the legend description of {@link #nearestLevel()} as display text, or an
	 * empty string when it is not available
	 */
	public String nearestLabel() {
		JsonContent label = labelOf(nearestLevel());
		return label == null ? "" : label.toDisplayString();
	}

	/**
	 * @return the highest level this rubric declares, or {@code -1} when the legend is
	 * empty
	 */
	public int maxLevel() {
		return this.legend.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1);
	}
}
