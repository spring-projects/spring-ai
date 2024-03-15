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

package org.springframework.ai.vectorstore;

import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionConverter;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.*;

class ElasticsearchAiSearchFilterExpressionConverterTest {

	final FilterExpressionConverter converter = new ElasticsearchAiSearchFilterExpressionConverter();

	@Test
	public void testDate() {
		String vectorExpr = converter.convertExpression(new Filter.Expression(EQ, new Filter.Key("activationDate"),
				new Filter.Value(new Date(1704637752148L))));
		assertThat(vectorExpr).isEqualTo("metadata.activationDate:2024-01-07T14:29:12Z");

		vectorExpr = converter.convertExpression(
				new Filter.Expression(EQ, new Filter.Key("activationDate"), new Filter.Value("1970-01-01T00:00:02Z")));
		assertThat(vectorExpr).isEqualTo("metadata.activationDate:1970-01-01T00:00:02Z");
	}

	@Test
	public void testEQ() {
		String vectorExpr = converter
			.convertExpression(new Filter.Expression(EQ, new Filter.Key("country"), new Filter.Value("BG")));
		assertThat(vectorExpr).isEqualTo("metadata.country:BG");
	}

	@Test
	public void tesEqAndGte() {
		String vectorExpr = converter.convertExpression(new Filter.Expression(AND,
				new Filter.Expression(EQ, new Filter.Key("genre"), new Filter.Value("drama")),
				new Filter.Expression(GTE, new Filter.Key("year"), new Filter.Value(2020))));
		assertThat(vectorExpr).isEqualTo("metadata.genre:drama AND metadata.year:>=2020");
	}

	@Test
	public void tesIn() {
		String vectorExpr = converter.convertExpression(new Filter.Expression(IN, new Filter.Key("genre"),
				new Filter.Value(List.of("comedy", "documentary", "drama"))));
		assertThat(vectorExpr).isEqualTo("(metadata.genre:comedy OR documentary OR drama)");
	}

	@Test
	public void testNe() {
		String vectorExpr = converter.convertExpression(
				new Filter.Expression(OR, new Filter.Expression(GTE, new Filter.Key("year"), new Filter.Value(2020)),
						new Filter.Expression(AND,
								new Filter.Expression(EQ, new Filter.Key("country"), new Filter.Value("BG")),
								new Filter.Expression(NE, new Filter.Key("city"), new Filter.Value("Sofia")))));
		assertThat(vectorExpr).isEqualTo("metadata.year:>=2020 OR metadata.country:BG AND metadata.city: NOT Sofia");
	}

	@Test
	public void testGroup() {
		String vectorExpr = converter.convertExpression(new Filter.Expression(AND,
				new Filter.Group(new Filter.Expression(OR,
						new Filter.Expression(GTE, new Filter.Key("year"), new Filter.Value(2020)),
						new Filter.Expression(EQ, new Filter.Key("country"), new Filter.Value("BG")))),
				new Filter.Expression(NIN, new Filter.Key("city"), new Filter.Value(List.of("Sofia", "Plovdiv")))));
		assertThat(vectorExpr)
			.isEqualTo("(metadata.year:>=2020 OR metadata.country:BG) AND NOT (metadata.city:Sofia OR Plovdiv)");
	}

	@Test
	public void tesBoolean() {
		String vectorExpr = converter.convertExpression(new Filter.Expression(AND,
				new Filter.Expression(AND, new Filter.Expression(EQ, new Filter.Key("isOpen"), new Filter.Value(true)),
						new Filter.Expression(GTE, new Filter.Key("year"), new Filter.Value(2020))),
				new Filter.Expression(IN, new Filter.Key("country"), new Filter.Value(List.of("BG", "NL", "US")))));

		assertThat(vectorExpr)
			.isEqualTo("metadata.isOpen:true AND metadata.year:>=2020 AND (metadata.country:BG OR NL OR US)");
	}

	@Test
	public void testDecimal() {
		String vectorExpr = converter.convertExpression(new Filter.Expression(AND,
				new Filter.Expression(GTE, new Filter.Key("temperature"), new Filter.Value(-15.6)),
				new Filter.Expression(LTE, new Filter.Key("temperature"), new Filter.Value(20.13))));

		assertThat(vectorExpr).isEqualTo("metadata.temperature:>=-15.6 AND metadata.temperature:<=20.13");
	}

	@Test
	public void testComplexIdentifiers() {
		String vectorExpr = converter
			.convertExpression(new Filter.Expression(EQ, new Filter.Key("\"country 1 2 3\""), new Filter.Value("BG")));
		assertThat(vectorExpr).isEqualTo("metadata.country 1 2 3:BG");

		vectorExpr = converter
			.convertExpression(new Filter.Expression(EQ, new Filter.Key("'country 1 2 3'"), new Filter.Value("BG")));
		assertThat(vectorExpr).isEqualTo("metadata.country 1 2 3:BG");
	}

}
