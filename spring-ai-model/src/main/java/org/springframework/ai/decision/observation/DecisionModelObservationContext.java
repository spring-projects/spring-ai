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

package org.springframework.ai.decision.observation;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.decision.DecisionPrompt;
import org.springframework.ai.decision.DecisionResponse;
import org.springframework.ai.model.observation.ModelObservationContext;
import org.springframework.ai.observation.AiOperationMetadata;
import org.springframework.ai.observation.conventions.AiOperationType;
import org.springframework.util.Assert;

/**
 * Context used to store metadata for decision model exchanges.
 *
 * @author Thomas Vitale
 * @since 2.1.0
 */
public class DecisionModelObservationContext extends ModelObservationContext<DecisionPrompt, DecisionResponse> {

	DecisionModelObservationContext(DecisionPrompt decisionPrompt, String provider) {
		super(decisionPrompt,
				AiOperationMetadata.builder()
					.operationType(AiOperationType.DECISION.value())
					.provider(provider)
					.build());
	}

	public static Builder builder() {
		return new Builder();
	}

	public String getOperationType() {
		return AiOperationType.DECISION.value();
	}

	public static final class Builder {

		private @Nullable DecisionPrompt decisionPrompt;

		private @Nullable String provider;

		private Builder() {
		}

		public Builder decisionPrompt(DecisionPrompt decisionPrompt) {
			this.decisionPrompt = decisionPrompt;
			return this;
		}

		public Builder provider(String provider) {
			this.provider = provider;
			return this;
		}

		public DecisionModelObservationContext build() {
			Assert.notNull(this.decisionPrompt, "decisionPrompt cannot be null");
			Assert.hasText(this.provider, "provider cannot be null or empty");
			return new DecisionModelObservationContext(this.decisionPrompt, this.provider);
		}

	}

}
