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

package org.springframework.ai.rag.postretrieval.document;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link RerankingDocumentPostProcessor}.
 *
 * @author KoreaNirsa
 */
class RerankingDocumentPostProcessorTests {

	@Test
	void whenDocumentRerankerIsNullThenThrow() {
		assertThatThrownBy(() -> RerankingDocumentPostProcessor.builder().documentReranker(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("documentReranker cannot be null");
	}

	@Test
	void whenTopNIsLessThanOneThenThrow() {
		assertThatThrownBy(() -> RerankingDocumentPostProcessor.builder()
			.documentReranker((query, documents) -> documents)
			.topN(0)
			.build()).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("topN must be greater than 0");
	}

	@Test
	void shouldRerankDocuments() {
		Document first = Document.builder().id("1").text("The first document").score(0.60).build();
		Document second = Document.builder().id("2").text("The second document").score(0.80).build();
		Document third = Document.builder().id("3").text("The third document").score(0.70).build();

		DocumentPostProcessor processor = RerankingDocumentPostProcessor.builder()
			.documentReranker((query, documents) -> List.of(second, third, first))
			.build();

		List<Document> documents = processor.process(new Query("query"), List.of(first, second, third));

		assertThat(documents).extracting(Document::getId).containsExactly("2", "3", "1");
	}

	@Test
	void shouldKeepOnlyTopNDocuments() {
		Document first = Document.builder().id("1").text("The first document").build();
		Document second = Document.builder().id("2").text("The second document").build();
		Document third = Document.builder().id("3").text("The third document").build();

		DocumentPostProcessor processor = RerankingDocumentPostProcessor.builder()
			.documentReranker((query, documents) -> List.of(second, third, first))
			.topN(2)
			.build();

		List<Document> documents = processor.process(new Query("query"), List.of(first, second, third));

		assertThat(documents).extracting(Document::getId).containsExactly("2", "3");
	}

	@Test
	void whenRerankedDocumentsAreNullThenThrow() {
		DocumentPostProcessor processor = RerankingDocumentPostProcessor.builder()
			.documentReranker((query, documents) -> null)
			.build();

		assertThatThrownBy(() -> processor.process(new Query("query"), List.of(document("1"))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("rerankedDocuments cannot be null");
	}

	@Test
	void whenRerankedDocumentsContainNullElementsThenThrow() {
		List<Document> rerankedDocuments = new ArrayList<>();
		rerankedDocuments.add(document("1"));
		rerankedDocuments.add(null);
		DocumentPostProcessor processor = RerankingDocumentPostProcessor.builder()
			.documentReranker((query, documents) -> rerankedDocuments)
			.build();

		assertThatThrownBy(() -> processor.process(new Query("query"), List.of(document("1"))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("rerankedDocuments cannot contain null elements");
	}

	@Test
	void shouldNotCallRerankerWhenDocumentsAreEmpty() {
		DocumentPostProcessor processor = RerankingDocumentPostProcessor.builder()
			.documentReranker((query, documents) -> {
				throw new AssertionError("Reranker should not be called for empty documents");
			})
			.build();

		assertThat(processor.process(new Query("query"), List.of())).isEmpty();
	}

	private static Document document(String id) {
		return Document.builder().id(id).text("Document " + id).build();
	}

}
