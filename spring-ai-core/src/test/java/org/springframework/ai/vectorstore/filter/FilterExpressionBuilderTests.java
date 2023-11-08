/*
 * Copyright 2023-2023 the original author or authors.
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
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.GTE;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.IN;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.NE;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.NIN;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.OR;

/**
 * @author Christian Tzolov
 */
public class FilterExpressionBuilderTests {

	FilterExpressionBuilder b = new FilterExpressionBuilder();

	@Test
	public void testEQ() {
		// country == "BG"
		assertThat(b.eq("country", "BG").build()).isEqualTo(new Expression(EQ, new Key("country"), new Value("BG")));
	}

	@Test
	public void tesEqAndGte() {
		// genre == "drama" AND year >= 2020
		Expression exp = b.and(b.eq("genre", "drama"), b.gte("year", 2020)).build();
		assertThat(exp).isEqualTo(new Expression(AND, new Expression(EQ, new Key("genre"), new Value("drama")),
				new Expression(GTE, new Key("year"), new Value(2020))));
	}

	@Test
	public void testIn() {
		// genre in ["comedy", "documentary", "drama"]
		var exp = b.in("genre", "comedy", "documentary", "drama").build();
		assertThat(exp)
			.isEqualTo(new Expression(IN, new Key("genre"), new Value(List.of("comedy", "documentary", "drama"))));
	}

	@Test
	public void testNe() {
		// year >= 2020 OR country == "BG" AND city != "Sofia"
		var exp = b.and(b.or(b.gte("year", 2020), b.eq("country", "BG")), b.ne("city", "Sofia")).build();

		assertThat(exp).isEqualTo(new Expression(AND,
				new Expression(OR, new Expression(GTE, new Key("year"), new Value(2020)),
						new Expression(EQ, new Key("country"), new Value("BG"))),
				new Expression(NE, new Key("city"), new Value("Sofia"))));
	}

	@Test
	public void testGroup() {
		// (year >= 2020 OR country == "BG") AND city NIN ["Sofia", "Plovdiv"]
		var exp = b.and(b.group(b.or(b.gte("year", 2020), b.eq("country", "BG"))), b.nin("city", "Sofia", "Plovdiv"))
			.build();

		assertThat(exp).isEqualTo(new Expression(AND,
				new Group(new Expression(OR, new Expression(GTE, new Key("year"), new Value(2020)),
						new Expression(EQ, new Key("country"), new Value("BG")))),
				new Expression(NIN, new Key("city"), new Value(List.of("Sofia", "Plovdiv")))));
	}

	@Test
	public void tesIn2() {
		// isOpen == true AND year >= 2020 AND country IN ["BG", "NL", "US"]
		var exp = b.and(b.and(b.eq("isOpen", true), b.gte("year", 2020)), b.in("country", "BG", "NL", "US")).build();

		assertThat(exp).isEqualTo(new Expression(AND,
				new Expression(AND, new Expression(EQ, new Key("isOpen"), new Value(true)),
						new Expression(GTE, new Key("year"), new Value(2020))),
				new Expression(IN, new Key("country"), new Value(List.of("BG", "NL", "US")))));
	}

}
