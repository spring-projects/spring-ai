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
				&& this.harassmentThreatening == that.harassmentThreatening && this.violence == that.violence;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.sexual, this.hate, this.harassment, this.selfHarm, this.sexualMinors,
				this.hateThreatening, this.violenceGraphic, this.selfHarmIntent, this.selfHarmInstructions,
				this.harassmentThreatening, this.violence);
	}

	@Override
	public String toString() {
		return "Categories{" + "sexual=" + this.sexual + ", hate=" + this.hate + ", harassment=" + this.harassment
				+ ", selfHarm=" + this.selfHarm + ", sexualMinors=" + this.sexualMinors + ", hateThreatening="
				+ this.hateThreatening + ", violenceGraphic=" + this.violenceGraphic + ", selfHarmIntent="
				+ this.selfHarmIntent + ", selfHarmInstructions=" + this.selfHarmInstructions
				+ ", harassmentThreatening=" + this.harassmentThreatening + ", violence=" + this.violence + '}';
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

}
