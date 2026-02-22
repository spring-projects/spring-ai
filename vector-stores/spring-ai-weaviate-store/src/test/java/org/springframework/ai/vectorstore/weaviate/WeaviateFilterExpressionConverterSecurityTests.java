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

package org.springframework.ai.vectorstore.weaviate;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.Filter.Key;
import org.springframework.ai.vectorstore.filter.Filter.Value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.EQ;

/**
 * Security tests for string escaping vulnerability in {@link WeaviateFilterExpressionConverter}.
 *
 * <p>
 * This test class verifies proper string escaping to prevent injection attacks in
 * Weaviate-specific filter expressions (valueText and valueDate formats).
 *
 * <p>
 * <b>Test Strategy:</b>
 * <ul>
 * <li>Tests verify Weaviate-specific filter syntax: {@code valueText:"..."}</li>
 * <li>All tests are written with the expectation of proper escaping after the fix</li>
 * <li>Tests cover the most critical injection scenarios for Weaviate filters</li>
 * </ul>
 *
 * @author Zexuan Peng &lt;pengzexuan2001@gmail.com&gt;
 * @see <a href="https://github.com/spring-projects/spring-ai/issues/4545">Issue #4545</a>
 * @see WeaviateFilterExpressionConverter
 */
@DisplayName("WeaviateFilterExpressionConverter Security Tests - Issue #4545")
class WeaviateFilterExpressionConverterSecurityTests {

	WeaviateFilterExpressionConverter converter;

	@BeforeEach
	void setUp() {
		this.converter = new WeaviateFilterExpressionConverter(List.of("country", "author", "title", "path"));
	}

	// ============================================================================
	// Basic String Escaping Tests
	// ============================================================================

	/**
	 * Test double quote escaping in Weaviate valueText format.
	 *
	 * <p>
	 * This test verifies that double quotes within string values are properly escaped to
	 * prevent Weaviate filter syntax breakage.
	 *
	 * <pre>
	 * Input:  author == "John \"The Boss\" Doe"
	 * Output: path:["meta_author"]&#10;operator:Equal &#10;valueText:"John \"The Boss\" Doe"
	 * Issue:  #4545 - Quotes must be escaped in valueText format
	 * </pre>
	 */
	@Test
	@DisplayName("Double quotes should be escaped in valueText format")
	void testDoubleQuoteEscapingInValueText() {
		// Arrange
		Expression expr = new Expression(EQ, new Key("author"), new Value("John \"The Boss\" Doe"));

		// Act
		String result = this.converter.convertExpression(expr);

		// Assert
		assertThat(result).as("Double quotes should be escaped with backslash").contains("\\\"");
		assertThat(result).as("Output should contain valueText format").contains("valueText:");
		assertThat(result).as("Output should contain the full name").contains("John");
		assertThat(result).as("Output should contain The Boss").contains("The Boss");
		assertThat(result).as("Output should contain Doe").contains("Doe");
	}

	/**
	 * Test backslash escaping in Weaviate valueText format.
	 *
	 * <p>
	 * This test verifies that backslashes within string values are properly escaped.
	 *
	 * <pre>
	 * Input:  path == "C:\Users\admin"
	 * Output: path:["meta_path"]&#10;operator:Equal &#10;valueText:"C:\\Users\\admin"
	 * Issue:  #4545 - Backslashes must be escaped in valueText format
	 * </pre>
	 */
	@Test
	@DisplayName("Backslashes should be escaped in valueText format")
	void testBackslashEscapingInValueText() {
		// Arrange
		Expression expr = new Expression(EQ, new Key("path"), new Value("C:\\Users\\admin"));

		// Act
		String result = this.converter.convertExpression(expr);

		// Assert
		assertThat(result).as("Backslashes should be escaped").contains("\\\\");
		assertThat(result).as("Output should contain the path").contains("Users");
		assertThat(result).as("Output should contain admin").contains("admin");
	}

	// ============================================================================
	// Injection Attack Scenarios
	// ============================================================================

	/**
	 * Test prevention of quote-based injection attacks in Weaviate filters.
	 *
	 * <p>
	 * This test verifies that malicious input containing quotes cannot break out of the
	 * valueText literal and alter the filter logic.
	 *
	 * <pre>
	 * Input:  country == "BG\" OR valueText:\"hacker"
	 * Expected: The entire input should be treated as a literal string
	 * Output: path:["meta_country"]&#10;operator:Equal &#10;valueText:"BG\" OR valueText:\"hacker"
	 * Issue:  #4545 - Without escaping, the attacker can bypass the filter
	 * Security Risk: HIGH - Allows unauthorized data access
	 * </pre>
	 */
	@Test
	@DisplayName("Quote injection attack should be prevented in valueText")
	void testQuoteInjectionAttackInValueText() {
		// Arrange
		String maliciousInput = "BG\" OR valueText:\"hacker";
		Expression expr = new Expression(EQ, new Key("country"), new Value(maliciousInput));

		// Act
		String result = this.converter.convertExpression(expr);

		// Assert
		assertThat(result).as("Quotes should be escaped to prevent injection").contains("\\\"");
		assertThat(result).as("Malicious content should be literal").contains("OR valueText");
		assertThat(result).as("The string should not create a valid filter expression").doesNotContain("}{");
	}

	/**
	 * Test prevention of nested operator injection attacks.
	 *
	 * <p>
	 * This test verifies attempts to inject Weaviate operators into string values.
	 *
	 * <pre>
	 * Input:  title == "test\"} operator:And {path:[\"id\"] operator:Equal valueText:\"admin"
	 * Expected: Entire input treated as literal string
	 * Issue:  #4545 - Complex injection attempts must also be prevented
	 * Security Risk: HIGH - Advanced injection technique
	 * </pre>
	 */
	@Test
	@DisplayName("Nested operator injection should be prevented")
	void testNestedOperatorInjection() {
		// Arrange
		String maliciousInput = "test\"} operator:And {path:[\"id\"] operator:Equal valueText:\"admin";
		Expression expr = new Expression(EQ, new Key("title"), new Value(maliciousInput));

		// Act
		String result = this.converter.convertExpression(expr);

		// Assert
		assertThat(result).as("All quotes should be escaped").contains("\\\"");
		assertThat(result).as("operator keywords should be literal").contains("operator:And");
		assertThat(result).as("Should not create multiple path specifications").doesNotMatch("path:\\[.*path:\\[");
	}

	// ============================================================================
	// Real-world Scenarios
	// ============================================================================

	/**
	 * Test Windows file path handling in Weaviate filters.
	 *
	 * <p>
	 * This test verifies a common use case: Windows file paths with backslashes.
	 *
	 * <pre>
	 * Input:  path == "C:\Program Files\MyApp\config.json"
	 * Output: path:["meta_path"]&#10;operator:Equal &#10;valueText:"C:\\Program Files\\MyApp\\config.json"
	 * Issue:  #4545 - File paths are a common real-world scenario
	 * Use Case: Windows file system paths in metadata filters
	 * </pre>
	 */
	@Test
	@DisplayName("File paths with backslashes should be escaped")
	void testFilePathEscaping() {
		// Arrange
		Expression expr = new Expression(EQ, new Key("path"), new Value("C:\\Program Files\\MyApp\\config.json"));

		// Act
		String result = this.converter.convertExpression(expr);

		// Assert
		assertThat(result).as("All backslashes should be escaped").contains("\\\\");
		assertThat(result).as("Output should contain Program Files").contains("Program Files");
		assertThat(result).as("Output should contain MyApp").contains("MyApp");
		assertThat(result).as("Output should contain config.json").contains("config.json");
	}

	/**
	 * Test handling of JSON content as string values in Weaviate filters.
	 *
	 * <p>
	 * This test verifies scenarios where the metadata itself contains JSON snippets that
	 * need to be properly escaped.
	 *
	 * <pre>
	 * Input:  title == "{\"key\": \"value\"}"
	 * Output: path:["meta_title"]&#10;operator:Equal &#10;valueText:"{\"key\": \"value\"}"
	 * Issue:  #4545 - Nested JSON structures require proper escaping
	 * Use Case: Storing JSON snippets in metadata fields
	 * </pre>
	 */
	@Test
	@DisplayName("JSON strings within values should be escaped")
	void testJsonStringInValue() {
		// Arrange
		Expression expr = new Expression(EQ, new Key("title"), new Value("{\"key\": \"value\"}"));

		// Act
		String result = this.converter.convertExpression(expr);

		// Assert
		assertThat(result).as("All quotes should be escaped").contains("\\\"");
		assertThat(result).as("Output should contain key").contains("key");
		assertThat(result).as("Output should contain value").contains("value");
	}

}
