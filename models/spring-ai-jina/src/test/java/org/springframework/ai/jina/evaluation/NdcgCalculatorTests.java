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

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for {@link NdcgCalculator}.
 *
 * @author Wongi Kim
 */
class NdcgCalculatorTests {

	@Test
	void perfectRankingShouldReturnOne() {
		// 완벽한 정렬: [3, 3, 2, 0, 0] — 이미 이상적인 순서
		List<Integer> relevances = List.of(3, 3, 2, 0, 0);
		assertThat(NdcgCalculator.calculate(relevances, 5)).isCloseTo(1.0, within(0.0001));
		assertThat(NdcgCalculator.calculate(relevances, 3)).isCloseTo(1.0, within(0.0001));
	}

	@Test
	void worstRankingShouldReturnLowScore() {
		// 최악의 정렬: 무관 문서가 상위에
		List<Integer> relevances = List.of(0, 0, 2, 3, 3);
		double ndcg5 = NdcgCalculator.calculate(relevances, 5);
		assertThat(ndcg5).isLessThan(0.7);
		assertThat(ndcg5).isGreaterThan(0.0);
	}

	@Test
	void allIrrelevantShouldReturnZero() {
		// 모든 문서가 무관
		List<Integer> relevances = List.of(0, 0, 0, 0);
		assertThat(NdcgCalculator.calculate(relevances, 4)).isEqualTo(0.0);
	}

	@Test
	void emptyListShouldReturnZero() {
		assertThat(NdcgCalculator.calculate(List.of(), 5)).isEqualTo(0.0);
	}

	@Test
	void nullListShouldReturnZero() {
		assertThat(NdcgCalculator.calculate(null, 5)).isEqualTo(0.0);
	}

	@Test
	void kLargerThanListSizeShouldWork() {
		// K가 리스트보다 큰 경우 — 리스트 크기만큼만 계산
		List<Integer> relevances = List.of(3, 2);
		double ndcgK10 = NdcgCalculator.calculate(relevances, 10);
		double ndcgK2 = NdcgCalculator.calculate(relevances, 2);
		assertThat(ndcgK10).isEqualTo(ndcgK2);
	}

	@Test
	void baselineExampleFromDocumentation() {
		// 문서에 기재된 Baseline 예시: [2, 3, 0, 3, 0]
		List<Integer> baselineRelevances = List.of(2, 3, 0, 3, 0);
		double ndcg5 = NdcgCalculator.calculate(baselineRelevances, 5);

		// DCG@5 = 2/1 + 3/1.585 + 0/2 + 3/2.322 + 0/2.585 = 5.185
		// IDCG@5 = 3/1 + 3/1.585 + 2/2 + 0/2.322 + 0/2.585 = 5.893
		// NDCG@5 = 5.185 / 5.893 ≈ 0.8799
		assertThat(ndcg5).isCloseTo(0.8799, within(0.001));
	}

	@Test
	void dcgCalculationIsCorrect() {
		// 수동으로 DCG 검증: [3, 2, 0]
		// DCG = 3/log₂(2) + 2/log₂(3) + 0/log₂(4)
		// = 3/1.0 + 2/1.585 + 0/2.0
		// = 3.0 + 1.262 + 0.0 = 4.262
		List<Integer> relevances = List.of(3, 2, 0);
		double dcg = NdcgCalculator.computeDcg(relevances, 3);
		assertThat(dcg).isCloseTo(4.262, within(0.001));
	}

}
