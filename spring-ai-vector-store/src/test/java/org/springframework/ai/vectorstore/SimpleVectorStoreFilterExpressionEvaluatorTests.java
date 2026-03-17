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

package org.springframework.ai.vectorstore;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.vectorstore.filter.Filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.AND;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.EQ;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.GT;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.GTE;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.IN;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.ISNOTNULL;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.ISNULL;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.LT;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.LTE;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.NE;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.NIN;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.NOT;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.OR;

/**
 * Tests for {@link SimpleVectorStoreFilterExpressionEvaluator}.
 *
 * @author Christian Tzolov
 */
class SimpleVectorStoreFilterExpressionEvaluatorTests {

	private final SimpleVectorStoreFilterExpressionEvaluator evaluator = new SimpleVectorStoreFilterExpressionEvaluator();

	// -------------------------------------------------------------------------
	// Comparison operators
	// -------------------------------------------------------------------------

	@Test
	void testEq() {
		var expr = new Filter.Expression(EQ, new Filter.Key("country"), new Filter.Value("BG"));
		assertThat(this.evaluator.evaluate(expr, Map.of("country", "BG"))).isTrue();
		assertThat(this.evaluator.evaluate(expr, Map.of("country", "NL"))).isFalse();
	}

	@Test
	void testNe() {
		var expr = new Filter.Expression(NE, new Filter.Key("country"), new Filter.Value("BG"));
		assertThat(this.evaluator.evaluate(expr, Map.of("country", "NL"))).isTrue();
		assertThat(this.evaluator.evaluate(expr, Map.of("country", "BG"))).isFalse();
	}

	@Test
	void testGt() {
		var expr = new Filter.Expression(GT, new Filter.Key("year"), new Filter.Value(2020));
		assertThat(this.evaluator.evaluate(expr, Map.of("year", 2021))).isTrue();
		assertThat(this.evaluator.evaluate(expr, Map.of("year", 2020))).isFalse();
		assertThat(this.evaluator.evaluate(expr, Map.of("year", 2019))).isFalse();
	}

	@Test
	void testGte() {
		var expr = new Filter.Expression(GTE, new Filter.Key("year"), new Filter.Value(2020));
		assertThat(this.evaluator.evaluate(expr, Map.of("year", 2020))).isTrue();
		assertThat(this.evaluator.evaluate(expr, Map.of("year", 2021))).isTrue();
		assertThat(this.evaluator.evaluate(expr, Map.of("year", 2019))).isFalse();
	}

	@Test
	void testLt() {
		var expr = new Filter.Expression(LT, new Filter.Key("year"), new Filter.Value(2020));
		assertThat(this.evaluator.evaluate(expr, Map.of("year", 2019))).isTrue();
		assertThat(this.evaluator.evaluate(expr, Map.of("year", 2020))).isFalse();
	}

	@Test
	void testLte() {
		var expr = new Filter.Expression(LTE, new Filter.Key("year"), new Filter.Value(2020));
		assertThat(this.evaluator.evaluate(expr, Map.of("year", 2020))).isTrue();
		assertThat(this.evaluator.evaluate(expr, Map.of("year", 2019))).isTrue();
		assertThat(this.evaluator.evaluate(expr, Map.of("year", 2021))).isFalse();
	}

	// -------------------------------------------------------------------------
	// Logical operators
	// -------------------------------------------------------------------------

	@Test
	void testAnd() {
		var expr = new Filter.Expression(AND,
				new Filter.Expression(EQ, new Filter.Key("genre"), new Filter.Value("drama")),
				new Filter.Expression(GTE, new Filter.Key("year"), new Filter.Value(2020)));

		assertThat(this.evaluator.evaluate(expr, Map.of("genre", "drama", "year", 2020))).isTrue();
		assertThat(this.evaluator.evaluate(expr, Map.of("genre", "comedy", "year", 2020))).isFalse();
		assertThat(this.evaluator.evaluate(expr, Map.of("genre", "drama", "year", 2019))).isFalse();
	}

	@Test
	void testOr() {
		var expr = new Filter.Expression(OR, new Filter.Expression(GTE, new Filter.Key("year"), new Filter.Value(2020)),
				new Filter.Expression(AND, new Filter.Expression(EQ, new Filter.Key("country"), new Filter.Value("BG")),
						new Filter.Expression(NE, new Filter.Key("city"), new Filter.Value("Sofia"))));

		assertThat(this.evaluator.evaluate(expr, Map.of("city", "Seoul", "year", 2020, "country", "BG"))).isTrue();
		assertThat(this.evaluator.evaluate(expr, Map.of("city", "Seoul", "year", 2019, "country", "BG"))).isTrue();
		assertThat(this.evaluator.evaluate(expr, Map.of("city", "Sofia", "year", 2019, "country", "BG"))).isFalse();
	}

	@Test
	void testNot() {
		var expr = new Filter.Expression(NOT,
				new Filter.Expression(EQ, new Filter.Key("country"), new Filter.Value("BG")));
		assertThat(this.evaluator.evaluate(expr, Map.of("country", "NL"))).isTrue();
		assertThat(this.evaluator.evaluate(expr, Map.of("country", "BG"))).isFalse();
	}

	// -------------------------------------------------------------------------
	// Collection operators
	// -------------------------------------------------------------------------

	@Test
	void testIn() {
		var expr = new Filter.Expression(IN, new Filter.Key("genre"),
				new Filter.Value(List.of("comedy", "documentary", "drama")));
		assertThat(this.evaluator.evaluate(expr, Map.of("genre", "drama"))).isTrue();
		assertThat(this.evaluator.evaluate(expr, Map.of("genre", "comedy"))).isTrue();
		assertThat(this.evaluator.evaluate(expr, Map.of("genre", "action"))).isFalse();
	}

	@Test
	void testNin() {
		var expr = new Filter.Expression(NIN, new Filter.Key("city"), new Filter.Value(List.of("Sofia", "Plovdiv")));
		assertThat(this.evaluator.evaluate(expr, Map.of("city", "Seoul"))).isTrue();
		assertThat(this.evaluator.evaluate(expr, Map.of("city", "Sofia"))).isFalse();
	}

	// -------------------------------------------------------------------------
	// Null checks
	// -------------------------------------------------------------------------

	@Test
	void testIsNull() {
		var expr = new Filter.Expression(ISNULL, new Filter.Key("country"));
		Map<String, Object> withNull = new java.util.HashMap<>();
		withNull.put("country", null);
		assertThat(this.evaluator.evaluate(expr, withNull)).isTrue();
		// missing key → null
		assertThat(this.evaluator.evaluate(expr, Map.of("year", 2020))).isTrue();
		assertThat(this.evaluator.evaluate(expr, Map.of("country", "BG"))).isFalse();
	}

	@Test
	void testIsNotNull() {
		var expr = new Filter.Expression(ISNOTNULL, new Filter.Key("country"));
		assertThat(this.evaluator.evaluate(expr, Map.of("country", "BG"))).isTrue();
		// missing key → null
		assertThat(this.evaluator.evaluate(expr, Map.of("year", 2020))).isFalse();
	}

	// -------------------------------------------------------------------------
	// Group (precedence)
	// -------------------------------------------------------------------------

	@Test
	void testGroup() {
		// (year >= 2020 OR country == "BG") AND city NIN ["Sofia", "Plovdiv"]
		var expr = new Filter.Expression(AND,
				new Filter.Group(new Filter.Expression(OR,
						new Filter.Expression(GTE, new Filter.Key("year"), new Filter.Value(2020)),
						new Filter.Expression(EQ, new Filter.Key("country"), new Filter.Value("BG")))),
				new Filter.Expression(NIN, new Filter.Key("city"), new Filter.Value(List.of("Sofia", "Plovdiv"))));

		assertThat(this.evaluator.evaluate(expr, Map.of("city", "Seoul", "year", 2020, "country", "BG"))).isTrue();
		assertThat(this.evaluator.evaluate(expr, Map.of("city", "Sofia", "year", 2020, "country", "BG"))).isFalse();
		assertThat(this.evaluator.evaluate(expr, Map.of("city", "Seoul", "year", 2019, "country", "NL"))).isFalse();
	}

	// -------------------------------------------------------------------------
	// Type handling
	// -------------------------------------------------------------------------

	@Test
	void testBoolean() {
		// isOpen == true AND year >= 2020 AND country IN ["BG", "NL", "US"]
		var expr = new Filter.Expression(AND,
				new Filter.Expression(AND, new Filter.Expression(EQ, new Filter.Key("isOpen"), new Filter.Value(true)),
						new Filter.Expression(GTE, new Filter.Key("year"), new Filter.Value(2020))),
				new Filter.Expression(IN, new Filter.Key("country"), new Filter.Value(List.of("BG", "NL", "US"))));

		assertThat(this.evaluator.evaluate(expr, Map.of("isOpen", true, "year", 2020, "country", "NL"))).isTrue();
		assertThat(this.evaluator.evaluate(expr, Map.of("isOpen", false, "year", 2020, "country", "NL"))).isFalse();
		assertThat(this.evaluator.evaluate(expr, Map.of("isOpen", true, "year", 2019, "country", "NL"))).isFalse();
		assertThat(this.evaluator.evaluate(expr, Map.of("isOpen", true, "year", 2020, "country", "KR"))).isFalse();
	}

	@Test
	void testDecimal() {
		var expr = new Filter.Expression(AND,
				new Filter.Expression(GTE, new Filter.Key("temperature"), new Filter.Value(-15.6)),
				new Filter.Expression(LTE, new Filter.Key("temperature"), new Filter.Value(20.13)));

		assertThat(this.evaluator.evaluate(expr, Map.of("temperature", -15.6))).isTrue();
		assertThat(this.evaluator.evaluate(expr, Map.of("temperature", 20.13))).isTrue();
		assertThat(this.evaluator.evaluate(expr, Map.of("temperature", -1.6))).isTrue();
		assertThat(this.evaluator.evaluate(expr, Map.of("temperature", -16.0))).isFalse();
		assertThat(this.evaluator.evaluate(expr, Map.of("temperature", 21.0))).isFalse();
	}

	@Test
	void testNumericCrossTypeComparison() {
		// metadata Integer vs filter Integer — should work via double promotion
		var expr = new Filter.Expression(GTE, new Filter.Key("year"), new Filter.Value(2020));
		assertThat(this.evaluator.evaluate(expr, Map.of("year", 2020))).isTrue();
		// metadata Integer vs filter Double
		assertThat(this.evaluator.evaluate(expr, Map.of("year", Integer.valueOf(2020)))).isTrue();
	}

	@Test
	void testInNumericCrossType() {
		// metadata Integer, filter list contains Long — must match via double promotion
		var expr = new Filter.Expression(IN, new Filter.Key("year"), new Filter.Value(List.of(2019L, 2020L, 2021L)));
		assertThat(this.evaluator.evaluate(expr, Map.of("year", Integer.valueOf(2020)))).isTrue();
		assertThat(this.evaluator.evaluate(expr, Map.of("year", Integer.valueOf(2022)))).isFalse();

		// metadata Double, filter list contains Integer
		var expr2 = new Filter.Expression(IN, new Filter.Key("score"), new Filter.Value(List.of(1, 2, 3)));
		assertThat(this.evaluator.evaluate(expr2, Map.of("score", 2.0))).isTrue();
		assertThat(this.evaluator.evaluate(expr2, Map.of("score", 4.0))).isFalse();
	}

	@Test
	void testNinNumericCrossType() {
		// metadata Long, filter list contains Integer
		var expr = new Filter.Expression(NIN, new Filter.Key("year"), new Filter.Value(List.of(2020, 2021)));
		assertThat(this.evaluator.evaluate(expr, Map.of("year", Long.valueOf(2022)))).isTrue();
		assertThat(this.evaluator.evaluate(expr, Map.of("year", Long.valueOf(2020)))).isFalse();
	}

	@Test
	void testDate() {
		var expr = new Filter.Expression(EQ, new Filter.Key("activationDate"),
				new Filter.Value(new Date(1704637752148L)));
		assertThat(this.evaluator.evaluate(expr, Map.of("activationDate", "2024-01-07T14:29:12Z"))).isTrue();
		assertThat(this.evaluator.evaluate(expr, Map.of("activationDate", "2024-01-07T00:00:00Z"))).isFalse();
	}

	@Test
	void testQuotedKey() {
		var exprDoubleQuote = new Filter.Expression(EQ, new Filter.Key("\"country 1 2 3\""), new Filter.Value("BG"));
		assertThat(this.evaluator.evaluate(exprDoubleQuote, Map.of("country 1 2 3", "BG"))).isTrue();

		var exprSingleQuote = new Filter.Expression(EQ, new Filter.Key("'country 1 2 3'"), new Filter.Value("BG"));
		assertThat(this.evaluator.evaluate(exprSingleQuote, Map.of("country 1 2 3", "BG"))).isTrue();
	}

}
