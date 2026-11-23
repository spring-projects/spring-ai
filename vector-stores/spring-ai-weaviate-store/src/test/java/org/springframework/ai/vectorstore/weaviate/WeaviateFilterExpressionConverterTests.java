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

package org.springframework.ai.vectorstore.weaviate;

import java.util.List;

import io.weaviate.client6.v1.api.collections.query.Filter;
import org.junit.jupiter.api.Test;

import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.Filter.Group;
import org.springframework.ai.vectorstore.filter.Filter.Key;
import org.springframework.ai.vectorstore.filter.Filter.Value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.AND;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.EQ;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.GTE;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.IN;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.LT;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.LTE;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.NE;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.NIN;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.OR;

/**
 * Tests for {@link WeaviateFilterExpressionConverter}.
 *
 * <p>
 * Assertions are made against the client6 {@link Filter#toString()} representation, which
 * is deterministic and renders the full operand tree (for example
 * {@code Filter(meta_country Equal BG)}). This is the most practical structural assertion
 * surface, since {@link Filter} exposes no accessors or {@code equals}.
 *
 * @author Christian Tzolov
 */
class WeaviateFilterExpressionConverterTests {

	private String convert(WeaviateFilterExpressionConverter converter, Expression expression) {
		return converter.convert(expression).toString();
	}

	@Test
	void testMissingFilterName() {
		WeaviateFilterExpressionConverter converter = new WeaviateFilterExpressionConverter(List.of());

		assertThatThrownBy(() -> converter.convert(new Expression(EQ, new Key("country"), new Value("BG"))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Not allowed filter identifier name: country")
			.hasMessageContaining("WeaviateVectorStore#filterMetadataFields");
	}

	@Test
	void testSystemId() {
		WeaviateFilterExpressionConverter converter = new WeaviateFilterExpressionConverter(List.of());

		// The Spring AI document id maps to Weaviate's system uuid field.
		assertThat(convert(converter, new Expression(EQ, new Key("id"), new Value("1"))))
			.isEqualTo("Filter(_id Equal 1)");
		assertThat(convert(converter, new Expression(NE, new Key("id"), new Value("1"))))
			.isEqualTo("Filter(_id NotEqual 1)");
	}

	@Test
	void testRemovedSystemTimestampIdentifiers() {
		WeaviateFilterExpressionConverter converter = new WeaviateFilterExpressionConverter(List.of());

		// The GraphQL _creationTimeUnix / _lastUpdateTimeUnix special identifiers are no
		// longer accepted after the client6 migration.
		assertThatThrownBy(() -> converter.convert(new Expression(GTE, new Key("_creationTimeUnix"), new Value("36"))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Not allowed filter identifier name: _creationTimeUnix");
	}

	@Test
	void testEq() {
		WeaviateFilterExpressionConverter converter = new WeaviateFilterExpressionConverter(List.of("country"));

		assertThat(convert(converter, new Expression(EQ, new Key("country"), new Value("BG"))))
			.isEqualTo("Filter(meta_country Equal BG)");
	}

	@Test
	void testEqAndGte() {
		WeaviateFilterExpressionConverter converter = new WeaviateFilterExpressionConverter(List.of("genre", "year"));

		// genre == "drama" AND year >= 2020
		String filter = convert(converter, new Expression(AND, new Expression(EQ, new Key("genre"), new Value("drama")),
				new Expression(GTE, new Key("year"), new Value(2020))));
		assertThat(filter)
			.isEqualTo("Filter(Filter(meta_genre Equal drama) And Filter(meta_year GreaterThanEqual 2020.0))");
	}

	@Test
	void testIn() {
		WeaviateFilterExpressionConverter converter = new WeaviateFilterExpressionConverter(List.of("genre"));

		// genre in ["comedy", "documentary", "drama"] expands to an OR of equals.
		String filter = convert(converter,
				new Expression(IN, new Key("genre"), new Value(List.of("comedy", "documentary", "drama"))));
		assertThat(filter).isEqualTo("Filter(Filter(meta_genre Equal comedy) Or Filter(meta_genre Equal documentary) "
				+ "Or Filter(meta_genre Equal drama))");
	}

	@Test
	void testNin() {
		WeaviateFilterExpressionConverter converter = new WeaviateFilterExpressionConverter(List.of("city"));

		// city not in ["Sofia", "Plovdiv"] expands to an AND of not-equals.
		String filter = convert(converter,
				new Expression(NIN, new Key("city"), new Value(List.of("Sofia", "Plovdiv"))));
		assertThat(filter).isEqualTo("Filter(Filter(meta_city NotEqual Sofia) And Filter(meta_city NotEqual Plovdiv))");
	}

	@Test
	void testNe() {
		WeaviateFilterExpressionConverter converter = new WeaviateFilterExpressionConverter(
				List.of("city", "year", "country"));

		// year >= 2020 OR (country == "BG" AND city != "Sofia")
		String filter = convert(converter,
				new Expression(OR, new Expression(GTE, new Key("year"), new Value(2020)),
						new Expression(AND, new Expression(EQ, new Key("country"), new Value("BG")),
								new Expression(NE, new Key("city"), new Value("Sofia")))));
		assertThat(filter).isEqualTo("Filter(Filter(meta_year GreaterThanEqual 2020.0) "
				+ "Or Filter(Filter(meta_country Equal BG) And Filter(meta_city NotEqual Sofia)))");
	}

	@Test
	void testGroup() {
		WeaviateFilterExpressionConverter converter = new WeaviateFilterExpressionConverter(
				List.of("city", "year", "country"));

		// (year >= 2020 OR country == "BG") AND city NIN ["Sofia", "Plovdiv"]
		String filter = convert(converter,
				new Expression(AND,
						new Group(new Expression(OR, new Expression(GTE, new Key("year"), new Value(2020)),
								new Expression(EQ, new Key("country"), new Value("BG")))),
						new Expression(NIN, new Key("city"), new Value(List.of("Sofia", "Plovdiv")))));
		assertThat(filter)
			.isEqualTo("Filter(Filter(Filter(meta_year GreaterThanEqual 2020.0) Or Filter(meta_country Equal BG)) "
					+ "And Filter(Filter(meta_city NotEqual Sofia) And Filter(meta_city NotEqual Plovdiv)))");
	}

	@Test
	void testBoolean() {
		WeaviateFilterExpressionConverter converter = new WeaviateFilterExpressionConverter(
				List.of("isOpen", "year", "country"));

		// isOpen == true AND year >= 2020 AND country IN ["BG", "NL", "US"]
		String filter = convert(converter,
				new Expression(AND,
						new Expression(AND, new Expression(EQ, new Key("isOpen"), new Value(true)),
								new Expression(GTE, new Key("year"), new Value(2020))),
						new Expression(IN, new Key("country"), new Value(List.of("BG", "NL", "US")))));
		assertThat(filter)
			.isEqualTo("Filter(Filter(Filter(meta_isOpen Equal true) And Filter(meta_year GreaterThanEqual 2020.0)) "
					+ "And Filter(Filter(meta_country Equal BG) Or Filter(meta_country Equal NL) "
					+ "Or Filter(meta_country Equal US)))");
	}

	@Test
	void testDecimal() {
		WeaviateFilterExpressionConverter converter = new WeaviateFilterExpressionConverter(List.of("temperature"));

		// temperature >= -15.6 AND temperature <= 20.13. Note the client6 LESS_THAN_EQUAL
		// operator renders as "LessThenEqual".
		String filter = convert(converter,
				new Expression(AND, new Expression(GTE, new Key("temperature"), new Value(-15.6)),
						new Expression(LTE, new Key("temperature"), new Value(20.13))));
		assertThat(filter).isEqualTo("Filter(Filter(meta_temperature GreaterThanEqual -15.6) "
				+ "And Filter(meta_temperature LessThenEqual 20.13))");
	}

	@Test
	void testComplexIdentifiers() {
		WeaviateFilterExpressionConverter converter = new WeaviateFilterExpressionConverter(List.of("country 1 2 3"));

		assertThat(convert(converter, new Expression(EQ, new Key("\"country 1 2 3\""), new Value("BG"))))
			.isEqualTo("Filter(meta_country 1 2 3 Equal BG)");

		assertThat(convert(converter, new Expression(EQ, new Key("'country 1 2 3'"), new Value("BG"))))
			.isEqualTo("Filter(meta_country 1 2 3 Equal BG)");
	}

	@Test
	void testUnsupportedOperatorForValueType() {
		WeaviateFilterExpressionConverter converter = new WeaviateFilterExpressionConverter(List.of("isOpen"));

		// A relational operator is not supported for a boolean value.
		assertThatThrownBy(() -> converter.convert(new Expression(LT, new Key("isOpen"), new Value(true))))
			.isInstanceOf(UnsupportedOperationException.class);
	}

}
