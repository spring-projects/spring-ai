/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.observation;

import org.springframework.ai.observation.conventions.AiOperationType;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.util.Assert;

/**
 * Metadata associated with an AI operation (e.g. model inference, fine-tuning,
 * evaluation).
 *
 * @param operationType The type of operation performed by the model. Whenever possible, a
 * value from {@link AiOperationType}.
 * @param provider The name of the system providing the model service. Whenever possible,
 * a value from {@link AiProvider}.
 * @author Thomas Vitale
 * @since 1.0.0
 */
public record AiOperationMetadata(String operationType, String provider) {

	public AiOperationMetadata {
		Assert.hasText(operationType, "operationType cannot be null or empty");
		Assert.hasText(provider, "provider cannot be null or empty");
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private String operationType;

		private String provider;

		private Builder() {
		}

		public Builder operationType(String operationType) {
			this.operationType = operationType;
			return this;
		}

		public Builder provider(String provider) {
			this.provider = provider;
			return this;
		}

		public AiOperationMetadata build() {
			return new AiOperationMetadata(operationType, provider);
		}

	}

}
