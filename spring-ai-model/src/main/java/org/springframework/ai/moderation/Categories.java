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
 * The Categories class represents a set of categories used to classify content. Each
 * category can be either true (indicating that the content belongs to the category) or
 * false (indicating that the content does not belong to the category).
 *
 * @author Ahmed Yousri
 * @author Ilayaperumal Gopinathan
 * @author Ricken Bazolo
 * @since 1.0.0
 */
public final class Categories {

	private final boolean sexual;

	private final boolean hate;

	private final boolean harassment;

	private final boolean selfHarm;

	private final boolean sexualMinors;

	private final boolean hateThreatening;

	private final boolean violenceGraphic;

	private final boolean selfHarmIntent;

	private final boolean selfHarmInstructions;

	private final boolean harassmentThreatening;

	private final boolean violence;

	private final boolean dangerousAndCriminalContent;

	private final boolean health;

	private final boolean financial;

	private final boolean law;

	private final boolean pii;

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
		this.dangerousAndCriminalContent = builder.dangerousAndCriminalContent;
		this.health = builder.health;
		this.financial = builder.financial;
		this.law = builder.law;
		this.pii = builder.pii;
	}

	public static Builder builder() {
		return new Builder();
	}

	public boolean isSexual() {
		return this.sexual;
	}

	public boolean isHate() {
		return this.hate;
	}

	public boolean isHarassment() {
		return this.harassment;
	}

	public boolean isSelfHarm() {
		return this.selfHarm;
	}

	public boolean isSexualMinors() {
		return this.sexualMinors;
	}

	public boolean isHateThreatening() {
		return this.hateThreatening;
	}

	public boolean isViolenceGraphic() {
		return this.violenceGraphic;
	}

	public boolean isSelfHarmIntent() {
		return this.selfHarmIntent;
	}

	public boolean isSelfHarmInstructions() {
		return this.selfHarmInstructions;
	}

	public boolean isHarassmentThreatening() {
		return this.harassmentThreatening;
	}

	public boolean isViolence() {
		return this.violence;
	}

	public boolean isDangerousAndCriminalContent() {
		return this.dangerousAndCriminalContent;
	}

	public boolean isHealth() {
		return this.health;
	}

	public boolean isFinancial() {
		return this.financial;
	}

	public boolean isLaw() {
		return this.law;
	}

	public boolean isPii() {
		return this.pii;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof Categories)) {
			return false;
		}
		Categories that = (Categories) o;
		return this.sexual == that.sexual && this.hate == that.hate && this.harassment == that.harassment
				&& this.selfHarm == that.selfHarm && this.sexualMinors == that.sexualMinors
				&& this.hateThreatening == that.hateThreatening && this.violenceGraphic == that.violenceGraphic
				&& this.selfHarmIntent == that.selfHarmIntent && this.selfHarmInstructions == that.selfHarmInstructions
				&& this.harassmentThreatening == that.harassmentThreatening && this.violence == that.violence
				&& this.dangerousAndCriminalContent == that.dangerousAndCriminalContent && this.health == that.health
				&& this.financial == that.financial && this.law == that.law && this.pii == that.pii;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.sexual, this.hate, this.harassment, this.selfHarm, this.sexualMinors,
				this.hateThreatening, this.violenceGraphic, this.selfHarmIntent, this.selfHarmInstructions,
				this.harassmentThreatening, this.violence, this.dangerousAndCriminalContent, this.health,
				this.financial, this.law, this.pii);
	}

	@Override
	public String toString() {
		return "Categories{" + "sexual=" + this.sexual + ", hate=" + this.hate + ", harassment=" + this.harassment
				+ ", selfHarm=" + this.selfHarm + ", sexualMinors=" + this.sexualMinors + ", hateThreatening="
				+ this.hateThreatening + ", violenceGraphic=" + this.violenceGraphic + ", selfHarmIntent="
				+ this.selfHarmIntent + ", selfHarmInstructions=" + this.selfHarmInstructions
				+ ", harassmentThreatening=" + this.harassmentThreatening + ", violence=" + this.violence
				+ ", dangerousAndCriminalContent=" + this.dangerousAndCriminalContent + ", health=" + this.health
				+ ", financial=" + this.financial + ", law=" + this.law + ", pii=" + this.pii + '}';
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

		private boolean dangerousAndCriminalContent;

		private boolean health;

		private boolean financial;

		private boolean law;

		private boolean pii;

		public Builder sexual(boolean sexual) {
			this.sexual = sexual;
			return this;
		}

		public Builder hate(boolean hate) {
			this.hate = hate;
			return this;
		}

		public Builder harassment(boolean harassment) {
			this.harassment = harassment;
			return this;
		}

		public Builder selfHarm(boolean selfHarm) {
			this.selfHarm = selfHarm;
			return this;
		}

		public Builder sexualMinors(boolean sexualMinors) {
			this.sexualMinors = sexualMinors;
			return this;
		}

		public Builder hateThreatening(boolean hateThreatening) {
			this.hateThreatening = hateThreatening;
			return this;
		}

		public Builder violenceGraphic(boolean violenceGraphic) {
			this.violenceGraphic = violenceGraphic;
			return this;
		}

		public Builder selfHarmIntent(boolean selfHarmIntent) {
			this.selfHarmIntent = selfHarmIntent;
			return this;
		}

		public Builder selfHarmInstructions(boolean selfHarmInstructions) {
			this.selfHarmInstructions = selfHarmInstructions;
			return this;
		}

		public Builder harassmentThreatening(boolean harassmentThreatening) {
			this.harassmentThreatening = harassmentThreatening;
			return this;
		}

		public Builder violence(boolean violence) {
			this.violence = violence;
			return this;
		}

		public Builder dangerousAndCriminalContent(boolean dangerousAndCriminalContent) {
			this.dangerousAndCriminalContent = dangerousAndCriminalContent;
			return this;
		}

		public Builder health(boolean health) {
			this.health = health;
			return this;
		}

		public Builder financial(boolean financial) {
			this.financial = financial;
			return this;
		}

		public Builder law(boolean law) {
			this.law = law;
			return this;
		}

		public Builder pii(boolean pii) {
			this.pii = pii;
			return this;
		}

		public Categories build() {
			return new Categories(this);
		}

	}

}
