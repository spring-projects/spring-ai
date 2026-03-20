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

package org.springframework.ai.vectorstore.filter.converter;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;

import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.Filter.Expression;

/**
 * Converts {@link Expression} into SpEL metadata filter expression format.
 * (https://docs.spring.io/spring-framework/reference/core/expressions.html)
 *
 * @author Jemin Huh
 */
public class SimpleVectorStoreFilterExpressionConverter extends AbstractFilterExpressionConverter {

	private final DateTimeFormatter dateFormat;

	public SimpleVectorStoreFilterExpressionConverter() {
		this.dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);
	}

	@Override
	protected void doExpression(Filter.Expression expression, StringBuilder context) {
		this.convertOperand(expression.left(), context);
		context.append(getOperationSymbol(expression));
		if (expression.right() != null) {
			this.convertOperand(expression.right(), context);
		}
		else {
			context.append("null");
		}
	}

	private String getOperationSymbol(Filter.Expression exp) {
		return switch (exp.type()) {
			case AND -> " and ";
			case OR -> " or ";
			case EQ -> " == ";
			case LT -> " < ";
			case LTE -> " <= ";
			case GT -> " > ";
			case GTE -> " >= ";
			case NE -> " != ";
			case IN -> " in ";
			case NIN -> " not in ";
			default -> throw new RuntimeException("Not supported expression type: " + exp.type());
		};
	}

	@Override
	protected void doKey(Filter.Key key, StringBuilder context) {
		var identifier = hasOuterQuotes(key.key()) ? removeOuterQuotes(key.key()) : key.key();
		context.append("#metadata['").append(identifier).append("']");
	}

	@Override
	protected void doValue(Filter.Value filterValue, StringBuilder context) {
		if (filterValue.value() instanceof List<?> list) {
			var formattedList = new StringBuilder("{");
			int c = 0;
			for (Object v : list) {
				this.doSingleValue(normalizeDateString(v), formattedList);
				if (c++ < list.size() - 1) {
					this.doAddValueRangeSpitter(filterValue, formattedList);
				}
			}
			formattedList.append("}");

			if (context.lastIndexOf("in ") == -1) {
				context.append(formattedList);
			}
			else {
				appendSpELContains(formattedList, context);
			}
		}
		else {
			this.doSingleValue(normalizeDateString(filterValue.value()), context);
		}
	}

	private void appendSpELContains(StringBuilder formattedList, StringBuilder context) {
		int metadataStart = context.lastIndexOf("#metadata");
		if (metadataStart == -1) {
			throw new RuntimeException("Wrong SpEL expression: " + context);
		}
		int metadataEnd = context.indexOf(" ", metadataStart);
		String metadata = context.substring(metadataStart, metadataEnd);
		context.setLength(context.lastIndexOf("in "));
		context.delete(metadataStart, metadataEnd + 1);
		context.append(formattedList).append(".contains(").append(metadata).append(")");
	}

	/**
	 * Emit a SpEL-formatted string value with single quote wrapping and escaping by
	 * appending to the provided context.
	 * <p>
	 * Escapes single quotes (using backslash) and backslashes (double backslash)
	 * according to SpEL string literal rules.
	 * <p>
	 * This method prevents SpEL injection attacks by properly escaping special
	 * characters.
	 * @param text the string value to format
	 * @param context the context to append the SpEL string literal to
	 * @since 2.0.0
	 */
	protected static void emitSpelString(String text, StringBuilder context) {
		context.append("'"); // Opening quote

		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);

			switch (c) {
				case '\'':
					// SpEL: single quote → backslash escaped
					context.append("\\'");
					break;
				case '\\':
					// SpEL: backslash → double backslash
					context.append("\\\\");
					break;
				default:
					context.append(c);
					break;
			}
		}

		context.append("'"); // Closing quote
	}

	@Override
	protected void doSingleValue(Object value, StringBuilder context) {
		if (value instanceof Date date) {
			context.append("'");
			context.append(this.dateFormat.format(date.toInstant()));
			context.append("'");
		}
		else if (value instanceof String text) {
			emitSpelString(text, context);
		}
		else {
			context.append(value);
		}
	}

	@Override
	protected void doGroup(Filter.Group group, StringBuilder context) {
		context.append("(");
		super.doGroup(group, context);
		context.append(")");
	}

}
