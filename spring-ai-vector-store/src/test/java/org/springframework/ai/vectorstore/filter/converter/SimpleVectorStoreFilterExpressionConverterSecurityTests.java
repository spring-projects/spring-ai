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

package org.springframework.ai.vectorstore.filter.converter;

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
 * {@link SimpleVectorStoreFilterExpressionConverter}.
 *
 * <p>
 * This test class verifies proper string escaping to prevent SpEL injection attacks in
 * Simple Vector Store filter expressions (SpEL syntax with single quotes).
 *
 * <p>
 * <b>Test Strategy:</b>
 * <ul>
 * <li>Tests verify Simple Vector Store SpEL syntax: {@code '...'}</li>
 * <li>SpEL-specific escaping: single quotes doubled ('')</li>
 * <li>Tests cover the most critical SpEL injection scenarios</li>
 * </ul>
 *
 * @author Zexuan Peng &lt;pengzexuan@gmail.com&gt;
 * @see <a href="https://github.com/spring-projects/spring-ai/issues/4545">Issue #4545</a>
 * @see SimpleVectorStoreFilterExpressionConverter
 */
@DisplayName("SimpleVectorStoreFilterExpressionConverter Security Tests - Issue #4545")
class SimpleVectorStoreFilterExpressionConverterSecurityTests {

	SimpleVectorStoreFilterExpressionConverter converter;

	@BeforeEach
	void setUp() {
		this.converter = new SimpleVectorStoreFilterExpressionConverter();
	}

	// ============================================================================
	// Basic SpEL String Escaping Tests
	// ============================================================================

	/**
	 * Test single quote escaping in SpEL string literals.
	 *
	 * <p>
	 * This test verifies that single quotes within string values are properly escaped to
	 * prevent SpEL injection.
	 *
	 * <pre>
	 * Input:  author == "John's Book"
	 * Output: #metadata['author'] == 'John''s Book'
	 * Issue:  #4545 - Single quotes must be doubled in SpEL
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
		assertThat(result).as("Output should contain #metadata").contains("#metadata");
		assertThat(result).as("Output should contain author").contains("author");
	}

	// ============================================================================
	// SpEL Injection Attack Scenarios
	// ============================================================================

	/**
	 * Test prevention of SpEL injection via single quotes.
	 *
	 * <p>
	 * This test verifies that malicious SpEL injection attempts are neutralized.
	 *
	 * <pre>
	 * Input:  country == "BG' or #metadata['country'] == 'hacker'"
	 * Expected: The entire input should be treated as a literal string
	 * Output: #metadata['country'] == 'BG'' or #metadata[''country''] == ''hacker''
	 * Issue:  #4545 - Without escaping, SpEL injection is possible
	 * Security Risk: HIGH - Allows SpEL injection attacks
	 * </pre>
	 */
	@Test
	@DisplayName("SpEL injection attack should be prevented")
	void testSpELInjectionAttack() {
		// Arrange
		String maliciousInput = "BG' or #metadata['country'] == 'hacker";
		Expression expr = new Expression(EQ, new Key("country"), new Value(maliciousInput));

		// Act
		String result = this.converter.convertExpression(expr);

		// Assert
		assertThat(result).as("Single quotes should be doubled").contains("''");
		assertThat(result).as("or keywords should be literal").contains(" or ");
		// Count that the escaped output has doubled quotes (more than original)
		long quoteCount = result.chars().filter(ch -> ch == '\'').count();
		assertThat(quoteCount).as("Should have more quotes than original (8)").isGreaterThan(8);
		assertThat(quoteCount).as("Quote count should be even (all doubled)").isEven();
	}

	/**
	 * Test prevention of SpEL expression injection.
	 *
	 * <p>
	 * This test verifies attempts to inject SpEL expressions.
	 *
	 * <pre>
	 * Input:  title == "test' && #metadata['secret'] != null"
	 * Expected: SpEL operators should be escaped as literal text
	 * Output: #metadata['title'] == 'test'' && #metadata[''secret''] != null'
	 * Issue:  #4545 - SpEL expression injection must be prevented
	 * Security Risk: HIGH - Could allow data exfiltration
	 * </pre>
	 */
	@Test
	@DisplayName("SpEL expression injection should be prevented")
	void testSpELExpressionInjection() {
		// Arrange
		String maliciousInput = "test' && #metadata['secret'] != null";
		Expression expr = new Expression(EQ, new Key("title"), new Value(maliciousInput));

		// Act
		String result = this.converter.convertExpression(expr);

		// Assert
		assertThat(result).as("Single quote should be doubled").contains("''");
		assertThat(result).as("&& operators should be literal within the string").contains(" && ");
		// The malicious input should be entirely within the string literal
		assertThat(result).as("Should contain #metadata as part of the string").contains("#metadata[''secret'']");
	}

	// ============================================================================
	// Real-world Scenarios
	// ============================================================================

	/**
	 * Test handling of contractions in English text.
	 *
	 * <p>
	 * This test verifies common English contractions that contain single quotes.
	 *
	 * <pre>
	 * Input:  title == "It's a wonderful day"
	 * Output: #metadata['title'] == 'It''s a wonderful day'
	 * Issue:  #4545 - English contractions require proper escaping
	 * Use Case: Search for document titles with contractions
	 * </pre>
	 */
	@Test
	@DisplayName("English contractions should be handled")
	void testEnglishContractions() {
		// Arrange
		Expression expr = new Expression(EQ, new Key("title"), new Value("It's a wonderful day"));

		// Act
		String result = this.converter.convertExpression(expr);

		// Assert
		assertThat(result).as("Single quote should be doubled").contains("''");
		assertThat(result).as("Output should contain It").contains("It");
		assertThat(result).as("Output should contain s a").contains("s a");
		assertThat(result).as("Output should contain wonderful day").contains("wonderful day");
	}

	/**
	 * Test handling of nested quotes in text.
	 *
	 * <p>
	 * This test verifies strings with multiple single quotes.
	 *
	 * <pre>
	 * Input:  author == "O'Brien's work"
	 * Output: #metadata['author'] == 'O''Brien''s work'
	 * Issue:  #4545 - Multiple quotes require proper escaping
	 * </pre>
	 */
	@Test
	@DisplayName("Multiple single quotes should be escaped")
	void testMultipleSingleQuotes() {
		// Arrange
		Expression expr = new Expression(EQ, new Key("author"), new Value("O'Brien's work"));

		// Act
		String result = this.converter.convertExpression(expr);

		// Assert
		assertThat(result).as("All single quotes should be doubled").contains("''");
		assertThat(result).as("Output should contain O").contains("O");
		assertThat(result).as("Output should contain Brien").contains("Brien");
		assertThat(result).as("Output should contain s work").contains("s work");
	}

}
