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

package org.springframework.ai.vectorstore.observation;

import java.util.List;

import io.micrometer.observation.ObservationRegistry;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.AbstractVectorStoreBuilder;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

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

	private final @Nullable VectorStoreObservationConvention customObservationConvention;

	protected final EmbeddingModel embeddingModel;

	protected final BatchingStrategy batchingStrategy;

	private AbstractObservationVectorStore(EmbeddingModel embeddingModel, ObservationRegistry observationRegistry,
			@Nullable VectorStoreObservationConvention customObservationConvention, BatchingStrategy batchingStrategy) {
		this.embeddingModel = embeddingModel;
		this.observationRegistry = observationRegistry;
		this.customObservationConvention = customObservationConvention;
		this.batchingStrategy = batchingStrategy;
	}

	/**
	 * Creates a new AbstractObservationVectorStore instance with the specified builder
	 * settings. Initializes observation-related components and the embedding model.
	 * @param builder the builder containing configuration settings
	 */
	public AbstractObservationVectorStore(AbstractVectorStoreBuilder<?> builder) {
		this(builder.getEmbeddingModel(), builder.getObservationRegistry(), builder.getCustomObservationConvention(),
				builder.getBatchingStrategy());
	}

	/**
	 * Create a new {@link AbstractObservationVectorStore} instance.
	 * @param documents the documents to add
	 */
	@Override
	public void add(List<Document> documents) {
		validateNonTextDocuments(documents);
		VectorStoreObservationContext observationContext = this
			.createObservationContextBuilder(VectorStoreObservationContext.Operation.ADD.value())
			.build();

		VectorStoreObservationDocumentation.AI_VECTOR_STORE
			.observation(this.customObservationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(() -> this.doAdd(documents));
	}

	private void validateNonTextDocuments(List<Document> documents) {
		if (documents == null) {
			return;
		}
		for (Document document : documents) {
			if (document != null && !document.isText()) {
				throw new IllegalArgumentException(
						"Only text documents are supported for now. One of the documents contains non-text content.");
			}
		}
	}

	@Override
	public void delete(List<String> deleteDocIds) {

		VectorStoreObservationContext observationContext = this
			.createObservationContextBuilder(VectorStoreObservationContext.Operation.DELETE.value())
			.build();

		VectorStoreObservationDocumentation.AI_VECTOR_STORE
			.observation(this.customObservationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(() -> this.doDelete(deleteDocIds));
	}

	@Override
	public void delete(Filter.Expression filterExpression) {
		VectorStoreObservationContext observationContext = this
			.createObservationContextBuilder(VectorStoreObservationContext.Operation.DELETE.value())
			.build();

		VectorStoreObservationDocumentation.AI_VECTOR_STORE
			.observation(this.customObservationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(() -> this.doDelete(filterExpression));
	}

	@Override
	// Micrometer Observation#observe returns the value of the Supplier, which is never
	// null
	@SuppressWarnings("DataFlowIssue")
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
	 */
	public abstract void doDelete(List<String> idList);

	/**
	 * Template method for concrete implementations to provide filter-based deletion
	 * logic.
	 * @param filterExpression Filter expression to identify documents to delete
	 */
	protected void doDelete(Filter.Expression filterExpression) {
		// this is temporary until we implement this method in all concrete vector stores,
		// at which point
		// this method will become an abstract method.
		throw new UnsupportedOperationException();
	}

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
