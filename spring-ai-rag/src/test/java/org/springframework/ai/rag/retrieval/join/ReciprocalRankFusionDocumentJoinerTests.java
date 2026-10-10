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

package org.springframework.ai.rag.retrieval.join;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for {@link ReciprocalRankFusionDocumentJoiner}.
 *
 * @author pj991207
 */
class ReciprocalRankFusionDocumentJoinerTests {

	@Test
	void whenDocumentsForQueryIsNullThenThrow() {
		DocumentJoiner documentJoiner = new ReciprocalRankFusionDocumentJoiner();
		assertThatThrownBy(() -> documentJoiner.apply(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("documentsForQuery cannot be null");
	}

	@Test
	void whenDocumentsForQueryContainsNullKeysThenThrow() {
		DocumentJoiner documentJoiner = new ReciprocalRankFusionDocumentJoiner();
		var documentsForQuery = new HashMap<Query, List<List<Document>>>();
		documentsForQuery.put(null, List.of());
		assertThatThrownBy(() -> documentJoiner.apply(documentsForQuery)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("documentsForQuery cannot contain null keys");
	}

	@Test
	void whenDocumentsForQueryContainsNullValuesThenThrow() {
		DocumentJoiner documentJoiner = new ReciprocalRankFusionDocumentJoiner();
		var documentsForQuery = new HashMap<Query, List<List<Document>>>();
		documentsForQuery.put(new Query("test"), null);
		assertThatThrownBy(() -> documentJoiner.apply(documentsForQuery)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("documentsForQuery cannot contain null values");
	}

	@Test
	void whenRankConstantIsNotPositiveThenThrow() {
		assertThatThrownBy(() -> new ReciprocalRankFusionDocumentJoiner(0)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("rankConstant must be greater than 0");
	}

	@Test
	void whenDocumentsForQueryIsEmptyThenReturnEmptyList() {
		DocumentJoiner documentJoiner = new ReciprocalRankFusionDocumentJoiner();

		List<Document> result = documentJoiner.join(new HashMap<>());

		assertThat(result).isEmpty();
	}

	@Test
	void whenAllDocumentListsAreEmptyThenReturnEmptyList() {
		DocumentJoiner documentJoiner = new ReciprocalRankFusionDocumentJoiner();
		var documentsForQuery = new HashMap<Query, List<List<Document>>>();
		documentsForQuery.put(new Query("query1"), List.of(List.of()));
		documentsForQuery.put(new Query("query2"), List.of());

		List<Document> result = documentJoiner.join(documentsForQuery);

		assertThat(result).isEmpty();
	}

	@Test
	void shouldRankDocumentsBySumOfReciprocalRanks() {
		DocumentJoiner documentJoiner = new ReciprocalRankFusionDocumentJoiner();
		var documentsForQuery = new HashMap<Query, List<List<Document>>>();
		documentsForQuery.put(new Query("query1"), List.of(List.of(document("a"), document("b"), document("c"))));
		documentsForQuery.put(new Query("query2"), List.of(List.of(document("b"), document("d"), document("a"))));

		List<Document> result = documentJoiner.join(documentsForQuery);

		assertThat(result).extracting(Document::getId).containsExactly("b", "a", "d", "c");
		assertThat(scoreOf(result, "b")).isCloseTo(1.0 / 61 + 1.0 / 62, within(1e-12));
		assertThat(scoreOf(result, "a")).isCloseTo(1.0 / 61 + 1.0 / 63, within(1e-12));
		assertThat(scoreOf(result, "d")).isCloseTo(1.0 / 62, within(1e-12));
		assertThat(scoreOf(result, "c")).isCloseTo(1.0 / 63, within(1e-12));
	}

	@Test
	void whenDocumentIsRetrievedByMultipleQueriesThenItIsReturnedOnce() {
		DocumentJoiner documentJoiner = new ReciprocalRankFusionDocumentJoiner();
		var documentsForQuery = new HashMap<Query, List<List<Document>>>();
		documentsForQuery.put(new Query("query1"), List.of(List.of(document("a"), document("b"))));
		documentsForQuery.put(new Query("query2"), List.of(List.of(document("a"), document("c"))));

		List<Document> result = documentJoiner.join(documentsForQuery);

		assertThat(result).extracting(Document::getId).containsExactly("a", "b", "c");
		assertThat(scoreOf(result, "a")).isCloseTo(1.0 / 61 + 1.0 / 61, within(1e-12));
	}

	@Test
	void whenDocumentIsDuplicatedWithinOneListThenOnlyItsFirstRankContributes() {
		DocumentJoiner documentJoiner = new ReciprocalRankFusionDocumentJoiner();
		var documentsForQuery = new HashMap<Query, List<List<Document>>>();
		documentsForQuery.put(new Query("query1"), List.of(List.of(document("a"), document("a"), document("b"))));

		List<Document> result = documentJoiner.join(documentsForQuery);

		assertThat(result).extracting(Document::getId).containsExactly("a", "b");
		assertThat(scoreOf(result, "a")).isCloseTo(1.0 / 61, within(1e-12));
		assertThat(scoreOf(result, "b")).isCloseTo(1.0 / 63, within(1e-12));
	}

	@Test
	void whenScoresAreEqualThenDocumentsAreSortedById() {
		DocumentJoiner documentJoiner = new ReciprocalRankFusionDocumentJoiner();
		var documentsForQuery = new HashMap<Query, List<List<Document>>>();
		documentsForQuery.put(new Query("query1"), List.of(List.of(document("b"), document("a"))));
		documentsForQuery.put(new Query("query2"), List.of(List.of(document("a"), document("b"))));

		List<Document> result = documentJoiner.join(documentsForQuery);

		assertThat(result).extracting(Document::getId).containsExactly("a", "b");
		assertThat(scoreOf(result, "a")).isEqualTo(scoreOf(result, "b"));
	}

	@Test
	void shouldRankDocumentsTheSameRegardlessOfMapIterationOrder() {
		DocumentJoiner documentJoiner = new ReciprocalRankFusionDocumentJoiner();
		var query1 = new Query("query1");
		var query2 = new Query("query2");
		var query3 = new Query("query3");
		// "a" is ranked first, first and second, a combination whose contributions are
		// not summed to the same double when they are accumulated in a different order.
		// "c" and "d" end up with the same score, so their order only depends on their
		// id.
		var documents1 = List.of(List.of(document("a"), document("c")));
		var documents2 = List.of(List.of(document("a"), document("d")));
		var documents3 = List.of(List.of(document("b"), document("a")));

		var oneOrder = new LinkedHashMap<Query, List<List<Document>>>();
		oneOrder.put(query1, documents1);
		oneOrder.put(query2, documents2);
		oneOrder.put(query3, documents3);

		var otherOrder = new LinkedHashMap<Query, List<List<Document>>>();
		otherOrder.put(query3, documents3);
		otherOrder.put(query2, documents2);
		otherOrder.put(query1, documents1);

		List<Document> result = documentJoiner.join(oneOrder);
		List<Document> reversedResult = documentJoiner.join(otherOrder);

		assertThat(ids(result)).containsExactly("a", "b", "c", "d");
		assertThat(ids(reversedResult)).containsExactlyElementsOf(ids(result));
		assertThat(scores(reversedResult)).containsExactlyElementsOf(scores(result));
	}

	@Test
	void whenSingleDocumentListThenOriginalOrderIsPreserved() {
		DocumentJoiner documentJoiner = new ReciprocalRankFusionDocumentJoiner();
		var documentsForQuery = new HashMap<Query, List<List<Document>>>();
		documentsForQuery.put(new Query("query1"),
				List.of(List.of(document("c"), document("a"), document("b"), document("d"))));

		List<Document> result = documentJoiner.join(documentsForQuery);

		assertThat(result).extracting(Document::getId).containsExactly("c", "a", "b", "d");
	}

	@Test
	void shouldUseTheGivenRankConstant() {
		DocumentJoiner documentJoiner = new ReciprocalRankFusionDocumentJoiner(1);
		var documentsForQuery = new HashMap<Query, List<List<Document>>>();
		documentsForQuery.put(new Query("query1"), List.of(List.of(document("a"), document("b"))));

		List<Document> result = documentJoiner.join(documentsForQuery);

		assertThat(scoreOf(result, "a")).isCloseTo(1.0 / 2, within(1e-12));
		assertThat(scoreOf(result, "b")).isCloseTo(1.0 / 3, within(1e-12));
	}

	@Test
	void shouldNotModifyTheInputDocuments() {
		DocumentJoiner documentJoiner = new ReciprocalRankFusionDocumentJoiner();
		var original = Document.builder().id("a").text("Content a").score(0.42).build();
		var documentsForQuery = new HashMap<Query, List<List<Document>>>();
		documentsForQuery.put(new Query("query1"), List.of(List.of(original, document("b"))));

		documentJoiner.join(documentsForQuery);

		assertThat(original.getScore()).isEqualTo(0.42);
		assertThat(documentsForQuery.get(new Query("query1")).get(0)).containsExactly(original, document("b"));
	}

	@Test
	void shouldPreserveTheDocumentFieldsAndReplaceOnlyTheScore() {
		DocumentJoiner documentJoiner = new ReciprocalRankFusionDocumentJoiner();
		var original = Document.builder()
			.id("a")
			.text("Content a")
			.metadata(Map.of("source", "example"))
			.score(0.42)
			.build();
		var documentsForQuery = new HashMap<Query, List<List<Document>>>();
		documentsForQuery.put(new Query("query1"), List.of(List.of(original)));

		List<Document> result = documentJoiner.join(documentsForQuery);

		assertThat(result).hasSize(1);
		assertThat(result.get(0).getId()).isEqualTo("a");
		assertThat(result.get(0).getText()).isEqualTo("Content a");
		assertThat(result.get(0).getMetadata()).containsEntry("source", "example");
		assertThat(result.get(0).getScore()).isCloseTo(1.0 / 61, within(1e-12));
	}

	private static Document document(String id) {
		return Document.builder().id(id).text("Content " + id).build();
	}

	private static Double scoreOf(List<Document> documents, String id) {
		return documents.stream().filter(document -> document.getId().equals(id)).findFirst().orElseThrow().getScore();
	}

	private static List<String> ids(List<Document> documents) {
		return documents.stream().map(Document::getId).toList();
	}

	private static List<Double> scores(List<Document> documents) {
		return documents.stream().map(Document::getScore).toList();
	}

}
