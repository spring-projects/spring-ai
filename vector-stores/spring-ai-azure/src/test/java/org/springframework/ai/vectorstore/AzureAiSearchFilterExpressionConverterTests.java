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

package org.springframework.ai.vectorstore;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.Filter.Group;
import org.springframework.ai.vectorstore.filter.Filter.Key;
import org.springframework.ai.vectorstore.filter.Filter.Value;
import org.springframework.ai.vectorstore.filter.converter.FilterExpressionConverter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.AND;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.EQ;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.GTE;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.IN;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.LTE;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.NE;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.NIN;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.OR;

/**
 * @author Christian Tzolov
 */
public class AzureAiSearchFilterExpressionConverterTests {

	private static String format(String text) {
		return text.trim().replace(" " + System.lineSeparator(), System.lineSeparator()) + "\n";
	}

	@Test
	public void testMissingFilterName() {

		FilterExpressionConverter converter = new AzureAiSearchFilterExpressionConverter(List.of());

		assertThatThrownBy(() -> {
			converter.convertExpression(new Expression(EQ, new Key("country"), new Value("BG")));
		}).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Not allowed filter identifier name: country");
	}

	@Test
	public void testEQ() {
		FilterExpressionConverter converter = new AzureAiSearchFilterExpressionConverter(List.of("country"));

		// country == "BG"
		String vectorExpr = converter.convertExpression(new Expression(EQ, new Key("country"), new Value("BG")));
		assertThat(format(vectorExpr)).isEqualTo("""
				meta_country eq 'BG'
				""");
	}

	@Test
	public void tesEqAndGte() {
		FilterExpressionConverter converter = new AzureAiSearchFilterExpressionConverter(List.of("genre", "year"));

		// genre == "drama" AND year >= 2020
		String vectorExpr = converter
			.convertExpression(new Expression(AND, new Expression(EQ, new Key("genre"), new Value("drama")),
					new Expression(GTE, new Key("year"), new Value(2020))));
		assertThat(format(vectorExpr)).isEqualTo("""
				meta_genre eq 'drama' and meta_year ge 2020
				""");
	}

	@Test
	public void tesIn() {
		FilterExpressionConverter converter = new AzureAiSearchFilterExpressionConverter(List.of("genre"));

		// genre in ["comedy", "documentary", "drama"]
		String vectorExpr = converter.convertExpression(
				new Expression(IN, new Key("genre"), new Value(List.of("comedy", "documentary", "drama"))));
		assertThat(format(vectorExpr)).isEqualTo("""
				search.in(meta_genre, 'comedy,documentary,drama', ',')
				""");
	}

	@Test
	public void tesNin() {
		FilterExpressionConverter converter = new AzureAiSearchFilterExpressionConverter(List.of("genre"));

		// genre in ["comedy", "documentary", "drama"]
		String vectorExpr = converter.convertExpression(
				new Expression(NIN, new Key("genre"), new Value(List.of("comedy", "documentary", "drama"))));
		assertThat(format(vectorExpr)).isEqualTo("""
				not search.in(meta_genre, 'comedy,documentary,drama', ',')
				""");
	}

	@Test
	public void testNe() {
		FilterExpressionConverter converter = new AzureAiSearchFilterExpressionConverter(
				List.of("city", "year", "country"));

		// year >= 2020 OR country == "BG" AND city != "Sofia"
		String vectorExpr = converter
			.convertExpression(new Expression(OR, new Expression(GTE, new Key("year"), new Value(2020)),
					new Expression(AND, new Expression(EQ, new Key("country"), new Value("BG")),
							new Expression(NE, new Key("city"), new Value("Sofia")))));
		assertThat(format(vectorExpr)).isEqualTo("""
				meta_year ge 2020 or meta_country eq 'BG' and meta_city ne 'Sofia'
				""");
	}

	@Test
	public void testGroup() {
		FilterExpressionConverter converter = new AzureAiSearchFilterExpressionConverter(
				List.of("city", "year", "country"));

		// (year >= 2020 OR country == "BG") AND city != "Sofia"
		String vectorExpr = converter.convertExpression(new Expression(AND,
				new Group(new Expression(OR, new Expression(GTE, new Key("year"), new Value(2020)),
						new Expression(EQ, new Key("country"), new Value("BG")))),
				new Expression(NE, new Key("city"), new Value("Sofia"))));

		assertThat(format(vectorExpr)).isEqualTo("""
				(meta_year ge 2020 or meta_country eq 'BG') and meta_city ne 'Sofia'
				""");
	}

	@Test
	public void tesBoolean() {
		FilterExpressionConverter converter = new AzureAiSearchFilterExpressionConverter(
				List.of("isOpen", "year", "country"));

		// isOpen == true AND year >= 2020 AND country IN ["BG", "NL", "US"]
		String vectorExpr = converter.convertExpression(new Expression(AND,
				new Expression(AND, new Expression(EQ, new Key("isOpen"), new Value(true)),
						new Expression(GTE, new Key("year"), new Value(2020))),
				new Expression(IN, new Key("country"), new Value(List.of("BG", "NL", "US")))));

		assertThat(format(vectorExpr)).isEqualTo("""
				meta_isOpen eq true and meta_year ge 2020 and  search.in(meta_country, 'BG,NL,US', ',')
				""");
	}

	@Test
	public void testDecimal() {
		FilterExpressionConverter converter = new AzureAiSearchFilterExpressionConverter(List.of("temperature"));

		// temperature >= -15.6 && temperature <= +20.13
		String vectorExpr = converter
			.convertExpression(new Expression(AND, new Expression(GTE, new Key("temperature"), new Value(-15.6)),
					new Expression(LTE, new Key("temperature"), new Value(20.13))));

		assertThat(format(vectorExpr)).isEqualTo("""
				meta_temperature ge -15.6 and meta_temperature le 20.13
				""");
	}

	@Test
	public void testComplexIdentifiers() {
		FilterExpressionConverter converter = new AzureAiSearchFilterExpressionConverter(List.of("country 1 2 3"));

		String vectorExpr = converter
			.convertExpression(new Expression(EQ, new Key("\"country 1 2 3\""), new Value("BG")));
		assertThat(format(vectorExpr)).isEqualTo("""
				'meta_country 1 2 3' eq 'BG'
				""");

		vectorExpr = converter.convertExpression(new Expression(EQ, new Key("'country 1 2 3'"), new Value("BG")));
		assertThat(format(vectorExpr)).isEqualTo("""
				'meta_country 1 2 3' eq 'BG'
				""");
	}

}