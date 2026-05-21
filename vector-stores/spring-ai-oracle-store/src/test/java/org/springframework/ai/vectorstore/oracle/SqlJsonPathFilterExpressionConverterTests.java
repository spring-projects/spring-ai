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

package org.springframework.ai.vectorstore.oracle;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionTextParser;

import static org.assertj.core.api.Assertions.assertThat;

public class SqlJsonPathFilterExpressionConverterTests {

	// Security test for metadataColumn identifier quoting
	@Test
	public void testMetadataColumnWithDoubleQuoteIsEscaped() {
		// Attempt SQL injection through metadataColumn parameter
		SqlJsonPathFilterExpressionConverter maliciousConverter = new SqlJsonPathFilterExpressionConverter(
				"meta\"); DROP TABLE users; --");
		Filter.Expression e = new Filter.Expression(Filter.ExpressionType.EQ, new Filter.Key("country"),
				new Filter.Value("BG"));
		String jsonPathExpression = maliciousConverter.convertExpression(e);
		// The identifier contains special characters, so it's quoted with escaped double
		// quotes
		assertThat(jsonPathExpression).startsWith("""
				JSON_EXISTS("meta""); DROP TABLE users; --", '""");
		assertThat(jsonPathExpression).doesNotContain("meta\"); DROP TABLE users;");
	}

	@Test
	public void testSimpleMetadataColumnNotQuoted() {
		// Simple alphanumeric column names should not be quoted to preserve
		// case-insensitive behavior
		SqlJsonPathFilterExpressionConverter converter = new SqlJsonPathFilterExpressionConverter("metadata");
		Filter.Expression e = new Filter.Expression(Filter.ExpressionType.EQ, new Filter.Key("country"),
				new Filter.Value("BG"));
		String jsonPathExpression = converter.convertExpression(e);
		assertThat(jsonPathExpression).startsWith("JSON_EXISTS(metadata, '");
	}

	@Test
	public void testNIN() {
		final Filter.Expression e = new FilterExpressionTextParser().parse("weather nin [\"windy\", \"rainy\"]");

		final String jsonPathExpression = new SqlJsonPathFilterExpressionConverter().convertExpression(e);

		assertThat(jsonPathExpression).isEqualTo("""
				JSON_EXISTS(metadata, '$?( !( @."weather" in ( "windy","rainy" ) ) )')""");
	}

	@Test
	public void testNOT() {
		final Filter.Expression e = new FilterExpressionTextParser().parse("NOT( weather in [\"windy\", \"rainy\"] )");

		final String jsonPathExpression = new SqlJsonPathFilterExpressionConverter().convertExpression(e);

		assertThat(jsonPathExpression).isEqualTo("""
				JSON_EXISTS(metadata, '$?( (!( @."weather" in ( "windy","rainy" ) )) )')""");
	}

	@Test
	public void testKeyWithSingleQuote() {
		final Filter.Expression e = new Filter.Expression(Filter.ExpressionType.EQ, new Filter.Key("x' OR 1=1--"),
				new Filter.Value("dummy"));

		final String jsonPathExpression = new SqlJsonPathFilterExpressionConverter().convertExpression(e);

		assertThat(jsonPathExpression).isEqualTo("""
				JSON_EXISTS(metadata, '$?( @."x'' OR 1=1--" == "dummy" )')""");
		assertThat(jsonPathExpression).doesNotContain("@.x' OR 1=1--'");
	}

	@Test
	public void testQuotedIdentifierFromTextParser() {
		final Filter.Expression e = new FilterExpressionTextParser().parse("'safe_key' == 'value'");

		final String jsonPathExpression = new SqlJsonPathFilterExpressionConverter().convertExpression(e);

		assertThat(jsonPathExpression).isEqualTo("""
				JSON_EXISTS(metadata, '$?( @."safe_key" == "value" )')""");
	}

	@Test
	public void testKeyWithApostrophe() {
		final Filter.Expression e = new Filter.Expression(Filter.ExpressionType.EQ, new Filter.Key("O'Brien"),
				new Filter.Value("test"));

		final String jsonPathExpression = new SqlJsonPathFilterExpressionConverter().convertExpression(e);

		assertThat(jsonPathExpression).isEqualTo("""
				JSON_EXISTS(metadata, '$?( @."O''Brien" == "test" )')""");
	}

	@Test
	public void testKeyWithDoubleQuote() {
		final Filter.Expression e = new Filter.Expression(Filter.ExpressionType.EQ, new Filter.Key("key\"inject"),
				new Filter.Value("v"));

		final String jsonPathExpression = new SqlJsonPathFilterExpressionConverter().convertExpression(e);

		assertThat(jsonPathExpression).isEqualTo("""
				JSON_EXISTS(metadata, '$?( @."key\\"inject" == "v" )')""");
		assertThat(jsonPathExpression).doesNotContain("@.\"key\"inject");
	}

	@Test
	public void testKeyWithBackslash() {
		final Filter.Expression e = new Filter.Expression(Filter.ExpressionType.EQ, new Filter.Key("key\\inject"),
				new Filter.Value("v"));

		final String jsonPathExpression = new SqlJsonPathFilterExpressionConverter().convertExpression(e);

		assertThat(jsonPathExpression).isEqualTo("""
				JSON_EXISTS(metadata, '$?( @."key\\\\inject" == "v" )')""");
	}

	@Test
	public void testValueWithApostrophe() {
		final Filter.Expression e = new Filter.Expression(Filter.ExpressionType.EQ, new Filter.Key("author"),
				new Filter.Value("O'Connor"));

		final String jsonPathExpression = new SqlJsonPathFilterExpressionConverter().convertExpression(e);

		assertThat(jsonPathExpression).isEqualTo("""
				JSON_EXISTS(metadata, '$?( @."author" == "O''Connor" )')""");
		assertThat(jsonPathExpression).doesNotContain("\"O'Connor\")");
	}

	@Test
	public void testValueWithSqlInjectionAttempt() {
		final Filter.Expression e = new Filter.Expression(Filter.ExpressionType.EQ, new Filter.Key("field"),
				new Filter.Value("' OR 1=1--"));

		final String jsonPathExpression = new SqlJsonPathFilterExpressionConverter().convertExpression(e);

		assertThat(jsonPathExpression).isEqualTo("""
				JSON_EXISTS(metadata, '$?( @."field" == "'' OR 1=1--" )')""");
		assertThat(jsonPathExpression).doesNotContain("\"' OR 1=1--\")");
	}

	@Test
	public void testValueInListWithApostrophe() {
		final Filter.Expression e = new Filter.Expression(Filter.ExpressionType.IN, new Filter.Key("author"),
				new Filter.Value(List.of("O'Brien", "Smith")));

		final String jsonPathExpression = new SqlJsonPathFilterExpressionConverter().convertExpression(e);

		assertThat(jsonPathExpression).contains("\"O''Brien\"");
		assertThat(jsonPathExpression).contains("\"Smith\"");
		assertThat(jsonPathExpression).doesNotContain("\"O'Brien\",");
	}

	@Test
	public void testQuotedKeyWithApostropheFromTextParser() {
		final Filter.Expression e = new FilterExpressionTextParser().parse("\"O'Brien\" == 'x'");

		final String jsonPathExpression = new SqlJsonPathFilterExpressionConverter().convertExpression(e);

		assertThat(jsonPathExpression).isEqualTo("""
				JSON_EXISTS(metadata, '$?( @."O''Brien" == "x" )')""");
	}

}
