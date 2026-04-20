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

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.ai.vectorstore.filter.Filter;

/**
 * Evaluates a Spring AI {@link Filter.Expression} tree against a metadata map
 * in Java.
 *
 * <p>
 * This approach avoids SQL type conversion issues (UUID, Double, Float
 * mismatches) and is proven in the langchain4j-community-arcadedb integration.
 *
 * @author Luca Garulli
 * @since 2.0.0
 */
final class ArcadeDBFilterExpressionConverter {

	private ArcadeDBFilterExpressionConverter() {
	}

	/**
	 * Evaluates whether the given metadata map matches the filter expression.
	 * @param expression the filter expression to evaluate
	 * @param metadata the metadata map to match against
	 * @return true if the metadata matches the expression
	 */
	static boolean matches(Filter.Expression expression, Map<String, Object> metadata) {
		if (expression == null) {
			return true;
		}
		return switch (expression.type()) {
			case AND -> {
				Filter.Expression left = (Filter.Expression) expression.left();
				Filter.Expression right = (Filter.Expression) expression.right();
				yield matches(left, metadata) && matches(right, metadata);
			}
			case OR -> {
				Filter.Expression left = (Filter.Expression) expression.left();
				Filter.Expression right = (Filter.Expression) expression.right();
				yield matches(left, metadata) || matches(right, metadata);
			}
			case NOT -> {
				Filter.Expression operand = (Filter.Expression) expression.left();
				yield !matches(operand, metadata);
			}
			case EQ -> {
				String key = ((Filter.Key) expression.left()).key();
				Object expected = ((Filter.Value) expression.right()).value();
				Object actual = metadata.get(key);
				yield actual != null && valueEquals(actual, expected);
			}
			case NE -> {
				String key = ((Filter.Key) expression.left()).key();
				Object expected = ((Filter.Value) expression.right()).value();
				Object actual = metadata.get(key);
				yield actual == null || !valueEquals(actual, expected);
			}
			case GT -> {
				String key = ((Filter.Key) expression.left()).key();
				Object expected = ((Filter.Value) expression.right()).value();
				Object actual = metadata.get(key);
				yield actual != null && compareValues(actual, expected) > 0;
			}
			case GTE -> {
				String key = ((Filter.Key) expression.left()).key();
				Object expected = ((Filter.Value) expression.right()).value();
				Object actual = metadata.get(key);
				yield actual != null && compareValues(actual, expected) >= 0;
			}
			case LT -> {
				String key = ((Filter.Key) expression.left()).key();
				Object expected = ((Filter.Value) expression.right()).value();
				Object actual = metadata.get(key);
				yield actual != null && compareValues(actual, expected) < 0;
			}
			case LTE -> {
				String key = ((Filter.Key) expression.left()).key();
				Object expected = ((Filter.Value) expression.right()).value();
				Object actual = metadata.get(key);
				yield actual != null && compareValues(actual, expected) <= 0;
			}
			case IN -> {
				String key = ((Filter.Key) expression.left()).key();
				Object actual = metadata.get(key);
				if (actual == null) {
					yield false;
				}
				Filter.Value filterValue = (Filter.Value) expression.right();
				if (filterValue.value() instanceof java.util.List<?> list) {
					yield list.stream().anyMatch(v -> valueEquals(actual, v));
				}
				yield valueEquals(actual, filterValue.value());
			}
			case NIN -> {
				String key = ((Filter.Key) expression.left()).key();
				Object actual = metadata.get(key);
				if (actual == null) {
					yield true;
				}
				Filter.Value filterValue = (Filter.Value) expression.right();
				if (filterValue.value() instanceof java.util.List<?> list) {
					yield list.stream().noneMatch(v -> valueEquals(actual, v));
				}
				yield !valueEquals(actual, filterValue.value());
			}
			default -> throw new UnsupportedOperationException(
					"Unsupported filter expression type: " + expression.type());
		};
	}

	private static boolean valueEquals(Object a, Object b) {
		if (a instanceof Number && b instanceof Number) {
			return ((Number) a).doubleValue() == ((Number) b).doubleValue();
		}
		return Objects.equals(objectToString(a), objectToString(b));
	}

	private static int compareValues(Object a, Object b) {
		if (a instanceof Number && b instanceof Number) {
			return Double.compare(((Number) a).doubleValue(), ((Number) b).doubleValue());
		}
		return objectToString(a).compareTo(objectToString(b));
	}

	private static String objectToString(Object o) {
		if (o == null) {
			return "";
		}
		return o.toString();
	}

}
