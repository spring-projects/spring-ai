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

package org.springframework.ai.vectorstore;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.filter.Filter;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the default {@link VectorStore#upsert(List)} implementation, which stores
 * that have not opted in inherit.
 *
 * @author Soby Chacko
 */
class VectorStoreUpsertDefaultTests {

	@Test
	void defaultUpsertThrowsWithStoreNameInMessage() {
		VectorStore store = new NoOpVectorStore();

		List<EmbeddedDocument> entries = List
			.of(new EmbeddedDocument(Document.builder().id("a").text("hello").build(), new float[] { 0.1f, 0.2f }));

		assertThatThrownBy(() -> store.upsert(entries)).isInstanceOf(UnsupportedOperationException.class)
			.hasMessageContaining("NoOpVectorStore")
			.hasMessageContaining("does not support upsert");
	}

	/**
	 * A minimal {@link VectorStore} that does not override {@code upsert}, used to
	 * exercise the interface default.
	 */
	private static final class NoOpVectorStore implements VectorStore {

		@Override
		public void add(List<Document> documents) {
		}

		@Override
		public void delete(List<String> idList) {
		}

		@Override
		public void delete(Filter.Expression filterExpression) {
		}

		@Override
		public List<Document> similaritySearch(SearchRequest request) {
			return List.of();
		}

	}

}
