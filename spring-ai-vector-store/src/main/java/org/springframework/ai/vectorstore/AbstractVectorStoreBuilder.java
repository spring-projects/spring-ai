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

package org.springframework.ai.vectorstore;

import io.micrometer.observation.ObservationRegistry;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationConvention;
import org.springframework.util.Assert;

/**
 * Abstract base builder implementing common builder functionality for
 * {@link VectorStore}. Provides default implementations for observation-related settings.
 *
 * @param <T> the concrete builder type, enabling method chaining with the correct return
 * type
 */
public abstract class AbstractVectorStoreBuilder<T extends AbstractVectorStoreBuilder<T>>
		implements VectorStore.Builder<T> {

	protected final EmbeddingModel embeddingModel;

	protected ObservationRegistry observationRegistry = ObservationRegistry.NOOP;

	protected @Nullable VectorStoreObservationConvention customObservationConvention;

	protected BatchingStrategy batchingStrategy = new TokenCountBatchingStrategy();

	public AbstractVectorStoreBuilder(EmbeddingModel embeddingModel) {
		Assert.notNull(embeddingModel, "EmbeddingModel must be configured");
		this.embeddingModel = embeddingModel;
	}

	public EmbeddingModel getEmbeddingModel() {
		return this.embeddingModel;
	}

	public BatchingStrategy getBatchingStrategy() {
		return this.batchingStrategy;
	}

	public ObservationRegistry getObservationRegistry() {
		return this.observationRegistry;
	}

	public @Nullable VectorStoreObservationConvention getCustomObservationConvention() {
		return this.customObservationConvention;
	}

	/**
	 * Returns this builder cast to the concrete builder type. Used internally to enable
	 * proper method chaining in subclasses.
	 * @return this builder cast to the concrete type
	 */
	@SuppressWarnings("unchecked")
	protected T self() {
		return (T) this;
	}

	@Override
	public T observationRegistry(ObservationRegistry observationRegistry) {
		Assert.notNull(observationRegistry, "ObservationRegistry must not be null");
		this.observationRegistry = observationRegistry;
		return self();
	}

	@Override
	public T customObservationConvention(@Nullable VectorStoreObservationConvention convention) {
		this.customObservationConvention = convention;
		return self();
	}

	/**
	 * Sets the batching strategy.
	 * @param batchingStrategy the strategy to use
	 * @return the builder instance
	 */
	public T batchingStrategy(BatchingStrategy batchingStrategy) {
		Assert.notNull(batchingStrategy, "BatchingStrategy must not be null");
		this.batchingStrategy = batchingStrategy;
		return self();
	}

}
