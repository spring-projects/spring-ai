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

package org.springframework.ai.vectorstore.neo4j;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.neo4j.cypherdsl.support.schema_name.SchemaNames;

import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.util.Assert;

/**
 * Translates the subset of metadata predicates supported by Neo4j 2026.01 SEARCH.
 */
final class Neo4jSearchFilter {

	private Neo4jSearchFilter() {
	}

	static Converted convert(Filter.Expression expression, List<String> fields) {
		var predicate = new StringBuilder();
		var parameters = new HashMap<String, Object>();
		var bounds = new HashMap<String, EnumSet<Bound>>();
		append(expression, fields, predicate, parameters, bounds);
		return new Converted(predicate.toString(), Map.copyOf(parameters));
	}

	private static void append(Filter.Operand operand, List<String> fields, StringBuilder predicate,
			Map<String, Object> parameters, Map<String, EnumSet<Bound>> bounds) {
		if (operand instanceof Filter.Group group) {
			append(group.content(), fields, predicate, parameters, bounds);
			return;
		}
		Assert.isTrue(operand instanceof Filter.Expression, "SEARCH requires a metadata comparison expression");
		var expression = (Filter.Expression) operand;
		if (expression.type() == Filter.ExpressionType.AND) {
			Assert.notNull(expression.right(), "SEARCH AND requires two expressions");
			append(expression.left(), fields, predicate, parameters, bounds);
			predicate.append(" AND ");
			append(expression.right(), fields, predicate, parameters, bounds);
			return;
		}
		String operator = switch (expression.type()) {
			case EQ -> " = ";
			case GT -> " > ";
			case GTE -> " >= ";
			case LT -> " < ";
			case LTE -> " <= ";
			default -> throw new IllegalArgumentException("The SEARCH strategy does not support " + expression.type()
					+ "; supported operators are EQ, GT, GTE, LT, LTE and AND");
		};
		Assert.isTrue(expression.left() instanceof Filter.Key && expression.right() instanceof Filter.Value,
				"SEARCH comparisons require a metadata key on the left and a scalar value on the right");
		String key = ((Filter.Key) expression.left()).key();
		Assert.isTrue(fields.contains(key), "SEARCH metadata key '" + key + "' is not in filterableMetadataFields");
		Object value = ((Filter.Value) expression.right()).value();
		Assert.isTrue(
				value instanceof String || value instanceof Boolean || value instanceof Byte || value instanceof Short
						|| value instanceof Integer || value instanceof Long || value instanceof Float
						|| value instanceof Double,
				"SEARCH filter values must be strings, booleans or finite primitive numbers");
		Assert.isTrue(!(value instanceof Number number) || Double.isFinite(number.doubleValue()),
				"SEARCH filter numbers must be finite");
		Assert.isTrue(!(value instanceof Boolean) || expression.type() == Filter.ExpressionType.EQ,
				"SEARCH supports only equality comparisons for boolean values");
		Bound bound = switch (expression.type()) {
			case EQ -> Bound.EQUALITY;
			case GT, GTE -> Bound.LOWER;
			default -> Bound.UPPER;
		};
		var existing = bounds.computeIfAbsent(key, ignored -> EnumSet.noneOf(Bound.class));
		Assert.isTrue(
				!existing.contains(bound) && !existing.contains(Bound.EQUALITY)
						&& (bound != Bound.EQUALITY || existing.isEmpty()),
				"SEARCH allows one equality or at most one lower and one upper bound per metadata key: " + key);
		existing.add(bound);
		String parameter = "filter" + parameters.size();
		predicate.append("node.").append(SchemaNames.sanitize("metadata." + key, true).orElseThrow());
		predicate.append(operator).append('$').append(parameter);
		parameters.put(parameter, value);
	}

	record Converted(String predicate, Map<String, Object> parameters) {
	}

	private enum Bound {

		EQUALITY, LOWER, UPPER

	}

}
