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

	public AbstractObservationVectorStore() {
		this(ObservationRegistry.NOOP, null);
	}

	public AbstractObservationVectorStore(ObservationRegistry observationRegistry) {
		this(observationRegistry, null);
	}

	public AbstractObservationVectorStore(ObservationRegistry observationRegistry,
			VectorStoreObservationConvention customSearchObservationConvention) {
		this.observationRegistry = observationRegistry;
		this.customObservationConvention = customSearchObservationConvention;
	}

	@Override
	public void add(List<Document> documents) {

		VectorStoreObservationContext observationContext = this.createObservationContextBuilder("add")
			.withAddRequest(documents)
			.build();

		VectorStoreObservationDocumentation.AI_VECTOR_STORE
			.observation(this.customObservationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					observationRegistry)
			.observe(() -> this.doAdd(documents));
	}

	@Override
	public Optional<Boolean> delete(List<String> deleteDocIds) {

		VectorStoreObservationContext observationContext = this.createObservationContextBuilder("delete")
			.withDeleteRequest(deleteDocIds)
			.build();

		return VectorStoreObservationDocumentation.AI_VECTOR_STORE
			.observation(this.customObservationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(() -> this.doDelete(deleteDocIds));
	}

	@Override
	public List<Document> similaritySearch(SearchRequest request) {

		VectorStoreObservationContext searchObservationContext = this.createObservationContextBuilder("search")
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

	abstract public void doAdd(List<Document> documents);

	abstract public Optional<Boolean> doDelete(List<String> idList);

	abstract public List<Document> doSimilaritySearch(SearchRequest request);

	abstract public VectorStoreObservationContext.Builder createObservationContextBuilder(String operationName);

}