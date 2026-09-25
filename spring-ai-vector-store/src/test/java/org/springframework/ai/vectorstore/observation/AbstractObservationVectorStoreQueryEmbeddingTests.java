/*
 * Copyright 2023-present the original author or authors.
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
import java.util.concurrent.atomic.AtomicReference;

import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import org.junit.jupiter.api.Test;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.AbstractVectorStoreBuilder;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.VectorStoreRetriever;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Tests the query-embedding {@code similaritySearch} wiring on
 * {@link AbstractObservationVectorStore}: routing to the query-embedding
 * {@code doSimilaritySearch}, validation of the vector, the throwing defaults, and
 * observation emission.
 *
 * @author Soby Chacko
 */
class AbstractObservationVectorStoreQueryEmbeddingTests {

	private static final SearchRequest REQUEST = SearchRequest.builder().topK(3).build();

	@Test
	void routesToQueryEmbeddingSearchWithoutTheEmbeddingModel() {
		EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
		AtomicReference<float[]> captured = new AtomicReference<>();
		TestVectorStore store = new TestVectorStore(embeddingModel, TestObservationRegistry.create(), captured);

		float[] queryEmbedding = { 0.1f, 0.2f };
		store.similaritySearch(queryEmbedding, REQUEST);

		assertThat(captured.get()).isSameAs(queryEmbedding);
		verifyNoInteractions(embeddingModel);
	}

	@Test
	void emitsQueryObservation() {
		TestObservationRegistry registry = TestObservationRegistry.create();
		TestVectorStore store = new TestVectorStore(mock(EmbeddingModel.class), registry, new AtomicReference<>());

		store.similaritySearch(new float[] { 0.1f, 0.2f }, REQUEST);

		TestObservationRegistryAssert.assertThat(registry)
			.hasObservationWithNameEqualTo(DefaultVectorStoreObservationConvention.DEFAULT_NAME)
			.that()
			.hasLowCardinalityKeyValue(
					VectorStoreObservationDocumentation.LowCardinalityKeyNames.DB_OPERATION_NAME.asString(), "query");
	}

	@Test
	void observationDoesNotRecordTheIgnoredQueryText() {
		TestObservationRegistry registry = TestObservationRegistry.create();
		TestVectorStore store = new TestVectorStore(mock(EmbeddingModel.class), registry, new AtomicReference<>());

		SearchRequest request = SearchRequest.builder().query("not used").topK(3).build();
		store.similaritySearch(new float[] { 0.1f, 0.2f }, request);

		TestObservationRegistryAssert.assertThat(registry)
			.hasObservationWithNameEqualTo(DefaultVectorStoreObservationConvention.DEFAULT_NAME)
			.that()
			.satisfies(context -> {
				SearchRequest recorded = ((VectorStoreObservationContext) context).getQueryRequest();
				assertThat(recorded).isNotNull();
				assertThat(recorded.getQuery()).isEmpty();
				assertThat(recorded.getTopK()).isEqualTo(3);
			});
	}

	@Test
	void nullArgumentsAreRejected() {
		TestVectorStore store = new TestVectorStore(mock(EmbeddingModel.class), TestObservationRegistry.create(),
				new AtomicReference<>());

		assertThatIllegalArgumentException().isThrownBy(() -> store.similaritySearch((float[]) null, REQUEST))
			.withMessageContaining("queryEmbedding must not be null");
		assertThatIllegalArgumentException().isThrownBy(() -> store.similaritySearch(new float[] { 0.1f }, null))
			.withMessageContaining("request must not be null");
	}

	@Test
	void invalidQueryEmbeddingIsRejectedBeforeSearch() {
		AtomicReference<float[]> captured = new AtomicReference<>();
		TestVectorStore store = new TestVectorStore(mock(EmbeddingModel.class), TestObservationRegistry.create(),
				captured);

		assertThatIllegalArgumentException().isThrownBy(() -> store.similaritySearch(new float[0], REQUEST))
			.withMessageContaining("must not be empty");
		assertThatIllegalArgumentException()
			.isThrownBy(() -> store.similaritySearch(new float[] { 0.1f, Float.NaN }, REQUEST))
			.withMessageContaining("NaN");
		assertThatIllegalArgumentException()
			.isThrownBy(() -> store.similaritySearch(new float[] { Float.POSITIVE_INFINITY }, REQUEST))
			.withMessageContaining("Infinity");
		assertThat(captured.get()).isNull();
	}

	@Test
	void defaultDoSimilaritySearchWithQueryEmbeddingThrows() {
		// A store that does not override the query-embedding doSimilaritySearch inherits
		// the throwing default.
		ThrowingVectorStore store = new ThrowingVectorStore(TestObservationRegistry.create());

		assertThatThrownBy(() -> store.similaritySearch(new float[] { 0.1f }, REQUEST))
			.isInstanceOf(UnsupportedOperationException.class)
			.hasMessageContaining("ThrowingVectorStore")
			.hasMessageContaining("does not support similarity search with a query embedding");
	}

	@Test
	void retrieverDefaultThrows() {
		VectorStoreRetriever retriever = request -> List.of();

		assertThatThrownBy(() -> retriever.similaritySearch(new float[] { 0.1f }, REQUEST))
			.isInstanceOf(UnsupportedOperationException.class)
			.hasMessageContaining("does not support similarity search with a query embedding");
	}

	private static class TestVectorStoreBuilder extends AbstractVectorStoreBuilder<TestVectorStoreBuilder> {

		protected TestVectorStoreBuilder(EmbeddingModel embeddingModel, TestObservationRegistry registry) {
			super(embeddingModel);
			observationRegistry(registry);
		}

		@Override
		public VectorStore build() {
			// The stores under test are constructed directly from this builder.
			throw new UnsupportedOperationException();
		}

	}

	/**
	 * A store that overrides the query-embedding {@code doSimilaritySearch} to capture
	 * the vector it receives.
	 */
	private static final class TestVectorStore extends AbstractObservationVectorStore {

		private final AtomicReference<float[]> captured;

		private TestVectorStore(EmbeddingModel embeddingModel, TestObservationRegistry registry,
				AtomicReference<float[]> captured) {
			super(new TestVectorStoreBuilder(embeddingModel, registry));
			this.captured = captured;
		}

		@Override
		protected List<Document> doSimilaritySearch(float[] queryEmbedding, SearchRequest request) {
			this.captured.set(queryEmbedding);
			return List.of();
		}

		@Override
		public void doAdd(List<Document> documents) {
		}

		@Override
		public void doDelete(List<String> idList) {
		}

		@Override
		public List<Document> doSimilaritySearch(SearchRequest request) {
			return List.of();
		}

		@Override
		public VectorStoreObservationContext.Builder createObservationContextBuilder(String operationName) {
			return VectorStoreObservationContext.builder("test", operationName);
		}

	}

	/**
	 * A store that does not override the query-embedding {@code doSimilaritySearch},
	 * exercising the throwing default.
	 */
	private static final class ThrowingVectorStore extends AbstractObservationVectorStore {

		private ThrowingVectorStore(TestObservationRegistry registry) {
			super(new TestVectorStoreBuilder(mock(EmbeddingModel.class), registry));
		}

		@Override
		public void doAdd(List<Document> documents) {
		}

		@Override
		public void doDelete(List<String> idList) {
		}

		@Override
		public List<Document> doSimilaritySearch(SearchRequest request) {
			return List.of();
		}

		@Override
		public VectorStoreObservationContext.Builder createObservationContextBuilder(String operationName) {
			return VectorStoreObservationContext.builder("test", operationName);
		}

	}

}
