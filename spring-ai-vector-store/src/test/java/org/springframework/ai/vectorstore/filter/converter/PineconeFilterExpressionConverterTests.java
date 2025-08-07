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
public class PineconeFilterExpressionConverterTests {

	FilterExpressionConverter converter = new PineconeFilterExpressionConverter();

	@Test
	public void testEQ() {
		// country == "BG"
		String vectorExpr = this.converter.convertExpression(new Expression(EQ, new Key("country"), new Value("BG")));
		assertThat(vectorExpr).isEqualTo("{\"country\": {\"$eq\": \"BG\"}}");
	}

	@Test
	public void tesEqAndGte() {
		// genre == "drama" AND year >= 2020
		String vectorExpr = this.converter
			.convertExpression(new Expression(AND, new Expression(EQ, new Key("genre"), new Value("drama")),
					new Expression(GTE, new Key("year"), new Value(2020))));
		assertThat(vectorExpr)
			.isEqualTo("{\"$and\": [{\"genre\": {\"$eq\": \"drama\"}},{\"year\": {\"$gte\": 2020}}]}");
	}

	@Test
	public void tesIn() {
		// genre in ["comedy", "documentary", "drama"]
		String vectorExpr = this.converter.convertExpression(
				new Expression(IN, new Key("genre"), new Value(List.of("comedy", "documentary", "drama"))));
		assertThat(vectorExpr).isEqualTo("{\"genre\": {\"$in\": [\"comedy\",\"documentary\",\"drama\"]}}");
	}

	@Test
	public void testNe() {
		// year >= 2020 OR country == "BG" AND city != "Sofia"
		String vectorExpr = this.converter
			.convertExpression(new Expression(OR, new Expression(GTE, new Key("year"), new Value(2020)),
					new Expression(AND, new Expression(EQ, new Key("country"), new Value("BG")),
							new Expression(NE, new Key("city"), new Value("Sofia")))));
		assertThat(vectorExpr).isEqualTo(
				"{\"$or\": [{\"year\": {\"$gte\": 2020}},{\"$and\": [{\"country\": {\"$eq\": \"BG\"}},{\"city\": {\"$ne\": \"Sofia\"}}]}]}");
	}

	@Test
	public void testGroup() {
		// (year >= 2020 OR country == "BG") AND city NIN ["Sofia", "Plovdiv"]
		String vectorExpr = this.converter.convertExpression(new Expression(AND,
				new Group(new Expression(OR, new Expression(GTE, new Key("year"), new Value(2020)),
						new Expression(EQ, new Key("country"), new Value("BG")))),
				new Expression(NIN, new Key("city"), new Value(List.of("Sofia", "Plovdiv")))));
		assertThat(vectorExpr).isEqualTo(
				"{\"$and\": [{\"$or\": [{\"year\": {\"$gte\": 2020}},{\"country\": {\"$eq\": \"BG\"}}]},{\"city\": {\"$nin\": [\"Sofia\",\"Plovdiv\"]}}]}");
	}

	@Test
	public void tesBoolean() {
		// isOpen == true AND year >= 2020 AND country IN ["BG", "NL", "US"]
		String vectorExpr = this.converter.convertExpression(new Expression(AND,
				new Expression(AND, new Expression(EQ, new Key("isOpen"), new Value(true)),
						new Expression(GTE, new Key("year"), new Value(2020))),
				new Expression(IN, new Key("country"), new Value(List.of("BG", "NL", "US")))));

		assertThat(vectorExpr).isEqualTo(
				"{\"$and\": [{\"$and\": [{\"isOpen\": {\"$eq\": true}},{\"year\": {\"$gte\": 2020}}]},{\"country\": {\"$in\": [\"BG\",\"NL\",\"US\"]}}]}");
	}

	@Test
	public void testDecimal() {
		// temperature >= -15.6 && temperature <= +20.13
		String vectorExpr = this.converter
			.convertExpression(new Expression(AND, new Expression(GTE, new Key("temperature"), new Value(-15.6)),
					new Expression(LTE, new Key("temperature"), new Value(20.13))));

		assertThat(vectorExpr)
			.isEqualTo("{\"$and\": [{\"temperature\": {\"$gte\": -15.6}},{\"temperature\": {\"$lte\": 20.13}}]}");
	}

	@Test
	public void testComplexIdentifiers() {
		String vectorExpr = this.converter
			.convertExpression(new Expression(EQ, new Key("\"country 1 2 3\""), new Value("BG")));
		assertThat(vectorExpr).isEqualTo("{\"country 1 2 3\": {\"$eq\": \"BG\"}}");

		vectorExpr = this.converter.convertExpression(new Expression(EQ, new Key("'country 1 2 3'"), new Value("BG")));
		assertThat(vectorExpr).isEqualTo("{\"country 1 2 3\": {\"$eq\": \"BG\"}}");
	}

	@Test
	public void testNumericValues() {
		// score > 85
		String vectorExpr = this.converter.convertExpression(new Expression(GT, new Key("score"), new Value(85)));
		assertThat(vectorExpr).isEqualTo("{\"score\": {\"$gt\": 85}}");
	}

	@Test
	public void testLessThan() {
		// priority < 10
		String vectorExpr = this.converter.convertExpression(new Expression(LT, new Key("priority"), new Value(10)));
		assertThat(vectorExpr).isEqualTo("{\"priority\": {\"$lt\": 10}}");
	}

	@Test
	public void testNotInWithNumbers() {
		// status NIN [100, 200, 404]
		String vectorExpr = this.converter
			.convertExpression(new Expression(NIN, new Key("status"), new Value(List.of(100, 200, 404))));
		assertThat(vectorExpr).isEqualTo("{\"status\": {\"$nin\": [100,200,404]}}");
	}

	@Test
	public void testComplexAndOrCombination() {
		// (category == "A" OR category == "B") AND (value >= 50 AND value <= 100)
		String vectorExpr = this.converter.convertExpression(new Expression(AND,
				new Group(new Expression(OR, new Expression(EQ, new Key("category"), new Value("A")),
						new Expression(EQ, new Key("category"), new Value("B")))),
				new Group(new Expression(AND, new Expression(GTE, new Key("value"), new Value(50)),
						new Expression(LTE, new Key("value"), new Value(100))))));

		assertThat(vectorExpr).isEqualTo(
				"{\"$and\": [{\"$or\": [{\"category\": {\"$eq\": \"A\"}},{\"category\": {\"$eq\": \"B\"}}]},{\"$and\": [{\"value\": {\"$gte\": 50}},{\"value\": {\"$lte\": 100}}]}]}");
	}

	@Test
	public void testNestedGroups() {
		// ((type == "premium" AND level > 5) OR (type == "basic" AND level > 10)) AND
		// active == true
		String vectorExpr = this.converter.convertExpression(new Expression(AND,
				new Group(new Expression(OR,
						new Group(new Expression(AND, new Expression(EQ, new Key("type"), new Value("premium")),
								new Expression(GT, new Key("level"), new Value(5)))),
						new Group(new Expression(AND, new Expression(EQ, new Key("type"), new Value("basic")),
								new Expression(GT, new Key("level"), new Value(10)))))),
				new Expression(EQ, new Key("active"), new Value(true))));

		assertThat(vectorExpr).isEqualTo(
				"{\"$and\": [{\"$or\": [{\"$and\": [{\"type\": {\"$eq\": \"premium\"}},{\"level\": {\"$gt\": 5}}]},{\"$and\": [{\"type\": {\"$eq\": \"basic\"}},{\"level\": {\"$gt\": 10}}]}]},{\"active\": {\"$eq\": true}}]}");
	}

	@Test
	public void testMixedDataTypes() {
		// name == "test" AND count >= 5 AND enabled == true AND ratio <= 0.95
		String vectorExpr = this.converter.convertExpression(new Expression(AND,
				new Expression(AND,
						new Expression(AND, new Expression(EQ, new Key("name"), new Value("test")),
								new Expression(GTE, new Key("count"), new Value(5))),
						new Expression(EQ, new Key("enabled"), new Value(true))),
				new Expression(LTE, new Key("ratio"), new Value(0.95))));

		assertThat(vectorExpr).isEqualTo(
				"{\"$and\": [{\"$and\": [{\"$and\": [{\"name\": {\"$eq\": \"test\"}},{\"count\": {\"$gte\": 5}}]},{\"enabled\": {\"$eq\": true}}]},{\"ratio\": {\"$lte\": 0.95}}]}");
	}

	@Test
	public void testInWithMixedTypes() {
		// tag IN ["A", "B", "C"]
		String vectorExpr = this.converter
			.convertExpression(new Expression(IN, new Key("tag"), new Value(List.of("A", "B", "C"))));
		assertThat(vectorExpr).isEqualTo("{\"tag\": {\"$in\": [\"A\",\"B\",\"C\"]}}");
	}

	@Test
	public void testNegativeNumbers() {
		// balance >= -100.0 AND balance <= -10.0
		String vectorExpr = this.converter
			.convertExpression(new Expression(AND, new Expression(GTE, new Key("balance"), new Value(-100.0)),
					new Expression(LTE, new Key("balance"), new Value(-10.0))));

		assertThat(vectorExpr)
			.isEqualTo("{\"$and\": [{\"balance\": {\"$gte\": -100.0}},{\"balance\": {\"$lte\": -10.0}}]}");
	}

	@Test
	public void testSpecialCharactersInValues() {
		// description == "Item with spaces & symbols!"
		String vectorExpr = this.converter
			.convertExpression(new Expression(EQ, new Key("description"), new Value("Item with spaces & symbols!")));
		assertThat(vectorExpr).isEqualTo("{\"description\": {\"$eq\": \"Item with spaces & symbols!\"}}");
	}

	@Test
	public void testMultipleOrConditions() {
		// status == "pending" OR status == "processing" OR status == "completed"
		String vectorExpr = this.converter.convertExpression(new Expression(OR,
				new Expression(OR, new Expression(EQ, new Key("status"), new Value("pending")),
						new Expression(EQ, new Key("status"), new Value("processing"))),
				new Expression(EQ, new Key("status"), new Value("completed"))));

		assertThat(vectorExpr).isEqualTo(
				"{\"$or\": [{\"$or\": [{\"status\": {\"$eq\": \"pending\"}},{\"status\": {\"$eq\": \"processing\"}}]},{\"status\": {\"$eq\": \"completed\"}}]}");
	}

	@Test
	public void testSingleElementList() {
		// category IN ["single"]
		String vectorExpr = this.converter
			.convertExpression(new Expression(IN, new Key("category"), new Value(List.of("single"))));
		assertThat(vectorExpr).isEqualTo("{\"category\": {\"$in\": [\"single\"]}}");
	}

	@Test
	public void testZeroValues() {
		// quantity == 0 AND price > 0
		String vectorExpr = this.converter
			.convertExpression(new Expression(AND, new Expression(EQ, new Key("quantity"), new Value(0)),
					new Expression(GT, new Key("price"), new Value(0))));

		assertThat(vectorExpr).isEqualTo("{\"$and\": [{\"quantity\": {\"$eq\": 0}},{\"price\": {\"$gt\": 0}}]}");
	}

	@Test
	public void testComplexNestedExpression() {
		// (priority >= 1 AND priority <= 5) OR (urgent == true AND category NIN ["low",
		// "medium"])
		String vectorExpr = this.converter.convertExpression(new Expression(OR,
				new Group(new Expression(AND, new Expression(GTE, new Key("priority"), new Value(1)),
						new Expression(LTE, new Key("priority"), new Value(5)))),
				new Group(new Expression(AND, new Expression(EQ, new Key("urgent"), new Value(true)),
						new Expression(NIN, new Key("category"), new Value(List.of("low", "medium")))))));

		assertThat(vectorExpr).isEqualTo(
				"{\"$or\": [{\"$and\": [{\"priority\": {\"$gte\": 1}},{\"priority\": {\"$lte\": 5}}]},{\"$and\": [{\"urgent\": {\"$eq\": true}},{\"category\": {\"$nin\": [\"low\",\"medium\"]}}]}]}");
	}

}
