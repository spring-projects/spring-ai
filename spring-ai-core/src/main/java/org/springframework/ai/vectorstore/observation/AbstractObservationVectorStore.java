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

package org.springframework.ai.vectorstore.observation;

import java.util.List;
import java.util.Optional;

import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.AbstractVectorStoreBuilder;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.lang.Nullable;

/**
 * Abstract base class for {@link VectorStore} implementations that provides observation
 * capabilities.
 *
 * @author Christian Tzolov
 * @author Soby Chacko
 * @since 1.0.0
 */
public abstract class AbstractObservationVectorStore implements VectorStore {

	private static final VectorStoreObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultVectorStoreObservationConvention();

	private final ObservationRegistry observationRegistry;

	@Nullable
	private final VectorStoreObservationConvention customObservationConvention;

	protected final EmbeddingModel embeddingModel;

	/**
	 * Create a new {@link AbstractObservationVectorStore} instance.
	 * @param observationRegistry the observation registry to use
	 * @param customObservationConvention the custom observation convention to use
	 */
	@Deprecated(since = "1.0.0-M5", forRemoval = true)
	public AbstractObservationVectorStore(ObservationRegistry observationRegistry,
			@Nullable VectorStoreObservationConvention customObservationConvention) {
		this(null, observationRegistry, customObservationConvention);
	}

	private AbstractObservationVectorStore(EmbeddingModel embeddingModel, ObservationRegistry observationRegistry,
			@Nullable VectorStoreObservationConvention customObservationConvention) {
		this.embeddingModel = embeddingModel;
		this.observationRegistry = observationRegistry;
		this.customObservationConvention = customObservationConvention;
	}

	/**
	 * Creates a new AbstractObservationVectorStore instance with the specified builder
	 * settings. Initializes observation-related components and the embedding model.
	 * @param builder the builder containing configuration settings
	 */
	public AbstractObservationVectorStore(AbstractVectorStoreBuilder<?> builder) {
		this(builder.getEmbeddingModel(), builder.getObservationRegistry(), builder.getCustomObservationConvention());
	}

	/**
	 * Create a new {@link AbstractObservationVectorStore} instance.
	 * @param documents the documents to add
	 */
	@Override
	public void add(List<Document> documents) {

		VectorStoreObservationContext observationContext = this
			.createObservationContextBuilder(VectorStoreObservationContext.Operation.ADD.value())
			.build();

		VectorStoreObservationDocumentation.AI_VECTOR_STORE
			.observation(this.customObservationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(() -> this.doAdd(documents));
	}

	@Override
	@Nullable
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
	@Nullable
	public List<Document> similaritySearch(SearchRequest request) {

		VectorStoreObservationContext searchObservationContext = this
			.createObservationContextBuilder(VectorStoreObservationContext.Operation.QUERY.value())
			.queryRequest(request)
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

	/**
	 * Perform the actual add operation.
	 * @param documents the documents to add
	 */
	public abstract void doAdd(List<Document> documents);

	/**
	 * Perform the actual delete operation.
	 * @param idList the list of document IDs to delete
	 * @return true if the documents were successfully deleted
	 */
	public abstract Optional<Boolean> doDelete(List<String> idList);

	/**
	 * Perform the actual similarity search operation.
	 * @param request the search request
	 * @return the list of documents that match the query request conditions
	 */
	public abstract List<Document> doSimilaritySearch(SearchRequest request);

	/**
	 * Create a new {@link VectorStoreObservationContext.Builder} instance.
	 * @param operationName the operation name
	 * @return the observation context builder
	 */
	public abstract VectorStoreObservationContext.Builder createObservationContextBuilder(String operationName);

}
