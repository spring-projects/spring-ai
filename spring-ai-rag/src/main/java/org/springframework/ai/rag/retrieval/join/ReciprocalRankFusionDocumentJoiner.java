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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.util.Assert;

/**
 * Combines documents retrieved based on multiple queries and from multiple data sources
 * by applying the reciprocal rank fusion algorithm, which merges the results based on the
 * rank each document has in each list instead of the scores assigned by the retrieval
 * strategy that produced it.
 * <p>
 * Each list of documents is treated as an independent ranked list, where the first
 * document is the most relevant one. A document occurring in several lists accumulates a
 * contribution of {@code 1 / (rankConstant + rank)} for each list where it occurs, so
 * that documents found by several queries or data sources are promoted. Documents are
 * aggregated by {@link Document#getId()}, and only the first occurrence of a document
 * within the same list contributes to the score. The result is a list of unique documents
 * sorted by their fused score in descending order, using the document id to break ties.
 * <p>
 * The score of each returned document is replaced with its fused score. Such a score is
 * only meaningful when compared with the score of the other documents returned by the
 * same invocation. It is not a similarity score, so any threshold defined for the scores
 * produced by a data source does not apply to it.
 * <p>
 * The caller is expected to use consistent document ids across the lists to join, which
 * is the case when the same data source is queried several times. Documents are never
 * matched based on their content or metadata.
 *
 * @author pj991207
 * @since 2.0.2
 * @see <a href="https://cormack.uwaterloo.ca/cormack/cormacksigir09-rrf.pdf">Reciprocal
 * Rank Fusion outperforms Condorcet and individual Rank Learning Methods</a>
 */
public class ReciprocalRankFusionDocumentJoiner implements DocumentJoiner {

	/**
	 * The rank constant used when none is provided, as suggested by the paper introducing
	 * the reciprocal rank fusion algorithm.
	 */
	public static final int DEFAULT_RANK_CONSTANT = 60;

	private static final Log logger = LogFactory.getLog(ReciprocalRankFusionDocumentJoiner.class);

	private final int rankConstant;

	/**
	 * Creates a joiner using the {@link #DEFAULT_RANK_CONSTANT}.
	 */
	public ReciprocalRankFusionDocumentJoiner() {
		this(DEFAULT_RANK_CONSTANT);
	}

	/**
	 * Creates a joiner using the given rank constant. The lower the value, the more the
	 * documents at the top of each list are favoured over the documents below them.
	 * @param rankConstant the constant added to each rank, must be greater than 0
	 */
	public ReciprocalRankFusionDocumentJoiner(int rankConstant) {
		Assert.isTrue(rankConstant > 0, "rankConstant must be greater than 0");
		this.rankConstant = rankConstant;
	}

	@Override
	public List<Document> join(Map<Query, List<List<Document>>> documentsForQuery) {
		Assert.notNull(documentsForQuery, "documentsForQuery cannot be null");
		Assert.noNullElements(documentsForQuery.keySet(), "documentsForQuery cannot contain null keys");
		Assert.noNullElements(documentsForQuery.values(), "documentsForQuery cannot contain null values");

		logger.debug("Joining documents by reciprocal rank fusion");

		Map<String, RankedDocument> rankedDocuments = new LinkedHashMap<>();
		for (List<List<Document>> documentLists : documentsForQuery.values()) {
			for (List<Document> documents : documentLists) {
				collectRanks(documents, rankedDocuments);
			}
		}

		return rankedDocuments.values()
			.stream()
			.map(rankedDocument -> rankedDocument.document.mutate()
				.score(rankedDocument.computeScore(this.rankConstant))
				.build())
			.sorted(Comparator
				.comparingDouble((Document document) -> document.getScore() != null ? document.getScore() : 0.0)
				.reversed()
				.thenComparing(Document::getId))
			.toList();
	}

	private static void collectRanks(List<Document> documents, Map<String, RankedDocument> rankedDocuments) {
		Set<String> idsInDocumentList = new HashSet<>();
		for (int index = 0; index < documents.size(); index++) {
			Document document = documents.get(index);
			if (idsInDocumentList.add(document.getId())) {
				rankedDocuments.computeIfAbsent(document.getId(), id -> new RankedDocument(document))
					.addRank(index + 1);
			}
		}
	}

	/**
	 * Accumulates the ranks a document has across the lists to join, together with the
	 * first occurrence of the document, used to build the joined document.
	 */
	private static final class RankedDocument {

		private final Document document;

		private final List<Integer> ranks = new ArrayList<>();

		private RankedDocument(Document document) {
			this.document = document;
		}

		private void addRank(int rank) {
			this.ranks.add(rank);
		}

		private double computeScore(int rankConstant) {
			double score = 0.0;
			// The ranks are accumulated in a fixed order so that the resulting score does
			// not depend on the iteration order of the map of documents to join.
			for (int rank : this.ranks.stream().sorted().toList()) {
				score += 1.0 / (rankConstant + rank);
			}
			return score;
		}

	}

}
