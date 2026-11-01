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

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.decision.answer.YesNoAnswer;
import org.springframework.util.Assert;

/**
 * A question with a yes or no answer, answered with the probability of "yes" as a
 * {@link YesNoAnswer}. The optional criteria describe when the answer is yes and when it
 * is no. Providers whose API has a place for them send them as such, the others add them
 * to the question text.
 *
 * @author Thomas Vitale
 * @since 2.1.0
 */
public final class YesNoQuestion implements DecisionQuestion {

	private final String text;

	private final @Nullable String whenYes;

	private final @Nullable String whenNo;

	private YesNoQuestion(String text, @Nullable String whenYes, @Nullable String whenNo) {
		Assert.hasText(text, "text cannot be null or empty");
		this.text = text;
		this.whenYes = whenYes;
		this.whenNo = whenNo;
	}

	/**
	 * A question without criteria.
	 */
	public static YesNoQuestion of(String text) {
		return builder().text(text).build();
	}

	public static Builder builder() {
		return new Builder();
	}

	@Override
	public String getText() {
		return this.text;
	}

	/**
	 * When the answer is yes, or {@code null} when not specified.
	 */
	public @Nullable String getWhenYes() {
		return this.whenYes;
	}

	/**
	 * When the answer is no, or {@code null} when not specified.
	 */
	public @Nullable String getWhenNo() {
		return this.whenNo;
	}

	@Override
	public boolean equals(@Nullable Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof YesNoQuestion that)) {
			return false;
		}
		return this.text.equals(that.text) && Objects.equals(this.whenYes, that.whenYes)
				&& Objects.equals(this.whenNo, that.whenNo);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.text, this.whenYes, this.whenNo);
	}

	@Override
	public String toString() {
		return "YesNoQuestion{text='" + this.text + "', whenYes='" + this.whenYes + "', whenNo='" + this.whenNo + "'}";
	}

	public static final class Builder {

		private @Nullable String text;

		private @Nullable String whenYes;

		private @Nullable String whenNo;

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
		 * Describe when the answer is yes.
		 */
		public Builder whenYes(@Nullable String whenYes) {
			this.whenYes = whenYes;
			return this;
		}

		/**
		 * Describe when the answer is no.
		 */
		public Builder whenNo(@Nullable String whenNo) {
			this.whenNo = whenNo;
			return this;
		}

		public YesNoQuestion build() {
			Assert.state(this.text != null, "text cannot be null");
			return new YesNoQuestion(this.text, this.whenYes, this.whenNo);
		}

	}

}
