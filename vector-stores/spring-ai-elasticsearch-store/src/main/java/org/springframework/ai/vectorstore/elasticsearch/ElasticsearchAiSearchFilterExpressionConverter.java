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

package org.springframework.ai.vectorstore.elasticsearch;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;

import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.Filter.Key;
import org.springframework.ai.vectorstore.filter.converter.AbstractFilterExpressionConverter;
import org.springframework.util.Assert;

/**
 * ElasticsearchAiSearchFilterExpressionConverter is a class that converts
 * Filter.Expression objects into Elasticsearch query string representation. It extends
 * the AbstractFilter ExpressionConverter class.
 *
 * @author Jemin Huh
 * @since 1.0.0
 */
public class ElasticsearchAiSearchFilterExpressionConverter extends AbstractFilterExpressionConverter {

	private final DateTimeFormatter dateFormat;

	public ElasticsearchAiSearchFilterExpressionConverter() {
		this.dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);
	}

	@Override
	protected void doExpression(Expression expression, StringBuilder context) {
		if (expression.type() == Filter.ExpressionType.IN || expression.type() == Filter.ExpressionType.NIN) {
			Assert.state(expression.right() != null, "expression.right() must not be null");
			context.append(getOperationSymbol(expression));
			this.convertOperand(expression.left(), context);
			context.append("(");
			this.convertOperand(expression.right(), context);
			context.append(")");
		}
		else if (expression.type() == Filter.ExpressionType.ISNULL) {
			context.append("-");
			this.convertOperand(expression.left(), context);
			context.append("*");
		}
		else if (expression.type() == Filter.ExpressionType.ISNOTNULL) {
			this.convertOperand(expression.left(), context);
			context.append("*");
		}
		else {
			Assert.state(expression.right() != null, "expression.right() must not be null");
			this.convertOperand(expression.left(), context);
			context.append(getOperationSymbol(expression));
			this.convertOperand(expression.right(), context);
		}
	}

	@Override
	protected void doStartValueRange(Filter.Value listValue, StringBuilder context) {
	}

	@Override
	protected void doEndValueRange(Filter.Value listValue, StringBuilder context) {
	}

	@Override
	protected void doAddValueRangeSpitter(Filter.Value listValue, StringBuilder context) {
		context.append(" OR ");
	}

	private String getOperationSymbol(Expression exp) {
		return switch (exp.type()) {
			case AND -> " AND ";
			case OR -> " OR ";
			case EQ, IN -> "";
			case NE -> " NOT ";
			case LT -> "<";
			case LTE -> "<=";
			case GT -> ">";
			case GTE -> ">=";
			case NIN -> "NOT ";
			default -> throw new RuntimeException("Not supported expression type: " + exp.type());
		};
	}

	@Override
	public void doKey(Key key, StringBuilder context) {
		var fieldPath = "metadata." + key.key().trim();
		emitLuceneString(fieldPath, context);
		context.append(':');
	}

	@Override
	protected void doValue(Filter.Value filterValue, StringBuilder context) {
		if (filterValue.value() instanceof List list) {
			int c = 0;
			for (Object v : list) {
				this.doSingleValue(normalizeDateString(v), context);
				if (c++ < list.size() - 1) {
					this.doAddValueRangeSpitter(filterValue, context);
				}
			}
		}
		else {
			this.doSingleValue(normalizeDateString(filterValue.value()), context);
		}
	}

	@Override
	protected void doSingleValue(Object value, StringBuilder context) {
		if (value instanceof Date date) {
			context.append(this.dateFormat.format(date.toInstant()));
		}
		else if (value instanceof String text) {
			context.append("\"");
			emitLuceneString(text, context);
			context.append("\"");
		}
		else {
			context.append(value);
		}
	}

	@Override
	public void doStartGroup(Filter.Group group, StringBuilder context) {
		context.append("(");
	}

	@Override
	public void doEndGroup(Filter.Group group, StringBuilder context) {
		context.append(")");
	}

}
