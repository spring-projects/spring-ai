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

package org.springframework.ai.vectorstore.s3;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.vectorstore.filter.Filter;

/**
 * Package-private helper used by {@link S3VectorStore} to evaluate a
 * {@link Filter.Expression} against metadata returned by the S3 Vectors ListVectors API.
 *
 * @author Jewoo Shin
 */
final class S3VectorStoreFilterExpressionEvaluator {

	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
		.withZone(ZoneOffset.UTC);

	boolean evaluate(Filter.Expression expression, Map<String, Object> metadata) {
		return evaluateExpression(expression, metadata);
	}

	private boolean evaluateOperand(Filter.Operand operand, Map<String, Object> metadata) {
		if (operand instanceof Filter.Group group) {
			return evaluateOperand(group.content(), metadata);
		}
		if (operand instanceof Filter.Expression expression) {
			return evaluateExpression(expression, metadata);
		}
		throw new IllegalArgumentException("Unsupported operand type: " + operand.getClass().getName());
	}

	private boolean evaluateExpression(Filter.Expression expression, Map<String, Object> metadata) {
		return switch (expression.type()) {
			case AND -> evaluateOperand(left(expression), metadata) && evaluateOperand(right(expression), metadata);
			case OR -> evaluateOperand(left(expression), metadata) || evaluateOperand(right(expression), metadata);
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
			case ISNULL -> metadataValue(left(expression), metadata) == null;
			case ISNOTNULL -> metadataValue(left(expression), metadata) != null;
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

	private @Nullable Object metadataValue(Filter.Operand operand, Map<String, Object> metadata) {
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

	private Object filterValue(Filter.Operand operand) {
		if (operand instanceof Filter.Value filterValue) {
			Object value = filterValue.value();
			return (value instanceof Date date) ? DATE_FORMATTER.format(date.toInstant()) : value;
		}
		throw new IllegalArgumentException("Expected a Value operand but got: " + operand.getClass().getName());
	}

	@SuppressWarnings("unchecked")
	private int compare(@Nullable Object metaVal, @Nullable Object filterVal) {
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
