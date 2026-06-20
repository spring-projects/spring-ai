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

package org.springframework.ai.vectorstore.pgvector;

import java.util.Date;
import java.util.List;

import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.Filter.Group;
import org.springframework.ai.vectorstore.filter.Filter.Key;
import org.springframework.ai.vectorstore.filter.converter.AbstractFilterExpressionConverter;
import org.springframework.util.Assert;

/**
 * Converts {@link Expression} into PgVector metadata filter expression format.
 * (https://www.postgresql.org/docs/current/functions-json.html)
 * <p>
 * The output is a complete SQL predicate ready for use in a WHERE clause (e.g.
 * {@code metadata::jsonb @@ '...'::jsonpath}). Single quotes are properly escaped, and
 * JSONPath member names are always wrapped in double quotes with {@code \} and {@code "}
 * JS-escaped.
 *
 * @author Muthukumaran Navaneethakrishnan
 * @author Christian Tzolov
 */
public class PgVectorFilterExpressionConverter extends AbstractFilterExpressionConverter {

	private static final String DEFAULT_METADATA_COLUMN = "metadata";

	private final String metadataColumn;

	public PgVectorFilterExpressionConverter() {
		this(DEFAULT_METADATA_COLUMN);
	}

	public PgVectorFilterExpressionConverter(String metadataColumn) {
		Assert.hasText(metadataColumn, "Metadata column name must not be empty");
		this.metadataColumn = metadataColumn;
	}

	@Override
	protected void doExpression(Expression expression, StringBuilder context) {
		Assert.state(expression.right() != null, "expression should have a right operand");
		if (expression.type() == Filter.ExpressionType.IN) {
			handleIn(expression, context);
		}
		else if (expression.type() == Filter.ExpressionType.NIN) {
			handleNotIn(expression, context);
		}
		else {
			this.convertOperand(expression.left(), context);
			context.append(getOperationSymbol(expression));
			this.convertOperand(expression.right(), context);
		}
	}

	private void handleIn(Expression expression, StringBuilder context) {
		context.append("(");
		convertToConditions(expression, context);
		context.append(")");
	}

	private void convertToConditions(Expression expression, StringBuilder context) {
		Assert.state(expression.right() != null, "expression should have a right operand");
		Filter.Value right = (Filter.Value) expression.right();
		Object value = right.value();
		if (!(value instanceof List)) {
			throw new IllegalArgumentException("Expected a List, but got: " + value.getClass().getSimpleName());
		}
		List<Object> values = (List) value;
		for (int i = 0; i < values.size(); i++) {
			this.convertOperand(expression.left(), context);
			context.append(" == ");
			this.doSingleValue(normalizeDateString(values.get(i)), context);
			if (i < values.size() - 1) {
				context.append(" || ");
			}
		}
	}

	private void handleNotIn(Expression expression, StringBuilder context) {
		context.append("!(");
		convertToConditions(expression, context);
		context.append(")");
	}

	private String getOperationSymbol(Expression exp) {
		return switch (exp.type()) {
			case AND -> " && ";
			case OR -> " || ";
			case EQ -> " == ";
			case NE -> " != ";
			case LT -> " < ";
			case LTE -> " <= ";
			case GT -> " > ";
			case GTE -> " >= ";
			default -> throw new RuntimeException("Not supported expression type: " + exp.type());
		};
	}

	@Override
	public String convertExpression(Expression expression) {
		String jsonPath = super.convertExpression(expression);
		return quoteIdentifier(this.metadataColumn) + "::jsonb @@ '" + jsonPath + "'::jsonpath";
	}

	/**
	 * Quote a SQL identifier using double quotes (PostgreSQL/SQL standard) only if
	 * needed. Simple identifiers (alphanumeric starting with letter/underscore) are
	 * returned unquoted to preserve PostgreSQL's case-insensitive behavior. Identifiers
	 * containing special characters are quoted with internal double quotes escaped by
	 * doubling.
	 */
	private static String quoteIdentifier(String identifier) {
		if (identifier.matches("^[A-Za-z_][A-Za-z0-9_]*$")) {
			return identifier;
		}
		return "\"" + identifier.replace("\"", "\"\"") + "\"";
	}

	@Override
	protected void doKey(Key key, StringBuilder context) {
		StringBuilder jsonKey = new StringBuilder();
		emitJsonValue(key.key(), jsonKey);
		// emitJsonValue handles \ and " internally.
		// We just need to handle the SQL-specific single-quote escaping ('' instead of
		// ').
		context.append("$.").append(jsonKey.toString().replace("'", "''"));
	}

	@Override
	protected void doStartGroup(Group group, StringBuilder context) {
		context.append("(");
	}

	@Override
	protected void doEndGroup(Group group, StringBuilder context) {
		context.append(")");
	}

	/**
	 * Serialize values for PostgreSQL JSONPath expressions with proper escaping.
	 * <p>
	 * Values are JSON-serialized, then single quotes are escaped for SQL embedding.
	 * @param value the value to serialize
	 * @param context the context to append the representation to
	 */
	@Override
	protected void doSingleValue(Object value, StringBuilder context) {
		StringBuilder jsonBuffer = new StringBuilder();
		if (value instanceof Date date) {
			emitJsonValue(ISO_DATE_FORMATTER.format(date.toInstant()), jsonBuffer);
		}
		else {
			emitJsonValue(value, jsonBuffer);
		}
		// Escape single quotes for SQL string literal embedding
		context.append(jsonBuffer.toString().replace("'", "''"));
	}

}
