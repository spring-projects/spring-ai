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

package org.springframework.ai.vectorstore.azure;

import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.vectorstore.azure.AzureVectorStore.MetadataField;
import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.Filter.Group;
import org.springframework.ai.vectorstore.filter.Filter.Key;
import org.springframework.ai.vectorstore.filter.Filter.Value;
import org.springframework.ai.vectorstore.filter.FilterExpressionConverter;

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

	@Test
	public void testMissingFilterName() {

		FilterExpressionConverter converter = new AzureAiSearchFilterExpressionConverter(List.of());

		assertThatThrownBy(() -> converter.convertExpression(new Expression(EQ, new Key("country"), new Value("BG"))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Not allowed filter identifier name: country");
	}

	@Test
	public void testDate() {
		FilterExpressionConverter converter = new AzureAiSearchFilterExpressionConverter(
				List.of(MetadataField.date("activationDate")));

		// country >= 1970-01-01T00:00:02Z
		String vectorExpr = converter
			.convertExpression(new Expression(EQ, new Key("activationDate"), new Value(new Date(2000))));
		assertThat(vectorExpr).isEqualTo("meta_activationDate eq 1970-01-01T00:00:02Z");

		vectorExpr = converter
			.convertExpression(new Expression(EQ, new Key("activationDate"), new Value("1970-01-01T00:00:02Z")));
		assertThat(vectorExpr).isEqualTo("meta_activationDate eq 1970-01-01T00:00:02Z");
	}

	@Test
	public void testEQ() {
		FilterExpressionConverter converter = new AzureAiSearchFilterExpressionConverter(
				List.of(MetadataField.text("country")));

		// country == "BG"
		String expected = "meta_country eq 'BG'";
		String vectorExpr = converter.convertExpression(new Expression(EQ, new Key("country"), new Value("BG")));
		assertThat(vectorExpr).isEqualTo(expected);
	}

	@Test
	public void tesEqAndGte() {
		FilterExpressionConverter converter = new AzureAiSearchFilterExpressionConverter(
				List.of(MetadataField.text("genre"), MetadataField.int32("year")));

		// genre == "drama" AND year >= 2020
		String expected = "meta_genre eq 'drama' and meta_year ge 2020";
		String vectorExpr = converter
			.convertExpression(new Expression(AND, new Expression(EQ, new Key("genre"), new Value("drama")),
					new Expression(GTE, new Key("year"), new Value(2020))));
		assertThat(vectorExpr).isEqualTo(expected);
	}

	@Test
	public void tesIn() {
		FilterExpressionConverter converter = new AzureAiSearchFilterExpressionConverter(
				List.of(MetadataField.text("genre")));

		// genre in ["comedy", "documentary", "drama"]
		String expected = " search.in(meta_genre, 'comedy,documentary,drama', ',')";
		String vectorExpr = converter.convertExpression(
				new Expression(IN, new Key("genre"), new Value(List.of("comedy", "documentary", "drama"))));
		assertThat(vectorExpr).isEqualTo(expected);
	}

	@Test
	public void tesNin() {
		FilterExpressionConverter converter = new AzureAiSearchFilterExpressionConverter(
				List.of(MetadataField.text("genre")));

		// genre in ["comedy", "documentary", "drama"]
		String expected = " not search.in(meta_genre, 'comedy,documentary,drama', ',')";
		String vectorExpr = converter.convertExpression(
				new Expression(NIN, new Key("genre"), new Value(List.of("comedy", "documentary", "drama"))));
		assertThat(vectorExpr).isEqualTo(expected);
	}

	@Test
	public void testNe() {
		FilterExpressionConverter converter = new AzureAiSearchFilterExpressionConverter(
				List.of(MetadataField.text("city"), MetadataField.int64("year"), MetadataField.text("country")));

		// year >= 2020 OR country == "BG" AND city != "Sofia"
		String expected = "meta_year ge 2020 or meta_country eq 'BG' and meta_city ne 'Sofia'";
		String vectorExpr = converter
			.convertExpression(new Expression(OR, new Expression(GTE, new Key("year"), new Value(2020)),
					new Expression(AND, new Expression(EQ, new Key("country"), new Value("BG")),
							new Expression(NE, new Key("city"), new Value("Sofia")))));
		assertThat(vectorExpr).isEqualTo(expected);
	}

	@Test
	public void testGroup() {
		FilterExpressionConverter converter = new AzureAiSearchFilterExpressionConverter(
				List.of(MetadataField.text("city"), MetadataField.int64("year"), MetadataField.text("country")));

		// (year >= 2020 OR country == "BG") AND city != "Sofia"
		String expected = "(meta_year ge 2020 or meta_country eq 'BG') and meta_city ne 'Sofia'";
		String vectorExpr = converter.convertExpression(new Expression(AND,
				new Group(new Expression(OR, new Expression(GTE, new Key("year"), new Value(2020)),
						new Expression(EQ, new Key("country"), new Value("BG")))),
				new Expression(NE, new Key("city"), new Value("Sofia"))));
		assertThat(vectorExpr).isEqualTo(expected);
	}

	@Test
	public void tesBoolean() {
		FilterExpressionConverter converter = new AzureAiSearchFilterExpressionConverter(
				List.of(MetadataField.bool("isOpen"), MetadataField.int64("year"), MetadataField.text("country")));

		// isOpen == true AND year >= 2020 AND country IN ["BG", "NL", "US"]
		String expected = "meta_isOpen eq true and meta_year ge 2020 and  search.in(meta_country, 'BG,NL,US', ',')";
		String vectorExpr = converter.convertExpression(new Expression(AND,
				new Expression(AND, new Expression(EQ, new Key("isOpen"), new Value(true)),
						new Expression(GTE, new Key("year"), new Value(2020))),
				new Expression(IN, new Key("country"), new Value(List.of("BG", "NL", "US")))));
		assertThat(vectorExpr).isEqualTo(expected);
	}

	@Test
	public void testDecimal() {
		FilterExpressionConverter converter = new AzureAiSearchFilterExpressionConverter(
				List.of(MetadataField.decimal("temperature")));

		// temperature >= -15.6 && temperature <= +20.13
		String vectorExpr = converter
			.convertExpression(new Expression(AND, new Expression(GTE, new Key("temperature"), new Value(-15.6)),
					new Expression(LTE, new Key("temperature"), new Value(20.13))));
		String expected = "meta_temperature ge -15.6 and meta_temperature le 20.13";
	}

	@Test
	public void testComplexIdentifiers() {
		FilterExpressionConverter converter = new AzureAiSearchFilterExpressionConverter(
				List.of(MetadataField.text("country 1 2 3")));

		String expected = "'meta_country 1 2 3' eq 'BG'";
		String vectorExpr = converter
			.convertExpression(new Expression(EQ, new Key("\"country 1 2 3\""), new Value("BG")));
		assertThat(vectorExpr).isEqualTo(expected);

		vectorExpr = converter.convertExpression(new Expression(EQ, new Key("'country 1 2 3'"), new Value("BG")));
		assertThat(vectorExpr).isEqualTo(expected);
	}

}
