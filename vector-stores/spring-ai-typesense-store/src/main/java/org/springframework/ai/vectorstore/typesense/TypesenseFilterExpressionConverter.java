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
		context.append("metadata." + key.key() + ":");
	}

}
