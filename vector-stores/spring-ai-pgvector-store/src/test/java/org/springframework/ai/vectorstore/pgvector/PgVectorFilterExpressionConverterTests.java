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

package org.springframework.ai.vectorstore.pgvector;

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
 * @author Muthukumaran Navaneethakrishnan
 * @author Christian Tzolov
 */
public class PgVectorFilterExpressionConverterTests {

	FilterExpressionConverter converter = new PgVectorFilterExpressionConverter();

	@Test
	public void testEQ() {
		// country == "BG"
		String vectorExpr = this.converter.convertExpression(new Expression(EQ, new Key("country"), new Value("BG")));
		assertThat(vectorExpr).isEqualTo("$.country == \"BG\"");
	}

	@Test
	public void tesEqAndGte() {
		// genre == "drama" AND year >= 2020
		String vectorExpr = this.converter
			.convertExpression(new Expression(AND, new Expression(EQ, new Key("genre"), new Value("drama")),
					new Expression(GTE, new Key("year"), new Value(2020))));
		assertThat(vectorExpr).isEqualTo("$.genre == \"drama\" && $.year >= 2020");
	}

	@Test
	public void tesIn() {
		// genre in ["comedy", "documentary", "drama"]
		String vectorExpr = this.converter.convertExpression(
				new Expression(IN, new Key("genre"), new Value(List.of("comedy", "documentary", "drama"))));
		assertThat(vectorExpr)
			.isEqualTo("($.genre == \"comedy\" || $.genre == \"documentary\" || $.genre == \"drama\")");
	}

	@Test
	public void testNe() {
		// year >= 2020 OR country == "BG" AND city != "Sofia"
		String vectorExpr = this.converter
			.convertExpression(new Expression(OR, new Expression(GTE, new Key("year"), new Value(2020)),
					new Expression(AND, new Expression(EQ, new Key("country"), new Value("BG")),
							new Expression(NE, new Key("city"), new Value("Sofia")))));
		assertThat(vectorExpr).isEqualTo("$.year >= 2020 || $.country == \"BG\" && $.city != \"Sofia\"");
	}

	@Test
	public void testGroup() {
		// (year >= 2020 OR country == "BG") AND city NIN ["Sofia", "Plovdiv"]
		String vectorExpr = this.converter.convertExpression(new Expression(AND,
				new Group(new Expression(OR, new Expression(GTE, new Key("year"), new Value(2020)),
						new Expression(EQ, new Key("country"), new Value("BG")))),
				new Expression(NIN, new Key("city"), new Value(List.of("Sofia", "Plovdiv")))));
		assertThat(vectorExpr)
			.isEqualTo("($.year >= 2020 || $.country == \"BG\") && !($.city == \"Sofia\" || $.city == \"Plovdiv\")");
	}

	@Test
	public void tesBoolean() {
		// isOpen == true AND year >= 2020 AND country IN ["BG", "NL", "US"]
		String vectorExpr = this.converter.convertExpression(new Expression(AND,
				new Expression(AND, new Expression(EQ, new Key("isOpen"), new Value(true)),
						new Expression(GTE, new Key("year"), new Value(2020))),
				new Expression(IN, new Key("country"), new Value(List.of("BG", "NL", "US")))));

		assertThat(vectorExpr).isEqualTo(
				"$.isOpen == true && $.year >= 2020 && ($.country == \"BG\" || $.country == \"NL\" || $.country == \"US\")");
	}

	@Test
	public void testDecimal() {
		// temperature >= -15.6 && temperature <= +20.13
		String vectorExpr = this.converter
			.convertExpression(new Expression(AND, new Expression(GTE, new Key("temperature"), new Value(-15.6)),
					new Expression(LTE, new Key("temperature"), new Value(20.13))));

		assertThat(vectorExpr).isEqualTo("$.temperature >= -15.6 && $.temperature <= 20.13");
	}

	@Test
	public void testComplexIdentifiers() {
		String vectorExpr = this.converter
			.convertExpression(new Expression(EQ, new Key("\"country 1 2 3\""), new Value("BG")));
		assertThat(vectorExpr).isEqualTo("$.\"country 1 2 3\" == \"BG\"");
	}

	@Test
	public void testLT() {
		// value < 100
		String vectorExpr = this.converter.convertExpression(new Expression(LT, new Key("value"), new Value(100)));
		assertThat(vectorExpr).isEqualTo("$.value < 100");
	}

	@Test
	public void testGT() {
		// score > 75
		String vectorExpr = this.converter.convertExpression(new Expression(GT, new Key("score"), new Value(100)));
		assertThat(vectorExpr).isEqualTo("$.score > 100");
	}

	@Test
	public void testLTE() {
		// amount <= 100.5
		String vectorExpr = this.converter.convertExpression(new Expression(LTE, new Key("amount"), new Value(100.5)));
		assertThat(vectorExpr).isEqualTo("$.amount <= 100.5");
	}

	@Test
	public void testNIN() {
		// category NOT IN ["typeA", "typeB"]
		String vectorExpr = this.converter
			.convertExpression(new Expression(NIN, new Key("category"), new Value(List.of("typeA", "typeB"))));
		assertThat(vectorExpr).isEqualTo("!($.category == \"typeA\" || $.category == \"typeB\")");
	}

	@Test
	public void testSingleValueIN() {
		// status IN ["active"] - single value in list
		String vectorExpr = this.converter
			.convertExpression(new Expression(IN, new Key("status"), new Value(List.of("active"))));
		assertThat(vectorExpr).isEqualTo("($.status == \"active\")");
	}

	@Test
	public void testSingleValueNIN() {
		// status NOT IN ["inactive"] - single value in list
		String vectorExpr = this.converter
			.convertExpression(new Expression(NIN, new Key("status"), new Value(List.of("inactive"))));
		assertThat(vectorExpr).isEqualTo("!($.status == \"inactive\")");
	}

	@Test
	public void testNumericIN() {
		// priority IN [1, 2, 3]
		String vectorExpr = this.converter
			.convertExpression(new Expression(IN, new Key("priority"), new Value(List.of(1, 2, 3))));
		assertThat(vectorExpr).isEqualTo("($.priority == 1 || $.priority == 2 || $.priority == 3)");
	}

	@Test
	public void testNumericNIN() {
		// level NOT IN [0, 10]
		String vectorExpr = this.converter
			.convertExpression(new Expression(NIN, new Key("level"), new Value(List.of(0, 10))));
		assertThat(vectorExpr).isEqualTo("!($.level == 0 || $.level == 10)");
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
		assertThat(vectorExpr).isEqualTo(
				"(($.score >= 80 && $.type == \"A\") || ($.score >= 90 && $.type == \"B\")) && $.status == \"valid\"");
	}

	@Test
	public void testBooleanFalse() {
		// active == false
		String vectorExpr = this.converter.convertExpression(new Expression(EQ, new Key("active"), new Value(false)));
		assertThat(vectorExpr).isEqualTo("$.active == false");
	}

	@Test
	public void testBooleanNE() {
		// active != true
		String vectorExpr = this.converter.convertExpression(new Expression(NE, new Key("active"), new Value(true)));
		assertThat(vectorExpr).isEqualTo("$.active != true");
	}

	@Test
	public void testKeyWithDots() {
		// "config.setting" == "value1"
		String vectorExpr = this.converter
			.convertExpression(new Expression(EQ, new Key("\"config.setting\""), new Value("value1")));
		assertThat(vectorExpr).isEqualTo("$.\"config.setting\" == \"value1\"");
	}

	@Test
	public void testEmptyString() {
		// description == ""
		String vectorExpr = this.converter.convertExpression(new Expression(EQ, new Key("description"), new Value("")));
		assertThat(vectorExpr).isEqualTo("$.description == \"\"");
	}

	@Test
	public void testNullValue() {
		// metadata == null
		String vectorExpr = this.converter.convertExpression(new Expression(EQ, new Key("metadata"), new Value(null)));
		assertThat(vectorExpr).isEqualTo("$.metadata == null");
	}

	@Test
	public void testComplexOrExpression() {
		// state == "ready" OR state == "pending" OR state == "processing"
		String vectorExpr = this.converter.convertExpression(new Expression(OR,
				new Expression(OR, new Expression(EQ, new Key("state"), new Value("ready")),
						new Expression(EQ, new Key("state"), new Value("pending"))),
				new Expression(EQ, new Key("state"), new Value("processing"))));
		assertThat(vectorExpr).isEqualTo("$.state == \"ready\" || $.state == \"pending\" || $.state == \"processing\"");
	}

}
