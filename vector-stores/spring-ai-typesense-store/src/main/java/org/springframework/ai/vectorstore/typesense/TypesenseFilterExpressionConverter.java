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

import java.util.Date;

import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.converter.AbstractFilterExpressionConverter;
import org.springframework.util.Assert;

/**
 * Converts {@link Filter.Expression} into Typesense metadata filter expression format.
 * (https://typesense.org/docs/0.24.0/api/search.html#filter-parameters)
 *
 * @author Pablo Sanchidrian
 * @author Dimitar Proynov
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
	protected void doStartGroup(Filter.Group group, StringBuilder context) {
		context.append("(");
	}

	@Override
	protected void doEndGroup(Filter.Group group, StringBuilder context) {
		context.append(")");
	}

	@Override
	protected void doKey(Filter.Key key, StringBuilder context) {
		var identifier = (hasOuterQuotes(key.key())) ? removeOuterQuotes(key.key()) : key.key();
		// Typesense field names are bare identifiers in filter_by syntax
		// (field_name:value) with no escaping mechanism. Validate that the
		// identifier contains only safe characters (alphanumeric, underscore, dot, dash).
		for (int i = 0; i < identifier.length(); i++) {
			char c = identifier.charAt(i);
			if (!Character.isLetterOrDigit(c) && c != '_' && c != '.' && c != '-') {
				throw new IllegalArgumentException("Not allowed filter identifier name: " + identifier);
			}
		}
		context.append("metadata.").append(identifier).append(":");
	}

	/**
	 * Serialize values for Typesense filter expressions. Typesense's tokenizer has no
	 * escape mechanism for an embedded backtick (it unconditionally toggles literal mode
	 * on every backtick character), so a value containing one is rejected outright rather
	 * than risk breaking out of the literal.
	 * @param value the value to serialize
	 * @param context the context to append the representation to
	 */
	@Override
	protected void doSingleValue(Object value, StringBuilder context) {
		if (value instanceof Number || value instanceof Boolean) {
			context.append(value);
		}
		else if (value instanceof Date date) {
			context.append(date.getTime());
		}
		else {
			String stringValue = String.valueOf(value);
			if (stringValue.indexOf('`') >= 0) {
				throw new IllegalArgumentException("Illegal character '`' in filter value: " + stringValue);
			}
			context.append('`').append(stringValue).append('`');
		}
	}

}
