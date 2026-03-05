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

package org.springframework.ai.vectorstore.filter.converter;

import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.Filter.ExpressionType;
import org.springframework.ai.vectorstore.filter.Filter.Key;
import org.springframework.util.Assert;

/**
 * Converts {@link Expression} into Pinecone metadata filter expression format.
 * (<a href="https://docs.pinecone.io/docs/metadata-filtering">Metadata filtering</a>)
 *
 * @author Christian Tzolov
 */
public class PineconeFilterExpressionConverter extends AbstractFilterExpressionConverter {

	@Override
	protected void doExpression(Expression exp, StringBuilder context) {
		Assert.state(exp.right() != null, "Codepath expects exp.right to be non-null");
		context.append("{");
		if (exp.type() == ExpressionType.AND || exp.type() == ExpressionType.OR) {
			context.append(getOperationSymbol(exp));
			context.append("[");
			this.convertOperand(exp.left(), context);
			context.append(",");
			this.convertOperand(exp.right(), context);
			context.append("]");
		}
		else {
			this.convertOperand(exp.left(), context);
			context.append("{");
			context.append(getOperationSymbol(exp));
			this.convertOperand(exp.right(), context);
			context.append("}");
		}
		context.append("}");

	}

	private String getOperationSymbol(Expression exp) {
		return "\"$" + exp.type().toString().toLowerCase() + "\": ";
	}

	@Override
	protected void doKey(Key key, StringBuilder context) {
		var identifier = (hasOuterQuotes(key.key())) ? removeOuterQuotes(key.key()) : key.key();
		context.append("\"").append(identifier).append("\": ");
	}

}
