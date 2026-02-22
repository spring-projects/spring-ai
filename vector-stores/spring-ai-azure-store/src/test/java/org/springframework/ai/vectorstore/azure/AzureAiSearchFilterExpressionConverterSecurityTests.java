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

package org.springframework.ai.vectorstore.azure;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.ai.vectorstore.azure.AzureVectorStore.MetadataField;
import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.Filter.Key;
import org.springframework.ai.vectorstore.filter.Filter.Value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.EQ;

/**
 * Security tests for string escaping vulnerability in
 * {@link AzureAiSearchFilterExpressionConverter}.
 *
 * <p>
 * This test class verifies proper string escaping to prevent OData injection attacks in
 * Azure AI Search filter expressions (OData syntax with single quotes).
 *
 * <p>
 * <b>Test Strategy:</b>
 * <ul>
 * <li>Tests verify Azure AI Search OData syntax: {@code '...'}</li>
 * <li>OData-specific escaping: single quotes doubled ('')</li>
 * <li>Tests cover the most critical OData injection scenarios</li>
 * </ul>
 *
 * @author Zexuan Peng &lt;pengzexuan2001@gmail.com&gt;
 * @see <a href="https://github.com/spring-projects/spring-ai/issues/4545">Issue #4545</a>
 * @see AzureAiSearchFilterExpressionConverter
 */
@DisplayName("AzureAiSearchFilterExpressionConverter Security Tests - Issue #4545")
class AzureAiSearchFilterExpressionConverterSecurityTests {

	AzureAiSearchFilterExpressionConverter converter;

	@BeforeEach
	void setUp() {
		MetadataField field1 = MetadataField.text("country");
		MetadataField field2 = MetadataField.text("author");
		MetadataField field3 = MetadataField.text("title");
		this.converter = new AzureAiSearchFilterExpressionConverter(List.of(field1, field2, field3));
	}

	// ============================================================================
	// Basic OData String Escaping Tests
	// ============================================================================

	/**
	 * Test single quote escaping in OData string literals.
	 *
	 * <p>
	 * This test verifies that single quotes within string values are properly escaped to
	 * prevent OData injection.
	 *
	 * <pre>
	 * Input:  author == "John's Book"
	 * Output: meta_author eq 'John''s Book'
	 * Issue:  #4545 - Single quotes must be doubled in OData
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
		assertThat(result).as("Output should contain eq operator").contains(" eq ");
		assertThat(result).as("Output should contain meta_author").contains("meta_author");
	}

	// ============================================================================
	// OData Injection Attack Scenarios
	// ============================================================================

	/**
	 * Test prevention of OData injection via single quotes.
	 *
	 * <p>
	 * This test verifies that malicious OData injection attempts are neutralized.
	 *
	 * <pre>
	 * Input:  country == "BG' or meta_country eq 'hacker"
	 * Expected: The entire input should be treated as a literal string
	 * Output: meta_country eq 'BG'' or meta_country eq ''hacker'
	 * Issue:  #4545 - Without escaping, OData injection is possible
	 * Security Risk: HIGH - Allows OData injection attacks
	 * </pre>
	 */
	@Test
	@DisplayName("OData injection attack should be prevented")
	void testODataInjectionAttack() {
		// Arrange
		String maliciousInput = "BG' or meta_country eq 'hacker";
		Expression expr = new Expression(EQ, new Key("country"), new Value(maliciousInput));

		// Act
		String result = this.converter.convertExpression(expr);

		// Assert
		assertThat(result).as("Single quotes should be doubled").contains("''");
		assertThat(result).as("or keywords should be literal").contains(" or ");
		// Count that the escaped output has doubled quotes (more than original)
		long quoteCount = result.chars().filter(ch -> ch == '\'').count();
		assertThat(quoteCount).as("Should have more quotes than original (4)").isGreaterThan(4);
		assertThat(quoteCount).as("Quote count should be even (all doubled)").isEven();
	}

	/**
	 * Test prevention of boolean logic injection.
	 *
	 * <p>
	 * This test verifies attempts to inject OData boolean operators.
	 *
	 * <pre>
	 * Input:  title == "test' and true eq true"
	 * Expected: Operators should be escaped as literal text
	 * Output: meta_title eq 'test'' and true eq true'
	 * Issue:  #4545 - Boolean injection must be prevented
	 * Security Risk: HIGH - Could allow authentication bypass
	 * </pre>
	 */
	@Test
	@DisplayName("Boolean logic injection should be prevented")
	void testBooleanLogicInjection() {
		// Arrange
		String maliciousInput = "test' and true eq true";
		Expression expr = new Expression(EQ, new Key("title"), new Value(maliciousInput));

		// Act
		String result = this.converter.convertExpression(expr);

		// Assert
		assertThat(result).as("Single quote should be doubled").contains("''");
		assertThat(result).as("and keywords should be literal within the string").contains(" and ");
		// The malicious input contains "eq", which becomes part of the string literal
		assertThat(result).as("Output should contain the full malicious input as literal").contains("true eq true");
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
	 * Output: meta_title eq 'It''s a wonderful day'
	 * Issue:  #4545 - English contractions require proper escaping
	 * Use Case: Search for book titles with contractions
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
	 * Output: meta_author eq 'O''Brien''s work'
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
		// Count the doubled quotes - original had 2 quotes, should now have 4
		long quoteCount = result.chars().filter(ch -> ch == '\'').count();
		assertThat(quoteCount).as("Should have 6 single quotes (2 delimiters + 2 doubled)").isGreaterThanOrEqualTo(6);
	}

}
