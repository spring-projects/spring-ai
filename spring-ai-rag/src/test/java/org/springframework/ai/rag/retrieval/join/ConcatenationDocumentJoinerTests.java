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

package org.springframework.ai.rag.retrieval.join;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ConcatenationDocumentJoiner}.
 *
 * @author Thomas Vitale
 */
class ConcatenationDocumentJoinerTests {

	@Test
	void whenDocumentsForQueryIsNullThenThrow() {
		DocumentJoiner documentJoiner = new ConcatenationDocumentJoiner();
		assertThatThrownBy(() -> documentJoiner.apply(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("documentsForQuery cannot be null");
	}

	@Test
	void whenDocumentsForQueryContainsNullKeysThenThrow() {
		DocumentJoiner documentJoiner = new ConcatenationDocumentJoiner();
		var documentsForQuery = new HashMap<Query, List<List<Document>>>();
		documentsForQuery.put(null, List.of());
		assertThatThrownBy(() -> documentJoiner.apply(documentsForQuery)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("documentsForQuery cannot contain null keys");
	}

	@Test
	void whenDocumentsForQueryContainsNullValuesThenThrow() {
		DocumentJoiner documentJoiner = new ConcatenationDocumentJoiner();
		var documentsForQuery = new HashMap<Query, List<List<Document>>>();
		documentsForQuery.put(new Query("test"), null);
		assertThatThrownBy(() -> documentJoiner.apply(documentsForQuery)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("documentsForQuery cannot contain null values");
	}

	@Test
	void whenNoDuplicatedDocumentsThenAllDocumentsAreJoined() {
		DocumentJoiner documentJoiner = new ConcatenationDocumentJoiner();
		var documentsForQuery = new HashMap<Query, List<List<Document>>>();
		documentsForQuery.put(new Query("query1"),
				List.of(List.of(new Document("1", "Content 1", Map.of()), new Document("2", "Content 2", Map.of())),
						List.of(new Document("3", "Content 3", Map.of()))));
		documentsForQuery.put(new Query("query2"), List.of(List.of(new Document("4", "Content 4", Map.of()))));

		List<Document> result = documentJoiner.join(documentsForQuery);

		assertThat(result).hasSize(4);
		assertThat(result).extracting(Document::getId).containsExactlyInAnyOrder("1", "2", "3", "4");
	}

	@Test
	void whenDuplicatedDocumentsThenOnlyFirstOccurrenceIsKept() {
		DocumentJoiner documentJoiner = new ConcatenationDocumentJoiner();
		var documentsForQuery = new HashMap<Query, List<List<Document>>>();
		documentsForQuery.put(new Query("query1"),
				List.of(List.of(new Document("1", "Content 1", Map.of()), new Document("2", "Content 2", Map.of())),
						List.of(new Document("3", "Content 3", Map.of()))));
		documentsForQuery.put(new Query("query2"),
				List.of(List.of(new Document("2", "Content 2", Map.of()), new Document("4", "Content 4", Map.of()))));

		List<Document> result = documentJoiner.join(documentsForQuery);

		assertThat(result).hasSize(4);
		assertThat(result).extracting(Document::getId).containsExactlyInAnyOrder("1", "2", "3", "4");
		assertThat(result).extracting(Document::getText).containsOnlyOnce("Content 2");
	}

	@Test
	void shouldSortDocumentsByDescendingScore() {
		//@formatter:off
		DocumentJoiner documentJoiner = new ConcatenationDocumentJoiner();
		var documentsForQuery = new HashMap<Query, List<List<Document>>>();
		documentsForQuery.put(new Query("query1"), List.of(
				List.of(
					Document.builder().id("1").text("Content 1").score(0.81).build(),
					Document.builder().id("2").text("Content 2").score(0.83).build()),
				List.of(
					Document.builder().id("3").text("Content 3").score(null).build())));
		documentsForQuery.put(new Query("query2"), List.of(
				List.of(
						Document.builder().id("4").text("Content 4").score(0.85).build(),
						Document.builder().id("5").text("Content 5").score(0.77).build())));

		List<Document> result = documentJoiner.join(documentsForQuery);

		assertThat(result).hasSize(5);
		assertThat(result).extracting(Document::getId).containsExactly("4", "2", "1", "5", "3");
		//@formatter:on
	}

}
