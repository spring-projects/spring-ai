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
import java.util.function.Supplier;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.lang.Nullable;

/**
 * @author Christian Tzolov
 * @author John Blum
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

		Supplier<VectorStoreObservationContext> observationContext = observationContextSupplier(
				VectorStoreObservationContext.Operation.ADD);

		VectorStoreObservationDocumentation.AI_VECTOR_STORE
			.observation(this.customObservationConvention, DEFAULT_OBSERVATION_CONVENTION, observationContext,
					this.observationRegistry)
			.observe(() -> doAdd(documents));
	}

	@Override
	@SuppressWarnings("all")
	public Optional<Boolean> delete(List<String> deleteDocIds) {

		Supplier<VectorStoreObservationContext> observationContext = observationContextSupplier(
				VectorStoreObservationContext.Operation.DELETE);

		return VectorStoreObservationDocumentation.AI_VECTOR_STORE
			.observation(this.customObservationConvention, DEFAULT_OBSERVATION_CONVENTION, observationContext,
					this.observationRegistry)
			.observe(() -> doDelete(deleteDocIds));
	}

	@Override
	@SuppressWarnings("all")
	public List<Document> similaritySearch(SearchRequest request) {

		Supplier<VectorStoreObservationContext> observationContext = observationContextSupplier(
				VectorStoreObservationContext.Operation.QUERY, builder -> builder.withQueryRequest(request));

		Observation observation = VectorStoreObservationDocumentation.AI_VECTOR_STORE.observation(
				this.customObservationConvention, DEFAULT_OBSERVATION_CONVENTION, observationContext,
				this.observationRegistry);

		return observation.observe(() -> {
			var documents = doSimilaritySearch(request);
			getObservationContext(observation).ifPresent(context -> context.setQueryResponse(documents));
			return documents;
		});
	}

	private Optional<VectorStoreObservationContext> getObservationContext(@Nullable Observation observation) {

		return Optional.ofNullable(observation)
			.map(Observation::getContext)
			.filter(VectorStoreObservationContext.class::isInstance)
			.map(VectorStoreObservationContext.class::cast);
	}

	private Supplier<VectorStoreObservationContext> observationContextSupplier(
			VectorStoreObservationContext.Operation operation) {

		return observationContextSupplier(operation, VectorStoreObservationContextBuilderCustomizer.IDENTITY);
	}

	private Supplier<VectorStoreObservationContext> observationContextSupplier(
			VectorStoreObservationContext.Operation operation,
			VectorStoreObservationContextBuilderCustomizer customizer) {

		return () -> customizer.customize(createObservationContextBuilder(operation.value())).build();
	}

	public abstract void doAdd(List<Document> documents);

	public abstract Optional<Boolean> doDelete(List<String> idList);

	public abstract List<Document> doSimilaritySearch(SearchRequest request);

	public abstract VectorStoreObservationContext.Builder createObservationContextBuilder(String operationName);

	@FunctionalInterface
	@SuppressWarnings("unused")
	protected interface VectorStoreObservationContextBuilderCustomizer {

		VectorStoreObservationContextBuilderCustomizer IDENTITY = builder -> builder;

		VectorStoreObservationContext.Builder customize(VectorStoreObservationContext.Builder builder);

		default VectorStoreObservationContextBuilderCustomizer andThen(
				@Nullable VectorStoreObservationContextBuilderCustomizer customizer) {

			return customizer != null ? builder -> customizer.customize(this.customize(builder)) : this;
		}

	}

}
