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

package org.springframework.ai.jina.evaluation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Utility class for calculating NDCG@K (Normalized Discounted Cumulative Gain).
 *
 * <p>
 * NDCG is a standard information retrieval metric that measures the quality of a ranked
 * list of results by comparing it against an ideal ranking. Values range from 0.0 (worst)
 * to 1.0 (perfect ranking).
 *
 * @author Wongi Kim
 * @since 2.0.0
 */
public final class NdcgCalculator {

	private NdcgCalculator() {
	}

	/**
	 * Calculate NDCG@K for a ranked list of relevance scores.
	 * @param relevanceScores relevance scores in the order they were ranked
	 * @param k the cutoff position (top-K)
	 * @return NDCG@K value between 0.0 and 1.0
	 */
	public static double calculate(List<Integer> relevanceScores, int k) {
		if (relevanceScores == null || relevanceScores.isEmpty() || k <= 0) {
			return 0.0;
		}

		int effectiveK = Math.min(k, relevanceScores.size());

		double dcg = computeDcg(relevanceScores, effectiveK);
		double idcg = computeIdcg(relevanceScores, effectiveK);

		if (idcg == 0.0) {
			return 0.0;
		}

		return dcg / idcg;
	}

	/**
	 * Compute DCG@K (Discounted Cumulative Gain).
	 *
	 * <p>
	 * Formula: DCG@K = Σ (rel_i / log₂(i + 1)) for i = 1 to K
	 */
	static double computeDcg(List<Integer> relevanceScores, int k) {
		double dcg = 0.0;
		for (int i = 0; i < k; i++) {
			double rel = relevanceScores.get(i);
			dcg += rel / (Math.log(i + 2) / Math.log(2));
		}
		return dcg;
	}

	/**
	 * Compute IDCG@K (Ideal Discounted Cumulative Gain). Sorts the relevance scores in
	 * descending order first, then computes DCG.
	 */
	static double computeIdcg(List<Integer> relevanceScores, int k) {
		List<Integer> sorted = new ArrayList<>(relevanceScores);
		sorted.sort(Collections.reverseOrder());
		return computeDcg(sorted, k);
	}

}
