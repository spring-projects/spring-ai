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

package org.springframework.ai.vectorstore.milvus;

import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.Filter.ExpressionType;
import org.springframework.ai.vectorstore.filter.Filter.Group;
import org.springframework.ai.vectorstore.filter.Filter.Key;
import org.springframework.ai.vectorstore.filter.converter.AbstractFilterExpressionConverter;
import org.springframework.util.Assert;

/**
 * Converts {@link Expression} into Milvus metadata filter expression format. See Milvus
 * JSON‑field & filtering docs:
 * <a href="https://milvus.io/docs/json-field-overview.md">json-field-overview</a>
 *
 * @author Christian Tzolov
 * @author Soby Chacko
 */
public class MilvusFilterExpressionConverter extends AbstractFilterExpressionConverter {

	@Override
	protected void doExpression(Expression exp, StringBuilder context) {
		Assert.state(exp.right() != null, "expected expression.right to be non null");
		this.convertOperand(exp.left(), context);
		context.append(getOperationSymbol(exp));
		this.convertOperand(exp.right(), context);
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
			case IN -> " in ";
			case NIN -> " not in ";
			default -> throw new RuntimeException("Not supported expression type:" + exp.type());
		};
	}

	@Override
	protected void doGroup(Group group, StringBuilder context) {
		this.convertOperand(new Expression(ExpressionType.AND, group.content(), group.content()), context); // trick
	}

	@Override
	protected void doKey(Key key, StringBuilder context) {
		var identifier = key.key();
		context.append("metadata[");
		emitJsonValue(identifier, context);
		context.append("]");
	}

	/**
	 * Serialize values using JSON serialization for Milvus filter expressions. Delegates
	 * to {@link #emitJsonValue(Object, StringBuilder)} for Jackson-based JSON
	 * serialization.
	 * @param value the value to serialize
	 * @param context the context to append the JSON representation to
	 */
	@Override
	protected void doSingleValue(Object value, StringBuilder context) {
		emitJsonValue(value, context);
	}

	/**
	 * Serialize a value as a Milvus filter-expression literal using the same
	 * Jackson-based JSON escaping applied to values inside {@link Expression} conversion.
	 * Produces a double-quoted, fully escaped string for text values (e.g.
	 * {@code "a\"b"}), which Milvus accepts as a string literal in filter expressions.
	 * This is intended for callers that build filter expressions by hand (e.g.
	 * {@code id in [...]} deletes) and need to inline user-supplied values safely without
	 * manual quote concatenation.
	 * @param value the value to serialize
	 * @return the escaped literal suitable for inlining in a Milvus filter expression
	 */
	static String toFilterExpressionLiteral(Object value) {
		StringBuilder sb = new StringBuilder();
		emitJsonValue(value, sb);
		return sb.toString();
	}

}
