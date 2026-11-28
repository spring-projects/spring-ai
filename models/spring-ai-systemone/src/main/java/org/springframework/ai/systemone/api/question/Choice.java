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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.systemone.api.JsonContent;
import org.springframework.util.Assert;

/**
 * A selection of exactly one label out of a set. The answer carries the selected label,
 * the probability of every option and a confidence statistic derived from how
 * concentrated that distribution is.
 *
 * <pre>{@code
 * Choice.builder()
 *     .instructions("Which team should handle this?")
 *     .option("billing", "Payments, invoicing, refunds")
 *     .option("technical", "Bugs, outages, integrations")
 *     .option("sales", "Pricing, upgrades, new accounts")
 *     .build()
 * }</pre>
 *
 * Option descriptions may themselves be structured, which is the way to sharpen the
 * boundary between options, and they may be {@code null} when the label speaks for
 * itself.
 *
 * @param instructions what the model is being asked
 * @param criteria the options, in declaration order, mapped to their descriptions
 * @author Christian Tzolov
 * @since 2.1.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "type", "instructions", "criteria" })
public record Choice(@JsonProperty("instructions") @Nullable JsonContent instructions,
		@JsonProperty("criteria") Map<String, JsonContent> criteria) implements Question {

	public Choice {
		Assert.notEmpty(criteria, "criteria must declare at least one option");
		criteria = Collections.unmodifiableMap(new LinkedHashMap<>(criteria));
	}

	@Override
	public QuestionType type() {
		return QuestionType.CHOICE;
	}

	/**
	 * Creates a choice over undescribed labels.
	 * @param instructions the question to ask
	 * @param options the option labels, in the order they should be presented
	 * @return a new choice
	 */
	public static Choice of(String instructions, String... options) {
		Builder builder = builder().instructions(instructions);
		for (String option : options) {
			builder.option(option);
		}
		return builder.build();
	}

	/**
	 * @return a new builder
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builder for {@link Choice}.
	 */
	public static final class Builder {

		private @Nullable JsonContent instructions;

		private final Map<String, JsonContent> criteria = new LinkedHashMap<>();

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
		 * Adds an option whose label is its own description.
		 * @param label the option label
		 * @return this builder
		 */
		public Builder option(String label) {
			return option(label, JsonContent.NULL);
		}

		public Builder option(String label, String description) {
			return option(label, JsonContent.of(description));
		}

		public Builder option(String label, Map<String, ?> description) {
			return option(label, JsonContent.of(description));
		}

		public Builder option(String label, List<?> description) {
			return option(label, JsonContent.of(description));
		}

		public Builder option(String label, JsonContent description) {
			Assert.hasText(label, "option label must not be empty");
			Assert.notNull(description, "option description must not be null, use JsonContent.NULL instead");
			this.criteria.put(label, description);
			return this;
		}

		/**
		 * Adds every entry of the given map as an option, preserving its iteration order.
		 * @param options the options to add
		 * @return this builder
		 */
		public Builder options(Map<String, ?> options) {
			Assert.notEmpty(options, "options must not be empty");
			options.forEach((label, description) -> option(label, JsonContent.of(description)));
			return this;
		}

		public Choice build() {
			return new Choice(this.instructions, this.criteria);
		}

	}
}
