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

package org.springframework.ai.vectorstore.azure;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;

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

	private final DateTimeFormatter dateFormat;

	private final List<String> allowedIdentifierNames;

	public AzureAiSearchFilterExpressionConverter(List<MetadataField> filterMetadataFields) {
		Assert.notNull(filterMetadataFields, "The filterMetadataFields can not null.");

		this.allowedIdentifierNames = filterMetadataFields.stream().map(MetadataField::name).toList();
		this.dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);
	}

	@Override
	protected void doExpression(Expression expression, StringBuilder context) {
		Assert.state(expression.right() != null, "expected expression to have a right operand");
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
		return switch (exp.type()) {
			case AND -> " and ";
			case OR -> " or ";
			case EQ -> " eq ";
			case NE -> " ne ";
			case LT -> " lt ";
			case LTE -> " le ";
			case GT -> " gt ";
			case GTE -> " ge ";
			case IN -> " search.in";
			case NIN -> " not search.in";
			default -> throw new RuntimeException("Not supported expression type: " + exp.type());
		};
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

	/**
	 * Adds the metadata field prefix to the given identifier name. Azure AI Search
	 * requires metadata fields to be prefixed with "meta_" to distinguish them from
	 * system fields.
	 * @param identifier the field identifier without prefix
	 * @return the prefixed field identifier (e.g., "meta_fieldName")
	 * @throws IllegalArgumentException if the identifier is not in the allowed list
	 */
	public String withMetaPrefix(String identifier) {

		if (this.allowedIdentifierNames.contains(identifier)) {
			return "meta_" + identifier;
		}

		throw new IllegalArgumentException("Not allowed filter identifier name: " + identifier);
	}

	@Override
	protected void doValue(Filter.Value filterValue, StringBuilder context) {
		if (filterValue.value() instanceof List list) {
			// search.in(field, 'val1,val2,val3', ',') requires one string literal
			doStartValueRange(filterValue, context);
			int c = 0;
			for (Object v : list) {
				appendListElementContent(normalizeDateString(v), context);
				if (c++ < list.size() - 1) {
					this.doAddValueRangeSpitter(filterValue, context);
				}
			}
			this.doEndValueRange(filterValue, context);
		}
		else {
			this.doSingleValue(normalizeDateString(filterValue.value()), context);
		}
	}

	/**
	 * Appends the content of one list element for search.in (no surrounding quotes). Used
	 * so the list renders as 'val1,val2,val3' not 'val1','val2','val3'.
	 */
	private void appendListElementContent(Object value, StringBuilder context) {
		if (value instanceof Date date) {
			context.append(this.dateFormat.format(date.toInstant()));
		}
		else if (value instanceof String text) {
			appendODataStringContent(text, context);
		}
		else {
			context.append(value);
		}
	}

	@Override
	protected void doSingleValue(Object value, StringBuilder context) {
		if (value instanceof Date date) {
			context.append(this.dateFormat.format(date.toInstant()));
		}
		else if (value instanceof String text) {
			emitODataString(text, context);
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

	/**
	 * Emit an OData-formatted string value with single quote wrapping and escaping by
	 * appending to the provided context. Used by Azure AI Search and other
	 * OData-compliant search services.
	 * <p>
	 * In OData, single quotes within string literals are escaped by doubling them:
	 * {@code '} → {@code ''}
	 * @param value the string value to format
	 * @param context the context to append the OData string literal to
	 * @since 2.0.0
	 * @see <a href=
	 * "https://docs.oasis-open.org/odata/odata/v4.01/odata-v4.01-part2-url-conventions.html#sec_PrimitiveLiterals">OData
	 * Primitive Literals</a>
	 */
	protected static void emitODataString(String value, StringBuilder context) {
		context.append("'");
		appendODataStringContent(value, context);
		context.append("'");
	}

	/**
	 * Appends string content with OData single-quote escaping (no surrounding quotes).
	 */
	private static void appendODataStringContent(String value, StringBuilder context) {
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (c == '\'') {
				context.append("''");
			}
			else {
				context.append(c);
			}
		}
	}

}
