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

package org.springframework.ai.tool.toolsearch.index.lucene;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import org.springframework.ai.tool.toolsearch.ToolReference;
import org.springframework.ai.tool.toolsearch.ToolSearchRequest;
import org.springframework.ai.tool.toolsearch.ToolSearchResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link LuceneToolIndex}.
 *
 * @author Christian Tzolov
 */
class LuceneToolIndexTests {

	private static final String SESSION = "session-1";

	private ToolReference ref(String name, String description) {
		return ToolReference.builder().toolName(name).summary(description).build();
	}

	private ToolSearchResponse search(LuceneToolIndex searcher, String query) {
		return searcher.search(new ToolSearchRequest(SESSION, query, null, null));
	}

	@Test
	void matchesByDescription() throws Exception {
		try (LuceneToolIndex searcher = new LuceneToolIndex()) {
			searcher.indexTool(SESSION, ref("weatherTool", "Returns current weather conditions"));
			searcher.indexTool(SESSION, ref("calculatorTool", "Performs arithmetic calculations"));

			ToolSearchResponse response = search(searcher, "weather conditions");

			assertThat(response.toolReferences()).hasSize(1);
			assertThat(response.toolReferences().get(0).toolName()).isEqualTo("weatherTool");
		}
	}

	@Test
	void matchesByToolName() throws Exception {
		// StandardAnalyzer lowercases tokens; "getWeather" -> "getweather".
		// Now it is a TextField so the QueryBuilder could match it.
		try (LuceneToolIndex searcher = new LuceneToolIndex()) {
			searcher.indexTool(SESSION, ref("getWeather", "Retrieves meteorological data"));
			searcher.indexTool(SESSION, ref("calculateTax", "Computes tax obligations"));

			ToolSearchResponse response = search(searcher, "getWeather");

			assertThat(response.toolReferences()).isNotEmpty();
			assertThat(response.toolReferences().get(0).toolName()).isEqualTo("getWeather");
		}
	}

	@Test
	void nameMatchRanksAboveDescriptionMatch() throws Exception {
		// "weather" appears as the full name of one tool and inside the description of
		// another. The name field carries a 4x boost, so the name-exact-match must win.
		try (LuceneToolIndex searcher = new LuceneToolIndex()) {
			searcher.indexTool(SESSION, ref("weather", "Current conditions provider"));
			searcher.indexTool(SESSION, ref("forecast", "Provides detailed weather predictions daily"));

			ToolSearchResponse response = search(searcher, "weather");

			assertThat(response.toolReferences()).hasSizeGreaterThanOrEqualTo(1);
			assertThat(response.toolReferences().get(0).toolName()).isEqualTo("weather");
		}
	}

	@Test
	void indexToolsBatchAddsAllTools() throws Exception {
		try (LuceneToolIndex searcher = new LuceneToolIndex()) {
			searcher.indexTools(SESSION, List.of(ref("tool1", "First tool description"),
					ref("tool2", "Second tool description"), ref("tool3", "Third tool description")));

			assertThat(searcher.size(SESSION)).isEqualTo(3);
		}
	}

	@Test
	void noResultsForEmptySession() throws Exception {
		try (LuceneToolIndex searcher = new LuceneToolIndex()) {
			ToolSearchResponse response = search(searcher, "weather");

			assertThat(response.toolReferences()).isEmpty();
		}
	}

	@Test
	void clearIndexRemovesAllTools() throws Exception {
		try (LuceneToolIndex searcher = new LuceneToolIndex()) {
			searcher.indexTools(SESSION, List.of(ref("tool1", "desc one"), ref("tool2", "desc two")));

			searcher.clearIndex(SESSION);

			assertThat(searcher.size(SESSION)).isEqualTo(0);
			assertThat(search(searcher, "desc").toolReferences()).isEmpty();
		}
	}

	@Test
	void sessionIsolationPreventsLeakage() throws Exception {
		try (LuceneToolIndex searcher = new LuceneToolIndex()) {
			searcher.indexTool(SESSION, ref("weatherTool", "Weather data source"));
			searcher.indexTool("session-2", ref("calculatorTool", "Math operations tool"));

			ToolSearchResponse response = searcher.search(new ToolSearchRequest("session-2", "weather", null, null));

			assertThat(response.toolReferences()).isEmpty();
		}
	}

	@Test
	void maxResultsLimitsOutput() throws Exception {
		try (LuceneToolIndex searcher = new LuceneToolIndex()) {
			for (int i = 0; i < 8; i++) {
				searcher.indexTool(SESSION, ref("dataTool" + i, "Retrieves data from source " + i));
			}

			ToolSearchResponse response = searcher.search(new ToolSearchRequest(SESSION, "data", 3, null));

			assertThat(response.toolReferences()).hasSizeLessThanOrEqualTo(3);
		}
	}

	@Test
	void lazyCommitMakesToolsVisibleAtSearchTime() throws Exception {
		try (LuceneToolIndex searcher = new LuceneToolIndex()) {
			// No explicit commit — tools are flushed inside the first search call
			searcher.indexTool(SESSION, ref("stockTool", "Fetches live stock prices"));
			searcher.indexTool(SESSION, ref("newsTool", "Returns latest news headlines"));

			ToolSearchResponse response = search(searcher, "stock prices");

			assertThat(response.toolReferences()).isNotEmpty();
			assertThat(response.toolReferences().get(0).toolName()).isEqualTo("stockTool");
		}
	}

	@Test
	void concurrentSearchesAreThreadSafe() throws Exception {
		// Concurrent calls to ensureAndGetReader() on the same
		// session must not leak readers or throw due to unsynchronised reader assignment.
		// minScore=0 avoids IDF collapse: with 10 identical-token documents the BM25
		// score stays above zero but falls below the default 0.25 threshold.
		try (LuceneToolIndex searcher = new LuceneToolIndex(0.0f)) {
			for (int i = 0; i < 10; i++) {
				searcher.indexTool(SESSION, ref("tool" + i, "Processes data stream number " + i));
			}

			int threadCount = 8;
			ExecutorService executor = Executors.newFixedThreadPool(threadCount);
			List<Future<ToolSearchResponse>> futures = new ArrayList<>(threadCount);

			for (int i = 0; i < threadCount; i++) {
				futures.add(executor.submit(() -> search(searcher, "data")));
			}

			executor.shutdown();
			assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

			for (Future<ToolSearchResponse> future : futures) {
				assertThat(future.get().toolReferences()).isNotEmpty();
			}
		}
	}

}
