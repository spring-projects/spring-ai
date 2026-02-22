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
import org.springframework.ai.vectorstore.filter.FilterExpressionConverter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.EQ;

/**
 * Security tests for string escaping vulnerability in
 * {@link org.springframework.ai.vectorstore.filter.converter.AbstractFilterExpressionConverter}.
 *
 * <p>
 * This test class verifies proper string escaping to prevent injection attacks and syntax
 * breakage in filter expressions. It addresses Issue #4545 where special characters
 * (quotes, backslashes, etc.) were not being properly escaped, leading to potential
 * security vulnerabilities.
 *
 * <p>
 * <b>Test Strategy:</b>
 * <ul>
 * <li>Tests use PineconeFilterExpressionConverter as it directly inherits the vulnerable
 * doSingleValue() method from AbstractFilterExpressionConverter</li>
 * <li>All tests are written with the expectation of proper escaping after the fix</li>
 * <li>Tests will fail until Issue #4545 is fixed, providing continuous reminder</li>
 * </ul>
 *
 * @author Zexuan Peng &lt;pengzexuan2001@gmail.com&gt;
 * @see <a href="https://github.com/spring-projects/spring-ai/issues/4545">Issue #4545</a>
 * <p>
 */
@DisplayName("AbstractFilterExpressionConverter Security Tests - Issue #4545")
class AbstractFilterExpressionConverterSecurityTests {

	private FilterExpressionConverter converter;

	@BeforeEach
	void setUp() {
		this.converter = new PineconeFilterExpressionConverter();
	}

	// ============================================================================
	// Basic Special Character Tests
	// ============================================================================

	/**
	 * Test double quote escaping in string values.
	 *
	 * <p>
	 * This test verifies that double quotes within string values are properly escaped to
	 * prevent filter syntax breakage and injection attacks.
	 *
	 * <pre>
	 * Input:  author == "John "The Boss" Doe"
	 * Output: {"author": {"$eq": "John \"The Boss\" Doe"}}
	 * Issue:  #4545 - Quotes are currently not escaped, breaking JSON syntax
	 * </pre>
	 */
	@Test
	@DisplayName("Double quotes in string values should be escaped")
	void testDoubleQuoteEscaping() {

		// Arrange
		Expression expr = new Expression(EQ, new Key("author"), new Value("John \"The Boss\" Doe"));

		// Act
		String result = this.converter.convertExpression(expr);

		// Assert
		assertThat(result).as("Double quotes should be escaped with backslash").contains("\\\"");
		assertThat(result).as("Output should contain the full name").contains("John");
		assertThat(result).as("Output should contain The Boss").contains("The Boss");
		assertThat(result).as("Output should contain Doe").contains("Doe");
	}

	/**
	 * Test backslash escaping in string values.
	 *
	 * <p>
	 * This test verifies that backslashes within string values are properly escaped to
	 * prevent unintended escape sequences.
	 *
	 * <pre>
	 * Input:  path == "C:\Users\admin"
	 * Output: {"path": {"$eq": "C:\\Users\\admin"}}
	 * Issue:  #4545 - Backslashes are currently not escaped
	 * </pre>
	 */
	@Test
	@DisplayName("Backslashes in string values should be escaped")
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

	/**
	 * Test single quote handling in JSON string literals.
	 *
	 * <p>
	 * This test verifies that single quotes within string values are preserved as-is in
	 * JSON format. Unlike SQL/OData/SpEL which use single quotes as string delimiters,
	 * JSON uses double quotes, so single quotes are ordinary characters that don't need
	 * escaping.
	 *
	 * <pre>
	 * Input:  title == "John's Book"
	 * Output: {"title": {"$eq": "John's Book"}}
	 * Note:   Single quotes are NOT escaped in JSON (only double quotes need escaping)
	 * Issue:  #4545 - Single quotes may cause issues in some implementations but not in JSON
	 * </pre>
	 */
	@Test
	@DisplayName("Single quotes should be preserved in JSON format (no escaping needed)")
	void testSingleQuoteHandling() {
		// Arrange
		Expression expr = new Expression(EQ, new Key("title"), new Value("John's Book"));

		// Act
		String result = this.converter.convertExpression(expr);

		// Assert
		assertThat(result).as("Output should contain the title with single quote").contains("John's Book");
	}

	// ============================================================================
	// Combined Special Characters Tests
	// ============================================================================

	/**
	 * Test escaping of quote and backslash combinations.
	 *
	 * <p>
	 * This test verifies complex scenarios with mixed special characters.
	 *
	 * <pre>
	 * Input:  message == "Path: C:\\\"Hello\\\""
	 * Output: {"message": {"$eq": "Path: C:\\\\\"Hello\\\\\""}}
	 * Issue:  #4545 - Both backslashes and quotes need proper escaping
	 * </pre>
	 */
	@Test
	@DisplayName("Quote and backslash combinations should be escaped")
	void testQuoteAndBackslashCombinations() {

		// Arrange
		Expression expr = new Expression(EQ, new Key("message"), new Value("Path: C:\\\"Hello\\\""));

		// Act
		String result = this.converter.convertExpression(expr);

		// Assert
		assertThat(result).as("Backslash should be escaped").contains("\\\\");
		assertThat(result).as("Quote should be escaped").contains("\\\"");
		assertThat(result).as("Output should contain Hello").contains("Hello");
	}

	/**
	 * Test escaping of consecutive special characters.
	 *
	 * <p>
	 * This test verifies strings with multiple consecutive special characters.
	 *
	 * <pre>
	 * Input:  pattern == "\"\\\'"
	 * Output: {"pattern": {"$eq": "\"\\\\\'"}}
	 * Issue:  #4545 - Consecutive special characters complicate escaping
	 * </pre>
	 */
	@Test
	@DisplayName("Consecutive special characters should be escaped")
	void testConsecutiveSpecialCharacters() {
		// Arrange
		Expression expr = new Expression(EQ, new Key("pattern"), new Value("\"\\'"));

		// Act
		String result = this.converter.convertExpression(expr);

		// Assert
		assertThat(result).as("Special characters should be escaped").contains("\\");
	}

	// ============================================================================
	// Boundary Cases
	// ============================================================================

	/**
	 * Test handling of empty string values.
	 *
	 * <p>
	 * This test verifies that empty strings don't cause syntax errors.
	 *
	 * <pre>
	 * Input:  name == ""
	 * Output: {"name": {"$eq": ""}}
	 * Issue:  #4545 - Empty strings should not break the filter
	 * </pre>
	 */
	@Test
	@DisplayName("Empty string should be handled correctly")
	void testEmptyString() {
		// Arrange
		Expression expr = new Expression(EQ, new Key("name"), new Value(""));

		// Act
		String result = this.converter.convertExpression(expr);

		// Assert
		assertThat(result).as("Output should contain empty string").contains("\"\"");
	}

	/**
	 * Test escaping of strings containing only special characters.
	 *
	 * <p>
	 * This test verifies edge cases with strings composed entirely of characters that
	 * need escaping.
	 *
	 * <pre>
	 * Input:  symbol == "\\"
	 * Output: {"symbol": {"$eq": "\\\\"}}
	 * Issue:  #4545 - Special-only strings need careful handling
	 * </pre>
	 */
	@Test
	@DisplayName("String with only special characters should be escaped")
	void testOnlySpecialCharacters() {

		// Arrange
		Expression expr = new Expression(EQ, new Key("symbol"), new Value("\\"));

		// Act
		String result = this.converter.convertExpression(expr);

		// Assert
		assertThat(result).as("Backslash should be escaped").contains("\\\\");
	}

	/**
	 * Test escaping in long strings with embedded special characters.
	 *
	 * <p>
	 * This test verifies that escaping works correctly in longer text, not just short
	 * test strings.
	 *
	 * <pre>
	 * Input:  description == "This is a \"very long\" description with \\ paths"
	 * Output: {"description": {"$eq": "This is a \"very long\" description with \\ paths"}}
	 * Issue:  #4545 - Escaping must work consistently regardless of string length
	 * </pre>
	 */
	@Test
	@DisplayName("Long strings with embedded special characters should be escaped")
	void testLongStringWithSpecialCharacters() {
		// Arrange
		String longText = "This is a \"very long\" description with \\ paths and \"multiple\" special characters";
		Expression expr = new Expression(EQ, new Key("description"), new Value(longText));

		// Act
		String result = this.converter.convertExpression(expr);

		// Assert
		assertThat(result).as("All double quotes should be escaped").contains("\\\"");
		assertThat(result).as("All backslashes should be escaped").contains("\\\\");
		assertThat(result).as("Output should contain very long").contains("very long");
		assertThat(result).as("Output should contain description").contains("description");
	}

	// ============================================================================
	// Injection Attack Scenarios
	// ============================================================================

	/**
	 * Test prevention of quote-based injection attacks.
	 *
	 * <p>
	 * This test verifies that malicious input containing quotes cannot break out of the
	 * string literal and alter the filter logic.
	 *
	 * <pre>
	 * Input:  author == "admin" OR valueText:"hacker"
	 * Expected: The entire input should be treated as a literal string
	 * Output: {"author": {"$eq": "admin\" OR valueText:\"hacker"}}
	 * Issue:  #4545 - Without escaping, the attacker can bypass the filter
	 * Security Risk: HIGH - Allows unauthorized data access
	 * </pre>
	 */
	@Test
	@DisplayName("Quote injection attack should be prevented")
	void testQuoteInjectionAttack() {
		// Arrange
		String maliciousInput = "admin\" OR valueText:\"hacker";
		Expression expr = new Expression(EQ, new Key("author"), new Value(maliciousInput));

		// Act
		String result = this.converter.convertExpression(expr);

		// Assert
		assertThat(result).as("Quotes should be escaped to prevent injection").contains("\\\"");
		assertThat(result).as("Malicious content should be literal").contains("OR valueText");
		assertThat(result).as("The string should not create a valid filter expression").doesNotContain("}{");
	}

	/**
	 * Test prevention of logic operator injection attacks.
	 *
	 * <p>
	 * This test verifies that attempts to inject boolean logic operators into string
	 * values are neutralized by proper escaping.
	 *
	 * <pre>
	 * Input:  user == "admin" AND "1"="1"
	 * Expected: Entire input treated as literal string
	 * Output: {"user": {"$eq": "admin\" AND \"1\"=\"1"}}
	 * Issue:  #4545 - Without escaping, boolean injection is possible
	 * Security Risk: HIGH - Allows authentication bypass
	 * </pre>
	 */
	@Test
	@DisplayName("Logic operator injection should be prevented")
	void testLogicOperatorInjection() {

		// Arrange
		String maliciousInput = "admin\" AND \"1\"=\"1";
		Expression expr = new Expression(EQ, new Key("user"), new Value(maliciousInput));

		// Act
		String result = this.converter.convertExpression(expr);

		// Assert
		assertThat(result).as("Quotes should be escaped").contains("\\\"");
		assertThat(result).as("Boolean operators should be literal").contains("AND");
		assertThat(result).as("Should not create multiple conditions").doesNotMatch("\\}\\s*\\{");
	}

	/**
	 * Test prevention of nested quote injection attacks.
	 *
	 * <p>
	 * This test verifies more complex injection attempts with nested quotes.
	 *
	 * <pre>
	 * Input:  key == "value\" + \""
	 * Expected: Quotes escaped, no expression concatenation
	 * Output: {"key": {"$eq": "value\" + \""}}
	 * Issue:  #4545 - Complex injection attempts must also be prevented
	 * Security Risk: HIGH - Advanced injection technique
	 * </pre>
	 */
	@Test
	@DisplayName("Nested quote injection should be prevented")
	void testNestedQuoteInjection() {
		// Arrange
		String maliciousInput = "value\" + \"";
		Expression expr = new Expression(EQ, new Key("key"), new Value(maliciousInput));

		// Act
		String result = this.converter.convertExpression(expr);

		// Assert
		assertThat(result).as("All quotes should be escaped").contains("\\\"");
		assertThat(result).as("Plus sign should be literal").contains("+");
	}

	// ============================================================================
	// Unicode and Control Characters
	// ============================================================================

	/**
	 * Test handling of Unicode characters in string values.
	 *
	 * <p>
	 * This test verifies that Unicode characters are properly preserved and don't
	 * interfere with the escaping mechanism.
	 *
	 * <pre>
	 * Input:  name == "用户名"
	 * Output: {"name": {"$eq": "用户名"}}
	 * Issue:  #4545 - Unicode chars should be preserved without breaking escaping
	 * </pre>
	 */
	@Test
	@DisplayName("Unicode characters should be preserved")
	void testUnicodeCharacters() {
		// Arrange
		Expression expr = new Expression(EQ, new Key("name"), new Value("用户名"));

		// Act
		String result = this.converter.convertExpression(expr);

		// Assert
		assertThat(result).as("Unicode characters should be preserved").contains("用户名");
	}

	/**
	 * Test Unicode characters combined with special characters.
	 *
	 * <p>
	 * This test verifies that escaping works correctly even with Unicode characters
	 * present in the string.
	 *
	 * <pre>
	 * Input:  title == "测试\"Title\""
	 * Output: {"title": {"$eq": "测试\"Title\""}}
	 * Issue:  #4545 - Escaping must work with Unicode characters
	 * </pre>
	 */
	@Test
	@DisplayName("Unicode with special characters should be handled")
	void testUnicodeWithSpecialCharacters() {
		// Arrange
		Expression expr = new Expression(EQ, new Key("title"), new Value("测试\"Title\""));

		// Act
		String result = this.converter.convertExpression(expr);

		// Assert
		assertThat(result).as("Quotes should be escaped").contains("\\\"");
		assertThat(result).as("Unicode characters should be preserved").contains("测试");
		assertThat(result).as("ASCII text should be preserved").contains("Title");
	}

	/**
	 * Test escaping of control characters in string values.
	 *
	 * <p>
	 * This test verifies that control characters like newlines and tabs are properly
	 * escaped or handled.
	 *
	 * <pre>
	 * Input:  text == "line1\nline2\ttabbed"
	 * Output: {"text": {"$eq": "line1\nline2\ttabbed"}}
	 * Issue:  #4545 - Control characters may need escaping depending on implementation
	 * </pre>
	 */
	@Test
	@DisplayName("Control characters should be escaped")
	void testControlCharacters() {

		// Arrange
		Expression expr = new Expression(EQ, new Key("text"), new Value("line1\nline2\ttabbed"));

		// Act
		String result = this.converter.convertExpression(expr);

		// Assert
		assertThat(result).as("Output should contain line1").contains("line1");
		assertThat(result).as("Output should contain line2").contains("line2");
		assertThat(result).as("Output should contain tabbed").contains("tabbed");
	}

	// ============================================================================
	// Real-world Scenarios
	// ============================================================================

	/**
	 * Test real-world file path handling.
	 *
	 * <p>
	 * This test verifies a common use case: Windows file paths with backslashes.
	 *
	 * <pre>
	 * Input:  filepath == "C:\Program Files\MyApp\config.json"
	 * Output: {"filepath": {"$eq": "C:\\Program Files\\MyApp\\config.json"}}
	 * Issue:  #4545 - File paths are a common real-world scenario
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
	 * Test handling of JSON content as string values.
	 *
	 * <p>
	 * This test verifies scenarios where the metadata itself contains JSON snippets that
	 * need to be properly escaped.
	 *
	 * <pre>
	 * Input:  data == "{\"key\": \"value\"}"
	 * Output: {"data": {"$eq": "{\"key\": \"value\"}"}}
	 * Issue:  #4545 - Nested JSON structures require proper escaping
	 * Use Case: Storing JSON snippets in metadata fields
	 * </pre>
	 */
	@Test
	@DisplayName("JSON strings within values should be escaped")
	void testJsonStringInValue() {
		// Arrange
		Expression expr = new Expression(EQ, new Key("data"), new Value("{\"key\": \"value\"}"));

		// Act
		String result = this.converter.convertExpression(expr);

		// Assert
		assertThat(result).as("All quotes should be escaped").contains("\\\"");
		assertThat(result).as("Output should contain key").contains("key");
		assertThat(result).as("Output should contain value").contains("value");
	}

	/**
	 * Test handling of SQL-like fragments in string values.
	 *
	 * <p>
	 * This test verifies that SQL-like syntax embedded in strings is treated as literal
	 * content, not executable code.
	 *
	 * <pre>
	 * Input:  query == "SELECT * FROM users WHERE name='admin'"
	 * Output: {"query": {"$eq": "SELECT * FROM users WHERE name='admin'"}}
	 * Issue:  #4545 - SQL-like syntax must not be executed
	 * Use Case: Logging SQL queries or storing query templates
	 * Security Risk: HIGH - Without escaping, could lead to NoSQL injection
	 * </pre>
	 */
	@Test
	@DisplayName("SQL-like fragments should be escaped")
	void testSqlLikeFragments() {

		// Arrange
		Expression expr = new Expression(EQ, new Key("query"), new Value("SELECT * FROM users WHERE name='admin'"));

		// Act
		String result = this.converter.convertExpression(expr);

		// Assert
		assertThat(result).as("Output should contain SELECT").contains("SELECT");
		assertThat(result).as("Output should contain FROM users").contains("FROM users");
		assertThat(result).as("Output should contain WHERE").contains("WHERE");
		assertThat(result).as("Output should contain admin").contains("admin");
	}

}
