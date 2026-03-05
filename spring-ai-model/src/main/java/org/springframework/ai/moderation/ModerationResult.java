/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.moderation;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * Represents the result of a moderation process, indicating whether content was flagged,
 * the categories of moderation, and detailed scores for each category. This class is
 * designed to be constructed via its Builder inner class.
 *
 * @author Ahmed Yousri
 * @since 1.0.0
 */
public final class ModerationResult {

	private boolean flagged;

	private @Nullable Categories categories;

	private @Nullable CategoryScores categoryScores;

	private ModerationResult(Builder builder) {
		this.flagged = builder.flagged;
		this.categories = builder.categories;
		this.categoryScores = builder.categoryScores;
	}

	public static Builder builder() {
		return new Builder();
	}

	public boolean isFlagged() {
		return this.flagged;
	}

	public void setFlagged(boolean flagged) {
		this.flagged = flagged;
	}

	public @Nullable Categories getCategories() {
		return this.categories;
	}

	public void setCategories(Categories categories) {
		this.categories = categories;
	}

	public @Nullable CategoryScores getCategoryScores() {
		return this.categoryScores;
	}

	public void setCategoryScores(CategoryScores categoryScores) {
		this.categoryScores = categoryScores;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof ModerationResult that)) {
			return false;
		}
		return this.flagged == that.flagged && Objects.equals(this.categories, that.categories)
				&& Objects.equals(this.categoryScores, that.categoryScores);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.flagged, this.categories, this.categoryScores);
	}

	@Override
	public String toString() {
		return "ModerationResult{" + "flagged=" + this.flagged + ", categories=" + this.categories + ", categoryScores="
				+ this.categoryScores + '}';
	}

	public static final class Builder {

		private boolean flagged;

		private @Nullable Categories categories;

		private @Nullable CategoryScores categoryScores;

		public Builder flagged(boolean flagged) {
			this.flagged = flagged;
			return this;
		}

		public Builder categories(Categories categories) {
			this.categories = categories;
			return this;
		}

		public Builder categoryScores(CategoryScores categoryScores) {
			this.categoryScores = categoryScores;
			return this;
		}

		public ModerationResult build() {
			return new ModerationResult(this);
		}

	}

}
