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

package org.springframework.ai.vectorstore.infinispan;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import org.springframework.ai.vectorstore.filter.Filter;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.AND;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.EQ;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.GT;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.GTE;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.IN;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.ISNOTNULL;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.ISNULL;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.LT;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.LTE;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.NE;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.NIN;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.NOT;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.OR;

public class InfinispanFilterExpressionConverterTest {

	private final InfinispanFilterExpressionConverter converter = new InfinispanFilterExpressionConverter();

	@Test
	void shouldMapNull() {
		assertThat(this.converter.convertExpression(null)).isEmpty();
	}

	@ParameterizedTest
	@MethodSource("stringComparisonFilterExpression")
	void shouldMapStringComparisonFilterExpression(Filter.Expression expression, String expectedQuery,
			String expectedJoin) {
		assertQueryAndJoin(expression, expectedQuery, expectedJoin);
	}

	static List<Arguments> stringComparisonFilterExpression() {
		return Arrays.asList(
				Arguments.of(new Filter.Expression(EQ, new Filter.Key("name"), new Filter.Value("John")),
						"m0.name='name' and m0.value='John'", " join i.metadata m0"),
				Arguments.of(new Filter.Expression(NE, new Filter.Key("status"), new Filter.Value("active")),
						"m0.name='status' and m0.value!='active' OR (i.metadata is null)", " join i.metadata m0"),
				Arguments.of(new Filter.Expression(GT, new Filter.Key("name"), new Filter.Value("A")),
						"m0.name='name' and m0.value>'A'", " join i.metadata m0"),
				Arguments.of(new Filter.Expression(GTE, new Filter.Key("name"), new Filter.Value("A")),
						"m0.name='name' and m0.value>='A'", " join i.metadata m0"),
				Arguments.of(new Filter.Expression(LT, new Filter.Key("name"), new Filter.Value("Z")),
						"m0.name='name' and m0.value<'Z'", " join i.metadata m0"),
				Arguments.of(new Filter.Expression(LTE, new Filter.Key("name"), new Filter.Value("Z")),
						"m0.name='name' and m0.value<='Z'", " join i.metadata m0"));
	}

	@ParameterizedTest
	@MethodSource("numericComparisonFilterExpression")
	void shouldMapNumericComparisonFilterExpression(Filter.Expression expression, String expectedQuery,
			String expectedJoin) {
		assertQueryAndJoin(expression, expectedQuery, expectedJoin);
	}

	static List<Arguments> numericComparisonFilterExpression() {
		return Arrays.asList(
				Arguments.of(new Filter.Expression(EQ, new Filter.Key("age"), new Filter.Value(25)),
						"m0.name='age' and m0.value_int=25", " join i.metadata m0"),
				Arguments.of(new Filter.Expression(EQ, new Filter.Key("age"), new Filter.Value(123L)),
						"m0.name='age' and m0.value_int=123", " join i.metadata m0"),
				Arguments.of(new Filter.Expression(EQ, new Filter.Key("score"), new Filter.Value(3.14f)),
						"m0.name='score' and m0.value_float=3.140000104904175", " join i.metadata m0"),
				Arguments.of(new Filter.Expression(EQ, new Filter.Key("price"), new Filter.Value(99.99d)),
						"m0.name='price' and m0.value_float=99.99", " join i.metadata m0"),
				Arguments.of(new Filter.Expression(GT, new Filter.Key("age"), new Filter.Value(18)),
						"m0.name='age' and m0.value_int>18", " join i.metadata m0"),
				Arguments.of(new Filter.Expression(LT, new Filter.Key("score"), new Filter.Value(4.5f)),
						"m0.name='score' and m0.value_float<4.5", " join i.metadata m0"));
	}

	@ParameterizedTest
	@MethodSource("inFilters")
	void shouldMapInFilterExpression(Filter.Expression expression, String expectedQuery, String expectedJoin) {
		assertQueryAndJoin(expression, expectedQuery, expectedJoin);
	}

	static List<Arguments> inFilters() {
		return Arrays.asList(
				Arguments.of(
						new Filter.Expression(IN, new Filter.Key("category"), new Filter.Value(List.of("A", "B", "C"))),
						"m0.name='category' and m0.value IN ('A', 'B', 'C')", " join i.metadata m0"),
				Arguments.of(new Filter.Expression(IN, new Filter.Key("status"), new Filter.Value(List.of(1, 2, 3))),
						"m0.name='status' and m0.value_int IN (1, 2, 3)", " join i.metadata m0"),
				Arguments.of(
						new Filter.Expression(IN, new Filter.Key("score"), new Filter.Value(List.of(1.1f, 2.2f, 3.3f))),
						"m0.name='score' and m0.value_float IN (1.1, 2.2, 3.3)", " join i.metadata m0"),
				Arguments.of(
						new Filter.Expression(IN, new Filter.Key("score"), new Filter.Value(List.of(5.1d, 6.2d, 7.3d))),
						"m0.name='score' and m0.value_float IN (5.1, 6.2, 7.3)", " join i.metadata m0"));
	}

	@ParameterizedTest
	@MethodSource("notInFilters")
	void shouldMapNotInFilter(Filter.Expression expression, String expectedQuery, String expectedJoin) {
		assertQueryAndJoin(expression, expectedQuery, expectedJoin);
	}

	static List<Arguments> notInFilters() {
		return Arrays.asList(Arguments.of(
				new Filter.Expression(NIN, new Filter.Key("category"), new Filter.Value(List.of("X", "Y", "Z"))),
				"(m0.value NOT IN ('X', 'Y', 'Z') and m0.name='category' ) OR (m0.value IN ('X', 'Y', 'Z') and m0.name!='category') OR (i.metadata is null)",
				" join i.metadata m0"),
				Arguments.of(new Filter.Expression(NIN, new Filter.Key("age"), new Filter.Value(List.of(2, 5, 6))),
						"(m0.value_int NOT IN (2, 5, 6) and m0.name='age' ) OR (m0.value_int IN (2, 5, 6) and m0.name!='age') OR (i.metadata is null)",
						" join i.metadata m0"),
				Arguments.of(
						new Filter.Expression(NIN, new Filter.Key("score"), new Filter.Value(List.of(1d, 3d, 4.4d))),
						"(m0.value_float NOT IN (1.0, 3.0, 4.4) and m0.name='score' ) OR (m0.value_float IN (1.0, 3.0, 4.4) and m0.name!='score') OR (i.metadata is null)",
						" join i.metadata m0"));
	}

	@Test
	void mapAndExpressions() {
		Filter.Expression ageEq = new Filter.Expression(EQ, new Filter.Key("age"), new Filter.Value(25));
		Filter.Expression sizeNotEq = new Filter.Expression(GT, new Filter.Key("size"), new Filter.Value(170));
		Filter.Expression andExpression = new Filter.Expression(AND, ageEq, sizeNotEq);

		String filter = this.converter.convertExpression(andExpression);
		String join = this.converter.doJoin();

		assertThat(filter).isEqualTo("((m0.name='age' and m0.value_int=25) AND (m1.name='size' and m1.value_int>170))");
		assertThat(join).isEqualTo(" join i.metadata m0 join i.metadata m1");
	}

	@Test
	void mapOrExpressions() {
		Filter.Expression ageEq = new Filter.Expression(EQ, new Filter.Key("age"), new Filter.Value(25));
		Filter.Expression sizeNotEq = new Filter.Expression(GT, new Filter.Key("size"), new Filter.Value(170));
		Filter.Expression andExpression = new Filter.Expression(OR, ageEq, sizeNotEq);

		String filter = this.converter.convertExpression(andExpression);
		String join = this.converter.doJoin();

		assertThat(filter).isEqualTo("((m0.name='age' and m0.value_int=25) OR (m1.name='size' and m1.value_int>170))");
		assertThat(join).isEqualTo(" join i.metadata m0 join i.metadata m1");
	}

	@Test
	public void shouldTransformNotExpression() {
		String filter = this.converter.convertExpression(
				new Filter.Expression(NOT, new Filter.Expression(EQ, new Filter.Key("age"), new Filter.Value(25))));
		assertThat(filter).isEqualTo("m0.name='age' and m0.value_int!=25 OR (i.metadata is null)");
	}

	@Test
	public void testDate() {
		Date date = new Date(2000);
		String vectorExpr = this.converter
			.convertExpression(new Filter.Expression(EQ, new Filter.Key("activationDate"), new Filter.Value(date)));
		Assertions.assertThat(vectorExpr)
			.isEqualTo("m0.name='activationDate' and m0.value_date=" + date.toInstant().toEpochMilli());

		vectorExpr = this.converter.convertExpression(
				new Filter.Expression(EQ, new Filter.Key("activationDate"), new Filter.Value("1970-01-01T00:00:02Z")));
		Assertions.assertThat(vectorExpr)
			.isEqualTo("m1.name='activationDate' and m1.value_date=" + date.toInstant().toEpochMilli());
	}

	@Test
	public void testNullNotNull() {
		String vectorExpr = this.converter
			.convertExpression(new Filter.Expression(ISNULL, new Filter.Key("activationDate")));
		Assertions.assertThat(vectorExpr)
			.isEqualTo(
					"(m0.name='activationDate' and m0.value IS NULL and m0.value_int IS NULL and m0.value_date IS NULL and m0.value_float IS NULL and m0.value_bool IS NULL) OR (m0.name NOT IN('activationDate'))");

		vectorExpr = this.converter
			.convertExpression(new Filter.Expression(ISNOTNULL, new Filter.Key("activationDate")));
		Assertions.assertThat(vectorExpr)
			.isEqualTo(
					"m1.name='activationDate' and (m1.value IS NOT NULL or m1.value_int IS NOT NULL or m1.value_date IS NOT NULL or m1.value_float IS NOT NULL or m1.value_bool IS NOT NULL)");
	}

	private void assertQueryAndJoin(Filter.Expression expression, String expectedQuery, String expectedJoin) {
		String filter = this.converter.convertExpression(expression);
		String join = this.converter.doJoin();
		assertThat(filter).isEqualTo(expectedQuery);
		assertThat(join).isEqualTo(expectedJoin);
	}

}
