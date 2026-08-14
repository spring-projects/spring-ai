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

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.Filter.Group;
import org.springframework.ai.vectorstore.filter.Filter.Key;
import org.springframework.ai.vectorstore.filter.Filter.Value;
import org.springframework.ai.vectorstore.filter.FilterExpressionConverter;
import org.springframework.ai.vectorstore.filter.FilterExpressionTextParser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.AND;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.EQ;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.GT;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.GTE;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.IN;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.LTE;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.NE;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.NIN;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.OR;

/**
 * Tests for {@link CouchbaseAiSearchFilterExpressionConverter}.
 */
class CouchbaseAiSearchFilterExpressionConverterTests {

	private final FilterExpressionConverter converter = new CouchbaseAiSearchFilterExpressionConverter();

	@Test
	void testEQ() {
		String expr = this.converter.convertExpression(new Expression(EQ, new Key("country"), new Value("NL")));
		assertThat(expr).isEqualTo("metadata.`country` == \"NL\"");
	}

	@Test
	void testEqAndGte() {
		String expr = this.converter
			.convertExpression(new Expression(AND, new Expression(EQ, new Key("genre"), new Value("drama")),
					new Expression(GTE, new Key("year"), new Value(2020))));
		assertThat(expr).isEqualTo("metadata.`genre` == \"drama\" AND metadata.`year` >= 2020");
	}

	@Test
	void testIn() {
		String expr = this.converter.convertExpression(
				new Expression(IN, new Key("genre"), new Value(List.of("comedy", "documentary", "drama"))));
		assertThat(expr).isEqualTo("metadata.`genre` IN [\"comedy\",\"documentary\",\"drama\"]");
	}

	@Test
	void testNe() {
		String expr = this.converter
			.convertExpression(new Expression(OR, new Expression(GTE, new Key("year"), new Value(2020)),
					new Expression(AND, new Expression(EQ, new Key("country"), new Value("BG")),
							new Expression(NE, new Key("city"), new Value("Sofia")))));
		assertThat(expr)
			.isEqualTo("metadata.`year` >= 2020 OR metadata.`country` == \"BG\" AND metadata.`city` != \"Sofia\"");
	}

	@Test
	void testGroup() {
		String expr = this.converter.convertExpression(new Expression(AND,
				new Group(new Expression(OR, new Expression(GTE, new Key("year"), new Value(2020)),
						new Expression(EQ, new Key("country"), new Value("BG")))),
				new Expression(NIN, new Key("city"), new Value(List.of("Sofia", "Plovdiv")))));
		assertThat(expr).isEqualTo(
				"(metadata.`year` >= 2020 OR metadata.`country` == \"BG\") AND metadata.`city` NOT IN [\"Sofia\",\"Plovdiv\"]");
	}

	@Test
	void testBoolean() {
		String expr = this.converter
			.convertExpression(new Expression(AND, new Expression(EQ, new Key("isOpen"), new Value(true)),
					new Expression(GTE, new Key("year"), new Value(2020))));
		assertThat(expr).isEqualTo("metadata.`isOpen` == true AND metadata.`year` >= 2020");
	}

	@Test
	void testDecimal() {
		String expr = this.converter
			.convertExpression(new Expression(AND, new Expression(GTE, new Key("temperature"), new Value(-15.6)),
					new Expression(LTE, new Key("temperature"), new Value(20.13))));
		assertThat(expr).isEqualTo("metadata.`temperature` >= -15.6 AND metadata.`temperature` <= 20.13");
	}

	@Test
	void testComplexIdentifiers() {
		String expr = this.converter.convertExpression(new Expression(EQ, new Key("country 1 2 3"), new Value("BG")));
		assertThat(expr).isEqualTo("metadata.`country 1 2 3` == \"BG\"");

		expr = this.converter.convertExpression(new Expression(EQ, new Key("\"country 1 2 3\""), new Value("BG")));
		assertThat(expr).isEqualTo("metadata.`\"country 1 2 3\"` == \"BG\"");

		expr = this.converter.convertExpression(new Expression(EQ, new Key("'country 1 2 3'"), new Value("BG")));
		assertThat(expr).isEqualTo("metadata.`'country 1 2 3'` == \"BG\"");
	}

	@Test
	void parsedExpressionUsesBacktickQuotedKey() {
		var parsed = new FilterExpressionTextParser().parse("country == 'NL'");
		String expr = this.converter.convertExpression(parsed);
		assertThat(expr).isEqualTo("metadata.`country` == \"NL\"");
	}

	// Security tests - backtick escaping prevents N1QL injection

	@Test
	void metadataKeyWithBacktickIsEscaped() {
		String expr = this.converter.convertExpression(new Expression(EQ, new Key("field`evil"), new Value("x")));
		assertThat(expr).isEqualTo("metadata.`field``evil` == \"x\"");
	}

	@Test
	void metadataKeyWithInjectionPayloadIsContained() {
		String expr = this.converter
			.convertExpression(new Expression(EQ, new Key("country` OR 1=1 --"), new Value("x")));
		assertThat(expr).isEqualTo("metadata.`country`` OR 1=1 --` == \"x\"");
		// The backtick in the key is escaped, so the injection payload stays inside
		// the identifier
		assertThat(expr).startsWith("metadata.`country``");
	}

	@Test
	void metadataKeyWithDoubleQuoteIsPreserved() {
		// Double quotes inside backtick-quoted identifiers don't need escaping
		String expr = this.converter.convertExpression(new Expression(EQ, new Key("country\" OR 1=1"), new Value("x")));
		assertThat(expr).isEqualTo("metadata.`country\" OR 1=1` == \"x\"");
	}

	@Test
	void testGt() {
		String expr = this.converter.convertExpression(new Expression(GT, new Key("priority"), new Value(1)));
		assertThat(expr).isEqualTo("metadata.`priority` > 1");
	}

}
