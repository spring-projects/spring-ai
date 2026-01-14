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

package org.springframework.ai.vectorstore.bedrockknowledgebase;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrievalFilter;

import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.Filter.ExpressionType;
import org.springframework.ai.vectorstore.filter.Filter.Key;
import org.springframework.ai.vectorstore.filter.Filter.Value;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link BedrockKnowledgeBaseFilterExpressionConverter}.
 *
 * @author Yuriy Bezsonov
 */
class BedrockKnowledgeBaseFilterExpressionConverterTest {

	private BedrockKnowledgeBaseFilterExpressionConverter converter;

	@BeforeEach
	void setUp() {
		this.converter = new BedrockKnowledgeBaseFilterExpressionConverter();
	}

	@Nested
	@DisplayName("Comparison Operator Tests")
	class ComparisonOperatorTests {

		@Test
		void shouldConvertEqualsWithString() {
			Expression expr = new Expression(ExpressionType.EQ, new Key("department"), new Value("HR"));

			RetrievalFilter result = BedrockKnowledgeBaseFilterExpressionConverterTest.this.converter
				.convertExpression(expr);

			assertThat(result).isNotNull();
			assertThat(result.equalsValue()).isNotNull();
			assertThat(result.equalsValue().key()).isEqualTo("department");
			assertThat(result.equalsValue().value().asString()).isEqualTo("HR");
		}

		@Test
		void shouldConvertEqualsWithNumber() {
			Expression expr = new Expression(ExpressionType.EQ, new Key("year"), new Value(2024));

			RetrievalFilter result = BedrockKnowledgeBaseFilterExpressionConverterTest.this.converter
				.convertExpression(expr);

			assertThat(result.equalsValue()).isNotNull();
			assertThat(result.equalsValue().key()).isEqualTo("year");
			assertThat(result.equalsValue().value().asNumber().intValue()).isEqualTo(2024);
		}

		@Test
		void shouldConvertEqualsWithBoolean() {
			Expression expr = new Expression(ExpressionType.EQ, new Key("active"), new Value(true));

			RetrievalFilter result = BedrockKnowledgeBaseFilterExpressionConverterTest.this.converter
				.convertExpression(expr);

			assertThat(result.equalsValue()).isNotNull();
			assertThat(result.equalsValue().value().asBoolean()).isTrue();
		}

		@Test
		void shouldConvertNotEquals() {
			Expression expr = new Expression(ExpressionType.NE, new Key("status"), new Value("archived"));

			RetrievalFilter result = BedrockKnowledgeBaseFilterExpressionConverterTest.this.converter
				.convertExpression(expr);

			assertThat(result.notEquals()).isNotNull();
			assertThat(result.notEquals().key()).isEqualTo("status");
			assertThat(result.notEquals().value().asString()).isEqualTo("archived");
		}

		@Test
		void shouldConvertGreaterThan() {
			Expression expr = new Expression(ExpressionType.GT, new Key("price"), new Value(100));

			RetrievalFilter result = BedrockKnowledgeBaseFilterExpressionConverterTest.this.converter
				.convertExpression(expr);

			assertThat(result.greaterThan()).isNotNull();
			assertThat(result.greaterThan().key()).isEqualTo("price");
			assertThat(result.greaterThan().value().asNumber().intValue()).isEqualTo(100);
		}

		@Test
		void shouldConvertGreaterThanOrEquals() {
			Expression expr = new Expression(ExpressionType.GTE, new Key("rating"), new Value(4.5));

			RetrievalFilter result = BedrockKnowledgeBaseFilterExpressionConverterTest.this.converter
				.convertExpression(expr);

			assertThat(result.greaterThanOrEquals()).isNotNull();
			assertThat(result.greaterThanOrEquals().key()).isEqualTo("rating");
			assertThat(result.greaterThanOrEquals().value().asNumber().doubleValue()).isEqualTo(4.5);
		}

		@Test
		void shouldConvertLessThan() {
			Expression expr = new Expression(ExpressionType.LT, new Key("age"), new Value(30));

			RetrievalFilter result = BedrockKnowledgeBaseFilterExpressionConverterTest.this.converter
				.convertExpression(expr);

			assertThat(result.lessThan()).isNotNull();
			assertThat(result.lessThan().key()).isEqualTo("age");
			assertThat(result.lessThan().value().asNumber().intValue()).isEqualTo(30);
		}

		@Test
		void shouldConvertLessThanOrEquals() {
			Expression expr = new Expression(ExpressionType.LTE, new Key("count"), new Value(10));

			RetrievalFilter result = BedrockKnowledgeBaseFilterExpressionConverterTest.this.converter
				.convertExpression(expr);

			assertThat(result.lessThanOrEquals()).isNotNull();
			assertThat(result.lessThanOrEquals().key()).isEqualTo("count");
			assertThat(result.lessThanOrEquals().value().asNumber().intValue()).isEqualTo(10);
		}

	}

	@Nested
	@DisplayName("List Operator Tests")
	class ListOperatorTests {

		@Test
		void shouldConvertInWithStringList() {
			Expression expr = new Expression(ExpressionType.IN, new Key("category"),
					new Value(List.of("travel", "expense", "policy")));

			RetrievalFilter result = BedrockKnowledgeBaseFilterExpressionConverterTest.this.converter
				.convertExpression(expr);

			assertThat(result.in()).isNotNull();
			assertThat(result.in().key()).isEqualTo("category");
			assertThat(result.in().value().asList()).hasSize(3);
		}

		@Test
		void shouldConvertInWithNumberList() {
			Expression expr = new Expression(ExpressionType.IN, new Key("year"), new Value(List.of(2022, 2023, 2024)));

			RetrievalFilter result = BedrockKnowledgeBaseFilterExpressionConverterTest.this.converter
				.convertExpression(expr);

			assertThat(result.in()).isNotNull();
			assertThat(result.in().key()).isEqualTo("year");
			assertThat(result.in().value().asList()).hasSize(3);
		}

		@Test
		void shouldConvertNotIn() {
			Expression expr = new Expression(ExpressionType.NIN, new Key("status"),
					new Value(List.of("deleted", "archived")));

			RetrievalFilter result = BedrockKnowledgeBaseFilterExpressionConverterTest.this.converter
				.convertExpression(expr);

			assertThat(result.notIn()).isNotNull();
			assertThat(result.notIn().key()).isEqualTo("status");
			assertThat(result.notIn().value().asList()).hasSize(2);
		}

	}

	@Nested
	@DisplayName("Logical Operator Tests")
	class LogicalOperatorTests {

		@Test
		void shouldConvertAnd() {
			Expression left = new Expression(ExpressionType.EQ, new Key("department"), new Value("HR"));
			Expression right = new Expression(ExpressionType.GT, new Key("year"), new Value(2020));
			Expression andExpr = new Expression(ExpressionType.AND, left, right);

			RetrievalFilter result = BedrockKnowledgeBaseFilterExpressionConverterTest.this.converter
				.convertExpression(andExpr);

			assertThat(result.andAll()).isNotNull();
			assertThat(result.andAll()).hasSize(2);
		}

		@Test
		void shouldConvertOr() {
			Expression left = new Expression(ExpressionType.EQ, new Key("type"), new Value("policy"));
			Expression right = new Expression(ExpressionType.EQ, new Key("type"), new Value("guideline"));
			Expression orExpr = new Expression(ExpressionType.OR, left, right);

			RetrievalFilter result = BedrockKnowledgeBaseFilterExpressionConverterTest.this.converter
				.convertExpression(orExpr);

			assertThat(result.orAll()).isNotNull();
			assertThat(result.orAll()).hasSize(2);
		}

		@Test
		void shouldConvertNestedAndOr() {
			// (department == 'HR' AND year > 2020) OR category == 'policy'
			Expression deptExpr = new Expression(ExpressionType.EQ, new Key("department"), new Value("HR"));
			Expression yearExpr = new Expression(ExpressionType.GT, new Key("year"), new Value(2020));
			Expression andExpr = new Expression(ExpressionType.AND, deptExpr, yearExpr);
			Expression catExpr = new Expression(ExpressionType.EQ, new Key("category"), new Value("policy"));
			Expression orExpr = new Expression(ExpressionType.OR, andExpr, catExpr);

			RetrievalFilter result = BedrockKnowledgeBaseFilterExpressionConverterTest.this.converter
				.convertExpression(orExpr);

			assertThat(result.orAll()).isNotNull();
			assertThat(result.orAll()).hasSize(2);
			// First element should be the AND filter
			assertThat(result.orAll().get(0).andAll()).hasSize(2);
		}

		@Test
		void shouldConvertTripleAnd() {
			// a == 1 AND b == 2 AND c == 3
			Expression a = new Expression(ExpressionType.EQ, new Key("a"), new Value(1));
			Expression b = new Expression(ExpressionType.EQ, new Key("b"), new Value(2));
			Expression c = new Expression(ExpressionType.EQ, new Key("c"), new Value(3));
			Expression ab = new Expression(ExpressionType.AND, a, b);
			Expression abc = new Expression(ExpressionType.AND, ab, c);

			RetrievalFilter result = BedrockKnowledgeBaseFilterExpressionConverterTest.this.converter
				.convertExpression(abc);

			assertThat(result.andAll()).isNotNull();
			assertThat(result.andAll()).hasSize(2);
		}

	}

	@Nested
	@DisplayName("Value Type Tests")
	class ValueTypeTests {

		@Test
		void shouldHandleDoubleValue() {
			Expression expr = new Expression(ExpressionType.EQ, new Key("score"), new Value(3.14159));

			RetrievalFilter result = BedrockKnowledgeBaseFilterExpressionConverterTest.this.converter
				.convertExpression(expr);

			assertThat(result.equalsValue().value().asNumber().doubleValue()).isEqualTo(3.14159);
		}

		@Test
		void shouldHandleLongValue() {
			Expression expr = new Expression(ExpressionType.EQ, new Key("timestamp"), new Value(1704067200000L));

			RetrievalFilter result = BedrockKnowledgeBaseFilterExpressionConverterTest.this.converter
				.convertExpression(expr);

			assertThat(result.equalsValue().value().asNumber().longValue()).isEqualTo(1704067200000L);
		}

	}

}
