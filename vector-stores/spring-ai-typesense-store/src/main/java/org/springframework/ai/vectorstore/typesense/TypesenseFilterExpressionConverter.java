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

package org.springframework.ai.vectorstore.typesense;

import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.converter.AbstractFilterExpressionConverter;
import org.springframework.util.Assert;

/**
 * Converts {@link Filter.Expression} into Typesense metadata filter expression format.
 * (https://typesense.org/docs/0.24.0/api/search.html#filter-parameters)
 *
 * @author Pablo Sanchidrian
 */
public class TypesenseFilterExpressionConverter extends AbstractFilterExpressionConverter {

	@Override
	protected void doExpression(Filter.Expression exp, StringBuilder context) {
		Assert.state(exp.right() != null, "expected non null right operand");
		this.convertOperand(exp.left(), context);
		context.append(getOperationSymbol(exp));
		this.convertOperand(exp.right(), context);
	}

	private String getOperationSymbol(Filter.Expression exp) {
		return switch (exp.type()) {
			case AND -> " && ";
			case OR -> " || ";
			case EQ -> " "; // in typesense "EQ" operator looks like -> country:USA
			case NE -> " != ";
			case LT -> " < ";
			case LTE -> " <= ";
			case GT -> " > ";
			case GTE -> " >= ";
			case IN -> " "; // in typesense "IN" operator looks like -> country: [USA, UK]
			case NIN -> " != "; // in typesense "NIN" operator looks like -> country:
			// !=[USA, UK]
			default -> throw new RuntimeException("Not supported expression type:" + exp.type());
		};
	}

	@Override
	protected void doGroup(Filter.Group group, StringBuilder context) {
		this.convertOperand(new Filter.Expression(Filter.ExpressionType.AND, group.content(), group.content()),
				context); // trick
	}

	@Override
	protected void doKey(Filter.Key key, StringBuilder context) {
		var identifier = (hasOuterQuotes(key.key())) ? removeOuterQuotes(key.key()) : key.key();
		// Typesense field names are bare identifiers in filter_by syntax
		// (field_name:value) with no escaping mechanism. Validate that the
		// identifier contains only safe characters to prevent filter injection.
		for (int i = 0; i < identifier.length(); i++) {
			char c = identifier.charAt(i);
			if (!Character.isLetterOrDigit(c) && c != '_' && c != '.' && c != '-') {
				throw new IllegalArgumentException("Not allowed filter identifier name: " + identifier);
			}
		}
		context.append("metadata.").append(identifier).append(":");
	}

	/**
	 * Serialize values using JSON serialization for Typesense filter expressions.
	 * Delegates to {@link #emitJsonValue(Object, StringBuilder)} for Jackson-based JSON
	 * serialization.
	 * @param value the value to serialize
	 * @param context the context to append the JSON representation to
	 */
	@Override
	protected void doSingleValue(Object value, StringBuilder context) {
		emitJsonValue(value, context);
	}

}
