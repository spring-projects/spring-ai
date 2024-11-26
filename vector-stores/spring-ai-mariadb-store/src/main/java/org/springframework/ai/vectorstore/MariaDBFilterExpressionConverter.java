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

package org.springframework.ai.vectorstore;

import java.util.List;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.Filter.Group;
import org.springframework.ai.vectorstore.filter.Filter.Key;
import org.springframework.ai.vectorstore.filter.converter.AbstractFilterExpressionConverter;

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
		this.convertOperand(expression.left(), context);
		context.append(getOperationSymbol(expression));
		this.convertOperand(expression.right(), context);
	}

	private void convertToConditions(Expression expression, StringBuilder context) {
		Filter.Value right = (Filter.Value) expression.right();
		Object value = right.value();
		if (!(value instanceof List)) {
			throw new IllegalArgumentException("Expected a List, but got: " + value.getClass().getSimpleName());
		}
		List<Object> values = (List) value;
		for (int i = 0; i < values.size(); i++) {
			this.convertOperand(expression.left(), context);
			context.append(" == ");
			this.doSingleValue(values.get(i), context);
			if (i < values.size() - 1) {
				context.append(" || ");
			}
		}
	}

	@Override
	protected void doSingleValue(Object value, StringBuilder context) {
		if (value instanceof String) {
			context.append(String.format("\'%s\'", value));
		}
		else {
			context.append(value);
		}
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
