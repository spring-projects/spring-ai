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

package org.springframework.ai.tool.toolsearch.index.regex;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import org.springframework.ai.tool.toolsearch.ToolReference;
import org.springframework.ai.tool.toolsearch.ToolSearchRequest;
import org.springframework.ai.tool.toolsearch.ToolSearchResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RegexToolIndex}.
 *
 * @author Christian Tzolov
 */
class RegexToolIndexTests {

	private static final String SESSION = "session-1";

	private ToolReference ref(String name, String description) {
		return ToolReference.builder().toolName(name).summary(description).build();
	}

	private ToolSearchResponse search(RegexToolIndex searcher, String query) {
		return searcher.search(new ToolSearchRequest(SESSION, query, null, null));
	}

	@Test
	void searchTypeIsRegex() {
		try (RegexToolIndex searcher = new RegexToolIndex()) {
			assertThat(searcher.getClass().getSimpleName()).isEqualTo("RegexToolIndex");
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	void matchesByToolName() {
		try (RegexToolIndex searcher = new RegexToolIndex()) {
			searcher.indexTool(SESSION, ref("weatherTool", "Returns current weather conditions"));
			searcher.indexTool(SESSION, ref("calculatorTool", "Performs arithmetic calculations"));

			ToolSearchResponse response = search(searcher, "weather");

			assertThat(response.toolReferences()).hasSize(1);
			assertThat(response.toolReferences().get(0).toolName()).isEqualTo("weatherTool");
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	void matchesByDescription() {
		try (RegexToolIndex searcher = new RegexToolIndex()) {
			searcher.indexTool(SESSION, ref("toolA", "Fetches stock price data"));
			searcher.indexTool(SESSION, ref("toolB", "Converts temperature units"));

			ToolSearchResponse response = search(searcher, "stock price");

			assertThat(response.toolReferences()).hasSize(1);
			assertThat(response.toolReferences().get(0).toolName()).isEqualTo("toolA");
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	void indexToolsBatchAddsAllTools() {
		try (RegexToolIndex searcher = new RegexToolIndex()) {
			searcher.indexTools(SESSION, List.of(ref("tool1", "First tool description"),
					ref("tool2", "Second tool description"), ref("tool3", "Third tool description")));

			assertThat(searcher.size(SESSION)).isEqualTo(3);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	void noResultsForEmptySession() {
		try (RegexToolIndex searcher = new RegexToolIndex()) {
			ToolSearchResponse response = search(searcher, "weather");

			assertThat(response.toolReferences()).isEmpty();
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	void clearIndexRemovesAllTools() {
		try (RegexToolIndex searcher = new RegexToolIndex()) {
			searcher.indexTools(SESSION, List.of(ref("tool1", "desc1"), ref("tool2", "desc2")));
			assertThat(searcher.size(SESSION)).isEqualTo(2);

			searcher.clearIndex(SESSION);

			assertThat(searcher.size(SESSION)).isEqualTo(0);
			assertThat(search(searcher, "tool").toolReferences()).isEmpty();
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	void sessionIsolationPreventsLeakage() {
		try (RegexToolIndex searcher = new RegexToolIndex()) {
			searcher.indexTool(SESSION, ref("weatherTool", "Weather data"));
			searcher.indexTool("session-2", ref("calculatorTool", "Math operations"));

			ToolSearchResponse response = searcher.search(new ToolSearchRequest("session-2", "weather", null, null));

			assertThat(response.toolReferences()).isEmpty();
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	void nameMatchScoresHigherThanDescriptionMatch() {
		try (RegexToolIndex searcher = new RegexToolIndex()) {
			searcher.indexTool(SESSION, ref("weatherTool", "General purpose tool"));
			searcher.indexTool(SESSION, ref("genericTool", "Provides weather forecasts"));

			ToolSearchResponse response = search(searcher, "weather");

			assertThat(response.toolReferences()).hasSize(2);
			assertThat(response.toolReferences().get(0).toolName()).isEqualTo("weatherTool");
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	void maxResultsLimitsOutput() {
		try (RegexToolIndex searcher = new RegexToolIndex()) {
			for (int i = 0; i < 10; i++) {
				searcher.indexTool(SESSION, ref("dataTool" + i, "Retrieves data from source " + i));
			}

			ToolSearchResponse response = searcher.search(new ToolSearchRequest(SESSION, "data", 3, null));

			assertThat(response.toolReferences()).hasSize(3);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	void convertQueryToRegexPattern() {
		try (RegexToolIndex searcher = new RegexToolIndex()) {
			assertThat(searcher.convertQueryToRegexPattern("weather tools")).isEqualTo("(?i)(weather|tools)");
			assertThat(searcher.convertQueryToRegexPattern("get user data")).isEqualTo("(?i)(get|user|data)");
			assertThat(searcher.convertQueryToRegexPattern("")).isEqualTo(".*");
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	void convertQueryToRegexPatternInTrLocale() {
		Locale defaultLocale = Locale.getDefault();
		try {
			Locale.setDefault(Locale.forLanguageTag("tr-TR"));
			try (RegexToolIndex searcher = new RegexToolIndex()) {
				// In turkish locale incorrect handling would result in
				// "(?i)(get|ınventory)" (dotless i)
				assertThat(searcher.convertQueryToRegexPattern("GET INVENTORY")).isEqualTo("(?i)(get|inventory)");
				assertThat(searcher.convertQueryToRegexPattern("")).isEqualTo(".*");
			}
			catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		finally {
			Locale.setDefault(defaultLocale);
		}
	}

	@Test
	void isValidPatternAcceptsValidRegex() {
		try (RegexToolIndex searcher = new RegexToolIndex()) {
			assertThat(searcher.isValidPattern("(?i)(weather|tools)")).isTrue();
			assertThat(searcher.isValidPattern("[invalid")).isFalse();
			assertThat(searcher.isValidPattern(null)).isFalse();
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}
