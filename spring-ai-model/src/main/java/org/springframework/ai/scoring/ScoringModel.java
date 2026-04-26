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

import org.springframework.ai.document.Document;
import org.springframework.ai.model.Model;

/**
 * Interface for scoring (reranking) models.
 *
 * @since 2.0.0
 */
public interface ScoringModel extends Model<ScoringRequest, ScoringResponse> {

	@Override
	ScoringResponse call(ScoringRequest request);

	/**
	 * Scores (reranks) a list of documents against a query.
	 * @param query the query to score against
	 * @param documents the list of documents to score
	 * @return the scoring response
	 */
	default ScoringResponse call(String query, List<Document> documents) {
		return call(new ScoringRequest(query, documents));
	}

}
