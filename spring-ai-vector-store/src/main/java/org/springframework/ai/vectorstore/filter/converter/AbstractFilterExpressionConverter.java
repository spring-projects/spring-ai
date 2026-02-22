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

package org.springframework.ai.vectorstore.filter.converter;

import java.util.List;

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
				this.doSingleValue(v, context);
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

	/**
	 * Convert the given value into a string representation.
	 * @param value the value to convert
	 * @param context the context to append the string representation to
	 */
	protected void doSingleValue(Object value, StringBuilder context) {
		if (value instanceof String s) {
			context.append("\"");
			context.append(escapeStringValue(s));
			context.append("\"");
		}
		else {
			context.append(value);
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
	 * Escape special characters in string values to prevent injection attacks.
	 *
	 * <p>
	 * This method escapes characters that could break the filter syntax or enable
	 * injection attacks. The order of replacements is critical: backslashes must be
	 * escaped first to avoid double-escaping.
	 *
	 * <p>
	 * Subclasses can override this method to implement custom escaping rules specific to
	 * their vector store implementation.
	 *
	 * <p>
	 * <b>Note:</b> This method escapes backslashes and double quotes as required by
	 * JSON-like filter syntax. Single quotes are NOT escaped as they are not special
	 * characters in JSON. Subclasses targeting vector stores with different escaping
	 * requirements (e.g., SQL-based stores) should override this method.
	 * @param input the string to escape
	 * @return the escaped string safe for use in filter expressions
	 * @author Zexuan Peng <pengzexuan@gmail.com>
	 */
	protected String escapeStringValue(String input) {
		// Replace in order: \ first, then "
		// Backslash MUST be first to avoid double-escaping
		// Single quotes are not escaped as they are not special in JSON
		return input.replace("\\", "\\\\").replace("\"", "\\\"");
	}

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
