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

import org.springframework.ai.content.Media;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.AbstractVectorStoreBuilder;
import org.springframework.ai.vectorstore.EmbeddedDocument;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.MimeType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Tests the {@code upsert} wiring on {@link AbstractObservationVectorStore}: routing
 * through {@code doUpsert}, the throwing default, and observation emission.
 *
 * @author Soby Chacko
 */
class AbstractObservationVectorStoreUpsertTests {

	private static final EmbeddingModel EMBEDDING_MODEL = mock(EmbeddingModel.class);

	private static EmbeddedDocument entry() {
		return new EmbeddedDocument(Document.builder().id("a").text("hello").build(), new float[] { 0.1f, 0.2f });
	}

	@Test
	void upsertRoutesToDoUpsert() {
		AtomicReference<List<EmbeddedDocument>> captured = new AtomicReference<>();
		TestObservationRegistry registry = TestObservationRegistry.create();
		TestVectorStore store = new TestVectorStore(registry, captured);

		List<EmbeddedDocument> entries = List.of(entry());
		store.upsert(entries);

		assertThat(captured.get()).isSameAs(entries);
	}

	@Test
	void upsertEmitsUpsertObservation() {
		TestObservationRegistry registry = TestObservationRegistry.create();
		TestVectorStore store = new TestVectorStore(registry, new AtomicReference<>());

		store.upsert(List.of(entry()));

		TestObservationRegistryAssert.assertThat(registry)
			.hasObservationWithNameEqualTo(DefaultVectorStoreObservationConvention.DEFAULT_NAME)
			.that()
			.hasLowCardinalityKeyValue(
					VectorStoreObservationDocumentation.LowCardinalityKeyNames.DB_OPERATION_NAME.asString(), "upsert");
	}

	@Test
	void mediaDocumentIsRejectedBeforeDoUpsert() {
		AtomicReference<List<EmbeddedDocument>> captured = new AtomicReference<>();
		TestVectorStore store = new TestVectorStore(TestObservationRegistry.create(), captured);

		Media media = new Media(MimeType.valueOf("image/png"), new ByteArrayResource(new byte[] { 0x00 }));
		Document imageDocument = Document.builder().id("a").media(media).build();
		List<EmbeddedDocument> entries = List.of(new EmbeddedDocument(imageDocument, new float[] { 0.1f, 0.2f }));

		// No store can persist a row that carries no text, so the entry is rejected up
		// front and never reaches doUpsert.
		assertThatThrownBy(() -> store.upsert(entries)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Only text documents are supported");
		assertThat(captured.get()).isNull();
	}

	@Test
	void emptyTextDocumentIsAccepted() {
		AtomicReference<List<EmbeddedDocument>> captured = new AtomicReference<>();
		TestVectorStore store = new TestVectorStore(TestObservationRegistry.create(), captured);

		// A reference row carries no content of its own, only a pointer in metadata, so
		// empty text has to stay valid.
		List<EmbeddedDocument> entries = List
			.of(new EmbeddedDocument(Document.builder().id("a").text("").build(), new float[] { 0.1f, 0.2f }));

		store.upsert(entries);

		assertThat(captured.get()).isSameAs(entries);
	}

	@Test
	void defaultDoUpsertThrows() {
		// A store that does not override doUpsert inherits the throwing default.
		ThrowingVectorStore store = new ThrowingVectorStore(TestObservationRegistry.create());

		assertThatThrownBy(() -> store.upsert(List.of(entry()))).isInstanceOf(UnsupportedOperationException.class)
			.hasMessageContaining("ThrowingVectorStore")
			.hasMessageContaining("does not support upsert");
	}

	private static class TestVectorStoreBuilder extends AbstractVectorStoreBuilder<TestVectorStoreBuilder> {

		protected TestVectorStoreBuilder(TestObservationRegistry registry) {
			super(EMBEDDING_MODEL);
			observationRegistry(registry);
		}

		@Override
		public VectorStore build() {
			// The stores under test are constructed directly from this builder.
			throw new UnsupportedOperationException();
		}

	}

	/**
	 * A store that overrides {@code doUpsert} to capture the entries it receives.
	 */
	private static final class TestVectorStore extends AbstractObservationVectorStore {

		private final AtomicReference<List<EmbeddedDocument>> captured;

		private TestVectorStore(TestObservationRegistry registry, AtomicReference<List<EmbeddedDocument>> captured) {
			super(new TestVectorStoreBuilder(registry));
			this.captured = captured;
		}

		@Override
		protected void doUpsert(List<EmbeddedDocument> entries) {
			this.captured.set(entries);
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
	 * A store that does not override {@code doUpsert}, exercising the throwing default.
	 */
	private static final class ThrowingVectorStore extends AbstractObservationVectorStore {

		private ThrowingVectorStore(TestObservationRegistry registry) {
			super(new TestVectorStoreBuilder(registry));
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
