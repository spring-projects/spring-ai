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

package org.springframework.ai.vectorstore.filter;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.Filter.Group;
import org.springframework.ai.vectorstore.filter.Filter.Key;
import org.springframework.ai.vectorstore.filter.Filter.Value;

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
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.NOT;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.OR;

/**
 * @author Christian Tzolov
 */
public class FilterExpressionBuilderTests {

	FilterExpressionBuilder b = new FilterExpressionBuilder();

	@Test
	public void testEQ() {
		// country == "BG"
		assertThat(this.b.eq("country", "BG").build())
			.isEqualTo(new Expression(EQ, new Key("country"), new Value("BG")));
	}

	@Test
	public void tesEqAndGte() {
		// genre == "drama" AND year >= 2020
		Expression exp = this.b.and(this.b.eq("genre", "drama"), this.b.gte("year", 2020)).build();
		assertThat(exp).isEqualTo(new Expression(AND, new Expression(EQ, new Key("genre"), new Value("drama")),
				new Expression(GTE, new Key("year"), new Value(2020))));
	}

	@Test
	public void testIn() {
		// genre in ["comedy", "documentary", "drama"]
		var exp = this.b.in("genre", "comedy", "documentary", "drama").build();
		assertThat(exp)
			.isEqualTo(new Expression(IN, new Key("genre"), new Value(List.of("comedy", "documentary", "drama"))));
	}

	@Test
	public void testNe() {
		// year >= 2020 OR country == "BG" AND city != "Sofia"
		var exp = this.b
			.and(this.b.or(this.b.gte("year", 2020), this.b.eq("country", "BG")), this.b.ne("city", "Sofia"))
			.build();

		assertThat(exp).isEqualTo(new Expression(AND,
				new Expression(OR, new Expression(GTE, new Key("year"), new Value(2020)),
						new Expression(EQ, new Key("country"), new Value("BG"))),
				new Expression(NE, new Key("city"), new Value("Sofia"))));
	}

	@Test
	public void testGroup() {
		// (year >= 2020 OR country == "BG") AND city NIN ["Sofia", "Plovdiv"]
		var exp = this.b
			.and(this.b.group(this.b.or(this.b.gte("year", 2020), this.b.eq("country", "BG"))),
					this.b.nin("city", "Sofia", "Plovdiv"))
			.build();

		assertThat(exp).isEqualTo(new Expression(AND,
				new Group(new Expression(OR, new Expression(GTE, new Key("year"), new Value(2020)),
						new Expression(EQ, new Key("country"), new Value("BG")))),
				new Expression(NIN, new Key("city"), new Value(List.of("Sofia", "Plovdiv")))));
	}

	@Test
	public void tesIn2() {
		// isOpen == true AND year >= 2020 AND country IN ["BG", "NL", "US"]
		var exp = this.b
			.and(this.b.and(this.b.eq("isOpen", true), this.b.gte("year", 2020)),
					this.b.in("country", "BG", "NL", "US"))
			.build();

		assertThat(exp).isEqualTo(new Expression(AND,
				new Expression(AND, new Expression(EQ, new Key("isOpen"), new Value(true)),
						new Expression(GTE, new Key("year"), new Value(2020))),
				new Expression(IN, new Key("country"), new Value(List.of("BG", "NL", "US")))));
	}

	@Test
	public void tesNot() {
		// isOpen == true AND year >= 2020 AND country IN ["BG", "NL", "US"]
		var exp = this.b.not(this.b.and(this.b.and(this.b.eq("isOpen", true), this.b.gte("year", 2020)),
				this.b.in("country", "BG", "NL", "US")))
			.build();

		assertThat(exp).isEqualTo(new Expression(NOT,
				new Expression(AND,
						new Expression(AND, new Expression(EQ, new Key("isOpen"), new Value(true)),
								new Expression(GTE, new Key("year"), new Value(2020))),
						new Expression(IN, new Key("country"), new Value(List.of("BG", "NL", "US")))),
				null));
	}

	@Test
	public void testLessThanOperators() {
		// value < 1
		var ltExp = this.b.lt("value", 1).build();
		assertThat(ltExp).isEqualTo(new Expression(LT, new Key("value"), new Value(1)));

		// value <= 1
		var lteExp = this.b.lte("value", 1).build();
		assertThat(lteExp).isEqualTo(new Expression(LTE, new Key("value"), new Value(1)));
	}

	@Test
	public void testGreaterThanOperators() {
		// value > 1
		var gtExp = this.b.gt("value", 1).build();
		assertThat(gtExp).isEqualTo(new Expression(GT, new Key("value"), new Value(1)));

		// value >= 10
		var gteExp = this.b.gte("value", 10).build();
		assertThat(gteExp).isEqualTo(new Expression(GTE, new Key("value"), new Value(10)));
	}

	@Test
	public void testNullValues() {
		// status == null
		var exp = this.b.eq("status", null).build();
		assertThat(exp).isEqualTo(new Expression(EQ, new Key("status"), new Value(null)));
	}

	@Test
	public void testEmptyInClause() {
		// category IN []
		var exp = this.b.in("category").build();
		assertThat(exp).isEqualTo(new Expression(IN, new Key("category"), new Value(List.of())));
	}

	@Test
	public void testSingleValueInClause() {
		// type IN ["basic"]
		var exp = this.b.in("type", "basic").build();
		assertThat(exp).isEqualTo(new Expression(IN, new Key("type"), new Value(List.of("basic"))));
	}

	@Test
	public void testComplexNestedGroups() {
		// ((level >= 1 AND level <= 5) OR status == "special") AND (region IN ["north",
		// "south"] OR enabled == true)
		var exp = this.b.and(
				this.b.or(this.b.group(this.b.and(this.b.gte("level", 1), this.b.lte("level", 5))),
						this.b.eq("status", "special")),
				this.b.group(this.b.or(this.b.in("region", "north", "south"), this.b.eq("enabled", true))))
			.build();

		Expression expected = new Expression(AND,
				new Expression(OR,
						new Group(new Expression(AND, new Expression(GTE, new Key("level"), new Value(1)),
								new Expression(LTE, new Key("level"), new Value(5)))),
						new Expression(EQ, new Key("status"), new Value("special"))),
				new Group(
						new Expression(OR, new Expression(IN, new Key("region"), new Value(List.of("north", "south"))),
								new Expression(EQ, new Key("enabled"), new Value(true)))));

		assertThat(exp).isEqualTo(expected);
	}

	@Test
	public void testNotWithSimpleExpression() {
		// NOT (active == true)
		var exp = this.b.not(this.b.eq("active", true)).build();
		assertThat(exp).isEqualTo(new Expression(NOT, new Expression(EQ, new Key("active"), new Value(true)), null));
	}

	@Test
	public void testNotWithGroup() {
		// NOT (level >= 3 AND region == "east")
		var exp = this.b.not(this.b.group(this.b.and(this.b.gte("level", 3), this.b.eq("region", "east")))).build();

		Expression expected = new Expression(NOT,
				new Group(new Expression(AND, new Expression(GTE, new Key("level"), new Value(3)),
						new Expression(EQ, new Key("region"), new Value("east")))),
				null);

		assertThat(exp).isEqualTo(expected);
	}

	@Test
	public void testMultipleNotOperators() {
		// NOT (NOT (active == true))
		var exp = this.b.not(this.b.not(this.b.eq("active", true))).build();

		Expression expected = new Expression(NOT,
				new Expression(NOT, new Expression(EQ, new Key("active"), new Value(true)), null), null);

		assertThat(exp).isEqualTo(expected);
	}

	@Test
	public void testSpecialCharactersInKeys() {
		// "item.name" == "test" AND "meta-data" != null
		var exp = this.b.and(this.b.eq("item.name", "test"), this.b.ne("meta-data", null)).build();

		Expression expected = new Expression(AND, new Expression(EQ, new Key("item.name"), new Value("test")),
				new Expression(NE, new Key("meta-data"), new Value(null)));

		assertThat(exp).isEqualTo(expected);
	}

	@Test
	public void testEmptyStringValues() {
		// description == "" OR label != ""
		var exp = this.b.or(this.b.eq("description", ""), this.b.ne("label", "")).build();

		Expression expected = new Expression(OR, new Expression(EQ, new Key("description"), new Value("")),
				new Expression(NE, new Key("label"), new Value("")));

		assertThat(exp).isEqualTo(expected);
	}

}
