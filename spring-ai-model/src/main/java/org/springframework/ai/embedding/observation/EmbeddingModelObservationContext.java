/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.embedding.observation;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.model.observation.ModelObservationContext;
import org.springframework.ai.observation.AiOperationMetadata;
import org.springframework.ai.observation.conventions.AiOperationType;
import org.springframework.util.Assert;

/**
 * Context used to store metadata for embedding model exchanges.
 *
 * @author Thomas Vitale
 * @author Soby Chacko
 * @since 1.0.0
 */
public class EmbeddingModelObservationContext extends ModelObservationContext<EmbeddingRequest, EmbeddingResponse> {

	EmbeddingModelObservationContext(EmbeddingRequest embeddingRequest, String provider) {
		super(embeddingRequest,
				AiOperationMetadata.builder()
					.operationType(AiOperationType.EMBEDDING.value())
					.provider(provider)
					.build());
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private @Nullable EmbeddingRequest embeddingRequest;

		private @Nullable String provider;

		private Builder() {
		}

		public Builder embeddingRequest(EmbeddingRequest embeddingRequest) {
			this.embeddingRequest = embeddingRequest;
			return this;
		}

		public Builder provider(String provider) {
			this.provider = provider;
			return this;
		}

		public EmbeddingModelObservationContext build() {
			Assert.state(this.embeddingRequest != null, "request cannot be null");
			Assert.state(this.provider != null, "provider cannot be null or empty");
			return new EmbeddingModelObservationContext(this.embeddingRequest, this.provider);
		}

	}

}
