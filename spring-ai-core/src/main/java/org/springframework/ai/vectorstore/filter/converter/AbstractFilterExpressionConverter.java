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
package org.springframework.ai.vectorstore.filter.converter;

import java.util.List;

import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionConverter;
import org.springframework.ai.vectorstore.filter.FilterHelper;
import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.Filter.ExpressionType;
import org.springframework.ai.vectorstore.filter.Filter.Group;
import org.springframework.ai.vectorstore.filter.Filter.Operand;

/**
 * @author Christian Tzolov
 */
public abstract class AbstractFilterExpressionConverter implements FilterExpressionConverter {

	@Override
	public String convertExpression(Expression expression) {
		return this.convertOperand(expression);
	}

	protected String convertOperand(Operand operand) {
		var context = new StringBuilder();
		this.convertOperand(operand, context);
		return context.toString();
	}

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
					&& expression.type() != ExpressionType.OR) && !(expression.right() instanceof Filter.Value)) {
				throw new RuntimeException("Non AND/OR expression must have Value right argument!");
			}
			if (expression.type() == ExpressionType.NOT) {
				this.doNot(expression, context);
			}
			else {
				this.doExpression(expression, context);
			}
		}
	}

	protected void doNot(Filter.Expression expression, StringBuilder context) {
		// Default behavior is to convert the NOT expression into its semantically
		// equivalent negation expression.
		// Effectively removing the NOT types form the boolean expression tree before
		// passing it to the doExpression.
		this.convertOperand(FilterHelper.negate(expression), context);
	}

	protected abstract void doExpression(Filter.Expression expression, StringBuilder context);

	protected abstract void doKey(Filter.Key filterKey, StringBuilder context);

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

	protected void doSingleValue(Object value, StringBuilder context) {
		if (value instanceof String) {
			context.append(String.format("\"%s\"", value));
		}
		else {
			context.append(value);
		}
	}

	protected void doGroup(Group group, StringBuilder context) {
		this.doStartGroup(group, context);
		this.convertOperand(group.content(), context);
		this.doEndGroup(group, context);
	}

	protected void doStartGroup(Group group, StringBuilder context) {
	}

	protected void doEndGroup(Group group, StringBuilder context) {
	}

	protected void doStartValueRange(Filter.Value listValue, StringBuilder context) {
		context.append("[");
	}

	protected void doEndValueRange(Filter.Value listValue, StringBuilder context) {
		context.append("]");
	}

	protected void doAddValueRangeSpitter(Filter.Value listValue, StringBuilder context) {
		context.append(",");
	}

	// Utilities
	protected boolean hasOuterQuotes(String str) {
		str = str.trim();
		return (str.startsWith("\"") && str.endsWith("\"")) || (str.startsWith("'") && str.endsWith("'"));
	}

	protected String removeOuterQuotes(String in) {
		return in.substring(1, in.length() - 1);
	}

}
