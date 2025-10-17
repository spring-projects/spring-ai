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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

/**
 * Tests to verify that FilterExpressionConverter implementations properly escape special
 * characters to prevent injection attacks.
 *
 * @author Spring AI Team
 * @since 1.0.0
 */
class FilterExpressionConverterSecurityTests {

	@Test
	void testSimpleVectorStoreFilterExpressionConverterEscaping() {
		SimpleVectorStoreFilterExpressionConverter converter = new SimpleVectorStoreFilterExpressionConverter();

		// Test with malicious string containing quotes and escape sequences
		String maliciousValue = "'; DROP TABLE users; --";
		FilterExpressionBuilder builder = new FilterExpressionBuilder();
		Filter.Expression filter = builder.eq("testField", maliciousValue).build();

		String result = converter.convertExpression(filter);

		// Verify that the malicious string is properly escaped
		assertThat(result).contains("\\'");
		assertThat(result).contains("DROP TABLE users");
	}

	@Test
	void testSimpleVectorStoreFilterExpressionConverterWithNewlines() {
		SimpleVectorStoreFilterExpressionConverter converter = new SimpleVectorStoreFilterExpressionConverter();

		// Test with string containing newlines and tabs
		String valueWithNewlines = "line1\nline2\tline3";
		FilterExpressionBuilder builder = new FilterExpressionBuilder();
		Filter.Expression filter = builder.eq("testField", valueWithNewlines).build();

		String result = converter.convertExpression(filter);

		// Verify that newlines and tabs are properly escaped
		assertThat(result).contains("\\n");
		assertThat(result).contains("\\t");
	}

	@Test
	void testSimpleVectorStoreFilterExpressionConverterWithBackslashes() {
		SimpleVectorStoreFilterExpressionConverter converter = new SimpleVectorStoreFilterExpressionConverter();

		// Test with string containing backslashes
		String valueWithBackslashes = "path\\to\\file";
		FilterExpressionBuilder builder = new FilterExpressionBuilder();
		Filter.Expression filter = builder.eq("testField", valueWithBackslashes).build();

		String result = converter.convertExpression(filter);

		// Verify that backslashes are properly escaped
		assertThat(result).contains("\\\\");
	}

	@Test
	void testAbstractFilterExpressionConverterEscaping() {
		// Create a test converter that extends AbstractFilterExpressionConverter
		TestFilterExpressionConverter converter = new TestFilterExpressionConverter();

		// Test with malicious string
		String maliciousValue = "\"; DROP TABLE users; --";
		FilterExpressionBuilder builder = new FilterExpressionBuilder();
		Filter.Expression filter = builder.eq("testField", maliciousValue).build();

		String result = converter.convertExpression(filter);

		// Verify that the malicious string is properly escaped
		assertThat(result).contains("\\\"");
		assertThat(result).contains("DROP TABLE users");
	}

	@Test
	void testComplexInjectionAttempts() {
		SimpleVectorStoreFilterExpressionConverter converter = new SimpleVectorStoreFilterExpressionConverter();

		// Test various injection patterns
		String[] injectionPatterns = { "'; DROP TABLE users; --", "' OR 1=1 --",
				"'; INSERT INTO users VALUES ('hacker', 'password'); --", "' UNION SELECT * FROM users --",
				"'; UPDATE users SET password='hacked'; --" };

		for (String pattern : injectionPatterns) {
			FilterExpressionBuilder builder = new FilterExpressionBuilder();
			Filter.Expression filter = builder.eq("testField", pattern).build();

			String result = converter.convertExpression(filter);

			// Verify that quotes are properly escaped
			assertThat(result).contains("\\'");
		}
	}

	@Test
	void testUnicodeAndSpecialCharacters() {
		SimpleVectorStoreFilterExpressionConverter converter = new SimpleVectorStoreFilterExpressionConverter();

		// Test with Unicode characters
		String unicodeValue = "Hello 世界 🌍";
		FilterExpressionBuilder builder = new FilterExpressionBuilder();
		Filter.Expression filter = builder.eq("testField", unicodeValue).build();

		String result = converter.convertExpression(filter);

		// Unicode characters should be preserved
		assertThat(result).contains("Hello 世界 🌍");
	}

	/**
	 * Test implementation of AbstractFilterExpressionConverter for testing purposes.
	 */
	private static class TestFilterExpressionConverter extends AbstractFilterExpressionConverter {

		@Override
		protected void doExpression(Filter.Expression expression, StringBuilder context) {
			this.convertOperand(expression.left(), context);
			context.append(" == ");
			this.convertOperand(expression.right(), context);
		}

		@Override
		protected void doKey(Filter.Key key, StringBuilder context) {
			context.append(key.key());
		}

		@Override
		protected void doGroup(Filter.Group group, StringBuilder context) {
			context.append("(");
			super.doGroup(group, context);
			context.append(")");
		}

	}

}
