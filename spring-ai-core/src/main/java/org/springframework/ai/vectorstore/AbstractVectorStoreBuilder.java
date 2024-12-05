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

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationConvention;
import org.springframework.lang.Nullable;
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

	private final EmbeddingModel embeddingModel;

	protected ObservationRegistry observationRegistry = ObservationRegistry.NOOP;

	@Nullable
	protected VectorStoreObservationConvention customObservationConvention;

	/**
	 * Creates a new builder instance with the required embedding model.
	 * @param embeddingModel the embedding model to use for vector operations
	 * @throws IllegalArgumentException if embeddingModel is null
	 */
	protected AbstractVectorStoreBuilder(EmbeddingModel embeddingModel) {
		Assert.notNull(embeddingModel, "EmbeddingModel must not be null");
		this.embeddingModel = embeddingModel;
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
		this.observationRegistry = observationRegistry;
		return self();
	}

	@Override
	public T customObservationConvention(VectorStoreObservationConvention convention) {
		this.customObservationConvention = convention;
		return self();
	}

	/**
	 * Returns the configured embedding model.
	 * @return the embedding model
	 */
	@Override
	public EmbeddingModel embeddingModel() {
		return this.embeddingModel;
	}

	/**
	 * Returns the configured observation registry.
	 * @return the observation registry, never null
	 */
	@Override
	public ObservationRegistry observationRegistry() {
		return this.observationRegistry;
	}

	/**
	 * Returns the configured custom observation convention.
	 * @return the custom observation convention, may be null
	 */
	@Override
	@Nullable
	public VectorStoreObservationConvention customObservationConvention() {
		return this.customObservationConvention;
	}

}
