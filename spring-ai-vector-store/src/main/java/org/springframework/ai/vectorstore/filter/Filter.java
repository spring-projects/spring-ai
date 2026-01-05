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

import org.jspecify.annotations.Nullable;

/**
 * Portable runtime generative for metadata filter expressions. This generic generative is
 * used to define store agnostic filter expressions than later can be converted into
 * vector-store specific, native, expressions.
 *
 * The expression generative supports constant comparison
 * {@code (e.g. ==, !=, <, <=, >, >=) }, IN/NON-IN checks and AND and OR to compose
 * multiple expressions.
 *
 * For example:
 *
 * <pre>{@code
 * // 1: country == "BG"
 * new Expression(EQ, new Key("country"), new Value("BG"));
 *
 * // 2: genre == "drama" AND year >= 2020
 * new Expression(AND, new Expression(EQ, new Key("genre"), new Value("drama")),
 * 		new Expression(GTE, new Key("year"), new Value(2020)));
 *
 * // 3: genre in ["comedy", "documentary", "drama"]
 * new Expression(IN, new Key("genre"), new Value(List.of("comedy", "documentary", "drama")));
 *
 * // 4: year >= 2020 OR country == "BG" AND city != "Sofia"
 * new Expression(OR, new Expression(GTE, new Key("year"), new Value(2020)),
 * 		new Expression(AND, new Expression(EQ, new Key("country"), new Value("BG")),
 * 				new Expression(NE, new Key("city"), new Value("Sofia"))));
 *
 * // 5: (year >= 2020 OR country == "BG") AND city NIN ["Sofia", "Plovdiv"]
 * new Expression(AND,
 * 		new Group(new Expression(OR, new Expression(EQ, new Key("country"), new Value("BG")),
 * 				new Expression(GTE, new Key("year"), new Value(2020)))),
 * 		new Expression(NIN, new Key("city"), new Value(List.of("Sofia", "Varna"))));
 *
 * // 6: isOpen == true AND year >= 2020 AND country IN ["BG", "NL", "US"]
 * new Expression(AND, new Expression(EQ, new Key("isOpen"), new Value(true)),
 * 		new Expression(AND, new Expression(GTE, new Key("year"), new Value(2020)),
 * 				new Expression(IN, new Key("country"), new Value(List.of("BG", "NL", "US")))));
 *
 * }</pre>
 *
 *
 * Usually you will not create expression manually but use either the
 * {@link FilterExpressionBuilder} DSL or the {@link FilterExpressionTextParser} for
 * parsing generic text expressions.
 *
 * @author Christian Tzolov
 */
public class Filter {

	/**
	 * Filter expression operations. <br/>
	 *
	 * - EQ, NE, GT, GTE, LT, LTE operations supports "Key ExprType Value"
	 * expressions.<br/>
	 *
	 * - AND, OR are binary operations that support "(Expression|Group) ExprType
	 * (Expression|Group)" expressions. <br/>
	 *
	 * - IN, NIN support "Key (IN|NIN) ArrayValue" expression. <br/>
	 */
	public enum ExpressionType {

		AND, OR, EQ, NE, GT, GTE, LT, LTE, IN, NIN, NOT, ISNULL, ISNOTNULL

	}

	/**
	 * Mark interface representing the supported expression types: {@link Key},
	 * {@link Value}, {@link Expression} and {@link Group}.
	 */
	public interface Operand {

	}

	/**
	 * String identifier representing an expression key. (e.g. the country in the country
	 * == "NL" expression).
	 *
	 * @param key expression key
	 */
	public record Key(String key) implements Operand {

	}

	/**
	 * Represents expression value constant or constant array. Support Numeric, Boolean
	 * and String data types.
	 *
	 * @param value value constant or constant array
	 */
	public record Value(Object value) implements Operand {

	}

	/**
	 * Triple that represents and filter boolean expression as
	 * <code>left type right</code>.
	 *
	 * @param type Specify the expression type.
	 * @param left For comparison and inclusion expression types, the operand must be of
	 * type {@link Key} and for the AND|OR expression types the left operand must be
	 * another {@link Expression}.
	 * @param right For comparison and inclusion expression types, the operand must be of
	 * type {@link Value} or array of values. For the AND|OR type the right operand must
	 * be another {@link Expression}.
	 */
	public record Expression(ExpressionType type, Operand left, @Nullable Operand right) implements Operand {

		public Expression(ExpressionType type, Operand operand) {
			this(type, operand, null);
		}

	}

	/**
	 * Represents expression grouping (e.g. (...) ) that indicates that the group needs to
	 * be evaluated with a precedence.
	 *
	 * @param content Inner expression to be evaluated as a part of the group.
	 */
	public record Group(Expression content) implements Operand {

	}

}
