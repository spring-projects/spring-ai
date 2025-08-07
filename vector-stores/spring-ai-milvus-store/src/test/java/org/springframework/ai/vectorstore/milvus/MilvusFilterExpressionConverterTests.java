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

package org.springframework.ai.vectorstore.milvus;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.Filter.Group;
import org.springframework.ai.vectorstore.filter.Filter.Key;
import org.springframework.ai.vectorstore.filter.Filter.Value;
import org.springframework.ai.vectorstore.filter.FilterExpressionConverter;

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
 * @author Christian Tzolov
 */
public class MilvusFilterExpressionConverterTests {

	FilterExpressionConverter converter = new MilvusFilterExpressionConverter();

	@Test
	public void testEQ() {
		// country == "BG"
		String vectorExpr = this.converter.convertExpression(new Expression(EQ, new Key("country"), new Value("BG")));
		assertThat(vectorExpr).isEqualTo("metadata[\"country\"] == \"BG\"");
	}

	@Test
	public void tesEqAndGte() {
		// genre == "drama" AND year >= 2020
		String vectorExpr = this.converter
			.convertExpression(new Expression(AND, new Expression(EQ, new Key("genre"), new Value("drama")),
					new Expression(GTE, new Key("year"), new Value(2020))));
		assertThat(vectorExpr).isEqualTo("metadata[\"genre\"] == \"drama\" && metadata[\"year\"] >= 2020");
	}

	@Test
	public void tesIn() {
		// genre in ["comedy", "documentary", "drama"]
		String vectorExpr = this.converter.convertExpression(
				new Expression(IN, new Key("genre"), new Value(List.of("comedy", "documentary", "drama"))));
		assertThat(vectorExpr).isEqualTo("metadata[\"genre\"] in [\"comedy\",\"documentary\",\"drama\"]");
	}

	@Test
	public void testNe() {
		// year >= 2020 OR country == "BG" AND city != "Sofia"
		String vectorExpr = this.converter
			.convertExpression(new Expression(OR, new Expression(GTE, new Key("year"), new Value(2020)),
					new Expression(AND, new Expression(EQ, new Key("country"), new Value("BG")),
							new Expression(NE, new Key("city"), new Value("Sofia")))));
		assertThat(vectorExpr).isEqualTo(
				"metadata[\"year\"] >= 2020 || metadata[\"country\"] == \"BG\" && metadata[\"city\"] != \"Sofia\"");
	}

	@Test
	public void testGroup() {
		// (year >= 2020 OR country == "BG") AND city NIN ["Sofia", "Plovdiv"]
		String vectorExpr = this.converter.convertExpression(new Expression(AND,
				new Group(new Expression(OR, new Expression(GTE, new Key("year"), new Value(2020)),
						new Expression(EQ, new Key("country"), new Value("BG")))),
				new Expression(NIN, new Key("city"), new Value(List.of("Sofia", "Plovdiv")))));
		assertThat(vectorExpr).isEqualTo(
				"metadata[\"year\"] >= 2020 || metadata[\"country\"] == \"BG\" && metadata[\"year\"] >= 2020 || metadata[\"country\"] == \"BG\" && metadata[\"city\"] not in [\"Sofia\",\"Plovdiv\"]");
	}

	@Test
	public void testBoolean() {
		// isOpen == true AND year >= 2020 AND country IN ["BG", "NL", "US"]
		String vectorExpr = this.converter.convertExpression(new Expression(AND,
				new Expression(AND, new Expression(EQ, new Key("isOpen"), new Value(true)),
						new Expression(GTE, new Key("year"), new Value(2020))),
				new Expression(IN, new Key("country"), new Value(List.of("BG", "NL", "US")))));

		assertThat(vectorExpr).isEqualTo(
				"metadata[\"isOpen\"] == true && metadata[\"year\"] >= 2020 && metadata[\"country\"] in [\"BG\",\"NL\",\"US\"]");
	}

	@Test
	public void testDecimal() {
		// temperature >= -15.6 && temperature <= +20.13
		String vectorExpr = this.converter
			.convertExpression(new Expression(AND, new Expression(GTE, new Key("temperature"), new Value(-15.6)),
					new Expression(LTE, new Key("temperature"), new Value(20.13))));

		assertThat(vectorExpr).isEqualTo("metadata[\"temperature\"] >= -15.6 && metadata[\"temperature\"] <= 20.13");
	}

	@Test
	public void testComplexIdentifiers() {
		String vectorExpr = this.converter
			.convertExpression(new Expression(EQ, new Key("\"country 1 2 3\""), new Value("BG")));
		assertThat(vectorExpr).isEqualTo("metadata[\"country 1 2 3\"] == \"BG\"");

		vectorExpr = this.converter.convertExpression(new Expression(EQ, new Key("'country 1 2 3'"), new Value("BG")));
		assertThat(vectorExpr).isEqualTo("metadata[\"country 1 2 3\"] == \"BG\"");
	}

	@Test
	public void testLt() {
		// temperature < 0
		String vectorExpr = this.converter.convertExpression(new Expression(LT, new Key("temperature"), new Value(0)));
		assertThat(vectorExpr).isEqualTo("metadata[\"temperature\"] < 0");
	}

	@Test
	public void testLte() {
		// humidity <= 100
		String vectorExpr = this.converter.convertExpression(new Expression(LTE, new Key("humidity"), new Value(100)));
		assertThat(vectorExpr).isEqualTo("metadata[\"humidity\"] <= 100");
	}

	@Test
	public void testGt() {
		// price > 1000
		String vectorExpr = this.converter.convertExpression(new Expression(GT, new Key("price"), new Value(1000)));
		assertThat(vectorExpr).isEqualTo("metadata[\"price\"] > 1000");
	}

	@Test
	public void testCombinedComparisons() {
		// price > 1000 && temperature < 25 && humidity <= 80
		String vectorExpr = this.converter.convertExpression(new Expression(AND,
				new Expression(AND, new Expression(GT, new Key("price"), new Value(1000)),
						new Expression(LT, new Key("temperature"), new Value(25))),
				new Expression(LTE, new Key("humidity"), new Value(80))));
		assertThat(vectorExpr)
			.isEqualTo("metadata[\"price\"] > 1000 && metadata[\"temperature\"] < 25 && metadata[\"humidity\"] <= 80");
	}

	@Test
	public void testNin() {
		// region not in ["A", "B", "C"]
		String vectorExpr = this.converter
			.convertExpression(new Expression(NIN, new Key("region"), new Value(List.of("A", "B", "C"))));
		assertThat(vectorExpr).isEqualTo("metadata[\"region\"] not in [\"A\",\"B\",\"C\"]");
	}

	@Test
	public void testNullValue() {
		// status == null
		String vectorExpr = this.converter.convertExpression(new Expression(EQ, new Key("status"), new Value(null)));
		assertThat(vectorExpr).isEqualTo("metadata[\"status\"] == null");
	}

	@Test
	public void testEmptyString() {
		// name == ""
		String vectorExpr = this.converter.convertExpression(new Expression(EQ, new Key("name"), new Value("")));
		assertThat(vectorExpr).isEqualTo("metadata[\"name\"] == \"\"");
	}

	@Test
	public void testNumericString() {
		// id == "12345"
		String vectorExpr = this.converter.convertExpression(new Expression(EQ, new Key("id"), new Value("12345")));
		assertThat(vectorExpr).isEqualTo("metadata[\"id\"] == \"12345\"");
	}

	@Test
	public void testLongValue() {
		// timestamp >= 1640995200000L
		String vectorExpr = this.converter
			.convertExpression(new Expression(GTE, new Key("timestamp"), new Value(1640995200000L)));
		assertThat(vectorExpr).isEqualTo("metadata[\"timestamp\"] >= 1640995200000");
	}

	@Test
	public void testFloatValue() {
		// score >= 4.5f
		String vectorExpr = this.converter.convertExpression(new Expression(GTE, new Key("score"), new Value(4.5f)));
		assertThat(vectorExpr).isEqualTo("metadata[\"score\"] >= 4.5");
	}

	@Test
	public void testMixedTypesList() {
		// tags in [1, "priority", true]
		String vectorExpr = this.converter
			.convertExpression(new Expression(IN, new Key("tags"), new Value(List.of(1, "priority", true))));
		assertThat(vectorExpr).isEqualTo("metadata[\"tags\"] in [1,\"priority\",true]");
	}

	@Test
	public void testEmptyList() {
		// categories in []
		String vectorExpr = this.converter
			.convertExpression(new Expression(IN, new Key("categories"), new Value(List.of())));
		assertThat(vectorExpr).isEqualTo("metadata[\"categories\"] in []");
	}

	@Test
	public void testSingleItemList() {
		// status in ["active"]
		String vectorExpr = this.converter
			.convertExpression(new Expression(IN, new Key("status"), new Value(List.of("active"))));
		assertThat(vectorExpr).isEqualTo("metadata[\"status\"] in [\"active\"]");
	}

	@Test
	public void testKeyWithDots() {
		// "value.field" >= 18
		String vectorExpr = this.converter
			.convertExpression(new Expression(GTE, new Key("value.field"), new Value(18)));
		assertThat(vectorExpr).isEqualTo("metadata[\"value.field\"] >= 18");
	}

	@Test
	public void testKeyWithSpecialCharacters() {
		// "field-name_with@symbols" == "value"
		String vectorExpr = this.converter
			.convertExpression(new Expression(EQ, new Key("field-name_with@symbols"), new Value("value")));
		assertThat(vectorExpr).isEqualTo("metadata[\"field-name_with@symbols\"] == \"value\"");
	}

	@Test
	public void testTripleAnd() {
		// value >= 100 AND type == "primary" AND region == "X"
		String vectorExpr = this.converter.convertExpression(new Expression(AND,
				new Expression(AND, new Expression(GTE, new Key("value"), new Value(100)),
						new Expression(EQ, new Key("type"), new Value("primary"))),
				new Expression(EQ, new Key("region"), new Value("X"))));

		assertThat(vectorExpr).isEqualTo(
				"metadata[\"value\"] >= 100 && metadata[\"type\"] == \"primary\" && metadata[\"region\"] == \"X\"");
	}

	@Test
	public void testTripleOr() {
		// value < 50 OR value > 200 OR type == "special"
		String vectorExpr = this.converter.convertExpression(new Expression(OR,
				new Expression(OR, new Expression(LT, new Key("value"), new Value(50)),
						new Expression(GT, new Key("value"), new Value(200))),
				new Expression(EQ, new Key("type"), new Value("special"))));

		assertThat(vectorExpr)
			.isEqualTo("metadata[\"value\"] < 50 || metadata[\"value\"] > 200 || metadata[\"type\"] == \"special\"");
	}

	@Test
	public void testNegativeNumbers() {
		// temperature >= -20 AND temperature <= -5
		String vectorExpr = this.converter
			.convertExpression(new Expression(AND, new Expression(GTE, new Key("temperature"), new Value(-20)),
					new Expression(LTE, new Key("temperature"), new Value(-5))));

		assertThat(vectorExpr).isEqualTo("metadata[\"temperature\"] >= -20 && metadata[\"temperature\"] <= -5");
	}

	@Test
	public void testZeroValues() {
		// count == 0
		String vectorExpr = this.converter.convertExpression(new Expression(EQ, new Key("count"), new Value(0)));
		assertThat(vectorExpr).isEqualTo("metadata[\"count\"] == 0");
	}

	@Test
	public void testBooleanFalse() {
		// enabled == false
		String vectorExpr = this.converter.convertExpression(new Expression(EQ, new Key("enabled"), new Value(false)));
		assertThat(vectorExpr).isEqualTo("metadata[\"enabled\"] == false");
	}

	@Test
	public void testVeryLongString() {
		// Test with a very long string value
		String longValue = "This is a very long string that might be used as a value in a filter expression to test how the converter handles lengthy text content that could potentially cause issues with string manipulation";
		String vectorExpr = this.converter
			.convertExpression(new Expression(EQ, new Key("content"), new Value(longValue)));
		assertThat(vectorExpr).isEqualTo("metadata[\"content\"] == \"" + longValue + "\"");
	}

	@Test
	public void testRangeQuery() {
		// value >= 10 AND value <= 100
		String vectorExpr = this.converter
			.convertExpression(new Expression(AND, new Expression(GTE, new Key("value"), new Value(10)),
					new Expression(LTE, new Key("value"), new Value(100))));

		assertThat(vectorExpr).isEqualTo("metadata[\"value\"] >= 10 && metadata[\"value\"] <= 100");
	}

	@Test
	public void testComplexOrWithMultipleFields() {
		// type == "primary" OR status == "active" OR priority > 5
		String vectorExpr = this.converter.convertExpression(new Expression(OR,
				new Expression(OR, new Expression(EQ, new Key("type"), new Value("primary")),
						new Expression(EQ, new Key("status"), new Value("active"))),
				new Expression(GT, new Key("priority"), new Value(5))));

		assertThat(vectorExpr).isEqualTo(
				"metadata[\"type\"] == \"primary\" || metadata[\"status\"] == \"active\" || metadata[\"priority\"] > 5");
	}

	@Test
	public void testDoubleQuotedKey() {
		// "field with spaces" == "value"
		String vectorExpr = this.converter
			.convertExpression(new Expression(EQ, new Key("\"field with spaces\""), new Value("value")));
		assertThat(vectorExpr).isEqualTo("metadata[\"field with spaces\"] == \"value\"");
	}

	@Test
	public void testSingleQuotedKey() {
		// 'field with spaces' == "value"
		String vectorExpr = this.converter
			.convertExpression(new Expression(EQ, new Key("'field with spaces'"), new Value("value")));
		assertThat(vectorExpr).isEqualTo("metadata[\"field with spaces\"] == \"value\"");
	}

}
