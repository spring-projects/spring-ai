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

package org.springframework.ai.decision.question;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.decision.answer.ScoreAnswer;
import org.springframework.util.Assert;

/**
 * A question answered on an ordered scale of levels, as a {@link ScoreAnswer}. The levels
 * are ordered from the lowest to the highest, and a level's position is its value: the
 * first level is 0, the last one is {@code levels.size() - 1}.
 *
 * @author Thomas Vitale
 * @since 2.1.0
 */
public final class ScoreQuestion implements DecisionQuestion {

	private final String text;

	private final List<ScoreLevel> levels;

	private ScoreQuestion(String text, List<ScoreLevel> levels) {
		Assert.hasText(text, "text cannot be null or empty");
		Assert.notNull(levels, "levels cannot be null");
		Assert.isTrue(levels.size() >= 2, "a score question needs at least 2 levels");
		this.text = text;
		this.levels = List.copyOf(levels);
	}

	/**
	 * A question whose levels have no description, from the lowest to the highest.
	 */
	public static ScoreQuestion of(String text, String... labels) {
		return builder().text(text).levels(labels).build();
	}

	public static Builder builder() {
		return new Builder();
	}

	@Override
	public String getText() {
		return this.text;
	}

	/**
	 * The levels, from the lowest to the highest.
	 */
	public List<ScoreLevel> getLevels() {
		return this.levels;
	}

	/**
	 * The level labels, from the lowest to the highest.
	 */
	public List<String> getLevelLabels() {
		return this.levels.stream().map(ScoreLevel::getLabel).toList();
	}

	@Override
	public boolean equals(@Nullable Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof ScoreQuestion that)) {
			return false;
		}
		return this.text.equals(that.text) && this.levels.equals(that.levels);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.text, this.levels);
	}

	@Override
	public String toString() {
		return "ScoreQuestion{text='" + this.text + "', levels=" + this.levels + "}";
	}

	public static final class Builder {

		private @Nullable String text;

		private final List<ScoreLevel> levels = new ArrayList<>();

		private Builder() {
		}

		/**
		 * The question text.
		 */
		public Builder text(String text) {
			this.text = text;
			return this;
		}

		/**
		 * Add a level.
		 */
		public Builder level(String label) {
			return level(label, null);
		}

		/**
		 * Add a level with a description.
		 */
		public Builder level(String label, @Nullable String description) {
			this.levels.add(ScoreLevel.builder().label(label).description(description).build());
			return this;
		}

		/**
		 * Add multiple levels.
		 */
		public Builder levels(List<ScoreLevel> levels) {
			this.levels.addAll(levels);
			return this;
		}

		/**
		 * Add levels without a description, from the lowest to the highest.
		 */
		public Builder levels(String... labels) {
			for (String label : labels) {
				level(label);
			}
			return this;
		}

		public ScoreQuestion build() {
			Assert.state(this.text != null, "text cannot be null");
			return new ScoreQuestion(this.text, this.levels);
		}

	}

}
