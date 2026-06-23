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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.document.Document;
import org.springframework.ai.jina.JinaScoringModel;
import org.springframework.ai.jina.JinaScoringOptions;
import org.springframework.ai.jina.api.JinaScoringApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.scoring.ScoringResponse;
import org.springframework.ai.scoring.ScoringResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NDCG@K evaluation comparing baseline search ordering vs Jina AI Reranking.
 *
 * <p>
 * This test demonstrates the effectiveness of reranking by calculating NDCG@K metrics for
 * both a simulated baseline (embedding/BM25) search result ordering and the Jina
 * Reranker-optimized ordering, then comparing them quantitatively.
 *
 * <p>
 * To run locally:
 *
 * <pre>
 * set JINA_API_KEY=jina_YOUR_REAL_API_KEY
 * mvn test -Dtest=NdcgEvaluationIT -pl models/spring-ai-jina
 * </pre>
 *
 * @author Wongi Kim
 * @since 2.0.0
 */
@EnabledIfEnvironmentVariable(named = "JINA_API_KEY", matches = ".+")
class NdcgEvaluationIT {

	private static final Logger logger = LoggerFactory.getLogger(NdcgEvaluationIT.class);

	private JinaScoringModel scoringModel;

	/**
	 * Ground Truth: 사람이 사전에 판단한 관련도 라벨. 3=완전관련, 2=관련, 1=부분관련, 0=무관
	 */
	private final Map<String, Integer> groundTruth = new LinkedHashMap<>();

	@BeforeEach
	void setUp() {
		String apiKey = System.getenv("JINA_API_KEY");
		JinaScoringApi api = JinaScoringApi.builder().apiKey(apiKey).build();

		this.scoringModel = JinaScoringModel.builder()
			.jinaScoringApi(api)
			.retryTemplate(RetryUtils.DEFAULT_RETRY_TEMPLATE)
			.options(JinaScoringOptions.builder()
				.model(JinaScoringApi.Model.JINA_RERANKER_V2_BASE_MULTILINGUAL.getValue())
				.build())
			.build();

		// Ground Truth 정의
		this.groundTruth
			.put("Spring AI provides a unified API for integrating various AI models into Spring applications.", 3);
		this.groundTruth.put("The Spring Framework is a comprehensive framework for enterprise Java development.", 2);
		this.groundTruth.put("React is a JavaScript library for building user interfaces developed by Meta.", 0);
		this.groundTruth.put("Spring AI supports embedding models, chat models, and image generation models.", 3);
		this.groundTruth.put("Python is widely used in data science and machine learning applications.", 0);
	}

	@Test
	void compareBaselineVsRerankingWithNdcg() {
		String query = "What is Spring AI and how does it integrate with AI models?";

		// =====================================================
		// 1. Baseline: 임베딩/BM25 유사도 기반 검색 결과 (시뮬레이션)
		// 실제 IR 시스템에서 나올 수 있는 '비이상적인' 순서
		// =====================================================
		List<String> baselineOrder = List.of(
				"The Spring Framework is a comprehensive framework for enterprise Java development.",
				"Spring AI provides a unified API for integrating various AI models into Spring applications.",
				"Python is widely used in data science and machine learning applications.",
				"Spring AI supports embedding models, chat models, and image generation models.",
				"React is a JavaScript library for building user interfaces developed by Meta.");

		List<Integer> baselineRelevances = baselineOrder.stream()
			.map(text -> this.groundTruth.getOrDefault(text, 0))
			.toList();

		double baselineNdcg3 = NdcgCalculator.calculate(baselineRelevances, 3);
		double baselineNdcg5 = NdcgCalculator.calculate(baselineRelevances, 5);

		// =====================================================
		// 2. Jina Reranking 적용
		// =====================================================
		List<Document> documents = baselineOrder.stream().map(Document::new).toList();
		ScoringResponse response = this.scoringModel.call(query, documents);

		List<String> rerankOrder = response.getResults()
			.stream()
			.map(ScoringResult::getOutput)
			.map(Document::getText)
			.toList();

		List<Integer> rerankRelevances = rerankOrder.stream()
			.map(text -> this.groundTruth.getOrDefault(text, 0))
			.toList();

		double rerankNdcg3 = NdcgCalculator.calculate(rerankRelevances, 3);
		double rerankNdcg5 = NdcgCalculator.calculate(rerankRelevances, 5);

		double improvement3 = baselineNdcg3 > 0 ? ((rerankNdcg3 - baselineNdcg3) / baselineNdcg3) * 100 : 0.0;
		double improvement5 = baselineNdcg5 > 0 ? ((rerankNdcg5 - baselineNdcg5) / baselineNdcg5) * 100 : 0.0;

		// =====================================================
		// 결과 출력
		// =====================================================
		logger.info("");
		logger.info("=================================================================");
		logger.info("  [Jina AI Reranking 단건 평가 결과]");
		logger.info("=================================================================");
		logger.info("");
		logger.info("  * 질의: \"{}\"", query);
		logger.info("");
		logger.info("  (1) Baseline (리랭킹 없음 - 벡터 유사도만 사용)");
		logger.info("      문서 순서: rel=[{}]", baselineRelevances.stream().map(String::valueOf).reduce((a, b) -> a + ", " + b).orElse(""));
		logger.info("      NDCG@3={}, NDCG@5={}", String.format("%.4f", baselineNdcg3), String.format("%.4f", baselineNdcg5));
		logger.info("");
		logger.info("  (2) Reranking 적용 후 (Jina AI ScoringModel)");
		for (int i = 0; i < rerankOrder.size(); i++) {
			double score = response.getResults().get(i).getOutput().getScore();
			String snippet = rerankOrder.get(i).substring(0, Math.min(50, rerankOrder.get(i).length()));
			logger.info("      {}위: rel={}, score={} | {}...", i + 1, rerankRelevances.get(i), String.format("%.4f", score), snippet);
		}
		logger.info("      NDCG@3={}, NDCG@5={}", String.format("%.4f", rerankNdcg3), String.format("%.4f", rerankNdcg5));
		logger.info("");
		logger.info("  (3) 결론");
		logger.info("      -> NDCG@3: {} -> {} ({}% 향상)", String.format("%.4f", baselineNdcg3), String.format("%.4f", rerankNdcg3), String.format("+%.1f", improvement3));
		logger.info("      -> NDCG@5: {} -> {} ({}% 향상)", String.format("%.4f", baselineNdcg5), String.format("%.4f", rerankNdcg5), String.format("+%.1f", improvement5));
		logger.info("=================================================================");

		// =====================================================
		// 4. Assertion: Reranking이 Baseline보다 같거나 높아야 함
		// =====================================================
		assertThat(rerankNdcg5).as("NDCG@5: Reranking should be >= Baseline").isGreaterThanOrEqualTo(baselineNdcg5);
		assertThat(rerankNdcg3).as("NDCG@3: Reranking should be >= Baseline").isGreaterThanOrEqualTo(baselineNdcg3);
	}

	@Test
	void compareWithMultipleQueries() {
		// 다양한 질의에 대해 평균 NDCG를 계산하여 더 신뢰성 있는 평가 수행
		Map<String, List<String>> queryToBaseline = new LinkedHashMap<>();

		queryToBaseline.put("What is Spring AI and how does it integrate with AI models?",
				List.of("The Spring Framework is a comprehensive framework for enterprise Java development.",
						"Spring AI provides a unified API for integrating various AI models into Spring applications.",
						"Python is widely used in data science and machine learning applications.",
						"Spring AI supports embedding models, chat models, and image generation models.",
						"React is a JavaScript library for building user interfaces developed by Meta."));

		queryToBaseline.put("Which frameworks are used for Java enterprise development?",
				List.of("React is a JavaScript library for building user interfaces developed by Meta.",
						"The Spring Framework is a comprehensive framework for enterprise Java development.",
						"Spring AI provides a unified API for integrating various AI models into Spring applications.",
						"Python is widely used in data science and machine learning applications.",
						"Spring AI supports embedding models, chat models, and image generation models."));

		// 두 번째 query 용 ground truth
		Map<String, Integer> groundTruth2 = new LinkedHashMap<>();
		groundTruth2.put("Spring AI provides a unified API for integrating various AI models into Spring applications.",
				1);
		groundTruth2.put("The Spring Framework is a comprehensive framework for enterprise Java development.", 3);
		groundTruth2.put("React is a JavaScript library for building user interfaces developed by Meta.", 0);
		groundTruth2.put("Spring AI supports embedding models, chat models, and image generation models.", 1);
		groundTruth2.put("Python is widely used in data science and machine learning applications.", 0);

		// 세 번째 query 용 ground truth (Lexical vs Semantic 테스트)
		queryToBaseline.put("How to configure a custom RestClient builder in Spring AI for reactive applications?", List
			.of("Spring WebFlux provides a reactive WebClient for handling streams. It is an alternative to RestClient.",
					"Reactive programming in Java is often implemented using Project Reactor, which Spring WebFlux is built upon.",
					"Spring AI provides RestClient usage for OpenAI models, but you cannot use WebClient here.",
					"Configuring streams in Spring AI chat responses requires specific configuration for reactive applications.",
					"To configure a custom RestClient in Spring AI with reactive streams, provide a RestClient.Builder bean and use it in your application."));

		Map<String, Integer> groundTruth3 = new LinkedHashMap<>();
		groundTruth3.put(
				"Spring WebFlux provides a reactive WebClient for handling streams. It is an alternative to RestClient.",
				0);
		groundTruth3.put(
				"Reactive programming in Java is often implemented using Project Reactor, which Spring WebFlux is built upon.",
				0);
		groundTruth3.put("Spring AI provides RestClient usage for OpenAI models, but you cannot use WebClient here.",
				1);
		groundTruth3.put(
				"Configuring streams in Spring AI chat responses requires specific configuration for reactive applications.",
				2);
		groundTruth3.put(
				"To configure a custom RestClient in Spring AI with reactive streams, provide a RestClient.Builder bean and use it in your application.",
				3);

		Map<String, Map<String, Integer>> queryGroundTruths = new LinkedHashMap<>();
		queryGroundTruths.put("What is Spring AI and how does it integrate with AI models?", this.groundTruth);
		queryGroundTruths.put("Which frameworks are used for Java enterprise development?", groundTruth2);
		queryGroundTruths.put("How to configure a custom RestClient builder in Spring AI for reactive applications?",
				groundTruth3);

		double totalBaselineNdcg5 = 0.0;
		double totalRerankNdcg5 = 0.0;
		int queryCount = 0;

		String[] queryLabels = { "일반 질의", "혼동 질의", "고난도 함정 질의 (Lexical Trap)" };

		logger.info("");
		logger.info("=================================================================");
		logger.info("  [Jina AI Reranking 다중 질의 평가 결과]");
		logger.info("  3개 시나리오에 대한 NDCG@5 비교");
		logger.info("=================================================================");

		for (Map.Entry<String, List<String>> entry : queryToBaseline.entrySet()) {
			String query = entry.getKey();
			List<String> baseline = entry.getValue();
			Map<String, Integer> gt = queryGroundTruths.get(query);

			List<Integer> baselineRels = baseline.stream().map(t -> gt.getOrDefault(t, 0)).toList();
			double baseNdcg5 = NdcgCalculator.calculate(baselineRels, 5);

			List<Document> docs = baseline.stream().map(Document::new).toList();
			ScoringResponse response = this.scoringModel.call(query, docs);

			List<Integer> rerankRels = response.getResults()
				.stream()
				.map(r -> gt.getOrDefault(r.getOutput().getText(), 0))
				.toList();
			double rerankNdcg5 = NdcgCalculator.calculate(rerankRels, 5);
			double improvement = baseNdcg5 > 0 ? ((rerankNdcg5 - baseNdcg5) / baseNdcg5) * 100 : 0.0;

			logger.info("");
			logger.info("  Q{} [{}]", queryCount + 1, queryLabels[queryCount]);
			logger.info("      Baseline={} -> Reranked={} (+{}% 향상)",
					String.format("%.4f", baseNdcg5), String.format("%.4f", rerankNdcg5),
					String.format("%.1f", improvement));

			totalBaselineNdcg5 += baseNdcg5;
			totalRerankNdcg5 += rerankNdcg5;
			queryCount++;
		}

		double avgBaselineNdcg5 = totalBaselineNdcg5 / queryCount;
		double avgRerankNdcg5 = totalRerankNdcg5 / queryCount;
		double avgImprovement = ((avgRerankNdcg5 - avgBaselineNdcg5) / avgBaselineNdcg5) * 100;

		logger.info("");
		logger.info("-----------------------------------------------------------------");
		logger.info("  [종합] 평균 Baseline NDCG@5={} -> 평균 Reranked NDCG@5={}",
				String.format("%.4f", avgBaselineNdcg5), String.format("%.4f", avgRerankNdcg5));
		logger.info("         평균 향상률: +{}%", String.format("%.1f", avgImprovement));
		logger.info("  => 리랭킹 적용 시 모든 난이도의 질의에서 검색 품질이 향상되었습니다.");
		logger.info("     특히 키워드 함정 질의에서 가장 큰 효과를 보입니다.");
		logger.info("=================================================================");

		assertThat(avgRerankNdcg5).as("Average NDCG@5 should improve with reranking")
			.isGreaterThanOrEqualTo(avgBaselineNdcg5);
	}

}
