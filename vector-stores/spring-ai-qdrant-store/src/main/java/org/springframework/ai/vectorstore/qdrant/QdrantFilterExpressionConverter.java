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

package org.springframework.ai.vectorstore.qdrant;

import java.util.ArrayList;
import java.util.List;

import io.qdrant.client.grpc.Points.Condition;
import io.qdrant.client.grpc.Points.Filter;
import io.qdrant.client.grpc.Points.Range;

import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.Filter.ExpressionType;
import org.springframework.ai.vectorstore.filter.Filter.Group;
import org.springframework.ai.vectorstore.filter.Filter.Key;
import org.springframework.ai.vectorstore.filter.Filter.Operand;
import org.springframework.ai.vectorstore.filter.Filter.Value;
import org.springframework.util.Assert;

/**
 * @author Anush Shetty
 * @since 0.8.1
 */
class QdrantFilterExpressionConverter {

	public Filter convertExpression(Expression expression) {
		return this.convertOperand(expression);
	}

	protected Filter convertOperand(Operand operand) {
		var context = Filter.newBuilder();
		List<Condition> mustClauses = new ArrayList<>();
		List<Condition> shouldClauses = new ArrayList<>();
		List<Condition> mustNotClauses = new ArrayList<>();

		if (operand instanceof Expression expression) {
			if (expression.type() == ExpressionType.NOT && expression.left() instanceof Group group) {
				mustNotClauses.add(io.qdrant.client.ConditionFactory.filter(convertOperand(group.content())));
			}
			else if (expression.type() == ExpressionType.AND) {
				Assert.state(expression.right() != null, "expected an expression with a right operand");
				mustClauses.add(io.qdrant.client.ConditionFactory.filter(convertOperand(expression.left())));
				mustClauses.add(io.qdrant.client.ConditionFactory.filter(convertOperand(expression.right())));
			}
			else if (expression.type() == ExpressionType.OR) {
				Assert.state(expression.right() != null, "expected an expression with a right operand");
				shouldClauses.add(io.qdrant.client.ConditionFactory.filter(convertOperand(expression.left())));
				shouldClauses.add(io.qdrant.client.ConditionFactory.filter(convertOperand(expression.right())));
			}
			else {
				if (!(expression.right() instanceof Value)) {
					throw new RuntimeException("Non AND/OR/NOT expression must have Value right argument!");
				}
				mustClauses.add(parseComparison((Key) expression.left(), (Value) expression.right(), expression));
			}

		}

		return context.addAllMust(mustClauses).addAllShould(shouldClauses).addAllMustNot(mustNotClauses).build();
	}

	protected Condition parseComparison(Key key, Value value, Expression exp) {

		ExpressionType type = exp.type();
		return switch (type) {
			case EQ -> buildEqCondition(key, value);
			case NE -> buildNeCondition(key, value);
			case GT -> buildGtCondition(key, value);
			case GTE -> buildGteCondition(key, value);
			case LT -> buildLtCondition(key, value);
			case LTE -> buildLteCondition(key, value);
			case IN -> buildInCondition(key, value);
			case NIN -> buildNInCondition(key, value);
			default -> throw new RuntimeException("Unsupported expression type: " + type);
		};
	}

	protected Condition buildEqCondition(Key key, Value value) {
		String identifier = doKey(key);
		if (value.value() instanceof String valueStr) {
			return io.qdrant.client.ConditionFactory.matchKeyword(identifier, valueStr);
		}
		else if (value.value() instanceof Number valueNum) {
			long lValue = Long.parseLong(valueNum.toString());
			return io.qdrant.client.ConditionFactory.match(identifier, lValue);
		}

		throw new IllegalArgumentException("Invalid value type for EQ. Can either be a string or Number");

	}

	protected Condition buildNeCondition(Key key, Value value) {
		String identifier = doKey(key);
		if (value.value() instanceof String valueStr) {
			return io.qdrant.client.ConditionFactory.filter(Filter.newBuilder()
				.addMustNot(io.qdrant.client.ConditionFactory.matchKeyword(identifier, valueStr))
				.build());
		}
		else if (value.value() instanceof Number valueNum) {
			long lValue = Long.parseLong(valueNum.toString());
			Condition condition = io.qdrant.client.ConditionFactory.match(identifier, lValue);
			return io.qdrant.client.ConditionFactory.filter(Filter.newBuilder().addMustNot(condition).build());
		}

		throw new IllegalArgumentException("Invalid value type for NEQ. Can either be a string or Number");

	}

	protected Condition buildGtCondition(Key key, Value value) {
		String identifier = doKey(key);
		if (value.value() instanceof Number valueNum) {
			Double dvalue = Double.parseDouble(valueNum.toString());
			return io.qdrant.client.ConditionFactory.range(identifier, Range.newBuilder().setGt(dvalue).build());
		}
		throw new RuntimeException("Unsupported value type for GT condition. Only supports Number");

	}

	protected Condition buildLtCondition(Key key, Value value) {
		String identifier = doKey(key);
		if (value.value() instanceof Number valueNum) {
			Double dvalue = Double.parseDouble(valueNum.toString());
			return io.qdrant.client.ConditionFactory.range(identifier, Range.newBuilder().setLt(dvalue).build());
		}
		throw new RuntimeException("Unsupported value type for LT condition. Only supports Number");

	}

	protected Condition buildGteCondition(Key key, Value value) {
		String identifier = doKey(key);
		if (value.value() instanceof Number valueNum) {
			Double dvalue = Double.parseDouble(valueNum.toString());
			return io.qdrant.client.ConditionFactory.range(identifier, Range.newBuilder().setGte(dvalue).build());
		}
		throw new RuntimeException("Unsupported value type for GTE condition. Only supports Number");

	}

	protected Condition buildLteCondition(Key key, Value value) {
		String identifier = doKey(key);
		if (value.value() instanceof Number valueNum) {
			Double dvalue = Double.parseDouble(valueNum.toString());
			return io.qdrant.client.ConditionFactory.range(identifier, Range.newBuilder().setLte(dvalue).build());
		}
		throw new RuntimeException("Unsupported value type for LTE condition. Only supports Number");

	}

	protected Condition buildInCondition(Key key, Value value) {
		if (value.value() instanceof List valueList && !valueList.isEmpty()) {
			Object firstValue = valueList.get(0);
			String identifier = doKey(key);

			if (firstValue instanceof String) {
				// If the first value is a string, then all values should be strings
				List<String> stringValues = new ArrayList<>();
				for (Object valueObj : valueList) {
					stringValues.add(valueObj.toString());
				}
				return io.qdrant.client.ConditionFactory.matchKeywords(identifier, stringValues);
			}
			else if (firstValue instanceof Number) {
				// If the first value is a number, then all values should be numbers
				List<Long> longValues = new ArrayList<>();
				for (Object valueObj : valueList) {
					Long longValue = Long.parseLong(valueObj.toString());
					longValues.add(longValue);
				}
				return io.qdrant.client.ConditionFactory.matchValues(identifier, longValues);
			}
			else {
				throw new RuntimeException("Unsupported value in IN value list. Only supports String or Number");
			}
		}
		throw new RuntimeException(
				"Unsupported value type for IN condition. Only supports non-empty List of String or Number");

	}

	protected Condition buildNInCondition(Key key, Value value) {
		if (value.value() instanceof List valueList && !valueList.isEmpty()) {
			Object firstValue = valueList.get(0);
			String identifier = doKey(key);

			if (firstValue instanceof String) {
				// If the first value is a string, then all values should be strings
				List<String> stringValues = new ArrayList<>();
				for (Object valueObj : valueList) {
					stringValues.add(valueObj.toString());
				}
				return io.qdrant.client.ConditionFactory.matchExceptKeywords(identifier, stringValues);
			}
			else if (firstValue instanceof Number) {
				// If the first value is a number, then all values should be numbers
				List<Long> longValues = new ArrayList<>();
				for (Object valueObj : valueList) {
					Long longValue = Long.parseLong(valueObj.toString());
					longValues.add(longValue);
				}
				return io.qdrant.client.ConditionFactory.matchExceptValues(identifier, longValues);
			}
			else {
				throw new RuntimeException("Unsupported value in NIN value list. Only supports String or Number");
			}
		}
		throw new RuntimeException(
				"Unsupported value type for NIN condition. Only supports non-empty List of String or Number");

	}

	protected String doKey(Key key) {
		var identifier = (hasOuterQuotes(key.key())) ? removeOuterQuotes(key.key()) : key.key();
		return identifier;
	}

	protected boolean hasOuterQuotes(String str) {
		str = str.trim();
		return (str.startsWith("\"") && str.endsWith("\"")) || (str.startsWith("'") && str.endsWith("'"));
	}

	protected String removeOuterQuotes(String in) {
		return in.substring(1, in.length() - 1);
	}

}
