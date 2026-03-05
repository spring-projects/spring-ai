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

package org.springframework.ai.vectorstore.oracle;

import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.converter.AbstractFilterExpressionConverter;
import org.springframework.util.Assert;

/**
 * Converts a {@link Filter} into a JSON Path expression.
 *
 * @author Loïc Lefèvre
 * @see <a href=
 * "https://docs.oracle.com/en/database/oracle/oracle-database/23/adjsn/json-path-expressions.html#GUID-8656CAB9-C293-4A99-BB62-F38F3CFC4C13">JSON
 * Path Documentation</a>
 */
public class SqlJsonPathFilterExpressionConverter extends AbstractFilterExpressionConverter {

	@Override
	protected String convertOperand(final Filter.Operand operand) {
		final StringBuilder context = new StringBuilder();
		context.append("$?( ");
		this.convertOperand(operand, context);
		return context.append(" )").toString();
	}

	@Override
	protected void doExpression(final Filter.Expression expression, final StringBuilder context) {
		Assert.state(expression.right() != null, "expression should have a right operand");
		if (expression.type() == Filter.ExpressionType.NIN) {
			context.append("!( ");
			this.convertOperand(expression.left(), context);
			context.append(" in ");
			this.convertOperand(expression.right(), context);
			context.append(" )");
		}
		else {
			this.convertOperand(expression.left(), context);
			context.append(getOperationSymbol(expression));
			this.convertOperand(expression.right(), context);
		}
	}

	private String getOperationSymbol(final Filter.Expression exp) {
		return switch (exp.type()) {
			case AND -> " && ";
			case OR -> " || ";
			case EQ -> " == ";
			case NE -> " != ";
			case LT -> " < ";
			case LTE -> " <= ";
			case GT -> " > ";
			case GTE -> " >= ";
			case IN -> " in ";
			default -> throw new RuntimeException("Not supported expression type: " + exp.type());
		};
	}

	@Override
	protected void doStartValueRange(Filter.Value listValue, StringBuilder context) {
		context.append("( ");
	}

	@Override
	protected void doEndValueRange(Filter.Value listValue, StringBuilder context) {
		context.append(" )");
	}

	@Override
	protected void doKey(final Filter.Key key, final StringBuilder context) {
		context.append("@.").append(key.key());
	}

	@Override
	protected void doStartGroup(final Filter.Group group, final StringBuilder context) {
		context.append("(");
	}

	@Override
	protected void doEndGroup(final Filter.Group group, final StringBuilder context) {
		context.append(")");
	}

}
