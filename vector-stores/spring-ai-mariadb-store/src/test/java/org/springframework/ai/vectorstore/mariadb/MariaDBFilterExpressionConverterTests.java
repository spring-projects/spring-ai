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

package org.springframework.ai.vectorstore.mariadb;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.Filter.Group;
import org.springframework.ai.vectorstore.filter.Filter.Key;
import org.springframework.ai.vectorstore.filter.Filter.Value;
import org.springframework.ai.vectorstore.filter.FilterExpressionConverter;
import org.springframework.ai.vectorstore.filter.FilterExpressionTextParser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.AND;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.EQ;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.GTE;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.IN;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.LTE;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.NE;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.NIN;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.OR;

/**
 * @author Diego Dupin
 */
public class MariaDBFilterExpressionConverterTests {

	FilterExpressionConverter converter = new MariaDBFilterExpressionConverter("metadata");

	@Test
	public void testEQ() {
		// country == "BG"
		String vectorExpr = this.converter.convertExpression(new Expression(EQ, new Key("country"), new Value("BG")));
		assertThat(vectorExpr).isEqualTo("JSON_VALUE(`metadata`, '$.\"country\"') = 'BG'");
	}

	@Test
	public void tesEqAndGte() {
		// genre == "drama" AND year >= 2020
		String vectorExpr = this.converter
			.convertExpression(new Expression(AND, new Expression(EQ, new Key("genre"), new Value("drama")),
					new Expression(GTE, new Key("year"), new Value(2020))));
		assertThat(vectorExpr).isEqualTo(
				"JSON_VALUE(`metadata`, '$.\"genre\"') = 'drama' AND JSON_VALUE(`metadata`, '$.\"year\"') >= 2020");
	}

	@Test
	public void tesIn() {
		// genre in ["comedy", "documentary", "drama"]
		String vectorExpr = this.converter.convertExpression(
				new Expression(IN, new Key("genre"), new Value(List.of("comedy", "documentary", "drama"))));
		assertThat(vectorExpr).isEqualTo("JSON_VALUE(`metadata`, '$.\"genre\"') IN ('comedy','documentary','drama')");
	}

	@Test
	public void testNe() {
		// year >= 2020 OR country == "BG" AND city != "Sofia"
		String vectorExpr = this.converter
			.convertExpression(new Expression(OR, new Expression(GTE, new Key("year"), new Value(2020)),
					new Expression(AND, new Expression(EQ, new Key("country"), new Value("BG")),
							new Expression(NE, new Key("city"), new Value("Sofia")))));
		assertThat(vectorExpr)
			.isEqualTo("JSON_VALUE(`metadata`, '$.\"year\"') >= 2020 OR JSON_VALUE(`metadata`, '$.\"country\"') = 'BG'"
					+ " AND JSON_VALUE(`metadata`, '$.\"city\"') != 'Sofia'");
	}

	@Test
	public void testGroup() {
		// (year >= 2020 OR country == "BG") AND city NIN ["Sofia", "Plovdiv"]
		String vectorExpr = this.converter.convertExpression(new Expression(AND,
				new Group(new Expression(OR, new Expression(GTE, new Key("year"), new Value(2020)),
						new Expression(EQ, new Key("country"), new Value("BG")))),
				new Expression(NIN, new Key("city"), new Value(List.of("Sofia", "Plovdiv")))));
		assertThat(vectorExpr)
			.isEqualTo("(JSON_VALUE(`metadata`, '$.\"year\"') >= 2020 OR JSON_VALUE(`metadata`, '$.\"country\"') ="
					+ " 'BG') AND JSON_VALUE(`metadata`, '$.\"city\"') NOT IN ('Sofia','Plovdiv')");
	}

	@Test
	public void testBoolean() {
		// isOpen == true AND year >= 2020 AND country IN ["BG", "NL", "US"]
		String vectorExpr = this.converter.convertExpression(new Expression(AND,
				new Expression(AND, new Expression(EQ, new Key("isOpen"), new Value(true)),
						new Expression(GTE, new Key("year"), new Value(2020))),
				new Expression(IN, new Key("country"), new Value(List.of("BG", "NL", "US")))));

		assertThat(vectorExpr)
			.isEqualTo("JSON_VALUE(`metadata`, '$.\"isOpen\"') = true AND JSON_VALUE(`metadata`, '$.\"year\"') >= 2020"
					+ " AND JSON_VALUE(`metadata`, '$.\"country\"') IN ('BG','NL','US')");
	}

	@Test
	public void testDecimal() {
		// temperature >= -15.6 && temperature <= +20.13
		String vectorExpr = this.converter
			.convertExpression(new Expression(AND, new Expression(GTE, new Key("temperature"), new Value(-15.6)),
					new Expression(LTE, new Key("temperature"), new Value(20.13))));

		assertThat(vectorExpr)
			.isEqualTo("JSON_VALUE(`metadata`, '$.\"temperature\"') >= -15.6 AND JSON_VALUE(`metadata`,"
					+ " '$.\"temperature\"') <= 20.13");
	}

	@Test
	public void testComplexIdentifiers() {
		String vectorExpr = this.converter
			.convertExpression(new Expression(EQ, new Key("\"country 1 2 3\""), new Value("BG")));
		assertThat(vectorExpr).isEqualTo("JSON_VALUE(`metadata`, '$.\"\\\\\"country 1 2 3\\\\\"\"') = 'BG'");
	}

	@Test
	public void testEmptyList() {
		// category IN []
		String vectorExpr = this.converter
			.convertExpression(new Expression(IN, new Key("category"), new Value(List.of())));
		assertThat(vectorExpr).isEqualTo("JSON_VALUE(`metadata`, '$.\"category\"') IN ()");
	}

	@Test
	public void testSingleItemList() {
		// status IN ["active"]
		String vectorExpr = this.converter
			.convertExpression(new Expression(IN, new Key("status"), new Value(List.of("active"))));
		assertThat(vectorExpr).isEqualTo("JSON_VALUE(`metadata`, '$.\"status\"') IN ('active')");
	}

	@Test
	public void testNullValue() {
		// description == null
		String vectorExpr = this.converter
			.convertExpression(new Expression(EQ, new Key("description"), new Value(null)));
		assertThat(vectorExpr).isEqualTo("JSON_VALUE(`metadata`, '$.\"description\"') = null");
	}

	@Test
	public void testNestedJsonPath() {
		// entity.profile.name == "EntityA"
		String vectorExpr = this.converter
			.convertExpression(new Expression(EQ, new Key("entity.profile.name"), new Value("EntityA")));
		assertThat(vectorExpr).isEqualTo("JSON_VALUE(`metadata`, '$.\"entity.profile.name\"') = 'EntityA'");
	}

	@Test
	public void testNumericStringValue() {
		// id == "1"
		String vectorExpr = this.converter.convertExpression(new Expression(EQ, new Key("id"), new Value("1")));
		assertThat(vectorExpr).isEqualTo("JSON_VALUE(`metadata`, '$.\"id\"') = '1'");
	}

	@Test
	public void testZeroValue() {
		// count == 0
		String vectorExpr = this.converter.convertExpression(new Expression(EQ, new Key("count"), new Value(0)));
		assertThat(vectorExpr).isEqualTo("JSON_VALUE(`metadata`, '$.\"count\"') = 0");
	}

	@Test
	public void testComplexNestedGroups() {
		// ((fieldA >= 100 AND fieldB == "X1") OR (fieldA >= 50 AND fieldB == "Y2")) AND
		// fieldC != "inactive"
		String vectorExpr = this.converter.convertExpression(new Expression(AND,
				new Group(new Expression(OR,
						new Group(new Expression(AND, new Expression(GTE, new Key("fieldA"), new Value(100)),
								new Expression(EQ, new Key("fieldB"), new Value("X1")))),
						new Group(new Expression(AND, new Expression(GTE, new Key("fieldA"), new Value(50)),
								new Expression(EQ, new Key("fieldB"), new Value("Y2")))))),
				new Expression(NE, new Key("fieldC"), new Value("inactive"))));

		assertThat(vectorExpr).isEqualTo(
				"((JSON_VALUE(`metadata`, '$.\"fieldA\"') >= 100 AND JSON_VALUE(`metadata`, '$.\"fieldB\"') = 'X1') OR "
						+ "(JSON_VALUE(`metadata`, '$.\"fieldA\"') >= 50 AND JSON_VALUE(`metadata`, '$.\"fieldB\"') = 'Y2')) AND "
						+ "JSON_VALUE(`metadata`, '$.\"fieldC\"') != 'inactive'");
	}

	@Test
	public void testMixedDataTypes() {
		// active == true AND score >= 1.5 AND tags IN ["featured", "premium"] AND
		// version == 1
		String vectorExpr = this.converter.convertExpression(new Expression(AND,
				new Expression(AND,
						new Expression(AND, new Expression(EQ, new Key("active"), new Value(true)),
								new Expression(GTE, new Key("score"), new Value(1.5))),
						new Expression(IN, new Key("tags"), new Value(List.of("featured", "premium")))),
				new Expression(EQ, new Key("version"), new Value(1))));

		assertThat(vectorExpr).isEqualTo(
				"JSON_VALUE(`metadata`, '$.\"active\"') = true AND JSON_VALUE(`metadata`, '$.\"score\"') >= 1.5 AND "
						+ "JSON_VALUE(`metadata`, '$.\"tags\"') IN ('featured','premium') AND JSON_VALUE(`metadata`, '$.\"version\"') = 1");
	}

	@Test
	public void testNinWithMixedTypes() {
		// status NIN ["A", "B", "C"]
		String vectorExpr = this.converter
			.convertExpression(new Expression(NIN, new Key("status"), new Value(List.of("A", "B", "C"))));
		assertThat(vectorExpr).isEqualTo("JSON_VALUE(`metadata`, '$.\"status\"') NOT IN ('A','B','C')");
	}

	@Test
	public void testEmptyStringValue() {
		// description != ""
		String vectorExpr = this.converter.convertExpression(new Expression(NE, new Key("description"), new Value("")));
		assertThat(vectorExpr).isEqualTo("JSON_VALUE(`metadata`, '$.\"description\"') != ''");
	}

	@Test
	public void testArrayIndexAccess() {
		// tags[0] == "important"
		String vectorExpr = this.converter
			.convertExpression(new Expression(EQ, new Key("tags[0]"), new Value("important")));
		assertThat(vectorExpr).isEqualTo("JSON_VALUE(`metadata`, '$.\"tags[0]\"') = 'important'");
	}

	// Security Tests - SQL Injection Prevention

	@Test
	public void testSqlInjectionWithSingleQuoteEscape() {
		// Attempt to inject: department == '' OR '1'='1'
		// Malicious value: ' OR '1'='1
		String maliciousValue = "' OR '1'='1";
		String vectorExpr = this.converter
			.convertExpression(new Expression(EQ, new Key("department"), new Value(maliciousValue)));

		// Expected format with SQL-escaped single quotes (doubled)
		// The single quote before OR should be doubled: ''' OR
		String expected = "JSON_VALUE(`metadata`, '$.\"department\"') = ''' OR ''1''=''1'";

		assertThat(vectorExpr).isEqualTo(expected);
	}

	@Test
	public void testSqlInjectionWithBackslashEscape() {
		// Attempt to inject using backslash escape: value\'
		String maliciousValue = "value\\'";
		String vectorExpr = this.converter
			.convertExpression(new Expression(EQ, new Key("field"), new Value(maliciousValue)));

		// Should escape both backslash and quote
		// Input: value\' → Output: value\\''' (backslash becomes \\, quote becomes '')
		assertThat(vectorExpr).isEqualTo("JSON_VALUE(`metadata`, '$.\"field\"') = 'value\\\\'''");
	}

	@Test
	public void testSqlInjectionWithDoubleQuote() {
		// Attempt to inject using double quotes: value" OR field="admin
		String maliciousValue = "value\" OR field=\"admin";
		String vectorExpr = this.converter
			.convertExpression(new Expression(EQ, new Key("field"), new Value(maliciousValue)));

		// In SQL single-quoted strings, double quotes don't need escaping
		// They are treated as literal characters
		assertThat(vectorExpr).isEqualTo("JSON_VALUE(`metadata`, '$.\"field\"') = 'value\" OR field=\"admin'");
	}

	@Test
	public void testSqlInjectionWithControlCharacters() {
		// Attempt to inject using newline: value\n OR field='admin'
		String maliciousValue = "value\n OR field='admin'";
		String vectorExpr = this.converter
			.convertExpression(new Expression(EQ, new Key("field"), new Value(maliciousValue)));

		// Should escape newline and single quotes
		assertThat(vectorExpr).isEqualTo("JSON_VALUE(`metadata`, '$.\"field\"') = 'value\\n OR field=''admin'''");
		assertThat(vectorExpr).contains("\\n");
		assertThat(vectorExpr).contains("''");
		// Verify newline is escaped (not a literal newline)
		assertThat(vectorExpr).doesNotContain("'\n");
	}

	@Test
	public void testSqlInjectionWithMultipleEscapes() {
		// Complex injection with multiple special characters
		String maliciousValue = "test'\"\\'\n\r\t";
		String vectorExpr = this.converter
			.convertExpression(new Expression(EQ, new Key("field"), new Value(maliciousValue)));

		// All special characters should be escaped according to SQL rules
		// Single quotes: doubled, backslashes: \\, control chars: \n, \r, \t
		// Double quotes: no escaping needed in SQL single-quoted strings
		assertThat(vectorExpr).isEqualTo("JSON_VALUE(`metadata`, '$.\"field\"') = 'test''\"\\\\''\\n\\r\\t'");
	}

	@Test
	public void testSqlInjectionInListValues() {
		// Attempt injection through IN clause
		String maliciousValue1 = "HR' OR department='Finance";
		String maliciousValue2 = "Engineering";
		String vectorExpr = this.converter.convertExpression(
				new Expression(IN, new Key("department"), new Value(List.of(maliciousValue1, maliciousValue2))));

		// Should escape single quotes in list values (doubled per SQL standard)
		assertThat(vectorExpr).contains("HR'' OR department=''Finance");
		assertThat(vectorExpr).contains("Engineering");
	}

	@Test
	public void testSqlInjectionInComplexExpression() {
		// Attempt injection in a complex AND/OR expression
		String maliciousValue = "' OR role='admin' OR dept='";
		String vectorExpr = this.converter
			.convertExpression(new Expression(AND, new Expression(EQ, new Key("department"), new Value(maliciousValue)),
					new Expression(GTE, new Key("year"), new Value(2020))));

		// Should not allow injection to break out of the expression
		// Single quotes should be doubled per SQL standard
		assertThat(vectorExpr).contains("'' OR role=''admin'' OR dept=''");
		// Verify the AND operator is still present (not broken by injection)
		assertThat(vectorExpr).contains(" AND ");
	}

	@Test
	public void testNormalStringsNotAffected() {
		// Verify normal strings work correctly after escaping fix
		String normalValue = "HR Department";
		String vectorExpr = this.converter
			.convertExpression(new Expression(EQ, new Key("department"), new Value(normalValue)));

		assertThat(vectorExpr).isEqualTo("JSON_VALUE(`metadata`, '$.\"department\"') = 'HR Department'");
	}

	@Test
	public void testUnicodeControlCharacters() {
		// Test Unicode control characters are escaped
		String valueWithControlChar = "test\u0000value"; // null character
		String vectorExpr = this.converter
			.convertExpression(new Expression(EQ, new Key("field"), new Value(valueWithControlChar)));

		// Should escape Unicode control character
		assertThat(vectorExpr).contains("\\u0000");
	}

	@Test
	public void testDateValue() {
		// Test that Date objects are properly formatted as ISO 8601 strings
		Date testDate = Date.from(Instant.parse("2024-01-15T10:30:00Z"));
		String vectorExpr = this.converter
			.convertExpression(new Expression(EQ, new Key("activationDate"), new Value(testDate)));

		// Verify date is formatted as ISO 8601 string with SQL escaping (milliseconds
		// from formatter)
		assertThat(vectorExpr).isEqualTo("JSON_VALUE(`metadata`, '$.\"activationDate\"') = '2024-01-15T10:30:00.000Z'");
	}

	@Test
	public void testDateStringValue() {
		// Test that ISO date strings are normalized to Date objects and formatted
		// correctly
		String vectorExpr = this.converter
			.convertExpression(new Expression(EQ, new Key("activationDate"), new Value("2024-01-15T10:30:00Z")));

		// Verify ISO date strings are normalized and formatted correctly (milliseconds
		// from formatter)
		assertThat(vectorExpr).isEqualTo("JSON_VALUE(`metadata`, '$.\"activationDate\"') = '2024-01-15T10:30:00.000Z'");
	}

	@Test
	public void testDateWithMilliseconds() {
		// Test that ISO date strings with milliseconds are handled correctly
		String vectorExpr = this.converter
			.convertExpression(new Expression(EQ, new Key("timestamp"), new Value("2024-01-15T10:30:00.123Z")));

		// After normalization, milliseconds should be preserved
		// Note: Actual output depends on whether DateTimeFormatter preserves milliseconds
		assertThat(vectorExpr).contains("2024-01-15T10:30:00");
	}

	@Test
	public void testDateInINClause() {
		// Test that Date objects in IN clauses are properly formatted
		Date date1 = Date.from(Instant.parse("2024-01-15T10:30:00Z"));
		Date date2 = Date.from(Instant.parse("2024-02-20T14:45:00Z"));

		String vectorExpr = this.converter
			.convertExpression(new Expression(IN, new Key("activationDate"), new Value(List.of(date1, date2))));

		// Verify dates are properly formatted in IN clause (milliseconds from formatter)
		assertThat(vectorExpr).contains("'2024-01-15T10:30:00.000Z'");
		assertThat(vectorExpr).contains("'2024-02-20T14:45:00.000Z'");
		assertThat(vectorExpr).contains("IN (");
	}

	@Test
	public void testDateStringInINClause() {
		// Test that ISO date strings in IN clauses are normalized and formatted
		String vectorExpr = this.converter.convertExpression(new Expression(IN, new Key("activationDate"),
				new Value(List.of("2024-01-15T10:30:00Z", "2024-02-20T14:45:00Z"))));

		// Verify ISO date strings are normalized and formatted in IN clause (milliseconds
		// from formatter)
		assertThat(vectorExpr).contains("'2024-01-15T10:30:00.000Z'");
		assertThat(vectorExpr).contains("'2024-02-20T14:45:00.000Z'");
	}

	@Test
	public void testDateComparison() {
		// Test date comparison with GTE operator
		Date testDate = Date.from(Instant.parse("2024-01-01T00:00:00Z"));
		String vectorExpr = this.converter
			.convertExpression(new Expression(GTE, new Key("createdAt"), new Value(testDate)));

		assertThat(vectorExpr).isEqualTo("JSON_VALUE(`metadata`, '$.\"createdAt\"') >= '2024-01-01T00:00:00.000Z'");
	}

	@Test
	public void testDateInComplexExpression() {
		// Test date in complex AND expression
		Date startDate = Date.from(Instant.parse("2024-01-01T00:00:00Z"));
		String vectorExpr = this.converter
			.convertExpression(new Expression(AND, new Expression(EQ, new Key("department"), new Value("Engineering")),
					new Expression(GTE, new Key("joinDate"), new Value(startDate))));

		assertThat(vectorExpr).contains("JSON_VALUE(`metadata`, '$.\"department\"') = 'Engineering'");
		assertThat(vectorExpr).contains("JSON_VALUE(`metadata`, '$.\"joinDate\"') >= '2024-01-01T00:00:00.000Z'");
		assertThat(vectorExpr).contains(" AND ");
	}

	@Test
	public void testKeyWithSingleQuote() {
		String vectorExpr = this.converter
			.convertExpression(new Expression(EQ, new Key("x' OR 1=1--"), new Value("dummy")));
		assertThat(vectorExpr).isEqualTo("JSON_VALUE(`metadata`, '$.\"x'' OR 1=1--\"') = 'dummy'");
		assertThat(vectorExpr).doesNotContain("'$.\"x\"' OR");
	}

	@Test
	public void testQuotedIdentifierFromTextParser() {
		Expression expr = new FilterExpressionTextParser().parse("'safe_key' == 'value'");
		String vectorExpr = this.converter.convertExpression(expr);
		assertThat(vectorExpr).isEqualTo("JSON_VALUE(`metadata`, '$.\"safe_key\"') = 'value'");
	}

	@Test
	public void testKeyWithApostrophe() {
		String vectorExpr = this.converter.convertExpression(new Expression(EQ, new Key("O'Brien"), new Value("test")));
		assertThat(vectorExpr).isEqualTo("JSON_VALUE(`metadata`, '$.\"O''Brien\"') = 'test'");
	}

	@Test
	public void testKeyWithBackslash() {
		String vectorExpr = this.converter
			.convertExpression(new Expression(EQ, new Key("key\\inject"), new Value("v")));
		assertThat(vectorExpr).isEqualTo("JSON_VALUE(`metadata`, '$.\"key\\\\\\\\inject\"') = 'v'");
	}

	@Test
	public void testKeyWithBackslashAndSingleQuote() {
		String vectorExpr = this.converter.convertExpression(new Expression(EQ, new Key("O\\'Brien"), new Value("v")));
		assertThat(vectorExpr).isEqualTo("JSON_VALUE(`metadata`, '$.\"O\\\\\\\\''Brien\"') = 'v'");
	}

}
