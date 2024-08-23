package org.springframework.ai.moderation;

import java.util.Objects;

/**
 * Represents the result of a moderation process, indicating whether content was flagged,
 * the categories of moderation, and detailed scores for each category. This class is
 * designed to be constructed via its Builder inner class.
 *
 * @author Ahmed Yousri
 * @since 1.0.0
 */
public class ModerationResult {

	private boolean flagged;

	private Categories categories;

	private CategoryScores categoryScores;

	private ModerationResult(Builder builder) {
		this.flagged = builder.flagged;
		this.categories = builder.categories;
		this.categoryScores = builder.categoryScores;
	}

	public boolean isFlagged() {
		return flagged;
	}

	public void setFlagged(boolean flagged) {
		this.flagged = flagged;
	}

	public Categories getCategories() {
		return categories;
	}

	public void setCategories(Categories categories) {
		this.categories = categories;
	}

	public CategoryScores getCategoryScores() {
		return categoryScores;
	}

	public void setCategoryScores(CategoryScores categoryScores) {
		this.categoryScores = categoryScores;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private boolean flagged;

		private Categories categories;

		private CategoryScores categoryScores;

		public Builder withFlagged(boolean flagged) {
			this.flagged = flagged;
			return this;
		}

		public Builder withCategories(Categories categories) {
			this.categories = categories;
			return this;
		}

		public Builder withCategoryScores(CategoryScores categoryScores) {
			this.categoryScores = categoryScores;
			return this;
		}

		public ModerationResult build() {
			return new ModerationResult(this);
		}

	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof ModerationResult))
			return false;
		ModerationResult that = (ModerationResult) o;
		return flagged == that.flagged && Objects.equals(categories, that.categories)
				&& Objects.equals(categoryScores, that.categoryScores);
	}

	@Override
	public int hashCode() {
		return Objects.hash(flagged, categories, categoryScores);
	}

	@Override
	public String toString() {
		return "ModerationResult{" + "flagged=" + flagged + ", categories=" + categories + ", categoryScores="
				+ categoryScores + '}';
	}

}
