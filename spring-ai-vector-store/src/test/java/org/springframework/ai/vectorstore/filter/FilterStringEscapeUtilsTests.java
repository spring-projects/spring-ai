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

package org.springframework.ai.vectorstore.filter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link FilterStringEscapeUtils} to ensure proper escaping of special
 * characters and prevention of injection attacks in filter expressions.
 *
 * @author Spring AI Team
 * @since 1.0.0
 */
class FilterStringEscapeUtilsTests {

	@Test
	void testEscapeForGraphQL() {
		assertThat(FilterStringEscapeUtils.escapeForGraphQL("hello")).isEqualTo("hello");
		assertThat(FilterStringEscapeUtils.escapeForGraphQL("hello\"world")).isEqualTo("hello\\\"world");
		assertThat(FilterStringEscapeUtils.escapeForGraphQL("hello\\world")).isEqualTo("hello\\\\world");
		assertThat(FilterStringEscapeUtils.escapeForGraphQL("hello\"\\world")).isEqualTo("hello\\\"\\\\world");
	}

	@Test
	void testEscapeForSql() {
		assertThat(FilterStringEscapeUtils.escapeForSql("hello")).isEqualTo("hello");
		assertThat(FilterStringEscapeUtils.escapeForSql("hello'world")).isEqualTo("hello''world");
		assertThat(FilterStringEscapeUtils.escapeForSql("hello''world")).isEqualTo("hello''''world");
	}

	@Test
	void testEscapeForDoubleQuotes() {
		assertThat(FilterStringEscapeUtils.escapeForDoubleQuotes("hello")).isEqualTo("hello");
		assertThat(FilterStringEscapeUtils.escapeForDoubleQuotes("hello\"world")).isEqualTo("hello\\\"world");
		assertThat(FilterStringEscapeUtils.escapeForDoubleQuotes("hello\\world")).isEqualTo("hello\\\\world");
		assertThat(FilterStringEscapeUtils.escapeForDoubleQuotes("hello\"\\world")).isEqualTo("hello\\\"\\\\world");
	}

	@Test
	void testEscapeForSingleQuotes() {
		assertThat(FilterStringEscapeUtils.escapeForSingleQuotes("hello")).isEqualTo("hello");
		assertThat(FilterStringEscapeUtils.escapeForSingleQuotes("hello'world")).isEqualTo("hello\\'world");
		assertThat(FilterStringEscapeUtils.escapeForSingleQuotes("hello\\'world")).isEqualTo("hello\\\\\\'world");
	}

	@Test
	void testEscapeForJson() {
		assertThat(FilterStringEscapeUtils.escapeForJson("hello")).isEqualTo("hello");
		assertThat(FilterStringEscapeUtils.escapeForJson("hello\"world")).isEqualTo("hello\\\"world");
		assertThat(FilterStringEscapeUtils.escapeForJson("hello\\world")).isEqualTo("hello\\\\world");
		assertThat(FilterStringEscapeUtils.escapeForJson("hello\"\\world")).isEqualTo("hello\\\"\\\\world");
	}

	@Test
	void testEscapeForRedis() {
		assertThat(FilterStringEscapeUtils.escapeForRedis("hello")).isEqualTo("hello");
		assertThat(FilterStringEscapeUtils.escapeForRedis("hello\"world")).isEqualTo("hello\\\"world");
		assertThat(FilterStringEscapeUtils.escapeForRedis("hello\\world")).isEqualTo("hello\\\\world");
		assertThat(FilterStringEscapeUtils.escapeForRedis("hello\"\\world")).isEqualTo("hello\\\"\\\\world");
	}

	@Test
	void testEscapeForElasticsearch() {
		assertThat(FilterStringEscapeUtils.escapeForElasticsearch("hello")).isEqualTo("hello");
		assertThat(FilterStringEscapeUtils.escapeForElasticsearch("hello\"world")).isEqualTo("hello\\\"world");
		assertThat(FilterStringEscapeUtils.escapeForElasticsearch("hello\\world")).isEqualTo("hello\\\\world");
		assertThat(FilterStringEscapeUtils.escapeForElasticsearch("hello\"\\world")).isEqualTo("hello\\\"\\\\world");
	}

	@Test
	void testGenericEscapeMethod() {
		assertThat(FilterStringEscapeUtils.escape("hello\"world", FilterStringEscapeUtils.EscapeType.DOUBLE_QUOTES))
			.isEqualTo("hello\\\"world");
		assertThat(FilterStringEscapeUtils.escape("hello'world", FilterStringEscapeUtils.EscapeType.SINGLE_QUOTES))
			.isEqualTo("hello\\'world");
		assertThat(FilterStringEscapeUtils.escape("hello'world", FilterStringEscapeUtils.EscapeType.SQL))
			.isEqualTo("hello''world");
		assertThat(FilterStringEscapeUtils.escape("hello\"world", FilterStringEscapeUtils.EscapeType.GRAPHQL))
			.isEqualTo("hello\\\"world");
		assertThat(FilterStringEscapeUtils.escape("hello\"world", FilterStringEscapeUtils.EscapeType.JSON))
			.isEqualTo("hello\\\"world");
		assertThat(FilterStringEscapeUtils.escape("hello\"world", FilterStringEscapeUtils.EscapeType.REDIS))
			.isEqualTo("hello\\\"world");
		assertThat(FilterStringEscapeUtils.escape("hello\"world", FilterStringEscapeUtils.EscapeType.ELASTICSEARCH))
			.isEqualTo("hello\\\"world");
	}

	@Test
	void testInjectionAttackPrevention() {
		// Test strings that could be used for injection attacks
		String maliciousString = "\"; DROP TABLE users; --";
		String escaped = FilterStringEscapeUtils.escapeForDoubleQuotes(maliciousString);
		assertThat(escaped).isEqualTo("\\\"; DROP TABLE users; --");
		// The assertion should check for the presence of escaped characters, not the
		// absence of original ones
		assertThat(escaped).contains("\\\"");

		String maliciousString2 = "'; DROP TABLE users; --";
		String escaped2 = FilterStringEscapeUtils.escapeForSingleQuotes(maliciousString2);
		assertThat(escaped2).isEqualTo("\\'; DROP TABLE users; --");
		assertThat(escaped2).contains("\\'");

		// Test GraphQL injection attempt
		String graphqlInjection = "\"value\": \"injected\", \"malicious\": true";
		String escapedGraphQL = FilterStringEscapeUtils.escapeForGraphQL(graphqlInjection);
		assertThat(escapedGraphQL).isEqualTo("\\\"value\\\": \\\"injected\\\", \\\"malicious\\\": true");
		assertThat(escapedGraphQL).contains("\\\"");
	}

	@Test
	void testUnicodeAndSpecialCharacters() {
		// Test Unicode characters
		String unicodeString = "Hello 世界 🌍";
		assertThat(FilterStringEscapeUtils.escapeForDoubleQuotes(unicodeString)).isEqualTo(unicodeString);

		// Test mixed special characters
		String mixedString = "Hello\"World'Test\\New\nLine\tTab";
		String escaped = FilterStringEscapeUtils.escapeForDoubleQuotes(mixedString);
		assertThat(escaped).isEqualTo("Hello\\\"World'Test\\\\New\\nLine\\tTab");
		assertThat(escaped).contains("\\\"");
		assertThat(escaped).contains("\\n");
		assertThat(escaped).contains("\\t");
	}

}
