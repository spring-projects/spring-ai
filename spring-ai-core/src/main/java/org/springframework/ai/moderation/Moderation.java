package org.springframework.ai.moderation;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * The Moderation class represents the result of a moderation process. It contains the
 * moderation ID, model, and a list of moderation results. To create an instance of
 * Moderation, use the Builder class.
 *
 * @author Ahmed Yousri
 * @since 1.0.0
 */
public class Moderation {

	private final String id;

	private final String model;

	private final List<ModerationResult> results;

	private Moderation(Builder builder) {
		this.id = builder.id;
		this.model = builder.model;
		this.results = builder.moderationResultList;
	}

	public String getId() {
		return id;
	}

	public String getModel() {
		return model;
	}

	public List<ModerationResult> getResults() {
		return results;
	}

	@Override
	public String toString() {
		return "Moderation{" + "id='" + id + '\'' + ", model='" + model + '\'' + ", results="
				+ Arrays.toString(results.toArray()) + '}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof Moderation))
			return false;
		Moderation that = (Moderation) o;
		return Objects.equals(id, that.id) && Objects.equals(model, that.model)
				&& Objects.equals(results, that.results);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, model, results);
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private String id;

		private String model;

		private List<ModerationResult> moderationResultList;

		public Builder withId(String id) {
			this.id = id;
			return this;
		}

		public Builder withModel(String model) {
			this.model = model;
			return this;
		}

		public Builder withResults(List<ModerationResult> results) {
			this.moderationResultList = results;
			return this;
		}

		public Moderation build() {
			return new Moderation(this);
		}

	}

}
