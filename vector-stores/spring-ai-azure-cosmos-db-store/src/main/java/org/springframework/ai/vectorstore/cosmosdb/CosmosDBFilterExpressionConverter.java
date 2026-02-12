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

package org.springframework.ai.vectorstore.cosmosdb;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.Filter.Key;
import org.springframework.ai.vectorstore.filter.converter.AbstractFilterExpressionConverter;
import org.springframework.util.Assert;

import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.AND;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.OR;

/**
 * Converts {@link org.springframework.ai.vectorstore.filter.Filter.Expression} into
 * Cosmos DB NoSQL API where clauses.
 *
 * @author Theo van Kraay
 * @since 1.1.0
 */
class CosmosDBFilterExpressionConverter extends AbstractFilterExpressionConverter {

	private Map<String, String> metadataFields;

	CosmosDBFilterExpressionConverter(Collection<String> columns) {
		Assert.notNull(columns, "Columns must not be null");
		this.metadataFields = columns.stream()
				.distinct()
				.collect(Collectors.toMap(Function.identity(), Function.identity()));
	}

	/**
	 * Gets the metadata field from the Cosmos DB document.
	 * @param name The name of the metadata field.
	 * @return The name of the metadata field as it should appear in the query.
	 */
	private Optional<String> getMetadataField(String name) {
		String metadataField = name;
		return Optional.ofNullable(this.metadataFields.get(metadataField));
	}

	@Override
	protected void doKey(Key key, StringBuilder context) {
		String keyName = key.key();

		String field = getMetadataField(keyName)
				.orElseThrow(() -> new IllegalArgumentException(
						String.format("No metadata field %s has been configured", keyName)));

		context.append("c.metadata.").append(field);
	}

	@Override
	protected void doExpression(Filter.Expression expression, StringBuilder context) {
		// Handling AND/OR
		if (AND.equals(expression.type()) || OR.equals(expression.type())) {
			doCompoundExpressionType(expression, context);
		}
		else {
			doSingleExpressionType(expression, context);
		}
	}

	private void doCompoundExpressionType(Filter.Expression expression, StringBuilder context) {
		Assert.state(expression.right() != null, "unexpected null right expression");
		context.append(" (");
		this.convertOperand(expression.left(), context);
		context.append(getOperationSymbol(expression));
		context.append(" (");
		this.convertOperand(expression.right(), context);
		int start = context.indexOf("[");
		if (start != -1) {
			context.replace(start, start + 1, "");
		}
		int end = context.indexOf("]");
		if (end != -1) {
			context.replace(end, end + 1, "");
		}
		context.append(")");
		context.append(")");
	}

	private void doSingleExpressionType(Filter.Expression expression, StringBuilder context) {
		Assert.state(expression.right() != null, "unexpected null right expression");
		this.convertOperand(expression.left(), context);
		context.append(getOperationSymbol(expression));
		context.append(" (");
		this.convertOperand(expression.right(), context);
		int start = context.indexOf("[");
		if (start != -1) {
			context.replace(start, start + 1, "");
		}
		int end = context.indexOf("]");
		if (end != -1) {
			context.replace(end, end + 1, "");
		}
		context.append(")");
	}

	private String getOperationSymbol(Filter.Expression exp) {
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
			case NIN -> " !IN ";
			default -> throw new RuntimeException("Not supported expression type:" + exp.type());
		};
	}

}
