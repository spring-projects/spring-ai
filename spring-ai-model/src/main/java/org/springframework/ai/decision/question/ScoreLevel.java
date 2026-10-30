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

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * One level of a {@link ScoreQuestion}: a label and, optionally, a description that tells
 * the model when the level applies. A blank description counts as none.
 *
 * @author Thomas Vitale
 * @since 2.1.0
 */
public final class ScoreLevel {

	private final String label;

	private final @Nullable String description;

	private ScoreLevel(String label, @Nullable String description) {
		Assert.hasText(label, "label cannot be null or empty");
		this.label = label;
		this.description = StringUtils.hasText(description) ? description : null;
	}

	public static Builder builder() {
		return new Builder();
	}

	public String getLabel() {
		return this.label;
	}

	/**
	 * When the level applies, or {@code null} when not specified or blank.
	 */
	public @Nullable String getDescription() {
		return this.description;
	}

	@Override
	public boolean equals(@Nullable Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof ScoreLevel that)) {
			return false;
		}
		return this.label.equals(that.label) && Objects.equals(this.description, that.description);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.label, this.description);
	}

	@Override
	public String toString() {
		return "ScoreLevel{label='" + this.label + "', description='" + this.description + "'}";
	}

	public static final class Builder {

		private @Nullable String label;

		private @Nullable String description;

		private Builder() {
		}

		/**
		 * The label of the level.
		 */
		public Builder label(String label) {
			this.label = label;
			return this;
		}

		/**
		 * When the level applies. A blank description counts as none.
		 */
		public Builder description(@Nullable String description) {
			this.description = description;
			return this;
		}

		public ScoreLevel build() {
			Assert.state(this.label != null, "label cannot be null");
			return new ScoreLevel(this.label, this.description);
		}

	}

}
