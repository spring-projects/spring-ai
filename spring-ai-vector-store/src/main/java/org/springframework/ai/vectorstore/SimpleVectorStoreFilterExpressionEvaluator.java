/*
 * Copyright 2023-present the original author or authors.
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

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

/**
 * Internal helper used by {@link SimpleVectorStore} to evaluate a
 * {@link Filter.Expression} AST directly against a document metadata map, without
 * converting to an intermediate string representation (e.g. SpEL or SQL).
 *
 * <p>
 * Supports all {@link Filter.ExpressionType} operations:
 * <ul>
 * <li>Logical: {@code AND}, {@code OR}, {@code NOT}</li>
 * <li>Comparison: {@code EQ}, {@code NE}, {@code GT}, {@code GTE}, {@code LT},
 * {@code LTE}</li>
 * <li>Collection: {@code IN}, {@code NIN}</li>
 * <li>Null checks: {@code ISNULL}, {@code ISNOTNULL}</li>
 * </ul>
 *
 * <p>
 * <b>Type handling:</b> Numbers are promoted to {@code double} so that mixed
 * {@code Integer}/{@code Long}/{@code Double} metadata values compare correctly.
 * {@link Date} filter values are normalised to their ISO-8601 UTC string representation
 * (matching the format used to store dates in metadata).
 *
 * <p>
 * <b>Missing-key semantics:</b> A metadata key that is absent is treated as {@code null}.
 * Null ordering follows SQL {@code NULLS FIRST} semantics — {@code null} is considered
 * less than any non-null value. Consequently, ordered comparisons ({@code GT},
 * {@code GTE}, {@code LT}, {@code LTE}) against a missing key evaluate as if that key
 * holds the smallest possible value (e.g. {@code year > 2020} returns {@code false} when
 * {@code year} is absent).
 *
 * @author Christian Tzolov
 */
final class SimpleVectorStoreFilterExpressionEvaluator {

	// 'Z' is intentionally a literal suffix, not the offset pattern 'X'. Combined with
	// withZone(UTC) this always produces the fixed form "yyyy-MM-dd'T'HH:mm:ss'Z'",
	// matching the format used to store Date values in document metadata.
	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
		.withZone(ZoneOffset.UTC);

	/**
	 * Evaluates the given filter expression against the provided metadata map.
	 * @param expression the filter expression to evaluate; must not be {@code null}
	 * @param metadata the document metadata to match against; must not be {@code null}
	 * @return {@code true} if the metadata satisfies the expression
	 */
	public boolean evaluate(Filter.Expression expression, Map<String, Object> metadata) {
		return evaluateExpression(expression, metadata);
	}

	private boolean evaluateOperand(Filter.Operand operand, Map<String, Object> metadata) {
		if (operand instanceof Filter.Group group) {
			return evaluateOperand(group.content(), metadata);
		}
		if (operand instanceof Filter.Expression expression) {
			return evaluateExpression(expression, metadata);
		}
		// Filter.Key and Filter.Value are leaf operands consumed directly by
		// metadataValue() and filterValue() inside evaluateExpression(). They are never
		// passed here as top-level boolean operands, so this branch is unreachable under
		// normal usage.
		throw new IllegalArgumentException("Unsupported operand type: " + operand.getClass().getName());
	}

	private boolean evaluateExpression(Filter.Expression expression, Map<String, Object> metadata) {
		return switch (expression.type()) {
			case AND -> evaluateOperand(left(expression), metadata) && evaluateOperand(right(expression), metadata);
			case OR -> evaluateOperand(left(expression), metadata) || evaluateOperand(right(expression), metadata);
			// Unary operator: only the left operand is used. Ignore right operand
			case NOT -> !evaluateOperand(left(expression), metadata);
			case EQ -> compare(metadataValue(left(expression), metadata), filterValue(right(expression))) == 0;
			case NE -> compare(metadataValue(left(expression), metadata), filterValue(right(expression))) != 0;
			case GT -> compare(metadataValue(left(expression), metadata), filterValue(right(expression))) > 0;
			case GTE -> compare(metadataValue(left(expression), metadata), filterValue(right(expression))) >= 0;
			case LT -> compare(metadataValue(left(expression), metadata), filterValue(right(expression))) < 0;
			case LTE -> compare(metadataValue(left(expression), metadata), filterValue(right(expression))) <= 0;
			case IN -> {
				Object metaVal = metadataValue(left(expression), metadata);
				List<?> list = asList(filterValue(right(expression)), expression);
				yield list.stream().anyMatch(item -> compare(metaVal, item) == 0);
			}
			case NIN -> {
				Object metaVal = metadataValue(left(expression), metadata);
				List<?> list = asList(filterValue(right(expression)), expression);
				yield list.stream().noneMatch(item -> compare(metaVal, item) == 0);
			}
		};
	}

	private Filter.Operand left(Filter.Expression expression) {
		Filter.Operand left = expression.left();
		if (left == null) {
			throw new IllegalArgumentException(
					"Expression of type %s requires a left operand".formatted(expression.type()));
		}
		return left;
	}

	private Filter.Operand right(Filter.Expression expression) {
		Filter.Operand right = expression.right();
		if (right == null) {
			throw new IllegalArgumentException(
					"Expression of type %s requires a right operand".formatted(expression.type()));
		}
		return right;
	}

	/**
	 * Extracts the metadata value for the given {@link Filter.Key} operand. Outer quotes
	 * ({@code "..."} or {@code '...'}) are stripped from the key name to match the format
	 * used by {@link FilterExpressionBuilder} and the text parser.
	 */
	private Object metadataValue(Filter.Operand operand, Map<String, Object> metadata) {
		if (operand instanceof Filter.Key key) {
			String k = key.key();
			if (k.length() >= 2
					&& ((k.startsWith("\"") && k.endsWith("\"")) || (k.startsWith("'") && k.endsWith("'")))) {
				k = k.substring(1, k.length() - 1);
			}
			return metadata.get(k);
		}
		throw new IllegalArgumentException("Expected a Key operand but got: " + operand.getClass().getName());
	}

	/**
	 * Extracts the constant value from a {@link Filter.Value} operand. {@link Date}
	 * instances are formatted to their ISO-8601 UTC string so they can be compared
	 * directly with metadata strings stored in the same format.
	 */
	private Object filterValue(Filter.Operand operand) {
		if (operand instanceof Filter.Value filterValue) {
			Object value = filterValue.value();
			return (value instanceof Date date) ? DATE_FORMATTER.format(date.toInstant()) : value;
		}
		throw new IllegalArgumentException("Expected a Value operand but got: " + operand.getClass().getName());
	}

	/**
	 * Compares two values. Numbers are promoted to {@code double} to allow cross-type
	 * numeric comparison (e.g. {@code Integer} vs {@code Double}). All other
	 * {@link Comparable} types are compared directly.
	 *
	 * <p>
	 * Null ordering follows SQL {@code NULLS FIRST} semantics: {@code null} is considered
	 * less than any non-null value. As a result, a missing metadata key causes ordered
	 * comparisons ({@code GT}, {@code GTE}, {@code LT}, {@code LTE}) to behave as if the
	 * key holds the smallest possible value — e.g. {@code year > 2020} returns
	 * {@code false} when {@code year} is absent.
	 */
	@SuppressWarnings("unchecked")
	private int compare(Object metaVal, Object filterVal) {
		if (metaVal == null && filterVal == null) {
			return 0;
		}
		if (metaVal == null) {
			return -1;
		}
		if (filterVal == null) {
			return 1;
		}
		if (metaVal instanceof Number n1 && filterVal instanceof Number n2) {
			return Double.compare(n1.doubleValue(), n2.doubleValue());
		}
		if (Objects.equals(metaVal, filterVal)) {
			return 0;
		}
		if (metaVal instanceof Comparable comparable && filterVal instanceof Comparable) {
			try {
				return comparable.compareTo(filterVal);
			}
			catch (ClassCastException ex) {
				throw new IllegalArgumentException("Cannot compare values of incompatible types %s and %s"
					.formatted(metaVal.getClass().getName(), filterVal.getClass().getName()), ex);
			}
		}
		throw new IllegalArgumentException("Cannot compare values of types %s and %s"
			.formatted(metaVal.getClass().getName(), filterVal.getClass().getName()));
	}

	private List<?> asList(Object value, Filter.Expression expression) {
		if (value instanceof List<?> list) {
			return list;
		}
		throw new IllegalArgumentException(
				"Expected a List value for %s expression but got: %s".formatted(expression.type(), value));
	}

}
