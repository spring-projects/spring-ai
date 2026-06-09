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

package org.springframework.ai.vectorstore.couchbase;

import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.Filter.Group;
import org.springframework.ai.vectorstore.filter.Filter.Key;
import org.springframework.ai.vectorstore.filter.converter.AbstractFilterExpressionConverter;

/**
 * @author Laurent Doguin
 * @since 1.0.0
 */
public class CouchbaseAiSearchFilterExpressionConverter extends AbstractFilterExpressionConverter {

	public CouchbaseAiSearchFilterExpressionConverter() {
	}

	@Override
	protected void doExpression(Expression expression, StringBuilder context) {
		this.convertOperand(expression.left(), context);
		context.append(getOperationSymbol(expression));
		if (expression.right() != null) {
			this.convertOperand(expression.right(), context);
		}
		else {
			context.append("NULL");
		}
	}

	private String getOperationSymbol(Expression exp) {
		return switch (exp.type()) {
			case AND -> " AND ";
			case OR -> " OR ";
			case EQ -> " == ";
			case NE -> " != ";
			case LT -> " < ";
			case LTE -> " <= ";
			case GT -> " > ";
			case GTE -> " >= ";
			case IN -> " IN ";
			case NIN -> " NOT IN ";
			default -> throw new RuntimeException("Not supported expression type: " + exp.type());
		};
	}

	@Override
	protected void doKey(Key key, StringBuilder context) {
		context.append("metadata.");
		var identifier = key.key();
		// Couchbase N1QL/SQL++ uses backtick-quoted identifiers.
		// Within backticks, the only character needing escaping is the backtick
		// itself (doubled as ``).
		context.append('`');
		for (int i = 0; i < identifier.length(); i++) {
			char c = identifier.charAt(i);
			if (c == '`') {
				context.append("``");
			}
			else {
				context.append(c);
			}
		}
		context.append('`');
	}

	@Override
	protected void doStartGroup(Group group, StringBuilder context) {
		context.append("(");
	}

	@Override
	protected void doEndGroup(Group group, StringBuilder context) {
		context.append(")");
	}

	@Override
	protected void doSingleValue(Object value, StringBuilder context) {
		emitJsonValue(value, context);
	}

}
