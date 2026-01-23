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

package org.springframework.ai.vectorstore.weaviate;

import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.time.DateFormatUtils;

import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.Filter.ExpressionType;
import org.springframework.ai.vectorstore.filter.Filter.Group;
import org.springframework.ai.vectorstore.filter.Filter.Key;
import org.springframework.ai.vectorstore.filter.FilterHelper;
import org.springframework.ai.vectorstore.filter.converter.AbstractFilterExpressionConverter;
import org.springframework.util.Assert;

/**
 * Converts {@link Expression} into Weaviate metadata filter expression format.
 * (https://weaviate.io/developers/weaviate/api/graphql/filters)
 *
 * @author Christian Tzolov
 * @author Jonghoon Park
 */
public class WeaviateFilterExpressionConverter extends AbstractFilterExpressionConverter {

	// https://weaviate.io/developers/weaviate/api/graphql/filters#special-cases
	private static final List<String> SYSTEM_IDENTIFIERS = List.of("id", "_creationTimeUnix", "_lastUpdateTimeUnix");

	private static final String DEFAULT_META_FIELD_PREFIX = "meta_";

	private boolean mapIntegerToNumberValue = true;

	private List<String> allowedIdentifierNames;

	private final String metaFieldPrefix;

	/**
	 * Constructs a new instance of the {@code WeaviateFilterExpressionConverter} class.
	 * This constructor uses the default meta field prefix
	 * ({@link #DEFAULT_META_FIELD_PREFIX}).
	 * @param allowedIdentifierNames A {@code List} of allowed identifier names.
	 */
	public WeaviateFilterExpressionConverter(List<String> allowedIdentifierNames) {
		this(allowedIdentifierNames, DEFAULT_META_FIELD_PREFIX);
	}

	/**
	 * Constructs a new instance of the {@code WeaviateFilterExpressionConverter} class.
	 * @param allowedIdentifierNames A {@code List} of allowed identifier names.
	 * @param metaFieldPrefix the prefix for meta fields
	 * @since 1.1.0
	 */
	public WeaviateFilterExpressionConverter(List<String> allowedIdentifierNames, String metaFieldPrefix) {
		Assert.notNull(allowedIdentifierNames, "List can be empty but not null.");
		Assert.notNull(metaFieldPrefix, "metaFieldPrefix can be empty but not null.");
		this.allowedIdentifierNames = allowedIdentifierNames;
		this.metaFieldPrefix = metaFieldPrefix;
	}

	public void setAllowedIdentifierNames(List<String> allowedIdentifierNames) {
		this.allowedIdentifierNames = allowedIdentifierNames;
	}

	public void setMapIntegerToNumberValue(boolean mapIntegerToNumberValue) {
		this.mapIntegerToNumberValue = mapIntegerToNumberValue;
	}

	@Override
	protected void doExpression(Expression exp, StringBuilder context) {
		Assert.state(exp.right() != null, "expected an expression with a right operand");
		if (exp.type() == ExpressionType.IN) {
			FilterHelper.expandIn(exp, context, this);
		}
		else if (exp.type() == ExpressionType.NIN) {
			FilterHelper.expandNin(exp, context, this);
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

	private String getOperationSymbol(Expression exp) {
		return switch (exp.type()) {
			case AND -> "operator:And \n";
			case OR -> "operator:Or \n";
			case EQ -> "operator:Equal \n";
			case NE -> "operator:NotEqual \n";
			case LT -> "operator:LessThan \n";
			case LTE -> "operator:LessThanEqual \n";
			case GT -> "operator:GreaterThan \n";
			case GTE -> "operator:GreaterThanEqual \n";
			case IN -> throw new IllegalStateException(
					"The 'IN' operator should have been transformed into chain of OR/EQ expressions.");
			case NIN -> throw new IllegalStateException(
					"The 'NIN' operator should have been transformed into chain of AND/NEQ expressions.");
			default -> throw new UnsupportedOperationException("Not supported expression type:" + exp.type());
		};
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
			return this.metaFieldPrefix + identifier;
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
