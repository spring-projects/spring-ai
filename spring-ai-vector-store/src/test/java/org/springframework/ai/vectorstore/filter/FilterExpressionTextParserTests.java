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
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.Filter.Group;
import org.springframework.ai.vectorstore.filter.Filter.Key;
import org.springframework.ai.vectorstore.filter.Filter.Value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.AND;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.EQ;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.GTE;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.IN;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.LTE;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.NE;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.NIN;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.NOT;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.OR;

/**
 * @author Christian Tzolov
 * @author Sun Yuhan
 * @author lance
 */
class FilterExpressionTextParserTests {

	FilterExpressionTextParser parser = new FilterExpressionTextParser();

	@Test
	void testEQ() {
		// country == "BG"
		Expression exp = this.parser.parse("country == 'BG'");
		assertThat(exp).isEqualTo(new Expression(EQ, new Key("country"), new Value("BG")));

		assertThat(this.parser.getCache()).containsEntry("WHERE " + "country == 'BG'", exp);
	}

	@Test
	void tesEqAndGte() {
		// genre == "drama" AND year >= 2020
		Expression exp = this.parser.parse("genre == 'drama' && year >= 2020");
		assertThat(exp).isEqualTo(new Expression(AND, new Expression(EQ, new Key("genre"), new Value("drama")),
				new Expression(GTE, new Key("year"), new Value(2020))));

		assertThat(this.parser.getCache()).containsEntry("WHERE " + "genre == 'drama' && year >= 2020", exp);
	}

	@Test
	void tesIn() {
		// genre in ["comedy", "documentary", "drama"]
		Expression exp = this.parser.parse("genre in ['comedy', 'documentary', 'drama']");
		assertThat(exp)
			.isEqualTo(new Expression(IN, new Key("genre"), new Value(List.of("comedy", "documentary", "drama"))));

		assertThat(this.parser.getCache()).containsEntry("WHERE " + "genre in ['comedy', 'documentary', 'drama']", exp);
	}

	@Test
	void testNe() {
		// year >= 2020 OR country == "BG" AND city != "Sofia"
		Expression exp = this.parser.parse("year >= 2020 OR country == \"BG\" AND city != \"Sofia\"");
		assertThat(exp).isEqualTo(new Expression(OR, new Expression(GTE, new Key("year"), new Value(2020)),
				new Expression(AND, new Expression(EQ, new Key("country"), new Value("BG")),
						new Expression(NE, new Key("city"), new Value("Sofia")))));

		assertThat(this.parser.getCache())
			.containsEntry("WHERE " + "year >= 2020 OR country == \"BG\" AND city != \"Sofia\"", exp);
	}

	@Test
	void testGroup() {
		// (year >= 2020 OR country == "BG") AND city NIN ["Sofia", "Plovdiv"]
		Expression exp = this.parser.parse("(year >= 2020 OR country == \"BG\") AND city NIN [\"Sofia\", \"Plovdiv\"]");

		assertThat(exp).isEqualTo(new Expression(AND,
				new Group(new Expression(OR, new Expression(GTE, new Key("year"), new Value(2020)),
						new Expression(EQ, new Key("country"), new Value("BG")))),
				new Expression(NIN, new Key("city"), new Value(List.of("Sofia", "Plovdiv")))));

		assertThat(this.parser.getCache())
			.containsEntry("WHERE " + "(year >= 2020 OR country == \"BG\") AND city NIN [\"Sofia\", \"Plovdiv\"]", exp);
	}

	@Test
	void tesBoolean() {
		// isOpen == true AND year >= 2020 AND country IN ["BG", "NL", "US"]
		Expression exp = this.parser.parse("isOpen == true AND year >= 2020 AND country IN [\"BG\", \"NL\", \"US\"]");

		assertThat(exp).isEqualTo(new Expression(AND,
				new Expression(AND, new Expression(EQ, new Key("isOpen"), new Value(true)),
						new Expression(GTE, new Key("year"), new Value(2020))),
				new Expression(IN, new Key("country"), new Value(List.of("BG", "NL", "US")))));

		assertThat(this.parser.getCache())
			.containsEntry("WHERE " + "isOpen == true AND year >= 2020 AND country IN [\"BG\", \"NL\", \"US\"]", exp);
	}

	@Test
	void tesNot() {
		// NOT(isOpen == true AND year >= 2020 AND country IN ["BG", "NL", "US"])
		Expression exp = this.parser
			.parse("not(isOpen == true AND year >= 2020 AND country IN [\"BG\", \"NL\", \"US\"])");

		assertThat(exp).isEqualTo(new Expression(NOT,
				new Group(new Expression(AND,
						new Expression(AND, new Expression(EQ, new Key("isOpen"), new Value(true)),
								new Expression(GTE, new Key("year"), new Value(2020))),
						new Expression(IN, new Key("country"), new Value(List.of("BG", "NL", "US"))))),
				null));

		assertThat(this.parser.getCache()).containsEntry(
				"WHERE " + "not(isOpen == true AND year >= 2020 AND country IN [\"BG\", \"NL\", \"US\"])", exp);
	}

	@Test
	void tesNotNin() {
		// NOT(country NOT IN ["BG", "NL", "US"])
		Expression exp = this.parser.parse("not(country NOT IN [\"BG\", \"NL\", \"US\"])");

		assertThat(exp).isEqualTo(new Expression(NOT,
				new Group(new Expression(NIN, new Key("country"), new Value(List.of("BG", "NL", "US")))), null));
	}

	@Test
	void tesNotNin2() {
		// NOT country NOT IN ["BG", "NL", "US"]
		Expression exp = this.parser.parse("NOT country NOT IN [\"BG\", \"NL\", \"US\"]");

		assertThat(exp).isEqualTo(new Expression(NOT,
				new Expression(NIN, new Key("country"), new Value(List.of("BG", "NL", "US"))), null));
	}

	@Test
	void tesNestedNot() {
		// NOT(isOpen == true AND year >= 2020 AND NOT(country IN ["BG", "NL", "US"]))
		Expression exp = this.parser
			.parse("not(isOpen == true AND year >= 2020 AND NOT(country IN [\"BG\", \"NL\", \"US\"]))");

		assertThat(exp).isEqualTo(new Expression(NOT,
				new Group(new Expression(AND,
						new Expression(AND, new Expression(EQ, new Key("isOpen"), new Value(true)),
								new Expression(GTE, new Key("year"), new Value(2020))),
						new Expression(NOT,
								new Group(new Expression(IN, new Key("country"), new Value(List.of("BG", "NL", "US")))),
								null))),
				null));

		assertThat(this.parser.getCache()).containsEntry(
				"WHERE " + "not(isOpen == true AND year >= 2020 AND NOT(country IN [\"BG\", \"NL\", \"US\"]))", exp);
	}

	@Test
	void testDecimal() {
		// temperature >= -15.6 && temperature <= +20.13
		String expText = "temperature >= -15.6 && temperature <= +20.13";
		Expression exp = this.parser.parse(expText);

		assertThat(exp).isEqualTo(new Expression(AND, new Expression(GTE, new Key("temperature"), new Value(-15.6)),
				new Expression(LTE, new Key("temperature"), new Value(20.13))));

		assertThat(this.parser.getCache()).containsEntry("WHERE " + expText, exp);
	}

	@Test
	void testLong() {
		Expression exp2 = this.parser.parse("biz_id == 3L");
		Expression exp3 = this.parser.parse("biz_id == -5L");

		assertThat(exp2).isEqualTo(new Expression(EQ, new Key("biz_id"), new Value(3L)));
		assertThat(exp3).isEqualTo(new Expression(EQ, new Key("biz_id"), new Value(-5L)));
	}

	@Test
	void testIdentifiers() {
		Expression exp = this.parser.parse("'country.1' == 'BG'");
		assertThat(exp).isEqualTo(new Expression(EQ, new Key("'country.1'"), new Value("BG")));

		exp = this.parser.parse("'country_1_2_3' == 'BG'");
		assertThat(exp).isEqualTo(new Expression(EQ, new Key("'country_1_2_3'"), new Value("BG")));

		exp = this.parser.parse("\"country 1 2 3\" == 'BG'");
		assertThat(exp).isEqualTo(new Expression(EQ, new Key("\"country 1 2 3\""), new Value("BG")));
	}

	@Test
	void testUnescapedIdentifierWithUnderscores() {
		Expression exp = this.parser.parse("file_name == 'medicaid-wa-faqs.pdf'");
		assertThat(exp).isEqualTo(new Expression(EQ, new Key("file_name"), new Value("medicaid-wa-faqs.pdf")));
	}

	@MethodSource("constantConstantProvider")
	@ParameterizedTest(name = "{index} => [{0}, expected={1}]")
	void testConstants(String expr, Object expectedValue) {
		Expression result = this.parser.parse(expr);
		assertThat(result).isEqualTo(new Expression(EQ, new Key("id"), new Value(expectedValue)));
	}

	static Stream<Arguments> constantConstantProvider() {
		return Stream.of(Arguments.of("id==" + Integer.MAX_VALUE, Integer.MAX_VALUE),
				Arguments.of("id==" + Integer.MIN_VALUE, Integer.MIN_VALUE),
				Arguments.of("id==" + Long.MAX_VALUE, Long.MAX_VALUE),
				Arguments.of("id==" + Long.MIN_VALUE, Long.MIN_VALUE), Arguments.of("id==" + 0x100, 0x100),
				Arguments.of("id==" + 1000000000000L, 1000000000000L), Arguments.of("id==" + Math.PI, Math.PI));
	}

}
