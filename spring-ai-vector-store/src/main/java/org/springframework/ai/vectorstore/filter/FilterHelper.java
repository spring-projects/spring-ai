/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.vectorstore.filter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.Filter.ExpressionType;
import org.springframework.ai.vectorstore.filter.Filter.Operand;
import org.springframework.util.Assert;

/**
 * Helper class providing various boolean transformation.
 *
 * @author Christian Tzolov
 */
public final class FilterHelper {

	private static final Map<ExpressionType, ExpressionType> TYPE_NEGATION_MAP = Map.of(ExpressionType.AND,
			ExpressionType.OR, ExpressionType.OR, ExpressionType.AND, ExpressionType.EQ, ExpressionType.NE,
			ExpressionType.NE, ExpressionType.EQ, ExpressionType.GT, ExpressionType.LTE, ExpressionType.GTE,
			ExpressionType.LT, ExpressionType.LT, ExpressionType.GTE, ExpressionType.LTE, ExpressionType.GT,
			ExpressionType.IN, ExpressionType.NIN, ExpressionType.NIN, ExpressionType.IN);

	private FilterHelper() {
	}

	/**
	 * Transforms the input expression into a semantically equivalent one with negation
	 * operators propagated thought the expression tree by following the negation rules:
	 *
	 * <pre>
	 * 	NOT(NOT(a)) = a
	 *
	 * 	NOT(a AND b) = NOT(a) OR NOT(b)
	 * 	NOT(a OR b) = NOT(a) AND NOT(b)
	 *
	 * 	NOT(a EQ b) = a NE b
	 * 	NOT(a NE b) = a EQ b
	 *
	 * 	NOT(a GT b) = a LTE b
	 * 	NOT(a GTE b) = a LT b
	 *
	 * 	NOT(a LT b) = a GTE b
	 * 	NOT(a LTE b) = a GT b
	 *
	 * 	NOT(a IN [...]) = a NIN [...]
	 * 	NOT(a NIN [...]) = a IN [...]
	 * </pre>
	 * @param operand Filter expression to negate.
	 * @return Returns a negation of the input expression.
	 */
	@SuppressWarnings("NullAway") // An AND or OR operand has a non-null right operand
	public static Filter.Operand negate(Filter.Operand operand) {

		if (operand instanceof Filter.Group group) {
			Operand inEx = negate(group.content());
			if (inEx instanceof Filter.Group inEx2) {
				inEx = inEx2.content();
			}
			return new Filter.Group((Expression) inEx);
		}
		else if (operand instanceof Filter.Expression exp) {
			switch (exp.type()) {
				case NOT: // NOT(NOT(a)) = a
					return negate(exp.left());
				case AND: // NOT(a AND b) = NOT(a) OR NOT(b)
				case OR: // NOT(a OR b) = NOT(a) AND NOT(b)
					return new Filter.Expression(TYPE_NEGATION_MAP.get(exp.type()), negate(exp.left()),
							negate(exp.right()));
				case EQ: // NOT(e EQ b) = e NE b
				case NE: // NOT(e NE b) = e EQ b
				case GT: // NOT(e GT b) = e LTE b
				case GTE: // NOT(e GTE b) = e LT b
				case LT: // NOT(e LT b) = e GTE b
				case LTE: // NOT(e LTE b) = e GT b
					return new Filter.Expression(TYPE_NEGATION_MAP.get(exp.type()), exp.left(), exp.right());
				case IN: // NOT(e IN [...]) = e NIN [...]
				case NIN: // NOT(e NIN [...]) = e IN [...]
					return new Filter.Expression(TYPE_NEGATION_MAP.get(exp.type()), exp.left(), exp.right());
				default:
					throw new IllegalArgumentException("Unknown expression type: " + exp.type());
			}
		}
		else {
			throw new IllegalArgumentException("Can not negate operand of type: " + operand.getClass());
		}
	}

	/**
	 * Expands the IN into a semantically equivalent boolean expressions of ORs of EQs.
	 * Useful for providers that don't provide native IN support.
	 *
	 * For example the <pre>
	 * foo IN ["bar1", "bar2", "bar3"]
	 * </pre>
	 *
	 * expression is equivalent to
	 *
	 * <pre>
	 * {@code foo == "bar1" || foo == "bar2" || foo == "bar3" (e.g. OR(foo EQ "bar1" OR(foo EQ "bar2" OR(foo EQ "bar3")))}
	 * </pre>
	 * @param exp input IN expression.
	 * @param context Output native expression.
	 * @param filterExpressionConverter {@link FilterExpressionConverter} used to compose
	 * the OR and EQ expanded expressions.
	 */
	public static void expandIn(Expression exp, StringBuilder context,
			FilterExpressionConverter filterExpressionConverter) {
		Assert.isTrue(exp.type() == ExpressionType.IN, "Expected IN expressions but was: " + exp.type());
		expandInNinExpressions(ExpressionType.OR, ExpressionType.EQ, exp, context, filterExpressionConverter);
	}

	/**
	 *
	 * Expands the NIN (e.g. NOT IN) into a semantically equivalent boolean expressions of
	 * ANDs of NEs. Useful for providers that don't provide native NIN support.<br/>
	 *
	 * For example the
	 *
	 * <pre>
	 * foo NIN ["bar1", "bar2", "bar3"] (or foo NOT IN ["bar1", "bar2", "bar3"])
	 * </pre>
	 *
	 * express is equivalent to
	 *
	 * <pre>
	 * {@code foo != "bar1" && foo != "bar2" && foo != "bar3" (e.g. AND(foo NE "bar1" AND( foo NE "bar2" OR(foo NE "bar3"))) )}
	 * </pre>
	 * @param exp input NIN expression.
	 * @param context Output native expression.
	 * @param filterExpressionConverter {@link FilterExpressionConverter} used to compose
	 * the AND and NE expanded expressions.
	 */
	public static void expandNin(Expression exp, StringBuilder context,
			FilterExpressionConverter filterExpressionConverter) {
		Assert.isTrue(exp.type() == ExpressionType.NIN, "Expected NIN expressions but was: " + exp.type());
		expandInNinExpressions(ExpressionType.AND, ExpressionType.NE, exp, context, filterExpressionConverter);
	}

	private static void expandInNinExpressions(Filter.ExpressionType outerExpressionType,
			Filter.ExpressionType innerExpressionType, Expression exp, StringBuilder context,
			FilterExpressionConverter expressionConverter) {
		if (exp.right() instanceof Filter.Value value) {
			if (value.value() instanceof List list) {
				// 1. foo IN ["bar1", "bar2", "bar3"] is equivalent to foo == "bar1" ||
				// foo == "bar2" || foo == "bar3"
				// or equivalent to OR(foo == "bar1" OR( foo == "bar2" OR(foo == "bar3")))
				// 2. foo IN ["bar1", "bar2", "bar3"] is equivalent to foo != "bar1" &&
				// foo != "bar2" && foo != "bar3"
				// or equivalent to AND(foo != "bar1" AND( foo != "bar2" OR(foo !=
				// "bar3")))
				List<Filter.Expression> eqExprs = new ArrayList<>();
				for (Object o : list) {
					eqExprs.add(new Filter.Expression(innerExpressionType, exp.left(), new Filter.Value(o)));
				}
				context.append(expressionConverter.convertExpression(aggregate(outerExpressionType, eqExprs)));
			}
			else {
				// 1. foo IN ["bar"] is equivalent to foo == "BAR"
				// 2. foo NIN ["bar"] is equivalent to foo != "BAR"
				context.append(expressionConverter
					.convertExpression(new Filter.Expression(innerExpressionType, exp.left(), exp.right())));
			}
		}
		else {
			Assert.state(exp.right() != null, "Filter IN right expression was null");
			throw new IllegalStateException(
					"Filter IN right expression should be of Filter.Value type but was " + exp.right().getClass());
		}
	}

	/**
	 * Recursively aggregates a list of expression into a binary tree with 'aggregateType'
	 * join nodes.
	 * @param aggregateType type all tree splits.
	 * @param expressions list of expressions to aggregate.
	 * @return Returns a binary tree expression.
	 */
	private static Filter.Expression aggregate(Filter.ExpressionType aggregateType,
			List<Filter.Expression> expressions) {

		if (expressions.size() == 1) {
			return expressions.get(0);
		}
		return new Filter.Expression(aggregateType, expressions.get(0),
				aggregate(aggregateType, expressions.subList(1, expressions.size())));
	}

}
