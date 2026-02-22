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

package org.springframework.ai.vectorstore.mariadb;

import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.Filter.Group;
import org.springframework.ai.vectorstore.filter.Filter.Key;
import org.springframework.ai.vectorstore.filter.converter.AbstractFilterExpressionConverter;
import org.springframework.util.Assert;

/**
 * Converts {@link Expression} into JSON metadata filter expression format.
 * (https://mariadb.com/kb/en/json-functions/)
 *
 * @author Diego Dupin
 */
public class MariaDBFilterExpressionConverter extends AbstractFilterExpressionConverter {

	private final String metadataFieldName;

	public MariaDBFilterExpressionConverter(String metadataFieldName) {
		this.metadataFieldName = metadataFieldName;
	}

	@Override
	protected void doExpression(Expression expression, StringBuilder context) {
		Assert.state(expression.right() != null, "expected expression.right to be non null");
		this.convertOperand(expression.left(), context);
		context.append(getOperationSymbol(expression));
		this.convertOperand(expression.right(), context);
	}

	@Override
	protected void doSingleValue(Object value, StringBuilder context) {
		if (value instanceof String s) {
			context.append("'");
			context.append(escapeSqlStringValue(s));
			context.append("'");
		}
		else {
			context.append(value);
		}
	}

	/**
	 * Escape special characters in string values for SQL to prevent injection attacks.
	 *
	 * <p>
	 * This method escapes characters according to SQL string literal rules. Single quotes
	 * are escaped by doubling them (') (''). Backslashes are also escaped to prevent
	 * unintended escape sequences.
	 * @param input the string to escape
	 * @return the escaped string safe for use in SQL string literals
	 * @author Zexuan Peng &lt;pengzexuan@gmail.com&gt;
	 */
	private String escapeSqlStringValue(String input) {
		// Replace in order: \ first, then '
		// Backslash MUST be first to avoid double-escaping
		// In SQL, single quote is escaped by doubling it
		return input.replace("\\", "\\\\").replace("'", "''");
	}

	private String getOperationSymbol(Expression exp) {
		return switch (exp.type()) {
			case AND -> " AND ";
			case OR -> " OR ";
			case EQ -> " = ";
			case NE -> " != ";
			case LT -> " < ";
			case LTE -> " <= ";
			case GT -> " > ";
			case GTE -> " >= ";
			case IN -> " IN ";
			case NOT, NIN -> " NOT IN ";
			// you never know what the future might bring
			default -> throw new RuntimeException("Not supported expression type: " + exp.type());
		};
	}

	@Override
	protected void doKey(Key key, StringBuilder context) {
		context.append("JSON_VALUE(" + this.metadataFieldName + ", '$." + key.key() + "')");
	}

	protected void doStartValueRange(Filter.Value listValue, StringBuilder context) {
		context.append("(");
	}

	protected void doEndValueRange(Filter.Value listValue, StringBuilder context) {
		context.append(")");
	}

	@Override
	protected void doStartGroup(Group group, StringBuilder context) {
		context.append("(");
	}

	@Override
	protected void doEndGroup(Group group, StringBuilder context) {
		context.append(")");
	}

}
