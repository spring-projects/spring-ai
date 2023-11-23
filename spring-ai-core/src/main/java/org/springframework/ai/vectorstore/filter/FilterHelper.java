/*
 * Copyright 2023-2023 the original author or authors.
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

package org.springframework.ai.vectorstore.filter;

import java.util.Map;

import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.Filter.ExpressionType;
import org.springframework.ai.vectorstore.filter.Filter.Operand;

/**
 * Helper class providing various boolean transformation.
 *
 * @author Christian Tzolov
 */
public class FilterHelper {

	private FilterHelper() {
	}

	private final static Map<ExpressionType, ExpressionType> TYPE_NEGATION_MAP = Map.of(ExpressionType.AND,
			ExpressionType.OR, ExpressionType.OR, ExpressionType.AND, ExpressionType.EQ, ExpressionType.NE,
			ExpressionType.NE, ExpressionType.EQ, ExpressionType.GT, ExpressionType.LTE, ExpressionType.GTE,
			ExpressionType.LT, ExpressionType.LT, ExpressionType.GTE, ExpressionType.LTE, ExpressionType.GT,
			ExpressionType.IN, ExpressionType.NIN, ExpressionType.NIN, ExpressionType.IN);

	/**
	 * Transforms the input expression into a semantically equivalent one with negation
	 * operators propagated thought the expression tree by following the negation rules:
	 *
	 * <pre>
	 * 	NOT(NOT(a)) = a
	 *
	 * 	NOT(a AND b) = NOT(a) OR NOT(b)
	 * 	NOT(a OR b) = NOT(a) AND NOT(b)
	 *
	 * 	NOT(a EQ b) = a NE b
	 * 	NOT(a NE b) = a EQ b
	 *
	 * 	NOT(a GT b) = a LTE b
	 * 	NOT(a GTE b) = a LT b
	 *
	 * 	NOT(a LT b) = a GTE b
	 * 	NOT(a LTE b) = a GT b
	 *
	 * 	NOT(a IN [...]) = a NIN [...]
	 * 	NOT(a NIN [...]) = a IN [...]
	 * </pre>
	 * @param operand Filter expression to negate.
	 * @return Returns an negation of the input expression.
	 */
	public static Filter.Operand negate(Filter.Operand operand) {

		if (operand instanceof Filter.Group group) {
			Operand inEx = negate(group.content());
			if (inEx instanceof Filter.Group inEx2) {
				inEx = inEx2.content();
			}
			return new Filter.Group((Expression) inEx);
		}
		else if (operand instanceof Filter.Expression exp) {
			switch (exp.type()) {
				case NOT: // NOT(NOT(a)) = a
					return negate(exp.left());
				case AND: // NOT(a AND b) = NOT(a) OR NOT(b)
				case OR: // NOT(a OR b) = NOT(a) AND NOT(b)
					return new Filter.Expression(TYPE_NEGATION_MAP.get(exp.type()), negate(exp.left()),
							negate(exp.right()));
				case EQ: // NOT(e EQ b) = e NE b
				case NE: // NOT(e NE b) = e EQ b
				case GT: // NOT(e GT b) = e LTE b
				case GTE: // NOT(e GTE b) = e LT b
				case LT: // NOT(e LT b) = e GTE b
				case LTE: // NOT(e LTE b) = e GT b
					return new Filter.Expression(TYPE_NEGATION_MAP.get(exp.type()), exp.left(), exp.right());
				case IN: // NOT(e IN [...]) = e NIN [...]
				case NIN: // NOT(e NIN [...]) = e IN [...]
					return new Filter.Expression(TYPE_NEGATION_MAP.get(exp.type()), exp.left(), exp.right());
				default:
					throw new IllegalArgumentException("Unknown expression type: " + exp.type());
			}
		}
		else {
			throw new IllegalArgumentException("Can not negate operand of type: " + operand.getClass());
		}
	}

}
