package org.springframework.ai.moderation;

import java.util.Objects;

/**
 * The Categories class represents a set of categories used to classify content. Each
 * category can be either true (indicating that the content belongs to the category) or
 * false (indicating that the content does not belong to the category).
 *
 * @author Ahmed Yousri
 * @since 1.0.0
 */
public class Categories {

	private boolean sexual;

	private boolean hate;

	private boolean harassment;

	private boolean selfHarm;

	private boolean sexualMinors;

	private boolean hateThreatening;

	private boolean violenceGraphic;

	private boolean selfHarmIntent;

	private boolean selfHarmInstructions;

	private boolean harassmentThreatening;

	private boolean violence;

	private Categories(Builder builder) {
		this.sexual = builder.sexual;
		this.hate = builder.hate;
		this.harassment = builder.harassment;
		this.selfHarm = builder.selfHarm;
		this.sexualMinors = builder.sexualMinors;
		this.hateThreatening = builder.hateThreatening;
		this.violenceGraphic = builder.violenceGraphic;
		this.selfHarmIntent = builder.selfHarmIntent;
		this.selfHarmInstructions = builder.selfHarmInstructions;
		this.harassmentThreatening = builder.harassmentThreatening;
		this.violence = builder.violence;
	}

	public boolean isSexual() {
		return sexual;
	}

	public boolean isHate() {
		return hate;
	}

	public boolean isHarassment() {
		return harassment;
	}

	public boolean isSelfHarm() {
		return selfHarm;
	}

	public boolean isSexualMinors() {
		return sexualMinors;
	}

	public boolean isHateThreatening() {
		return hateThreatening;
	}

	public boolean isViolenceGraphic() {
		return violenceGraphic;
	}

	public boolean isSelfHarmIntent() {
		return selfHarmIntent;
	}

	public boolean isSelfHarmInstructions() {
		return selfHarmInstructions;
	}

	public boolean isHarassmentThreatening() {
		return harassmentThreatening;
	}

	public boolean isViolence() {
		return violence;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private boolean sexual;

		private boolean hate;

		private boolean harassment;

		private boolean selfHarm;

		private boolean sexualMinors;

		private boolean hateThreatening;

		private boolean violenceGraphic;

		private boolean selfHarmIntent;

		private boolean selfHarmInstructions;

		private boolean harassmentThreatening;

		private boolean violence;

		public Builder withSexual(boolean sexual) {
			this.sexual = sexual;
			return this;
		}

		public Builder withHate(boolean hate) {
			this.hate = hate;
			return this;
		}

		public Builder withHarassment(boolean harassment) {
			this.harassment = harassment;
			return this;
		}

		public Builder withSelfHarm(boolean selfHarm) {
			this.selfHarm = selfHarm;
			return this;
		}

		public Builder withSexualMinors(boolean sexualMinors) {
			this.sexualMinors = sexualMinors;
			return this;
		}

		public Builder withHateThreatening(boolean hateThreatening) {
			this.hateThreatening = hateThreatening;
			return this;
		}

		public Builder withViolenceGraphic(boolean violenceGraphic) {
			this.violenceGraphic = violenceGraphic;
			return this;
		}

		public Builder withSelfHarmIntent(boolean selfHarmIntent) {
			this.selfHarmIntent = selfHarmIntent;
			return this;
		}

		public Builder withSelfHarmInstructions(boolean selfHarmInstructions) {
			this.selfHarmInstructions = selfHarmInstructions;
			return this;
		}

		public Builder withHarassmentThreatening(boolean harassmentThreatening) {
			this.harassmentThreatening = harassmentThreatening;
			return this;
		}

		public Builder withViolence(boolean violence) {
			this.violence = violence;
			return this;
		}

		public Categories build() {
			return new Categories(this);
		}

	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof Categories))
			return false;
		Categories that = (Categories) o;
		return sexual == that.sexual && hate == that.hate && harassment == that.harassment && selfHarm == that.selfHarm
				&& sexualMinors == that.sexualMinors && hateThreatening == that.hateThreatening
				&& violenceGraphic == that.violenceGraphic && selfHarmIntent == that.selfHarmIntent
				&& selfHarmInstructions == that.selfHarmInstructions
				&& harassmentThreatening == that.harassmentThreatening && violence == that.violence;
	}

	@Override
	public int hashCode() {
		return Objects.hash(sexual, hate, harassment, selfHarm, sexualMinors, hateThreatening, violenceGraphic,
				selfHarmIntent, selfHarmInstructions, harassmentThreatening, violence);
	}

	@Override
	public String toString() {
		return "Categories{" + "sexual=" + sexual + ", hate=" + hate + ", harassment=" + harassment + ", selfHarm="
				+ selfHarm + ", sexualMinors=" + sexualMinors + ", hateThreatening=" + hateThreatening
				+ ", violenceGraphic=" + violenceGraphic + ", selfHarmIntent=" + selfHarmIntent
				+ ", selfHarmInstructions=" + selfHarmInstructions + ", harassmentThreatening=" + harassmentThreatening
				+ ", violence=" + violence + '}';
	}

}
