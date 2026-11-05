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

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.systemone.api.JsonContent;
import org.springframework.util.Assert;

/**
 * A binary truth assessment. The answer is a single value between {@code 0} and {@code 1}
 * where {@code 1} means yes, {@code 0} means no and {@code 0.5} means undecided. Nouls
 * carry no confidence statistic, so they are thresholded on the value itself.
 *
 * <pre>{@code
 * Noul.of("Does this convey urgency?")
 *
 * Noul.builder()
 *     .instructions("Does this convey urgency?")
 *     .whenTrue("Explicitly time-sensitive")
 *     .whenFalse("No urgency expressed")
 *     .build()
 * }</pre>
 *
 * @param instructions what the model is being asked
 * @param criteria optional descriptions of the two outcomes
 * @author Christian Tzolov
 * @since 2.1.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "type", "instructions", "criteria" })
public record Noul(@JsonProperty("instructions") @Nullable JsonContent instructions,
		@JsonProperty("criteria") @Nullable NoulCriteria criteria) implements Question {

	@Override
	public QuestionType type() {
		return QuestionType.NOUL;
	}

	/**
	 * Creates a noul from plain text instructions.
	 * @param instructions the question to ask
	 * @return a new noul without criteria
	 */
	public static Noul of(String instructions) {
		Assert.hasText(instructions, "instructions must not be empty");
		return new Noul(JsonContent.of(instructions), null);
	}

	/**
	 * Creates a noul from structured instructions.
	 * @param instructions the question to ask, as a JSON object
	 * @return a new noul without criteria
	 */
	public static Noul of(Map<String, ?> instructions) {
		Assert.notEmpty(instructions, "instructions must not be empty");
		return new Noul(JsonContent.of(instructions), null);
	}

	/**
	 * @return a new builder
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builder for {@link Noul}.
	 */
	public static final class Builder {

		private @Nullable JsonContent instructions;

		private @Nullable JsonContent whenTrue;

		private @Nullable JsonContent whenFalse;

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

		public Builder whenTrue(String description) {
			Assert.hasText(description, "description must not be empty");
			return whenTrue(JsonContent.of(description));
		}

		public Builder whenTrue(Map<String, ?> description) {
			Assert.notEmpty(description, "description must not be empty");
			return whenTrue(JsonContent.of(description));
		}

		public Builder whenTrue(JsonContent description) {
			Assert.notNull(description, "description must not be null");
			this.whenTrue = description;
			return this;
		}

		public Builder whenFalse(String description) {
			Assert.hasText(description, "description must not be empty");
			return whenFalse(JsonContent.of(description));
		}

		public Builder whenFalse(Map<String, ?> description) {
			Assert.notEmpty(description, "description must not be empty");
			return whenFalse(JsonContent.of(description));
		}

		public Builder whenFalse(JsonContent description) {
			Assert.notNull(description, "description must not be null");
			this.whenFalse = description;
			return this;
		}

		public Noul build() {
			// The API rejects a noul with neither instructions nor criteria; fail here
			// rather than as a 400 on the wire.
			Assert.isTrue(this.instructions != null || this.whenTrue != null || this.whenFalse != null,
					"a noul needs instructions or criteria (whenTrue/whenFalse)");
			NoulCriteria criteria = (this.whenTrue == null && this.whenFalse == null) ? null
					: new NoulCriteria(this.whenTrue, this.whenFalse);
			return new Noul(this.instructions, criteria);
		}

	}
}
