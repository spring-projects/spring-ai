/*
* Copyright 2024 - 2024 the original author or authors.
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
package org.springframework.ai.vectorstore.observation;

import java.util.List;
import java.util.Optional;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.lang.Nullable;

import io.micrometer.observation.ObservationRegistry;

/**
 * @author Christian Tzolov
 * @since 1.0.0
 */
public abstract class AbstractObservationVectorStore implements VectorStore {

	private static final VectorStoreObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultVectorStoreObservationConvention();

	private final ObservationRegistry observationRegistry;

	@Nullable
	private final VectorStoreObservationConvention customObservationConvention;

	public AbstractObservationVectorStore(ObservationRegistry observationRegistry,
			VectorStoreObservationConvention customObservationConvention) {
		this.observationRegistry = observationRegistry;
		this.customObservationConvention = customObservationConvention;
	}

	@Override
	public void add(List<Document> documents) {

		VectorStoreObservationContext observationContext = this
			.createObservationContextBuilder(VectorStoreObservationContext.Operation.ADD.value())
			.build();

		VectorStoreObservationDocumentation.AI_VECTOR_STORE
			.observation(this.customObservationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					observationRegistry)
			.observe(() -> this.doAdd(documents));
	}

	@Override
	public Optional<Boolean> delete(List<String> deleteDocIds) {

		VectorStoreObservationContext observationContext = this
			.createObservationContextBuilder(VectorStoreObservationContext.Operation.DELETE.value())
			.build();

		return VectorStoreObservationDocumentation.AI_VECTOR_STORE
			.observation(this.customObservationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(() -> this.doDelete(deleteDocIds));
	}

	@Override
	public List<Document> similaritySearch(SearchRequest request) {

		VectorStoreObservationContext searchObservationContext = this
			.createObservationContextBuilder(VectorStoreObservationContext.Operation.QUERY.value())
			.withQueryRequest(request)
			.build();

		return VectorStoreObservationDocumentation.AI_VECTOR_STORE
			.observation(this.customObservationConvention, DEFAULT_OBSERVATION_CONVENTION,
					() -> searchObservationContext, this.observationRegistry)
			.observe(() -> {
				var documents = this.doSimilaritySearch(request);
				searchObservationContext.setQueryResponse(documents);
				return documents;
			});
	}

	public abstract void doAdd(List<Document> documents);

	public abstract Optional<Boolean> doDelete(List<String> idList);

	public abstract List<Document> doSimilaritySearch(SearchRequest request);

	public abstract VectorStoreObservationContext.Builder createObservationContextBuilder(String operationName);

}