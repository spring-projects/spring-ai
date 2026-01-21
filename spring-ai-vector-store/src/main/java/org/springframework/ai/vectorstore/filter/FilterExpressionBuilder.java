/*
 * Copyright 2023-2025 the original author or authors.
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

import java.util.List;

import org.springframework.ai.vectorstore.filter.Filter.ExpressionType;
import org.springframework.ai.vectorstore.filter.Filter.Key;
import org.springframework.ai.vectorstore.filter.Filter.Value;

/**
 * DSL builder for {@link Filter.Expression} instances. Here are some common examples:
 *
 * <pre>{@code
 * var b = new FilterExpressionBuilder();
 *
 * // 1: country == "BG"
 * var exp1 = b.eq("country", "BG");
 *
 * // 2: genre == "drama" AND year >= 2020
 * var exp2 = b.and(b.eq("genre", "drama"), b.gte("year", 2020));
 *
 * // 3: genre in ["comedy", "documentary", "drama"]
 * var exp3 = b.in("genre", "comedy", "documentary", "drama");
 *
 * // 4: year >= 2020 OR country == "BG" AND city != "Sofia"
 * var exp4 = b.and(b.or(b.gte("year", 2020), b.eq("country", "BG")), b.ne("city", "Sofia"));
 *
 * // 5: (year >= 2020 OR country == "BG") AND city NIN ["Sofia", "Plovdiv"]
 * var exp5 = b.and(b.group(b.or(b.gte("year", 2020), b.eq("country", "BG"))), b.nin("city", "Sofia", "Plovdiv"));
 *
 * // 6: isOpen == true AND year >= 2020 AND country IN ["BG", "NL", "US"]
 * var exp6 = b.and(b.and(b.eq("isOpen", true), b.gte("year", 2020)), b.in("country", "BG", "NL", "US"));
 *
 * }</pre>
 *
 *
 * This builder DSL mimics the common
 * <a href="https://www.baeldung.com/hibernate-criteria-queries">Criteria Queries</a>
 * syntax.
 *
 * @author Christian Tzolov
 */
public class FilterExpressionBuilder {

	public Op eq(String key, Object value) {
		return new Op(new Filter.Expression(ExpressionType.EQ, new Key(key), new Value(value)));
	}

	public Op ne(String key, Object value) {
		return new Op(new Filter.Expression(ExpressionType.NE, new Key(key), new Value(value)));
	}

	public Op gt(String key, Object value) {
		return new Op(new Filter.Expression(ExpressionType.GT, new Key(key), new Value(value)));
	}

	public Op gte(String key, Object value) {
		return new Op(new Filter.Expression(ExpressionType.GTE, new Key(key), new Value(value)));
	}

	public Op lt(String key, Object value) {
		return new Op(new Filter.Expression(ExpressionType.LT, new Key(key), new Value(value)));
	}

	public Op lte(String key, Object value) {
		return new Op(new Filter.Expression(ExpressionType.LTE, new Key(key), new Value(value)));
	}

	public Op and(Op left, Op right) {
		return new Op(new Filter.Expression(ExpressionType.AND, left.expression, right.expression));
	}

	public Op or(Op left, Op right) {
		return new Op(new Filter.Expression(ExpressionType.OR, left.expression, right.expression));
	}

	public Op in(String key, Object... values) {
		return this.in(key, List.of(values));
	}

	public Op in(String key, List<Object> values) {
		return new Op(new Filter.Expression(ExpressionType.IN, new Key(key), new Value(values)));
	}

	public Op nin(String key, Object... values) {
		return this.nin(key, List.of(values));
	}

	public Op nin(String key, List<Object> values) {
		return new Op(new Filter.Expression(ExpressionType.NIN, new Key(key), new Value(values)));
	}

	public Op isNull(String key) {
		return new Op(new Filter.Expression(ExpressionType.ISNULL, new Key(key)));
	}

	public Op isNotNull(String key) {
		return new Op(new Filter.Expression(ExpressionType.ISNOTNULL, new Key(key)));
	}

	public Op group(Op content) {
		return new Op(new Filter.Group(content.build()));
	}

	public Op not(Op content) {
		return new Op(new Filter.Expression(ExpressionType.NOT, content.expression, null));
	}

	public record Op(Filter.Operand expression) {

		public Filter.Expression build() {
			if (this.expression instanceof Filter.Group group) {
				// Remove the top-level grouping.
				return group.content();
			}
			else if (this.expression instanceof Filter.Expression exp) {
				return exp;
			}
			throw new RuntimeException("Invalid expression: " + this.expression);
		}

	}

}
