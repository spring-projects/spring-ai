/*
 * Copyright 2023-2023 the original author or authors.
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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.time.DateFormatUtils;

import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.Filter.ExpressionType;
import org.springframework.ai.vectorstore.filter.Filter.Group;
import org.springframework.ai.vectorstore.filter.Filter.Key;
import org.springframework.ai.vectorstore.filter.converter.AbstractFilterExpressionConverter;
import org.springframework.util.Assert;

/**
 * Converts {@link Expression} into Weaviate metadata filter expression format.
 * (https://weaviate.io/developers/weaviate/api/graphql/filters)
 *
 * @author Christian Tzolov
 */
public class WeaviateFilterExpressionConverter extends AbstractFilterExpressionConverter {

	private boolean mapIntegerToNumberValue = true;

	// https://weaviate.io/developers/weaviate/api/graphql/filters#special-cases
	private static final List<String> SYSTEM_IDENTIFIERS = List.of("id", "_creationTimeUnix", "_lastUpdateTimeUnix");

	private List<String> allowedIdentifierNames;

	public WeaviateFilterExpressionConverter(List<String> allowedIdentifierNames) {
		Assert.notNull(allowedIdentifierNames, "List can be empty but not null.");
		this.allowedIdentifierNames = allowedIdentifierNames;
	}

	public void setAllowedIdentifierNames(List<String> allowedIdentifierNames) {
		this.allowedIdentifierNames = allowedIdentifierNames;
	}

	public void setMapIntegerToNumberValue(boolean mapIntegerToNumberValue) {
		this.mapIntegerToNumberValue = mapIntegerToNumberValue;
	}

	@Override
	protected void doExpression(Expression exp, StringBuilder context) {

		if (exp.type() == ExpressionType.IN) {
			rewriteInNinExpressions(Filter.ExpressionType.OR, Filter.ExpressionType.EQ, exp, context);
		}
		else if (exp.type() == ExpressionType.NIN) {
			rewriteInNinExpressions(Filter.ExpressionType.AND, Filter.ExpressionType.NE, exp, context);
		}
		else if (exp.type() == ExpressionType.AND || exp.type() == ExpressionType.OR) {
			context.append(getOperationSymbol(exp));
			context.append("operands:[{");
			this.convertOperand(exp.left(), context);
			context.append("},\n{");
			this.convertOperand(exp.right(), context);
			context.append("}]");
		}
		else {
			this.convertOperand(exp.left(), context);
			context.append(getOperationSymbol(exp));
			this.convertOperand(exp.right(), context);
		}
	}

	/**
	 * Recursively aggregates a list of expression into a binary tree with 'aggregateType'
	 * join nodes.
	 * @param aggregateType type all tree splits.
	 * @param expressions list of expressions to aggregate.
	 * @return Returns a binary tree expression.
	 */
	private Filter.Expression aggregate(Filter.ExpressionType aggregateType, List<Filter.Expression> expressions) {

		if (expressions.size() == 1) {
			return expressions.get(0);
		}
		return new Filter.Expression(aggregateType, expressions.get(0),
				aggregate(aggregateType, expressions.subList(1, expressions.size())));
	}

	private void rewriteInNinExpressions(Filter.ExpressionType outerExpressionType,
			Filter.ExpressionType innerExpressionType, Expression exp, StringBuilder context) {
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
				this.doExpression(aggregate(outerExpressionType, eqExprs), context);
			}
			else {
				// 1. foo IN ["bar"] is equivalent to foo == "BAR"
				// 2. foo NIN ["bar"] is equivalent to foo != "BAR"
				this.doExpression(new Filter.Expression(innerExpressionType, exp.left(), exp.right()), context);
			}
		}
		else {
			throw new IllegalStateException(
					"Filter IN right expression should be of Filter.Value type but was " + exp.right().getClass());
		}
	}

	private String getOperationSymbol(Expression exp) {
		switch (exp.type()) {
			case AND:
				return "operator:And \n";
			case OR:
				return "operator:Or \n";
			case EQ:
				return "operator:Equal \n";
			case NE:
				return "operator:NotEqual \n";
			case LT:
				return "operator:LessThan \n";
			case LTE:
				return "operator:LessThanEqual \n";
			case GT:
				return "operator:GreaterThan \n";
			case GTE:
				return "operator:GreaterThanEqual \n";
			case IN:
				throw new IllegalStateException(
						"The 'IN' operator should have been transformed into chain of OR/EQ expressions.");
			case NIN:
				throw new IllegalStateException(
						"The 'NIN' operator should have been transformed into chain of AND/NEQ expressions.");
			default:
				throw new UnsupportedOperationException("Not supported expression type:" + exp.type());
		}
	}

	@Override
	protected void doKey(Key key, StringBuilder context) {
		var identifier = (hasOuterQuotes(key.key())) ? removeOuterQuotes(key.key()) : key.key();
		context.append("path:[\"" + withMetaPrefix(identifier) + "\"] \n");
	}

	public String withMetaPrefix(String identifier) {
		if (SYSTEM_IDENTIFIERS.contains(identifier)) {
			return identifier;
		}

		if (this.allowedIdentifierNames.contains(identifier)) {
			return "meta_" + identifier;
		}

		throw new IllegalArgumentException("Not allowed filter identifier name: " + identifier
				+ ". Consider adding it to WeaviateVectorStore#filterMetadataKeys.");
	}

	@Override
	protected void doValue(Filter.Value filterValue, StringBuilder context) {
		if (filterValue.value() instanceof List) {
			// nothing
			throw new IllegalStateException("");
		}
		else {
			this.doSingleValue(filterValue.value(), context);
		}
	}

	@Override
	protected void doSingleValue(Object value, StringBuilder context) {
		String singleValueFormat = "valueNumber:%s ";
		if (value instanceof Integer i) {
			if (this.mapIntegerToNumberValue) {
				context.append(String.format(singleValueFormat, i));
			}
			else {
				context.append(String.format("valueInt:%s ", i));
			}
		}
		else if (value instanceof Long l) {
			if (this.mapIntegerToNumberValue) {
				context.append(String.format(singleValueFormat, l));
			}
			else {
				context.append(String.format("valueInt:%s ", l));
			}
		}
		else if (value instanceof Double d) {
			context.append(String.format(singleValueFormat, d));
		}
		else if (value instanceof Float f) {
			context.append(String.format(singleValueFormat, f));
		}
		else if (value instanceof Boolean b) {
			context.append(String.format("valueBoolean:%s ", b));
		}
		else if (value instanceof String s) {
			context.append(String.format("valueText:\"%s\" ", s));
		}
		else if (value instanceof Date date) {
			String dateString = DateFormatUtils.format(date, "yyyy-MM-dd\'T\'HH:mm:ssZZZZZ");
			context.append(String.format("valueDate:\"%s\" ", dateString));
		}
		else {
			throw new RuntimeException("Unsupported value type: " + value);
		}
	}

	@Override
	protected void doGroup(Group group, StringBuilder context) {
		// Replaces the group: AND((foo == "bar" OR bar == "foo"), "boza" == "koza") into
		// AND(AND(id != -1, (foo == "bar" OR bar == "foo")), "boza" == "koza") into
		this.convertOperand(new Expression(ExpressionType.AND,
				new Expression(ExpressionType.NE, new Filter.Key("id"), new Filter.Value("-1")), group.content()),
				context);
	}

}