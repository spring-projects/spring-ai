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
package org.springframework.ai.embedding.observation;

import org.springframework.ai.embedding.EmbeddingOptions;
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
 * @since 1.0.0
 */
public class EmbeddingModelObservationContext extends ModelObservationContext<EmbeddingRequest, EmbeddingResponse> {

	private final EmbeddingOptions requestOptions;

	EmbeddingModelObservationContext(EmbeddingRequest embeddingRequest, String provider,
			EmbeddingOptions requestOptions) {
		super(embeddingRequest,
				AiOperationMetadata.builder()
					.operationType(AiOperationType.EMBEDDING.value())
					.provider(provider)
					.build());
		Assert.notNull(requestOptions, "requestOptions cannot be null");
		this.requestOptions = requestOptions;
	}

	public EmbeddingOptions getRequestOptions() {
		return requestOptions;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private EmbeddingRequest embeddingRequest;

		private String provider;

		private EmbeddingOptions requestOptions;

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

		public Builder requestOptions(EmbeddingOptions requestOptions) {
			this.requestOptions = requestOptions;
			return this;
		}

		public EmbeddingModelObservationContext build() {
			return new EmbeddingModelObservationContext(embeddingRequest, provider, requestOptions);
		}

	}

}
