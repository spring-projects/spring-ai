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

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.Filter.ExpressionType;
import org.springframework.ai.vectorstore.filter.Filter.Group;
import org.springframework.ai.vectorstore.filter.Filter.Operand;
import org.springframework.ai.vectorstore.filter.FilterExpressionConverter;
import org.springframework.ai.vectorstore.filter.FilterHelper;

/**
 * AbstractFilterExpressionConverter is an abstract class that implements the
 * FilterExpressionConverter interface. It provides default implementations for converting
 * a Filter.Expression into a string representation. All specific filter expression
 * converters should extend this abstract class and implement the remaining abstract
 * methods. Note: The class cannot be directly instantiated as it is abstract.
 *
 * @author Christian Tzolov
 */
public abstract class AbstractFilterExpressionConverter implements FilterExpressionConverter {

	/**
	 * ObjectMapper used for JSON string escaping.
	 */
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	/**
	 * Pattern for ISO-8601 date strings in UTC (yyyy-MM-dd'T'HH:mm:ss'Z') used to
	 * recognize and normalize date strings before passing to converters.
	 */
	protected static final Pattern ISO_DATE_PATTERN = Pattern
		.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d{1,9})?Z");

	/**
	 * Formatter for parsing and normalizing ISO date strings.
	 */
	protected static final DateTimeFormatter ISO_DATE_FORMATTER = DateTimeFormatter
		.ofPattern("yyyy-MM-dd'T'HH:mm:ss[.SSS]'Z'")
		.withZone(ZoneOffset.UTC);

	/**
	 * Create a new AbstractFilterExpressionConverter.
	 */
	public AbstractFilterExpressionConverter() {
	}

	@Override
	public String convertExpression(Expression expression) {
		return this.convertOperand(expression);
	}

	/**
	 * Convert the given operand into a string representation.
	 * @param operand the operand to convert
	 * @return the string representation of the operand
	 */
	protected String convertOperand(Operand operand) {
		var context = new StringBuilder();
		this.convertOperand(operand, context);
		return context.toString();
	}

	/**
	 * Convert the given operand into a string representation.
	 * @param operand the operand to convert
	 * @param context the context to append the string representation to
	 */
	protected void convertOperand(Operand operand, StringBuilder context) {

		if (operand instanceof Filter.Group group) {
			this.doGroup(group, context);
		}
		else if (operand instanceof Filter.Key key) {
			this.doKey(key, context);
		}
		else if (operand instanceof Filter.Value value) {
			this.doValue(value, context);
		}
		else if (operand instanceof Filter.Expression expression) {
			if ((expression.type() != ExpressionType.NOT && expression.type() != ExpressionType.AND
					&& expression.type() != ExpressionType.OR) && !(expression.right() instanceof Filter.Value)
					&& !(expression.type() == ExpressionType.ISNULL || expression.type() == ExpressionType.ISNOTNULL)) {
				throw new RuntimeException("Non AND/OR/ISNULL/ISNOTNULL expression must have Value right argument!");
			}
			if (expression.type() == ExpressionType.NOT) {
				this.doNot(expression, context);
			}
			else {
				this.doExpression(expression, context);
			}
		}
	}

	/**
	 * Convert the given expression into a string representation.
	 * @param expression the expression to convert
	 * @param context the context to append the string representation to
	 */
	protected void doNot(Filter.Expression expression, StringBuilder context) {
		// Default behavior is to convert the NOT expression into its semantically
		// equivalent negation expression.
		// Effectively removing the NOT types form the boolean expression tree before
		// passing it to the doExpression.
		this.convertOperand(FilterHelper.negate(expression), context);
	}

	/**
	 * Convert the given expression into a string representation.
	 * @param expression the expression to convert
	 * @param context the context to append the string representation to
	 */
	protected abstract void doExpression(Filter.Expression expression, StringBuilder context);

	/**
	 * Convert the given key into a string representation.
	 * @param filterKey the key to convert
	 * @param context the context to append the string representation to
	 */
	protected abstract void doKey(Filter.Key filterKey, StringBuilder context);

	/**
	 * Convert the given value into a string representation.
	 * @param filterValue the value to convert
	 * @param context the context to append the string representation to
	 */
	protected void doValue(Filter.Value filterValue, StringBuilder context) {
		if (filterValue.value() instanceof List list) {
			doStartValueRange(filterValue, context);
			int c = 0;
			for (Object v : list) {
				this.doSingleValue(normalizeDateString(v), context);
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
	 * If the value is a string matching the ISO date pattern, parse and return as
	 * {@link Date} so that all converters that handle {@code Date} automatically support
	 * date strings. Otherwise return the value unchanged.
	 * @param value the value (possibly a date string)
	 * @return the value, or a {@code Date} if the value was a parseable date string
	 */
	protected static Object normalizeDateString(Object value) {
		if (!(value instanceof String text) || !ISO_DATE_PATTERN.matcher(text).matches()) {
			return value;
		}
		try {
			return Date.from(Instant.from(ISO_DATE_FORMATTER.parse(text)));
		}
		catch (DateTimeParseException e) {
			throw new IllegalArgumentException("Invalid date type: " + text, e);
		}
	}

	/**
	 * Convert the given single value into a string representation and append it to the
	 * context. This method handles all value types including String, Number, Boolean,
	 * Date, etc.
	 * <p>
	 * For convenience, implementations can use the provided static helper methods such as
	 * {@link #emitJsonValue(Object, StringBuilder)} for JSON-based filters,
	 * {@link #emitLuceneString(String, StringBuilder)} for Lucene-based filters, or
	 * implement their own format-specific escaping logic as needed.
	 * @param value the value to convert
	 * @param context the context to append the string representation to
	 */
	protected abstract void doSingleValue(Object value, StringBuilder context);

	/**
	 * Emit a string value formatted for Lucene query syntax by appending escaped
	 * characters to the provided context. Used by Elasticsearch, OpenSearch, and GemFire
	 * VectorDB query string filters.
	 * <p>
	 * Lucene/Elasticsearch query strings require backslash-escaping of special
	 * characters: {@code + - = ! ( ) { } [ ] ^ " ~ * ? : \ / & | < > } as well as spaces.
	 * @param value the string value to format
	 * @param context the context to append the escaped string to
	 * @see <a href=
	 * "https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-query-string-query.html#_reserved_characters">Elasticsearch
	 * Reserved Characters</a>
	 */
	protected static void emitLuceneString(String value, StringBuilder context) {
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);

			// Escape Lucene query string special characters
			switch (c) {
				case '+':
				case '-':
				case '=':
				case '!':
				case '(':
				case ')':
				case '{':
				case '}':
				case '[':
				case ']':
				case '^':
				case '"':
				case '~':
				case '*':
				case '?':
				case ':':
				case '\\':
				case '/':
				case '&':
				case '|':
				case '<':
				case '>':
				case ' ':
					context.append('\\').append(c);
					break;
				default:
					context.append(c);
					break;
			}
		}
	}

	/**
	 * Emit a value formatted as JSON by appending its JSON representation to the provided
	 * context. Used for PostgreSQL JSONPath, Neo4j Cypher, Weaviate GraphQL, and other
	 * JSON-based filter expressions.
	 * <p>
	 * This method uses Jackson's ObjectMapper to properly serialize all value types:
	 * <ul>
	 * <li>Strings: properly quoted and escaped with double quotes, backslashes, and
	 * control characters handled</li>
	 * <li>Numbers: formatted without quotes (e.g., 42, 3.14)</li>
	 * <li>Booleans: formatted as JSON literals {@code true} or {@code false}</li>
	 * <li>null: formatted as JSON literal {@code null}</li>
	 * <li>Other types: handled according to Jackson's default serialization</li>
	 * </ul>
	 * @param value the value to format (can be any type)
	 * @param context the context to append the JSON representation to
	 */
	protected static void emitJsonValue(Object value, StringBuilder context) {
		try {
			context.append(OBJECT_MAPPER.writeValueAsString(value));
		}
		catch (JacksonException e) {
			throw new RuntimeException("Error serializing value to JSON.", e);
		}
	}

	/**
	 * Convert the given group into a string representation.
	 * @param group the group to convert
	 * @param context the context to append the string representation to
	 */
	protected void doGroup(Group group, StringBuilder context) {
		this.doStartGroup(group, context);
		this.convertOperand(group.content(), context);
		this.doEndGroup(group, context);
	}

	/**
	 * Convert the given group into a string representation.
	 * @param group the group to convert
	 * @param context the context to append the string representation to
	 */
	protected void doStartGroup(Group group, StringBuilder context) {
	}

	/**
	 * Convert the given group into a string representation.
	 * @param group the group to convert
	 * @param context the context to append the string representation to
	 */
	protected void doEndGroup(Group group, StringBuilder context) {
	}

	/**
	 * Convert the given value range into a string representation.
	 * @param listValue the value range to convert
	 * @param context the context to append the string representation to
	 */
	protected void doStartValueRange(Filter.Value listValue, StringBuilder context) {
		context.append("[");
	}

	/**
	 * Convert the given value range into a string representation.
	 * @param listValue the value range to convert
	 * @param context the context to append the string representation to
	 */
	protected void doEndValueRange(Filter.Value listValue, StringBuilder context) {
		context.append("]");
	}

	/**
	 * Convert the given value range into a string representation.
	 * @param listValue the value range to convert
	 * @param context the context to append the string representation to
	 */
	protected void doAddValueRangeSpitter(Filter.Value listValue, StringBuilder context) {
		context.append(",");
	}

	// Utilities
	/**
	 * Check if the given string has outer quotes.
	 * @param str the string to check
	 * @return true if the string has outer quotes, false otherwise
	 */
	protected boolean hasOuterQuotes(String str) {
		str = str.trim();
		return (str.startsWith("\"") && str.endsWith("\"")) || (str.startsWith("'") && str.endsWith("'"));
	}

	/**
	 * Remove the outer quotes from the given string.
	 * @param in the string to remove the outer quotes from
	 * @return the string without the outer quotes
	 */
	protected String removeOuterQuotes(String in) {
		return in.substring(1, in.length() - 1);
	}

}
