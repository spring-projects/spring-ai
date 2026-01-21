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
import java.util.Objects;

import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockagentruntime.model.FilterAttribute;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrievalFilter;

import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.Filter.ExpressionType;
import org.springframework.ai.vectorstore.filter.Filter.Key;
import org.springframework.ai.vectorstore.filter.Filter.Value;
import org.springframework.ai.vectorstore.filter.FilterHelper;

/**
 * Converts Spring AI {@link Filter.Expression} to Bedrock Knowledge Base
 * {@link RetrievalFilter}.
 *
 * @author Yuriy Bezsonov
 * @since 2.0.0
 */
public class BedrockKnowledgeBaseFilterExpressionConverter {

	/**
	 * Converts a Spring AI filter expression to a Bedrock RetrievalFilter.
	 * @param expression the Spring AI filter expression
	 * @return the Bedrock RetrievalFilter
	 */
	public RetrievalFilter convertExpression(final Expression expression) {
		return convert(expression);
	}

	private RetrievalFilter convert(final Expression expression) {
		ExpressionType type = expression.type();

		return switch (type) {
			case AND -> convertAnd(expression);
			case OR -> convertOr(expression);
			case NOT -> convertNot(expression);
			case EQ -> buildComparison(expression, ComparisonOp.EQ);
			case NE -> buildComparison(expression, ComparisonOp.NE);
			case GT -> buildComparison(expression, ComparisonOp.GT);
			case GTE -> buildComparison(expression, ComparisonOp.GTE);
			case LT -> buildComparison(expression, ComparisonOp.LT);
			case LTE -> buildComparison(expression, ComparisonOp.LTE);
			case IN -> convertIn(expression);
			case NIN -> convertNotIn(expression);
			default -> throw new UnsupportedOperationException("Filter type not supported: " + type);
		};
	}

	private RetrievalFilter convertAnd(final Expression expression) {
		Filter.Operand leftOp = Objects.requireNonNull(expression.left(), "left operand");
		Filter.Operand rightOp = Objects.requireNonNull(expression.right(), "right operand");
		RetrievalFilter left = convert(asExpression(leftOp));
		RetrievalFilter right = convert(asExpression(rightOp));
		return RetrievalFilter.builder().andAll(left, right).build();
	}

	private RetrievalFilter convertOr(final Expression expression) {
		Filter.Operand leftOp = Objects.requireNonNull(expression.left(), "left operand");
		Filter.Operand rightOp = Objects.requireNonNull(expression.right(), "right operand");
		RetrievalFilter left = convert(asExpression(leftOp));
		RetrievalFilter right = convert(asExpression(rightOp));
		return RetrievalFilter.builder().orAll(left, right).build();
	}

	private RetrievalFilter convertNot(final Expression expression) {
		Filter.Operand negated = FilterHelper.negate(expression);
		if (negated instanceof Expression negatedExpr) {
			return convert(negatedExpr);
		}
		throw new IllegalArgumentException(
				"NOT operator negation failed for expression type: " + expression.type() + ". Operand: " + negated);
	}

	private RetrievalFilter buildComparison(final Expression exp, final ComparisonOp op) {
		Filter.Operand leftOp = Objects.requireNonNull(exp.left(), "left operand");
		Filter.Operand rightOp = Objects.requireNonNull(exp.right(), "right operand");
		String key = ((Key) leftOp).key();
		Object value = extractValue(rightOp);
		FilterAttribute attr = createFilterAttribute(key, value);

		return switch (op) {
			case EQ -> RetrievalFilter.builder().equalsValue(attr).build();
			case NE -> RetrievalFilter.builder().notEquals(attr).build();
			case GT -> RetrievalFilter.builder().greaterThan(attr).build();
			case GTE -> RetrievalFilter.builder().greaterThanOrEquals(attr).build();
			case LT -> RetrievalFilter.builder().lessThan(attr).build();
			case LTE -> RetrievalFilter.builder().lessThanOrEquals(attr).build();
		};
	}

	private RetrievalFilter convertIn(final Expression expression) {
		Filter.Operand leftOp = Objects.requireNonNull(expression.left(), "left operand");
		Filter.Operand rightOp = Objects.requireNonNull(expression.right(), "right operand");
		String key = ((Key) leftOp).key();
		List<?> values = extractListValue(rightOp);
		List<Document> docs = values.stream().map(this::toDocument).toList();
		FilterAttribute attr = FilterAttribute.builder().key(key).value(Document.fromList(docs)).build();
		return RetrievalFilter.builder().in(attr).build();
	}

	private RetrievalFilter convertNotIn(final Expression expression) {
		Filter.Operand leftOp = Objects.requireNonNull(expression.left(), "left operand");
		Filter.Operand rightOp = Objects.requireNonNull(expression.right(), "right operand");
		String key = ((Key) leftOp).key();
		List<?> values = extractListValue(rightOp);
		List<Document> docs = values.stream().map(this::toDocument).toList();
		FilterAttribute attr = FilterAttribute.builder().key(key).value(Document.fromList(docs)).build();
		return RetrievalFilter.builder().notIn(attr).build();
	}

	private FilterAttribute createFilterAttribute(final String key, final Object value) {
		return FilterAttribute.builder().key(key).value(toDocument(value)).build();
	}

	private Expression asExpression(final Filter.Operand operand) {
		if (operand instanceof Expression expr) {
			return expr;
		}
		throw new IllegalArgumentException("Expected Expression but got: " + operand.getClass());
	}

	private Object extractValue(final Filter.Operand operand) {
		if (operand instanceof Value value) {
			return value.value();
		}
		throw new IllegalArgumentException("Expected Value but got: " + operand.getClass());
	}

	private List<?> extractListValue(final Filter.Operand operand) {
		Object value = extractValue(operand);
		if (value instanceof List) {
			return (List<?>) value;
		}
		throw new IllegalArgumentException("Expected List for IN/NIN but got: " + value.getClass());
	}

	private Document toDocument(final Object value) {
		if (value instanceof String s) {
			return Document.fromString(s);
		}
		if (value instanceof Number n) {
			return Document.fromNumber(n.toString());
		}
		if (value instanceof Boolean b) {
			return Document.fromBoolean(b);
		}
		return Document.fromString(value.toString());
	}

	private enum ComparisonOp {

		EQ, NE, GT, GTE, LT, LTE

	}

}
