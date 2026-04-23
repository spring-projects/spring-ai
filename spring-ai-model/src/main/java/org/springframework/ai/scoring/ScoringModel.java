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

package org.springframework.ai.scoring;

import java.util.List;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.document.Document;
import org.springframework.ai.model.Model;
import org.springframework.ai.rag.Query;
import org.springframework.util.Assert;

/**
 * {@link ScoringModel} is a generic interface for scoring/reranking models used in
 * cross-encoder reranking for RAG pipelines. It evaluates query-document pairs and
 * returns relevance scores that can be used to reorder documents.
 *
 * @author Thomas Vitale
 * @author Christian Tzolov
 * @since 2.0.0
 *
 */
public interface ScoringModel extends Model<ScoringRequest, ScoringResponse> {

	@Override
	ScoringResponse call(ScoringRequest request);

	/**
	 * Scores a single query-document pair.
	 * @param query the search query
	 * @param document the document to score
	 * @return the relevance score
	 */
	default double score(String query, Document document) {
		Assert.notNull(query, "Query must not be null");
		Assert.notNull(document, "Document must not be null");
		ScoringResponse response = this.call(new ScoringRequest(query, List.of(document.getText()), null));
		return response.getResult().getScore();
	}

	/**
	 * Scores a batch of query-document pairs and returns ordered documents by score.
	 * @param query the search query
	 * @param documents the documents to score
	 * @return list of scored documents in descending relevance order
	 */
	default List<ScoringResult> scoreDocuments(String query, List<Document> documents) {
		Assert.notNull(query, "Query must not be null");
		Assert.notNull(documents, "Documents must not be null");
		Assert.notEmpty(documents, "Documents must not be empty");

		List<String> texts = documents.stream().map(Document::getText).toList();
		ScoringResponse response = this.call(new ScoringRequest(query, texts, null));

		List<ScoringResult> results = response.getResults();
		return results.stream()
			.sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
			.toList();
	}

}
