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

package org.springframework.ai.vectorstore.mariadb;

import java.util.Date;

import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.Filter.Group;
import org.springframework.ai.vectorstore.filter.Filter.Key;
import org.springframework.ai.vectorstore.filter.converter.AbstractFilterExpressionConverter;
import org.springframework.util.Assert;

/**
 * Converts {@link Expression} into MariaDB SQL WHERE clause format using JSON_VALUE
 * functions for metadata filtering.
 * <p>
 * Generates SQL predicates that query JSON metadata fields using MariaDB's JSON
 * functions. For more information on MariaDB JSON functions, see:
 * <a href="https://mariadb.com/kb/en/json-functions/">MariaDB JSON Functions</a>
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
		if (value instanceof Date date) {
			emitSqlString(ISO_DATE_FORMATTER.format(date.toInstant()), context);
		}
		else if (value instanceof String stringValue) {
			emitSqlString(stringValue, context);
		}
		else {
			context.append(value);
		}
	}

	/**
	 * Emit a SQL-formatted string value with single quote wrapping and escaping by
	 * appending to the provided context. Used by MariaDB and MySQL for filter
	 * expressions.
	 * <p>
	 * This method prevents SQL injection attacks by properly escaping all special
	 * characters and control sequences according to MariaDB/MySQL string literal rules.
	 * <p>
	 * Escape sequences:
	 * <ul>
	 * <li>{@code '} → {@code ''} (SQL standard single quote doubling)</li>
	 * <li>{@code \} → {@code \\} (backslash escaping)</li>
	 * <li>{@code \b \f \n \r \t} → Escape sequences for control characters</li>
	 * <li>Unicode control chars (U+0000 to U+001F) → {@code \\uXXXX} format</li>
	 * </ul>
	 * @param value the string value to format
	 * @param context the context to append the SQL string literal to
	 * @since 2.0.0
	 */
	protected static void emitSqlString(String value, StringBuilder context) {
		context.append("'"); // Opening quote

		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);

			switch (c) {
				case '\'':
					// SQL standard: single quote → doubled
					context.append("''");
					break;
				case '\\':
					// Backslash → escaped for MySQL/MariaDB
					context.append("\\\\");
					break;
				case '\b':
					context.append("\\b");
					break;
				case '\f':
					context.append("\\f");
					break;
				case '\n':
					context.append("\\n");
					break;
				case '\r':
					context.append("\\r");
					break;
				case '\t':
					context.append("\\t");
					break;
				default:
					// Escape Unicode control characters (U+0000 to U+001F)
					if (c < 0x20) {
						context.append(String.format("\\u%04x", (int) c));
					}
					else {
						context.append(c);
					}
					break;
			}
		}

		context.append("'"); // Closing quote
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
		// metadataFieldName could contain a malicious character and hence we treat it as
		// a MariaDB SQL identifier.
		context.append("JSON_VALUE(").append(quoteIdentifier(this.metadataFieldName)).append(", ");

		StringBuilder jsonKey = new StringBuilder();
		emitJsonValue(key.key(), jsonKey);
		// Now, the whole JSONPath is emitted as a SQL string
		emitSqlString("$." + jsonKey.toString(), context);
		context.append(")");
	}

	/**
	 * Quote a SQL identifier using backticks (MySQL/MariaDB standard). Identifiers
	 * containing backticks are escaped by doubling them.
	 */
	private static String quoteIdentifier(String identifier) {
		return "`" + identifier.replace("`", "``") + "`";
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
