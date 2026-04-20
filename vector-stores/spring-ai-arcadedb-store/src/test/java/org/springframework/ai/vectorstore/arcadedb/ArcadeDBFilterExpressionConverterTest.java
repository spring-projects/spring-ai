/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.vectorstore.arcadedb;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.Filter.ExpressionType;
import org.springframework.ai.vectorstore.filter.Filter.Key;
import org.springframework.ai.vectorstore.filter.Filter.Value;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ArcadeDBFilterExpressionConverter}.
 *
 * @author Luca Garulli
 */
class ArcadeDBFilterExpressionConverterTest {

	@Test
	void testEqualMatch() {
		Expression expr = new Expression(ExpressionType.EQ, new Key("color"),
				new Value("red"));
		assertThat(ArcadeDBFilterExpressionConverter.matches(expr,
				Map.of("color", "red"))).isTrue();
		assertThat(ArcadeDBFilterExpressionConverter.matches(expr,
				Map.of("color", "blue"))).isFalse();
	}

	@Test
	void testNotEqualMatch() {
		Expression expr = new Expression(ExpressionType.NE, new Key("color"),
				new Value("red"));
		assertThat(ArcadeDBFilterExpressionConverter.matches(expr,
				Map.of("color", "blue"))).isTrue();
		assertThat(ArcadeDBFilterExpressionConverter.matches(expr,
				Map.of("color", "red"))).isFalse();
	}

	@Test
	void testGreaterThan() {
		Expression expr = new Expression(ExpressionType.GT, new Key("score"),
				new Value(5));
		assertThat(ArcadeDBFilterExpressionConverter.matches(expr,
				Map.of("score", 10))).isTrue();
		assertThat(ArcadeDBFilterExpressionConverter.matches(expr,
				Map.of("score", 5))).isFalse();
		assertThat(ArcadeDBFilterExpressionConverter.matches(expr,
				Map.of("score", 3))).isFalse();
	}

	@Test
	void testGreaterThanOrEqual() {
		Expression expr = new Expression(ExpressionType.GTE, new Key("score"),
				new Value(5));
		assertThat(ArcadeDBFilterExpressionConverter.matches(expr,
				Map.of("score", 5))).isTrue();
		assertThat(ArcadeDBFilterExpressionConverter.matches(expr,
				Map.of("score", 4))).isFalse();
	}

	@Test
	void testLessThan() {
		Expression expr = new Expression(ExpressionType.LT, new Key("score"),
				new Value(5));
		assertThat(ArcadeDBFilterExpressionConverter.matches(expr,
				Map.of("score", 3))).isTrue();
		assertThat(ArcadeDBFilterExpressionConverter.matches(expr,
				Map.of("score", 5))).isFalse();
	}

	@Test
	void testLessThanOrEqual() {
		Expression expr = new Expression(ExpressionType.LTE, new Key("score"),
				new Value(5));
		assertThat(ArcadeDBFilterExpressionConverter.matches(expr,
				Map.of("score", 5))).isTrue();
		assertThat(ArcadeDBFilterExpressionConverter.matches(expr,
				Map.of("score", 6))).isFalse();
	}

	@Test
	void testInList() {
		Expression expr = new Expression(ExpressionType.IN, new Key("color"),
				new Value(List.of("red", "blue")));
		assertThat(ArcadeDBFilterExpressionConverter.matches(expr,
				Map.of("color", "red"))).isTrue();
		assertThat(ArcadeDBFilterExpressionConverter.matches(expr,
				Map.of("color", "green"))).isFalse();
	}

	@Test
	void testNotInList() {
		Expression expr = new Expression(ExpressionType.NIN, new Key("color"),
				new Value(List.of("red", "blue")));
		assertThat(ArcadeDBFilterExpressionConverter.matches(expr,
				Map.of("color", "green"))).isTrue();
		assertThat(ArcadeDBFilterExpressionConverter.matches(expr,
				Map.of("color", "red"))).isFalse();
	}

	@Test
	void testAndExpression() {
		Expression left = new Expression(ExpressionType.EQ, new Key("color"),
				new Value("red"));
		Expression right = new Expression(ExpressionType.GT, new Key("score"),
				new Value(5));
		Expression and = new Expression(ExpressionType.AND, left, right);

		assertThat(ArcadeDBFilterExpressionConverter.matches(and,
				Map.of("color", "red", "score", 10))).isTrue();
		assertThat(ArcadeDBFilterExpressionConverter.matches(and,
				Map.of("color", "red", "score", 3))).isFalse();
		assertThat(ArcadeDBFilterExpressionConverter.matches(and,
				Map.of("color", "blue", "score", 10))).isFalse();
	}

	@Test
	void testOrExpression() {
		Expression left = new Expression(ExpressionType.EQ, new Key("color"),
				new Value("red"));
		Expression right = new Expression(ExpressionType.EQ, new Key("color"),
				new Value("blue"));
		Expression or = new Expression(ExpressionType.OR, left, right);

		assertThat(ArcadeDBFilterExpressionConverter.matches(or,
				Map.of("color", "red"))).isTrue();
		assertThat(ArcadeDBFilterExpressionConverter.matches(or,
				Map.of("color", "blue"))).isTrue();
		assertThat(ArcadeDBFilterExpressionConverter.matches(or,
				Map.of("color", "green"))).isFalse();
	}

	@Test
	void testNotExpression() {
		Expression inner = new Expression(ExpressionType.EQ, new Key("color"),
				new Value("red"));
		Expression not = new Expression(ExpressionType.NOT, inner);

		assertThat(ArcadeDBFilterExpressionConverter.matches(not,
				Map.of("color", "blue"))).isTrue();
		assertThat(ArcadeDBFilterExpressionConverter.matches(not,
				Map.of("color", "red"))).isFalse();
	}

	@Test
	void testNullExpression() {
		assertThat(ArcadeDBFilterExpressionConverter.matches(null,
				Map.of("color", "red"))).isTrue();
	}

	@Test
	void testMissingKeyReturnsCorrectResult() {
		Expression expr = new Expression(ExpressionType.EQ, new Key("missing"),
				new Value("val"));
		assertThat(ArcadeDBFilterExpressionConverter.matches(expr,
				Map.of("color", "red"))).isFalse();

		Expression ne = new Expression(ExpressionType.NE, new Key("missing"),
				new Value("val"));
		assertThat(ArcadeDBFilterExpressionConverter.matches(ne,
				Map.of("color", "red"))).isTrue();
	}

	@Test
	void testNumericTypeCoercion() {
		Expression expr = new Expression(ExpressionType.EQ, new Key("score"),
				new Value(5.0));
		assertThat(ArcadeDBFilterExpressionConverter.matches(expr,
				Map.of("score", 5))).isTrue();
	}

}
