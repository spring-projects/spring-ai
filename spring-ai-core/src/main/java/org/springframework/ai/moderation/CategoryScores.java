package org.springframework.ai.moderation;

import java.util.Objects;

/**
 * This class represents the scores for different categories of content. Each category has
 * a score ranging from 0.0 to 1.0. The scores represent the severity or intensity of the
 * content in each respective category.
 *
 * @author Ahmed Yousri
 * @since 1.0.0
 */
public class CategoryScores {

	private double sexual;

	private double hate;

	private double harassment;

	private double selfHarm;

	private double sexualMinors;

	private double hateThreatening;

	private double violenceGraphic;

	private double selfHarmIntent;

	private double selfHarmInstructions;

	private double harassmentThreatening;

	private double violence;

	private CategoryScores(Builder builder) {
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

	public double getSexual() {
		return sexual;
	}

	public double getHate() {
		return hate;
	}

	public double getHarassment() {
		return harassment;
	}

	public double getSelfHarm() {
		return selfHarm;
	}

	public double getSexualMinors() {
		return sexualMinors;
	}

	public double getHateThreatening() {
		return hateThreatening;
	}

	public double getViolenceGraphic() {
		return violenceGraphic;
	}

	public double getSelfHarmIntent() {
		return selfHarmIntent;
	}

	public double getSelfHarmInstructions() {
		return selfHarmInstructions;
	}

	public double getHarassmentThreatening() {
		return harassmentThreatening;
	}

	public double getViolence() {
		return violence;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private double sexual;

		private double hate;

		private double harassment;

		private double selfHarm;

		private double sexualMinors;

		private double hateThreatening;

		private double violenceGraphic;

		private double selfHarmIntent;

		private double selfHarmInstructions;

		private double harassmentThreatening;

		private double violence;

		public Builder withSexual(double sexual) {
			this.sexual = sexual;
			return this;
		}

		public Builder withHate(double hate) {
			this.hate = hate;
			return this;
		}

		public Builder withHarassment(double harassment) {
			this.harassment = harassment;
			return this;
		}

		public Builder withSelfHarm(double selfHarm) {
			this.selfHarm = selfHarm;
			return this;
		}

		public Builder withSexualMinors(double sexualMinors) {
			this.sexualMinors = sexualMinors;
			return this;
		}

		public Builder withHateThreatening(double hateThreatening) {
			this.hateThreatening = hateThreatening;
			return this;
		}

		public Builder withViolenceGraphic(double violenceGraphic) {
			this.violenceGraphic = violenceGraphic;
			return this;
		}

		public Builder withSelfHarmIntent(double selfHarmIntent) {
			this.selfHarmIntent = selfHarmIntent;
			return this;
		}

		public Builder withSelfHarmInstructions(double selfHarmInstructions) {
			this.selfHarmInstructions = selfHarmInstructions;
			return this;
		}

		public Builder withHarassmentThreatening(double harassmentThreatening) {
			this.harassmentThreatening = harassmentThreatening;
			return this;
		}

		public Builder withViolence(double violence) {
			this.violence = violence;
			return this;
		}

		public CategoryScores build() {
			return new CategoryScores(this);
		}

	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof CategoryScores))
			return false;
		CategoryScores that = (CategoryScores) o;
		return Double.compare(that.sexual, sexual) == 0 && Double.compare(that.hate, hate) == 0
				&& Double.compare(that.harassment, harassment) == 0 && Double.compare(that.selfHarm, selfHarm) == 0
				&& Double.compare(that.sexualMinors, sexualMinors) == 0
				&& Double.compare(that.hateThreatening, hateThreatening) == 0
				&& Double.compare(that.violenceGraphic, violenceGraphic) == 0
				&& Double.compare(that.selfHarmIntent, selfHarmIntent) == 0
				&& Double.compare(that.selfHarmInstructions, selfHarmInstructions) == 0
				&& Double.compare(that.harassmentThreatening, harassmentThreatening) == 0
				&& Double.compare(that.violence, violence) == 0;
	}

	@Override
	public int hashCode() {
		return Objects.hash(sexual, hate, harassment, selfHarm, sexualMinors, hateThreatening, violenceGraphic,
				selfHarmIntent, selfHarmInstructions, harassmentThreatening, violence);
	}

	@Override
	public String toString() {
		return "CategoryScores{" + "sexual=" + sexual + ", hate=" + hate + ", harassment=" + harassment + ", selfHarm="
				+ selfHarm + ", sexualMinors=" + sexualMinors + ", hateThreatening=" + hateThreatening
				+ ", violenceGraphic=" + violenceGraphic + ", selfHarmIntent=" + selfHarmIntent
				+ ", selfHarmInstructions=" + selfHarmInstructions + ", harassmentThreatening=" + harassmentThreatening
				+ ", violence=" + violence + '}';
	}

}
