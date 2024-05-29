/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.vectorstore.azure;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.regex.Pattern;

import org.springframework.ai.vectorstore.azure.AzureVectorStore.MetadataField;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.Filter.ExpressionType;
import org.springframework.ai.vectorstore.filter.Filter.Group;
import org.springframework.ai.vectorstore.filter.Filter.Key;
import org.springframework.ai.vectorstore.filter.converter.AbstractFilterExpressionConverter;
import org.springframework.util.Assert;

/**
 * Converts {@link Expression} into Azure Search OData filter syntax.
 * https://learn.microsoft.com/en-us/azure/search/search-query-odata-filter
 *
 * @author Christian Tzolov
 */
public class AzureAiSearchFilterExpressionConverter extends AbstractFilterExpressionConverter {

	private static Pattern DATE_FORMAT_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z");

	private final SimpleDateFormat dateFormat;

	private List<String> allowedIdentifierNames;

	public AzureAiSearchFilterExpressionConverter(List<MetadataField> filterMetadataFields) {
		Assert.notNull(filterMetadataFields, "The filterMetadataFields can not null.");

		this.allowedIdentifierNames = filterMetadataFields.stream().map(MetadataField::name).toList();
		this.dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
		this.dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
	}

	@Override
	protected void doExpression(Expression expression, StringBuilder context) {
		if (expression.type() == ExpressionType.IN || expression.type() == ExpressionType.NIN) {
			context.append(getOperationSymbol(expression));
			context.append("(");
			this.convertOperand(expression.left(), context);
			context.append(", ");
			this.convertOperand(expression.right(), context);
			context.append(", ',')");
		}
		else {
			this.convertOperand(expression.left(), context);
			context.append(getOperationSymbol(expression));
			this.convertOperand(expression.right(), context);
		}
	}

	protected void doStartValueRange(Filter.Value listValue, StringBuilder context) {
		context.append("'");
	}

	protected void doEndValueRange(Filter.Value listValue, StringBuilder context) {
		context.append("'");
	}

	private String getOperationSymbol(Expression exp) {
		switch (exp.type()) {
			case AND:
				return " and ";
			case OR:
				return " or ";
			case EQ:
				return " eq ";
			case NE:
				return " ne ";
			case LT:
				return " lt ";
			case LTE:
				return " le ";
			case GT:
				return " gt ";
			case GTE:
				return " ge ";
			case IN:
				return " search.in";
			case NIN:
				return " not search.in";
			default:
				throw new RuntimeException("Not supported expression type: " + exp.type());
		}
	}

	@Override
	public void doKey(Key key, StringBuilder context) {
		var hasOuterQuotes = hasOuterQuotes(key.key());
		var identifier = (hasOuterQuotes) ? removeOuterQuotes(key.key()) : key.key();
		var prefixedIdentifier = withMetaPrefix(identifier);
		if (hasOuterQuotes) {
			prefixedIdentifier = "'" + prefixedIdentifier.trim() + "'";
		}
		context.append(prefixedIdentifier);
	}

	public String withMetaPrefix(String identifier) {

		if (this.allowedIdentifierNames.contains(identifier)) {
			return "meta_" + identifier;
		}

		throw new IllegalArgumentException("Not allowed filter identifier name: " + identifier);
	}

	@Override
	protected void doValue(Filter.Value filterValue, StringBuilder context) {
		if (filterValue.value() instanceof List list) {
			doStartValueRange(filterValue, context);
			int c = 0;
			for (Object v : list) {
				// this.doSingleValue(v, context);
				context.append(v);
				if (c++ < list.size() - 1) {
					this.doAddValueRangeSpitter(filterValue, context);
				}
			}
			this.doEndValueRange(filterValue, context);
		}
		else {
			this.doSingleValue(filterValue.value(), context);
		}
	}

	@Override
	protected void doSingleValue(Object value, StringBuilder context) {
		if (value instanceof Date date) {
			context.append(this.dateFormat.format(date));
		}
		else if (value instanceof String text) {
			if (DATE_FORMAT_PATTERN.matcher(text).matches()) {
				try {
					Date date = this.dateFormat.parse(text);
					context.append(this.dateFormat.format(date));
				}
				catch (ParseException e) {
					throw new IllegalArgumentException("Invalid date type:" + text, e);
				}
			}
			else {
				context.append(String.format("'%s'", text));
			}
		}
		else {
			context.append(value);
		}
	}

	@Override
	public void doStartGroup(Group group, StringBuilder context) {
		context.append("(");
	}

	@Override
	public void doEndGroup(Group group, StringBuilder context) {
		context.append(")");
	}

}