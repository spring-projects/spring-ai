/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.vectorstore.gemfire;

import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionConverter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.AND;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.EQ;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.GT;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.GTE;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.IN;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.LTE;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.NE;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.NIN;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.OR;

/**
 * @author Jason Huynh
 */
class GemFireAiSearchFilterExpressionConverterTest {

	final FilterExpressionConverter converter = new GemFireAiSearchFilterExpressionConverter();

	@Test
	public void testDate() {
		String vectorExpr = this.converter.convertExpression(new Filter.Expression(EQ, new Filter.Key("activationDate"),
				new Filter.Value(new Date(1704637752148L))));
		assertThat(vectorExpr).isEqualTo("activationDate:2024-01-07T14:29:12Z");

		vectorExpr = this.converter.convertExpression(
				new Filter.Expression(EQ, new Filter.Key("activationDate"), new Filter.Value("1970-01-01T00:00:02Z")));
		assertThat(vectorExpr).isEqualTo("activationDate:1970-01-01T00:00:02Z");
	}

	@Test
	public void testEQ() {
		String vectorExpr = this.converter
			.convertExpression(new Filter.Expression(EQ, new Filter.Key("country"), new Filter.Value("BG")));
		assertThat(vectorExpr).isEqualTo("country:BG");
	}

	@Test
	public void testEqAndGte() {
		String vectorExpr = this.converter.convertExpression(new Filter.Expression(AND,
				new Filter.Expression(EQ, new Filter.Key("genre"), new Filter.Value("drama")),
				new Filter.Expression(GTE, new Filter.Key("year"), new Filter.Value(2020))));
		assertThat(vectorExpr).isEqualTo("genre:drama AND year:[2020 TO *]");
	}

	@Test
	public void testEqAndGe() {
		String vectorExpr = this.converter.convertExpression(new Filter.Expression(AND,
				new Filter.Expression(EQ, new Filter.Key("genre"), new Filter.Value("drama")),
				new Filter.Expression(GT, new Filter.Key("year"), new Filter.Value(2020))));
		assertThat(vectorExpr).isEqualTo("genre:drama AND year:{2020 TO *]");
	}

	@Test
	public void testIn() {
		String vectorExpr = this.converter.convertExpression(new Filter.Expression(IN, new Filter.Key("genre"),
				new Filter.Value(List.of("comedy", "documentary", "drama"))));
		assertThat(vectorExpr).isEqualTo("genre:(comedy OR documentary OR drama)");
	}

	@Test
	public void testNe() {
		String vectorExpr = this.converter.convertExpression(
				new Filter.Expression(OR, new Filter.Expression(GTE, new Filter.Key("year"), new Filter.Value(2020)),
						new Filter.Expression(AND,
								new Filter.Expression(EQ, new Filter.Key("country"), new Filter.Value("BG")),
								new Filter.Expression(NE, new Filter.Key("city"), new Filter.Value("Sofia")))));
		assertThat(vectorExpr).isEqualTo("year:[2020 TO *] OR country:BG AND city: NOT Sofia");
	}

	@Test
	public void testGroup() {
		String vectorExpr = this.converter.convertExpression(new Filter.Expression(AND,
				new Filter.Group(new Filter.Expression(OR,
						new Filter.Expression(GTE, new Filter.Key("year"), new Filter.Value(2020)),
						new Filter.Expression(EQ, new Filter.Key("country"), new Filter.Value("BG")))),
				new Filter.Expression(NIN, new Filter.Key("city"), new Filter.Value(List.of("Sofia", "Plovdiv")))));
		assertThat(vectorExpr).isEqualTo("(year:[2020 TO *] OR country:BG) AND NOT city:(Sofia OR Plovdiv)");
	}

	@Test
	public void testBoolean() {
		String vectorExpr = this.converter.convertExpression(new Filter.Expression(AND,
				new Filter.Expression(AND, new Filter.Expression(EQ, new Filter.Key("isOpen"), new Filter.Value(true)),
						new Filter.Expression(GTE, new Filter.Key("year"), new Filter.Value(2020))),
				new Filter.Expression(IN, new Filter.Key("country"), new Filter.Value(List.of("BG", "NL", "US")))));

		assertThat(vectorExpr).isEqualTo("isOpen:true AND year:[2020 TO *] AND country:(BG OR NL OR US)");
	}

	@Test
	public void testDecimal() {
		String vectorExpr = this.converter.convertExpression(new Filter.Expression(AND,
				new Filter.Expression(GTE, new Filter.Key("temperature"), new Filter.Value(-15.6)),
				new Filter.Expression(LTE, new Filter.Key("temperature"), new Filter.Value(20.13))));

		assertThat(vectorExpr).isEqualTo("temperature:[-15.6 TO *] AND temperature:[* TO 20.13]");
	}

	@Test
	public void testComplexIdentifiers() {
		String vectorExpr = this.converter
			.convertExpression(new Filter.Expression(EQ, new Filter.Key("\"country 1 2 3\""), new Filter.Value("BG")));
		assertThat(vectorExpr).isEqualTo("country 1 2 3:BG");

		vectorExpr = this.converter
			.convertExpression(new Filter.Expression(EQ, new Filter.Key("'country 1 2 3'"), new Filter.Value("BG")));
		assertThat(vectorExpr).isEqualTo("country 1 2 3:BG");
	}

}
