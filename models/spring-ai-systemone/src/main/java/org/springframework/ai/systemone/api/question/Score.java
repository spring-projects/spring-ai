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

package org.springframework.ai.systemone.api.question;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.systemone.api.JsonContent;
import org.springframework.util.Assert;

/**
 * A placement on an ordered rubric. Level {@code 0} is the first entry of
 * {@code criteria}, level {@code 1} the second, and so on. The answer is a
 * probability-weighted value across those levels, so it is continuous rather than an
 * index, and it comes with the per-level probabilities, a legend and a confidence
 * statistic.
 *
 * <pre>{@code
 * Score.builder()
 *     .instructions("How frustrated is the customer?")
 *     .level("Calm")
 *     .level("Frustrated")
 *     .level("Very angry")
 *     .build()
 * }</pre>
 *
 * @param instructions what the model is being asked
 * @param criteria the level descriptions, ordered from the lowest score upwards
 * @author Christian Tzolov
 * @since 2.1.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "type", "instructions", "criteria" })
public record Score(@JsonProperty("instructions") @Nullable JsonContent instructions,
		@JsonProperty("criteria") List<JsonContent> criteria) implements Question {

	public Score {
		Assert.notNull(criteria, "criteria must not be null");
		Assert.isTrue(criteria.size() >= 2, "criteria must declare at least two levels");
		criteria = Collections.unmodifiableList(new ArrayList<>(criteria));
	}

	/**
	 * Creates a score from plain text level descriptions.
	 * @param instructions the question to ask
	 * @param levels the level descriptions, ordered from the lowest score upwards
	 * @return a new score
	 */
	public static Score of(String instructions, String... levels) {
		Builder builder = builder().instructions(instructions);
		for (String level : levels) {
			builder.level(level);
		}
		return builder.build();
	}

	@Override
	public QuestionType type() {
		return QuestionType.SCORE;
	}

	/**
	 * @return the highest score this rubric can produce, that is
	 * {@code criteria.size() - 1}
	 */
	public int maxLevel() {
		return this.criteria.size() - 1;
	}

	/**
	 * Returns the description of a level.
	 * @param level the level index
	 * @return the description, or {@code null} when the level is out of range
	 */
	public @Nullable JsonContent levelAt(int level) {
		return (level < 0 || level >= this.criteria.size()) ? null : this.criteria.get(level);
	}

	/**
	 * @return a new builder
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builder for {@link Score}.
	 */
	public static final class Builder {

		private @Nullable JsonContent instructions;

		private final List<JsonContent> criteria = new ArrayList<>();

		private Builder() {
		}

		public Builder instructions(String instructions) {
			Assert.hasText(instructions, "instructions must not be empty");
			return instructions(JsonContent.of(instructions));
		}

		public Builder instructions(Map<String, ?> instructions) {
			Assert.notEmpty(instructions, "instructions must not be empty");
			return instructions(JsonContent.of(instructions));
		}

		public Builder instructions(List<?> instructions) {
			Assert.notEmpty(instructions, "instructions must not be empty");
			return instructions(JsonContent.of(instructions));
		}

		public Builder instructions(JsonContent instructions) {
			Assert.notNull(instructions, "instructions must not be null");
			this.instructions = instructions;
			return this;
		}

		/**
		 * Appends the next level of the rubric.
		 * @param description what this level means
		 * @return this builder
		 */
		public Builder level(String description) {
			Assert.hasText(description, "level description must not be empty");
			return level(JsonContent.of(description));
		}

		public Builder level(Map<String, ?> description) {
			Assert.notEmpty(description, "level description must not be empty");
			return level(JsonContent.of(description));
		}

		public Builder level(List<?> description) {
			Assert.notEmpty(description, "level description must not be empty");
			return level(JsonContent.of(description));
		}

		public Builder level(JsonContent description) {
			Assert.notNull(description, "level description must not be null");
			this.criteria.add(description);
			return this;
		}

		public Score build() {
			return new Score(this.instructions, this.criteria);
		}

	}
}
