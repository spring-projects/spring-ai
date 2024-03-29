/*
 * Copyright 2024 - 2024 the original author or authors.
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
package org.springframework.ai.vectorstore;

import com.datastax.oss.driver.api.core.metadata.schema.ColumnMetadata;
import com.datastax.oss.driver.api.core.type.DataTypes;
import com.datastax.oss.driver.api.core.type.codec.registry.CodecRegistry;
import com.datastax.oss.driver.shaded.guava.common.base.Preconditions;
import static java.lang.String.format;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.ai.vectorstore.filter.Filter;

import org.springframework.ai.vectorstore.filter.Filter.ExpressionType;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.AND;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.EQ;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.GT;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.GTE;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.LT;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.LTE;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.NE;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.NIN;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.NOT;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.OR;
import org.springframework.ai.vectorstore.filter.Filter.Group;
import org.springframework.ai.vectorstore.filter.Filter.Key;
import org.springframework.ai.vectorstore.filter.Filter.Value;
import org.springframework.ai.vectorstore.filter.converter.AbstractFilterExpressionConverter;

/**
 * Converts {@link Expression} into CQL where clauses.
 *
 * @author Mick Semb Wever
 */
final class CassandraFilterExpressionConverter extends AbstractFilterExpressionConverter {

	private final Map<String, ColumnMetadata> columnsByName;

	public CassandraFilterExpressionConverter(Collection<ColumnMetadata> columns) {

		columnsByName = columns.stream()
			.collect(Collectors.toMap((c) -> c.getName().asInternal(), Function.identity()));
	}

	@Override
	public String convertExpression(Filter.Expression expression) {
		// TODO
		// scan and collect all keys in the expression
		// and validate we have a valid where clause
		// rules:
		// - if one partition column is specified, or partition columns must be
		// - if a clustering column is specified all previous clustering columns must be
		// specified with EQ operand
		return super.convertExpression(expression);
	}

	@Override
	protected void doKey(Key key, StringBuilder context) {
		String keyName = key.key();
		Optional<ColumnMetadata> column = getColumn(keyName);
		Preconditions.checkArgument(column.isPresent(), "No metafield %s has been configured", keyName);
		context.append(column.get().getName().asCql(false));
	}

	@Override
	protected void doExpression(Filter.Expression expression, StringBuilder context) {
		switch (expression.type()) {
			case AND:
				doBinaryOperation(" and ", expression, context);
				break;
			case OR:
				doBinaryOperation(" or ", expression, context);
				break;
			case NIN:
			case NOT:
				throw new UnsupportedOperationException(
						format("Expression type %s not yet implemented. Patches welcome.", expression.type()));
			default:
				doField(expression, context);
				break;
		}
	}

	private static void doOperand(ExpressionType type, StringBuilder context) {
		switch (type) {
			case EQ:
				context.append(" = ");
				break;
			case NE:
				context.append(" != ");
				break;
			case GT:
				context.append(" > ");
				break;
			case GTE:
				context.append(" >= ");
				break;
			case IN:
				context.append(" IN ");
				break;
			// TODO SAI supports collections
			// reach out to mck@apache.org if you'd like these implemented
			// case CONTAINS:
			// context.append(" CONTAINS ");
			// break;
			// case CONTAINS_KEY:
			// context.append(" CONTAINS KEY ");
			// break;
			case LT:
				context.append(" < ");
				break;
			case LTE:
				context.append(" <= ");
				break;
			default:
				throw new UnsupportedOperationException(
						format("Expression type %s not yet implemented. Patches welcome.", type));
		}
	}

	private void doBinaryOperation(String operator, Filter.Expression expression, StringBuilder context) {
		this.convertOperand(expression.left(), context);
		context.append(operator);
		this.convertOperand(expression.right(), context);
	}

	private void doField(Filter.Expression expression, StringBuilder context) {
		doKey((Key) expression.left(), context);
		doOperand(expression.type(), context);
		ColumnMetadata column = getColumn(((Key) expression.left()).key()).get();
		var v = ((Value) expression.right()).value();
		if (ExpressionType.IN.equals(expression.type())) {
			Preconditions.checkArgument(v instanceof Collection);
			doListValue(column, v, context);
		}
		else {
			doValue(column, v, context);
		}
	}

	private void doListValue(ColumnMetadata column, Object v, StringBuilder context) {
		context.append('(');
		for (var e : (Collection) v) {
			doValue(column, e, context);
			context.append(',');
		}
		context.deleteCharAt(context.length() - 1);
		context.append(')');
	}

	private void doValue(ColumnMetadata column, Object v, StringBuilder context) {
		if (DataTypes.SMALLINT.equals(column.getType())) {
			v = ((Number) v).shortValue();
		}
		context.append(CodecRegistry.DEFAULT.codecFor(column.getType()).format(v));
	}

	private Optional<ColumnMetadata> getColumn(String name) {
		Optional<ColumnMetadata> column = Optional.ofNullable(columnsByName.get(name));

		// work around the need to escape filter keys the ANTLR parser doesn't like
		// e.g. with underscores like chunk_no
		if (column.isEmpty()) {
			if (name.startsWith("\"") && name.endsWith("\"")) {
				name = name.substring(1, name.length() - 1);
				column = Optional.ofNullable(columnsByName.get(name));
			}
		}
		return column;
	}

}
