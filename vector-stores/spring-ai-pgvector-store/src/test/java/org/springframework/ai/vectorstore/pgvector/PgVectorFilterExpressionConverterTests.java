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

package org.springframework.ai.vectorstore.pgvector;

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
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.GT;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.GTE;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.IN;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.LT;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.LTE;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.NE;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.NIN;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.OR;

/**
 * @author Muthukumaran Navaneethakrishnan
 * @author Christian Tzolov
 */
public class PgVectorFilterExpressionConverterTests {

	FilterExpressionConverter converter = new PgVectorFilterExpressionConverter();

	private static String sqlPredicate(String jsonPath) {
		return "metadata::jsonb @@ '" + jsonPath.replace("'", "''") + "'::jsonpath";
	}

	// Security test for metadataColumn identifier quoting
	@Test
	public void testMetadataColumnWithDoubleQuoteIsEscaped() {
		// Attempt SQL injection through metadataColumn parameter
		FilterExpressionConverter maliciousConverter = new PgVectorFilterExpressionConverter(
				"meta\"); DROP TABLE users; --");
		String vectorExpr = maliciousConverter
			.convertExpression(new Expression(EQ, new Key("country"), new Value("BG")));
		// The identifier contains special characters, so it's quoted with escaped double
		// quotes
		assertThat(vectorExpr).startsWith("\"meta\"\"); DROP TABLE users; --\"::jsonb @@ '");
		assertThat(vectorExpr).doesNotContain("meta\"); DROP TABLE users;");
	}

	@Test
	public void testSimpleMetadataColumnNotQuoted() {
		// Simple alphanumeric column names should not be quoted to preserve
		// case-insensitive behavior
		FilterExpressionConverter converter = new PgVectorFilterExpressionConverter("metadata");
		String vectorExpr = converter.convertExpression(new Expression(EQ, new Key("country"), new Value("BG")));
		assertThat(vectorExpr).startsWith("metadata::jsonb @@ '");
	}

	@Test
	public void testEQ() {
		// country == "BG"
		String vectorExpr = this.converter.convertExpression(new Expression(EQ, new Key("country"), new Value("BG")));
		assertThat(vectorExpr).isEqualTo(sqlPredicate("$.\"country\" == \"BG\""));
	}

	@Test
	public void tesEqAndGte() {
		// genre == "drama" AND year >= 2020
		String vectorExpr = this.converter
			.convertExpression(new Expression(AND, new Expression(EQ, new Key("genre"), new Value("drama")),
					new Expression(GTE, new Key("year"), new Value(2020))));
		assertThat(vectorExpr).isEqualTo(sqlPredicate("$.\"genre\" == \"drama\" && $.\"year\" >= 2020"));
	}

	@Test
	public void tesIn() {
		// genre in ["comedy", "documentary", "drama"]
		String vectorExpr = this.converter.convertExpression(
				new Expression(IN, new Key("genre"), new Value(List.of("comedy", "documentary", "drama"))));
		assertThat(vectorExpr).isEqualTo(sqlPredicate(
				"($.\"genre\" == \"comedy\" || $.\"genre\" == \"documentary\" || $.\"genre\" == \"drama\")"));
	}

	@Test
	public void testNe() {
		// year >= 2020 OR country == "BG" AND city != "Sofia"
		String vectorExpr = this.converter
			.convertExpression(new Expression(OR, new Expression(GTE, new Key("year"), new Value(2020)),
					new Expression(AND, new Expression(EQ, new Key("country"), new Value("BG")),
							new Expression(NE, new Key("city"), new Value("Sofia")))));
		assertThat(vectorExpr)
			.isEqualTo(sqlPredicate("$.\"year\" >= 2020 || $.\"country\" == \"BG\" && $.\"city\" != \"Sofia\""));
	}

	@Test
	public void testGroup() {
		// (year >= 2020 OR country == "BG") AND city NIN ["Sofia", "Plovdiv"]
		String vectorExpr = this.converter.convertExpression(new Expression(AND,
				new Group(new Expression(OR, new Expression(GTE, new Key("year"), new Value(2020)),
						new Expression(EQ, new Key("country"), new Value("BG")))),
				new Expression(NIN, new Key("city"), new Value(List.of("Sofia", "Plovdiv")))));
		assertThat(vectorExpr).isEqualTo(sqlPredicate(
				"($.\"year\" >= 2020 || $.\"country\" == \"BG\") && !($.\"city\" == \"Sofia\" || $.\"city\" == \"Plovdiv\")"));
	}

	@Test
	public void tesBoolean() {
		// isOpen == true AND year >= 2020 AND country IN ["BG", "NL", "US"]
		String vectorExpr = this.converter.convertExpression(new Expression(AND,
				new Expression(AND, new Expression(EQ, new Key("isOpen"), new Value(true)),
						new Expression(GTE, new Key("year"), new Value(2020))),
				new Expression(IN, new Key("country"), new Value(List.of("BG", "NL", "US")))));

		assertThat(vectorExpr).isEqualTo(sqlPredicate(
				"$.\"isOpen\" == true && $.\"year\" >= 2020 && ($.\"country\" == \"BG\" || $.\"country\" == \"NL\" || $.\"country\" == \"US\")"));
	}

	@Test
	public void testDecimal() {
		// temperature >= -15.6 && temperature <= +20.13
		String vectorExpr = this.converter
			.convertExpression(new Expression(AND, new Expression(GTE, new Key("temperature"), new Value(-15.6)),
					new Expression(LTE, new Key("temperature"), new Value(20.13))));

		assertThat(vectorExpr).isEqualTo(sqlPredicate("$.\"temperature\" >= -15.6 && $.\"temperature\" <= 20.13"));
	}

	@Test
	public void testComplexIdentifiers() {
		String vectorExpr = this.converter
			.convertExpression(new Expression(EQ, new Key("country 1 2 3"), new Value("BG")));
		assertThat(vectorExpr).isEqualTo(sqlPredicate("$.\"country 1 2 3\" == \"BG\""));
	}

	@Test
	public void testLT() {
		// value < 100
		String vectorExpr = this.converter.convertExpression(new Expression(LT, new Key("value"), new Value(100)));
		assertThat(vectorExpr).isEqualTo(sqlPredicate("$.\"value\" < 100"));
	}

	@Test
	public void testGT() {
		// score > 75
		String vectorExpr = this.converter.convertExpression(new Expression(GT, new Key("score"), new Value(100)));
		assertThat(vectorExpr).isEqualTo(sqlPredicate("$.\"score\" > 100"));
	}

	@Test
	public void testLTE() {
		// amount <= 100.5
		String vectorExpr = this.converter.convertExpression(new Expression(LTE, new Key("amount"), new Value(100.5)));
		assertThat(vectorExpr).isEqualTo(sqlPredicate("$.\"amount\" <= 100.5"));
	}

	@Test
	public void testNIN() {
		// category NOT IN ["typeA", "typeB"]
		String vectorExpr = this.converter
			.convertExpression(new Expression(NIN, new Key("category"), new Value(List.of("typeA", "typeB"))));
		assertThat(vectorExpr).isEqualTo(sqlPredicate("!($.\"category\" == \"typeA\" || $.\"category\" == \"typeB\")"));
	}

	@Test
	public void testSingleValueIN() {
		// status IN ["active"] - single value in list
		String vectorExpr = this.converter
			.convertExpression(new Expression(IN, new Key("status"), new Value(List.of("active"))));
		assertThat(vectorExpr).isEqualTo(sqlPredicate("($.\"status\" == \"active\")"));
	}

	@Test
	public void testSingleValueNIN() {
		// status NOT IN ["inactive"] - single value in list
		String vectorExpr = this.converter
			.convertExpression(new Expression(NIN, new Key("status"), new Value(List.of("inactive"))));
		assertThat(vectorExpr).isEqualTo(sqlPredicate("!($.\"status\" == \"inactive\")"));
	}

	@Test
	public void testNumericIN() {
		// priority IN [1, 2, 3]
		String vectorExpr = this.converter
			.convertExpression(new Expression(IN, new Key("priority"), new Value(List.of(1, 2, 3))));
		assertThat(vectorExpr)
			.isEqualTo(sqlPredicate("($.\"priority\" == 1 || $.\"priority\" == 2 || $.\"priority\" == 3)"));
	}

	@Test
	public void testNumericNIN() {
		// level NOT IN [0, 10]
		String vectorExpr = this.converter
			.convertExpression(new Expression(NIN, new Key("level"), new Value(List.of(0, 10))));
		assertThat(vectorExpr).isEqualTo(sqlPredicate("!($.\"level\" == 0 || $.\"level\" == 10)"));
	}

	@Test
	public void testNestedGroups() {
		// ((score >= 80 AND type == "A") OR (score >= 90 AND type == "B")) AND status ==
		// "valid"
		String vectorExpr = this.converter.convertExpression(new Expression(AND,
				new Group(new Expression(OR,
						new Group(new Expression(AND, new Expression(GTE, new Key("score"), new Value(80)),
								new Expression(EQ, new Key("type"), new Value("A")))),
						new Group(new Expression(AND, new Expression(GTE, new Key("score"), new Value(90)),
								new Expression(EQ, new Key("type"), new Value("B")))))),
				new Expression(EQ, new Key("status"), new Value("valid"))));
		assertThat(vectorExpr).isEqualTo(sqlPredicate(
				"(($.\"score\" >= 80 && $.\"type\" == \"A\") || ($.\"score\" >= 90 && $.\"type\" == \"B\")) && $.\"status\" == \"valid\""));
	}

	@Test
	public void testBooleanFalse() {
		// active == false
		String vectorExpr = this.converter.convertExpression(new Expression(EQ, new Key("active"), new Value(false)));
		assertThat(vectorExpr).isEqualTo(sqlPredicate("$.\"active\" == false"));
	}

	@Test
	public void testBooleanNE() {
		// active != true
		String vectorExpr = this.converter.convertExpression(new Expression(NE, new Key("active"), new Value(true)));
		assertThat(vectorExpr).isEqualTo(sqlPredicate("$.\"active\" != true"));
	}

	@Test
	public void testKeyWithDots() {
		// config.setting == "value1"
		String vectorExpr = this.converter
			.convertExpression(new Expression(EQ, new Key("config.setting"), new Value("value1")));
		assertThat(vectorExpr).isEqualTo(sqlPredicate("$.\"config.setting\" == \"value1\""));
	}

	@Test
	public void testEmptyString() {
		// description == ""
		String vectorExpr = this.converter.convertExpression(new Expression(EQ, new Key("description"), new Value("")));
		assertThat(vectorExpr).isEqualTo(sqlPredicate("$.\"description\" == \"\""));
	}

	@Test
	public void testNullValue() {
		// metadata == null
		String vectorExpr = this.converter.convertExpression(new Expression(EQ, new Key("metadata"), new Value(null)));
		assertThat(vectorExpr).isEqualTo(sqlPredicate("$.\"metadata\" == null"));
	}

	@Test
	public void testComplexOrExpression() {
		// state == "ready" OR state == "pending" OR state == "processing"
		String vectorExpr = this.converter.convertExpression(new Expression(OR,
				new Expression(OR, new Expression(EQ, new Key("state"), new Value("ready")),
						new Expression(EQ, new Key("state"), new Value("pending"))),
				new Expression(EQ, new Key("state"), new Value("processing"))));
		assertThat(vectorExpr).isEqualTo(sqlPredicate(
				"$.\"state\" == \"ready\" || $.\"state\" == \"pending\" || $.\"state\" == \"processing\""));
	}

	// Security Tests - JSONPath Injection Prevention

	@Test
	public void testInjectionWithDoubleQuoteEscape() {
		// Attempt to inject: department == "" || $.department == "Finance"
		// Malicious value: " || $.department == "Finance
		String maliciousValue = "\" || $.department == \"Finance";
		String vectorExpr = this.converter
			.convertExpression(new Expression(EQ, new Key("department"), new Value(maliciousValue)));

		// Key is always quoted; value double-quotes are JSON-escaped
		String expected = sqlPredicate("$.\"department\" == \"\\\" || $.department == \\\"Finance\"");

		assertThat(vectorExpr).isEqualTo(expected);
		assertThat(vectorExpr).contains("\\\"");
		assertThat(vectorExpr).doesNotContain("== \"\"'");
	}

	@Test
	public void testInjectionWithBackslashEscape() {
		// Attempt to inject using backslash escape: value\"
		String maliciousValue = "value\\\"";
		String vectorExpr = this.converter
			.convertExpression(new Expression(EQ, new Key("field"), new Value(maliciousValue)));

		// Should escape both backslash and quote
		assertThat(vectorExpr).isEqualTo(sqlPredicate("$.\"field\" == \"value\\\\\\\"\""));
		assertThat(vectorExpr).contains("\\\\");
	}

	@Test
	public void testInjectionWithSingleQuote() {
		// Attempt to inject using single quotes: value' || $.other == 'admin
		String maliciousValue = "value' || $.other == 'admin";
		String vectorExpr = this.converter
			.convertExpression(new Expression(EQ, new Key("field"), new Value(maliciousValue)));

		// Single quotes in JSON string values are SQL-escaped (doubled) so they cannot
		// terminate the surrounding SQL single-quoted literal
		assertThat(vectorExpr).isEqualTo(sqlPredicate("$.\"field\" == \"value' || $.other == 'admin\""));
		assertThat(vectorExpr).contains("value'' || $.other == ''admin");
	}

	@Test
	public void testInjectionWithControlCharacters() {
		// Attempt to inject using newline: value\n|| $.field == "admin"
		String maliciousValue = "value\n|| $.field == \"admin\"";
		String vectorExpr = this.converter
			.convertExpression(new Expression(EQ, new Key("field"), new Value(maliciousValue)));

		// Should escape newline and quotes; key is always quoted
		assertThat(vectorExpr).isEqualTo(sqlPredicate("$.\"field\" == \"value\\n|| $.field == \\\"admin\\\"\""));
		assertThat(vectorExpr).contains("\\n");
	}

	@Test
	public void testInjectionWithMultipleEscapes() {
		// Complex injection with multiple special characters
		String maliciousValue = "test\"\\'\n\r\t";
		String vectorExpr = this.converter
			.convertExpression(new Expression(EQ, new Key("field"), new Value(maliciousValue)));

		// JSON escaping handles double-quotes and backslashes; single quote is
		// SQL-escaped (doubled) when embedded in the SQL string literal
		assertThat(vectorExpr).isEqualTo(sqlPredicate("$.\"field\" == \"test\\\"\\\\\'\\n\\r\\t\""));
		assertThat(vectorExpr).contains("\\\""); // escaped double quote
		assertThat(vectorExpr).contains("\\\\"); // escaped backslash
		assertThat(vectorExpr).contains("''"); // SQL-escaped single quote
	}

	@Test
	public void testInjectionInListValues() {
		// Attempt injection through IN clause
		String maliciousValue1 = "HR\" || $.department == \"Finance";
		String maliciousValue2 = "Engineering";
		String vectorExpr = this.converter.convertExpression(
				new Expression(IN, new Key("department"), new Value(List.of(maliciousValue1, maliciousValue2))));

		// Should escape quotes in list values
		assertThat(vectorExpr).contains("HR\\\" || $.department == \\\"Finance");
		assertThat(vectorExpr).contains("Engineering");
	}

	@Test
	public void testInjectionInComplexExpression() {
		// Attempt injection in a complex AND/OR expression
		String maliciousValue = "\" || $.role == \"admin\" || $.dept == \"";
		String vectorExpr = this.converter
			.convertExpression(new Expression(AND, new Expression(EQ, new Key("department"), new Value(maliciousValue)),
					new Expression(GTE, new Key("year"), new Value(2020))));

		// Should not allow injection to break out of the expression
		assertThat(vectorExpr).contains("\\\" || $.role == \\\"admin\\\" || $.dept == \\\"");
		assertThat(vectorExpr).contains("&&");
		assertThat(vectorExpr).contains("$.\"department\"");
	}

	@Test
	public void testNormalStringsNotAffected() {
		// Verify normal strings work correctly after escaping fix
		String normalValue = "HR Department";
		String vectorExpr = this.converter
			.convertExpression(new Expression(EQ, new Key("department"), new Value(normalValue)));

		assertThat(vectorExpr).isEqualTo(sqlPredicate("$.\"department\" == \"HR Department\""));
	}

	@Test
	public void testUnicodeControlCharacters() {
		// Test Unicode control characters are escaped
		String valueWithControlChar = "test\u0000value"; // null character
		String vectorExpr = this.converter
			.convertExpression(new Expression(EQ, new Key("field"), new Value(valueWithControlChar)));

		assertThat(vectorExpr).contains("\\u0000");
	}

	@Test
	public void testKeyWithSingleQuote() {
		String vectorExpr = this.converter
			.convertExpression(new Expression(EQ, new Key("x' OR 1=1--"), new Value("dummy")));
		assertThat(vectorExpr).isEqualTo(sqlPredicate("$.\"x' OR 1=1--\" == \"dummy\""));
		assertThat(vectorExpr).doesNotContain("$.x' OR 1=1--'");
	}

	@Test
	public void testQuotedIdentifierFromTextParser() {
		Expression expr = new FilterExpressionTextParser().parse("'safe_key' == 'value'");
		String vectorExpr = this.converter.convertExpression(expr);
		assertThat(vectorExpr).isEqualTo(sqlPredicate("$.\"safe_key\" == \"value\""));
	}

	@Test
	public void testKeyWithApostrophe() {
		String vectorExpr = this.converter.convertExpression(new Expression(EQ, new Key("O'Brien"), new Value("test")));
		assertThat(vectorExpr).isEqualTo(sqlPredicate("$.\"O'Brien\" == \"test\""));
	}

	@Test
	public void testKeyWithDoubleQuote() {
		String vectorExpr = this.converter
			.convertExpression(new Expression(EQ, new Key("key\"inject"), new Value("v")));
		assertThat(vectorExpr).isEqualTo(sqlPredicate("$.\"key\\\"inject\" == \"v\""));
		assertThat(vectorExpr).doesNotContain("$.\"key\"inject");
	}

	@Test
	public void testKeyWithBackslash() {
		String vectorExpr = this.converter
			.convertExpression(new Expression(EQ, new Key("key\\inject"), new Value("v")));
		assertThat(vectorExpr).isEqualTo(sqlPredicate("$.\"key\\\\inject\" == \"v\""));
	}

	@Test
	public void testDateInINClause() {
		// Test that date strings in IN clauses are properly normalized
		String vectorExpr = this.converter.convertExpression(new Expression(IN, new Key("activationDate"),
				new Value(List.of("2024-01-15T10:30:00Z", "2024-02-20T14:45:00Z"))));

		// Note: Jackson serializes dates with milliseconds, so .000Z is expected
		assertThat(vectorExpr).contains("$.\"activationDate\" == \"2024-01-15T10:30:00.000Z\"");
		assertThat(vectorExpr).contains("$.\"activationDate\" == \"2024-02-20T14:45:00.000Z\"");
		assertThat(vectorExpr).contains(" || ");
	}

	@Test
	public void testDateInNINClause() {
		// Test that date strings in NIN clauses are properly normalized
		String vectorExpr = this.converter.convertExpression(new Expression(NIN, new Key("activationDate"),
				new Value(List.of("2024-01-15T10:30:00Z", "2024-02-20T14:45:00Z"))));

		assertThat(vectorExpr).startsWith("metadata::jsonb @@ '!(");
		assertThat(vectorExpr).endsWith(")'::jsonpath");
		assertThat(vectorExpr).contains("$.\"activationDate\" == \"2024-01-15T10:30:00.000Z\"");
		assertThat(vectorExpr).contains("$.\"activationDate\" == \"2024-02-20T14:45:00.000Z\"");
	}

	@Test
	public void testDateObjectInINClause() {
		// Test that Date objects in IN clauses are properly formatted
		Date date1 = Date.from(Instant.parse("2024-01-15T10:30:00Z"));
		Date date2 = Date.from(Instant.parse("2024-02-20T14:45:00Z"));

		String vectorExpr = this.converter
			.convertExpression(new Expression(IN, new Key("activationDate"), new Value(List.of(date1, date2))));

		assertThat(vectorExpr).contains("$.\"activationDate\"");
		assertThat(vectorExpr).contains("2024-01-15T10:30:00.000Z");
		assertThat(vectorExpr).contains("2024-02-20T14:45:00.000Z");
	}

}
