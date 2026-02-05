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

package org.springframework.ai.vectorstore.filter.converter;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionConverter;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.AND;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.EQ;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.GTE;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.IN;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.LTE;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.NE;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.NIN;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.OR;

/**
 * @author Jemin Huh
 */
public class SimpleVectorStoreFilterExpressionConverterTests {

	final FilterExpressionConverter converter = new SimpleVectorStoreFilterExpressionConverter();

	@Test
	public void testDate() {
		String vectorExpr = this.converter.convertExpression(new Filter.Expression(EQ, new Filter.Key("activationDate"),
				new Filter.Value(new Date(1704637752148L))));
		assertThat(vectorExpr).isEqualTo("#metadata['activationDate'] == '2024-01-07T14:29:12Z'");

		StandardEvaluationContext context = new StandardEvaluationContext();
		ExpressionParser parser = new SpelExpressionParser();
		context.setVariable("metadata",
				Map.of("activationDate", "2024-01-07T14:29:12Z", "year", 2020, "country", "BG"));
		Assertions.assertEquals(Boolean.TRUE, parser.parseExpression(vectorExpr).getValue(context, Boolean.class));

		vectorExpr = this.converter.convertExpression(
				new Filter.Expression(EQ, new Filter.Key("activationDate"), new Filter.Value("1970-01-01T00:00:02Z")));
		assertThat(vectorExpr).isEqualTo("#metadata['activationDate'] == '1970-01-01T00:00:02Z'");

		context.setVariable("metadata",
				Map.of("activationDate", "1970-01-01T00:00:02Z", "year", 2020, "country", "BG"));
		Assertions.assertEquals(Boolean.TRUE, parser.parseExpression(vectorExpr).getValue(context, Boolean.class));

	}

	@Test
	public void testDatesConcurrently() {
		IntStream.range(0, 10).parallel().forEach(i -> {
			String vectorExpr = this.converter.convertExpression(new Filter.Expression(EQ,
					new Filter.Key("activationDate"), new Filter.Value(new Date(1704637752148L))));
			String vectorExpr2 = this.converter.convertExpression(new Filter.Expression(EQ,
					new Filter.Key("activationDate"), new Filter.Value(new Date(1704637753150L))));
			assertThat(vectorExpr).isEqualTo("#metadata['activationDate'] == '2024-01-07T14:29:12Z'");
			assertThat(vectorExpr2).isEqualTo("#metadata['activationDate'] == '2024-01-07T14:29:13Z'");
		});
	}

	@Test
	public void testEQ() {
		String vectorExpr = this.converter
			.convertExpression(new Filter.Expression(EQ, new Filter.Key("country"), new Filter.Value("BG")));
		assertThat(vectorExpr).isEqualTo("#metadata['country'] == 'BG'");

		StandardEvaluationContext context = new StandardEvaluationContext();
		ExpressionParser parser = new SpelExpressionParser();
		context.setVariable("metadata", Map.of("city", "Seoul", "year", 2020, "country", "BG"));
		Assertions.assertEquals(Boolean.TRUE, parser.parseExpression(vectorExpr).getValue(context, Boolean.class));

	}

	@Test
	public void tesEqAndGte() {
		String vectorExpr = this.converter.convertExpression(new Filter.Expression(AND,
				new Filter.Expression(EQ, new Filter.Key("genre"), new Filter.Value("drama")),
				new Filter.Expression(GTE, new Filter.Key("year"), new Filter.Value(2020))));
		assertThat(vectorExpr).isEqualTo("#metadata['genre'] == 'drama' and #metadata['year'] >= 2020");

		StandardEvaluationContext context = new StandardEvaluationContext();
		ExpressionParser parser = new SpelExpressionParser();
		context.setVariable("metadata", Map.of("genre", "drama", "year", 2020, "country", "BG"));
		Assertions.assertEquals(Boolean.TRUE, parser.parseExpression(vectorExpr).getValue(context, Boolean.class));

	}

	@Test
	public void tesIn() {
		String vectorExpr = this.converter.convertExpression(new Filter.Expression(IN, new Filter.Key("genre"),
				new Filter.Value(List.of("comedy", "documentary", "drama"))));
		assertThat(vectorExpr).isEqualTo("{'comedy','documentary','drama'}.contains(#metadata['genre'])");

		StandardEvaluationContext context = new StandardEvaluationContext();
		ExpressionParser parser = new SpelExpressionParser();
		context.setVariable("metadata", Map.of("genre", "drama", "year", 2020, "country", "BG"));
		Assertions.assertEquals(Boolean.TRUE, parser.parseExpression(vectorExpr).getValue(context, Boolean.class));

	}

	@Test
	public void testNe() {
		String vectorExpr = this.converter.convertExpression(
				new Filter.Expression(OR, new Filter.Expression(GTE, new Filter.Key("year"), new Filter.Value(2020)),
						new Filter.Expression(AND,
								new Filter.Expression(EQ, new Filter.Key("country"), new Filter.Value("BG")),
								new Filter.Expression(NE, new Filter.Key("city"), new Filter.Value("Sofia")))));
		assertThat(vectorExpr)
			.isEqualTo("#metadata['year'] >= 2020 or #metadata['country'] == 'BG' and #metadata['city'] != 'Sofia'");

		StandardEvaluationContext context = new StandardEvaluationContext();
		ExpressionParser parser = new SpelExpressionParser();
		context.setVariable("metadata", Map.of("city", "Seoul", "year", 2020, "country", "BG"));
		Assertions.assertEquals(Boolean.TRUE, parser.parseExpression(vectorExpr).getValue(context, Boolean.class));

	}

	@Test
	public void testGroup() {
		String vectorExpr = this.converter.convertExpression(new Filter.Expression(AND,
				new Filter.Group(new Filter.Expression(OR,
						new Filter.Expression(GTE, new Filter.Key("year"), new Filter.Value(2020)),
						new Filter.Expression(EQ, new Filter.Key("country"), new Filter.Value("BG")))),
				new Filter.Expression(NIN, new Filter.Key("city"), new Filter.Value(List.of("Sofia", "Plovdiv")))));
		assertThat(vectorExpr).isEqualTo(
				"(#metadata['year'] >= 2020 or #metadata['country'] == 'BG') and not {'Sofia','Plovdiv'}.contains(#metadata['city'])");

		StandardEvaluationContext context = new StandardEvaluationContext();
		ExpressionParser parser = new SpelExpressionParser();
		context.setVariable("metadata", Map.of("city", "Seoul", "year", 2020, "country", "BG"));
		Assertions.assertEquals(Boolean.TRUE, parser.parseExpression(vectorExpr).getValue(context, Boolean.class));

	}

	@Test
	public void tesBoolean() {
		String vectorExpr = this.converter.convertExpression(new Filter.Expression(AND,
				new Filter.Expression(AND, new Filter.Expression(EQ, new Filter.Key("isOpen"), new Filter.Value(true)),
						new Filter.Expression(GTE, new Filter.Key("year"), new Filter.Value(2020))),
				new Filter.Expression(IN, new Filter.Key("country"), new Filter.Value(List.of("BG", "NL", "US")))));

		assertThat(vectorExpr).isEqualTo(
				"#metadata['isOpen'] == true and #metadata['year'] >= 2020 and {'BG','NL','US'}.contains(#metadata['country'])");

		StandardEvaluationContext context = new StandardEvaluationContext();
		ExpressionParser parser = new SpelExpressionParser();
		context.setVariable("metadata", Map.of("isOpen", true, "year", 2020, "country", "NL"));
		Assertions.assertEquals(Boolean.TRUE, parser.parseExpression(vectorExpr).getValue(context, Boolean.class));

		vectorExpr = this.converter.convertExpression(new Filter.Expression(AND,
				new Filter.Expression(AND, new Filter.Expression(EQ, new Filter.Key("isOpen"), new Filter.Value(true)),
						new Filter.Expression(GTE, new Filter.Key("year"), new Filter.Value(2020))),
				new Filter.Expression(NIN, new Filter.Key("country"), new Filter.Value(List.of("BG", "NL", "US")))));

		assertThat(vectorExpr).isEqualTo(
				"#metadata['isOpen'] == true and #metadata['year'] >= 2020 and not {'BG','NL','US'}.contains(#metadata['country'])");

		context.setVariable("metadata", Map.of("isOpen", true, "year", 2020, "country", "KR"));
		Assertions.assertEquals(Boolean.TRUE, parser.parseExpression(vectorExpr).getValue(context, Boolean.class));
	}

	@Test
	public void testDecimal() {
		String vectorExpr = this.converter.convertExpression(new Filter.Expression(AND,
				new Filter.Expression(GTE, new Filter.Key("temperature"), new Filter.Value(-15.6)),
				new Filter.Expression(LTE, new Filter.Key("temperature"), new Filter.Value(20.13))));

		assertThat(vectorExpr).isEqualTo("#metadata['temperature'] >= -15.6 and #metadata['temperature'] <= 20.13");

		StandardEvaluationContext context = new StandardEvaluationContext();
		ExpressionParser parser = new SpelExpressionParser();
		context.setVariable("metadata", Map.of("temperature", -15.6));
		Assertions.assertEquals(Boolean.TRUE, parser.parseExpression(vectorExpr).getValue(context, Boolean.class));
		context.setVariable("metadata", Map.of("temperature", 20.13));
		Assertions.assertEquals(Boolean.TRUE, parser.parseExpression(vectorExpr).getValue(context, Boolean.class));
		context.setVariable("metadata", Map.of("temperature", -1.6));
		Assertions.assertEquals(Boolean.TRUE, parser.parseExpression(vectorExpr).getValue(context, Boolean.class));

	}

	@Test
	public void testComplexIdentifiers() {
		String vectorExpr = this.converter
			.convertExpression(new Filter.Expression(EQ, new Filter.Key("\"country 1 2 3\""), new Filter.Value("BG")));
		assertThat(vectorExpr).isEqualTo("#metadata['country 1 2 3'] == 'BG'");

		vectorExpr = this.converter
			.convertExpression(new Filter.Expression(EQ, new Filter.Key("'country 1 2 3'"), new Filter.Value("BG")));
		assertThat(vectorExpr).isEqualTo("#metadata['country 1 2 3'] == 'BG'");

		StandardEvaluationContext context = new StandardEvaluationContext();
		ExpressionParser parser = new SpelExpressionParser();
		context.setVariable("metadata", Map.of("country 1 2 3", "BG"));
		Assertions.assertEquals(Boolean.TRUE, parser.parseExpression(vectorExpr).getValue(context, Boolean.class));
	}

	@Test
	void eqExpressionWithListAndKeyContainingInSpaceShouldNotUseContains() {
		Filter.Expression expr = new Filter.Expression(
				Filter.ExpressionType.EQ,
				new Filter.Key("pin code"),
				new Filter.Value(List.of("1234", "5678"))
		);

		String result = converter.convertExpression(expr);

		Assertions.assertEquals("#metadata['pin code'] == {'1234','5678'}", result);
	}

}
