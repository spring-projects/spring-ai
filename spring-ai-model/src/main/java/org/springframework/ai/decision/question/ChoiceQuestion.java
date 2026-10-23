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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.decision.answer.ChoiceAnswer;
import org.springframework.util.Assert;

/**
 * A question answered by picking one of the offered options, as a {@link ChoiceAnswer}.
 * Each option has a name and, optionally, a description that tells the model when the
 * option applies.
 *
 * @author Thomas Vitale
 * @since 2.1.0
 */
public final class ChoiceQuestion implements DecisionQuestion {

	private final String text;

	private final Map<String, @Nullable String> options;

	private ChoiceQuestion(String text, Map<String, @Nullable String> options) {
		Assert.hasText(text, "text cannot be null or empty");
		Assert.notNull(options, "options cannot be null");
		Assert.isTrue(options.size() >= 2, "a choice question needs at least 2 options");
		options.keySet().forEach(option -> Assert.hasText(option, "option names cannot be null or empty"));
		this.text = text;
		this.options = Collections.unmodifiableMap(new LinkedHashMap<>(options));
	}

	/**
	 * A question whose options have no description.
	 */
	public static ChoiceQuestion of(String text, String... options) {
		return builder().text(text).options(options).build();
	}

	public static Builder builder() {
		return new Builder();
	}

	@Override
	public String getText() {
		return this.text;
	}

	/**
	 * The options, name to description, in the order they were declared. A description is
	 * {@code null} when the option has none.
	 */
	public Map<String, @Nullable String> getOptions() {
		return this.options;
	}

	/**
	 * The option names, in the order they were declared.
	 */
	public List<String> getOptionNames() {
		return List.copyOf(this.options.keySet());
	}

	@Override
	public boolean equals(@Nullable Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof ChoiceQuestion that)) {
			return false;
		}
		return this.text.equals(that.text) && this.options.equals(that.options);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.text, this.options);
	}

	@Override
	public String toString() {
		return "ChoiceQuestion{text='" + this.text + "', options=" + this.options + "}";
	}

	public static final class Builder {

		private @Nullable String text;

		private final Map<String, @Nullable String> options = new LinkedHashMap<>();

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
		 * Add an option.
		 */
		public Builder option(String name) {
			return option(name, null);
		}

		/**
		 * Add an option with a description.
		 */
		public Builder option(String name, @Nullable String description) {
			Assert.hasText(name, "option name cannot be null or empty");
			Assert.isTrue(!this.options.containsKey(name), () -> "duplicate option '" + name + "'");
			this.options.put(name, description);
			return this;
		}

		/**
		 * Add multiple options.
		 */
		public Builder options(Map<String, @Nullable String> options) {
			options.forEach(this::option);
			return this;
		}

		/**
		 * Add options without a description.
		 */
		public Builder options(String... names) {
			for (String name : names) {
				option(name);
			}
			return this;
		}

		public ChoiceQuestion build() {
			Assert.state(this.text != null, "text cannot be null");
			return new ChoiceQuestion(this.text, this.options);
		}

	}

}
