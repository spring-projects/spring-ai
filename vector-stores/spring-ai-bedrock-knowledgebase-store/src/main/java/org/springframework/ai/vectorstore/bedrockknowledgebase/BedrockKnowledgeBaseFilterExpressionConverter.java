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

import org.jspecify.annotations.Nullable;

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
 * <p>
 * This converter translates Spring AI's portable filter expressions into Bedrock's native
 * RetrievalFilter format for metadata filtering during similarity searches.
 * </p>
 *
 * <p>
 * Supported operators:
 * </p>
 * <ul>
 * <li>Comparison: EQ, NE, GT, GTE, LT, LTE</li>
 * <li>List: IN, NIN</li>
 * <li>Logical: AND, OR, NOT</li>
 * </ul>
 *
 * <p>
 * Usage example:
 * </p>
 * <pre>{@code
 * // Filter expression: department == 'HR' && year >= 2024
 * List<Document> results = vectorStore.similaritySearch(
 *     SearchRequest.builder()
 *         .query("policy")
 *         .filterExpression("department == 'HR' && year >= 2024")
 *         .build()
 * );
 * }</pre>
 *
 * <p>
 * Note: Bedrock KB also supports startsWith, listContains, and stringContains operators,
 * but these are not exposed through Spring AI's filter expression syntax.
 * </p>
 *
 * @author Yuriy Bezsonov
 * @since 2.0.0
 */
public class BedrockKnowledgeBaseFilterExpressionConverter {

	/**
	 * Converts a Spring AI filter expression to a Bedrock RetrievalFilter.
	 * @param expression the Spring AI filter expression
	 * @return the Bedrock RetrievalFilter, or null if expression is null
	 */
	@Nullable public RetrievalFilter convertExpression(@Nullable final Expression expression) {
		if (expression == null) {
			return null;
		}
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
		RetrievalFilter left = convert(asExpression(Objects.requireNonNull(expression.left(), "left operand")));
		RetrievalFilter right = convert(asExpression(Objects.requireNonNull(expression.right(), "right operand")));
		return RetrievalFilter.builder().andAll(left, right).build();
	}

	private RetrievalFilter convertOr(final Expression expression) {
		RetrievalFilter left = convert(asExpression(Objects.requireNonNull(expression.left(), "left operand")));
		RetrievalFilter right = convert(asExpression(Objects.requireNonNull(expression.right(), "right operand")));
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
		String key = ((Key) Objects.requireNonNull(exp.left(), "left operand")).key();
		Object value = extractValue(Objects.requireNonNull(exp.right(), "right operand"));
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
		String key = ((Key) Objects.requireNonNull(expression.left(), "left operand")).key();
		List<?> values = extractListValue(Objects.requireNonNull(expression.right(), "right operand"));
		List<Document> docs = values.stream().map(this::toDocument).toList();
		FilterAttribute attr = FilterAttribute.builder().key(key).value(Document.fromList(docs)).build();
		return RetrievalFilter.builder().in(attr).build();
	}

	private RetrievalFilter convertNotIn(final Expression expression) {
		String key = ((Key) Objects.requireNonNull(expression.left(), "left operand")).key();
		List<?> values = extractListValue(Objects.requireNonNull(expression.right(), "right operand"));
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
		if (value == null) {
			return Document.fromNull();
		}
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

	/**
	 * Comparison types for filter operations.
	 */
	private enum ComparisonOp {

		/** Equals comparison. */
		EQ,
		/** Not equals comparison. */
		NE,
		/** Greater than comparison. */
		GT,
		/** Greater than or equals comparison. */
		GTE,
		/** Less than comparison. */
		LT,
		/** Less than or equals comparison. */
		LTE

	}

}
