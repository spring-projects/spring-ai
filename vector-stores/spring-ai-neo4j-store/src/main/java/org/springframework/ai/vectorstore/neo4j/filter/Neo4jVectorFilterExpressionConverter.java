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

package org.springframework.ai.vectorstore.neo4j.filter;

import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.Filter.Group;
import org.springframework.ai.vectorstore.filter.Filter.Key;
import org.springframework.ai.vectorstore.filter.converter.AbstractFilterExpressionConverter;
import org.springframework.util.Assert;

/**
 * Converts {@link Expression} into Neo4j condition expression format.
 *
 * @author Gerrit Meier
 * @author Dimitrios Begnis
 */
public class Neo4jVectorFilterExpressionConverter extends AbstractFilterExpressionConverter {

	@Override
	protected void doExpression(Expression expression, StringBuilder context) {
		if (expression.type() == Filter.ExpressionType.NIN) {
			// shift the "<left> not in <right>" into "not <left> in <right>"
			this.doNot(new Expression(Filter.ExpressionType.NOT,
					new Expression(Filter.ExpressionType.IN, expression.left(), expression.right())), context);
		}
		else {
			Assert.state(expression.right() != null, "expression.right() must not be null");
			this.convertOperand(expression.left(), context);
			context.append(this.getOperationSymbol(expression));
			this.convertOperand(expression.right(), context);
		}
	}

	private String getOperationSymbol(Expression exp) {
		return switch (exp.type()) {
			case AND -> " AND ";
			case OR -> " OR ";
			case EQ -> " = ";
			case NE -> " <> ";
			case LT -> " < ";
			case LTE -> " <= ";
			case GT -> " > ";
			case GTE -> " >= ";
			case IN -> " IN ";
			case NOT, NIN -> " NOT ";
			// you never know what the future might bring
			default -> throw new RuntimeException("Not supported expression type: " + exp.type());
		};
	}

	@Override
	protected void doNot(Expression expression, StringBuilder context) {
		Filter.ExpressionType expressionType = expression.type();
		// should not happen, but better safe than sorry
		if (expressionType != Filter.ExpressionType.NOT) {
			throw new RuntimeException(
					"Unsupported expression type %s. Only NOT is supported here".formatted(expressionType));
		}

		// explicitly prefix the embedded expression with NOT
		context.append("NOT ").append(this.convertOperand(expression.left()));
	}

	@Override
	protected void doKey(Key key, StringBuilder context) {
		context.append("node.").append("`metadata.").append(key.key().replace("\"", "")).append("`");
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
