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

package org.springframework.ai.vectorstore.mariadb;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.Filter.Key;
import org.springframework.ai.vectorstore.filter.Filter.Value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.EQ;

/**
 * Security tests for string escaping vulnerability in
 * {@link MariaDBFilterExpressionConverter}.
 *
 * <p>
 * This test class verifies proper string escaping to prevent SQL injection attacks in
 * MariaDB-specific filter expressions (SQL string literal format).
 *
 * <p>
 * <b>Test Strategy:</b>
 * <ul>
 * <li>Tests verify MariaDB SQL syntax: {@code '...'}</li>
 * <li>SQL-specific escaping: single quotes doubled (''), backslashes escaped</li>
 * <li>Tests cover the most critical SQL injection scenarios</li>
 * </ul>
 *
 * @author Zexuan Peng &lt;pengzexuan@gmail.com&gt;
 * @see <a href="https://github.com/spring-projects/spring-ai/issues/4545">Issue #4545</a>
 * @see MariaDBFilterExpressionConverter
 */
@DisplayName("MariaDBFilterExpressionConverter Security Tests - Issue #4545")
class MariaDBFilterExpressionConverterSecurityTests {

	MariaDBFilterExpressionConverter converter;

	@BeforeEach
	void setUp() {
		this.converter = new MariaDBFilterExpressionConverter("metadata");
	}

	// ============================================================================
	// Basic SQL String Escaping Tests
	// ============================================================================

	/**
	 * Test single quote escaping in SQL string literals.
	 *
	 * <p>
	 * This test verifies that single quotes within string values are properly escaped to
	 * prevent SQL injection.
	 *
	 * <pre>
	 * Input:  author == "John's Book"
	 * Output: JSON_VALUE(metadata, '$.author') = 'John''s Book'
	 * Issue:  #4545 - Single quotes must be doubled in SQL
	 * </pre>
	 */
	@Test
	@DisplayName("Single quotes should be escaped by doubling")
	void testSingleQuoteEscaping() {
		// Arrange
		Expression expr = new Expression(EQ, new Key("author"), new Value("John's Book"));

		// Act
		String result = this.converter.convertExpression(expr);

		// Assert
		assertThat(result).as("Single quotes should be doubled").contains("John''s");
		assertThat(result).as("Output should contain JSON_VALUE").contains("JSON_VALUE");
		assertThat(result).as("Output should contain the author field").contains("$.author");
	}

	/**
	 * Test backslash escaping in SQL string literals.
	 *
	 * <p>
	 * This test verifies that backslashes within string values are properly escaped.
	 *
	 * <pre>
	 * Input:  path == "C:\Users\admin"
	 * Output: JSON_VALUE(metadata, '$.path') = 'C:\\Users\\admin'
	 * Issue:  #4545 - Backslashes must be escaped in SQL
	 * </pre>
	 */
	@Test
	@DisplayName("Backslashes should be escaped")
	void testBackslashEscaping() {
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
	// SQL Injection Attack Scenarios
	// ============================================================================

	/**
	 * Test prevention of SQL injection via single quotes.
	 *
	 * <p>
	 * This test verifies that malicious SQL injection attempts are neutralized.
	 *
	 * <pre>
	 * Input:  country == "BG' OR '1'='1"
	 * Expected: The entire input should be treated as a literal string
	 * Output: JSON_VALUE(metadata, '$.country') = 'BG'' OR ''1''=''1'
	 * Issue:  #4545 - Without escaping, SQL injection is possible
	 * Security Risk: HIGH - Allows SQL injection attacks
	 * </pre>
	 */
	@Test
	@DisplayName("SQL injection attack should be prevented")
	void testSqlInjectionAttack() {
		// Arrange
		String maliciousInput = "BG' OR '1'='1";
		Expression expr = new Expression(EQ, new Key("country"), new Value(maliciousInput));

		// Act
		String result = this.converter.convertExpression(expr);

		// Assert
		// All single quotes in the malicious input should be doubled ('')
		assertThat(result).as("Should contain doubled single quotes").contains("''");
		assertThat(result).as("OR keywords should be literal within the string").contains(" OR ");
		// The key test: original malicious input had 4 single quotes
		// After escaping, we should have doubled quotes (more than 4)
		long quoteCount = result.chars().filter(ch -> ch == '\'').count();
		assertThat(quoteCount).as("Should have more quotes than original (4)").isGreaterThan(4);
		assertThat(quoteCount).as("Quote count should be even (all doubled)").isEven();
	}

	/**
	 * Test prevention of comment-based SQL injection.
	 *
	 * <p>
	 * This test verifies attempts to inject SQL comments to bypass filters.
	 *
	 * <pre>
	 * Input:  name == "admin'--"
	 * Expected: Comment markers should be escaped
	 * Output: JSON_VALUE(metadata, '$.name') = 'admin''--'
	 * Issue:  #4545 - Comment injection must be prevented
	 * Security Risk: HIGH - Could allow authentication bypass
	 * </pre>
	 */
	@Test
	@DisplayName("SQL comment injection should be prevented")
	void testSqlCommentInjection() {
		// Arrange
		String maliciousInput = "admin'--";
		Expression expr = new Expression(EQ, new Key("name"), new Value(maliciousInput));

		// Act
		String result = this.converter.convertExpression(expr);

		// Assert
		assertThat(result).as("Single quote should be doubled").contains("''");
		assertThat(result).as("Comment marker should be literal").contains("--");
		// Verify \
		long quoteCount = result.chars().filter(ch -> ch == '\'').count();
		assertThat(quoteCount).as("Should have at least 4 quotes (2 delimiters + 1 doubled)").isGreaterThanOrEqualTo(4);
		assertThat(quoteCount).as("Quote count should be even").isEven();
	}

	// ============================================================================
	// Real-world Scenarios
	// ============================================================================

	/**
	 * Test Windows file path handling in SQL string literals.
	 *
	 * <p>
	 * This test verifies a common use case: Windows file paths with backslashes.
	 *
	 * <pre>
	 * Input:  filepath == "C:\Program Files\MyApp\config.json"
	 * Output: JSON_VALUE(metadata, '$.filepath') = 'C:\\Program Files\\MyApp\\config.json'
	 * Issue:  #4545 - File paths require backslash escaping
	 * Use Case: Windows file system paths in metadata filters
	 * </pre>
	 */
	@Test
	@DisplayName("File paths with backslashes should be escaped")
	void testFilePathEscaping() {
		// Arrange
		Expression expr = new Expression(EQ, new Key("filepath"), new Value("C:\\Program Files\\MyApp\\config.json"));

		// Act
		String result = this.converter.convertExpression(expr);

		// Assert
		assertThat(result).as("All backslashes should be escaped").contains("\\\\");
		assertThat(result).as("Output should contain Program Files").contains("Program Files");
		assertThat(result).as("Output should contain MyApp").contains("MyApp");
		assertThat(result).as("Output should contain config.json").contains("config.json");
	}

	/**
	 * Test handling of strings with both single quotes and backslashes.
	 *
	 * <p>
	 * This test verifies complex real-world scenarios with mixed special characters.
	 *
	 * <pre>
	 * Input:  text == "It's C:\drive"
	 * Output: JSON_VALUE(metadata, '$.text') = 'It''s C:\\drive'
	 * Issue:  #4545 - Mixed special characters require proper escaping order
	 * </pre>
	 */
	@Test
	@DisplayName("Mixed special characters should be escaped")
	void testMixedSpecialCharacters() {
		// Arrange
		Expression expr = new Expression(EQ, new Key("text"), new Value("It's C:\\drive"));

		// Act
		String result = this.converter.convertExpression(expr);

		// Assert
		assertThat(result).as("Single quote should be doubled").contains("''");
		assertThat(result).as("Backslash should be escaped").contains("\\\\");
		assertThat(result).as("Output should contain It").contains("It");
		assertThat(result).as("Output should contain s C:").contains("s C:");
		assertThat(result).as("Output should contain drive").contains("drive");
	}

}
