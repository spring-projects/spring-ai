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

package org.springframework.ai.vectorstore.redis;

import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import redis.clients.jedis.search.RediSearchUtil;

import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.Filter.ExpressionType;
import org.springframework.ai.vectorstore.filter.Filter.Group;
import org.springframework.ai.vectorstore.filter.Filter.Key;
import org.springframework.ai.vectorstore.filter.Filter.Value;
import org.springframework.ai.vectorstore.filter.converter.AbstractFilterExpressionConverter;
import org.springframework.ai.vectorstore.redis.RedisVectorStore.MetadataField;
import org.springframework.util.Assert;

/**
 * Converts {@link Expression} into Redis search filter expression format. (<a href=
 * "https://redis.io/docs/latest/develop/ai/search-and-query/">search-and-query</a>)
 *
 * @author Julien Ruaux
 */
public class RedisFilterExpressionConverter extends AbstractFilterExpressionConverter {

	private final Map<String, MetadataField> metadataFields;

	public RedisFilterExpressionConverter(List<MetadataField> metadataFields) {
		this.metadataFields = metadataFields.stream()
			.collect(Collectors.toMap(MetadataField::name, Function.identity()));
	}

	@Override
	protected void doStartGroup(Group group, StringBuilder context) {
		context.append("(");
	}

	@Override
	protected void doEndGroup(Group group, StringBuilder context) {
		context.append(")");
	}

	@Override
	protected void doKey(Key key, StringBuilder context) {
		var identifier = key.key();
		// RediSearch field names are bare identifiers in the @field: query syntax
		// and have no escaping mechanism. Validate against the configured metadata
		// fields to prevent query injection through crafted field names.
		if (!this.metadataFields.containsKey(identifier)) {
			throw new IllegalArgumentException("Not allowed filter identifier name: " + identifier);
		}
		context.append("@").append(identifier).append(":");
	}

	@Override
	protected void doExpression(Expression expression, StringBuilder context) {
		switch (expression.type()) {
			case NIN:
				doExpression(negate(ExpressionType.IN, expression), context);
				break;
			case NE:
				doExpression(negate(ExpressionType.EQ, expression), context);
				break;
			case AND:
				doBinaryOperation(" ", expression, context);
				break;
			case OR:
				doBinaryOperation(" | ", expression, context);
				break;
			case NOT:
				context.append("-");
				convertOperand(expression.left(), context);
				break;
			default:
				doField(expression, context);
				break;
		}

	}

	private Expression negate(ExpressionType expressionType, Expression expression) {
		return new Expression(ExpressionType.NOT, new Expression(expressionType, expression.left(), expression.right()),
				null);
	}

	private void doBinaryOperation(String delimiter, Expression expression, StringBuilder context) {
		Assert.state(expression.right() != null, "expected an expression with a right operand");
		this.convertOperand(expression.left(), context);
		context.append(delimiter);
		this.convertOperand(expression.right(), context);
	}

	private void doField(Expression expression, StringBuilder context) {
		Key key = (Key) expression.left();
		doKey(key, context);
		var identifier = key.key();
		MetadataField field = this.metadataFields.get(identifier);
		Assert.state(field != null, "No metadata field configured for: " + identifier);
		Value value = (Value) expression.right();
		Assert.state(value != null, "expected an expression with a right operand");
		switch (field.fieldType()) {
			case NUMERIC:
				Numeric numeric = numeric(expression, value);
				context.append("[");
				context.append(numeric.lower());
				context.append(" ");
				context.append(numeric.upper());
				context.append("]");
				break;
			case TAG:
				context.append("{");
				context.append(tagStringValue(expression, value));
				context.append("}");
				break;
			case TEXT:
				context.append("(");
				context.append(textStringValue(expression, value));
				context.append(")");
				break;
			default:
				throw new UnsupportedOperationException(
						MessageFormat.format("Field type {0} not supported", field.fieldType()));
		}
	}

	private String tagStringValue(Expression expression, Value value) {
		String delimiter = tagValueDelimiter(expression);
		if (value.value() instanceof List<?> list) {
			return list.stream().map(String::valueOf).map(this::escapeTagValue).collect(Collectors.joining(delimiter));
		}
		return escapeTagValue(String.valueOf(value.value()));
	}

	private String textStringValue(Expression expression, Value value) {
		String delimiter = tagValueDelimiter(expression);
		if (value.value() instanceof List<?> list) {
			return list.stream()
				.map(String::valueOf)
				.map(RediSearchUtil::escapeQuery)
				.collect(Collectors.joining(delimiter));
		}
		return RediSearchUtil.escapeQuery(String.valueOf(value.value()));
	}

	/**
	 * Escapes characters that have special meaning inside a RediSearch TAG query clause
	 * ({@code @field:\{value\}}). The following characters are escaped with a backslash:
	 * {@code $}, {@code \}, {@code |}, {@code {}, {@code }}, {@code (}, {@code )},
	 * {@code [}, {@code ]}, {@code -}, and {@code '}.
	 */
	private String escapeTagValue(String value) {
		StringBuilder sb = new StringBuilder(value.length());
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			switch (c) {
				case '\\', '$', '|', '{', '}', '(', ')', '[', ']', '-', '\'' -> sb.append('\\').append(c);
				default -> sb.append(c);
			}
		}
		return sb.toString();
	}

	private String tagValueDelimiter(Expression expression) {
		return switch (expression.type()) {
			case IN -> " | ";
			case EQ -> " ";
			default -> throw new UnsupportedOperationException(
					MessageFormat.format("Tag operand {0} not supported", expression.type()));
		};
	}

	private Numeric numeric(Expression expression, Value value) {
		return switch (expression.type()) {
			case EQ -> new Numeric(inclusive(value), inclusive(value));
			case GT -> new Numeric(exclusive(value), NumericBoundary.POSITIVE_INFINITY);
			case GTE -> new Numeric(inclusive(value), NumericBoundary.POSITIVE_INFINITY);
			case LT -> new Numeric(NumericBoundary.NEGATIVE_INFINITY, exclusive(value));
			case LTE -> new Numeric(NumericBoundary.NEGATIVE_INFINITY, inclusive(value));
			default -> throw new UnsupportedOperationException(
					MessageFormat.format("Expression type {0} not supported for numeric fields", expression.type()));
		};
	}

	private NumericBoundary inclusive(Value value) {
		if (!(value.value() instanceof Number)) {
			throw new IllegalArgumentException("Numeric value must be a Number");
		}
		return new NumericBoundary(value.value(), false);
	}

	private NumericBoundary exclusive(Value value) {
		if (!(value.value() instanceof Number)) {
			throw new IllegalArgumentException("Numeric value must be a Number");
		}
		return new NumericBoundary(value.value(), true);
	}

	@Override
	protected void doSingleValue(Object value, StringBuilder context) {
		emitJsonValue(value, context);
	}

	record Numeric(NumericBoundary lower, NumericBoundary upper) {

	}

	record NumericBoundary(Object value, boolean exclusive) {

		private static final NumericBoundary POSITIVE_INFINITY = new NumericBoundary(Double.POSITIVE_INFINITY, true);

		private static final NumericBoundary NEGATIVE_INFINITY = new NumericBoundary(Double.NEGATIVE_INFINITY, true);

		private static final String INFINITY = "inf";

		private static final String MINUS_INFINITY = "-inf";

		private static final String INCLUSIVE_FORMAT = "%s";

		private static final String EXCLUSIVE_FORMAT = "(%s";

		@Override
		public String toString() {
			if (this == NEGATIVE_INFINITY) {
				return MINUS_INFINITY;
			}
			if (this == POSITIVE_INFINITY) {
				return INFINITY;
			}
			return String.format(formatString(), this.value);
		}

		private String formatString() {
			if (this.exclusive) {
				return EXCLUSIVE_FORMAT;
			}
			return INCLUSIVE_FORMAT;

		}

	}

}
