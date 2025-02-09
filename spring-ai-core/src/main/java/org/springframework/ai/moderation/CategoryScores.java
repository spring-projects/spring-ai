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

/**
 * This class represents the scores for different categories of content. Each category has
 * a score ranging from 0.0 to 1.0. The scores represent the severity or intensity of the
 * content in each respective category.
 *
 * @author Ahmed Yousri
 * @author Ilayaperumal Gopinathan
 * @author Ricken Bazolo
 * @since 1.0.0
 */
public final class CategoryScores {

	private final double sexual;

	private final double hate;

	private final double harassment;

	private final double selfHarm;

	private final double sexualMinors;

	private final double hateThreatening;

	private final double violenceGraphic;

	private final double selfHarmIntent;

	private final double selfHarmInstructions;

	private final double harassmentThreatening;

	private final double violence;

	private final double dangerousAndCriminalContent;

	private final double health;

	private final double financial;

	private final double law;

	private final double pii;

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
		this.dangerousAndCriminalContent = builder.dangerousAndCriminalContent;
		this.health = builder.health;
		this.financial = builder.financial;
		this.law = builder.law;
		this.pii = builder.pii;
	}

	public static Builder builder() {
		return new Builder();
	}

	public double getSexual() {
		return this.sexual;
	}

	public double getHate() {
		return this.hate;
	}

	public double getHarassment() {
		return this.harassment;
	}

	public double getSelfHarm() {
		return this.selfHarm;
	}

	public double getSexualMinors() {
		return this.sexualMinors;
	}

	public double getHateThreatening() {
		return this.hateThreatening;
	}

	public double getViolenceGraphic() {
		return this.violenceGraphic;
	}

	public double getSelfHarmIntent() {
		return this.selfHarmIntent;
	}

	public double getSelfHarmInstructions() {
		return this.selfHarmInstructions;
	}

	public double getHarassmentThreatening() {
		return this.harassmentThreatening;
	}

	public double getViolence() {
		return this.violence;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof CategoryScores)) {
			return false;
		}
		CategoryScores that = (CategoryScores) o;
		return Double.compare(that.sexual, this.sexual) == 0 && Double.compare(that.hate, this.hate) == 0
				&& Double.compare(that.harassment, this.harassment) == 0
				&& Double.compare(that.selfHarm, this.selfHarm) == 0
				&& Double.compare(that.sexualMinors, this.sexualMinors) == 0
				&& Double.compare(that.hateThreatening, this.hateThreatening) == 0
				&& Double.compare(that.violenceGraphic, this.violenceGraphic) == 0
				&& Double.compare(that.selfHarmIntent, this.selfHarmIntent) == 0
				&& Double.compare(that.selfHarmInstructions, this.selfHarmInstructions) == 0
				&& Double.compare(that.harassmentThreatening, this.harassmentThreatening) == 0
				&& Double.compare(that.violence, this.violence) == 0;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.sexual, this.hate, this.harassment, this.selfHarm, this.sexualMinors,
				this.hateThreatening, this.violenceGraphic, this.selfHarmIntent, this.selfHarmInstructions,
				this.harassmentThreatening, this.violence);
	}

	@Override
	public String toString() {
		return "CategoryScores{" + "sexual=" + this.sexual + ", hate=" + this.hate + ", harassment=" + this.harassment
				+ ", selfHarm=" + this.selfHarm + ", sexualMinors=" + this.sexualMinors + ", hateThreatening="
				+ this.hateThreatening + ", violenceGraphic=" + this.violenceGraphic + ", selfHarmIntent="
				+ this.selfHarmIntent + ", selfHarmInstructions=" + this.selfHarmInstructions
				+ ", harassmentThreatening=" + this.harassmentThreatening + ", violence=" + this.violence + '}';
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

		private double dangerousAndCriminalContent;

		private double health;

		private double financial;

		private double law;

		private double pii;

		public Builder sexual(double sexual) {
			this.sexual = sexual;
			return this;
		}

		public Builder hate(double hate) {
			this.hate = hate;
			return this;
		}

		public Builder harassment(double harassment) {
			this.harassment = harassment;
			return this;
		}

		public Builder selfHarm(double selfHarm) {
			this.selfHarm = selfHarm;
			return this;
		}

		public Builder sexualMinors(double sexualMinors) {
			this.sexualMinors = sexualMinors;
			return this;
		}

		public Builder hateThreatening(double hateThreatening) {
			this.hateThreatening = hateThreatening;
			return this;
		}

		public Builder violenceGraphic(double violenceGraphic) {
			this.violenceGraphic = violenceGraphic;
			return this;
		}

		public Builder selfHarmIntent(double selfHarmIntent) {
			this.selfHarmIntent = selfHarmIntent;
			return this;
		}

		public Builder selfHarmInstructions(double selfHarmInstructions) {
			this.selfHarmInstructions = selfHarmInstructions;
			return this;
		}

		public Builder harassmentThreatening(double harassmentThreatening) {
			this.harassmentThreatening = harassmentThreatening;
			return this;
		}

		public Builder violence(double violence) {
			this.violence = violence;
			return this;
		}

		public Builder dangerousAndCriminalContent(double dangerousAndCriminalContent) {
			this.dangerousAndCriminalContent = dangerousAndCriminalContent;
			return this;
		}

		public Builder health(double health) {
			this.health = health;
			return this;
		}

		public Builder financial(double financial) {
			this.financial = financial;
			return this;
		}

		public Builder law(double law) {
			this.law = law;
			return this;
		}

		public Builder pii(double pii) {
			this.pii = pii;
			return this;
		}

		public CategoryScores build() {
			return new CategoryScores(this);
		}

	}

}
